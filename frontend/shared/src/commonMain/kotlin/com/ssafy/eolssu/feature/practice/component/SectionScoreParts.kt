package com.ssafy.eolssu.feature.practice.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScoreError
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.scoreStrips
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.score.ScoreCellDecoration
import com.ssafy.eolssu.core.ui.score.errorDecoration
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.weak_edge_end
import eolssu.shared.generated.resources.weak_edge_start
import org.jetbrains.compose.resources.stringResource

/** 구간 반복·재평가는 소박 율명만 조금 키우고, 나뉘지 않은 율명과 각 너비는 유지한다. */
internal fun sectionLoopScoreMetrics(song: Song): JeongganboMetrics {
    val longestStrip = scoreStrips(song.score, song.jangdan).maxOfOrNull { it.rows.count() } ?: 1
    val metrics = JeongganboMetrics.practiceBoard(big = false, longestStrip = longestStrip)
    return metrics.copy(splitScaleTwo = 16f / metrics.yulSize.value, splitScaleMany = 15f / metrics.yulSize.value)
}

/**
 * 취약 구간 정간 꾸밈(PRACTICE-02·03). 틀린 정간은 종류별 색·아이콘이고, [section] 밖 정간은 [outsideAlpha]로 흐리게 한다.
 * [edgeLabels]면 구간 처음·끝 정간에 ‘처음’·‘끝’ 알약을 붙이고(구간 반복 시안에는 없다), [edgeRing]이면 먹색 안쪽 테두리를 두른다.
 *
 * @param markColumns 이 각의 틀린 정간만 칠한다(구간 반복의 앞뒤 각은 칠하지 않는다). null이면 모두.
 * @param current 지금 연주 위치. 연두 현재 표시와 겹치지 않게 종류 테두리를 빼고 바탕만 둔다.
 */
@Composable
internal fun sectionDecorations(
    score: Score,
    errors: List<ScoreError>,
    section: ScoreSection?,
    outsideAlpha: Float,
    edgeRing: Boolean,
    edgeLabels: Boolean = true,
    columns: IntRange = score.columns.indices,
    markColumns: IntRange? = null,
    current: ScorePosition? = null,
): Map<Pair<Int, Int>, ScoreCellDecoration> {
    val colors = EolssuTheme.colors
    val startLabel = stringResource(Res.string.weak_edge_start)
    val endLabel = stringResource(Res.string.weak_edge_end)
    val kinds = errors.associate { (it.column to it.row) to it.kind }
    val out = mutableMapOf<Pair<Int, Int>, ScoreCellDecoration>()
    for (column in columns) {
        val cells = score.columns.getOrNull(column) ?: continue
        for (row in cells.indices) {
            val position = ScorePosition(column, row)
            val kind = kinds[column to row]?.takeIf { markColumns == null || column in markColumns }
            var decoration = kind?.let { errorDecoration(it) } ?: ScoreCellDecoration()
            val inSection = section == null || position in section
            if (!inSection) decoration = decoration.copy(alpha = outsideAlpha)
            if (section != null && (position == section.start || position == section.end)) {
                val label = if (position == section.start) startLabel else endLabel
                decoration = decoration.copy(edgeLabel = label.takeIf { edgeLabels }, ring = if (edgeRing) colors.text else decoration.ring, dashed = decoration.dashed && !edgeRing)
            }
            if (position == current) decoration = decoration.copy(ring = null)
            if (decoration != ScoreCellDecoration()) out[column to row] = decoration
        }
    }
    return out
}

/** 시안의 반복 횟수 버튼(28dp, 반지름 8): 고르면 연두 바탕. */
@Composable
internal fun SegmentButton(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 28.dp).semantics { this.selected = selected },
        shape = RoundedCornerShape(8.dp),
        color = if (selected) colors.primary else colors.surface,
        contentColor = colors.text,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.lineSoft),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
        }
    }
}
