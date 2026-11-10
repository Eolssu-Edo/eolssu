package com.ssafy.eolssu

/** The on-device export is replaced when a new command starts. */
internal class QaPerformanceSnapshots {
    private data class Snapshot(val requestId: String, val streamId: String, val end: Long, var exported: Long)

    private var active: Snapshot? = null

    fun begin(request: String, stream: String, ack: Long, end: Long): Long {
        val snapshot = active
        if (snapshot?.requestId == request && snapshot.streamId == stream) return snapshot.end
        active = Snapshot(request, stream, end, ack)
        return end
    }

    fun end(request: String, stream: String?): Long? =
        active?.takeIf { it.requestId == request && it.streamId == stream }?.end

    fun canResume(ack: Long, cursor: Long): Boolean = active?.let { cursor in ack..it.exported } ?: false

    /** Only call after publishing the bytes for the already contiguous range. */
    fun exported(through: Long) {
        val snapshot = checkNotNull(active)
        require(through <= snapshot.end)
        snapshot.exported = maxOf(snapshot.exported, through)
    }
}

internal class QaPerformanceChunk(envelope: String) {
    private val content = StringBuilder(envelope.dropLast(1)).append(",\"events\":[")
    private var bytes = content.toString().toByteArray(Charsets.UTF_8).size + 2
    private var needsComma = false

    fun add(seq: Long, event: String): Boolean {
        val item = "{\"seq\":$seq,\"event\":$event}"
        val size = item.toByteArray(Charsets.UTF_8).size + if (needsComma) 1 else 0
        if (bytes + size > MAX_BYTES) return false
        if (needsComma) content.append(',')
        content.append(item)
        bytes += size
        needsComma = true
        return true
    }

    fun finish(): String = content.append("]}").toString()

    companion object {
        const val MAX_BYTES = 128 * 1024
    }
}
