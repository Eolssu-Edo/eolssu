package com.ssafy.eolssu.feature.history.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuMenuItem
import com.ssafy.eolssu.core.ui.component.EolssuMenuShape
import com.ssafy.eolssu.feature.history.GrowthLoad
import com.ssafy.eolssu.feature.history.GrowthMetric
import com.ssafy.eolssu.feature.history.GrowthRange
import com.ssafy.eolssu.feature.history.PeriodChart
import com.ssafy.eolssu.feature.history.weekBarLabels
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.growth_axis_count
import eolssu.shared.generated.resources.growth_axis_hours
import eolssu.shared.generated.resources.growth_axis_minutes
import eolssu.shared.generated.resources.growth_bar_month
import eolssu.shared.generated.resources.growth_chart_desc
import eolssu.shared.generated.resources.growth_count_value
import eolssu.shared.generated.resources.growth_empty
import eolssu.shared.generated.resources.growth_hours_minutes
import eolssu.shared.generated.resources.growth_load_failed
import eolssu.shared.generated.resources.growth_metric_count
import eolssu.shared.generated.resources.growth_metric_time
import eolssu.shared.generated.resources.growth_minutes
import eolssu.shared.generated.resources.growth_month
import eolssu.shared.generated.resources.growth_range_days
import eolssu.shared.generated.resources.growth_range_months
import eolssu.shared.generated.resources.growth_range_pick
import eolssu.shared.generated.resources.growth_range_years
import eolssu.shared.generated.resources.growth_retry
import eolssu.shared.generated.resources.growth_unit_count
import eolssu.shared.generated.resources.growth_unit_hour
import eolssu.shared.generated.resources.growth_unit_minute
import eolssu.shared.generated.resources.growth_week
import eolssu.shared.generated.resources.growth_zoom_in
import eolssu.shared.generated.resources.growth_zoom_out
import org.jetbrains.compose.resources.stringResource

/**
 * 연습 기록지의 여백·크기. 휴대폰([Compact])과 태블릿([Tablet])이 다르다.
 *
 * @property chartHeight 기간 그래프 높이(시안 150). 태블릿 가로는 카드가 남은 높이를 채운다.
 */
internal data class GrowthSpacing(val gap: Dp, val cardPadding: Dp, val chartHeight: Dp) {
    companion object {
        val Compact = GrowthSpacing(12.dp, 16.dp, 150.dp)
        val Tablet = GrowthSpacing(16.dp, 20.dp, 200.dp)
    }
}

/** 시안 색 중 테마에 이름이 없는 것: 지난 막대(`#D2CCC1`)와 고를 수 없는 날짜 글자(`#C9C3B8`). */
internal object GrowthColors {
    val PastBar = Color(0xFFD2CCC1)
    val DisabledDay = Color(0xFFC9C3B8)
}

/** 흰 바탕 · 옅은 테두리(`#E8E4DA`) · 모서리 20 카드(연주 결과 D안 톤). */
@Composable
internal fun GrowthCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    gap: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = EolssuTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, colors.lineSoft, RoundedCornerShape(20.dp))
            .background(colors.surface, RoundedCornerShape(20.dp))
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/** 주별 · 월별 탭: 회색(`#EEEBE3`) 바탕 위 고른 칸만 흰 알약. */
@Composable
internal fun RangeTabs(range: GrowthRange, onRange: (GrowthRange) -> Unit) {
    val colors = EolssuTheme.colors
    Row(
        Modifier.fillMaxWidth().background(colors.surfaceStrong, RoundedCornerShape(12.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
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
                color = if (on) colors.surface else Color.Transparent,
                contentColor = if (on) colors.text else colors.textSub,
                shadowElevation = if (on) 1.dp else 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                    )
                }
            }
        }
    }
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

/** 연습 시간 글: 1시간 이상이면 'N시간 M분', 아니면 'M분'. */
@Composable
internal fun minutesLabel(minutes: Long): String =
    if (minutes >= 60) stringResource(Res.string.growth_hours_minutes, minutes / 60, minutes % 60)
    else stringResource(Res.string.growth_minutes, minutes)

/**
 * 큰 숫자: 숫자는 Noto Serif KR([numberSize]), 단위는 본문 글꼴([unitSize]). 연습 시간은 1시간 이상이면 'N시간 M분'으로 나눈다.
 */
@Composable
internal fun bigValue(value: Long, metric: GrowthMetric, numberSize: TextUnit, unitSize: TextUnit): AnnotatedString {
    val serif = EolssuTheme.serif.yul.fontFamily
    val number = SpanStyle(fontFamily = serif, fontSize = numberSize, fontWeight = FontWeight.Bold)
    val unit = SpanStyle(fontSize = unitSize, fontWeight = FontWeight.SemiBold)
    val parts: List<Pair<Long, String>> = when {
        metric == GrowthMetric.Count -> listOf(value to stringResource(Res.string.growth_unit_count))
        value >= 60 -> listOf(value / 60 to stringResource(Res.string.growth_unit_hour), value % 60 to stringResource(Res.string.growth_unit_minute))
        else -> listOf(value to stringResource(Res.string.growth_unit_minute))
    }
    return buildAnnotatedString {
        parts.forEachIndexed { i, (n, u) ->
            if (i > 0) append(" ")
            withStyle(number) { append("$n") }
            withStyle(unit) { append(u) }
        }
    }
}

/** 기간 표시: 주별 '9월 21일 – 11월 15일', 월별 '1월 – 12월'(해가 다르면 '2025년 12월 – 2026년 11월'). */
@Composable
internal fun rangeLabel(chart: PeriodChart): String {
    val from = KoreanDateTime.ofEpochDay(chart.fromDay)
    val to = KoreanDateTime.ofEpochDay(chart.toDay)
    return when {
        chart.range == GrowthRange.Week -> stringResource(Res.string.growth_range_days, from.month, from.day, to.month, to.day)
        from.year != to.year -> stringResource(Res.string.growth_range_years, from.year, from.month, to.year, to.month)
        else -> stringResource(Res.string.growth_range_months, from.month, to.month)
    }
}

/**
 * 기간 그래프 카드(A안 2차): 위 기간 표시(누르면 [onOpenPicker]) · '연습 시간 ▾' 단위 고르기, 기간 전체 합계와 다른 값 한 줄,
 * 점선 두 줄(최댓값 · 절반)과 오른쪽 눈금이 있는 막대(마지막 막대만 연두), 아래 막대 이름과 돋보기.
 * [fill]이면 그래프가 카드가 받은 남은 높이를 채운다(태블릿 가로).
 */
@Composable
internal fun PeriodChartCard(
    chart: PeriodChart,
    metric: GrowthMetric,
    dense: Boolean,
    empty: Boolean,
    spacing: GrowthSpacing,
    onOpenPicker: () -> Unit,
    onMetric: (GrowthMetric) -> Unit,
    onToggleDense: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
) {
    val colors = EolssuTheme.colors
    GrowthCard(modifier, padding = spacing.cardPadding) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val pick = stringResource(Res.string.growth_range_pick)
            Row(
                Modifier
                    .offset(x = (-6).dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClickLabel = pick, onClick = onOpenPicker)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(rangeLabel(chart), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = colors.textSub, maxLines = 1)
                Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.textSub)
            }
            Spacer(Modifier.weight(1f))
            MetricPicker(metric, onMetric)
        }
        val other = if (metric == GrowthMetric.Time) GrowthMetric.Count else GrowthMetric.Time
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val total = if (metric == GrowthMetric.Time) chart.totalMinutes else chart.totalCount
            Text(bigValue(total, metric, 38.sp, 16.sp), style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 46.sp))
            Text(
                buildAnnotatedString {
                    append(stringResource(if (other == GrowthMetric.Time) Res.string.growth_metric_time else Res.string.growth_metric_count))
                    append(" ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.text)) {
                        append(if (other == GrowthMetric.Time) minutesLabel(chart.totalMinutes) else stringResource(Res.string.growth_count_value, chart.totalCount))
                    }
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.textMuted,
            )
            if (empty) {
                Text(stringResource(Res.string.growth_empty), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
            }
        }
        PeriodBars(chart, metric, dense, if (fill) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(spacing.chartHeight))
        BarLabelsRow(chart, dense, onToggleDense)
    }
}

/** 축 눈금 칸 너비와 그래프 사이 간격. 막대 이름 줄도 같은 너비를 비워 막대와 이름을 맞춘다. */
private val AxisWidth = 48.dp
private val AxisGap = 8.dp

@Composable
private fun PeriodBars(chart: PeriodChart, metric: GrowthMetric, dense: Boolean, modifier: Modifier) {
    val colors = EolssuTheme.colors
    val axisScale = chart.axis(metric)
    val max = axisScale.top
    val values = chart.bars.map { it.value(metric) }
    val rangeName = stringResource(if (chart.range == GrowthRange.Week) Res.string.growth_week else Res.string.growth_month)
    val metricName = stringResource(if (metric == GrowthMetric.Time) Res.string.growth_metric_time else Res.string.growth_metric_count)
    val description = stringResource(Res.string.growth_chart_desc, rangeName, metricName, values.joinToString(", "))
    val barWidth = if (dense) (if (chart.range == GrowthRange.Week) 12.dp else 10.dp) else 18.dp
    val axis = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp)
    Row(modifier.clearAndSetSemantics { contentDescription = description }) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val height = maxHeight
            Canvas(Modifier.fillMaxSize()) {
                val dotted = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 2.5.dp.toPx()))
                val stroke = 1.dp.toPx()
                drawLine(colors.line, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke, pathEffect = dotted)
                drawLine(colors.line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke, pathEffect = dotted)
                drawLine(colors.line, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
            }
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                values.forEachIndexed { i, v ->
                    val last = i == values.lastIndex
                    val barHeight = if (v > 0) maxOf(6.dp, height * (v.toFloat() / max)) else 3.dp
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier
                                .width(barWidth)
                                .height(barHeight)
                                .background(
                                    when {
                                        v <= 0 -> colors.surfaceStrong
                                        last -> colors.primary
                                        else -> GrowthColors.PastBar
                                    },
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(AxisGap))
        BoxWithConstraints(Modifier.width(AxisWidth).fillMaxHeight()) {
            val muted = colors.textMuted
            Text(
                stringResource(when { metric != GrowthMetric.Time -> Res.string.growth_axis_count; axisScale.hours -> Res.string.growth_axis_hours; else -> Res.string.growth_axis_minutes }),
                Modifier.align(Alignment.TopEnd).offset(y = (-16).dp),
                style = axis,
                color = muted,
            )
            Text("${axisScale.top / axisScale.unit}", Modifier.align(Alignment.TopEnd).offset(y = 2.dp), style = axis, color = muted)
            Text("${axisScale.half / axisScale.unit}", Modifier.align(Alignment.TopEnd).offset(y = maxHeight / 2 + 2.dp), style = axis, color = muted)
        }
    }
}

@Composable
private fun BarLabelsRow(chart: PeriodChart, dense: Boolean, onToggleDense: () -> Unit) {
    val colors = EolssuTheme.colors
    val labels = when (chart.range) {
        GrowthRange.Week -> weekBarLabels(chart.bars)
        // 12개월은 휴대폰 너비에서 'N월'이 서로 붙어 숫자만 둔다(달 이름은 위 기간 표시에 있다).
        GrowthRange.Month -> chart.bars.map { bar ->
            val month = KoreanDateTime.ofEpochDay(bar.fromDay).month
            if (dense) "$month" else stringResource(Res.string.growth_bar_month, month)
        }
    }
    val size = 11.sp
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // 이름은 그래프의 일부로 읽히므로(설명에 값이 있다) 따로 읽지 않는다.
        Row(Modifier.weight(1f).clearAndSetSemantics {}) {
            labels.forEachIndexed { i, label ->
                val last = i == labels.lastIndex
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = size, lineHeight = 15.sp, fontWeight = if (last) FontWeight.Bold else FontWeight.Normal),
                    color = if (last) colors.text else colors.textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Spacer(Modifier.width(AxisGap))
        Box(Modifier.width(AxisWidth), contentAlignment = Alignment.CenterEnd) {
            Surface(
                onClick = onToggleDense,
                modifier = Modifier
                    .padding(end = 2.dp)
                    .size(44.dp)
                    .shadow(2.dp, CircleShape, ambientColor = colors.text.copy(alpha = 0.08f), spotColor = colors.text.copy(alpha = 0.08f)),
                shape = CircleShape,
                color = colors.surface,
                contentColor = colors.text,
                border = BorderStroke(1.dp, colors.lineSoft),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (dense) EolssuIcons.Zoom else EolssuIcons.ZoomOut,
                        contentDescription = stringResource(if (dense) Res.string.growth_zoom_in else Res.string.growth_zoom_out),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/** '연습 시간 ▾' 알약과 연습 시간 · 연습 횟수 메뉴. */
@Composable
private fun MetricPicker(metric: GrowthMetric, onMetric: (GrowthMetric) -> Unit) {
    val colors = EolssuTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val names = mapOf(
        GrowthMetric.Time to stringResource(Res.string.growth_metric_time),
        GrowthMetric.Count to stringResource(Res.string.growth_metric_count),
    )
    Box {
        Row(
            Modifier
                .height(32.dp)
                .clip(CircleShape)
                .border(1.dp, colors.lineSoft, CircleShape)
                .clickable(role = Role.DropdownList) { expanded = true }
                .padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(names.getValue(metric), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
            Icon(EolssuIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.text)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = EolssuMenuShape,
            containerColor = colors.surface,
            shadowElevation = 8.dp,
            modifier = Modifier.width(140.dp).padding(horizontal = 6.dp),
        ) {
            names.forEach { (m, name) ->
                EolssuMenuItem(name, m == metric, onClick = {
                    expanded = false
                    onMetric(m)
                })
            }
        }
    }
}
