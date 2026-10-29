package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.audio.RecordingFiles
import com.ssafy.eolssu.core.audio.RecordingSpec
import com.ssafy.eolssu.core.storage.KeyValueStore
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * 연습 세션 하나에 올릴 녹음. [startedAtMillis]는 세션을 시작한 시각(epoch ms, 서버 `started_at`)이고 업로드 만료 판정에 쓴다.
 */
data class PendingRecording(val sessionId: String, val path: String, val startedAtMillis: Long)

/**
 * 업로드 API(PRA-02 `docs/specs/recording-file.md`) 응답을 앱이 다룰 결과로 나눈 것.
 * 202와, 응답을 놓친 재전송이 받는 409(이미 저장됨)는 [Uploaded]다. 연결 실패·503·500은 같은 세션에 다시 올릴 수 있어 [Unreachable],
 * 410은 [Expired], 413·415·422와 404(세션 없음)는 같은 파일·세션으로 다시 보내도 안 되므로 [Rejected]다.
 * [Rejected]의 화면 문구는 파일과 세션 어느 쪽 탓으로도 읽히지 않게 쓴다.
 */
sealed interface UploadOutcome {
    /** 저장됨(202, 409). 서버가 분석을 시작했거나 이미 시작했다. 상태 조회로 결과를 확인한다. */
    data object Uploaded : UploadOutcome

    /** 연결 실패, 503, 500. 같은 세션에 다시 올릴 수 있다. */
    data object Unreachable : UploadOutcome

    /** 410. 세션 업로드 시간이 지났다. */
    data object Expired : UploadOutcome

    /** 413·415·422·404. 다시 보내도 거부된다. */
    data object Rejected : UploadOutcome
}

/** 녹음 업로드. 실제 HTTP 구현은 FE 업로드 API 연결 Task(S15P31E102-245)에서 [UploadOutcome]의 대응대로 붙인다. */
interface RecordingUploader {
    /** [path]의 녹음을 세션 [sessionId]에 올린다. 연결 오류는 [UploadOutcome.Unreachable]로 돌려주거나 예외로 던진다. */
    suspend fun upload(sessionId: String, path: String): UploadOutcome
}

/** 보낸 결과. 화면은 [CanResend]에만 '녹음 다시 보내기'를 보인다. [Missing]은 녹음 파일이 기기에 없는 경우다. */
enum class SendResult { Sent, CanResend, Expired, Rejected, Missing }

/**
 * 기기 시계가 서버보다 빠를 수 있어, 업로드 시간이 지나도 이만큼은 파일을 지우지 않는다. 그동안 다시 보내면 서버가 410으로 판정한다.
 */
internal val LocalClockMargin: Duration = 10.minutes

/**
 * 녹음을 보내고, 성공할 때까지 기기에 둔다(PRA-03 '녹음 다시 보내기').
 * 한 번에 녹음 하나만 기다린다. 새 녹음을 보내면 전에 기다리던 녹음은 버린다.
 * 서버는 세션 시작 뒤 40분([RecordingSpec.UploadWindow])이 지나면 410으로 거부한다(정각은 아직 유효). 기기 시계로 그 시간이 지나면
 * 다시 보내기를 권하지 않고, [LocalClockMargin]이 더 지나면 파일을 지운다. 그 사이의 판정은 서버에 맡긴다.
 */
class RecordingUploads(
    private val uploader: RecordingUploader,
    private val store: KeyValueStore,
    private val files: RecordingFiles,
    private val clock: () -> Long,
) {
    /**
     * 녹음을 보관 위치에 옮긴 뒤 보낸다. 옮기지 못하면 원래 자리에서 보낸다(그 파일은 다음 녹음 때 지워질 수 있다).
     * 파일이 없으면 보내지 않는다.
     */
    suspend fun send(recording: PendingRecording): SendResult {
        val path = files.keep(recording.path) ?: recording.path.takeIf(files::exists) ?: return SendResult.Missing
        val pending = recording.copy(path = path)
        load()?.let { old -> if (old.path != path) files.delete(old.path) }
        files.deleteKeptExcept(path)
        save(pending)
        return upload(pending)
    }

    /**
     * [recording]과 같은 세션의 기다리는 녹음을 다시 보낸다. 다른 세션의 녹음은 보내지 않는다.
     * 그 세션의 녹음이 없을 때 업로드 시간이 지났으면 [SendResult.Expired], 아니면(새 녹음으로 바뀜, 파일 사라짐) [SendResult.Missing]이다.
     */
    suspend fun resend(recording: PendingRecording): SendResult {
        val pending = current()?.takeIf { it.sessionId == recording.sessionId }
            ?: return if (age(recording) > RecordingSpec.UploadWindow.inWholeMilliseconds) SendResult.Expired else SendResult.Missing
        return upload(pending)
    }

    /** 다시 보내기를 권할 녹음. 업로드 시간이 지난 녹음은 숨기고, 지울 때가 지난 녹음은 지운다. */
    fun pending(): PendingRecording? = current()?.takeIf { age(it) <= RecordingSpec.UploadWindow.inWholeMilliseconds }

    /**
     * 앱을 켤 때 보내기 전에 한 번 부른다. 기다리는 녹음이 아닌 보관 파일(보관과 기록 저장 사이에 앱이 꺼져 남은 파일)을 치운다.
     * 읽기에서는 치우지 않는다. [send]가 보관한 뒤 기록을 저장하기 전에 읽으면 방금 보관한 파일을 지우게 된다.
     */
    fun cleanUp() {
        files.deleteKeptExcept(current()?.path)
    }

    /** 저장된 녹음. 기록이 깨졌으면 비우고, 지울 때가 지났으면 지운다. */
    private fun current(): PendingRecording? {
        val pending = load()
        if (pending == null) {
            if (!store.getString(KEY).isNullOrEmpty()) store.putString(KEY, "")
            return null
        }
        if (age(pending) > (RecordingSpec.UploadWindow + LocalClockMargin).inWholeMilliseconds) {
            discard(pending)
            return null
        }
        return pending
    }

    private suspend fun upload(pending: PendingRecording): SendResult {
        if (!files.exists(pending.path)) {
            discard(pending)
            return SendResult.Missing
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

    private fun age(pending: PendingRecording): Long = clock() - pending.startedAtMillis

    /** [pending]의 파일을 지우고, 저장된 기록이 아직 그 녹음이면 비운다. 그 사이 새 녹음이 저장됐으면 그대로 둔다. */
    private fun discard(pending: PendingRecording) {
        files.delete(pending.path)
        if (load() == pending) store.putString(KEY, "")
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
