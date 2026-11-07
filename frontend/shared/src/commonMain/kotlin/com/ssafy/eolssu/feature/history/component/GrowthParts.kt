package com.ssafy.eolssu.feature.history.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.feature.history.ScoreTrendPoint
import com.ssafy.eolssu.feature.history.TrendGridLines
import com.ssafy.eolssu.feature.history.TrendHeight
import com.ssafy.eolssu.feature.history.TrendWidth
import com.ssafy.eolssu.feature.history.scoreTrendDots
import com.ssafy.eolssu.feature.history.ScoreTrendActions
import com.ssafy.eolssu.feature.history.ScoreTrendState
import com.ssafy.eolssu.core.ui.component.EolssuChip
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import eolssu.shared.generated.resources.growth_trend_basic_only
import eolssu.shared.generated.resources.growth_trend_empty
import eolssu.shared.generated.resources.growth_trend_instrument
import eolssu.shared.generated.resources.growth_trend_no_basic
import eolssu.shared.generated.resources.growth_trend_song_label
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import eolssu.shared.generated.resources.growth_trend_failed
import eolssu.shared.generated.resources.growth_trend_mixed
import eolssu.shared.generated.resources.growth_trend_song
import eolssu.shared.generated.resources.growth_per_week
import eolssu.shared.generated.resources.growth_score_trend
import eolssu.shared.generated.resources.growth_score_trend_desc
import eolssu.shared.generated.resources.growth_score_trend_range
import eolssu.shared.generated.resources.growth_score_trend_today
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
import com.ssafy.eolssu.feature.history.GrowthLoad
import com.ssafy.eolssu.feature.history.GrowthRange
import com.ssafy.eolssu.feature.history.GrowthStats
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.growth_by_day
import eolssu.shared.generated.resources.growth_by_week
import eolssu.shared.generated.resources.growth_change_last_month
import eolssu.shared.generated.resources.growth_change_last_week
import eolssu.shared.generated.resources.growth_count_value
import eolssu.shared.generated.resources.growth_items
import eolssu.shared.generated.resources.growth_load_failed
import eolssu.shared.generated.resources.growth_minutes
import eolssu.shared.generated.resources.growth_month
import eolssu.shared.generated.resources.growth_retry
import eolssu.shared.generated.resources.growth_score_change
import eolssu.shared.generated.resources.growth_score_value
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
        modifier.fillMaxWidth().border(1.dp, EolssuTheme.colors.lineSoft, RoundedCornerShape(16.dp)).padding(spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

@Composable
internal fun RangeTabs(range: GrowthRange, onRange: (GrowthRange) -> Unit) {
    val colors = EolssuTheme.colors
    Row(
        // 보드: 흰 바탕 · `#E8E4DA` 테두리 · 반지름 12, 고른 탭은 연두 바탕.
        Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(12.dp)).border(1.dp, colors.lineSoft, RoundedCornerShape(12.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(GrowthRange.Week to Res.string.growth_week, GrowthRange.Month to Res.string.growth_month).forEach { (r, label) ->
            val on = r == range
            Surface(
                onClick = { onRange(r) },
                modifier = Modifier.weight(1f).height(36.dp).semantics {
                    role = Role.Tab
                    selected = on
                },
                shape = RoundedCornerShape(9.dp),
                color = if (on) colors.primary else Color.Transparent,
                contentColor = if (on) colors.onPrimary else colors.textMuted,
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
            // 보드: 주 보기는 연속 일수, 월 보기는 주 평균 횟수(‘주 3.5회’).
            if (week) stringResource(Res.string.growth_streak, stats.streakDays) else stringResource(Res.string.growth_per_week, perWeekLabel(stats.perWeek)),
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
                    .border(1.dp, colors.lineSoft, RoundedCornerShape(14.dp))
                    .padding(horizontal = spacing.tilePaddingH, vertical = spacing.tilePaddingV),
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = spacing.tileLabelSize), color = colors.textMuted)
                Text(value, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyLarge.copy(fontSize = spacing.tileValueSize, fontWeight = FontWeight.Bold))
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
                                // 보드: 오늘 막대 연두, 나머지 `#9A948A`.
                                .background(if (strong) colors.primary else colors.textDisabled, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)),
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

/** 항목별 첫 기록 → 최근 점수 카드(보드: 제목만, 이전은 `#9A948A` 막대 · 최근은 연두 표식). 같은 조건 연주끼리 비교한 값이다. */
@Composable
internal fun MetricGrowthCard(growth: MetricGrowth, spacing: GrowthSpacing) {
    GrowthOutlinedCard(spacing, gap = spacing.itemsGap) {
        Text(stringResource(Res.string.growth_items), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
        growth.changes.forEach { GrowthBarRow(it.metric, it.first, it.last) }
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
                Box(Modifier.background(colors.textDisabled, RoundedCornerShape(4.dp)))
                Box(Modifier.background(colors.primary, RoundedCornerShape(2.dp)))
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

/** 주 평균 횟수 표시: 3.5 → ‘3.5’, 3.0 → ‘3’. 소수 첫째 자리에서 반올림한다. */
internal fun perWeekLabel(value: Double): String {
    val tenths = kotlin.math.round(value * 10).toLong()
    return if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}

/**
 * 보드 ‘점수 변화 · 최근 5회’ 카드(GRO-03): 오른쪽 위 ‘첫 점수 → 마지막 점수’, 아래 꺾은선(가로선 3개 · 점 · 점수 · 날짜, 마지막 점은 연두).
 * 곡 하나씩 기본 평가(곡 전체 연주)만 그리고(`docs/specs/growth-score-trend.md`), 제목 아래에 악기 칩과 곡 고르기를 둔다.
 * 조건(속도·악보 판본·채점 모델)이 섞인 추이는 그래프 아래에 참고용이라고 알린다.
 */
@Composable
internal fun ScoreTrendCard(
    trend: ScoreTrendState,
    today: KoreanDateTime,
    spacing: GrowthSpacing,
    actions: ScoreTrendActions,
    modifier: Modifier = Modifier,
) {
    val colors = EolssuTheme.colors
    val view = trend.view
    // 실패하면 앞서 받은 다른 곡의 점수를 보이지 않는다.
    val points = if (trend.failed) emptyList() else view?.points.orEmpty()
    val note = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
    GrowthOutlinedCard(spacing, modifier, gap = 8.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(Res.string.growth_score_trend, points.size.takeIf { it > 0 } ?: 5),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
            if (points.isNotEmpty()) {
                Text(
                    stringResource(Res.string.growth_score_trend_range, points.first().total, points.last().total),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
        if (trend.basicSongs.isNotEmpty()) {
            Text(stringResource(Res.string.growth_trend_basic_only), style = note.copy(fontSize = 11.5.sp), color = colors.textMuted)
            ScoreTrendControls(trend, actions)
        }
        when {
            trend.failed -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.growth_trend_failed), Modifier.weight(1f), style = note, color = colors.textMuted)
                EolssuButton(stringResource(Res.string.growth_retry), actions.onRetry, style = EolssuButtonStyle.Secondary, small = true)
            }
            view == null || (points.isEmpty() && trend.loading) -> Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), color = colors.text, trackColor = colors.surfaceStrong, strokeWidth = 2.dp)
            }
            // 재평가만 있으면 기본 평가가 없는 것으로 본다.
            trend.basicSongs.isEmpty() -> Text(stringResource(Res.string.growth_trend_empty), style = note, color = colors.textMuted)
            points.isEmpty() -> Text(stringResource(Res.string.growth_trend_no_basic), style = note, color = colors.textMuted)
            else -> {
                ScoreTrendChart(points, today)
                if (view.mixedConditions) Text(stringResource(Res.string.growth_trend_mixed), style = note, color = colors.textMuted)
            }
        }
    }
}

/**
 * 악기 칩(고를 수 있는 곡이 있는 악기만, 많으면 가로로 밀어 본다)과 그 악기 곡의 고르기(드롭다운). 받는 중·실패해도 방금 고른 악기·곡을 보인다.
 * 악기 대응을 받지 못했으면 악기 줄 없이 곡만 고른다. 받는 중이면 곡 고르기 옆에 작은 진행 표시가 보인다.
 */
@Composable
private fun ScoreTrendControls(trend: ScoreTrendState, actions: ScoreTrendActions) {
    val colors = EolssuTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val songLabel = stringResource(Res.string.growth_trend_song)
    val song = trend.basicSongs.firstOrNull { it.songId == trend.songId }
    val label = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val labelWidth = 32.dp
    val instruments = trend.instruments
    if (instruments.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.growth_trend_instrument), Modifier.width(labelWidth), style = label, color = colors.textMuted)
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                instruments.forEach { instrument ->
                    EolssuChip(
                        instrument.instrument?.let { stringResource(it.label) } ?: instrument.name,
                        selected = trend.selectedInstrument?.instrumentId == instrument.instrumentId,
                        onClick = { actions.onInstrument(instrument.instrumentId) },
                        small = true,
                    )
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.growth_trend_song_label), Modifier.width(labelWidth), style = label, color = colors.textMuted)
        Box(Modifier.weight(1f)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp)
                    .background(colors.surface, RoundedCornerShape(10.dp))
                    .border(1.dp, colors.lineSoft, RoundedCornerShape(10.dp))
                    .clickable(role = Role.DropdownList, onClickLabel = songLabel) { expanded = true }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    song?.title.orEmpty(),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                )
                if (trend.loading) {
                    CircularProgressIndicator(Modifier.padding(end = 6.dp).size(14.dp), color = colors.text, trackColor = colors.surfaceStrong, strokeWidth = 2.dp)
                }
                Icon(EolssuIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = colors.surface) {
                trend.instrumentSongs.forEach { item ->
                    val current = item.songId == song?.songId
                    DropdownMenuItem(
                        text = { Text(item.title, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            expanded = false
                            actions.onSong(item.songId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreTrendChart(points: List<ScoreTrendPoint>, today: KoreanDateTime) {
    val colors = EolssuTheme.colors
    val measurer = rememberTextMeasurer()
    val todayLabel = stringResource(Res.string.growth_score_trend_today)
    val labels = points.map { p -> if (p.playedAt.isSameDay(today)) todayLabel else "${p.playedAt.month}/${p.playedAt.day}" }
    val description = stringResource(Res.string.growth_score_trend_desc, points.size, points.joinToString(", ") { "${it.total}" })
    val dots = scoreTrendDots(points.map { it.total })
    val small = TextStyle(fontSize = 10.sp, color = colors.textMuted)
    val lastScore = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.text)
    val dateStyle = TextStyle(fontSize = 9.sp, color = colors.textMuted)
    Canvas(Modifier.fillMaxWidth().aspectRatio(TrendWidth / TrendHeight).clearAndSetSemantics { contentDescription = description }) {
        val k = size.width / TrendWidth
        fun at(x: Float, y: Float) = Offset(x * k, y * k)
        TrendGridLines.forEach { y -> drawLine(colors.surfaceStrong, at(0f, y), at(TrendWidth, y), strokeWidth = 1.dp.toPx()) }
        val line = Path().apply {
            dots.forEachIndexed { i, d -> if (i == 0) moveTo(d.x * k, d.y * k) else lineTo(d.x * k, d.y * k) }
        }
        drawPath(line, colors.text, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
        dots.forEachIndexed { i, d ->
            val last = i == dots.lastIndex
            val r = (if (last) 5f else 4f) * k
            drawCircle(if (last) colors.primary else colors.surface, r, at(d.x, d.y))
            drawCircle(colors.text, r, at(d.x, d.y), style = Stroke(2.dp.toPx()))
            fun label(text: String, style: TextStyle, baseline: Float) {
                val layout = measurer.measure(text, style)
                drawText(layout, topLeft = Offset(d.x * k - layout.size.width / 2f, baseline * k - layout.firstBaseline))
            }
            label("${points[i].total}", if (last) lastScore else small, d.y - 12f)
            label(labels[i], if (last) dateStyle.copy(color = colors.text) else dateStyle, 100f)
        }
    }
}
