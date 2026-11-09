package com.ssafy.eolssu.feature.history.component

import androidx.compose.material3.CircularProgressIndicator
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import eolssu.shared.generated.resources.records_load_failed
import eolssu.shared.generated.resources.growth_retry
import eolssu.shared.generated.resources.records_section
import eolssu.shared.generated.resources.records_row_today
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.core.ui.component.monthDayLabel
import com.ssafy.eolssu.core.ui.component.timeLabel
import com.ssafy.eolssu.feature.history.RecordGroup
import com.ssafy.eolssu.feature.history.RecordListUiState
import com.ssafy.eolssu.feature.history.RecordSort
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.records_best
import eolssu.shared.generated.resources.records_collapsed
import eolssu.shared.generated.resources.records_empty
import eolssu.shared.generated.resources.records_unavailable
import eolssu.shared.generated.resources.records_expanded
import eolssu.shared.generated.resources.records_group_meta
import eolssu.shared.generated.resources.records_growth_link
import eolssu.shared.generated.resources.records_just_saved
import eolssu.shared.generated.resources.records_memo
import eolssu.shared.generated.resources.records_meta_section
import eolssu.shared.generated.resources.records_meta_whole
import eolssu.shared.generated.resources.records_score_unit
import eolssu.shared.generated.resources.records_by_song
import eolssu.shared.generated.resources.records_instrument_all
import eolssu.shared.generated.resources.records_instrument_empty
import eolssu.shared.generated.resources.records_sort_new
import eolssu.shared.generated.resources.records_sort_old
import eolssu.shared.generated.resources.records_this_week
import eolssu.shared.generated.resources.records_this_week_none
import eolssu.shared.generated.resources.records_this_week_summary
import eolssu.shared.generated.resources.records_today
import eolssu.shared.generated.resources.records_whole
import org.jetbrains.compose.resources.stringResource

/** 기록 목록의 여백·크기. 배치마다 와이어프레임 값이 조금씩 다르다. */
internal data class RecordListSpacing(
    val weekPaddingH: Dp,
    val weekPaddingV: Dp,
    val weekSummarySize: TextUnit,
    val toolbarTop: Dp,
    val toolbarBottom: Dp,
    val groupPaddingH: Dp,
    val groupPaddingV: Dp,
    val rowPaddingH: Dp,
    val rowPaddingV: Dp,
    val rowIcon: Dp,
) {
    companion object {
        /** 휴대폰(RECORD-01). */
        val Compact = RecordListSpacing(16.dp, 14.dp, 15.sp, 16.dp, 8.dp, 16.dp, 14.dp, 16.dp, 12.dp, 36.dp)

        /** 태블릿 세로(`Tab-RecordList-Port`). */
        val Portrait = RecordListSpacing(20.dp, 18.dp, 17.sp, 20.dp, 10.dp, 20.dp, 16.dp, 20.dp, 14.dp, 40.dp)

        /** 태블릿 가로 왼쪽 목록(`Tab-Records-Land`). */
        val Pane = Compact.copy(toolbarTop = 14.dp)
    }
}

/** '이번 주' 요약 카드. 누르면 연습 기록지로 간다. */
@Composable
internal fun WeekCard(state: RecordListUiState, spacing: RecordListSpacing, onOpenGrowth: () -> Unit) {
    val week = state.thisWeek
    // 보드: 흰 바탕 · `#E8E4DA` 테두리 · 반지름 16.
    Surface(onClick = onOpenGrowth, shape = RoundedCornerShape(16.dp), color = EolssuTheme.colors.surface, border = BorderStroke(1.dp, EolssuTheme.colors.lineSoft)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = spacing.weekPaddingH, vertical = spacing.weekPaddingV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.records_this_week), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = EolssuTheme.colors.textMuted)
                Text(
                    if (week != null) stringResource(Res.string.records_this_week_summary, week.first, week.second) else stringResource(Res.string.records_this_week_none),
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = spacing.weekSummarySize, fontWeight = FontWeight.Bold),
                )
            }
            Text(stringResource(Res.string.records_growth_link), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.padding(start = 2.dp).size(14.dp))
        }
    }
}

/** 빈 기록과 조회 기능을 아직 사용할 수 없는 상태를 구분한다. */
@Composable
internal fun RecordsEmpty(available: Boolean, modifier: Modifier = Modifier) {
    Text(
        stringResource(if (available) Res.string.records_empty else Res.string.records_unavailable),
        modifier = modifier.fillMaxWidth().padding(vertical = 56.dp),
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
        textAlign = TextAlign.Center,
    )
}

/** 기록은 있지만 고른 악기의 기록이 없을 때. 위의 악기 선택은 그대로 둔다. */
@Composable
internal fun RecordsInstrumentEmpty(modifier: Modifier = Modifier) {
    Text(
        stringResource(Res.string.records_instrument_empty),
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp),
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
        color = EolssuTheme.colors.textMuted,
        textAlign = TextAlign.Center,
    )
}

/** ‘곡별 기록’ 제목 · 악기 선택 · 정렬 선택 줄. */
@Composable
internal fun RecordListToolbar(
    instrument: Instrument?,
    sort: RecordSort,
    spacing: RecordListSpacing,
    onInstrument: (Instrument?) -> Unit,
    onSort: (RecordSort) -> Unit,
) {
    Row(
        Modifier.padding(top = spacing.toolbarTop, bottom = spacing.toolbarBottom),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Res.string.records_by_song),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = EolssuTheme.colors.textMuted,
        )
        val all = stringResource(Res.string.records_instrument_all)
        SelectBox(instrument, listOf(null) + Instrument.entries, { it?.let { i -> stringResource(i.label) } ?: all }, onInstrument)
        SelectBox(sort, RecordSort.entries, { stringResource(if (it == RecordSort.Newest) Res.string.records_sort_new else Res.string.records_sort_old) }, onSort)
    }
}

/** 흰 바탕 · 연한 테두리의 드롭다운 선택 상자. */
@Composable
private fun <T> SelectBox(value: T, options: List<T>, label: @Composable (T) -> String, onPick: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .width(108.dp)
                .height(38.dp)
                .background(EolssuTheme.colors.surface, RoundedCornerShape(10.dp))
                .border(1.dp, EolssuTheme.colors.lineSoft, RoundedCornerShape(10.dp))
                .clickable(role = Role.DropdownList) { expanded = true }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label(value), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), maxLines = 1)
            Icon(EolssuIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = EolssuTheme.colors.surface) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(label(option)) }, onClick = {
                    expanded = false
                    onPick(option)
                })
            }
        }
    }
}

/**
 * 곡별 녹음 묶음들(사이 8dp).
 *
 * @param selectedId 두 칸 배치에서 오른쪽에 보이는 기록. null이면 고르기 표시 없이 누르면 [onOpenRecord]로 상세 화면에 간다.
 */
@Composable
internal fun RecordGroups(
    state: RecordListUiState,
    spacing: RecordListSpacing,
    onToggle: (String) -> Unit,
    onOpenRecord: (String) -> Unit,
    selectedId: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.groups.forEach { group ->
            SongGroupCard(group, state.isOpen(group.songId), state, spacing, selectedId, onToggle, onOpenRecord)
        }
    }
}

@Composable
private fun SongGroupCard(
    group: RecordGroup,
    open: Boolean,
    state: RecordListUiState,
    spacing: RecordListSpacing,
    selectedId: String?,
    onToggle: (String) -> Unit,
    onOpenRecord: (String) -> Unit,
) {
    val colors = EolssuTheme.colors
    val expandedLabel = stringResource(Res.string.records_expanded)
    val collapsedLabel = stringResource(Res.string.records_collapsed)
    // 보드: 흰 묶음 카드(테두리 `#E8E4DA`, 반지름 16), 펼친 머리는 `#EFF2D6`.
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surface, border = BorderStroke(1.dp, colors.lineSoft)) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (open) colors.accentTint else colors.surface)
                    .clickable(role = Role.Button) { onToggle(group.songId) }
                    .semantics { stateDescription = if (open) expandedLabel else collapsedLabel }
                    .padding(horizontal = spacing.groupPaddingH, vertical = spacing.groupPaddingV),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(group.songTitle, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                    Text(
                        stringResource(Res.string.records_group_meta, group.records.size, dayLabel(group.latest, state.today)),
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.textMuted,
                    )
                }
                Text(
                    buildAnnotatedString {
                        append(stringResource(Res.string.records_best) + " ")
                        withStyle(SpanStyle(fontSize = 15.sp, color = colors.text, fontWeight = FontWeight.Bold)) { append("${group.best}") }
                        append(stringResource(Res.string.records_score_unit))
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textMuted,
                )
                Icon(EolssuIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(18.dp).rotate(if (open) 180f else 0f))
            }
            if (open) {
                HorizontalDivider(color = colors.lineSoft)
                group.records.forEachIndexed { k, record ->
                    if (k > 0) HorizontalDivider(color = colors.background)
                    RecordRow(
                        record = record,
                        justSaved = record.id == state.justSavedId,
                        today = state.today,
                        spacing = spacing,
                        selected = selectedId?.let { it == record.id },
                    ) { onOpenRecord(record.id) }
                }
            }
        }
    }
}

/** 녹음 줄의 날짜: 오늘이면 ‘오늘 · 10월 20일’(보드), 아니면 ‘10월 18일’. 묶음 머리는 [dayLabel]의 ‘오늘 10월 20일’을 쓴다. */
@Composable
private fun rowDayLabel(dateTime: KoreanDateTime, today: KoreanDateTime): String =
    if (dateTime.isSameDay(today)) stringResource(Res.string.records_row_today, monthDayLabel(dateTime)) else monthDayLabel(dateTime)

@Composable
private fun dayLabel(dateTime: KoreanDateTime, today: KoreanDateTime): String =
    if (dateTime.isSameDay(today)) stringResource(Res.string.records_today) + " " + monthDayLabel(dateTime) else monthDayLabel(dateTime)

/**
 * 녹음 한 줄. [selected]가 null이 아니면 두 칸 배치의 고르기 줄이라, 고른 줄은 회색 바탕 · 왼쪽 검은 띠 ·
 * 검은 재생 아이콘으로 표시한다(`Tab-Records-Land`).
 */
@Composable
private fun RecordRow(
    record: PracticeRecord,
    justSaved: Boolean,
    today: KoreanDateTime,
    spacing: RecordListSpacing,
    selected: Boolean?,
    onClick: () -> Unit,
) {
    val colors = EolssuTheme.colors
    val on = selected == true
    val selection = when (selected) {
        null -> Modifier
        else -> Modifier.semantics { this.selected = on }
    }
    val marker = if (on) {
        Modifier.background(colors.surface).drawBehind { drawRect(colors.text, size = Size(3.dp.toPx(), size.height)) }
    } else {
        Modifier
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(marker)
            .clickable(onClick = onClick)
            .then(selection)
            .padding(horizontal = spacing.rowPaddingH, vertical = spacing.rowPaddingV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 보드: 36dp 흰 원(테두리 `#E8E4DA`) 안 14dp 재생 표시. 두 칸 배치에서 고른 줄은 검은 원.
        Box(
            Modifier
                .size(spacing.rowIcon)
                .background(if (on) colors.text else colors.surface, CircleShape)
                .then(if (on) Modifier else Modifier.border(1.dp, colors.lineSoft, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(EolssuIcons.Play, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (on) colors.background else colors.text)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(record.section?.let { stringResource(Res.string.records_section, it) } ?: stringResource(Res.string.records_whole), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
                if (justSaved) {
                    Text(
                        stringResource(Res.string.records_just_saved),
                        modifier = Modifier.background(colors.primary, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.onPrimary,
                    )
                }
            }
            val small = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
            Text("${rowDayLabel(record.recordedAt, today)} · ${timeLabel(record.recordedAt)}", modifier = Modifier.padding(top = 2.dp), style = small, color = colors.textMuted)
            Text(
                if (record.section != null && record.repeats != null) {
                    stringResource(Res.string.records_meta_section, formatClock(record.recordedSeconds.toDouble()), record.repeats, record.bpm)
                } else {
                    stringResource(Res.string.records_meta_whole, formatClock(record.recordedSeconds.toDouble()), record.bpm)
                },
                modifier = Modifier.padding(top = 1.dp),
                style = small,
                color = colors.textMuted,
            )
            if (record.memo.isNotEmpty()) {
                Text(
                    stringResource(Res.string.records_memo, record.memo),
                    modifier = Modifier.padding(top = 4.dp),
                    style = small,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold)) { append("${record.score}") }
                withStyle(SpanStyle(fontSize = 11.sp, color = colors.textMuted)) { append(stringResource(Res.string.records_score_unit)) }
            },
        )
    }
}

/** 목록을 받는 중이면 도는 표시, 받지 못했으면 안내와 ‘다시 시도’. */
@Composable
internal fun RecordsLoadStatus(failed: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Column(
        modifier.fillMaxWidth().padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (failed) {
            Text(stringResource(Res.string.records_load_failed), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), textAlign = TextAlign.Center)
            EolssuButton(stringResource(Res.string.growth_retry), onRetry, style = EolssuButtonStyle.Secondary, small = true)
        } else {
            CircularProgressIndicator(Modifier.size(36.dp), color = colors.text, trackColor = colors.surfaceStrong, strokeWidth = 3.dp)
        }
    }
}
