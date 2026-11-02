package com.ssafy.eolssu.feature.history

import androidx.compose.foundation.border
import com.ssafy.eolssu.core.ui.score.JeongganboLook
import com.ssafy.eolssu.feature.history.component.HistoryBoardTopBar
import eolssu.shared.generated.resources.records_section
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.monthDayLabel
import com.ssafy.eolssu.core.ui.component.timeLabel
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.ScoreNotation
import com.ssafy.eolssu.feature.history.component.PlaybackBarContent
import com.ssafy.eolssu.feature.history.component.RecordScoreText
import com.ssafy.eolssu.feature.history.component.RecordSongLookupContent
import com.ssafy.eolssu.feature.history.component.SwitchBox
import com.ssafy.eolssu.feature.history.component.historyFrame
import com.ssafy.eolssu.feature.history.component.recordMetricsLabel
import com.ssafy.eolssu.feature.history.layout.RecordDetailTabletLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.record_detail_follow
import eolssu.shared.generated.resources.record_detail_hint
import eolssu.shared.generated.resources.record_detail_show_errors
import eolssu.shared.generated.resources.record_detail_subtitle
import eolssu.shared.generated.resources.record_detail_title
import eolssu.shared.generated.resources.record_detail_whole
import eolssu.shared.generated.resources.records_memo
import org.jetbrains.compose.resources.stringResource

/** 기록 상세에서 부르는 동작. */
class RecordDetailActions(
    val onBack: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onSeek: (Int) -> Unit,
    val onSkip: (Int) -> Unit,
    val onToggleErrors: () -> Unit,
    val onToggleFollow: () -> Unit,
    val onWeakSection: () -> Unit,
    val onRetrySong: () -> Unit = {},
)

/**
 * RECORD-02 기록 상세: 점수 요약, 오류 위치를 표시한 정간보, 녹음 재생 위치.
 * 태블릿([WindowWidthClass.Medium] 이상)은 정간보를 여러 줄로 접는 세로 배치를 쓴다. 태블릿 가로에서는 보통
 * 기록 목록 오른쪽 칸([com.ssafy.eolssu.feature.history.component.RecordDetailPane])으로 보고, 이 화면은 세로에서 연 상세를 돌렸을 때 보인다.
 */
@Composable
fun RecordDetailScreen(
    state: RecordDetailUiState,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    actions: RecordDetailActions,
) {
    if (state.record == null || state.song == null) {
        EolssuScaffold(containerColor = EolssuTheme.colors.surface, topBar = { HistoryBoardTopBar(stringResource(Res.string.record_detail_title), null, actions.onBack, bottom = 8.dp) }) {
            RecordSongLookupContent(state, actions)
        }
        return
    }
    if (widthClass == WindowWidthClass.Compact) {
        RecordDetailCompact(state, snackbarHostState, actions)
    } else {
        RecordDetailTabletLayout(state, historyFrame(widthClass), snackbarHostState, actions)
    }
}

/** 상단 바 보조 문구: '10월 20일 오후 3:20 · 아리랑 전체'. */
@Composable
internal fun recordDetailSubtitle(record: PracticeRecord?): String? = record?.let {
    stringResource(
        Res.string.record_detail_subtitle,
        "${monthDayLabel(it.recordedAt)} ${timeLabel(it.recordedAt)}",
        it.songTitle,
        it.section?.let { s -> stringResource(Res.string.records_section, s) } ?: stringResource(Res.string.record_detail_whole),
    )
}

@Composable
private fun RecordDetailCompact(state: RecordDetailUiState, snackbarHostState: SnackbarHostState, actions: RecordDetailActions) {
    val record = state.record
    val song = state.song
    val colors = EolssuTheme.colors
    EolssuScaffold(
        containerColor = EolssuTheme.colors.surface,
        snackbarHostState = snackbarHostState,
        topBar = { HistoryBoardTopBar(stringResource(Res.string.record_detail_title), recordDetailSubtitle(record), actions.onBack, bottom = 8.dp) },
        bottomBar = { if (song != null && record != null) PlaybackBarContent(state, actions, gap = 12.dp) },
    ) {
        if (record == null || song == null) return@EolssuScaffold
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(14.dp)).border(1.dp, colors.lineSoft, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                RecordScoreText(record, 32.sp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(recordMetricsLabel(record), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
                    if (record.memo.isNotEmpty()) {
                        Text(stringResource(Res.string.records_memo, record.memo), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
                    }
                }
            }
            Row(Modifier.padding(top = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SwitchBox(stringResource(Res.string.record_detail_show_errors), state.showErrors, actions.onToggleErrors, Modifier.weight(1f))
                SwitchBox(stringResource(Res.string.record_detail_follow), state.follow, actions.onToggleFollow, Modifier.weight(1f))
            }
            Text(
                stringResource(Res.string.record_detail_hint),
                modifier = Modifier.padding(bottom = 6.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = colors.textMuted,
            )
            JeongganboView(
                song = song,
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                notation = ScoreNotation(showHangul = true),
                currentIndex = state.position,
                follow = state.follow,
                errors = if (state.showErrors) record.errors.associate { (it.column to it.row) to it.kind } else emptyMap(),
                onEventClick = { actions.onSeek(it.index) },
                look = JeongganboLook.Record,
            )
        }
    }
}
