package com.ssafy.eolssu.feature.history.component

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.data.practice.MetricGrowth
import com.ssafy.eolssu.data.user.LearnProgressRepository
import com.ssafy.eolssu.feature.history.GrowthLoad
import com.ssafy.eolssu.feature.history.GrowthRange
import com.ssafy.eolssu.feature.history.GrowthStats
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.growth_best_by_song
import eolssu.shared.generated.resources.growth_best_none
import eolssu.shared.generated.resources.growth_by_day
import eolssu.shared.generated.resources.growth_by_week
import eolssu.shared.generated.resources.growth_change_last_month
import eolssu.shared.generated.resources.growth_change_last_week
import eolssu.shared.generated.resources.growth_count_value
import eolssu.shared.generated.resources.growth_items
import eolssu.shared.generated.resources.growth_items_basis
import eolssu.shared.generated.resources.growth_load_failed
import eolssu.shared.generated.resources.growth_minutes
import eolssu.shared.generated.resources.growth_month
import eolssu.shared.generated.resources.growth_practice_days
import eolssu.shared.generated.resources.growth_retry
import eolssu.shared.generated.resources.growth_score_change
import eolssu.shared.generated.resources.growth_score_value
import eolssu.shared.generated.resources.growth_steps
import eolssu.shared.generated.resources.growth_steps_total
import eolssu.shared.generated.resources.growth_steps_value
import eolssu.shared.generated.resources.growth_streak
import eolssu.shared.generated.resources.growth_tile_count
import eolssu.shared.generated.resources.growth_tile_score
import eolssu.shared.generated.resources.growth_tile_time
import eolssu.shared.generated.resources.growth_week
import eolssu.shared.generated.resources.result_metric_length
import eolssu.shared.generated.resources.result_metric_pitch
import eolssu.shared.generated.resources.result_metric_rhythm
import org.jetbrains.compose.resources.stringResource

/**
 * 연습 기록지의 여백·크기. 휴대폰([Compact])과 태블릿([Tablet], `Tab-Growth`)이 다르다.
 *
 * @property tablet 막대를 그래프 높이의 72%까지 그린다.
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
    val tablet: Boolean,
) {
    companion object {
        val Compact = GrowthSpacing(12.dp, 12.dp, 12.dp, 11.sp, 20.sp, 14.dp, 10.dp, 110.dp, tablet = false)
        val Tablet = GrowthSpacing(14.dp, 16.dp, 14.dp, 12.sp, 24.sp, 16.dp, 12.dp, 170.dp, tablet = true)
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

/** 고른 탭의 옅은 그림자(와이어프레임 `.seg .on` box-shadow). 기본 그림자색은 너무 진하다. */
private val SelectedTabShadow = Color.Black.copy(alpha = 0.12f)

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
                modifier = Modifier.weight(1f).height(36.dp).then(if (on) Modifier.shadow(2.dp, RoundedCornerShape(9.dp), ambientColor = SelectedTabShadow, spotColor = SelectedTabShadow) else Modifier).semantics {
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

/** 연습 시간 · 횟수 · 평균 점수 칸 세 개. 횟수 칸 아래는 주 보기면 연속 일수, 월 보기면 연습한 날 수다. */
@Composable
internal fun GrowthTiles(stats: GrowthStats, spacing: GrowthSpacing) {
    val colors = EolssuTheme.colors
    val week = stats.range == GrowthRange.Week
    val tiles = listOf(
        Triple(
            stringResource(Res.string.growth_tile_time),
            stringResource(Res.string.growth_minutes, stats.practiceMinutes),
            stringResource(if (week) Res.string.growth_change_last_week else Res.string.growth_change_last_month, signed(stats.minutesChange)),
        ),
        Triple(
            stringResource(Res.string.growth_tile_count),
            stringResource(Res.string.growth_count_value, stats.practiceCount),
            if (week) stringResource(Res.string.growth_streak, stats.streakDays) else stringResource(Res.string.growth_practice_days, stats.practiceDays),
        ),
        Triple(
            stringResource(Res.string.growth_tile_score),
            stats.averageScore?.let { stringResource(Res.string.growth_score_value, it) } ?: "–",
            stats.averageChange?.let { stringResource(Res.string.growth_score_change, signed(it.toLong())) }.orEmpty(),
        ),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.forEach { (label, value, sub) ->
            Column(
                Modifier
                    .weight(1f)
                    .background(colors.surface, RoundedCornerShape(14.dp))
                    .padding(horizontal = spacing.tilePaddingH, vertical = spacing.tilePaddingV),
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = spacing.tileLabelSize), color = colors.textMuted)
                Text(value, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.titleLarge.copy(fontSize = spacing.tileValueSize, fontWeight = FontWeight.Bold))
                Text(sub, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
            }
        }
    }
}

/** 변화량 표시: +23, -5, ±0. */
internal fun signed(value: Long): String = when {
    value > 0 -> "+$value"
    value < 0 -> "$value"
    else -> "±0"
}

/** 불러오는 중이면 도는 표시, 받지 못했으면 안내와 '다시 시도'. */
@Composable
internal fun GrowthStatus(load: GrowthLoad, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        if (load == GrowthLoad.Failed) {
            Text(stringResource(Res.string.growth_load_failed), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = colors.textMuted)
            EolssuButton(stringResource(Res.string.growth_retry), onRetry, style = EolssuButtonStyle.Secondary, small = true)
        } else {
            CircularProgressIndicator(Modifier.size(36.dp), color = colors.text, trackColor = colors.surfaceStrong, strokeWidth = 3.dp)
        }
    }
}

/** 요일·주별 연습 시간 카드. [fill]이면 카드가 받은 높이를 그래프가 채운다(태블릿 가로). */
@Composable
internal fun BarChartCard(stats: GrowthStats, spacing: GrowthSpacing, modifier: Modifier = Modifier, fill: Boolean = false) {
    GrowthOutlinedCard(spacing, modifier) {
        Text(
            stringResource(if (stats.range == GrowthRange.Week) Res.string.growth_by_day else Res.string.growth_by_week),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
        BarChart(stats, spacing, if (fill) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(spacing.barChartHeight))
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

/** 항목별 첫 기록 → 최근 점수 카드. 같은 조건 연주끼리 비교하므로 기준 곡·BPM을 제목 아래에 적는다. */
@Composable
internal fun MetricGrowthCard(growth: MetricGrowth, spacing: GrowthSpacing) {
    GrowthOutlinedCard(spacing, gap = spacing.itemsGap) {
        Column {
            Text(stringResource(Res.string.growth_items), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Text(
                stringResource(Res.string.growth_items_basis, growth.title, growth.bpm),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = EolssuTheme.colors.textMuted,
            )
        }
        growth.changes.forEach { GrowthBarRow(it.metric, it.first, it.last) }
    }
}

/** 완료한 단계(기초부터 배우기 9단계 중) · 곡별 최고 점수 두 칸. */
@Composable
internal fun GrowthLinks(stats: GrowthStats, completedSteps: Int, spacing: GrowthSpacing, onOpenSteps: () -> Unit, onOpenRecords: () -> Unit) {
    val colors = EolssuTheme.colors
    val padding = Modifier.padding(horizontal = spacing.tilePaddingH, vertical = spacing.tilePaddingV)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = spacing.tileLabelSize)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(onClick = onOpenSteps, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = colors.surface) {
            Column(padding) {
                Text(stringResource(Res.string.growth_steps), style = labelStyle, color = colors.textMuted)
                val value = stringResource(Res.string.growth_steps_value, completedSteps)
                val total = stringResource(Res.string.growth_steps_total, LearnProgressRepository.STAGE_COUNT)
                Text(
                    buildAnnotatedString {
                        append("$value ")
                        withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)) { append(total) }
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
                    if (stats.bestBySong.isEmpty()) stringResource(Res.string.growth_best_none) else stats.bestBySong.joinToString(" · ") { "${it.title} ${it.bestTotalScore}" },
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
