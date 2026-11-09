package com.ssafy.eolssu.core.ui.score

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.ScoreErrorKind
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.key
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.result_filter_all
import eolssu.shared.generated.resources.result_filter_length
import eolssu.shared.generated.resources.result_filter_missed
import eolssu.shared.generated.resources.result_filter_pitch
import eolssu.shared.generated.resources.result_filter_rhythm
import org.jetbrains.compose.resources.stringResource

/*
 * 평가 결과의 오류 종류 필터. 평가 결과(오류 위치)와 이전 연주 결과(연습 기록)가 함께 쓴다.
 */

/** 정간보에 표시할 오류 종류. [kind]가 null이면 모두 보인다. */
enum class ErrorFilter(val kind: ScoreErrorKind?) {
    All(null),
    Pitch(ScoreErrorKind.Pitch),
    Rhythm(ScoreErrorKind.Rhythm),
    Length(ScoreErrorKind.Length),
    Missed(ScoreErrorKind.Missed),
}

/**
 * [filter]에 해당하는 틀린 칸 수. 전체는 오류 종류가 여럿인 칸도 한 칸으로 센다('틀린 정간 N개').
 * 종류별은 그 종류가 있는 칸 수다.
 */
fun EvaluationResult.errorCount(filter: ErrorFilter): Int =
    if (filter.kind == null) errors.distinctBy { it.column to it.row }.size else errors.count { it.kind == filter.kind }

/**
 * 칸(각, 정간)마다의 오류 종류 전부. 한 칸에 음이 여럿이면 종류도 여럿일 수 있다.
 * 종류는 [ScoreErrorKind] 순서(음정·박자·음 길이·미검출)로 겹치지 않게 놓고, [filter]에 해당하는 종류만 남긴다.
 */
fun EvaluationResult.visibleErrorKinds(filter: ErrorFilter): Map<Pair<Int, Int>, List<ScoreErrorKind>> =
    errors.filter { filter.kind == null || it.kind == filter.kind }
        .groupBy({ it.column to it.row }, { it.kind })
        .mapValues { (_, kinds) -> kinds.distinct().sorted() }

/**
 * 소박(표시 자리)마다 칠할 틀린 종류. 음별 판정([EvaluationResult.eventErrors])이 없으면 빈 지도라 정간 단위로 칠한다.
 * [filter]에 해당하는 종류만 남긴다.
 */
fun EvaluationResult.visibleUnitErrors(song: Song, filter: ErrorFilter): Map<ScoreUnitKey, List<ScoreErrorKind>> {
    if (eventErrors.isEmpty()) return emptyMap()
    return song.displayUnits.mapNotNull { unit ->
        val kinds = eventErrors[unit.event.index].orEmpty().filter { filter.kind == null || it == filter.kind }
        if (kinds.isEmpty()) null else unit.key to kinds
    }.toMap()
}

/** 정간보에 칠할 틀린 칸(각, 정간) → 대표 종류(바탕·테두리 색). 대표는 [visibleErrorKinds]의 첫 종류다. */
fun EvaluationResult.visibleErrors(filter: ErrorFilter): Map<Pair<Int, Int>, ScoreErrorKind> =
    visibleErrorKinds(filter).mapValues { it.value.first() }

/**
 * 오류 종류 필터(전체·음정·박자·길이·미검출). 고른 종류의 틀린 정간만 정간보에 색칠한다.
 * [hideEmptyMissed]이면 미검출이 없을 때 그 칩을 뺀다(2차 틀린 부분 RESULT-03은 네 칩).
 * 작은 칩(28dp)을 한 줄에 나눠 놓고, 좁으면 글자를 9sp까지 줄여 줄이 넘어가지 않게 한다(이전 연주 결과·틀린 부분).
 */
@Composable
fun ErrorFilterChips(
    result: EvaluationResult,
    selected: ErrorFilter,
    onSelect: (ErrorFilter) -> Unit,
    modifier: Modifier = Modifier,
    hideEmptyMissed: Boolean = false,
) {
    val filters = ErrorFilter.entries.filterNot { hideEmptyMissed && it == ErrorFilter.Missed && result.errorCount(it) == 0 }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        filters.forEach { filter ->
            val count = result.errorCount(filter)
            val label = filterLabel(filter, count)
            // 글자 수(견본·여백 몫 2자 포함)만큼 나눠, 좁아질 때 모든 칩의 글자가 비슷한 크기로 줄게 한다.
            CompactFilterChip(filter, label, on = filter == selected, Modifier.weight(label.length + 2f)) { onSelect(filter) }
        }
    }
}

/** 한 줄 필터의 작은 칩: 28dp 높이 · 8dp 견본 · 12sp(좁으면 9sp까지 줄임). */
@Composable
private fun CompactFilterChip(filter: ErrorFilter, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.height(28.dp).semantics { selected = on },
        shape = CircleShape,
        color = if (on) colors.primary else colors.background,
        contentColor = if (on) colors.onPrimary else colors.text,
        border = BorderStroke(1.dp, if (on) colors.accentText else colors.line),
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        ) {
            FilterSwatch(filter, on, 8.dp)
            BasicText(
                label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (on) colors.onPrimary else colors.text,
                ),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
            )
        }
    }
}

/** 칩 앞의 색 견본. 전체는 연두 상자, 종류는 그 종류의 칸 색이다. */
@Composable
private fun FilterSwatch(filter: ErrorFilter, on: Boolean, size: Dp) {
    val colors = EolssuTheme.colors
    val kind = filter.kind
    if (kind == null) {
        Box(Modifier.size(size).background(if (on) colors.onPrimary else colors.primary, RoundedCornerShape(3.dp)))
    } else {
        ErrorMarkBox(kind, Modifier.size(size))
    }
}

@Composable
private fun filterLabel(filter: ErrorFilter, count: Int): String = stringResource(
    when (filter) {
        ErrorFilter.All -> Res.string.result_filter_all
        ErrorFilter.Pitch -> Res.string.result_filter_pitch
        ErrorFilter.Rhythm -> Res.string.result_filter_rhythm
        ErrorFilter.Length -> Res.string.result_filter_length
        ErrorFilter.Missed -> Res.string.result_filter_missed
    },
    count,
)
