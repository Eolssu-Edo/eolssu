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
import com.ssafy.eolssu.data.practice.WeakSectionPick
import com.ssafy.eolssu.data.practice.WeakSectionRepository
import com.ssafy.eolssu.core.ui.score.ErrorFilter
import com.ssafy.eolssu.data.practice.PracticeRecordUnavailable
import com.ssafy.eolssu.data.song.SongRepository
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
 * 연주 결과(RESULT-02·03을 합친 한 화면) 상태.
 *
 * @property savedRecord ‘연습 기록 저장’ → ‘저장’으로 저장한 기록. 있으면 아래 버튼이 ‘저장됨’이 된다.
 * @property saveSheet 저장 시트 단계. null이면 닫혀 있다.
 * @property memo 저장 시트에 적는 메모(선택). 저장할 때 함께 보낸다.
 * @property filter 정간보에 칠할 틀린 종류.
 * @property weakSection 서버가 추천한 취약 구간(EVA-03 첫 구간). 없거나 받지 못하면 null.
 * @property leaveWarning 저장하지 않은 결과를 두고 뒤로 가려 할 때 띄우는 ‘연주 결과를 버릴까요?’ 창(S15P31E102-1214).
 */
data class ResultUiState(
    val result: EvaluationResult? = null,
    val song: Song? = null,
    val savedRecord: PracticeRecord? = null,
    val saveSheet: SaveSheet? = null,
    val savingRecord: Boolean = false,
    val recordSaveError: RecordSaveError? = null,
    val memo: String = "",
    val songLookup: SongLookup = SongLookup.Loading,
    val resultLookup: SongLookup = SongLookup.Loading,
    val scorePreview: EvaluationResult? = null,
    val filter: ErrorFilter = ErrorFilter.All,
    val weakSection: WeakSectionPick? = null,
    val leaveWarning: Boolean = false,
) {
    /** 저장할 수 있는 결과가 보이는데 아직 저장하지 않았으면 뒤로 가기 전에 확인한다. 저장을 마쳤거나 결과·곡을 못 받았으면 바로 나간다. */
    val confirmsLeave: Boolean get() = result != null && song != null && savedRecord == null
}

/** 저장 시트 두 단계: [Form] 곡·점수 카드와 메모, ‘저장’ 버튼 → [Saved] ‘연습 기록에 저장했어요’만. */
enum class SaveSheet { Form, Saved }

enum class RecordSaveError { Unavailable, Failed }

class ResultViewModel(
    private val evaluationId: String,
    private val evaluationRepository: EvaluationRepository,
    private val songRepository: SongRepository,
    private val recordRepository: PracticeRecordRepository,
    member: SignedInMember? = null,
    private val initialSong: Song? = null,
    private val perf: PerformanceVisit? = null,
    private val weakSections: WeakSectionRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()
    private var songJob: Job? = null
    private var resultJob: Job? = null
    private var weakJob: Job? = null
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
            if (song == null) retrySong()
            loadWeakSection()
        }
    }

    /** 취약 구간 추천(EVA-03). 받지 못해도 결과 표시는 그대로 두고 취약 구간 줄만 숨긴다. */
    private fun loadWeakSection() {
        val repository = weakSections ?: return
        if (weakJob?.isActive == true || _uiState.value.weakSection != null) return
        weakJob = accountScope.launch {
            val pick = try {
                repository.weakSections(evaluationId)?.firstOrNull()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            currentCoroutineContext().ensureActive()
            _uiState.update { it.copy(weakSection = pick) }
        }
    }

    fun selectFilter(filter: ErrorFilter) = _uiState.update { it.copy(filter = filter) }

    fun retrySong() {
        if (!accountScope.isActive) return
        val result = _uiState.value.result ?: return
        val songId = result.songId
        if (songJob?.isActive == true || _uiState.value.song != null) return
        _uiState.update { it.copy(songLookup = SongLookup.Loading) }
        songJob = accountScope.launch(perf.context(PerfReason.RETRY)) {
            val song = try {
                if (result.scoreVersion == null) songRepository.getSong(songId) else songRepository.getSong(songId, result.scoreVersion)
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
        }
    }

    /**
     * ‘연습 기록 저장’. 아직 저장하지 않았으면 메모를 적는 시트(①)를, 저장했으면 ‘연습 기록에 저장했어요’(②)만 띄운다.
     */
    fun save() {
        if (!accountScope.isActive) return
        val state = _uiState.value
        if (state.savingRecord || state.result == null || state.song == null) return
        _uiState.update { it.copy(saveSheet = if (it.savedRecord != null) SaveSheet.Saved else SaveSheet.Form, recordSaveError = null) }
    }

    /** 시트의 ‘저장’: 메모와 함께 한 번에 저장한다(REC-01). 같은 결과는 다시 저장하지 않는다. */
    fun confirmSave() {
        if (!accountScope.isActive) return
        val state = _uiState.value
        if (state.savingRecord || state.saveSheet != SaveSheet.Form) return
        val result = state.result ?: return
        val title = state.song?.title ?: return
        if (state.savedRecord != null) {
            _uiState.update { it.copy(saveSheet = SaveSheet.Saved) }
            return
        }
        val memo = state.memo.trim()
        _uiState.update { it.copy(savingRecord = true, recordSaveError = null) }
        accountScope.launch {
            try {
                val record = recordRepository.saveEvaluation(result, title, memo)
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(savedRecord = record, saveSheet = SaveSheet.Saved, memo = record.memo) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                // 시트와 적은 메모는 그대로 두어 다시 ‘저장’할 수 있게 한다.
                _uiState.update { it.copy(recordSaveError = error.toRecordSaveError()) }
            } finally {
                _uiState.update { it.copy(savingRecord = false) }
            }
        }
    }

    fun dismissRecordSaveError() = _uiState.update { it.copy(recordSaveError = null) }

    fun changeMemo(memo: String) = _uiState.update { if (it.savedRecord == null) it.copy(memo = memo) else it }

    /** 뒤로 가기. 바로 나가도 되면 true, 저장하지 않은 결과라 확인 창을 띄웠으면 false다(S15P31E102-1214). */
    fun requestLeave(): Boolean {
        if (!_uiState.value.confirmsLeave) return true
        _uiState.update { it.copy(leaveWarning = true) }
        return false
    }

    /** 확인 창을 닫고 결과 화면에 머문다. ‘버리기’로 나갈 때도 창을 먼저 닫는다. */
    fun dismissLeaveWarning() = _uiState.update { it.copy(leaveWarning = false) }

    /** 시트 바깥을 누르거나 닫으면 닫는다. 저장 중에는 결과가 올 때까지 둔다. */
    fun closeSheet() = _uiState.update { if (it.savingRecord) it else it.copy(saveSheet = null) }
}

private fun Exception.toRecordSaveError(): RecordSaveError =
    if (this is PracticeRecordUnavailable) RecordSaveError.Unavailable else RecordSaveError.Failed
