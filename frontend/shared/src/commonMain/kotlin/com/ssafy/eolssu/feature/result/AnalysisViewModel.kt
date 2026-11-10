package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.data.practice.SessionSection
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.audio.RecordedTake
import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.EvaluationRepository
import com.ssafy.eolssu.data.practice.PracticeSessionRepository
import com.ssafy.eolssu.data.practice.RecordingUploads
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * ANALYSIS-01 상태. 분석이 끝나면 [evaluationId]가 채워져 결과 화면으로 간다. 실패하면 [failure]에 사유가 있다.
 * 녹음을 보내지 못하면 분석 전이라 [failure] 대신 [upload]에 사유가 있다.
 * [retryBusy]는 다시 시도를 서버가 지금 받지 못했다는 안내(503), [exit]는 이 화면을 떠나야 하는 이유다.
 */
data class AnalysisUiState(
    val song: Song? = null,
    val failure: AnalysisFailure? = null,
    val retrying: Boolean = false,
    val retryBusy: Boolean = false,
    val evaluationId: String? = null,
    val upload: UploadProblem? = null,
    val resending: Boolean = false,
    val songLookup: SongLookup = SongLookup.Loading,
    val exit: AnalysisExit? = null,
)

/** 분석 대기 화면을 떠나는 이유. */
enum class AnalysisExit {
    /** 다시 시도할 세션이 서버에 없다(404). 곡 목록으로 간다. */
    SessionGone,

    /** 로그인이 풀렸다(401). 로그인 화면으로 간다. */
    NeedsLogin,
}

/** 녹음을 보내지 못한 사유. 세션 업로드 시간이 지난 410은 [AnalysisFailure.UploadTimeout]으로 보인다. */
enum class UploadProblem(val retryable: Boolean = false) {
    CanResend(retryable = true),
    TimedOut(retryable = true),
    ConnectionFailed(retryable = true),
    ServerError(retryable = true),

    /** 413·415·422·404. 같은 파일은 다시 보내도 거부되므로 '다시 연주하기'만 준다. */
    Rejected,

    /** 녹음 파일이 기기에 없다(시스템이 캐시를 지운 경우 등). '다시 연주하기'만 준다. */
    Missing,
}

/** 같은 녹음으로 다시 분석할 수 있는 실패이고, 다시 시도하는 중이 아닌지. */
internal fun AnalysisUiState.canRetry(): Boolean = !retrying && failure?.retryable == true

/**
 * 녹음을 분석에 맡기고 2초마다 상태를 묻는다. 동작은 [AnalysisSession]에 있다.
 *
 * @param take 연습 화면에서 마친 녹음. 세션을 만들어 올린다. 없으면(녹음기가 없는 플랫폼) 예시 분석이다.
 * @param pendingSessionId 앱을 다시 켜고 보내는, 기기에 남은 녹음의 세션.
 * @param section 구간 반복에서 마친 구간 재평가 녹음의 범위와 기준 세션. 없으면 곡 전체 연주다.
 * @param uploads·sessions 녹음을 보관할 수 있는 플랫폼(현재 Android)에만 있다.
 */
class AnalysisViewModel(
    private val songId: String,
    private val bpm: Int,
    private val take: RecordedTake?,
    private val pendingSessionId: String?,
    private val songRepository: SongRepository,
    evaluationRepository: EvaluationRepository,
    uploads: RecordingUploads? = null,
    sessions: PracticeSessionRepository? = null,
    member: SignedInMember? = null,
    private val section: SessionSection? = null,
    private val initialSong: Song? = null,
) : ViewModel() {
    private val accountScope = member.screenScope(viewModelScope) { clearAccount() }
    private fun clearAccount(): Unit = session.clearAccount()
    private val session = AnalysisSession(
        accountScope,
        evaluationRepository,
        clock = TimeSource.Monotonic.markNow().let { start -> { start.elapsedNow().inWholeMilliseconds } },
        sleep = { delay(it) },
        uploads = uploads,
        sessions = sessions,
    )
    val uiState: StateFlow<AnalysisUiState> = session.state
    private var songJob: Job? = null

    init {
        loadSong()
    }

    fun retrySong() {
        if (uiState.value.songLookup in setOf(SongLookup.Missing, SongLookup.Failed)) loadSong()
    }

    private fun loadSong() {
        if (songJob?.isActive == true) return
        songJob = accountScope.launch { session.loadSong(songId, bpm, songRepository, take, pendingSessionId, section, initialSong) }
    }

    /** 분석 다시 시도. */
    fun retry() = session.retry()

    /** 녹음 다시 보내기. */
    fun resend() = session.resend()

    /** 화면이 백그라운드로 갔다. */
    fun pause() = session.pause()

    /** 화면에 돌아왔다. */
    fun resume() = session.resume()
}
