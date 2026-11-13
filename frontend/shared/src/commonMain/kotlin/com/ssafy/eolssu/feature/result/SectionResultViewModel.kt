package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.ui.score.ErrorFilter
import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.diagnostics.context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.model.notesSection
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import com.ssafy.eolssu.data.practice.CheckLoad
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.practice.PracticeRecordUnavailable
import com.ssafy.eolssu.data.practice.SectionEvaluation
import com.ssafy.eolssu.data.practice.WeakSectionRepository
import com.ssafy.eolssu.feature.practice.toSection
import com.ssafy.eolssu.data.practice.WeakSectionPick
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RESULT-04 구간 재평가 결과 상태. 끝났는데 [evaluation]이 없으면 찾지 못한 것이다.
 * 저장 칸([savedRecord]·[saveSheet]·[savingRecord]·[recordSaveError]·[memo])은 연주 결과([ResultUiState])와 같은 뜻이다.
 */
data class SectionResultUiState(
    val loading: Boolean = true,
    val evaluation: SectionEvaluation? = null,
    /** 구간 정간보에 보일 틀린 종류(연주 결과와 같은 5칸 필터). */
    val filter: ErrorFilter = ErrorFilter.All,
    val savedRecord: PracticeRecord? = null,
    val saveSheet: SaveSheet? = null,
    val savingRecord: Boolean = false,
    val recordSaveError: RecordSaveError? = null,
    val memo: String = "",
) {
    /** 평가 결과(EVA-01)를 받아야 저장할 수 있다. 저장은 이 구간 세션을 연습 기록으로 남긴다(REC-01). */
    val canSave: Boolean
        get() = evaluation?.result != null

    /** 재평가한 구간(각 전체). 서버 비교(EVA-04)가 알려 준 이번 세션 구간이고, 비교를 받지 못하면 null. */
    val section: ScoreSection?
        get() {
            val evaluation = evaluation ?: return null
            val check = (evaluation.check as? CheckLoad.Loaded)?.check ?: return null
            val start = check.startColumn ?: return null
            val end = check.endColumn ?: return null
            return WeakSectionPick(start, end, 0).toSection(evaluation.song.score)
        }
}

/**
 * 실제로 평가된 정간: 이번 평가의 음 판정(EVA-01, 세션 음 범위 전체)이 걸친 첫 음부터 마지막 음(이음 포함)까지.
 * 고른 구간 앞뒤의 쉼·빈 정간과 [SectionResultUiState.section]의 나머지 각 정간은 넣지 않는다. 음 판정이 없으면 null.
 */
internal fun SectionEvaluation.playedSection(): ScoreSection? {
    val notes = result?.notes.orEmpty().map { it.noteIndex }
    if (notes.isEmpty()) return null
    return song.notesSection(notes.min()..notes.max())
}

/**
 * 구간만 녹음한 재평가 세션 [sessionId]의 결과(EVA-01)와 바탕 세션과의 비교(EVA-04)를 받는다.
 * ‘연습 기록 저장’은 연주 결과와 같은 두 단계 시트로 이 세션을 저장한다(S15P31E102-832). 서버는 분석이 끝난 세션이면
 * 구간 세션도 저장하고, 기록 목록은 구간 기록(각 범위)으로 보인다.
 * 화면을 연 뒤 계정이 바뀌면 진행 중인 일을 멈추고 비운다(연주 결과와 같음).
 */
class SectionResultViewModel(
    sessionId: String,
    repository: WeakSectionRepository,
    private val perf: PerformanceVisit? = null,
    private val recordRepository: PracticeRecordRepository? = null,
    member: SignedInMember? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SectionResultUiState())
    val uiState: StateFlow<SectionResultUiState> = _uiState.asStateFlow()

    private val accountScope = member.screenScope(viewModelScope) {
        _uiState.value = SectionResultUiState(loading = false)
    }

    init {
        accountScope.launch(perf.context()) {
            val evaluation = repository.sectionResult(sessionId)
            currentCoroutineContext().ensureActive()
            _uiState.update { it.copy(loading = false, evaluation = evaluation) }
        }
    }

    fun selectFilter(filter: ErrorFilter) = _uiState.update { it.copy(filter = filter) }

    /** ‘연습 기록 저장’. 아직 저장하지 않았으면 메모를 적는 시트(①)를, 저장했으면 ‘연습 기록에 저장했어요’(②)만 띄운다. */
    fun save() {
        if (!accountScope.isActive || recordRepository == null) return
        val state = _uiState.value
        if (state.savingRecord || !state.canSave) return
        _uiState.update { it.copy(saveSheet = if (it.savedRecord != null) SaveSheet.Saved else SaveSheet.Form, recordSaveError = null) }
    }

    /** 시트의 ‘저장’: 메모와 함께 한 번에 저장한다(REC-01). 같은 결과는 다시 저장하지 않는다. */
    fun confirmSave() {
        val records = recordRepository ?: return
        if (!accountScope.isActive) return
        val state = _uiState.value
        if (state.savingRecord || state.saveSheet != SaveSheet.Form) return
        val evaluation = state.evaluation ?: return
        val result = evaluation.result ?: return
        if (state.savedRecord != null) {
            _uiState.update { it.copy(saveSheet = SaveSheet.Saved) }
            return
        }
        val memo = state.memo.trim()
        _uiState.update { it.copy(savingRecord = true, recordSaveError = null) }
        accountScope.launch {
            try {
                val record = records.saveEvaluation(result, evaluation.song.title, memo)
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(savedRecord = record, saveSheet = SaveSheet.Saved, memo = record.memo) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                // 시트와 적은 메모는 그대로 두어 다시 ‘저장’할 수 있게 한다.
                _uiState.update { it.copy(recordSaveError = if (error is PracticeRecordUnavailable) RecordSaveError.Unavailable else RecordSaveError.Failed) }
            } finally {
                _uiState.update { it.copy(savingRecord = false) }
            }
        }
    }

    fun dismissRecordSaveError() = _uiState.update { it.copy(recordSaveError = null) }

    fun changeMemo(memo: String) = _uiState.update { if (it.savedRecord == null) it.copy(memo = memo) else it }

    /** 시트 바깥을 누르거나 닫으면 닫는다. 저장 중에는 결과가 올 때까지 둔다. */
    fun closeSheet() = _uiState.update { if (it.savingRecord) it else it.copy(saveSheet = null) }
}
