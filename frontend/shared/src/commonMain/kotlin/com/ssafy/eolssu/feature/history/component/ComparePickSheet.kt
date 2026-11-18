package com.ssafy.eolssu.feature.history.component

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuBottomSheet
import com.ssafy.eolssu.core.ui.component.EolssuIconButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.monthDayLabel
import com.ssafy.eolssu.core.ui.component.timeLabel
import com.ssafy.eolssu.feature.history.CompareDay
import com.ssafy.eolssu.feature.history.CompareMonth
import com.ssafy.eolssu.feature.history.RecordCompareLoad
import com.ssafy.eolssu.feature.history.ComparePick
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_close
import eolssu.shared.generated.resources.result_score_unit
import eolssu.shared.generated.resources.weak_compare_day_after
import eolssu.shared.generated.resources.weak_compare_day_empty
import eolssu.shared.generated.resources.weak_compare_day_records
import eolssu.shared.generated.resources.weak_compare_day_title
import eolssu.shared.generated.resources.weak_compare_day_today
import eolssu.shared.generated.resources.weak_compare_month
import eolssu.shared.generated.resources.weak_compare_next_month
import eolssu.shared.generated.resources.weak_compare_none
import eolssu.shared.generated.resources.weak_compare_pick_title
import eolssu.shared.generated.resources.weak_compare_prev_month
import eolssu.shared.generated.resources.weak_compare_record_label
import eolssu.shared.generated.resources.weak_compare_weekdays
import org.jetbrains.compose.resources.stringResource

/**
 * 비교할 기록 고르기 시트(S15P31E102-753, 시안 ② 시트): 한 달 달력(일요일 시작, ‘‹ 2026년 11월 ›’로 달 넘기기) → 그날의 기록 목록.
 * 첫 기록이 있는 달보다 앞, 이번 기록이 있는 달보다 뒤로는 넘기지 않는다. 녹음한 날에 점, 이번 기록 날은 따로 표시하고
 * 이번 기록 이후 날은 더 흐리게 둔다. 기록 칸(왼쪽 시간 · 오른쪽 점수 · ›)을 누르면 바로 [onPick]한다.
 * 처음에는 비교 중인 기록, 없으면 바로 전 기록의 날을 보여 준다.
 */
@Composable
internal fun ComparePickSheet(
    pick: ComparePick,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    load: RecordCompareLoad = RecordCompareLoad.Ready,
    onRetry: () -> Unit = {},
) {
    val colors = EolssuTheme.colors
    var shownDay by remember(pick.current.id, pick.initialDay) { mutableLongStateOf(pick.initialDay) }
    var month by remember(pick.current.id, pick.initialDay) { mutableStateOf(CompareMonth.of(KoreanDateTime.ofEpochDay(pick.initialDay))) }
    // 연습 설정 시트처럼 회색 시트 위에 달력을 흰 카드로 담는다(S15P31E102-768).
    EolssuBottomSheet(
        onDismiss = onDismiss,
        containerColor = colors.groupedBackground,
        header = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.weak_compare_pick_title),
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp),
                )
                EolssuIconButton(EolssuIcons.Close, stringResource(Res.string.common_close), onDismiss, bordered = false)
            }
        },
    ) {
        if (load != RecordCompareLoad.Ready) {
            RecordsLoadStatus(failed = load == RecordCompareLoad.Failed, onRetry = onRetry)
            return@EolssuBottomSheet
        }
        if (pick.candidates.isEmpty()) {
            Text(stringResource(Res.string.weak_compare_none), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = colors.textSub)
            return@EolssuBottomSheet
        }
        Column(
            Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MonthNavigation(month, canPrev = month > pick.firstMonth, canNext = month < pick.lastMonth, onMove = { month = month.plus(it) })
            // Web은 문자열 리소스를 비동기로 읽어 첫 그림에서 빈 문자열이 올 수 있으므로 인덱스로 바로 꺼내지 않는다.
            val weekdays = stringResource(Res.string.weak_compare_weekdays).map(Char::toString)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (k in 0 until 7) {
                    Text(
                        weekdays.getOrNull(k).orEmpty(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                        // 연습 기록지 달력처럼 일요일 머리만 붉은 글자로 둔다.
                        color = if (k == 6) colors.rec else colors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            pick.month(month).chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { day ->
                        if (day == null) {
                            Spacer(Modifier.weight(1f).height(40.dp))
                        } else {
                            DayButton(day, on = day.date.epochDay == shownDay, onClick = UserActionLog.click(QaTarget.COMPARE_PICK_SHEET_01, { shownDay = day.date.epochDay }), modifier = Modifier.weight(1f))
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        val records = pick.recordsOn(shownDay)
        if (records.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(Res.string.weak_compare_day_title, monthDayLabel(KoreanDateTime.ofEpochDay(shownDay))),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                )
                records.forEach { record -> RecordRow(record, on = record.id == pick.selectedId, onClick = UserActionLog.click(QaTarget.COMPARE_PICK_SHEET_02, { onPick(record.id) })) }
            }
        }
    }
}

/** ‘‹ 2026년 11월 ›’. 넘길 수 없는 쪽 화살표는 흐리게 두고 누를 수 없다. */
@Composable
private fun MonthNavigation(month: CompareMonth, canPrev: Boolean, canNext: Boolean, onMove: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        MonthArrow(EolssuIcons.Back, stringResource(Res.string.weak_compare_prev_month), canPrev) { onMove(-1) }
        Text(
            stringResource(Res.string.weak_compare_month, month.year, month.month),
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
        )
        MonthArrow(EolssuIcons.Chevron, stringResource(Res.string.weak_compare_next_month), canNext) { onMove(1) }
    }
}

@Composable
private fun MonthArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = UserActionLog.click(QaTarget.COMPARE_PICK_SHEET_03, onClick),
        enabled = enabled,
        modifier = Modifier.size(44.dp).semantics { contentDescription = label },
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = if (enabled) colors.text else colors.missedErrorLine,
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
    }
}

/**
 * 달력의 하루(40dp · 반지름 10): 보고 있는 날 연두 · 녹음한 날 회색 채움 + 먹색 점 · 이번 기록 날 연한 연두 채움 + 연두 점 ·
 * 이번 기록 이후 날은 더 흐린 글자. 녹음한 날만 누를 수 있다.
 */
@Composable
private fun DayButton(day: CompareDay, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val has = day.records.isNotEmpty()
    val dateLabel = monthDayLabel(day.date)
    val label = when {
        has -> stringResource(Res.string.weak_compare_day_records, dateLabel, day.records.size)
        day.current -> stringResource(Res.string.weak_compare_day_today, dateLabel)
        day.after -> stringResource(Res.string.weak_compare_day_after, dateLabel)
        else -> stringResource(Res.string.weak_compare_day_empty, dateLabel)
    }
    // 테두리 없이 바탕색으로 나눈다(S15P31E102-768): 보고 있는 날 연두, 녹음한 날 회색 채움, 이번 기록 날은 연한 연두.
    val (background, border) = when {
        on -> colors.accent to Color.Transparent
        has -> colors.groupedBackground to Color.Transparent
        day.current -> colors.accentTint to Color.Transparent
        else -> Color.Transparent to Color.Transparent
    }
    val text = when {
        has || day.current -> colors.text
        day.after -> colors.missedErrorLine
        else -> colors.textDisabled
    }
    Surface(
        onClick = UserActionLog.click(QaTarget.COMPARE_PICK_SHEET_04, onClick),
        enabled = has,
        modifier = modifier.height(40.dp).semantics {
            contentDescription = label
            selected = on
        },
        shape = RoundedCornerShape(10.dp),
        color = background,
        contentColor = text,
        border = BorderStroke(1.dp, border),
    ) {
        Column(Modifier.clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
            Text("${day.date.day}", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold), color = text)
            val dot = when {
                has -> colors.text
                day.current -> colors.accent
                else -> Color.Transparent
            }
            Box(Modifier.size(5.dp).background(dot, CircleShape))
        }
    }
}

/** 기록 칸(52dp · 반지름 14): 왼쪽 시간, 오른쪽 점수(명조), 끝에 ›. 회색 시트 위 테두리 없는 흰 칸이고 지금 비교 중인 기록은 연한 연두 채움. */
@Composable
private fun RecordRow(record: PracticeRecord, on: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    val time = timeLabel(record.recordedAt)
    val label = stringResource(Res.string.weak_compare_record_label, time, record.score)
    Surface(
        onClick = UserActionLog.click(QaTarget.COMPARE_PICK_SHEET_05, onClick),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics {
            contentDescription = label
            selected = on
        },
        shape = RoundedCornerShape(14.dp),
        color = if (on) colors.accentTint else colors.surface,
    ) {
        Row(
            Modifier.clearAndSetSemantics {}.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                time,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
                color = colors.text,
            )
            Text(
                buildAnnotatedString {
                    append("${record.score}")
                    withStyle(SpanStyle(fontFamily = MaterialTheme.typography.bodyLarge.fontFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textMuted)) {
                        append(stringResource(Res.string.result_score_unit))
                    }
                },
                style = EolssuTheme.serif.yul.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
                color = colors.text,
            )
            Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textDisabled)
        }
    }
}
