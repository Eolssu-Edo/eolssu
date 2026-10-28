package com.ssafy.eolssu.feature.history

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_coming_soon
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
        RecordsTwoPaneRoute(state, justSavedId, onBack, onOpenGrowth, viewModel)
    } else {
        RecordListScreen(state, widthClass, onBack, onOpenGrowth, viewModel::toggle, viewModel::changeSort, onOpenRecord)
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
    RecordListScreen(
        state = state,
        widthClass = WindowWidthClass.Expanded,
        onBack = onBack,
        onOpenGrowth = onOpenGrowth,
        onToggle = viewModel::toggle,
        onSort = viewModel::changeSort,
        onOpenRecord = viewModel::select,
        detail = detail,
        detailActions = rememberRecordDetailActions(detailViewModel, snackbarHostState, onBack),
        snackbarHostState = snackbarHostState,
    )
}

/** RECORD-02 연결. */
@Composable
fun RecordDetailRoute(
    recordId: String,
    onBack: () -> Unit,
    viewModel: RecordDetailViewModel = koinViewModel(key = recordId) { parametersOf(recordId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    RecordDetailScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        actions = rememberRecordDetailActions(viewModel, snackbarHostState, onBack),
    )
}

/** 기록 상세 동작. 뒤로 가기 전에 재생을 멈추고, 취약 구간 연습은 2차 확장 화면이라 '준비 중' 안내를 띄운다. */
@Composable
private fun rememberRecordDetailActions(
    viewModel: RecordDetailViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
): RecordDetailActions {
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(Res.string.common_coming_soon)
    val latestOnBack by rememberUpdatedState(onBack)
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
            onWeakSection = { scope.launch { snackbarHostState.showSnackbar(comingSoon) } },
        )
    }
}

class GrowthViewModel(recordRepository: PracticeRecordRepository, now: () -> Long) : ViewModel() {
    private val _uiState = MutableStateFlow(GrowthUiState())
    val uiState: StateFlow<GrowthUiState> = _uiState.asStateFlow()
    val today: KoreanDateTime = KoreanDateTime.fromEpochMillis(now())

    init {
        viewModelScope.launch { recordRepository.records.collect { list -> _uiState.update { it.copy(records = list) } } }
    }

    fun changeRange(range: GrowthRange) = _uiState.update { it.copy(range = range) }
}

/** GROWTH-01 연결. [onOpenMissions]는 '완료한 미션'(학습 화면) 이동. */
@Composable
fun GrowthRoute(
    onBack: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenMissions: () -> Unit,
    viewModel: GrowthViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GrowthScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        todayEpochDay = viewModel.today.epochDay,
        onBack = onBack,
        onRange = viewModel::changeRange,
        onOpenMissions = onOpenMissions,
        onOpenRecords = onOpenRecords,
    )
}
