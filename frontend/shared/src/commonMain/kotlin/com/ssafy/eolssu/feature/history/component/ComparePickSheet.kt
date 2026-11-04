package com.ssafy.eolssu.feature.history.component

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuBottomSheet
import com.ssafy.eolssu.core.ui.component.EolssuIconButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.monthDayLabel
import com.ssafy.eolssu.core.ui.component.timeLabel
import com.ssafy.eolssu.feature.history.CompareDay
import com.ssafy.eolssu.feature.history.CompareUiState
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_close
import eolssu.shared.generated.resources.weak_compare_comparing
import eolssu.shared.generated.resources.weak_compare_day_empty
import eolssu.shared.generated.resources.weak_compare_day_records
import eolssu.shared.generated.resources.weak_compare_day_title
import eolssu.shared.generated.resources.weak_compare_day_today
import eolssu.shared.generated.resources.weak_compare_pick_hint
import eolssu.shared.generated.resources.weak_compare_pick_title
import eolssu.shared.generated.resources.weak_compare_record_meta
import eolssu.shared.generated.resources.weak_compare_weekdays
import org.jetbrains.compose.resources.stringResource

/**
 * COMPARE-01 비교할 기록 고르기 시트(2차 `Compare-Pick`): 최근 2주 달력(녹음한 날에 점, 오늘은 연두 점) → 그날의 기록 목록.
 * 기록을 누르면 그 기록으로 비교하고 시트를 닫는다.
 */
@Composable
internal fun ComparePickSheet(
    state: CompareUiState,
    onDismiss: () -> Unit,
    onPickDay: (Long) -> Unit,
    onPickRecord: (String) -> Unit,
) {
    val colors = EolssuTheme.colors
    val calendar = state.calendar
    val shownDay = state.shownDay
    EolssuBottomSheet(
        onDismiss = onDismiss,
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
        Text(stringResource(Res.string.weak_compare_pick_hint), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
        if (calendar.isNotEmpty()) {
            // 첫 날의 요일부터 머리를 적는다(월요일 0 … 일요일 6).
            // Web은 문자열 리소스를 비동기로 읽어 첫 그림에서 빈 문자열이 올 수 있으므로 인덱스로 바로 꺼내지 않는다.
            val weekdays = stringResource(Res.string.weak_compare_weekdays).map(Char::toString)
            val first = calendar.first().date.dayOfWeek
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (k in 0 until 7) {
                        Text(
                            weekdays.getOrNull((first + k) % 7).orEmpty(),
                            modifier = Modifier.weight(1f).padding(bottom = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = colors.textMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                calendar.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { day ->
                            DayButton(day, on = day.date.epochDay == shownDay, onClick = { onPickDay(day.date.epochDay) }, modifier = Modifier.weight(1f))
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        val day = calendar.firstOrNull { it.date.epochDay == shownDay }
        if (day != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(Res.string.weak_compare_day_title, monthDayLabel(day.date)),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                )
                day.records.forEach { record ->
                    val on = record.id == state.previous?.id
                    Surface(
                        onClick = { onPickRecord(record.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { selected = on },
                        shape = RoundedCornerShape(14.dp),
                        color = if (on) colors.accentTint else colors.surface,
                        border = BorderStroke(1.dp, if (on) colors.accent else colors.lineSoft),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).background(colors.accentTint, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Text("${record.score}", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(timeLabel(record.recordedAt), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                                Text(
                                    stringResource(Res.string.weak_compare_record_meta, record.bpm, record.errors.size),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = colors.textMuted,
                                )
                            }
                            if (on) Text(stringResource(Res.string.weak_compare_comparing), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
    }
}

/** 달력의 하루(44dp): 고른 날 연두 · 녹음한 날 흰 바탕 테두리 + 먹색 점 · 오늘 먹색 테두리 + 연두 점 · 나머지 흐린 글자(누를 수 없음). */
@Composable
private fun DayButton(day: CompareDay, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val has = day.records.isNotEmpty()
    val dateLabel = monthDayLabel(day.date)
    val label = when {
        day.today -> stringResource(Res.string.weak_compare_day_today, dateLabel)
        has -> stringResource(Res.string.weak_compare_day_records, dateLabel, day.records.size)
        else -> stringResource(Res.string.weak_compare_day_empty, dateLabel)
    }
    val (background, border, text) = when {
        on -> Triple(colors.accent, colors.accent, colors.text)
        has -> Triple(colors.surface, colors.lineSoft, colors.text)
        day.today -> Triple(colors.background, colors.text, colors.text)
        else -> Triple(Color.Transparent, Color.Transparent, colors.textDisabled)
    }
    Surface(
        onClick = onClick,
        enabled = has,
        modifier = modifier.height(44.dp).semantics {
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
                day.today -> colors.accent
                else -> Color.Transparent
            }
            Box(Modifier.size(5.dp).background(dot, CircleShape))
        }
    }
}
