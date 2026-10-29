package com.ssafy.eolssu.feature.result.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.score.ErrorMarkBox
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.ScoreNoteGlyph
import com.ssafy.eolssu.core.ui.score.scoreStrips
import com.ssafy.eolssu.feature.result.ErrorFilter
import com.ssafy.eolssu.feature.result.ScoreCellPosition
import com.ssafy.eolssu.feature.result.errorAt
import com.ssafy.eolssu.feature.result.errorCount
import com.ssafy.eolssu.feature.result.metricLabel
import com.ssafy.eolssu.feature.result.visibleErrors
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.result_cell_error
import eolssu.shared.generated.resources.result_cell_ok_body
import eolssu.shared.generated.resources.result_cell_ok_title
import eolssu.shared.generated.resources.result_cell_sample_length
import eolssu.shared.generated.resources.result_cell_sample_pitch
import eolssu.shared.generated.resources.result_cell_sample_rhythm
import eolssu.shared.generated.resources.result_cell_where
import eolssu.shared.generated.resources.result_errors_cluster
import eolssu.shared.generated.resources.result_errors_count
import eolssu.shared.generated.resources.result_errors_none
import eolssu.shared.generated.resources.result_errors_title
import eolssu.shared.generated.resources.result_filter_all
import eolssu.shared.generated.resources.result_filter_length
import eolssu.shared.generated.resources.result_filter_pitch
import eolssu.shared.generated.resources.result_filter_rhythm
import eolssu.shared.generated.resources.score_hold
import eolssu.shared.generated.resources.score_rest
import org.jetbrains.compose.resources.stringResource

/** 오류 위치 제목과 요약('틀린 칸 14개 중 9개가 3–4각에 몰려 있어요'). */
@Composable
fun ErrorSummary(result: EvaluationResult, modifier: Modifier = Modifier) {
    val cluster = result.densestColumnPair()
    Column(modifier) {
        Text(stringResource(Res.string.result_errors_title), style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
        Text(
            when {
                cluster != null -> stringResource(Res.string.result_errors_cluster, result.errors.size, cluster.second, cluster.first + 1, cluster.first + 2)
                result.errors.isEmpty() -> stringResource(Res.string.result_errors_none)
                else -> stringResource(Res.string.result_errors_count, result.errors.size)
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.5.sp),
            color = EolssuTheme.colors.textMuted,
        )
    }
}

/** 오류 종류 필터(전체·음정·박자·길이). 고른 종류의 틀린 칸만 정간보에 색칠한다. */
@Composable
fun ErrorFilterChips(result: EvaluationResult, selected: ErrorFilter, onSelect: (ErrorFilter) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ErrorFilter.entries.forEach { filter ->
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
            val metric = filter.metric
            if (metric == null) {
                Box(Modifier.size(10.dp).background(if (on) colors.onPrimary else colors.primary, RoundedCornerShape(3.dp)))
            } else {
                ErrorMarkBox(metric, Modifier.size(10.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }
}

/**
 * 고른 칸 설명: 칸의 글자 · 위치와 음 이름 · 오류 종류. 틀리지 않은 칸이면 그렇다고 알린다.
 * 오류마다의 자세한 설명은 아직 결과에 없어 예시 결과([EvaluationResult.sample])일 때만 예시 문구를 붙인다.
 */
@Composable
fun SelectedCellCard(song: Song, result: EvaluationResult, cell: ScoreCellPosition, modifier: Modifier = Modifier, markFallback: Color) {
    val colors = EolssuTheme.colors
    val units = song.displayUnits.filter { it.column == cell.column && it.row == cell.row }
    val notes = units.map { it.note }
    val metric = result.errorAt(cell)
    val names = units.map { unit ->
        when (val note = unit.note) {
            is ScoreNote.Pitch -> note.yul.fullName
            is ScoreNote.SourcePitch -> note.fullName
            ScoreNote.Hold -> stringResource(Res.string.score_hold)
            ScoreNote.Blank -> stringResource(if (unit.event.note == ScoreNote.Rest) Res.string.score_rest else Res.string.score_hold)
            ScoreNote.Rest -> stringResource(Res.string.score_rest)
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ErrorMarkBox(metric, Modifier.size(44.dp), cornerRadius = 10.dp, fallback = markFallback) {
            val size = if (notes.size > 1) 16.sp else 22.sp
            Column(Modifier.fillMaxSize()) {
                val rows = units.groupBy { it.unitRow }.values
                rows.forEach { units ->
                    Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        units.forEach { unit ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ScoreNoteGlyph(unit.note, size) }
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.result_cell_where, cell.column + 1, cell.row + 1, names.joinToString("·")),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textMuted,
            )
            Text(
                if (metric != null) stringResource(Res.string.result_cell_error, metricLabel(metric)) else stringResource(Res.string.result_cell_ok_title),
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
            val desc = when {
                metric == null -> stringResource(Res.string.result_cell_ok_body)
                result.sample -> stringResource(sampleDescription(metric))
                else -> null
            }
            if (desc != null) {
                Text(desc, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
            }
        }
    }
}

private fun sampleDescription(metric: EvaluationMetric) = when (metric) {
    EvaluationMetric.Pitch -> Res.string.result_cell_sample_pitch
    EvaluationMetric.Rhythm -> Res.string.result_cell_sample_rhythm
    EvaluationMetric.Length -> Res.string.result_cell_sample_length
}

/**
 * 틀린 칸을 표시한 정간보 전체. 고른 종류의 틀린 칸만 색칠하고 나머지는 흐리게, 틀린 칸이 몰린 두 각은 노랗게 강조한다.
 * 칸(음)을 누르면 [onSelectCell]로 알린다.
 *
 * @param wrap true면 가로 스크롤 없이 너비에 맞춰 각을 여러 줄로 놓는다(태블릿 세로). false면 한 줄에 놓고 가로로 스크롤한다(태블릿 가로).
 */
@Composable
fun ErrorScore(
    song: Song,
    result: EvaluationResult,
    filter: ErrorFilter,
    selected: ScoreCellPosition?,
    onSelectCell: (ScoreCellPosition) -> Unit,
    wrap: Boolean,
    modifier: Modifier = Modifier,
) {
    val errors = result.visibleErrors(filter)
    val cluster = result.densestColumnPair()?.let { it.first..it.first + 1 }
    val selectedCell = selected?.let { it.column to it.row }
    val strips = scoreStrips(song.score, song.jangdan)
    val longest = strips.maxOf { it.rows.count() }
    val stripsPerColumn = strips.groupBy { it.column }.maxOf { it.value.size }
    val columnCount = song.score.columns.size
    BoxWithConstraints(modifier) {
        if (wrap) {
            val metrics = ErrorScoreMetrics.copy(cellHeight = WrapCellHeight)
            val rendered = metrics.forFontScale(androidx.compose.ui.platform.LocalDensity.current.fontScale)
            val stripsFit = ((maxWidth - 4.dp + rendered.columnGap) / (rendered.columnWidth + rendered.columnGap)).toInt()
            val perRow = maxOf(1, stripsFit / stripsPerColumn)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for (first in 0 until columnCount step perRow) {
                    JeongganboView(
                        song = song,
                        metrics = metrics,
                        errors = errors,
                        selectedCell = selectedCell,
                        onUnitClick = { onSelectCell(ScoreCellPosition(it.column, it.row)) },
                        columns = first until minOf(first + perRow, columnCount),
                        dimUnmarked = true,
                        highlightColumns = cluster,
                    )
                }
            }
        } else {
            val fitted = (maxHeight - ErrorScoreMetrics.headHeight - 12.dp) / longest
            val metrics = ErrorScoreMetrics.copy(cellHeight = maxOf(fitted, WrapCellHeight))
            JeongganboView(
                song = song,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                metrics = metrics,
                errors = errors,
                selectedCell = selectedCell,
                onUnitClick = { onSelectCell(ScoreCellPosition(it.column, it.row)) },
                dimUnmarked = true,
                highlightColumns = cluster,
            )
        }
    }
}

/** 결과 화면 정간보 크기(와이어프레임: 각 60px, 율명 16px). 정간 높이는 배치마다 정한다. */
private val ErrorScoreMetrics = JeongganboMetrics(columnWidth = 60.dp, cellHeight = 36.dp, yulSize = 16.sp, auxSize = 8.5.sp)

/** 태블릿 세로에서 여러 줄로 놓을 때의 정간 높이이자 가로 배치의 최소 정간 높이. */
private val WrapCellHeight: Dp = 36.dp
