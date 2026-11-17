package com.ssafy.eolssu.feature.history.layout

import com.ssafy.eolssu.core.theme.EolssuTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.feature.history.RecordDetailActions
import com.ssafy.eolssu.feature.history.RecordDetailUiState
import com.ssafy.eolssu.feature.history.RecordListUiState
import com.ssafy.eolssu.feature.history.component.HistoryFrame
import com.ssafy.eolssu.feature.history.component.HistoryTopBar
import com.ssafy.eolssu.feature.history.component.RecordDetailPane
import com.ssafy.eolssu.feature.history.component.RecordGroups
import com.ssafy.eolssu.feature.history.component.RecordListSpacing
import com.ssafy.eolssu.feature.history.component.RecordListToolbar
import com.ssafy.eolssu.feature.history.component.RecordsInstrumentEmpty
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.feature.history.component.RecordsEmpty
import com.ssafy.eolssu.feature.history.component.RecordsLoadStatus
import com.ssafy.eolssu.feature.history.component.GrowthLinkButton

/**
 * 태블릿 가로·넓은 웹 창의 연습 기록(`Tab-Records-Land`): 왼쪽 400dp 목록(묶음만 스크롤) · 오른쪽 고른 녹음의 기록 상세.
 * 창이 좁으면 목록을 본문의 36%(최소 320dp)로 줄인다.
 * 저장한 연주가 없으면 고를 녹음이 없으므로 두 칸으로 나누지 않고 빈 기록 안내와 '연습하러 가기'를 가운데에 둔다(S15P31E102-1031).
 */
@Composable
internal fun RecordsExpandedLayout(
    state: RecordListUiState,
    frame: HistoryFrame,
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    onOpenGrowth: () -> Unit,
    onToggle: (String) -> Unit,
    onInstrument: (Instrument?) -> Unit,
    onToggleDay: (String, Long) -> Unit,
    onToggleName: () -> Unit,
    onSearch: (String) -> Unit,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: (String) -> Unit,
    onStartPractice: () -> Unit,
    detail: RecordDetailUiState,
    detailActions: RecordDetailActions,
    snackbarHostState: SnackbarHostState,
) {
    val spacing = RecordListSpacing.Pane
    EolssuScaffold(
        containerColor = EolssuTheme.colors.groupedBackground,
        snackbarHostState = snackbarHostState,
        contentMaxWidth = frame.contentMaxWidth,
        topBar = { HistoryTopBar(frame, title, subtitle, onBack) { if (state.recordsAvailable) GrowthLinkButton(onOpenGrowth) } },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(start = frame.gutter, end = frame.gutter, top = 4.dp, bottom = 24.dp)) {
            if (!state.showLoadStatus && state.summaries.isEmpty()) {
                RecordsEmpty(available = state.recordsAvailable, onStartPractice = onStartPractice, modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp))
                return@BoxWithConstraints
            }
            val listWidth = (maxWidth * 0.36f).coerceIn(320.dp, 400.dp)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(listWidth).fillMaxHeight()) {
                    if (state.showLoadStatus) {
                        RecordsLoadStatus(failed = state.loadFailed, onRetry = onRetry)
                    }
                    if (state.summaries.isNotEmpty()) {
                        RecordListToolbar(state.instrument, state.nameOrder, state.query, spacing, onInstrument, onToggleName, onSearch)
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            if (state.groups.isEmpty()) RecordsInstrumentEmpty(searching = state.query.isNotBlank())
                            else RecordGroups(state, spacing, onToggle, onSelect, onLoadMore, onToggleDay, selectedId = state.selectedRecordId)
                        }
                    }
                }
                RecordDetailPane(detail, detailActions, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}
