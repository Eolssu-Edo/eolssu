package com.ssafy.eolssu.feature.history

import androidx.compose.runtime.CompositionLocalProvider
import com.ssafy.eolssu.core.diagnostics.LocalPerformanceVisit
import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import eolssu.shared.generated.resources.record_play_unavailable
import eolssu.shared.generated.resources.record_play_busy
import eolssu.shared.generated.resources.record_play_failed
import eolssu.shared.generated.resources.record_detail_failed
import eolssu.shared.generated.resources.record_delete_busy
import eolssu.shared.generated.resources.record_delete_failed
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.data.practice.GrowthRepository
import eolssu.shared.generated.resources.Res
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * RECORD-01 연결. [justSavedId]는 결과 화면에서 방금 저장한 기록.
 * 태블릿 가로·넓은 웹 창에서는 오른쪽에서 고른 기록의 결과와 녹음을 함께 보여준다.
 * [initialRecordId]는 가로 창에서 기록 상세 경로로 들어왔을 때 처음 고를 기록이다.
 */
@Composable
fun RecordListRoute(
    justSavedId: String?,
    onBack: () -> Unit,
    onOpenGrowth: () -> Unit,
    onOpenRecord: (String) -> Unit,
    initialRecordId: String? = null,
    onSelectSection: (recordId: String) -> Unit = {},
    /** ‘구간 연습’ → 구간 고르기(반복 횟수·속도) → 구간 연습(S15P31E102-794). */
    onPracticeSection: (recordId: String, songId: String) -> Unit = { _, _ -> },
    onCompare: (recordId: String, previousId: String) -> Unit = { _, _ -> },
    /** 저장한 연주가 없을 때 '연습하러 가기'(S15P31E102-1031). */
    onStartPractice: () -> Unit = {},
    /** 두 칸 배치의 오른쪽 상세에서 기록을 지웠을 때. 지운 기록 ID를 넘긴다. */
    onRecordDeleted: (recordId: String) -> Unit = {},
    perf: PerformanceVisit? = null,
    viewModel: RecordListViewModel = koinViewModel(key = "records-$justSavedId") { parametersOf(justSavedId, perf) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 처음 한 번만 고른다. 화면을 다시 그려도(회전 등) 사용자가 그 뒤에 고른 기록을 덮지 않는다.
    var initialShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialRecordId) {
        if (initialRecordId != null && !initialShown) {
            viewModel.show(initialRecordId)
            initialShown = true
        }
    }
    CompositionLocalProvider(LocalPerformanceVisit provides perf) {
        val widthClass = currentWindowLayout().widthClass
        if (widthClass == WindowWidthClass.Expanded) {
            RecordsTwoPaneRoute(state, justSavedId, onBack, onOpenGrowth, viewModel, onCompare, onSelectSection, onPracticeSection, onStartPractice, onRecordDeleted)
        } else {
            RecordListScreen(
                state, widthClass, onBack, onOpenGrowth, viewModel::toggle,
                onOpenRecord = { id ->
                    viewModel.rememberDetailPreview(id)
                    onOpenRecord(id)
                },
                onRetry = viewModel::retry, onLoadMore = viewModel::loadMoreGroup, onInstrument = viewModel::changeInstrument,
                onToggleDay = viewModel::toggleDay,
                onToggleName = viewModel::toggleNameOrder,
                onSearch = viewModel::search,
                onStartPractice = onStartPractice,
            )
        }
    }
}

/**
 * 두 칸 배치. 오른쪽 상세는 ViewModel 하나가 고른 기록을 [RecordDetailViewModel.show]로 바꿔 보여주며, 바꿀 때 재생을 멈추고 처음 위치로 돌린다.
 * 두 칸 배치를 벗어나면(화면 이동, 세로로 돌림) 재생을 멈춘다.
 */
@Composable
private fun RecordsTwoPaneRoute(
    state: RecordListUiState,
    justSavedId: String?,
    onBack: () -> Unit,
    onOpenGrowth: () -> Unit,
    viewModel: RecordListViewModel,
    onCompare: (recordId: String, previousId: String) -> Unit,
    onSelectSection: (String) -> Unit,
    onPracticeSection: (recordId: String, songId: String) -> Unit,
    onStartPractice: () -> Unit,
    onRecordDeleted: (recordId: String) -> Unit,
) {
    val selectedId = state.selectedRecordId
    val detailViewModel: RecordDetailViewModel = koinViewModel(key = "records-pane-$justSavedId") { parametersOf("") }
    LaunchedEffect(detailViewModel, selectedId) {
        selectedId?.let(viewModel::rememberDetailPreview)
        detailViewModel.show(selectedId.orEmpty())
    }
    DisposableEffect(detailViewModel) {
        onDispose { detailViewModel.pause() }
    }
    val detail by detailViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(detail.record?.id, detail.detailLoaded) {
        if (detail.detailLoaded) detail.record?.let(viewModel::revealLinkedRecord)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    RecordNoticeEffect(detail.notice, snackbarHostState, detailViewModel::consumeNotice)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { detailViewModel.pause() }
    RecordDeleteEffect(detail.delete, snackbarHostState, detailViewModel::dismissDeleteError) {
        detail.record?.id?.let(onRecordDeleted)
    }
    RecordListScreen(
        state = state,
        widthClass = WindowWidthClass.Expanded,
        onBack = onBack,
        onOpenGrowth = onOpenGrowth,
        onToggle = viewModel::toggle,
        onOpenRecord = viewModel::select,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMoreGroup,
        onInstrument = viewModel::changeInstrument,
        onToggleDay = viewModel::toggleDay,
        onToggleName = viewModel::toggleNameOrder,
        onSearch = viewModel::search,
        onStartPractice = onStartPractice,
        detail = detail,
        detailActions = rememberRecordDetailActions(detailViewModel, onBack, onCompare, onSelectSection, onPracticeSection),
        snackbarHostState = snackbarHostState,
    )
}

/**
 * RECORD-02 연결.
 *
 * @param onCompare 기록 고르기 시트에서 고른 이전 기록과 비교 → COMPARE-01(2차).
 */
@Composable
fun RecordDetailRoute(
    recordId: String,
    onBack: () -> Unit,
    onCompare: (recordId: String, previousId: String) -> Unit = { _, _ -> },
    onSelectSection: (recordId: String) -> Unit = {},
    /** ‘구간 연습’ → 구간 고르기(반복 횟수·속도) → 구간 연습(S15P31E102-794). */
    onPracticeSection: (recordId: String, songId: String) -> Unit = { _, _ -> },
    onDeleted: () -> Unit = onBack,
    viewModel: RecordDetailViewModel = koinViewModel(key = recordId) { parametersOf(recordId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    RecordNoticeEffect(state.notice, snackbarHostState, viewModel::consumeNotice)
    DisposableEffect(viewModel) { onDispose { viewModel.pause() } }
    RecordDeleteEffect(state.delete, snackbarHostState, viewModel::dismissDeleteError, onDeleted)
    // 앱을 벗어나면(홈 화면·전화) 녹음 재생을 멈춘다.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }
    RecordDetailScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        actions = rememberRecordDetailActions(viewModel, onBack, onCompare, onSelectSection, onPracticeSection),
    )
}

/** 삭제 성공은 화면별로 처리하고, 실패는 현재 화면의 스낵바로 알린다. */
@Composable
private fun RecordDeleteEffect(
    delete: RecordDeleteState,
    snackbarHostState: SnackbarHostState,
    dismissError: () -> Unit,
    onDeleted: () -> Unit = {},
) {
    val message = when (delete) {
        RecordDeleteState.Busy -> stringResource(Res.string.record_delete_busy)
        RecordDeleteState.Failed -> stringResource(Res.string.record_delete_failed)
        else -> null
    }
    val latestOnDeleted by rememberUpdatedState(onDeleted)
    LaunchedEffect(delete) {
        if (delete == RecordDeleteState.Deleted) latestOnDeleted()
        else if (message != null) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
            dismissError()
        }
    }
}

/** 기록 상세의 한 번짜리 안내를 스낵바로 보이고 지운다. */
@Composable
private fun RecordNoticeEffect(notice: RecordNotice?, snackbarHostState: SnackbarHostState, onShown: () -> Unit) {
    val message = notice?.let {
        stringResource(
            when (it) {
                RecordNotice.PlaybackUnavailable -> Res.string.record_play_unavailable
                RecordNotice.PlaybackBusy -> Res.string.record_play_busy
                RecordNotice.PlaybackFailed -> Res.string.record_play_failed
                RecordNotice.DetailFailed -> Res.string.record_detail_failed
            },
        )
    }
    LaunchedEffect(notice) {
        if (message == null) return@LaunchedEffect
        onShown()
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(message)
    }
}

/** 기록 상세 동작. 뒤로 가기·비교로 가기 전에 재생을 멈추고, 비교는 지금 보이는 기록과 시트에서 고른 이전 기록으로 연다. */
@Composable
private fun rememberRecordDetailActions(
    viewModel: RecordDetailViewModel,
    onBack: () -> Unit,
    onCompare: (recordId: String, previousId: String) -> Unit,
    onSelectSection: (recordId: String) -> Unit,
    onPracticeSection: (recordId: String, songId: String) -> Unit,
): RecordDetailActions {
    val latestOnBack by rememberUpdatedState(onBack)
    val latestOnCompare by rememberUpdatedState(onCompare)
    val latestOnSelectSection by rememberUpdatedState(onSelectSection)
    val latestOnPracticeSection by rememberUpdatedState(onPracticeSection)
    return remember(viewModel) {
        RecordDetailActions(
            onBack = {
                viewModel.pause()
                latestOnBack()
            },
            onTogglePlay = viewModel::togglePlay,
            onSeek = viewModel::seek,
            onSeekMillis = viewModel::seekToMillis,
            onPlayAt = viewModel::playAt,
            onSkip = viewModel::skipSeconds,
            onSelectFilter = viewModel::selectFilter,
            onOpenMemo = viewModel::openMemo,
            onCloseMemo = viewModel::closeMemo,
            onEditMemo = viewModel::editMemo,
            onCancelMemo = viewModel::cancelMemoEdit,
            onSaveMemo = viewModel::saveMemo,
            onAskDelete = viewModel::askDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onCancelDelete = viewModel::cancelDelete,
            onPractice = {
                val state = viewModel.uiState.value.takeIf { it.canPractice }
                val id = state?.record?.id
                val songId = state?.song?.id
                if (id != null && songId != null) {
                    viewModel.pause()
                    latestOnPracticeSection(id, songId)
                }
            },
            onReevaluate = {
                viewModel.uiState.value.takeIf { it.canPractice }?.record?.id?.let { id ->
                    viewModel.pause()
                    latestOnSelectSection(id)
                }
            },
            onFollowNow = viewModel::followNow,
            onDetachFollow = viewModel::detachFollow,
            onRetrySong = viewModel::retrySong,
            onCompare = viewModel::openComparePick,
            onCloseCompare = viewModel::closeComparePick,
            onPickCompare = { previousId ->
                viewModel.uiState.value.record?.id?.let { recordId ->
                    viewModel.pause()
                    viewModel.closeComparePick()
                    latestOnCompare(recordId, previousId)
                }
            },
        )
    }
}
/**
 * GROWTH-01 상태(S15P31E102-795). 처음에 오늘까지 1년(GRO-02 최대 366일)의 날짜별 기록을 한 번 받아 기간 그래프와 연습량 달력을 모두 그린다.
 * 그 1년 안에 기록이 있으면 더 이전 1년씩 이어서 받아 첫 기록 날을 찾는다(기록이 없는 1년을 만나거나 받지 못하면 멈춘다).
 * 기간·단위·날짜를 바꿔도 다시 받지 않는다.
 */
class GrowthViewModel(
    private val repository: GrowthRepository,
    now: () -> Long,
    member: SignedInMember? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GrowthUiState(today = KoreanDateTime.fromEpochMillis(now())))
    val uiState: StateFlow<GrowthUiState> = _uiState.asStateFlow()

    private val accountScope = member.screenScope(viewModelScope) {
        _uiState.value = GrowthUiState(today = _uiState.value.today)
    }

    init {
        load()
    }

    fun retry() = load()

    fun changeRange(range: GrowthRange) = _uiState.update { it.copy(range = range) }

    fun changeMetric(metric: GrowthMetric) = _uiState.update { it.copy(metric = metric) }

    /** 돋보기: 작게 보기(주별 8주 · 월별 12개월) ↔ 크게 보기(주별 4주 · 월별 6개월). */
    fun toggleDense() = _uiState.update { it.copy(dense = !it.dense) }

    /** 기간 시트에서 고른 날. 그 날이 든 주·달이 그래프의 마지막 막대가 되고, 연습량 달력도 그 달·그 날로 옮긴다. */
    fun pickAnchor(day: Long) = _uiState.update {
        if (it.history == null || !it.canPickAnchor(day)) it
        else it.copy(anchorDay = day, heatMonth = GrowthDays.monthStart(day), heatDay = day)
    }

    /** 연습량 달력의 달을 [months]만큼 넘긴다. 첫 기록이 든 달 ~ 이번 달 밖으로는 넘기지 않는다. */
    fun moveHeatMonth(months: Int) = _uiState.update {
        val next = GrowthDays.addMonths(it.heatMonth, months)
        if (it.history == null || !it.canShowMonth(next)) it else it.copy(heatMonth = next)
    }

    /** 연습량 달력에서 고른 날. 오늘 뒤 날은 고를 수 없다. */
    fun pickHeatDay(day: Long) = _uiState.update { if (it.history == null || day > it.todayDay) it else it.copy(heatDay = day) }

    private fun load() {
        if (!accountScope.isActive) return
        if (loadJob?.isActive == true) return
        _uiState.update { it.copy(load = GrowthLoad.Loading) }
        loadJob = accountScope.launch {
            val to = _uiState.value.todayDay
            val from = to - (GrowthRepository.MAX_CALENDAR_DAYS - 1)
            val days = repository.calendar(from, to)
            if (days == null) {
                _uiState.update { it.copy(load = GrowthLoad.Failed) }
                return@launch
            }
            var history = PracticeHistory.of(days, from, searchingOlder = days.any { it.hasRecord })
            _uiState.update { it.copy(load = GrowthLoad.Loaded(history)) }
            var olderTo = from - 1
            var found = history.searchingOlder
            repeat(MAX_OLDER_YEARS) {
                if (!found) return@repeat
                val olderFrom = olderTo - (GrowthRepository.MAX_CALENDAR_DAYS - 1)
                val older = repository.calendar(olderFrom, olderTo)
                found = older != null && older.any { it.hasRecord }
                if (older != null) history = history.merged(older, olderFrom)
                _uiState.update { it.copy(load = GrowthLoad.Loaded(history.copy(searchingOlder = found))) }
                olderTo = olderFrom - 1
            }
            history = history.copy(searchingOlder = false)
            _uiState.update { it.copy(load = GrowthLoad.Loaded(history)) }
        }
    }

    private var loadJob: Job? = null

    private companion object {
        /** 첫 기록을 찾아 더 받는 최대 해 수(처음 1년 제외). */
        const val MAX_OLDER_YEARS = 4
    }
}

/** GROWTH-01 연결. */
@Composable
fun GrowthRoute(
    onBack: () -> Unit,
    viewModel: GrowthViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GrowthScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        actions = GrowthActions(
            onBack = onBack,
            onRange = viewModel::changeRange,
            onMetric = viewModel::changeMetric,
            onToggleDense = viewModel::toggleDense,
            onPickAnchor = viewModel::pickAnchor,
            onMoveHeatMonth = viewModel::moveHeatMonth,
            onPickHeatDay = viewModel::pickHeatDay,
            onRetry = viewModel::retry,
        ),
    )
}
