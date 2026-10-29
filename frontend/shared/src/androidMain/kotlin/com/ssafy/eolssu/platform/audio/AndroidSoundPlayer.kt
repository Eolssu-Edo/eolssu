package com.ssafy.eolssu.platform.audio

import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer
import com.ssafy.eolssu.core.audio.SoundPlayer
import eolssu.shared.generated.resources.Res
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 리소스 파일을 메모리로 읽어 `MediaPlayer`로 튼다. 다 나면 바로 놓아준다. */
class AndroidSoundPlayer : SoundPlayer {
    private var player: MediaPlayer? = null

    override suspend fun play(path: String): Boolean {
        stop()
        val bytes = try {
            Res.readBytes(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        // 읽는 동안 창을 닫았으면 틀지 않는다. 여기서부터는 멈추지 않고 이어지므로 stop()과 엇갈리지 않는다.
        currentCoroutineContext().ensureActive()
        val mediaPlayer = MediaPlayer()
        return try {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
            )
            mediaPlayer.setDataSource(ByteArraySource(bytes))
            mediaPlayer.prepare()
            mediaPlayer.setOnCompletionListener { finished ->
                if (player === finished) player = null
                finished.release()
            }
            mediaPlayer.start()
            player = mediaPlayer
            true
        } catch (e: Exception) {
            mediaPlayer.release()
            false
        }
    }

    override fun stop() {
        val current = player ?: return
        player = null
        current.release()
    }
}

private class ByteArraySource(private val bytes: ByteArray) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= bytes.size) return -1
        val count = minOf(size, bytes.size - position.toInt())
        bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + count)
        return count
    }

    override fun getSize(): Long = bytes.size.toLong()

    override fun close() = Unit
}
