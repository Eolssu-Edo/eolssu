package com.ssafy.eolssu.feature.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.Bar
import com.ssafy.eolssu.core.ui.component.EnsoRing
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuStartTopBar
import com.ssafy.eolssu.core.ui.component.TintPillButton
import com.ssafy.eolssu.core.ui.component.metricLabel
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.ScoreCellDecoration
import com.ssafy.eolssu.core.ui.score.rememberReadingScoreFormat
import com.ssafy.eolssu.data.practice.CheckLoad
import com.ssafy.eolssu.feature.history.component.ComparePickSheet
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.result_score_unit
import eolssu.shared.generated.resources.weak_compare_card_label
import eolssu.shared.generated.resources.weak_compare_change_button
import eolssu.shared.generated.resources.weak_compare_check_failed
import eolssu.shared.generated.resources.weak_compare_down
import eolssu.shared.generated.resources.weak_compare_legend_now
import eolssu.shared.generated.resources.weak_compare_legend_prev
import eolssu.shared.generated.resources.weak_compare_metric_row
import eolssu.shared.generated.resources.weak_compare_metrics_close
import eolssu.shared.generated.resources.weak_compare_metrics_open
import eolssu.shared.generated.resources.weak_compare_no_delta
import eolssu.shared.generated.resources.weak_compare_none
import eolssu.shared.generated.resources.weak_compare_now
import eolssu.shared.generated.resources.weak_compare_prev
import eolssu.shared.generated.resources.weak_compare_rejected
import eolssu.shared.generated.resources.weak_compare_same
import eolssu.shared.generated.resources.weak_compare_title
import eolssu.shared.generated.resources.weak_compare_up
import eolssu.shared.generated.resources.weak_load_missing
import org.jetbrains.compose.resources.stringResource

/** 이전 연주와 비교에서 부르는 동작. */
class CompareActions(
    val onBack: () -> Unit,
    val onOpenPicker: () -> Unit,
    val onClosePicker: () -> Unit,
    val onPickRecord: (String) -> Unit,
    val onToggleMetrics: () -> Unit,
)

/**
 * COMPARE-01 이전 연주와 비교(S15P31E102-753, 시안 ③ 비교): 제목과 오른쪽 위 ‘비교 기록 바꾸기’ · 점수 비교 카드
 * (이전 점수 → 변화 칩 → 이번 일원상, ‘항목별 점수 보기’로 펼침) · 범례 · 곡 전체 정간보(이전에 틀림 = 옅은 붉은 바탕,
 * 이번에 틀림 = 붉은 테두리). 정간보는 보기 전용이고, 듣기·연습 버튼은 없다.
 */
@Composable
fun CompareScreen(state: CompareUiState, actions: CompareActions) {
    val colors = EolssuTheme.colors
    val comparison = state.comparison
    EolssuScaffold(
        containerColor = colors.groupedBackground,
        topBar = {
            EolssuStartTopBar(
                stringResource(Res.string.weak_compare_title),
                onBack = actions.onBack,
                right = {
                    if (comparison != null && comparison.previous.isNotEmpty()) {
                        TintPillButton(
                            stringResource(Res.string.weak_compare_change_button), actions.onOpenPicker,
                            Modifier.padding(end = 12.dp), icon = EolssuIcons.Calendar,
                        )
                    }
                },
            )
        },
    ) {
        if (comparison == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.loading) CircularProgressIndicator() else Text(stringResource(Res.string.weak_load_missing))
            }
            return@EolssuScaffold
        }
        val song = comparison.song
        val format = rememberReadingScoreFormat(song)
        Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val previous = state.previous
            if (previous == null) {
                Text(stringResource(Res.string.weak_compare_none), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = colors.textSub)
            } else {
                ScoreCompareCard(previous.score, comparison.current.score, state, actions.onToggleMetrics)
                CompareCheckNote(state.check)
            }
            CompareLegend(state.counts)
            JeongganboView(
                song = song,
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                metrics = format.metrics,
                look = format.look,
                cellHeights = format.cellHeights,
                decorations = compareDecorations(state.marks),
            )
        }
    }
    val pick = state.pick
    if (state.sheetOpen && pick != null) {
        ComparePickSheet(pick, actions.onClosePicker, actions.onPickRecord)
    }
}

/** 이전 · 이번 정간 꾸밈: 이전에 틀림 = 옅은 붉은 바탕, 이번에 틀림 = 2dp 붉은 테두리. */
@Composable
private fun compareDecorations(marks: Map<com.ssafy.eolssu.core.model.ScorePosition, CompareMark>): Map<Pair<Int, Int>, ScoreCellDecoration> {
    val colors = EolssuTheme.colors
    return marks.entries.associate { (p, mark) ->
        (p.column to p.row) to ScoreCellDecoration(
            fill = if (mark != CompareMark.New) colors.recFaint else null,
            ring = if (mark != CompareMark.Fixed) colors.rec else null,
        )
    }
}

/** 서버가 두 연주를 비교할 수 없다고 했거나(400) 비교 결과를 받지 못했을 때만 짧게 알린다. */
@Composable
private fun CompareCheckNote(check: CheckLoad?) {
    val text = when (check) {
        CheckLoad.Rejected -> stringResource(Res.string.weak_compare_rejected)
        CheckLoad.Failed -> stringResource(Res.string.weak_compare_check_failed)
        else -> return
    }
    Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp), color = EolssuTheme.colors.textMuted)
}

/** 붉은 칩 테두리(시안 #EBC3BC). 테마에 같은 색이 없어 여기에만 둔다. */
private val DownChipLine = Color(0xFFEBC3BC)

/**
 * 점수 비교 카드(회색 바탕 위 테두리 없는 흰 카드 · 반지름 16): 왼쪽 ‘이전’ + 이전 점수(회색 명조 30), 가운데 변화 칩 + 점선 화살표,
 * 오른쪽 연두 일원상(84) 안에 ‘이번’ + 이번 점수. 오르면 연두 ‘▲ n점’, 내리면 칩·화살표만 붉은 ‘▼ n점’,
 * 같으면 ‘= 같아요’. 서버가 총점 차이를 주지 않으면 두 기록의 총점 차이를 쓴다. 아래 ‘항목별 점수 보기’로 항목 막대를 펼친다.
 */
@Composable
private fun ScoreCompareCard(before: Int, after: Int, state: CompareUiState, onToggle: () -> Unit) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val change = state.scoreChange
    val cardLabel = stringResource(Res.string.weak_compare_card_label, before, after)
    Column(Modifier.fillMaxWidth().background(colors.surface, shape).padding(start = 12.dp, end = 12.dp, top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = cardLabel },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(Modifier.width(76.dp).clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(Res.string.weak_compare_prev), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = colors.textMuted)
                Text(
                    buildAnnotatedString {
                        append("$before")
                        withStyle(SpanStyle(fontFamily = MaterialTheme.typography.bodyLarge.fontFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium)) {
                            append(stringResource(Res.string.result_score_unit))
                        }
                    },
                    style = EolssuTheme.serif.yul.copy(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
                    color = colors.textDisabled,
                    maxLines = 1,
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ChangeChip(change)
                ChangeArrow(down = change is ScoreChange.Down, muted = change !is ScoreChange.Up && change !is ScoreChange.Down)
            }
            EnsoRing(84.dp, Modifier.clearAndSetSemantics {}) {
                Text(stringResource(Res.string.weak_compare_now), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold), color = colors.accentText)
                Text("$after", style = EolssuTheme.serif.yul.copy(fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold), color = colors.text)
            }
        }
        if (state.metricsOpen) MetricCompare(state.metricChanges)
        val open = state.metricsOpen
        val toggleLabel = stringResource(if (open) Res.string.weak_compare_metrics_close else Res.string.weak_compare_metrics_open)
        Surface(
            onClick = onToggle,
            modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 32.dp).semantics { stateDescription = toggleLabel },
            shape = RoundedCornerShape(10.dp),
            color = Color.Transparent,
            contentColor = colors.textSub,
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(toggleLabel, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold))
                Icon(EolssuIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(14.dp).rotate(if (open) 180f else 0f))
            }
        }
    }
}

/** 가운데 변화 칩(높이 26 · 13sp 굵게). 받는 중이면 칩 자리만 비워 둔다. */
@Composable
private fun ChangeChip(change: ScoreChange) {
    val colors = EolssuTheme.colors
    val (text, background, foreground, border) = when (change) {
        ScoreChange.Pending -> {
            Box(Modifier.height(26.dp))
            return
        }
        is ScoreChange.Up -> Quad(stringResource(Res.string.weak_compare_up, change.points), colors.primary, colors.onPrimary, null)
        is ScoreChange.Down -> Quad(stringResource(Res.string.weak_compare_down, change.points), colors.recFaint, colors.rec, DownChipLine)
        ScoreChange.Same -> Quad(stringResource(Res.string.weak_compare_same), colors.background, colors.textSub, colors.lineSoft)
        ScoreChange.Unavailable -> Quad(stringResource(Res.string.weak_compare_no_delta), colors.background, colors.textSub, colors.lineSoft)
    }
    val shape = RoundedCornerShape(13.dp)
    Box(
        Modifier.height(26.dp).background(background, shape).then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold), color = foreground, maxLines = 1)
    }
}

private data class Quad(val text: String, val background: Color, val foreground: Color, val border: Color?)

/** 점선 화살표(높이 12). 오르면 연두, 내리면 붉은색, 그 밖에는 회색이다. */
@Composable
private fun ChangeArrow(down: Boolean, muted: Boolean) {
    val colors = EolssuTheme.colors
    val line = when {
        down -> DownChipLine
        muted -> colors.lineSoft
        else -> colors.accentTintLine
    }
    val head = when {
        down -> colors.rec
        muted -> colors.missedErrorLine
        else -> colors.primary
    }
    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        val y = size.height / 2
        val stroke = 2.dp.toPx()
        drawLine(line, Offset(2.dp.toPx(), y), Offset(size.width - 6.dp.toPx(), y), stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
        val tip = Offset(size.width - 3.dp.toPx(), y)
        drawLine(head, Offset(tip.x - 7.dp.toPx(), y - 4.5.dp.toPx()), tip, stroke, cap = StrokeCap.Round)
        drawLine(head, Offset(tip.x - 7.dp.toPx(), y + 4.5.dp.toPx()), tip, stroke, cap = StrokeCap.Round)
    }
}

/** 펼친 항목별 점수: 범례(이전 회색 · 이번 연두)와 항목마다 나란한 두 막대 + 차이 칩. */
@Composable
private fun MetricCompare(rows: List<MetricChange>) {
    val colors = EolssuTheme.colors
    val small = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp)
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).dashedTopLine(colors.lineSoft).padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendBar(colors.missedErrorLine, stringResource(Res.string.weak_compare_prev), small)
            LegendBar(colors.primary, stringResource(Res.string.weak_compare_now), small)
        }
        rows.forEach { row ->
            val label = metricLabel(row.metric)
            val description = stringResource(Res.string.weak_compare_metric_row, label, row.before, row.after)
            Row(
                Modifier.semantics(mergeDescendants = true) { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(label, modifier = Modifier.width(44.dp).clearAndSetSemantics {}, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = colors.textSub, maxLines = 1)
                Column(Modifier.weight(1f).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Bar(row.before / 100f, colors.missedErrorLine, Modifier.fillMaxWidth().height(6.dp))
                    Bar(row.after / 100f, colors.primary, Modifier.fillMaxWidth().height(6.dp))
                }
                DeltaChip(row.delta)
            }
        }
    }
}

/** 항목 차이 칩(44×22): 오르면 연두 ‘+n’, 내리면 붉은 ‘−n’, 같으면 ‘0’. */
@Composable
private fun DeltaChip(delta: Int) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(11.dp)
    val base = Modifier.width(44.dp).height(22.dp)
    val (background, foreground, border) = when {
        delta > 0 -> Triple(colors.accentTint, colors.accentText, null)
        delta < 0 -> Triple(colors.recFaint, colors.rec, DownChipLine)
        else -> Triple(colors.background, colors.textSub, null)
    }
    Box(
        base.background(background, shape).then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(metricDeltaLabel(delta), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold), color = foreground)
    }
}

/** 항목 차이 표시: ‘+12’ · ‘−4’ · ‘0’. */
internal fun metricDeltaLabel(delta: Int): String = when {
    delta > 0 -> "+$delta"
    delta < 0 -> "−${-delta}"
    else -> "0"
}

@Composable
private fun LegendBar(color: Color, label: String, style: TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.width(12.dp).height(6.dp).background(color, RoundedCornerShape(3.dp)))
        Text(label, style = style, color = EolssuTheme.colors.textMuted)
    }
}

/** 위쪽 1dp 점선. */
private fun Modifier.dashedTopLine(color: Color): Modifier = drawBehind {
    drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
}

/** 범례: 14dp 견본(이전 = 옅은 붉은 바탕, 이번 = 흰 바탕 붉은 테두리)과 수. 두 번 모두 틀린 정간은 두 꾸밈이 겹쳐 보여 따로 적지 않는다. */
@Composable
private fun CompareLegend(counts: CompareCounts) {
    val colors = EolssuTheme.colors
    val text = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendItem(colors.recFaint, null, stringResource(Res.string.weak_compare_legend_prev, counts.previous), text)
        LegendItem(colors.surface, colors.rec, stringResource(Res.string.weak_compare_legend_now, counts.current), text)
    }
}

@Composable
private fun LegendItem(fill: Color, ring: Color?, label: String, style: TextStyle) {
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
