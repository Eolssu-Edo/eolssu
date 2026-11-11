package com.ssafy.eolssu.core.ui.score

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.ScoreErrorKind
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.key
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.error_segment_all
import eolssu.shared.generated.resources.error_segment_description
import eolssu.shared.generated.resources.error_segment_length
import eolssu.shared.generated.resources.error_segment_missed
import eolssu.shared.generated.resources.error_segment_pitch
import eolssu.shared.generated.resources.error_segment_rhythm
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
 * 오류 종류 5칸 세그먼트(전체 · 음정 · 박자 · 길이 · 미검출). 칸마다 위에 종류 아이콘과 개수, 아래에 이름을 둔다(높이 40).
 * 고른 칸은 흰 바탕, 나머지는 회색 바탕 그대로다. 이전 연주 결과와 연주 결과가 함께 쓴다.
 */
@Composable
fun ErrorKindSegments(
    result: EvaluationResult,
    selected: ErrorFilter,
    onSelect: (ErrorFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EolssuTheme.colors
    Row(
        modifier.fillMaxWidth().background(colors.surfaceStrong, RoundedCornerShape(12.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ErrorFilter.entries.forEach { filter ->
            val on = filter == selected
            val count = result.errorCount(filter)
            val name = segmentLabel(filter)
            val description = stringResource(Res.string.error_segment_description, name, count)
            Surface(
                onClick = { onSelect(filter) },
                modifier = Modifier.weight(1f).height(40.dp)
                    .then(if (on) Modifier.shadow(1.5.dp, RoundedCornerShape(9.dp)) else Modifier)
                    .semantics(mergeDescendants = true) {
                        this.selected = on
                        contentDescription = description
                    },
                shape = RoundedCornerShape(9.dp),
                color = if (on) colors.surface else Color.Transparent,
                contentColor = colors.text,
            ) {
                Column(
                    Modifier.clearAndSetSemantics {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SegmentTag(filter)
                        Text(
                            "$count",
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                        )
                    }
                    Text(
                        name,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp),
                        color = colors.textSub,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 칸 위의 14dp 아이콘 상자. 전체는 연두 바탕에 먹색 점, 종류는 [ErrorKindTag]와 같다. */
@Composable
private fun SegmentTag(filter: ErrorFilter) {
    val kind = filter.kind
    if (kind != null) {
        ErrorKindTag(kind, size = 14.dp, corner = 3.dp)
        return
    }
    val colors = EolssuTheme.colors
    Box(Modifier.size(14.dp).background(colors.primary, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(6.dp).background(colors.text, RoundedCornerShape(1.dp)))
    }
}

@Composable
private fun segmentLabel(filter: ErrorFilter): String = stringResource(
    when (filter) {
        ErrorFilter.All -> Res.string.error_segment_all
        ErrorFilter.Pitch -> Res.string.error_segment_pitch
        ErrorFilter.Rhythm -> Res.string.error_segment_rhythm
        ErrorFilter.Length -> Res.string.error_segment_length
        ErrorFilter.Missed -> Res.string.error_segment_missed
    },
)
