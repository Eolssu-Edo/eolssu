package com.ssafy.eolssu.feature.history.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.feature.history.GrowthRange
import com.ssafy.eolssu.feature.history.GrowthStats
import com.ssafy.eolssu.feature.history.GrowthUiState
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.growth_best_by_song
import eolssu.shared.generated.resources.growth_by_day
import eolssu.shared.generated.resources.growth_by_week
import eolssu.shared.generated.resources.growth_items
import eolssu.shared.generated.resources.growth_minutes
import eolssu.shared.generated.resources.growth_missions
import eolssu.shared.generated.resources.growth_missions_total
import eolssu.shared.generated.resources.growth_missions_value
import eolssu.shared.generated.resources.growth_month
import eolssu.shared.generated.resources.growth_trend
import eolssu.shared.generated.resources.growth_trend_range
import eolssu.shared.generated.resources.growth_trend_section
import eolssu.shared.generated.resources.growth_week
import eolssu.shared.generated.resources.records_today
import eolssu.shared.generated.resources.result_metric_length
import eolssu.shared.generated.resources.result_metric_pitch
import eolssu.shared.generated.resources.result_metric_rhythm
import org.jetbrains.compose.resources.stringResource

/**
 * 연습 기록지의 여백·크기. 휴대폰([Compact])과 태블릿([Tablet], `Tab-Growth`)이 다르다.
 *
 * @property tablet 막대를 그래프 높이의 72%까지 그리고, 점수 변화 그래프는 와이어프레임 SVG처럼 비율을 지켜 가운데에 키운다.
 */
internal data class GrowthSpacing(
    val gap: Dp,
    val tilePaddingH: Dp,
    val tilePaddingV: Dp,
    val tileLabelSize: TextUnit,
    val tileValueSize: TextUnit,
    val cardPadding: Dp,
    val itemsGap: Dp,
    val barChartHeight: Dp,
    val trendHeight: Dp,
    val tablet: Boolean,
) {
    companion object {
        val Compact = GrowthSpacing(12.dp, 12.dp, 12.dp, 11.sp, 20.sp, 14.dp, 10.dp, 110.dp, 104.dp, tablet = false)
        val Tablet = GrowthSpacing(14.dp, 16.dp, 14.dp, 12.sp, 24.sp, 16.dp, 12.dp, 170.dp, 170.dp, tablet = true)
    }
}

@Composable
private fun GrowthOutlinedCard(
    spacing: GrowthSpacing,
    modifier: Modifier = Modifier,
    gap: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().border(1.dp, EolssuTheme.colors.line, MaterialTheme.shapes.medium).padding(spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

@Composable
internal fun RangeTabs(range: GrowthRange, onRange: (GrowthRange) -> Unit) {
    val colors = EolssuTheme.colors
    Row(
        Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(12.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(GrowthRange.Week to Res.string.growth_week, GrowthRange.Month to Res.string.growth_month).forEach { (r, label) ->
            val on = r == range
            Surface(
                onClick = { onRange(r) },
                modifier = Modifier.weight(1f).height(36.dp).then(if (on) Modifier.shadow(1.dp, RoundedCornerShape(9.dp)) else Modifier).semantics {
                    role = Role.Tab
                    selected = on
                },
                shape = RoundedCornerShape(9.dp),
                color = if (on) colors.background else colors.surface,
                contentColor = if (on) colors.text else colors.textMuted,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(stringResource(label), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

/** 연습 시간 · 횟수 · 평균 점수 칸 세 개. */
@Composable
internal fun GrowthTiles(stats: GrowthStats, spacing: GrowthSpacing) {
    val colors = EolssuTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.tiles.forEach { tile ->
            Column(
                Modifier
                    .weight(1f)
                    .background(colors.surface, RoundedCornerShape(14.dp))
                    .padding(horizontal = spacing.tilePaddingH, vertical = spacing.tilePaddingV),
            ) {
                Text(tile.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = spacing.tileLabelSize), color = colors.textMuted)
                Text(tile.value, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.titleLarge.copy(fontSize = spacing.tileValueSize, fontWeight = FontWeight.Bold))
                Text(tile.sub, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
            }
        }
    }
}

/** 요일·주별 연습 시간 카드. [fill]이면 카드가 받은 높이를 그래프가 채운다(태블릿 가로). */
@Composable
internal fun BarChartCard(state: GrowthUiState, spacing: GrowthSpacing, modifier: Modifier = Modifier, fill: Boolean = false) {
    GrowthOutlinedCard(spacing, modifier) {
        Text(
            stringResource(if (state.range == GrowthRange.Week) Res.string.growth_by_day else Res.string.growth_by_week),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
        BarChart(state.stats, spacing, if (fill) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(spacing.barChartHeight))
    }
}

@Composable
private fun BarChart(stats: GrowthStats, spacing: GrowthSpacing, modifier: Modifier) {
    val colors = EolssuTheme.colors
    val max = stats.bars.maxOf { it.minutes }.coerceAtLeast(1)
    val description = stats.bars.joinToString(", ") { "${it.label} ${it.minutes}분" }
    BoxWithConstraints(modifier.clearAndSetSemantics { contentDescription = description }) {
        val barMax = if (spacing.tablet) maxHeight * 0.72f else 72.dp
        Row(
            Modifier.fillMaxWidth().fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // 주별 보기는 막대 4개라도 7칸 너비를 유지한다(와이어프레임).
            (0 until 7).forEach { i ->
                val bar = stats.bars.getOrNull(i)
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom)) {
                    if (bar != null) {
                        val strong = i == stats.highlightBar
                        Text(
                            if (bar.minutes > 0) stringResource(Res.string.growth_minutes, bar.minutes) else "–",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = colors.textMuted,
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((barMax * (bar.minutes.toFloat() / max)).coerceAtLeast(2.dp))
                                .background(if (strong) colors.text else colors.lineStrong, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)),
                        )
                        Text(
                            bar.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal),
                            color = if (strong) colors.text else colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

/** 최근 점수 변화 선 그래프. 와이어프레임 SVG(310×104) 좌표를 쓴다. */
@Composable
internal fun TrendCard(state: GrowthUiState, todayEpochDay: Long, spacing: GrowthSpacing, chartHeight: Dp = spacing.trendHeight) {
    val colors = EolssuTheme.colors
    val points = state.trend
    val measurer = rememberTextMeasurer()
    val todayLabel = stringResource(Res.string.records_today)
    val sectionTemplate = stringResource(Res.string.growth_trend_section, "%s")
    val labels = points.map { r ->
        when {
            r.recordedAt.epochDay == todayEpochDay -> todayLabel
            r.section != null -> sectionTemplate.replace("%s", "${r.recordedAt.month}/${r.recordedAt.day}")
            else -> "${r.recordedAt.month}/${r.recordedAt.day}"
        }
    }
    GrowthOutlinedCard(spacing) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(Res.string.growth_trend, points.size), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Text(stringResource(Res.string.growth_trend_range, points.first().score, points.last().score), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
        }
        val description = points.joinToString(", ") { "${it.score}점" }
        Canvas(Modifier.fillMaxWidth().height(chartHeight).clearAndSetSemantics { contentDescription = description }) {
            // 휴대폰은 너비에 맞춰 늘이고, 태블릿은 SVG처럼 비율을 지켜 높이에 맞춘 뒤 가운데에 둔다(글자·선도 함께 키운다).
            val s = if (spacing.tablet) minOf(size.width / 310f, size.height / 104f) else size.width / 310f
            val k = if (spacing.tablet) (s / density).coerceAtLeast(1f) else 1f
            val left = (size.width - 310f * s) / 2f
            listOf(18f, 48f, 78f).forEach { y -> drawLine(colors.surfaceStrong, Offset(left, y * s), Offset(left + 310f * s, y * s), 1.dp.toPx()) }
            val step = if (points.size > 1) 280f / (points.size - 1) else 0f
            // 점수 60~90점을 그래프 높이(y 84~14)에 맞춘다.
            val coords = points.mapIndexed { i, r -> Offset(left + (14f + step * i) * s, (84f - (r.score - 60).coerceIn(0, 30) / 30f * 70f) * s) }
            for (i in 0 until coords.size - 1) drawLine(colors.text, coords[i], coords[i + 1], 2.dp.toPx() * k)
            coords.forEachIndexed { i, c ->
                val last = i == coords.lastIndex
                if (last) {
                    drawCircle(colors.text, 5.dp.toPx() * k, c)
                } else {
                    drawCircle(colors.background, 4.dp.toPx() * k, c)
                    drawCircle(colors.text, 4.dp.toPx() * k, c, style = Stroke(2.dp.toPx() * k))
                }
                val score = measurer.measure(
                    "${points[i].score}",
                    TextStyle(fontSize = (if (last) 11.sp else 10.sp) * k, fontWeight = if (last) FontWeight.Bold else FontWeight.Normal, color = if (last) colors.text else colors.textMuted),
                )
                drawText(score, topLeft = Offset(c.x - score.size.width / 2f, c.y - 12.dp.toPx() * k - score.size.height))
                val label = measurer.measure(labels[i], TextStyle(fontSize = 9.sp * k, color = if (last) colors.text else colors.textMuted))
                drawText(label, topLeft = Offset(c.x - label.size.width / 2f, size.height - label.size.height))
            }
        }
    }
}

/** 항목별 첫 기록 → 최근 점수 카드. */
@Composable
internal fun MetricGrowthCard(state: GrowthUiState, spacing: GrowthSpacing) {
    GrowthOutlinedCard(spacing, gap = spacing.itemsGap) {
        Text(stringResource(Res.string.growth_items), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
        state.metricGrowth.forEach { (metric, before, after) -> GrowthBarRow(metric, before, after) }
    }
}

/** 완료한 미션 · 곡별 최고 점수 두 칸. */
@Composable
internal fun GrowthLinks(state: GrowthUiState, spacing: GrowthSpacing, onOpenMissions: () -> Unit, onOpenRecords: () -> Unit) {
    val colors = EolssuTheme.colors
    val padding = Modifier.padding(horizontal = spacing.tilePaddingH, vertical = spacing.tilePaddingV)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = spacing.tileLabelSize)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(onClick = onOpenMissions, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = colors.surface) {
            Column(padding) {
                Text(stringResource(Res.string.growth_missions), style = labelStyle, color = colors.textMuted)
                Text(
                    buildAnnotatedString {
                        append(stringResource(Res.string.growth_missions_value, 5) + " ")
                        withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)) { append(stringResource(Res.string.growth_missions_total, 14)) }
                    },
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
        Surface(onClick = onOpenRecords, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = colors.surface) {
            Column(padding) {
                Text(stringResource(Res.string.growth_best_by_song), style = labelStyle, color = colors.textMuted)
                Text(
                    state.bestBySong.joinToString(" · ") { (title, score) -> "$title $score" },
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.5.sp),
                )
            }
        }
    }
}

@Composable
private fun GrowthBarRow(metric: EvaluationMetric, before: Int, after: Int) {
    val colors = EolssuTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(
                when (metric) {
                    EvaluationMetric.Pitch -> Res.string.result_metric_pitch
                    EvaluationMetric.Rhythm -> Res.string.result_metric_rhythm
                    EvaluationMetric.Length -> Res.string.result_metric_length
                },
            ),
            modifier = Modifier.width(52.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
        )
        Layout(
            content = {
                Box(Modifier.background(colors.surfaceStrong, RoundedCornerShape(4.dp)))
                Box(Modifier.background(colors.lineStrong, RoundedCornerShape(4.dp)))
                Box(Modifier.background(colors.text, RoundedCornerShape(2.dp)))
            },
            modifier = Modifier.weight(1f).height(12.dp),
        ) { measurables, constraints ->
            val w = constraints.maxWidth
            val track = measurables[0].measure(Constraints.fixed(w, 8.dp.roundToPx()))
            val fill = measurables[1].measure(Constraints.fixed((w * before / 100f).toInt(), 8.dp.roundToPx()))
            val mark = measurables[2].measure(Constraints.fixed(4.dp.roundToPx(), 12.dp.roundToPx()))
            layout(w, 12.dp.roundToPx()) {
                track.place(0, 2.dp.roundToPx())
                fill.place(0, 2.dp.roundToPx())
                mark.place((w * after / 100f).toInt(), 0)
            }
        }
        Text(
            buildAnnotatedString {
                append("$before → ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$after") }
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
        )
    }
}
