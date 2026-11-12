package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.feature.history.component.RecordsLoadStatus
import com.ssafy.eolssu.core.theme.EolssuTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.feature.history.component.HistoryTopBar
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.feature.history.component.RecordGroups
import com.ssafy.eolssu.feature.history.component.RecordListSpacing
import com.ssafy.eolssu.feature.history.component.RecordListToolbar
import com.ssafy.eolssu.feature.history.component.RecordsInstrumentEmpty
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.feature.history.component.RecordsEmpty
import com.ssafy.eolssu.feature.history.component.GrowthLinkButton
import androidx.compose.foundation.layout.RowScope
import com.ssafy.eolssu.feature.history.component.historyFrame
import com.ssafy.eolssu.feature.history.layout.RecordsExpandedLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.records_title
import org.jetbrains.compose.resources.stringResource

/**
 * RECORD-01 연습 기록: 상단 바 오른쪽 연습 기록지 버튼 · 이름 토글 · 곡 제목 검색 · 곡별 녹음 묶음(펼치기, 안에서 날짜별로 다시 펼친다) · 악기 필터 · 정렬.
 * [onToggle]·[onLoadMore]는 곡 묶음 키를, [onToggleDay]는 곡 묶음 키와 날짜를 받는다.
 * 휴대폰·태블릿 세로는 한 칸이고 녹음을 누르면 [onOpenRecord]로 기록 상세에 간다.
 * 태블릿 가로·넓은 웹 창([WindowWidthClass.Expanded])은 [detail]이 있으면 왼쪽 목록 · 오른쪽 기록 상세의 두 칸이고,
 * 녹음을 누르면([onOpenRecord]) 오른쪽 상세만 바뀐다.
 */
@Composable
fun RecordListScreen(
    state: RecordListUiState,
    widthClass: WindowWidthClass,
    onBack: () -> Unit,
    onOpenGrowth: () -> Unit,
    onToggle: (String) -> Unit,
    onOpenRecord: (String) -> Unit,
    onRetry: () -> Unit = {},
    onLoadMore: (String) -> Unit = {},
    onInstrument: (Instrument?) -> Unit = {},
    onToggleDay: (String, Long) -> Unit = { _, _ -> },
    onToggleName: () -> Unit = {},
    onSearch: (String) -> Unit = {},
    detail: RecordDetailUiState? = null,
    detailActions: RecordDetailActions? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val frame = historyFrame(widthClass)
    val title = stringResource(Res.string.records_title)
    if (widthClass == WindowWidthClass.Expanded && detail != null && detailActions != null) {
        RecordsExpandedLayout(
            state = state,
            frame = frame,
            title = title,
            subtitle = null,
            onBack = onBack,
            onOpenGrowth = onOpenGrowth,
            onToggle = onToggle,
            onInstrument = onInstrument,
            onToggleDay = onToggleDay,
            onToggleName = onToggleName,
            onSearch = onSearch,
            onSelect = onOpenRecord,
            onRetry = onRetry,
            onLoadMore = onLoadMore,
            detail = detail,
            detailActions = detailActions,
            snackbarHostState = snackbarHostState,
        )
        return
    }
    val spacing = if (frame.isTablet) RecordListSpacing.Portrait else RecordListSpacing.Compact
    EolssuScaffold(
        containerColor = EolssuTheme.colors.groupedBackground,
        snackbarHostState = snackbarHostState,
        contentMaxWidth = frame.contentMaxWidth,
        // 제목은 마이페이지 등 다른 화면과 같은 공용 상단 바를 쓰고 저장 개수는 보이지 않는다(S15P31E102-768).
        topBar = {
            val growth: @Composable RowScope.() -> Unit = { if (state.recordsAvailable) GrowthLinkButton(onOpenGrowth) }
            if (frame.isTablet) HistoryTopBar(frame, title, null, onBack, right = growth) else EolssuTopBar(title, onBack = onBack, right = growth)
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = frame.gutter, end = frame.gutter, top = if (frame.isTablet) 4.dp else 0.dp, bottom = if (frame.isTablet) 28.dp else 24.dp),
        ) {
            if (state.showLoadStatus) {
                RecordsLoadStatus(failed = state.loadFailed, onRetry = onRetry)
                if (state.summaries.isEmpty()) return@Column
            }
            if (!state.recordsAvailable || state.summaries.isEmpty()) {
                RecordsEmpty(available = state.recordsAvailable)
                return@Column
            }
            RecordListToolbar(state.instrument, state.nameOrder, state.query, spacing, onInstrument, onToggleName, onSearch)
            if (state.groups.isEmpty()) RecordsInstrumentEmpty(searching = state.query.isNotBlank()) else RecordGroups(state, spacing, onToggle, onOpenRecord, onLoadMore, onToggleDay)
        }
    }
}
