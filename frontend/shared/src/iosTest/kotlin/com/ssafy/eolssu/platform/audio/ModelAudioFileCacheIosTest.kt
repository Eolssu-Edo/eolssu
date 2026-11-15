package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.ModelAudioFileCache
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.util.sha256
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.write
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.playable
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import kotlin.math.PI
import kotlin.math.sin

/** iOS AVPlayer가 모범 연주 캐시 파일을 열 수 있는지 실제 AVFoundation으로 확인한다(S15P31E102-915). */
@OptIn(ExperimentalForeignApi::class)
class ModelAudioFileCacheIosTest {
    private val root = Path(NSTemporaryDirectory(), "model-audio-${NSUUID().UUIDString}")

    @AfterTest fun clean() = deleteTree(root)

    private fun deleteTree(path: Path) {
        if (!SystemFileSystem.exists(path)) return
        if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) SystemFileSystem.list(path).forEach(::deleteTree)
        SystemFileSystem.delete(path)
    }

    @Test fun cachedModelAudioIsPlayableByAVFoundation() = runTest {
        val bytes = aacBytes()
        val hash = sha256(bytes).toHexString()
        val cache = ModelAudioFileCache(Path(root, "cache"), MockEngine { respond(bytes) }, Dispatchers.IO)

        val file = assertNotNull(cache.fileFor("https://media.test/model", hash, "audio/mp4"))

        assertTrue(file.path.endsWith(".m4a"))
        assertTrue(playable(file.path), "AVFoundation must open the cached model audio")
    }

    @Test fun theSameBytesUnderTheOldExtensionCannotBeOpened() {
        // 수정 전 이름(`<sha256>.audio`)이 왜 재생되지 않았는지 남겨 두는 대조군.
        val bytes = aacBytes()
        val legacy = Path(root, "model.audio")
        SystemFileSystem.sink(legacy).buffered().use { it.write(bytes) }
        assertFalse(playable(legacy.toString()))
    }

    private fun playable(path: String): Boolean = AVURLAsset(uRL = NSURL.fileURLWithPath(path), options = null).playable

    /** 0.5초 440Hz 사인파를 녹음기와 같은 AAC m4a로 쓴다. */
    private fun aacBytes(): ByteArray {
        SystemFileSystem.createDirectories(root)
        val path = Path(root, "source-${NSUUID().UUIDString}.m4a")
        val pcm = checkNotNull(AVAudioFormat(commonFormat = AVAudioPCMFormatFloat32, sampleRate = SAMPLE_RATE, channels = 1u, interleaved = false))
        val frames = (SAMPLE_RATE / 2).toUInt()
        val buffer = AVAudioPCMBuffer(pCMFormat = pcm, frameCapacity = frames)
        buffer.frameLength = frames
        val samples = buffer.floatChannelData!![0]!!
        for (i in 0 until frames.toInt()) samples[i] = (0.3 * sin(2 * PI * 440 * i / SAMPLE_RATE)).toFloat()
        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to NSNumber(unsignedInt = kAudioFormatMPEG4AAC),
            AVSampleRateKey to SAMPLE_RATE,
            AVNumberOfChannelsKey to 1,
            AVEncoderBitRateKey to 128_000,
        )
        val writer = AVAudioFile(forWriting = NSURL.fileURLWithPath(path.toString()), settings = settings,
            commonFormat = AVAudioPCMFormatFloat32, interleaved = false, error = null)
        assertTrue(writer.writeFromBuffer(buffer, error = null))
        writer.close()
        return SystemFileSystem.source(path).buffered().use { it.readByteArray() }.also { SystemFileSystem.delete(path) }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100.0
    }
}
