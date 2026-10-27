package com.ssafy.eolssu.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.core.ui.component.monthDayLabel
import com.ssafy.eolssu.core.ui.component.timeLabel
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.records_best
import eolssu.shared.generated.resources.records_collapsed
import eolssu.shared.generated.resources.records_expanded
import eolssu.shared.generated.resources.records_empty
import eolssu.shared.generated.resources.records_group_meta
import eolssu.shared.generated.resources.records_growth_link
import eolssu.shared.generated.resources.records_just_saved
import eolssu.shared.generated.resources.records_memo
import eolssu.shared.generated.resources.records_meta_section
import eolssu.shared.generated.resources.records_meta_whole
import eolssu.shared.generated.resources.records_score_unit
import eolssu.shared.generated.resources.records_song_count
import eolssu.shared.generated.resources.records_sort_new
import eolssu.shared.generated.resources.records_sort_old
import eolssu.shared.generated.resources.records_subtitle
import eolssu.shared.generated.resources.records_this_week
import eolssu.shared.generated.resources.records_this_week_none
import eolssu.shared.generated.resources.records_this_week_summary
import eolssu.shared.generated.resources.records_title
import eolssu.shared.generated.resources.records_today
import eolssu.shared.generated.resources.records_whole
import org.jetbrains.compose.resources.stringResource

/** RECORD-01 연습 기록: 이번 주 요약 · 곡별 녹음 묶음(펼치기) · 정렬. */
@Composable
fun RecordListScreen(
    state: RecordListUiState,
    onBack: () -> Unit,
    onOpenGrowth: () -> Unit,
    onToggle: (String) -> Unit,
    onSort: (RecordSort) -> Unit,
    onOpenRecord: (String) -> Unit,
) {
    EolssuScaffold(
        topBar = {
            EolssuTopBar(
                stringResource(Res.string.records_title), onBack = onBack,
                subtitle = stringResource(Res.string.records_subtitle, state.records.size),
            )
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            WeekCard(state, onOpenGrowth)
            val groups = state.groups
            if (groups.isEmpty()) {
                Text(
                    stringResource(Res.string.records_empty),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                return@Column
            }
            Row(Modifier.padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.records_song_count, groups.size),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = EolssuTheme.colors.textMuted,
                )
                SortSelect(state.sort, onSort)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { group ->
                    val open = state.isOpen(group.songId)
                    SongGroupCard(group, open, state, onToggle, onOpenRecord)
                }
            }
        }
    }
}

@Composable
private fun WeekCard(state: RecordListUiState, onOpenGrowth: () -> Unit) {
    val week = state.thisWeek
    Surface(onClick = onOpenGrowth, shape = MaterialTheme.shapes.medium, color = EolssuTheme.colors.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.records_this_week), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = EolssuTheme.colors.textMuted)
                Text(
                    if (week != null) stringResource(Res.string.records_this_week_summary, week.first, week.second) else stringResource(Res.string.records_this_week_none),
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                )
            }
            Text(stringResource(Res.string.records_growth_link), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.padding(start = 2.dp).size(16.dp))
        }
    }
}

@Composable
private fun SortSelect(sort: RecordSort, onSort: (RecordSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = @Composable { s: RecordSort -> stringResource(if (s == RecordSort.Newest) Res.string.records_sort_new else Res.string.records_sort_old) }
    Box {
        Row(
            Modifier
                .width(108.dp)
                .height(38.dp)
                .border(1.dp, EolssuTheme.colors.line, RoundedCornerShape(10.dp))
                .clickable(role = Role.DropdownList) { expanded = true }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label(sort), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
            Icon(EolssuIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = EolssuTheme.colors.background) {
            RecordSort.entries.forEach { s ->
                DropdownMenuItem(text = { Text(label(s)) }, onClick = {
                    expanded = false
                    onSort(s)
                })
            }
        }
    }
}

@Composable
private fun SongGroupCard(
    group: RecordGroup,
    open: Boolean,
    state: RecordListUiState,
    onToggle: (String) -> Unit,
    onOpenRecord: (String) -> Unit,
) {
    val colors = EolssuTheme.colors
    val expandedLabel = stringResource(Res.string.records_expanded)
    val collapsedLabel = stringResource(Res.string.records_collapsed)
    Surface(shape = MaterialTheme.shapes.medium, color = colors.background, border = BorderStroke(1.dp, colors.line)) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (open) colors.surface else colors.background)
                    .clickable(role = Role.Button) { onToggle(group.songId) }
                    .semantics { stateDescription = if (open) expandedLabel else collapsedLabel }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
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
                HorizontalDivider(color = colors.line)
                group.records.forEachIndexed { k, record ->
                    if (k > 0) HorizontalDivider(color = colors.surface)
                    RecordRow(record, record.id == state.justSavedId, state.today) { onOpenRecord(record.id) }
                }
            }
        }
    }
}

@Composable
private fun dayLabel(dateTime: KoreanDateTime, today: KoreanDateTime): String =
    if (dateTime.isSameDay(today)) stringResource(Res.string.records_today) + " " + monthDayLabel(dateTime) else monthDayLabel(dateTime)

@Composable
private fun RecordRow(record: PracticeRecord, justSaved: Boolean, today: KoreanDateTime, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).background(colors.surface, CircleShape), contentAlignment = Alignment.Center) {
            Icon(EolssuIcons.Play, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.text)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(record.section ?: stringResource(Res.string.records_whole), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
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
            Text("${dayLabel(record.recordedAt, today)} · ${timeLabel(record.recordedAt)}", modifier = Modifier.padding(top = 2.dp), style = small, color = colors.textMuted)
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
