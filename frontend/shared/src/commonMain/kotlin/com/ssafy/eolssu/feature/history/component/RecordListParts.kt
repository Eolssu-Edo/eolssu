package com.ssafy.eolssu.feature.history.component

import com.ssafy.eolssu.core.ui.component.EolssuMenuItem
import com.ssafy.eolssu.core.ui.component.EolssuMenuPanel
import com.ssafy.eolssu.core.ui.component.EolssuMenuPopup
import com.ssafy.eolssu.core.diagnostics.PerfPhase
import com.ssafy.eolssu.core.diagnostics.observeFirstDraw
import androidx.compose.material3.CircularProgressIndicator
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import eolssu.shared.generated.resources.records_load_failed
import eolssu.shared.generated.resources.growth_retry
import eolssu.shared.generated.resources.records_section
import eolssu.shared.generated.resources.records_row_today
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.role
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.monthDayLabel
import com.ssafy.eolssu.core.ui.component.timeLabel
import com.ssafy.eolssu.core.ui.component.InstrumentPicture
import com.ssafy.eolssu.core.ui.component.SongSearchField
import com.ssafy.eolssu.core.ui.component.SongSortButton
import com.ssafy.eolssu.core.model.SongSortOrder
import com.ssafy.eolssu.feature.history.RecordDay
import com.ssafy.eolssu.feature.history.RecordSongGroup
import eolssu.shared.generated.resources.records_load_more
import com.ssafy.eolssu.feature.history.RecordListUiState
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.records_best
import eolssu.shared.generated.resources.records_collapsed
import eolssu.shared.generated.resources.records_day_meta
import eolssu.shared.generated.resources.records_empty
import eolssu.shared.generated.resources.records_empty_hint
import eolssu.shared.generated.resources.records_empty_practice
import eolssu.shared.generated.resources.records_unavailable
import eolssu.shared.generated.resources.records_expanded
import eolssu.shared.generated.resources.records_growth_link
import eolssu.shared.generated.resources.records_just_saved
import eolssu.shared.generated.resources.records_memo
import eolssu.shared.generated.resources.records_score_unit
import eolssu.shared.generated.resources.records_instrument_all
import eolssu.shared.generated.resources.records_instrument_empty
import eolssu.shared.generated.resources.records_search_empty
import eolssu.shared.generated.resources.records_whole
import org.jetbrains.compose.resources.stringResource

/** 기록 목록의 여백·크기. 배치마다 와이어프레임 값이 조금씩 다르다. */
internal data class RecordListSpacing(
    val toolbarTop: Dp,
    val toolbarBottom: Dp,
    val groupPaddingH: Dp,
    val groupPaddingV: Dp,
    val rowPaddingH: Dp,
    val rowPaddingV: Dp,
) {
    companion object {
        /** 휴대폰(RECORD-01). */
        val Compact = RecordListSpacing(4.dp, 12.dp, 16.dp, 14.dp, 16.dp, 12.dp)

        /** 태블릿 세로(`Tab-RecordList-Port`). */
        val Portrait = RecordListSpacing(4.dp, 14.dp, 20.dp, 16.dp, 20.dp, 14.dp)

        /** 태블릿 가로 왼쪽 목록(`Tab-Records-Land`). */
        val Pane = Compact
    }
}

/**
 * 상단 바 오른쪽의 작은 ‘연습 기록지 >’ 버튼.
 * 이번 주 횟수·최고 점수는 저장한 연주만 세어 연습 기록지(끝낸 연주 모두)와 달라 이 화면에서 보이지 않는다.
 */
@Composable
internal fun GrowthLinkButton(onOpenGrowth: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(onClick = onOpenGrowth, modifier = Modifier.heightIn(min = 32.dp), shape = CircleShape, color = colors.surface, contentColor = colors.textSub) {
        Row(
            Modifier.padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(Res.string.records_growth_link),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * 빈 기록과 조회 기능을 아직 사용할 수 없는 상태를 구분한다.
 * 저장한 연주가 없으면 기록이 어떻게 쌓이는지 알리고 [onStartPractice]('연습하러 가기')로 곡 고르기에 보낸다(S15P31E102-1031).
 */
@Composable
internal fun RecordsEmpty(available: Boolean, onStartPractice: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.observeFirstDraw(PerfPhase.RECORD_EMPTY).fillMaxWidth().padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(if (available) Res.string.records_empty else Res.string.records_unavailable),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            textAlign = TextAlign.Center,
        )
        if (!available) return@Column
        Text(
            stringResource(Res.string.records_empty_hint),
            Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = EolssuTheme.colors.textMuted,
            textAlign = TextAlign.Center,
        )
        EolssuButton(
            stringResource(Res.string.records_empty_practice),
            onStartPractice,
            Modifier.padding(top = 20.dp).widthIn(min = 200.dp),
            compact = true,
        )
    }
}

/** 기록은 있지만 고른 악기나 검색어([searching])에 맞는 곡이 없을 때. 위의 악기 선택·검색칸은 그대로 둔다. */
@Composable
internal fun RecordsInstrumentEmpty(searching: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        stringResource(if (searching) Res.string.records_search_empty else Res.string.records_instrument_empty),
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp),
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
        color = EolssuTheme.colors.textMuted,
        textAlign = TextAlign.Center,
    )
}

/**
 * 곡 묶음 위의 도구 두 줄: 곡 제목 검색칸, 그 아래 악기 선택 · 이름 토글(곡 묶음 순서).
 * 묶음 안 날짜·연주는 늘 최신순이다.
 */
@Composable
internal fun RecordListToolbar(
    instrument: Instrument?,
    nameOrder: SongSortOrder,
    query: String,
    spacing: RecordListSpacing,
    onInstrument: (Instrument?) -> Unit,
    onToggleName: () -> Unit,
    onSearch: (String) -> Unit,
) {
    Column(Modifier.padding(top = spacing.toolbarTop, bottom = spacing.toolbarBottom), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SongSearchField(query, onSearch, onClear = { onSearch("") }, bordered = false)
        Row(verticalAlignment = Alignment.CenterVertically) {
            InstrumentSelectBox(instrument, onInstrument)
            Spacer(Modifier.weight(1f))
            SongSortButton(nameOrder, onToggleName, bordered = false)
        }
    }
}

/**
 * 악기 선택 상자: 고른 악기의 그림과 이름(‘악기 전체’는 그림 없이)을 보이고, 목록에도 악기마다 그림을 둔다.
 * 보이는 알약은 36dp이고 누르는 영역은 48dp이며, 눌림 효과는 알약 모양 안에만 보인다(S15P31E102-970).
 */
@Composable
private fun InstrumentSelectBox(value: Instrument?, onPick: (Instrument?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val all = stringResource(Res.string.records_instrument_all)
    val selectedLabel = value?.let { stringResource(it.label) } ?: all
    Box {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.height(36.dp).semantics { role = Role.DropdownList },
            shape = CircleShape,
            color = EolssuTheme.colors.surface,
            contentColor = EolssuTheme.colors.text,
        ) {
            Row(
                Modifier.padding(start = if (value == null) 14.dp else 6.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                value?.let { InstrumentPicture(it, size = 24.dp) }
                Text(
                    selectedLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Icon(
                    EolssuIcons.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp).rotate(if (expanded) 180f else 0f),
                )
            }
        }
        if (expanded) {
            EolssuMenuPopup(onDismissRequest = { expanded = false }) {
                EolssuMenuPanel(width = 180.dp) {
                    (listOf(null) + Instrument.entries).forEach { option ->
                        EolssuMenuItem(
                            option?.let { stringResource(it.label) } ?: all,
                            option == value,
                            onClick = {
                                expanded = false
                                onPick(option)
                            },
                            leading = option?.let { { InstrumentPicture(it) } },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 곡 묶음들(사이 8dp). 곡 하나는 악기와 관계없이 묶음 하나이고, 펼치면 연습한 날짜별 드롭다운이 나온다.
 *
 * @param onToggle 곡 묶음([RecordSongGroup.key])을 펼치거나 접는다.
 * @param onLoadMore 곡 묶음의 다음 기록을 받거나 실패한 받기를 다시 한다.
 * @param onToggleDay 곡 묶음 안의 날짜([RecordDay.epochDay])를 펼치거나 접는다.
 * @param selectedId 두 칸 배치에서 오른쪽에 보이는 기록. null이면 고르기 표시 없이 누르면 [onOpenRecord]로 상세 화면에 간다.
 */
@Composable
internal fun RecordGroups(
    state: RecordListUiState,
    spacing: RecordListSpacing,
    onToggle: (String) -> Unit,
    onOpenRecord: (String) -> Unit,
    onLoadMore: (String) -> Unit,
    onToggleDay: (String, Long) -> Unit,
    selectedId: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.groups.forEach { group ->
            SongGroupCard(group, state.isOpen(group.key), state, spacing, selectedId, onToggle, onOpenRecord, onLoadMore, onToggleDay)
        }
    }
}

@Composable
private fun SongGroupCard(
    group: RecordSongGroup,
    open: Boolean,
    state: RecordListUiState,
    spacing: RecordListSpacing,
    selectedId: String?,
    onToggle: (String) -> Unit,
    onOpenRecord: (String) -> Unit,
    onLoadMore: (String) -> Unit,
    onToggleDay: (String, Long) -> Unit,
) {
    val colors = EolssuTheme.colors
    val expandedLabel = stringResource(Res.string.records_expanded)
    val collapsedLabel = stringResource(Res.string.records_collapsed)
    // 보드: 흰 묶음 카드(테두리 `#E8E4DA`, 반지름 16), 펼친 머리는 `#EFF2D6`.
    Surface(
        modifier = Modifier.observeFirstDraw(PerfPhase.RECORD_DRAWN),
        shape = RoundedCornerShape(16.dp), color = colors.surface,
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (open) colors.accentTint else colors.surface)
                    .clickable(role = Role.Button) { onToggle(group.key) }
                    .semantics { stateDescription = if (open) expandedLabel else collapsedLabel }
                    .padding(horizontal = spacing.groupPaddingH, vertical = spacing.groupPaddingV),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    group.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                )
                Text(
                    buildAnnotatedString {
                        append(stringResource(Res.string.records_best) + " ")
                        withStyle(SpanStyle(fontFamily = EolssuTheme.serif.yul.fontFamily, fontSize = 16.sp, color = colors.text, fontWeight = FontWeight.Bold)) { append("${group.bestScore}") }
                        append(stringResource(Res.string.records_score_unit))
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textMuted,
                )
                Icon(EolssuIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(18.dp).rotate(if (open) 180f else 0f))
            }
            if (open) {
                state.days(group).forEach { day ->
                    val dayOpen = state.isDayOpen(group, day.epochDay)
                    HorizontalDivider(color = colors.lineSoft)
                    DayHeader(day, dayOpen, state.today, spacing) { onToggleDay(group.key, day.epochDay) }
                    if (dayOpen) {
                        day.records.forEach { record ->
                            HorizontalDivider(color = colors.background)
                            RecordRow(
                                record = record,
                                instrument = state.songInstruments[record.songId],
                                justSaved = record.id == state.justSavedId,
                                spacing = spacing,
                                selected = selectedId?.let { it == record.id },
                            ) { onOpenRecord(record.id) }
                        }
                    }
                }
                val pages = group.songIds.map { state.pages[it] }
                when {
                    pages.any { it == null || it.loading } -> RecordsLoadStatus(failed = false, onRetry = {})
                    pages.any { it?.failed == true } -> RecordsLoadStatus(failed = true, onRetry = { onLoadMore(group.key) })
                    pages.any { it?.nextPage != null } -> EolssuButton(
                        stringResource(Res.string.records_load_more), { onLoadMore(group.key) },
                        modifier = Modifier.padding(8.dp), style = EolssuButtonStyle.Secondary, small = true,
                    )
                }
            }
        }
    }
}

/** 곡 묶음 안의 날짜 드롭다운 머리: ‘오늘 · 11월 12일’ · 연주 수와 그날 최고 점수 · 펼침 표시. */
@Composable
private fun DayHeader(day: RecordDay, open: Boolean, today: KoreanDateTime, spacing: RecordListSpacing, onToggle: () -> Unit) {
    val colors = EolssuTheme.colors
    val expandedLabel = stringResource(Res.string.records_expanded)
    val collapsedLabel = stringResource(Res.string.records_collapsed)
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.groupedBackground)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (open) expandedLabel else collapsedLabel }
            .padding(horizontal = spacing.rowPaddingH, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            rowDayLabel(day.date, today),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
        Text(
            stringResource(Res.string.records_day_meta, day.records.size, day.records.maxOf { it.score }),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colors.textMuted,
        )
        Icon(EolssuIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(16.dp).rotate(if (open) 180f else 0f))
    }
}

/** 날짜 머리의 날짜: 오늘이면 ‘오늘 · 10월 20일’(보드), 아니면 ‘10월 18일’. */
@Composable
private fun rowDayLabel(dateTime: KoreanDateTime, today: KoreanDateTime): String =
    if (dateTime.isSameDay(today)) stringResource(Res.string.records_row_today, monthDayLabel(dateTime)) else monthDayLabel(dateTime)

/** 기록 줄의 악기: 기초부터 배우기와 같은 악기 그림과 악기 이름. 같은 곡을 여러 악기로 연습해도 줄마다 구분된다. */
@Composable
private fun InstrumentTag(instrument: Instrument) {
    Row(
        Modifier.background(EolssuTheme.colors.groupedBackground, CircleShape).padding(start = 2.dp, end = 8.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        InstrumentPicture(instrument, size = 18.dp)
        Text(
            stringResource(instrument.label),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = EolssuTheme.colors.textSub,
        )
    }
}

/**
 * 녹음 한 줄: 구간 · 시각 · 메모, 오른쪽에 악기와 점수, 끝에 상세로 가는 ›. 날짜는 위 날짜 머리에 있어 시각만 쓴다.
 * 줄 자체는 재생하지 않으므로 재생 표시를 두지 않는다(S15P31E102-874). [selected]가 null이 아니면 두 칸 배치의 고르기 줄이라,
 * 고른 줄은 회색 바탕 · 왼쪽 검은 띠 · 검은 ›로 표시한다(`Tab-Records-Land`).
 */
@Composable
private fun RecordRow(
    record: PracticeRecord,
    instrument: Instrument?,
    justSaved: Boolean,
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
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(record.section?.let { stringResource(Res.string.records_section, it) } ?: stringResource(Res.string.records_whole), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal))
                if (justSaved) {
                    Text(
                        stringResource(Res.string.records_just_saved),
                        modifier = Modifier.background(colors.primary, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.onPrimary,
                    )
                }
            }
            val small = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
            Text(timeLabel(record.recordedAt), modifier = Modifier.padding(top = 2.dp), style = small, color = colors.textMuted)
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
        // 악기 표시는 점수 바로 왼쪽에 둔다.
        instrument?.let { InstrumentTag(it) }
        // 점수 자릿수가 달라도 악기 칩이 한 열에 서도록 '100점' 폭을 잡고 오른쪽에 붙인다.
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = EolssuTheme.serif.yul.fontFamily, fontSize = 18.sp, fontWeight = FontWeight.Bold)) { append("${record.score}") }
                withStyle(SpanStyle(fontSize = 11.sp, color = colors.textMuted)) { append(stringResource(Res.string.records_score_unit)) }
            },
            modifier = Modifier.widthIn(min = RecordScoreWidth),
            textAlign = TextAlign.End,
        )
        // 줄 전체가 기록 상세로 가는 버튼임을 보이는 ›. 두 칸 배치에서 고른 줄은 검게.
        Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (on) colors.text else colors.textMuted)
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

/** 기록 줄 점수 칸의 최소 폭: 세리프 18sp ‘100’과 11sp ‘점’이 들어간다. */
private val RecordScoreWidth = 44.dp
