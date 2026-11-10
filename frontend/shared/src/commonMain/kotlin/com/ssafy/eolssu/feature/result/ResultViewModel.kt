package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.diagnostics.context
import com.ssafy.eolssu.core.diagnostics.PerfReason
import com.ssafy.eolssu.core.diagnostics.PerfPhase
import com.ssafy.eolssu.core.diagnostics.PerfSource
import com.ssafy.eolssu.core.diagnostics.PerfTarget
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.EvaluationRepository
import com.ssafy.eolssu.data.practice.ScreenEvaluation
import com.ssafy.eolssu.core.network.ensureAccountSession
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.practice.PracticeRecordUnavailable
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.data.user.LearnSongPass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RESULT-02 상태.
 *
 * @property savedRecord '연습 기록 저장'으로 저장한 기록. 있으면 저장 확인 시트를 띄운다.
 * @property learnSongPassed 이 결과로 기초부터 배우기 8단계를 완료로 남겼는지. 화면이 '완료했어요!'를 닫으면 [ResultViewModel.dismissLearnSongPassed]로 지운다.
 * @property learnSongTrack 8단계를 통과한 기초부터 배우기 트랙(`GuideInstrument.id`). '다음 단계로'가 그 악기의 다음 단계를 연다. 모르면 null.
 */
data class ResultUiState(
    val result: EvaluationResult? = null,
    val song: Song? = null,
    val savedRecord: PracticeRecord? = null,
    val sheetOpen: Boolean = false,
    val savingRecord: Boolean = false,
    val recordSaveError: RecordSaveError? = null,
    val memo: String = "",
    val songLookup: SongLookup = SongLookup.Loading,
    val resultLookup: SongLookup = SongLookup.Loading,
    val scorePreview: EvaluationResult? = null,
    val learnSongPassed: Boolean = false,
    val learnSongTrack: String? = null,
)

enum class RecordSaveError { Unavailable, Failed }

class ResultViewModel(
    private val evaluationId: String,
    private val evaluationRepository: EvaluationRepository,
    private val songRepository: SongRepository,
    private val recordRepository: PracticeRecordRepository,
    member: SignedInMember? = null,
    private val judgeLearnSong: (suspend (EvaluationResult, Song) -> LearnSongPass)? = null,
    private val learnTrackOf: (suspend (Song) -> String?)? = null,
    private val initialSong: Song? = null,
    private val perf: PerformanceVisit? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()
    private var songJob: Job? = null
    private var resultJob: Job? = null
    private var learnJudged = false
    private var pendingEvaluation: ScreenEvaluation? = null

    private val accountScope = member.screenScope(viewModelScope) {
        pendingEvaluation = null
        _uiState.value = ResultUiState()
    }

    init {
        retryResult()
    }

    fun retryResult() {
        if (!accountScope.isActive) return
        if (resultJob?.isActive == true || _uiState.value.result != null) return
        _uiState.update { it.copy(resultLookup = SongLookup.Loading) }
        resultJob = accountScope.launch(perf.context(if (resultJob == null) PerfReason.INITIAL else PerfReason.RETRY)) {
            val loaded = try {
                val retained = pendingEvaluation
                if (retained != null) perf?.reuse(PerfTarget.EVALUATION, evaluationId, PerfSource.RETAINED)
                val evaluation = retained ?: evaluationRepository.resultForScreen(evaluationId, initialSong).also { fetched ->
                    ensureAccountSession()
                    pendingEvaluation = fetched
                    if (fetched != null) perf?.milestone(PerfPhase.SCORE_READY)
                    _uiState.update { it.copy(scorePreview = fetched?.scores) }
                }
                evaluation?.loadDetails?.invoke()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureAccountSession()
                _uiState.update { it.copy(resultLookup = SongLookup.Failed) }
                return@launch
            }
            ensureAccountSession()
            if (loaded == null) {
                val lookup = if (pendingEvaluation == null) SongLookup.Missing else SongLookup.Failed
                _uiState.update { it.copy(resultLookup = lookup) }
                return@launch
            }
            pendingEvaluation = null
            val (result, song) = loaded
            _uiState.update {
                it.copy(result = result, resultLookup = SongLookup.Ready, song = song,
                    songLookup = if (song == null) SongLookup.Loading else SongLookup.Ready, scorePreview = null)
            }
            if (song == null) retrySong() else markLearnSong(result, song)
        }
    }

    fun retrySong() {
        if (!accountScope.isActive) return
        val result = _uiState.value.result ?: return
        val songId = result.songId
        if (songJob?.isActive == true || _uiState.value.song != null) return
        _uiState.update { it.copy(songLookup = SongLookup.Loading) }
        songJob = accountScope.launch(perf.context(PerfReason.RETRY)) {
            val song = try {
                songRepository.getSong(songId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(songLookup = SongLookup.Failed) }
                return@launch
            }
            currentCoroutineContext().ensureActive()
            if (song != null && result.scoreVersion != null && song.scoreVersion != result.scoreVersion) {
                _uiState.update { it.copy(song = null, songLookup = SongLookup.Failed) }
                return@launch
            }
            _uiState.update { it.copy(song = song, songLookup = if (song == null) SongLookup.Missing else SongLookup.Ready) }
            if (song != null) markLearnSong(result, song)
        }
    }

    /**
     * 곡 전체 결과가 기초부터 배우기 8단계 곡의 통과 기준을 넘으면 8단계를 완료로 남긴다(S15P31E102-611). 한 결과에 한 번만 본다.
     * 판정하지 못해도 결과 화면에는 알리지 않는다(사용자는 8단계에서 직접 완료로 표시할 수 있다).
     */
    private fun markLearnSong(result: EvaluationResult, song: Song) {
        val judge = judgeLearnSong ?: return
        if (learnJudged) return
        learnJudged = true
        accountScope.launch {
            val pass = try {
                judge(result, song)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return@launch
            }
            currentCoroutineContext().ensureActive()
            if (pass != LearnSongPass.Passed) return@launch
            val track = learnTrackOf?.invoke(song)
            currentCoroutineContext().ensureActive()
            _uiState.update { it.copy(learnSongPassed = true, learnSongTrack = track) }
        }
    }

    fun dismissLearnSongPassed() = _uiState.update { it.copy(learnSongPassed = false) }

    /** 연습 기록에 저장하고 확인 시트를 연다. 같은 결과는 다시 저장하지 않는다. */
    fun save() {
        if (!accountScope.isActive) return
        val state = _uiState.value
        if (state.savingRecord) return
        val result = state.result ?: return
        val title = state.song?.title ?: return
        if (state.savedRecord != null) {
            _uiState.update { it.copy(sheetOpen = true, recordSaveError = null) }
            return
        }
        _uiState.update { it.copy(savingRecord = true, recordSaveError = null) }
        accountScope.launch {
            try {
                val record = recordRepository.saveEvaluation(result, title)
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(savedRecord = record, sheetOpen = true, memo = record.memo) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(sheetOpen = false, recordSaveError = error.toRecordSaveError()) }
            } finally {
                _uiState.update { it.copy(savingRecord = false) }
            }
        }
    }

    fun dismissRecordSaveError() = _uiState.update { it.copy(recordSaveError = null) }

    fun changeMemo(memo: String) = _uiState.update { it.copy(memo = memo) }

    /** 시트를 닫거나 기록으로 갈 때 적은 메모를 남긴다. */
    fun closeSheet() {
        if (!accountScope.isActive) return
        commitMemo()
        _uiState.update { it.copy(sheetOpen = false) }
    }

    fun commitMemo() {
        if (!accountScope.isActive) return
        val state = _uiState.value
        val record = state.savedRecord ?: return
        if (record.memo == state.memo) return
        accountScope.launch {
            try {
                recordRepository.updateMemo(record.id, state.memo)
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(savedRecord = record.copy(memo = state.memo)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(recordSaveError = error.toRecordSaveError()) }
            }
        }
    }
}

private fun Exception.toRecordSaveError(): RecordSaveError =
    if (this is PracticeRecordUnavailable) RecordSaveError.Unavailable else RecordSaveError.Failed
