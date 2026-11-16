package com.ssafy.eolssu

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.system.Os
import android.util.Log
import androidx.core.content.ContextCompat
import com.ssafy.eolssu.core.diagnostics.PerfPhase
import com.ssafy.eolssu.core.diagnostics.PerformanceEvent
import com.ssafy.eolssu.core.diagnostics.PerformanceLog
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal object QaPerformanceLog {
    private const val QA_PACKAGE = "com.ssafy.eolssu.qa"
    private const val ACTION = "$QA_PACKAGE.APPLOG"
    private const val DUMP_PERMISSION = "android.permission.DUMP"
    private var worker: QaPerformanceWorker? = null
    private var owner: Activity? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION || intent.`package` != QA_PACKAGE || !isOrderedBroadcast) return
            val request = runCatching { SnapshotRequest.parse(intent) }.getOrNull() ?: return
            val log = worker ?: return
            val result = goAsync()
            if (!log.submit(request, result)) result.finish()
        }
    }

    fun install(context: Context) {
        if (!BuildConfig.DEBUG || BuildConfig.APPLICATION_ID != QA_PACKAGE || worker != null) return
        val log = QaPerformanceWorker(context.applicationContext)
        worker = log
        val live = BuildConfig.QA_LIVE && BuildConfig.API_BASE_URL == "http://127.0.0.1:18180"
        PerformanceLog.liveRequestsEnabled = live
        PerformanceLog.sink = { event ->
            // Keep the snapshot relay's established schema while logcat carries live-only fields.
            if (event.requestId == null && event.phase != PerfPhase.SONG_DETAIL_DRAWN &&
                event.phase != PerfPhase.SYNTHETIC_INPUT) log.offer(event)
            if (live) Log.i("EolssuLive", event.encode())
        }
    }

    fun show(activity: Activity) {
        if (worker == null || owner === activity) return
        owner?.let(::hide)
        val filter = IntentFilter(ACTION)
        ContextCompat.registerReceiver(
            activity, receiver, filter, DUMP_PERMISSION, null, ContextCompat.RECEIVER_EXPORTED,
        )
        owner = activity
    }

    fun hide(activity: Activity) {
        if (owner !== activity) return
        activity.unregisterReceiver(receiver)
        owner = null
    }
}

private data class SnapshotRequest(val id: String, val op: String, val stream: String?, val cursor: Long) {
    companion object {
        private val idPattern = Regex("[0-9a-f]{32}")
        fun parse(intent: Intent): SnapshotRequest? {
            val id = intent.getStringExtra("request_id")?.takeIf(idPattern::matches) ?: return null
            val op = intent.getStringExtra("op") ?: return null
            if (op == "begin") return SnapshotRequest(id, op, null, 0)
            if (op != "read" && op != "ack") return null
            val stream = intent.getStringExtra("stream_id")?.takeIf(idPattern::matches) ?: return null
            val cursor = intent.getLongExtra("cursor", -1).takeIf { it >= 0 } ?: return null
            return SnapshotRequest(id, op, stream, cursor)
        }
    }
}

private class QaPerformanceWorker(context: Context) {
    private sealed interface Work {
        data class Event(val event: PerformanceEvent) : Work
        data class Export(val request: SnapshotRequest, val result: BroadcastReceiver.PendingResult) : Work
    }

    private data class Reply(val json: String, val exportedThrough: Long? = null)

    private val store by lazy { QaPerformanceStore(context) }
    private val databaseDirectory by lazy { context.noBackupFilesDir }
    private val exportDirectory by lazy { File(context.cacheDir, "qa-perf") }
    private val runId = newQaPerformanceId()
    private val work = ArrayBlockingQueue<Work>(257)
    private val eventSlots = Semaphore(256)
    private val exportPending = AtomicBoolean()
    private val overflow = AtomicLong()
    private var storageMissing = 0L
    private val snapshots = QaPerformanceSnapshots()

    init {
        Thread(::run, "eolssu-qa-performance").apply {
            isDaemon = true
            start()
        }
    }

    fun offer(event: PerformanceEvent) {
        if (!eventSlots.tryAcquire()) {
            overflow.incrementAndGet()
            return
        }
        if (!work.offer(Work.Event(event))) {
            eventSlots.release()
            overflow.incrementAndGet()
        }
    }

    fun submit(request: SnapshotRequest, result: BroadcastReceiver.PendingResult): Boolean {
        if (!exportPending.compareAndSet(false, true)) return false
        if (work.offer(Work.Export(request, result))) return true
        exportPending.set(false)
        return false
    }

    private fun run() {
        // SQLite, JSON and filesystem access stay on this worker.
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        runCatching { removeOldResponses() }
        persist(emptyList())
        while (true) {
            when (val next = work.take()) {
                is Work.Event -> {
                    val batch = mutableListOf(next.event)
                    while (batch.size < 24 && work.peek() is Work.Event) {
                        batch += (work.remove() as Work.Event).event
                    }
                    try {
                        persist(batch)
                    } finally {
                        eventSlots.release(batch.size)
                    }
                }
                is Work.Export -> {
                    try {
                        if (next.request.op == "begin") persist(emptyList())
                        val request = next.request
                        val response = try {
                            export(request)
                        } catch (_: Exception) {
                            Reply(response(request).put("error", "storage_unavailable").toString())
                        }
                        writeResponse(request, response.json)
                        response.exportedThrough?.let(snapshots::exported)
                        next.result.setResultCode(Activity.RESULT_OK)
                    } catch (_: Exception) {
                        // A missing response is reported by the relay. Never write exception text to logs.
                    } finally {
                        exportPending.set(false)
                        next.result.finish()
                    }
                }
            }
        }
    }

    private fun persist(events: List<PerformanceEvent>) {
        if (databaseDirectory.usableSpace < MIN_FREE_BYTES) {
            storageMissing += events.size
            return
        }
        var lost = overflow.getAndSet(0)
        val encoded = events.mapNotNull { event ->
            val value = runCatching { addRunMetadata(JSONObject(event.encode())).toString() }.getOrNull()
            if (value == null || value.toByteArray(Charsets.UTF_8).size > MAX_EVENT_BYTES) {
                lost++
                null
            } else value
        }
        try {
            val state = store.state()
            val records = buildList {
                if (state.runId != runId) {
                    add(marker("run_started"))
                    if (state.runId != null) add(marker("log_gap", "uncertain"))
                }
                if (lost > 0) add(marker("log_gap", "overflow", lost))
                if (storageMissing > 0) add(marker("log_gap", "storage", storageMissing))
                addAll(encoded)
            }
            if (records.isNotEmpty()) store.append(runId, records)
            storageMissing = 0
        } catch (_: Exception) {
            overflow.addAndGet(lost)
            storageMissing += encoded.size
        }
    }

    private fun marker(kind: String, reason: String? = null, missing: Long? = null): String =
        addRunMetadata(JSONObject().put("kind", kind).put("ms", 0L)).apply {
            if (reason != null) put("reason", reason)
            if (missing != null) put("missing", missing)
        }.toString()

    private fun addRunMetadata(event: JSONObject): JSONObject =
        event.put("run_id", runId).put("version_name", BuildConfig.VERSION_NAME).put("version_code", BuildConfig.VERSION_CODE)

    private fun response(request: SnapshotRequest): JSONObject = JSONObject()
        .put("request_id", request.id)
        .put("op", request.op)

    private fun export(request: SnapshotRequest): Reply {
        val state = store.state()
        val reply = response(request).put("stream_id", state.streamId)
        if (request.op == "begin") {
            val end = snapshots.begin(request.id, state.streamId, state.ack, state.end)
            return Reply(reply.put("ack", state.ack).put("end", end).toString())
        }
        val end = snapshots.end(request.id, request.stream)
        if (end == null || request.stream != state.streamId) {
            return Reply(response(request).put("error", "snapshot_unavailable").toString())
        }
        if (!snapshots.canResume(state.ack, request.cursor)) {
            return Reply(response(request).put("error", "invalid_request").toString())
        }
        if (request.op == "ack") {
            store.acknowledge(request.cursor)
            return Reply(reply.put("ack", request.cursor).toString())
        }
        val chunk = QaPerformanceChunk(reply.toString())
        val through = store.read(request.cursor, end, chunk::add)
        check(through > request.cursor || request.cursor == end) { "Empty QA performance chunk before watermark" }
        return Reply(chunk.finish(), through)
    }

    private fun writeResponse(request: SnapshotRequest, contents: String) {
        check(exportDirectory.isDirectory || exportDirectory.mkdirs())
        val path = File(exportDirectory, "${request.id}-${request.op}.json")
        val temporary = File(exportDirectory, "${path.name}.tmp")
        val bytes = contents.toByteArray(Charsets.UTF_8)
        check(bytes.size <= QaPerformanceChunk.MAX_BYTES)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            Os.rename(temporary.path, path.path)
        } finally {
            temporary.delete()
        }
        removeOldResponses(path)
    }

    private fun removeOldResponses(keep: File? = null) {
        // The relay normally removes each response immediately. Clean up after a disconnect.
        exportDirectory.listFiles()?.filter { it != keep && EXPORT_FILENAME.matches(it.name) }
            ?.forEach(File::delete)
    }

    private companion object {
        const val MAX_EVENT_BYTES = 1024
        const val MIN_FREE_BYTES = 64L * 1024 * 1024
        val EXPORT_FILENAME = Regex("[0-9a-f]{32}-(begin|read|ack)\\.json(\\.tmp)?")
    }
}
