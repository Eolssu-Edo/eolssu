package com.ssafy.eolssu.feature.history

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
import com.ssafy.eolssu.feature.history.component.RecordGroups
import com.ssafy.eolssu.feature.history.component.RecordListSpacing
import com.ssafy.eolssu.feature.history.component.RecordListToolbar
import com.ssafy.eolssu.feature.history.component.RecordsEmpty
import com.ssafy.eolssu.feature.history.component.WeekCard
import com.ssafy.eolssu.feature.history.component.historyFrame
import com.ssafy.eolssu.feature.history.layout.RecordsExpandedLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.records_subtitle
import eolssu.shared.generated.resources.records_title
import org.jetbrains.compose.resources.stringResource

/**
 * RECORD-01 연습 기록: 이번 주 요약 · 곡별 녹음 묶음(펼치기) · 정렬.
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
    onSort: (RecordSort) -> Unit,
    onOpenRecord: (String) -> Unit,
    detail: RecordDetailUiState? = null,
    detailActions: RecordDetailActions? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val frame = historyFrame(widthClass)
    val title = stringResource(Res.string.records_title)
    val subtitle = if (state.recordsAvailable) stringResource(Res.string.records_subtitle, state.records.size) else null
    if (widthClass == WindowWidthClass.Expanded && detail != null && detailActions != null) {
        RecordsExpandedLayout(
            state = state,
            frame = frame,
            title = title,
            subtitle = subtitle,
            onBack = onBack,
            onOpenGrowth = onOpenGrowth,
            onToggle = onToggle,
            onSort = onSort,
            onSelect = onOpenRecord,
            detail = detail,
            detailActions = detailActions,
            snackbarHostState = snackbarHostState,
        )
        return
    }
    val spacing = if (frame.isTablet) RecordListSpacing.Portrait else RecordListSpacing.Compact
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        contentMaxWidth = frame.contentMaxWidth,
        topBar = { HistoryTopBar(frame, title, subtitle, onBack) },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = frame.gutter, end = frame.gutter, top = if (frame.isTablet) 4.dp else 0.dp, bottom = if (frame.isTablet) 28.dp else 24.dp),
        ) {
            if (state.recordsAvailable) WeekCard(state, spacing, onOpenGrowth)
            if (!state.recordsAvailable || state.groups.isEmpty()) {
                RecordsEmpty(available = state.recordsAvailable)
                return@Column
            }
            RecordListToolbar(state.groups.size, state.sort, spacing, onSort)
            RecordGroups(state, spacing, onToggle, onOpenRecord)
        }
    }
}
