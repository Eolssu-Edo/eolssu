package com.ssafy.eolssu.platform.audio

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosRecordingFilesTest {
    private val files = NSFileManager.defaultManager
    private val root = NSTemporaryDirectory() + "recording-files-" + NSUUID().UUIDString
    private val kept = "$root/pending"
    private val recordings = IosRecordingFiles(kept)

    @AfterTest
    fun cleanUp() {
        files.removeItemAtPath(root, error = null)
    }

    @Test
    fun keepMovesRecordingOutOfRecorderFolder() {
        val source = write("$root/recordings/practice-1.m4a")

        val path = recordings.keep(source)

        assertEquals("$kept/practice-1.m4a", path)
        assertTrue(recordings.exists(path!!))
        assertFalse(recordings.exists(source))
        assertEquals(path, recordings.keep(path))
    }

    @Test
    fun keepReturnsNullForMissingFile() {
        assertNull(recordings.keep("$root/recordings/missing.m4a"))
    }

    @Test
    fun deleteKeptExceptLeavesOnlyTheGivenRecording() {
        val first = recordings.keep(write("$root/recordings/a.m4a"))!!
        val second = recordings.keep(write("$root/recordings/b.m4a"))!!

        recordings.deleteKeptExcept(second)

        assertFalse(recordings.exists(first))
        assertTrue(recordings.exists(second))
    }

    private fun write(path: String): String {
        files.createDirectoryAtPath(path.substringBeforeLast('/'), withIntermediateDirectories = true, attributes = null, error = null)
        val data: NSData = NSString.create(string = "m4a").dataUsingEncoding(NSUTF8StringEncoding)!!
        files.createFileAtPath(path, contents = data, attributes = null)
        return path
    }
}
