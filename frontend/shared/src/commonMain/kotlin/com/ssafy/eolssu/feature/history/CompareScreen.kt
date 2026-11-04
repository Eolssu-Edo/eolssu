package com.ssafy.eolssu.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.monthDayLabel
import com.ssafy.eolssu.core.ui.component.songInstrumentLabel
import com.ssafy.eolssu.core.ui.component.timeLabel
import com.ssafy.eolssu.core.ui.score.JeongganboLook
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.ScoreCellDecoration
import com.ssafy.eolssu.core.ui.score.scorePositionLabel
import com.ssafy.eolssu.core.ui.score.scoreStrips
import com.ssafy.eolssu.feature.history.component.ComparePickSheet
import com.ssafy.eolssu.feature.history.component.HistoryBoardTopBar
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_pause
import eolssu.shared.generated.resources.weak_compare_change
import eolssu.shared.generated.resources.weak_compare_change_label
import eolssu.shared.generated.resources.weak_compare_delta
import eolssu.shared.generated.resources.weak_compare_down
import eolssu.shared.generated.resources.weak_compare_legend_both
import eolssu.shared.generated.resources.weak_compare_legend_now
import eolssu.shared.generated.resources.weak_compare_legend_prev
import eolssu.shared.generated.resources.weak_compare_listen
import eolssu.shared.generated.resources.weak_compare_none
import eolssu.shared.generated.resources.weak_compare_now
import eolssu.shared.generated.resources.weak_compare_play
import eolssu.shared.generated.resources.weak_compare_practice
import eolssu.shared.generated.resources.weak_compare_prev
import eolssu.shared.generated.resources.weak_compare_score_bpm
import eolssu.shared.generated.resources.weak_compare_speed
import eolssu.shared.generated.resources.weak_compare_subtitle
import eolssu.shared.generated.resources.weak_compare_title
import eolssu.shared.generated.resources.weak_compare_up
import eolssu.shared.generated.resources.weak_load_missing
import org.jetbrains.compose.resources.stringResource

/** 이전 연주와 비교에서 부르는 동작. */
class CompareActions(
    val onBack: () -> Unit,
    val onSelect: (ScorePosition) -> Unit,
    val onTogglePlay: () -> Unit,
    val onOpenPicker: () -> Unit,
    val onClosePicker: () -> Unit,
    val onPickDay: (Long) -> Unit,
    val onPickRecord: (String) -> Unit,
    val onPractice: () -> Unit,
)

/**
 * COMPARE-01 이전 연주와 비교(2차 `Compare` · `Compare-Pick`): 이전(바꾸기) → 이번 카드 · 점수 차이 · 범례 ·
 * 곡 전체 정간보(이전에 틀림 = 옅은 붉은 바탕, 이번에 틀림 = 붉은 테두리, 둘 다 = 둘 다. 점과 고른 정간 검은 테두리는 없다) ·
 * 재생 버튼 하나(고른 정간부터 이전 → 이번) · ‘틀린 구간 연습하기’. ‘바꾸기’는 최근 2주 달력 시트를 연다.
 */
@Composable
fun CompareScreen(state: CompareUiState, actions: CompareActions) {
    val colors = EolssuTheme.colors
    val comparison = state.comparison
    EolssuScaffold(
        containerColor = colors.surface,
        topBar = {
            HistoryBoardTopBar(
                stringResource(Res.string.weak_compare_title),
                comparison?.let { stringResource(Res.string.weak_compare_subtitle, it.song.title, songInstrumentLabel(it.song)) },
                actions.onBack,
                bottom = 8.dp,
            )
        },
        bottomBar = if (comparison != null) {
            { CompareFooter(state, actions) }
        } else {
            null
        },
    ) {
        if (comparison == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.loading) CircularProgressIndicator() else Text(stringResource(Res.string.weak_load_missing))
            }
            return@EolssuScaffold
        }
        val song = comparison.song
        val longestStrip = remember(song) { scoreStrips(song.score, song.jangdan).maxOfOrNull { it.rows.count() } ?: 1 }
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RecordPair(state.previous, comparison.current, actions.onOpenPicker)
            val previous = state.previous
            val delta = state.delta
            if (previous == null || delta == null) {
                Text(stringResource(Res.string.weak_compare_none), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = colors.textSub)
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(Res.string.weak_compare_delta, if (delta >= 0) "+$delta" else "$delta"),
                        style = EolssuTheme.serif.brand.copy(fontSize = 24.sp, lineHeight = 24.sp),
                    )
                    val trend = stringResource(if (delta >= 0) Res.string.weak_compare_up else Res.string.weak_compare_down)
                    Text(
                        if (previous.bpm != comparison.current.bpm) stringResource(Res.string.weak_compare_speed, trend, previous.bpm, comparison.current.bpm) else trend,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = colors.textSub,
                    )
                }
            }
            CompareLegend(state.counts)
            JeongganboView(
                song = song,
                modifier = Modifier.weight(1f).padding(top = 6.dp).verticalScroll(rememberScrollState()),
                metrics = JeongganboMetrics.practiceBoard(big = false, longestStrip = longestStrip),
                look = JeongganboLook.practice(big = false),
                onUnitClick = { actions.onSelect(ScorePosition(it.column, it.row)) },
                decorations = compareDecorations(state.marks),
            )
        }
    }
    if (state.sheetOpen && comparison != null) {
        ComparePickSheet(state, actions.onClosePicker, actions.onPickDay, actions.onPickRecord)
    }
}

/** 이전 · 이번 정간 꾸밈: 이전에 틀림 = 옅은 붉은 바탕, 이번에 틀림 = 2dp 붉은 테두리. */
@Composable
private fun compareDecorations(marks: Map<ScorePosition, CompareMark>): Map<Pair<Int, Int>, ScoreCellDecoration> {
    val colors = EolssuTheme.colors
    return marks.entries.associate { (p, mark) ->
        (p.column to p.row) to ScoreCellDecoration(
            fill = if (mark != CompareMark.New) colors.recFaint else null,
            ring = if (mark != CompareMark.Fixed) colors.rec else null,
        )
    }
}

/** 이전(누르면 바꾸기) → 이번 카드. */
@Composable
private fun RecordPair(previous: PracticeRecord?, current: PracticeRecord, onChange: () -> Unit) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val small = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
    val meta = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
    val date = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val changeLabel = stringResource(Res.string.weak_compare_change_label)
    Row(
        Modifier.fillMaxWidth().border(1.dp, colors.lineSoft, shape).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            Modifier.weight(1f).clickable(role = Role.Button, onClick = onChange).semantics { contentDescription = changeLabel },
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                buildAnnotatedString {
                    append(stringResource(Res.string.weak_compare_prev) + " · ")
                    withStyle(SpanStyle(color = colors.text, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)) {
                        append(stringResource(Res.string.weak_compare_change))
                    }
                },
                style = small,
                color = colors.textMuted,
            )
            if (previous != null) {
                Text("${monthDayLabel(previous.recordedAt)} ${timeLabel(previous.recordedAt)}", style = date)
                Text(stringResource(Res.string.weak_compare_score_bpm, previous.score, previous.bpm), style = meta, color = colors.textMuted)
            } else {
                Text("—", style = date)
            }
        }
        Text("→", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), color = colors.textDisabled)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(stringResource(Res.string.weak_compare_now), style = small, color = colors.accentText)
            Text("${monthDayLabel(current.recordedAt)} ${timeLabel(current.recordedAt)}", style = date, textAlign = TextAlign.End)
            Text(stringResource(Res.string.weak_compare_score_bpm, current.score, current.bpm), style = meta, color = colors.textMuted)
        }
    }
}

/** 범례: 14dp 견본(이전 = 옅은 붉은 바탕, 이번 = 흰 바탕 붉은 테두리, 둘 다)과 수. */
@Composable
private fun CompareLegend(counts: CompareCounts) {
    val colors = EolssuTheme.colors
    val text = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendItem(colors.recFaint, null, stringResource(Res.string.weak_compare_legend_prev, counts.previous), text)
        LegendItem(colors.surface, colors.rec, stringResource(Res.string.weak_compare_legend_now, counts.current), text)
        LegendItem(colors.recFaint, colors.rec, stringResource(Res.string.weak_compare_legend_both, counts.still), text)
    }
}

@Composable
private fun LegendItem(fill: Color, ring: Color?, label: String, style: androidx.compose.ui.text.TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            Modifier.size(14.dp).background(fill, RoundedCornerShape(3.dp)).drawBehind {
                if (ring == null) return@drawBehind
                val stroke = 2.dp.toPx()
                drawRoundRect(
                    ring, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(stroke),
                )
            },
        )
        Text(label, style = style, color = EolssuTheme.colors.textSub)
    }
}

/** 아래 고정 영역: 44dp 재생 버튼과 듣기 안내 · ‘틀린 구간 연습하기’. */
@Composable
private fun CompareFooter(state: CompareUiState, actions: CompareActions) {
    val colors = EolssuTheme.colors
    val playLabel = stringResource(if (state.playing) Res.string.practice_pause else Res.string.weak_compare_play)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            onClick = actions.onTogglePlay,
            enabled = state.previous != null,
            modifier = Modifier.size(44.dp).semantics { contentDescription = playLabel },
            shape = CircleShape,
            color = colors.primary,
            contentColor = colors.onPrimary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (state.playing) EolssuIcons.Pause else EolssuIcons.Play, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
        Text(
            stringResource(Res.string.weak_compare_listen, scorePositionLabel(state.listenFrom)),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
            color = colors.textSub,
        )
    }
    EolssuButton(stringResource(Res.string.weak_compare_practice), actions.onPractice)
}
