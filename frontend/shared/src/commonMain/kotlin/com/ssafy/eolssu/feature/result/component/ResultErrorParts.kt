package com.ssafy.eolssu.feature.result.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.ScoreErrorKind
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.score.ErrorKindTag
import com.ssafy.eolssu.core.ui.score.errorKindLabel
import com.ssafy.eolssu.core.ui.score.ErrorMarkBox
import com.ssafy.eolssu.core.ui.score.ScoreNoteGlyph
import com.ssafy.eolssu.feature.result.ErrorFilter
import com.ssafy.eolssu.feature.result.ScoreCellPosition
import com.ssafy.eolssu.feature.result.errorKindsAt
import com.ssafy.eolssu.feature.result.shownUnit
import com.ssafy.eolssu.feature.result.errorCount
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.result_cell_error
import eolssu.shared.generated.resources.result_cell_missed_body
import eolssu.shared.generated.resources.result_cell_missed_title
import eolssu.shared.generated.resources.result_cell_ok_body
import eolssu.shared.generated.resources.result_cell_ok_title
import eolssu.shared.generated.resources.result_cell_ok_unit_body
import eolssu.shared.generated.resources.result_cell_ok_unit_title
import eolssu.shared.generated.resources.result_cell_sample_length
import eolssu.shared.generated.resources.result_cell_sample_pitch
import eolssu.shared.generated.resources.result_cell_sample_rhythm
import eolssu.shared.generated.resources.result_cell_where
import eolssu.shared.generated.resources.result_cell_where_unit
import eolssu.shared.generated.resources.result_filter_all
import eolssu.shared.generated.resources.result_filter_length
import eolssu.shared.generated.resources.result_filter_missed
import eolssu.shared.generated.resources.result_filter_pitch
import eolssu.shared.generated.resources.result_filter_rhythm
import eolssu.shared.generated.resources.score_hold
import eolssu.shared.generated.resources.score_rest
import org.jetbrains.compose.resources.stringResource

/**
 * 오류 종류 필터(전체·음정·박자·길이·미검출). 고른 종류의 틀린 정간만 정간보에 색칠한다.
 * [hideEmptyMissed]이면 미검출이 없을 때 그 칩을 뺀다(2차 오류 위치 RESULT-03은 네 칩).
 */
@Composable
fun ErrorFilterChips(
    result: EvaluationResult,
    selected: ErrorFilter,
    onSelect: (ErrorFilter) -> Unit,
    modifier: Modifier = Modifier,
    hideEmptyMissed: Boolean = false,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ErrorFilter.entries.filterNot { hideEmptyMissed && it == ErrorFilter.Missed && result.errorCount(it) == 0 }.forEach { filter ->
            FilterChip(filter, result.errorCount(filter), on = filter == selected) { onSelect(filter) }
        }
    }
}

@Composable
private fun FilterChip(filter: ErrorFilter, count: Int, on: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    val label = stringResource(
        when (filter) {
            ErrorFilter.All -> Res.string.result_filter_all
            ErrorFilter.Pitch -> Res.string.result_filter_pitch
            ErrorFilter.Rhythm -> Res.string.result_filter_rhythm
            ErrorFilter.Length -> Res.string.result_filter_length
            ErrorFilter.Missed -> Res.string.result_filter_missed
        },
        count,
    )
    Surface(
        onClick = onClick,
        modifier = Modifier.height(34.dp).semantics { selected = on },
        shape = CircleShape,
        color = if (on) colors.primary else colors.background,
        contentColor = if (on) colors.onPrimary else colors.text,
        border = BorderStroke(1.dp, if (on) colors.accentText else colors.line),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            val kind = filter.kind
            if (kind == null) {
                Box(Modifier.size(10.dp).background(if (on) colors.onPrimary else colors.primary, RoundedCornerShape(3.dp)))
            } else {
                ErrorMarkBox(kind, Modifier.size(10.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }
}

/**
 * 고른 칸 설명: 칸의 글자 · 위치와 음 이름 · 오류 종류. 틀리지 않은 칸이면 그렇다고 알린다.
 * 소박으로 나뉜 정간은 고른 소박 하나만 크게 보여 글자가 잘리지 않게 하고, 그 소박의 판정을 적는다.
 * 오류가 여럿이면 종류를 모두 적고('음정·박자 오류') 종류 아이콘을 나란히 둔다.
 * 미검출은 판정 의미를 표시하고, 다른 오류의 상세 설명은 예시 결과([EvaluationResult.sample])일 때만 붙인다.
 */
@Composable
fun SelectedCellCard(song: Song, result: EvaluationResult, cell: ScoreCellPosition, modifier: Modifier = Modifier, markFallback: Color) {
    val colors = EolssuTheme.colors
    val units = song.displayUnits.filter { it.column == cell.column && it.row == cell.row }
    val split = units.size > 1
    val unit = result.shownUnit(cell, units)
    val kinds = if (split && unit != null) result.errorKindsAt(unit) else result.errorKindsAt(cell)
    val kind = kinds.firstOrNull()
    val name = unit?.let { shown ->
        when (val note = shown.note) {
            is ScoreNote.Pitch -> note.yul.fullName
            is ScoreNote.SourcePitch -> note.fullName
            ScoreNote.Hold -> stringResource(Res.string.score_hold)
            ScoreNote.Blank -> stringResource(if (shown.event.note == ScoreNote.Rest) Res.string.score_rest else Res.string.score_hold)
            ScoreNote.Rest -> stringResource(Res.string.score_rest)
        }
    }.orEmpty()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ErrorMarkBox(kind, Modifier.size(44.dp), cornerRadius = 10.dp, fallback = markFallback) {
            if (unit != null) ScoreNoteGlyph(unit.note, 22.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (split && unit != null) {
                    stringResource(Res.string.result_cell_where_unit, cell.column + 1, cell.row + 1, units.indexOf(unit) + 1, name)
                } else {
                    stringResource(Res.string.result_cell_where, cell.column + 1, cell.row + 1, name)
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textMuted,
            )
            val judged = kinds.filter { it != ScoreErrorKind.Missed }
            val missed = ScoreErrorKind.Missed in kinds
            // 미검출이 다른 종류와 섞이면 짧은 이름('미검출')으로 함께 적는다.
            val labels = kinds.map { errorKindLabel(it) }
            Row(
                Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (kinds.size > 1) kinds.forEach { ErrorKindTag(it, size = 16.dp, corner = 4.dp) }
                Text(
                    when {
                        kind == null -> stringResource(if (split) Res.string.result_cell_ok_unit_title else Res.string.result_cell_ok_title)
                        judged.isEmpty() -> stringResource(Res.string.result_cell_missed_title)
                        else -> stringResource(Res.string.result_cell_error, labels.joinToString("·"))
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
            }
            val descriptions = buildList {
                if (kind == null) add(stringResource(if (split) Res.string.result_cell_ok_unit_body else Res.string.result_cell_ok_body))
                if (result.sample) judged.forEach { add(stringResource(sampleDescription(it))) }
                if (missed) add(stringResource(Res.string.result_cell_missed_body))
            }
            descriptions.forEach { desc ->
                Text(desc, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
            }
        }
    }
}

private fun sampleDescription(kind: ScoreErrorKind) = when (kind) {
    ScoreErrorKind.Pitch -> Res.string.result_cell_sample_pitch
    ScoreErrorKind.Rhythm -> Res.string.result_cell_sample_rhythm
    ScoreErrorKind.Length -> Res.string.result_cell_sample_length
    ScoreErrorKind.Missed -> Res.string.result_cell_missed_body
}
