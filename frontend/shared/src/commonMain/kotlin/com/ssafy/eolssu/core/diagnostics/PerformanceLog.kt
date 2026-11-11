package com.ssafy.eolssu.core.diagnostics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.TimeSource

enum class PerfKind { ACTION, VISIT_STARTED, DATA_LOAD, HTTP_ATTEMPT, POLL_SUMMARY, MILESTONE, FLOW_END }
enum class PerfTarget {
    SONG_DETAIL, PRACTICE, RESULT, WEAK_ERRORS, WEAK_SELECT, WEAK_LOOP, SECTION_RESULT, ANALYSIS,
    RECORDS, RECORD_DETAIL, SONG_BUNDLE, SCORE, EVALUATION, RECORD_PAGE, MODEL_AUDIO_METADATA,
    MODEL_AUDIO_TIMELINE, MODEL_AUDIO, PRACTICE_STATUS, WEAK_SECTIONS, COMPARISON, AUTH_REFRESH,
    INSTRUMENTS, SONG_LIST,
}
enum class PerfMethod { GET, POST }
enum class PerfSource { NETWORK, NAVIGATION, RETAINED }
enum class PerfReason {
    INITIAL, RETRY, VERSION_MISMATCH, REFRESH, REPLAY, POLL,
    CANCELLED, FAILED, MISSING, READY, LEFT, UNAVAILABLE, HTTP_TIMEOUT,
}
enum class PerfPhase {
    SCORE_READY, SCORE_DRAWN, PRACTICE_READY, PRACTICE_DRAWN, RECORD_DRAWN, RECORD_EMPTY,
    MODEL_AUDIO_PLAY, TIMELINE_READY, PLAYER_LOAD, PLAYER_STARTED,
    SESSION_CREATE, RECORDING_UPLOAD, ANALYSIS_RETRY, FIRST_STATUS_REQUEST, ANALYSIS_READY,
}

/** Diagnostic events contain allowlisted labels, timings, aliases and random correlation IDs. */
data class PerformanceEvent(
    val kind: PerfKind,
    val ms: Long,
    val flow: Int,
    val visit: Int,
    val target: PerfTarget,
    val load: Int? = null,
    val resource: Int? = null,
    val revision: Int? = null,
    val source: PerfSource? = null,
    val reason: PerfReason? = null,
    val phase: PerfPhase? = null,
    val status: Int? = null,
    val durationMs: Long? = null,
    val count: Int? = null,
    val failed: Int? = null,
    val method: PerfMethod? = null,
    val outcome: PerfReason? = null,
    val evaluationTimingId: String? = null,
) {
    fun encode(): String = buildJsonObject {
        put("kind", kind.name.lowercase())
        put("ms", ms)
        put("flow", flow)
        put("visit", visit)
        put("target", target.name.lowercase())
        load?.let { put("load", it) }
        resource?.let { put("resource", it) }
        revision?.let { put("revision", it) }
        source?.let { put("source", it.name.lowercase()) }
        reason?.let { put("reason", it.name.lowercase()) }
        phase?.let { put("phase", it.name.lowercase()) }
        status?.let { put("status", it) }
        durationMs?.let { put("duration_ms", it) }
        count?.let { put("count", it) }
        failed?.let { put("failed", it) }
        method?.let { put("method", it.name.lowercase()) }
        outcome?.let { put("outcome", it.name.lowercase()) }
        evaluationTimingId?.let { put("evaluation_timing_id", it) }
    }.toString()
}

object PerformanceLog {
    @Volatile
    var sink: ((PerformanceEvent) -> Unit)? = null
    private val started = TimeSource.Monotonic.markNow()
    private val ids = MutableStateFlow(0)
    internal fun id(): Int = ids.updateAndGet { it + 1 }
    internal fun now(): Long = started.elapsedNow().inWholeMilliseconds
    internal fun emit(event: PerformanceEvent) {
        // Diagnostics must never change the result of the observed operation.
        runCatching { sink?.invoke(event) }
    }
}

class PerformanceFlow {
    internal val id = PerformanceLog.id()
    private val aliases = MutableStateFlow(emptyMap<Pair<PerfTarget, String>, Int>())

    internal fun alias(target: PerfTarget, value: String): Int? {
        val key = target to value
        return aliases.updateAndGet { old ->
            if (key in old || old.size >= 256) old else old + (key to (old.size + 1))
        }[key]
    }
}

class PerformanceVisit(
    internal val flow: PerformanceFlow,
    val target: PerfTarget,
    private val enteredAt: Long = PerformanceLog.now(),
) :
    AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<PerformanceVisit>

    private val id = PerformanceLog.id()
    private val marks = MutableStateFlow(emptySet<PerfPhase>())

    fun action(destination: PerfTarget) = event(PerfKind.ACTION, destination)
    fun started() = event(PerfKind.VISIT_STARTED)
    fun ended() = event(PerfKind.FLOW_END, reason = PerfReason.LEFT)

    fun milestone(phase: PerfPhase, once: Boolean = true, startedAt: Long = enteredAt) {
        if (once) {
            while (true) {
                val old = marks.value
                if (phase in old) return
                if (marks.compareAndSet(old, old + phase)) break
            }
        }
        event(PerfKind.MILESTONE, phase = phase, durationMs = (PerformanceLog.now() - startedAt).coerceAtLeast(0))
    }

    internal fun event(
        kind: PerfKind,
        target: PerfTarget = this.target,
        load: Int? = null,
        resource: Int? = null,
        revision: Int? = null,
        source: PerfSource? = null,
        reason: PerfReason? = null,
        phase: PerfPhase? = null,
        status: Int? = null,
        durationMs: Long? = null,
        count: Int? = null,
        failed: Int? = null,
        method: PerfMethod? = null,
        outcome: PerfReason? = null,
        evaluationTiming: EvaluationTiming? = null,
    ) = PerformanceLog.emit(PerformanceEvent(kind, PerformanceLog.now(), flow.id, id, target, load, resource,
        revision, source, reason, phase, status, durationMs, count, failed, method, outcome, evaluationTiming?.id))

    fun reuse(target: PerfTarget, key: String, source: PerfSource, revision: Int? = null) =
        event(PerfKind.DATA_LOAD, target, load = PerformanceLog.id(), resource = flow.alias(target, key),
            revision = revision?.let { flow.alias(target, "$key#$it") }, source = source, outcome = PerfReason.READY)
}

internal class PerformanceReason(val reason: PerfReason) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<PerformanceReason>
}

internal class PerformanceLoad(val id: Int, val resource: Int?) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<PerformanceLoad>
}

internal fun PerformanceVisit?.context(reason: PerfReason = PerfReason.INITIAL): CoroutineContext =
    if (this == null) EmptyCoroutineContext else this + PerformanceReason(reason)

internal suspend fun <T : Any> measureLoad(
    target: PerfTarget,
    key: String,
    revision: (T) -> Int? = { null },
    block: suspend () -> T?,
): T? {
    val visit = currentCoroutineContext()[PerformanceVisit] ?: return block()
    val load = PerformanceLoad(PerformanceLog.id(), visit.flow.alias(target, key))
    val reason = currentCoroutineContext()[PerformanceReason]?.reason
    val start = PerformanceLog.now()
    var outcome = PerfReason.FAILED
    var version: Int? = null
    try {
        return withContext(load) { block() }.also {
            outcome = if (it == null) PerfReason.MISSING else PerfReason.READY
            version = it?.let(revision)?.let { value -> visit.flow.alias(target, "$key#$value") }
        }
    } catch (cancelled: CancellationException) {
        outcome = PerfReason.CANCELLED
        throw cancelled
    } finally {
        visit.event(PerfKind.DATA_LOAD, target, load.id, load.resource, version, PerfSource.NETWORK,
            reason = reason ?: PerfReason.INITIAL, outcome = outcome,
            durationMs = PerformanceLog.now() - start)
    }
}
