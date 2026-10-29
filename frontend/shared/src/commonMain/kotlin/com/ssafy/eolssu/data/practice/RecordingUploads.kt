package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.audio.RecordingFiles
import com.ssafy.eolssu.core.audio.RecordingSpec
import com.ssafy.eolssu.core.storage.KeyValueStore
import kotlinx.coroutines.CancellationException

/**
 * 연습 세션 하나에 올릴 녹음. [startedAtMillis]는 세션을 시작한 시각(epoch ms)이고 업로드 만료 판정에 쓴다.
 */
data class PendingRecording(val sessionId: String, val path: String, val startedAtMillis: Long)

/** 업로드 API(PRA-02) 응답을 앱이 다룰 결과로 나눈 것. */
sealed interface UploadOutcome {
    /** 저장됨. 서버가 분석을 시작한다. */
    data object Uploaded : UploadOutcome

    /** 연결 실패나 503. 같은 세션에 다시 올릴 수 있다. */
    data object Unreachable : UploadOutcome

    /** 410. 세션 업로드 시간이 지났다. */
    data object Expired : UploadOutcome

    /** 413·415·422. 같은 파일은 다시 올려도 거부된다. */
    data object Rejected : UploadOutcome
}

/** 녹음 업로드. 실제 HTTP 구현은 FE 업로드 API 연결 Task에서 붙인다. */
interface RecordingUploader {
    /** [path]의 녹음을 세션 [sessionId]에 올린다. 연결 오류는 [UploadOutcome.Unreachable]로 돌려주거나 예외로 던진다. */
    suspend fun upload(sessionId: String, path: String): UploadOutcome
}

/** 보낸 결과. 화면은 [CanResend]에만 '녹음 다시 보내기'를 보인다. */
enum class SendResult { Sent, CanResend, Expired, Rejected }

/**
 * 녹음을 보내고, 성공할 때까지 기기에 둔다(PRA-03 '녹음 다시 보내기').
 * 한 번에 녹음 하나만 기다린다. 새 녹음을 보내면 전에 기다리던 녹음은 버린다.
 * 서버가 세션 시작 뒤 40분([RecordingSpec.UploadWindow])부터 410으로 거부하므로, 그 뒤의 녹음은 보내지 않고 지운다.
 */
class RecordingUploads(
    private val uploader: RecordingUploader,
    private val store: KeyValueStore,
    private val files: RecordingFiles,
    private val clock: () -> Long,
) {
    /** 녹음을 기기에 옮겨 둔 뒤 보낸다. 옮기지 못한 녹음은 다시 보낼 수 없어 [SendResult.Rejected]다. */
    suspend fun send(recording: PendingRecording): SendResult {
        load()?.let { discard(it) }
        val kept = files.keep(recording.path) ?: return SendResult.Rejected
        val pending = recording.copy(path = kept)
        save(pending)
        return upload(pending)
    }

    /** 기다리는 녹음을 다시 보낸다. 없거나 만료됐으면 [SendResult.Expired]. */
    suspend fun resend(): SendResult {
        val pending = pending() ?: return SendResult.Expired
        return upload(pending)
    }

    /** 다시 보낼 수 있는 녹음. 만료된 녹음은 여기서 지운다. */
    fun pending(): PendingRecording? {
        val pending = load() ?: return null
        if (expired(pending)) {
            discard(pending)
            return null
        }
        return pending
    }

    private suspend fun upload(pending: PendingRecording): SendResult {
        if (expired(pending)) {
            discard(pending)
            return SendResult.Expired
        }
        val outcome = try {
            uploader.upload(pending.sessionId, pending.path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UploadOutcome.Unreachable
        }
        return when (outcome) {
            UploadOutcome.Unreachable -> SendResult.CanResend
            UploadOutcome.Uploaded -> SendResult.Sent.also { discard(pending) }
            UploadOutcome.Expired -> SendResult.Expired.also { discard(pending) }
            UploadOutcome.Rejected -> SendResult.Rejected.also { discard(pending) }
        }
    }

    private fun expired(pending: PendingRecording): Boolean =
        clock() - pending.startedAtMillis >= RecordingSpec.UploadWindow.inWholeMilliseconds

    private fun discard(pending: PendingRecording) {
        files.delete(pending.path)
        store.putString(KEY, "")
    }

    private fun save(pending: PendingRecording) {
        store.putString(KEY, listOf(pending.sessionId, pending.startedAtMillis.toString(), pending.path).joinToString(SEPARATOR))
    }

    private fun load(): PendingRecording? {
        val parts = store.getString(KEY)?.split(SEPARATOR, limit = 3) ?: return null
        if (parts.size != 3) return null
        val startedAt = parts[1].toLongOrNull() ?: return null
        return PendingRecording(sessionId = parts[0], path = parts[2], startedAtMillis = startedAt)
    }

    private companion object {
        const val KEY = "pending_recording_upload"
        const val SEPARATOR = "\n"
    }
}
