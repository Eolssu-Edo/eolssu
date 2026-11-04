package com.ssafy.eolssu.feature.history

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import eolssu.shared.generated.resources.record_play_unavailable
import eolssu.shared.generated.resources.record_play_busy
import eolssu.shared.generated.resources.record_play_failed
import eolssu.shared.generated.resources.record_detail_failed
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.data.practice.GrowthRepository
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.LearnProgressRepository
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_coming_soon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * RECORD-01 연결. [justSavedId]는 결과 화면에서 방금 저장한 기록.
 * 태블릿 가로·넓은 웹 창에서는 같은 화면 오른쪽에 기록 상세를 두고, 녹음을 누르면 화면 이동 없이 오른쪽만 바꾼다.
 * [initialRecordId]는 가로 창에서 기록 상세 경로로 들어왔을 때 처음 고를 기록이다.
 */
@Composable
fun RecordListRoute(
    justSavedId: String?,
    onBack: () -> Unit,
    onOpenGrowth: () -> Unit,
    onOpenRecord: (String) -> Unit,
    initialRecordId: String? = null,
    onCompare: (recordId: String) -> Unit = {},
    onWeakSection: ((recordId: String) -> Unit)? = null,
    viewModel: RecordListViewModel = koinViewModel(key = "records-$justSavedId") { parametersOf(justSavedId) },
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
    val widthClass = currentWindowLayout().widthClass
    if (widthClass == WindowWidthClass.Expanded) {
        RecordsTwoPaneRoute(state, justSavedId, onBack, onOpenGrowth, viewModel, onCompare, onWeakSection)
    } else {
        RecordListScreen(state, widthClass, onBack, onOpenGrowth, viewModel::toggle, viewModel::changeSort, onOpenRecord, onRetry = viewModel::retry)
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
    onCompare: (recordId: String) -> Unit,
    onWeakSection: ((recordId: String) -> Unit)?,
) {
    val selectedId = state.selectedRecordId
    val detailViewModel: RecordDetailViewModel = koinViewModel(key = "records-pane-$justSavedId") { parametersOf(selectedId.orEmpty()) }
    LaunchedEffect(detailViewModel, selectedId) {
        if (selectedId != null) detailViewModel.show(selectedId)
    }
    DisposableEffect(detailViewModel) {
        onDispose { detailViewModel.pause() }
    }
    val detail by detailViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    RecordNoticeEffect(detail.notice, snackbarHostState, detailViewModel::consumeNotice)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { detailViewModel.pause() }
    RecordListScreen(
        state = state,
        widthClass = WindowWidthClass.Expanded,
        onBack = onBack,
        onOpenGrowth = onOpenGrowth,
        onToggle = viewModel::toggle,
        onSort = viewModel::changeSort,
        onOpenRecord = viewModel::select,
        onRetry = viewModel::retry,
        detail = detail,
        detailActions = rememberRecordDetailActions(detailViewModel, snackbarHostState, onBack, onCompare, onWeakSection),
        snackbarHostState = snackbarHostState,
    )
}

/**
 * RECORD-02 연결.
 *
 * @param onCompare ‘지난 연주와 비교’ → COMPARE-01(2차).
 * @param onWeakSection ‘취약 구간 연습하기’ → PRACTICE-02(2차). null이면 '준비 중' 안내를 띄운다.
 */
@Composable
fun RecordDetailRoute(
    recordId: String,
    onBack: () -> Unit,
    onCompare: (recordId: String) -> Unit = {},
    onWeakSection: ((recordId: String) -> Unit)? = null,
    viewModel: RecordDetailViewModel = koinViewModel(key = recordId) { parametersOf(recordId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    RecordNoticeEffect(state.notice, snackbarHostState, viewModel::consumeNotice)
    // 앱을 벗어나면(홈 화면·전화) 녹음 재생을 멈춘다.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }
    RecordDetailScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        actions = rememberRecordDetailActions(viewModel, snackbarHostState, onBack, onCompare, onWeakSection),
    )
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

/**
 * 기록 상세 동작. 뒤로 가기·비교·취약 구간으로 가기 전에 재생을 멈춘다. 취약 구간 연결이 없으면 '준비 중' 안내를 띄운다.
 * 비교·취약 구간은 지금 보이는 기록 ID로 연다.
 */
@Composable
private fun rememberRecordDetailActions(
    viewModel: RecordDetailViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onCompare: (recordId: String) -> Unit,
    onWeakSection: ((recordId: String) -> Unit)?,
): RecordDetailActions {
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(Res.string.common_coming_soon)
    val latestOnBack by rememberUpdatedState(onBack)
    val latestOnCompare by rememberUpdatedState(onCompare)
    val latestOnWeakSection by rememberUpdatedState(onWeakSection)
    return remember(viewModel, snackbarHostState, scope, comingSoon) {
        RecordDetailActions(
            onBack = {
                viewModel.pause()
                latestOnBack()
            },
            onTogglePlay = viewModel::togglePlay,
            onSeek = viewModel::seek,
            onSkip = viewModel::skipSeconds,
            onToggleErrors = viewModel::toggleErrors,
            onToggleFollow = viewModel::toggleFollow,
            onWeakSection = {
                val open = latestOnWeakSection
                val recordId = viewModel.uiState.value.record?.id
                if (open != null && recordId != null) {
                    viewModel.pause()
                    open(recordId)
                } else {
                    scope.launch { snackbarHostState.showSnackbar(comingSoon) }
                }
            },
            onRetrySong = viewModel::retrySong,
            onCompare = {
                viewModel.uiState.value.record?.id?.let { recordId ->
                    viewModel.pause()
                    latestOnCompare(recordId)
                }
            },
        )
    }
}

/**
 * GROWTH-01 상태. 처음에 이번 주를 받고, 다른 기간은 탭을 처음 고를 때 받는다. 받지 못한 기간은 탭을 다시 고르거나 '다시 시도'로 받는다.
 * '완료한 단계'는 연습 악기가 바뀌거나 학습 화면에서 완료 표시를 바꾸면 다시 센다.
 */
class GrowthViewModel(
    private val repository: GrowthRepository,
    private val learnProgress: LearnProgressRepository,
    instruments: InstrumentRepository,
    now: () -> Long,
    member: SignedInMember? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GrowthUiState(today = KoreanDateTime.fromEpochMillis(now())))
    val uiState: StateFlow<GrowthUiState> = _uiState.asStateFlow()

    private val accountScope = member.screenScope(viewModelScope) {
        _uiState.value = GrowthUiState(today = _uiState.value.today)
    }

    init {
        load(GrowthRange.Week)
        accountScope.launch {
            combine(instruments.instrument, learnProgress.saves) { instrument, _ -> instrument?.let(learnProgress::doneStageCount) }
                .collect { count -> _uiState.update { it.copy(completedSteps = count) } }
        }
    }

    fun changeRange(range: GrowthRange) {
        if (!accountScope.isActive) return
        _uiState.update { it.copy(range = range) }
        if (_uiState.value.loads[range] !is GrowthLoad.Loaded) load(range)
    }

    fun retry() = load(_uiState.value.range)

    private fun load(range: GrowthRange) {
        if (!accountScope.isActive) return
        if (_uiState.value.loads[range] == GrowthLoad.Loading) return
        _uiState.update { it.copy(loads = it.loads + (range to GrowthLoad.Loading)) }
        accountScope.launch {
            val load = loadGrowth(repository, GrowthPeriod.of(range, _uiState.value.today))
            _uiState.update { it.copy(loads = it.loads + (range to load)) }
        }
    }
}

/** GROWTH-01 연결. [onOpenSteps]는 '완료한 단계'(기초부터 배우기) 이동. */
@Composable
fun GrowthRoute(
    onBack: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenSteps: () -> Unit,
    viewModel: GrowthViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GrowthScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        onBack = onBack,
        onRange = viewModel::changeRange,
        onRetry = viewModel::retry,
        onOpenSteps = onOpenSteps,
        onOpenRecords = onOpenRecords,
    )
}
