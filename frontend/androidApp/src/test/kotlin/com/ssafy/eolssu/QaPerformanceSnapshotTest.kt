package com.ssafy.eolssu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QaPerformanceSnapshotTest {
    @Test
    fun retryKeepsWatermarkAndUnexportedCursorCannotBeAcknowledged() {
        val snapshots = QaPerformanceSnapshots()
        assertEquals(9L, snapshots.begin("request", "stream", 3, 9))
        assertTrue(snapshots.canResume(3, 3))
        assertFalse(snapshots.canResume(3, 4))
        snapshots.exported(6)
        assertEquals(9L, snapshots.begin("request", "stream", 3, 12))
        assertTrue(snapshots.canResume(3, 6))
        assertFalse(snapshots.canResume(3, 7))
        assertFalse(snapshots.canResume(6, 3))
        assertEquals(9L, snapshots.end("request", "stream"))
    }

    @Test
    fun newRequestCannotUsePreviousExportProof() {
        val snapshots = QaPerformanceSnapshots()
        snapshots.begin("first", "stream", 3, 9)
        snapshots.exported(9)
        snapshots.begin("second", "stream", 5, 12)
        assertNull(snapshots.end("first", "stream"))
        assertNull(snapshots.end("second", "other"))
        assertEquals(12L, snapshots.end("second", "stream"))
        assertFalse(snapshots.canResume(5, 9))
        assertTrue(snapshots.canResume(5, 5))
    }

    @Test
    fun chunkBoundsIncludeUtf8BytesAndEnvelope() {
        val chunk = QaPerformanceChunk("""{"request_id":"sample","op":"read","stream_id":"stream"}""")
        val event = """{"value":"${"악".repeat(300)}"}"""
        var seq = 1L
        while (chunk.add(seq, event)) seq++
        val body = chunk.finish()
        assertTrue(body.toByteArray(Charsets.UTF_8).size <= QaPerformanceChunk.MAX_BYTES)
        assertTrue(body.toByteArray(Charsets.UTF_8).size + event.toByteArray(Charsets.UTF_8).size > QaPerformanceChunk.MAX_BYTES)
        assertTrue(body.contains(""""seq":${seq - 1},"event":"""))
        assertFalse(body.contains(""""seq":$seq,"event":"""))
        assertTrue(body.endsWith("}]}"))
    }
}
