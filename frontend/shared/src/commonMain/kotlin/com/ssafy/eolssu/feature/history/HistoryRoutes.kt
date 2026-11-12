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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.data.practice.GrowthRepository
import com.ssafy.eolssu.data.practice.TrendScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.CancellationException
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
            RecordsTwoPaneRoute(state, justSavedId, onBack, onOpenGrowth, viewModel, onCompare, onSelectSection, onPracticeSection)
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
    RecordDeleteEffect(detail.delete, snackbarHostState, detailViewModel::dismissDeleteError)
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
 * GROWTH-01 상태. 처음에 이번 주를 받고, 다른 기간은 탭을 처음 고를 때 받는다. 받지 못한 기간은 탭을 다시 고르거나 '다시 시도'로 받는다.
 * '점수 변화'(GRO-03)는 기간과 따로 처음에 가장 최근에 연주한 곡의 기본 평가를 받고, 악기·곡을 고를 때마다 다시 받는다.
 * 악기 고르기용 곡 → 악기 대응은 곡 목록(CAT-01·02)에서 한 번 받고, 곡 목록에 없는 곡(지우거나 비공개로 바꾼 곡)은 고르지 않는다.
 */
class GrowthViewModel(
    private val repository: GrowthRepository,
    private val songs: SongRepository,
    now: () -> Long,
    member: SignedInMember? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GrowthUiState(today = KoreanDateTime.fromEpochMillis(now())))
    val uiState: StateFlow<GrowthUiState> = _uiState.asStateFlow()

    private val accountScope = member.screenScope(viewModelScope) {
        _uiState.value = GrowthUiState(today = _uiState.value.today)
    }

    private var trendJob: Job? = null

    /** 악기 고르기용 곡 → 악기 대응. 한 번만 받고, 받지 못하면 null이라 악기 없이 곡만 고른다. */
    private val trendCatalog: Deferred<TrendCatalog?> = accountScope.async {
        try {
            TrendCatalog.of(songs.getInstruments(), songs.getSongs())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    init {
        load(GrowthRange.Week)
        loadTrend(songId = null)
    }

    fun changeRange(range: GrowthRange) {
        if (!accountScope.isActive) return
        _uiState.update { it.copy(range = range) }
        if (_uiState.value.loads[range] !is GrowthLoad.Loaded) load(range)
    }

    fun retry() = load(_uiState.value.range)

    /** 점수 변화 악기를 바꾼다. 그 악기에서 가장 최근에 연주한 기본 평가 곡을 보인다. */
    fun selectTrendInstrument(instrumentId: String) {
        val trend = _uiState.value.scoreTrend
        if (trend.selectedInstrument?.instrumentId == instrumentId && !trend.failed) return
        val song = trend.songFor(instrumentId) ?: return
        loadTrend(song.songId)
    }

    /** 점수 변화 곡을 바꾼다. 기본 평가가 없는 곡은 고를 수 없다. */
    fun selectTrendSong(songId: String) {
        val trend = _uiState.value.scoreTrend
        if (trend.basicSongs.none { it.songId == songId }) return
        if (songId == trend.songId && !trend.failed) return
        loadTrend(songId)
    }

    fun retryTrend() = loadTrend(_uiState.value.scoreTrend.songId)

    /**
     * 늘 기본 평가(`FULL`)만 받는다. 서버가 기본 평가만 골라 최근 5개를 오래된 순으로 주므로 재평가가 5개 안에 끼지 않는다.
     * 앞선 요청은 취소한다(빠르게 곡을 바꾸면 마지막에 고른 곡만 보인다). 곡 → 악기 대응을 받은 뒤에 결과를 보여,
     * 서버가 고른 곡을 고를 수 없으면(기본 평가가 없거나 곡 목록에 없는 곡) 그 곡을 그리지 않고 가장 최근에 연주한 고를 수 있는 곡을 다시 받는다.
     */
    private fun loadTrend(songId: String?) {
        if (!accountScope.isActive) return
        trendJob?.cancel()
        _uiState.update { it.copy(scoreTrend = it.scoreTrend.requesting(songId)) }
        trendJob = accountScope.launch {
            val trend = repository.scoreTrend(songId, TrendScope.Full)
            val catalog = trendCatalog.await()
            val received = { state: ScoreTrendState -> state.copy(catalog = catalog).received(trend) }
            val next = received(_uiState.value.scoreTrend)
            val fallback = next.fallbackSongId?.takeIf { trend != null && it != songId }
            if (fallback != null) {
                _uiState.update { it.copy(scoreTrend = it.scoreTrend.copy(catalog = catalog)) }
                loadTrend(fallback)
            } else {
                _uiState.update { it.copy(scoreTrend = received(it.scoreTrend)) }
            }
        }
    }

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
        onBack = onBack,
        onRange = viewModel::changeRange,
        onRetry = viewModel::retry,
        trendActions = ScoreTrendActions(viewModel::selectTrendInstrument, viewModel::selectTrendSong, viewModel::retryTrend),
    )
}
