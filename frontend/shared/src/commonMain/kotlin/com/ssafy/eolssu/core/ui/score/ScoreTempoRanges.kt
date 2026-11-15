package com.ssafy.eolssu.core.ui.score

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.ScoreTempoRange
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.score_tempo_bpm_range
import eolssu.shared.generated.resources.score_tempo_position
import eolssu.shared.generated.resources.score_tempo_bounds
import eolssu.shared.generated.resources.score_tempo_end
import eolssu.shared.generated.resources.score_tempo_boundary
import org.jetbrains.compose.resources.stringResource
import kotlin.math.round

/** 숫자를 세로 한배 칸 밖에 두고, 남은 높이를 악보의 단 나누기·스크롤에 전달한다. */
@Composable
fun ScoreTempoLayout(
    ranges: List<ScoreTempoRange>,
    modifier: Modifier = Modifier,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    if (ranges.isEmpty()) {
        BoxWithConstraints(modifier, content = content)
        return
    }
    BoxWithConstraints(modifier) {
        val bounded = constraints.hasBoundedHeight
        Column {
            ScoreTempoRanges(ranges)
            BoxWithConstraints(if (bounded) Modifier.weight(1f, fill = false) else Modifier, content = content)
        }
    }
}

@Composable
fun ScoreTempoRanges(ranges: List<ScoreTempoRange>, modifier: Modifier = Modifier) {
    if (ranges.isEmpty()) return
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ranges.forEach { range ->
            val start = stringResource(Res.string.score_tempo_position, range.start.column + 1, range.start.row + 1)
            val end = range.end?.let { stringResource(Res.string.score_tempo_boundary,
                stringResource(Res.string.score_tempo_position, it.column + 1, it.row + 1)) }
                ?: stringResource(Res.string.score_tempo_end)
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.semantics(mergeDescendants = true) {}.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(stringResource(Res.string.score_tempo_bpm_range, range.startBpm.tempoText(), range.targetBpm.tempoText()),
                        style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(Res.string.score_tempo_bounds, start, end), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun Double.tempoText(): String = (round(this * 10) / 10).toString().removeSuffix(".0")
