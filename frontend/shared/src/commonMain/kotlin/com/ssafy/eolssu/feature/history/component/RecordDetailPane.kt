package com.ssafy.eolssu.feature.history.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.monthDayLabel
import com.ssafy.eolssu.core.ui.component.timeLabel
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.ScoreNotation
import com.ssafy.eolssu.feature.history.RecordDetailActions
import com.ssafy.eolssu.feature.history.RecordDetailUiState
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.record_detail_follow
import eolssu.shared.generated.resources.record_detail_hint
import eolssu.shared.generated.resources.record_detail_show_errors
import eolssu.shared.generated.resources.record_detail_whole
import eolssu.shared.generated.resources.records_memo
import eolssu.shared.generated.resources.records_pane_empty
import org.jetbrains.compose.resources.stringResource

/** 이 너비보다 좁으면 머리 스위치를 요약 아래로, 재생 막대를 버튼 위로 내린다(좁은 가로 창). */
private val PaneWideMinWidth = 560.dp

/**
 * 태블릿 가로 두 칸 배치의 오른쪽 칸(`Tab-Records-Land`): 고른 녹음의 점수 요약 · 오류 표시 정간보 · 재생.
 * 상단 바가 없어 기록 이름·시각을 요약 카드에 적는다.
 */
@Composable
internal fun RecordDetailPane(state: RecordDetailUiState, actions: RecordDetailActions, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val record = state.record
    BoxWithConstraints(
        modifier
            .border(1.dp, colors.line, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        if (record == null) {
            Text(
                stringResource(Res.string.records_pane_empty),
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
            return@BoxWithConstraints
        }
        val wide = maxWidth >= PaneWideMinWidth
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PaneHeader(state, record, actions, wide)
            Text(
                stringResource(Res.string.record_detail_hint),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textMuted,
            )
            val song = state.song
            if (song == null) {
                Spacer(Modifier.weight(1f))
                return@Column
            }
            JeongganboView(
                song = song,
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                metrics = RecordScoreLandscape,
                notation = ScoreNotation(showHangul = true),
                currentIndex = state.position,
                follow = state.follow,
                errors = if (state.showErrors) record.errors.associate { (it.column to it.row) to it.metric } else emptyMap(),
                onEventClick = { actions.onSeek(it.index) },
            )
            Column {
                HorizontalDivider(color = colors.line)
                PaneControls(state, actions, wide, Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun PaneHeader(state: RecordDetailUiState, record: PracticeRecord, actions: RecordDetailActions, wide: Boolean) {
    val showErrors = stringResource(Res.string.record_detail_show_errors)
    val follow = stringResource(Res.string.record_detail_follow)
    if (wide) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PaneSummary(record, Modifier.weight(1f).fillMaxHeight())
            SwitchBox(showErrors, state.showErrors, actions.onToggleErrors, Modifier.fillMaxHeight(), paddingH = 12.dp, fillLabel = false)
            SwitchBox(follow, state.follow, actions.onToggleFollow, Modifier.fillMaxHeight(), paddingH = 12.dp, fillLabel = false)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PaneSummary(record, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SwitchBox(showErrors, state.showErrors, actions.onToggleErrors, Modifier.weight(1f))
                SwitchBox(follow, state.follow, actions.onToggleFollow, Modifier.weight(1f))
            }
        }
    }
}

/** 점수 · '아리랑 전체 · 10월 20일 오후 3:20' · 항목 점수 · 메모. */
@Composable
private fun PaneSummary(record: PracticeRecord, modifier: Modifier) {
    val colors = EolssuTheme.colors
    Row(
        modifier.background(colors.surface, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RecordScoreText(record, 32.sp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val part = record.section ?: stringResource(Res.string.record_detail_whole)
            Text(
                buildAnnotatedString {
                    append("${record.songTitle} $part · ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = colors.textMuted)) {
                        append("${monthDayLabel(record.recordedAt)} ${timeLabel(record.recordedAt)}")
                    }
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(recordMetricsLabel(record), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
            if (record.memo.isNotEmpty()) {
                Text(
                    stringResource(Res.string.records_memo, record.memo),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 재생 줄. 넓으면 −5초 · 재생 · +5초 · (시간 · 막대) · 취약 구간 버튼을 한 줄에 놓고,
 * 좁으면 시간 · 막대를 위 줄로 올린다.
 */
@Composable
private fun PaneControls(state: RecordDetailUiState, actions: RecordDetailActions, wide: Boolean, modifier: Modifier) {
    if (wide) {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PlaybackButtons(state, actions, skipSize = 44.dp, playSize = 56.dp, gap = 14.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PlaybackTimeRow(state)
                PlaybackSlider(state, actions)
            }
            WeakSectionButton(actions.onWeakSection, height = 44.dp, paddingH = 16.dp)
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PlaybackTimeRow(state)
            PlaybackSlider(state, actions)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlaybackButtons(state, actions, skipSize = 44.dp, playSize = 56.dp, gap = 12.dp)
                Box(Modifier.weight(1f)) { WeakSectionButton(actions.onWeakSection, Modifier.fillMaxWidth(), height = 44.dp) }
            }
        }
    }
}
