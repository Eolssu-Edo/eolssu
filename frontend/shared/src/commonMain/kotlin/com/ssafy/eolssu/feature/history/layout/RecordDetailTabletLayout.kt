package com.ssafy.eolssu.feature.history.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.feature.history.RecordDetailActions
import com.ssafy.eolssu.feature.history.RecordDetailUiState
import com.ssafy.eolssu.feature.history.component.HistoryFrame
import com.ssafy.eolssu.feature.history.component.HistoryTopBar
import com.ssafy.eolssu.feature.history.component.PlaybackBarContent
import com.ssafy.eolssu.feature.history.component.RecordScorePortrait
import com.ssafy.eolssu.feature.history.component.RecordScoreText
import com.ssafy.eolssu.feature.history.component.SwitchBox
import com.ssafy.eolssu.feature.history.component.WrappedJeongganbo
import com.ssafy.eolssu.feature.history.component.recordMetricsLabel
import com.ssafy.eolssu.feature.history.recordDetailSubtitle
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.record_detail_follow
import eolssu.shared.generated.resources.record_detail_hint_wrapped
import eolssu.shared.generated.resources.record_detail_show_errors
import eolssu.shared.generated.resources.record_detail_title
import eolssu.shared.generated.resources.records_memo
import org.jetbrains.compose.resources.stringResource

/**
 * 태블릿 세로 기록 상세(`Tab-RecordDetail-Port`): 점수 요약 옆에 스위치 두 개를 세로로 쌓고,
 * 정간보는 너비에 맞춰 여러 줄로 접는다. 재생 영역은 가운데 640dp 안에 둔다.
 */
@Composable
internal fun RecordDetailTabletLayout(
    state: RecordDetailUiState,
    frame: HistoryFrame,
    snackbarHostState: SnackbarHostState,
    actions: RecordDetailActions,
) {
    val record = state.record
    val song = state.song
    val colors = EolssuTheme.colors
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        contentMaxWidth = frame.contentMaxWidth,
        topBar = { HistoryTopBar(frame, stringResource(Res.string.record_detail_title), recordDetailSubtitle(record), actions.onBack) },
        bottomBar = {
            if (song != null && record != null) {
                Column(
                    Modifier.align(Alignment.CenterHorizontally).widthIn(max = 640.dp).fillMaxWidth().padding(top = 4.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PlaybackBarContent(state, actions, gap = 16.dp)
                }
            }
        },
    ) {
        if (record == null || song == null) return@EolssuScaffold
        Column(Modifier.fillMaxSize().padding(start = frame.gutter, end = frame.gutter, top = 4.dp, bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard(record, Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.width(200.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SwitchBox(stringResource(Res.string.record_detail_show_errors), state.showErrors, actions.onToggleErrors, Modifier.weight(1f).fillMaxWidth())
                    SwitchBox(stringResource(Res.string.record_detail_follow), state.follow, actions.onToggleFollow, Modifier.weight(1f).fillMaxWidth())
                }
            }
            Text(
                stringResource(Res.string.record_detail_hint_wrapped),
                modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textMuted,
            )
            WrappedJeongganbo(
                song = song,
                metrics = RecordScorePortrait,
                currentIndex = state.position,
                follow = state.follow,
                errors = if (state.showErrors) record.errors.associate { (it.column to it.row) to it.kind } else emptyMap(),
                onEventClick = actions.onSeek,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

/** 점수(40) · 항목 점수 · 메모. */
@Composable
private fun SummaryCard(record: PracticeRecord, modifier: Modifier) {
    val colors = EolssuTheme.colors
    Row(
        modifier.background(colors.surface, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RecordScoreText(record, 40.sp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(recordMetricsLabel(record), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
            if (record.memo.isNotEmpty()) {
                Text(stringResource(Res.string.records_memo, record.memo), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
            }
        }
    }
}
