package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.audio.RecordingFiles
import com.ssafy.eolssu.core.audio.RecordingSpec
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.KeyValueStore
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * 연습 세션 하나에 올릴 녹음. [startedAtMillis]는 세션을 시작한 시각(epoch ms, 서버 `started_at`)이고 업로드 만료 판정에 쓴다.
 * [songId]·[bpm]은 앱을 다시 켜고 남은 녹음을 보낼 때 분석 대기 화면이 곡을 보이는 데 쓴다.
 * [memberId]는 세션을 만든 회원이다. 소유자를 모르는 이전 녹음은 전송하거나 삭제하지 않는다.
 */
data class PendingRecording(
    val sessionId: String,
    val path: String,
    val startedAtMillis: Long,
    val songId: String = "",
    val bpm: Int = 0,
    val memberId: String? = null,
)

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

/** 녹음 업로드. HTTP 구현은 [HttpRecordingUploader]다. */
interface RecordingUploader {
    /** [path]의 녹음을 세션 [sessionId]에 올린다. 연결 오류는 [UploadOutcome.Unreachable]로 돌려주거나 예외로 던진다. */
    suspend fun upload(sessionId: String, path: String): UploadOutcome
}

/** [NotOwner]는 로그인한 회원이 세션 소유자와 달라 파일을 건드리지 않은 경우다. */
enum class SendResult { Sent, CanResend, Expired, Rejected, Missing, NotOwner }

/**
 * 기기 시계가 서버보다 빠를 수 있어, 업로드 시간이 지나도 이만큼은 파일을 지우지 않는다. 그동안 다시 보내면 서버가 410으로 판정한다.
 */
internal val LocalClockMargin: Duration = 10.minutes

/**
 * 녹음을 보내고, 성공할 때까지 기기에 둔다(PRA-03 '녹음 다시 보내기').
 * 회원마다 녹음 하나를 기다린다. 같은 회원의 새 녹음을 보내면 그 회원의 이전 녹음만 버린다.
 * 서버는 세션 시작 뒤 40분([RecordingSpec.UploadWindow])이 지나면 410으로 거부한다(정각은 아직 유효). 기기 시계로 그 시간이 지나면
 * 다시 보내기를 권하지 않고, [LocalClockMargin]이 더 지나면 파일을 지운다. 그 사이의 판정은 서버에 맡긴다.
 */
class RecordingUploads(
    private val uploader: RecordingUploader,
    private val store: KeyValueStore,
    private val files: RecordingFiles,
    private val clock: () -> Long,
    private val member: SignedInMember,
) {
    /**
     * 녹음을 보관 위치에 옮긴 뒤 보낸다. 옮기지 못하면 원래 자리에서 보낸다(그 파일은 다음 녹음 때 지워질 수 있다).
     * 파일이 없으면 보내지 않는다.
     */
    suspend fun send(recording: PendingRecording): SendResult = member.whileSignedIn { memberId ->
        if (!recording.belongsTo(memberId)) return@whileSignedIn SendResult.NotOwner
        val path = files.keep(recording.path) ?: recording.path.takeIf(files::exists) ?: return@whileSignedIn SendResult.Missing
        val pending = recording.copy(path = path)
        load(memberId)?.let { old -> if (old.path != path) files.delete(old.path) }
        save(pending)
        upload(pending)
    }

    /**
     * [recording]과 같은 세션의 기다리는 녹음을 다시 보낸다. 다른 세션의 녹음은 보내지 않는다.
     * 그 세션의 녹음이 없을 때 업로드 시간이 지났으면 [SendResult.Expired], 아니면(새 녹음으로 바뀜, 파일 사라짐) [SendResult.Missing]이다.
     */
    suspend fun resend(recording: PendingRecording): SendResult = member.whileSignedIn { memberId ->
        if (!recording.belongsTo(memberId)) return@whileSignedIn SendResult.NotOwner
        val pending = current(memberId)?.takeIf { it.sessionId == recording.sessionId }
            ?: return@whileSignedIn if (age(recording) > RecordingSpec.UploadWindow.inWholeMilliseconds) SendResult.Expired else SendResult.Missing
        upload(pending)
    }

    /** 다시 보내기를 권할 녹음. 업로드 시간이 지난 녹음은 숨기고, 지울 때가 지난 녹음은 지운다. */
    fun pending(): PendingRecording? = current(member.id)?.takeIf { age(it) <= RecordingSpec.UploadWindow.inWholeMilliseconds }

    /**
     * 현재 회원의 만료된 녹음만 정리한다. 다른 회원·소유자 없는 기존 파일은 삭제 정책이 정해질 때까지 보존한다.
     */
    fun cleanUp() {
        current(member.id)
    }

    /** 해당 회원의 녹음만 읽고 만료를 처리한다. 소유자를 확인할 수 없는 기록은 손대지 않는다. */
    private fun current(memberId: String?): PendingRecording? {
        val pending = load(memberId) ?: return null
        if (age(pending) > (RecordingSpec.UploadWindow + LocalClockMargin).inWholeMilliseconds) {
            discard(pending)
            return null
        }
        return pending
    }

    private suspend fun upload(pending: PendingRecording): SendResult {
        val path = files.locate(pending.path)
        if (path == null) {
            discard(pending)
            return SendResult.Missing
        }
        val outcome = try {
            uploader.upload(pending.sessionId, path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UploadOutcome.Unreachable
        }
        return when (outcome) {
            UploadOutcome.Unreachable -> SendResult.CanResend
            UploadOutcome.Uploaded -> SendResult.Sent.also { discard(pending, path) }
            UploadOutcome.Expired -> SendResult.Expired.also { discard(pending, path) }
            UploadOutcome.Rejected -> SendResult.Rejected.also { discard(pending, path) }
        }
    }

    private fun age(pending: PendingRecording): Long = clock() - pending.startedAtMillis

    /**
     * [pending]의 파일([path], 앱 폴더가 옮겨졌으면 찾은 경로)을 지우고, 저장된 기록이 아직 그 녹음이면 비운다.
     * 그 사이 새 녹음이 저장됐으면 그대로 둔다.
     */
    private fun discard(pending: PendingRecording, path: String = pending.path) {
        files.delete(path)
        if (load(pending.memberId) == pending) store.putString(key(checkNotNull(pending.memberId)), "")
    }

    private fun save(pending: PendingRecording) {
        val memberId = checkNotNull(pending.memberId)
        val parts = listOf(memberId, pending.sessionId, pending.startedAtMillis.toString(), pending.songId, pending.bpm.toString(), pending.path)
        store.putString(key(memberId), parts.joinToString(SEPARATOR))
    }

    /** 회원별 키와 저장된 소유자가 모두 일치해야 읽는다. 이전 전역 키는 소유자를 모르므로 그대로 보존한다. */
    private fun load(memberId: String?): PendingRecording? {
        if (memberId.isNullOrBlank()) return null
        val parts = store.getString(key(memberId))?.split(SEPARATOR, limit = 6) ?: return null
        if (parts.size != 6 || parts[0] != memberId) return null
        val startedAt = parts[2].toLongOrNull() ?: return null
        val bpm = parts[4].toIntOrNull() ?: return null
        return PendingRecording(sessionId = parts[1], path = parts[5], startedAtMillis = startedAt, songId = parts[3], bpm = bpm, memberId = memberId)
    }

    private fun PendingRecording.belongsTo(currentMemberId: String?) =
        !memberId.isNullOrBlank() && memberId == currentMemberId

    private fun key(memberId: String) = "$KEY.member.$memberId"

    private companion object {
        const val KEY = "pending_recording_upload"
        const val SEPARATOR = "\n"
    }
}
