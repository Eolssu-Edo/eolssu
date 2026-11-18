package com.ssafy.eolssu.feature.history.component

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuBottomSheet
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.history.GrowthDays
import com.ssafy.eolssu.feature.history.GrowthMetric
import com.ssafy.eolssu.feature.history.GrowthRange
import com.ssafy.eolssu.feature.history.GrowthUiState
import com.ssafy.eolssu.feature.history.heatLevel
import com.ssafy.eolssu.feature.history.minutes
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.growth_count_value
import eolssu.shared.generated.resources.growth_day_desc
import eolssu.shared.generated.resources.growth_day_desc_none
import eolssu.shared.generated.resources.growth_day_desc_practiced
import eolssu.shared.generated.resources.growth_day_desc_record
import eolssu.shared.generated.resources.growth_day_none
import eolssu.shared.generated.resources.growth_day_title
import eolssu.shared.generated.resources.growth_metric_count
import eolssu.shared.generated.resources.growth_metric_time
import eolssu.shared.generated.resources.growth_month_title
import eolssu.shared.generated.resources.growth_next_month
import eolssu.shared.generated.resources.growth_prev_month
import eolssu.shared.generated.resources.growth_sheet_hint_month
import eolssu.shared.generated.resources.growth_sheet_hint_week
import eolssu.shared.generated.resources.growth_today
import eolssu.shared.generated.resources.growth_unit_count
import eolssu.shared.generated.resources.growth_unit_minute
import eolssu.shared.generated.resources.growth_weekdays
import eolssu.shared.generated.resources.growth_year_month_title
import org.jetbrains.compose.resources.stringResource

/** 진하기 단계별 날짜 원 색(0 없음 · 1~4): `#EFF2D6` · `#DEE5AD` · `#CED883` · `#ADBE31`. */
@Composable
private fun heatFills(): List<Color> {
    val colors = EolssuTheme.colors
    return listOf(Color.Transparent, colors.accentTint, colors.accentTintStrong, colors.accentTintLine, colors.primary)
}

@Composable
private fun weekdayNames(): List<String> = stringResource(Res.string.growth_weekdays).split(',').map { it.trim() }

/** 달 이름: 올해면 '11월', 다른 해면 '2025년 12월'. [alwaysYear]면 늘 해를 붙인다(기간 시트). */
@Composable
private fun monthTitle(monthStart: Long, todayYear: Int, alwaysYear: Boolean = false): String {
    val date = KoreanDateTime.ofEpochDay(monthStart)
    return if (alwaysYear || date.year != todayYear) stringResource(Res.string.growth_year_month_title, date.year, date.month)
    else stringResource(Res.string.growth_month_title, date.month)
}

/** 달 넘김 줄: 양옆 44dp 꺽쇠와 가운데 달 이름. 넘길 수 없는 쪽은 옅은 색으로 두고 눌러도 아무 일이 없다. */
@Composable
private fun MonthNav(title: String, titleSize: TextUnit, canPrev: Boolean, canNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        NavButton(EolssuIcons.Back, stringResource(Res.string.growth_prev_month), canPrev, onPrev)
        Text(
            title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleMedium.copy(fontSize = titleSize, lineHeight = 24.sp),
            textAlign = TextAlign.Center,
        )
        NavButton(EolssuIcons.Chevron, stringResource(Res.string.growth_next_month), canNext, onNext)
    }
}

@Composable
private fun NavButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = UserActionLog.click(QaTarget.GROWTH_CALENDAR_PARTS_01, onClick))
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = if (enabled) colors.text else colors.line)
    }
}

/** 월~일 머리. 일요일은 붉은 글자. 웹은 문구를 늦게 불러오므로 늘 7칸을 둔다. */
@Composable
private fun WeekdayHeader(divider: Boolean) {
    val colors = EolssuTheme.colors
    Column(Modifier.clearAndSetSemantics {}) {
        Row(Modifier.fillMaxWidth().padding(bottom = if (divider) 8.dp else 0.dp)) {
            val names = weekdayNames()
            (0 until 7).forEach { i ->
                Text(
                    names.getOrElse(i) { "" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = if (divider) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (i == 6) colors.rec else colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (divider) HorizontalDivider(thickness = 1.dp, color = colors.surfaceStrong)
    }
}

/**
 * 연습량 달력 카드(A안 2차 아래 카드): 한 달 달력(‹ ›로 넘김), 그 달 연습 시간 · 연습 횟수 합계, 날짜 원 진하기(그날 연습 시간 4단계)와
 * 아래에 고른 날의 연습 시간 · 횟수(색 설명 범례는 두지 않는다). 오늘은 밑줄과 '오늘' 표, 오늘 뒤 날짜는 누를 수 없다.
 */
@Composable
internal fun HeatCalendarCard(
    state: GrowthUiState,
    spacing: GrowthSpacing,
    onMoveMonth: (Int) -> Unit,
    onPickDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EolssuTheme.colors
    val history = state.history ?: return
    val month = state.heatMonth
    val monthEnd = GrowthDays.monthEnd(month)
    val (seconds, count) = history.sum(month, monthEnd)
    val fills = heatFills()
    GrowthCard(modifier, padding = 0.dp, gap = 10.dp) {
        Column(
            Modifier.padding(start = spacing.cardPadding - 4.dp, end = spacing.cardPadding - 4.dp, top = 10.dp, bottom = spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MonthNav(
                monthTitle(month, state.today.year), 17.sp,
                canPrev = state.canShowMonth(GrowthDays.addMonths(month, -1)),
                canNext = state.canShowMonth(GrowthDays.addMonths(month, 1)),
                onPrev = { onMoveMonth(-1) },
                onNext = { onMoveMonth(1) },
            )
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(bigValue(seconds / 60, GrowthMetric.Time, 24.sp, 13.sp), style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 30.sp))
                Text(
                    buildAnnotatedString {
                        append(stringResource(Res.string.growth_metric_count))
                        append(" ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.text)) { append(stringResource(Res.string.growth_count_value, count)) }
                    },
                    modifier = Modifier.padding(bottom = 4.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = colors.textMuted,
                )
            }
            WeekdayHeader(divider = true)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GrowthDays.monthWeeks(month).forEach { weekStart ->
                    Row(Modifier.fillMaxWidth()) {
                        (0 until 7).forEach { c ->
                            val day = weekStart + c
                            Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                                if (day in month..monthEnd) {
                                    val record = history[day]
                                    HeatDay(
                                        day = day,
                                        level = record?.let { heatLevel(it.minutes) } ?: 0,
                                        fill = fills[record?.let { heatLevel(it.minutes) } ?: 0],
                                        description = record?.let { stringResource(Res.string.growth_day_desc_record, dateOf(day).month, dateOf(day).day, it.minutes, it.practiceCount) }
                                            ?: stringResource(Res.string.growth_day_desc_none, dateOf(day).month, dateOf(day).day),
                                        sunday = c == 6,
                                        today = day == state.todayDay,
                                        future = day > state.todayDay,
                                        selected = day == state.heatDay,
                                        onClick = UserActionLog.click(QaTarget.GROWTH_CALENDAR_PARTS_02, { onPickDay(day) }),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 2.dp), thickness = 1.dp, color = colors.surfaceStrong)
            HeatDayDetail(state)
        }
    }
}

private fun dateOf(day: Long) = KoreanDateTime.ofEpochDay(day)

@Composable
private fun HeatDay(
    day: Long,
    level: Int,
    fill: Color,
    description: String,
    sunday: Boolean,
    today: Boolean,
    future: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = EolssuTheme.colors
    val ink = colors.text
    val textColor = when {
        future -> GrowthColors.DisabledDay
        level == 4 -> colors.text
        level > 0 -> colors.accentText
        sunday -> colors.rec
        else -> colors.text
    }
    Box(
        Modifier
            .size(40.dp)
            .drawBehind {
                // 고른 날: 흰 틈 2dp 바깥 먹색 고리 2dp(시안 box-shadow).
                if (selected) drawCircle(ink, radius = size.minDimension / 2 + 3.dp.toPx(), style = Stroke(2.dp.toPx()))
            }
            .clip(CircleShape)
            .background(fill)
            .clickable(enabled = !future, role = Role.Button, onClick = UserActionLog.click(QaTarget.GROWTH_CALENDAR_PARTS_03, onClick))
            .semantics {
                contentDescription = description
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${dateOf(day).day}",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                fontWeight = when {
                    !future && (selected || today) -> FontWeight.Bold
                    !future && level > 0 -> FontWeight.SemiBold
                    else -> FontWeight.Normal
                },
                textDecoration = if (today && !selected) TextDecoration.Underline else null,
            ),
            color = textColor,
        )
    }
}

/** 연습량 달력에서 고른 날의 연습 시간 · 횟수. 기록이 없으면 안내 한 줄. */
@Composable
private fun HeatDayDetail(state: GrowthUiState) {
    val colors = EolssuTheme.colors
    val day = state.heatDay
    val date = dateOf(day)
    val record = state.history?.get(day)
    Column(Modifier.padding(horizontal = 4.dp).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(Res.string.growth_day_title, date.month, date.day, weekdayNames().getOrElse(date.dayOfWeek) { "" }),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            )
            if (day == state.todayDay) {
                Box(Modifier.height(20.dp).background(colors.primary, RoundedCornerShape(10.dp)).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.growth_today), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = colors.onPrimary)
                }
            }
        }
        if (record == null) {
            Text(
                stringResource(Res.string.growth_day_none),
                Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                color = colors.textMuted,
            )
        } else {
            Row(Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(14.dp)).padding(vertical = 12.dp)) {
                DayStat(stringResource(Res.string.growth_metric_time), record.minutes, stringResource(Res.string.growth_unit_minute), Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(42.dp).background(colors.surfaceStrong))
                DayStat(stringResource(Res.string.growth_metric_count), record.practiceCount, stringResource(Res.string.growth_unit_count), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DayStat(label: String, value: Long, unit: String, modifier: Modifier) {
    val colors = EolssuTheme.colors
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = colors.textMuted)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = EolssuTheme.serif.yul.fontFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold)) { append("$value") }
                withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)) { append(unit) }
            },
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
        )
    }
}

/**
 * 기간 기준 날짜 고르기 시트: 월~일 달력에서 연습한 날은 연두 원, 고른 날은 먹색 원, 주별이면 고른 주 줄에 옅은 연두 띠.
 * 오늘 뒤 날짜와 첫 기록 전 날짜는 고를 수 없고, 첫 기록이 든 달 ~ 이번 달만 넘긴다. 고르면 [onPick] 후 닫힌다.
 */
@Composable
internal fun AnchorPickerSheet(state: GrowthUiState, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val colors = EolssuTheme.colors
    val history = state.history ?: return
    var month by rememberSaveable { mutableStateOf(GrowthDays.monthStart(state.anchorDay)) }
    val week = state.range == GrowthRange.Week
    val anchorWeek = GrowthDays.weekStart(state.anchorDay)
    EolssuBottomSheet(
        onDismiss = onDismiss,
        maxHeightFraction = 0.9f,
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(if (week) Res.string.growth_sheet_hint_week else Res.string.growth_sheet_hint_month),
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                )
                MonthNav(
                    monthTitle(month, state.today.year, alwaysYear = true), 18.sp,
                    canPrev = state.canShowMonth(GrowthDays.addMonths(month, -1)),
                    canNext = state.canShowMonth(GrowthDays.addMonths(month, 1)),
                    onPrev = { month = GrowthDays.addMonths(month, -1) },
                    onNext = { month = GrowthDays.addMonths(month, 1) },
                )
                WeekdayHeader(divider = false)
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val monthEnd = GrowthDays.monthEnd(month)
            GrowthDays.monthWeeks(month).forEach { weekStart ->
                val band = week && weekStart == anchorWeek
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (band) colors.accentSoft else Color.Transparent, RoundedCornerShape(22.dp))
                        .padding(vertical = 2.dp),
                ) {
                    (0 until 7).forEach { c ->
                        val day = weekStart + c
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            PickDay(
                                day = day,
                                inMonth = day in month..monthEnd,
                                practiced = history[day] != null,
                                enabled = state.canPickAnchor(day),
                                selected = day == state.anchorDay,
                                sunday = c == 6,
                                onClick = UserActionLog.click(QaTarget.GROWTH_CALENDAR_PARTS_04, { onPick(day) }),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickDay(day: Long, inMonth: Boolean, practiced: Boolean, enabled: Boolean, selected: Boolean, sunday: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    val date = dateOf(day)
    val (background, ink, weight) = when {
        selected -> Triple(colors.text, colors.surface, FontWeight.SemiBold)
        !enabled -> Triple(Color.Transparent, GrowthColors.DisabledDay, FontWeight.Normal)
        practiced -> Triple(colors.accentTint, colors.accentText, FontWeight.SemiBold)
        else -> Triple(Color.Transparent, if (sunday) colors.rec else colors.text, FontWeight.Normal)
    }
    val description = if (practiced) stringResource(Res.string.growth_day_desc_practiced, date.month, date.day)
    else stringResource(Res.string.growth_day_desc, date.month, date.day)
    Box(
        Modifier
            .alpha(if (inMonth) 1f else 0.35f)
            .size(40.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = UserActionLog.click(QaTarget.GROWTH_CALENDAR_PARTS_05, onClick))
            .semantics {
                contentDescription = description
                this.selected = selected
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("${date.day}", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = weight), color = ink)
    }
}
