package com.ssafy.eolssu.platform.audio

import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebRecordingFilesTest {
    private val store = WebRecordingStore().apply { put("web-recording-1-1.wav", byteArrayOf(1, 2, 3)) }
    private val files = WebRecordingFiles(store)

    @Test
    fun keepMovesTheTakeToAKeptKeyAndUploadReadsItFromMemory() {
        val kept = assertNotNull(files.keep("web-recording-1-1.wav"))

        assertEquals("kept/web-recording-1-1.wav", kept)
        assertFalse(files.exists("web-recording-1-1.wav"))
        assertEquals(kept, files.keep(kept))
        assertEquals(3L, files.size(kept))
        assertContentEquals(byteArrayOf(1, 2, 3), files.open(kept).readByteArray())
        // 업로드를 다시 보낼 때도 처음부터 읽는다.
        assertContentEquals(byteArrayOf(1, 2, 3), files.open(kept).readByteArray())
    }

    @Test
    fun missingRecordingsCannotBeKeptOrSized() {
        assertNull(files.keep("web-recording-9-9.wav"))
        assertNull(files.keep("kept/web-recording-9-9.wav"))
        assertNull(files.size("kept/web-recording-9-9.wav"))
    }

    @Test
    fun deleteKeptExceptLeavesTheGivenAndUnkeptRecordings() {
        val kept = files.keep("web-recording-1-1.wav")!!
        store.put("kept/web-recording-2-2.wav", byteArrayOf(4))
        store.put("web-recording-3-3.wav", byteArrayOf(5))

        files.deleteKeptExcept(kept)

        assertTrue(files.exists(kept))
        assertFalse(files.exists("kept/web-recording-2-2.wav"))
        assertTrue(files.exists("web-recording-3-3.wav"))
    }
}
