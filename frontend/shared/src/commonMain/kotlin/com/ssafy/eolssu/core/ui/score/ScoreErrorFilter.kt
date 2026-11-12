package com.ssafy.eolssu.core.ui.score

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * 오류 종류 5칸 탭 바(전체 · 음정 · 박자 · 길이 · 미검출). 이전 연주 결과와 연주 결과가 함께 쓴다.
 * 흰 알약 틀(높이 52, 안쪽 5dp) 안에서 고른 칸만 연한 연두(accentTintStrong) 알약으로 넓게 펼쳐 '아이콘 · 이름 · 개수'를 보이고,
 * 나머지 칸은 '아이콘 · 개수'만 둔다. 고른 칸이 바뀌면 너비·바탕·이름이 함께 부드럽게 바뀐다(S15P31E102-786).
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
        modifier.fillMaxWidth().height(52.dp).background(colors.surface, CircleShape).padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ErrorFilter.entries.forEach { filter ->
            val on = filter == selected
            val count = result.errorCount(filter)
            val name = segmentLabel(filter)
            val description = stringResource(Res.string.error_segment_description, name, count)
            val weight by animateFloatAsState(if (on) SelectedTabWeight else 1f, tween(TabMotionMillis), label = "errorTabWeight")
            val fill by animateColorAsState(if (on) colors.accentTintStrong else Color.Transparent, tween(TabMotionMillis), label = "errorTabFill")
            Surface(
                onClick = { onSelect(filter) },
                modifier = Modifier.weight(weight).fillMaxHeight()
                    .semantics(mergeDescendants = true) {
                        this.selected = on
                        contentDescription = description
                    },
                shape = CircleShape,
                color = fill,
                contentColor = if (on) colors.text else colors.textSub,
            ) {
                Row(
                    Modifier.clearAndSetSemantics {}.padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SegmentTag(filter)
                    AnimatedVisibility(
                        visible = on,
                        enter = fadeIn(tween(TabMotionMillis)) + expandHorizontally(tween(TabMotionMillis)),
                        exit = fadeOut(tween(TabMotionMillis / 2)) + shrinkHorizontally(tween(TabMotionMillis)),
                    ) {
                        Text(
                            name,
                            Modifier.padding(start = 6.dp),
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Text(
                        "$count",
                        Modifier.padding(start = if (on) 4.dp else 3.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        ),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** 고른 칸이 다른 칸보다 넓어지는 비율. 폰 너비에서 '미검출 12'가 한 줄에 들어가는 값이다. */
private const val SelectedTabWeight = 2.2f

/** 고른 칸이 바뀔 때 너비·바탕·이름이 바뀌는 시간(ms). */
private const val TabMotionMillis = 240

/**
 * 칸 왼쪽의 16dp 아이콘. 종류는 [ErrorKindTag]와 같고,
 * 전체는 네 종류(음정·박자·음 길이·미검출) 색의 7dp 칸 네 개를 2×2로 놓아 '모든 종류'를 뜻한다(간격 1dp·모서리 2dp).
 */
@Composable
private fun SegmentTag(filter: ErrorFilter) {
    val kind = filter.kind
    if (kind != null) {
        ErrorKindTag(kind, size = 16.dp, corner = 4.dp)
        return
    }
    val kinds = ScoreErrorKind.entries
    Column(Modifier.size(16.dp).padding(0.5.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        kinds.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                pair.forEach { Box(Modifier.size(7.dp).background(errorStyle(it).tag, RoundedCornerShape(2.dp))) }
            }
        }
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
