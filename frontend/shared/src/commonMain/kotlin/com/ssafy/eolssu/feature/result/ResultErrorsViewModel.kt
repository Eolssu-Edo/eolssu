package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.diagnostics.context
import com.ssafy.eolssu.core.diagnostics.PerfReason
import com.ssafy.eolssu.core.ui.score.ErrorFilter
import com.ssafy.eolssu.feature.practice.toSection
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import com.ssafy.eolssu.data.song.CatalogInstrument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.WeakSectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RESULT-03 오류 위치(2차) 상태. 틀린 정간은 평가 결과(EVA-01), 취약 구간은 서버 추천(EVA-03)이다.
 *
 * @property loading 데이터를 받는 중. 끝났는데 [song]·[result]가 없으면 찾지 못한 것이다.
 * @property section 서버가 추천한 취약 구간(각 전체). 추천할 구간이 없거나 받지 못하면 null.
 * @property selectedCell 눌러 고른 정간. null이면 [shownCell]이 취약 구간의 첫 오류를 고른다.
 */
data class ResultErrorsUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val instrumentName: String? = null,
    val song: Song? = null,
    val result: EvaluationResult? = null,
    val filter: ErrorFilter = ErrorFilter.All,
    val selectedCell: ScoreCellPosition? = null,
    val section: ScoreSection? = null,
) {
    val songTitle: String? get() = song?.title?.let { title ->
        instrumentName?.let { "$title · $it" } ?: title
    }

    /** 취약 구간 안의 틀린 정간 수. */
    val sectionErrorCount: Int get() = section?.let { sec -> result?.errors?.filter { sec.contains(it.column, it.row) }?.distinctBy { it.column to it.row }?.size } ?: 0

    /** 고른 정간. 고르지 않았으면 추천 취약 구간의 첫 오류, 추천이 없으면 틀린 정간이 가장 몰린 곳의 첫 오류다. */
    val shownCell: ScoreCellPosition?
        get() = selectedCell
            ?: section?.let { sec -> result?.errors?.filter { sec.contains(it.column, it.row) }?.minWithOrNull(compareBy({ it.column }, { it.row })) }
                ?.let { ScoreCellPosition(it.column, it.row) }
            ?: result?.defaultSelectedCell()
}

class ResultErrorsViewModel(
    private val sourceId: String,
    private val repository: WeakSectionRepository,
    private val loadInstruments: suspend () -> List<CatalogInstrument> = { emptyList() },
    member: SignedInMember? = null,
    private val perf: PerformanceVisit? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ResultErrorsUiState())
    val uiState: StateFlow<ResultErrorsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private val accountScope = member.screenScope(viewModelScope) {
        _uiState.value = ResultErrorsUiState()
    }

    init { retry() }

    fun retry() {
        if (!accountScope.isActive || loadJob?.isActive == true) return
        _uiState.value = ResultErrorsUiState()
        loadJob = accountScope.launch(perf.context(if (loadJob == null) PerfReason.INITIAL else PerfReason.RETRY)) {
            val source = try {
                repository.source(sourceId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = ResultErrorsUiState(loading = false, failed = true)
                return@launch
            }
            // 같은 곡이어도 다른 세션의 점수·오류를 표시하지 않는다.
            if (source == null || source.evaluation.id != sourceId ||
                source.evaluation.songId != source.song.id ||
                (source.evaluation.scoreVersion != null && source.evaluation.scoreVersion != source.song.scoreVersion)
            ) {
                _uiState.value = ResultErrorsUiState(loading = false)
                return@launch
            }
            _uiState.value = ResultErrorsUiState(loading = false, song = source.song, result = source.evaluation)
            // 선택한 단독 평가를 먼저 표시하고, 보조 정보 실패와 분리한다.
            launch {
                val picks = optional { repository.weakSections(sourceId) }
                _uiState.update { it.copy(section = picks?.firstOrNull()?.toSection(source.song.score)) }
            }
            launch {
                val name = source.song.instrumentId?.let { id ->
                    optional { loadInstruments() }?.firstOrNull { it.instrumentId == id }?.name?.takeIf { it.isNotBlank() }
                }
                _uiState.update { it.copy(instrumentName = name) }
            }
        }
    }

    private suspend fun <T> optional(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    fun selectFilter(filter: ErrorFilter) = _uiState.update { it.copy(filter = filter) }

    fun selectCell(cell: ScoreCellPosition) = _uiState.update { it.copy(selectedCell = cell) }

}
