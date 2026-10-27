package com.ssafy.eolssu.feature.result.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.ScoreError
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.result_legend_length
import eolssu.shared.generated.resources.result_legend_pitch
import eolssu.shared.generated.resources.result_legend_rhythm
import eolssu.shared.generated.resources.result_tag_length
import eolssu.shared.generated.resources.result_tag_pitch
import eolssu.shared.generated.resources.result_tag_rhythm
import org.jetbrains.compose.resources.stringResource

/** 오류 종류의 테두리·바탕·글자 색. 음 길이는 점선 테두리. */
private data class ErrorStyle(val ring: androidx.compose.ui.graphics.Color, val fill: androidx.compose.ui.graphics.Color, val tag: androidx.compose.ui.graphics.Color, val dashed: Boolean)

@Composable
private fun errorStyle(metric: EvaluationMetric): ErrorStyle {
    val c = EolssuTheme.colors
    return when (metric) {
        EvaluationMetric.Pitch -> ErrorStyle(c.pitchError, c.pitchErrorSoft, c.pitchErrorText, dashed = false)
        EvaluationMetric.Rhythm -> ErrorStyle(c.rhythmError, c.rhythmErrorSoft, c.rhythmErrorText, dashed = false)
        EvaluationMetric.Length -> ErrorStyle(c.text, c.surfaceStrong, c.text, dashed = true)
    }
}

@Composable
fun errorTag(metric: EvaluationMetric): String = stringResource(
    when (metric) {
        EvaluationMetric.Pitch -> Res.string.result_tag_pitch
        EvaluationMetric.Rhythm -> Res.string.result_tag_rhythm
        EvaluationMetric.Length -> Res.string.result_tag_length
    },
)

/** 오류 종류 범례(음정·박자·음 길이). */
@Composable
fun ErrorLegend(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf(
            EvaluationMetric.Pitch to Res.string.result_legend_pitch,
            EvaluationMetric.Rhythm to Res.string.result_legend_rhythm,
            EvaluationMetric.Length to Res.string.result_legend_length,
        ).forEach { (metric, label) ->
            val style = errorStyle(metric)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.size(10.dp).background(style.fill, RoundedCornerShape(3.dp)).drawBehind {
                        drawRoundRect(
                            style.ring, cornerRadius = CornerRadius(3.dp.toPx()),
                            style = Stroke(1.5.dp.toPx(), pathEffect = if (style.dashed) PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 1.5.dp.toPx())) else null),
                        )
                    },
                )
                Text(stringResource(label), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = EolssuTheme.colors.textMuted)
            }
        }
    }
}

/**
 * 작은 정간보 미리보기(각 [columns]개). 틀린 정간을 색칠하고 오른쪽 위에 종류 글자(음·박·길)를 붙인다.
 * [highlight]에 든 각은 노란 테두리로 강조한다.
 */
@Composable
fun ErrorScorePreview(
    song: Song,
    errors: List<ScoreError>,
    columns: IntRange,
    highlight: IntRange?,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = EolssuTheme.colors
    val byCell = errors.associateBy { it.column to it.row }
    val starts = song.jangdan.daegangStarts
    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        for (c in columns.reversed()) {
            val cells = song.score.columns.getOrNull(c) ?: continue
            val strong = highlight != null && c in highlight
            val shape = RoundedCornerShape(6.dp)
            Column(
                Modifier
                    .width(34.dp)
                    .then(if (strong) Modifier.border(4.dp, colors.accentSoft, RoundedCornerShape(8.dp)).padding(2.dp) else Modifier)
                    .clip(shape)
                    .background(colors.background)
                    .border(1.dp, if (strong) colors.accent else colors.line, shape),
            ) {
                Box(Modifier.fillMaxWidth().height(16.dp).background(colors.surface), contentAlignment = Alignment.Center) {
                    Text("${c + 1}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold), color = colors.textMuted)
                }
                cells.forEachIndexed { r, cell ->
                    val error = byCell[c to r]
                    val style = error?.let { errorStyle(it.metric) }
                    val lineColor = if (r in starts) colors.lineStrong else colors.line
                    val lineWidth = if (r in starts) 2.dp else 1.dp
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .then(if (style != null) Modifier.background(style.fill) else Modifier)
                            .drawBehind {
                                drawRect(lineColor, size = Size(size.width, lineWidth.toPx()))
                                if (style != null) {
                                    val inset = if (style.dashed) 3.dp.toPx() else 0.75.dp.toPx()
                                    drawRect(
                                        style.ring, topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2),
                                        style = Stroke(
                                            1.5.dp.toPx(),
                                            pathEffect = if (style.dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null,
                                        ),
                                    )
                                }
                            },
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            cell.notes.forEach { note ->
                                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                    val pitch = note is ScoreNote.Pitch
                                    val size = if (cell.notes.size > 1) 9.sp else 12.sp
                                    Text(
                                        note.symbol,
                                        style = EolssuTheme.serif.yul.copy(fontSize = size, lineHeight = size),
                                        fontWeight = if (pitch) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (pitch) colors.text else colors.textMuted,
                                    )
                                }
                            }
                        }
                        if (error != null && style != null) {
                            Text(
                                errorTag(error.metric),
                                modifier = Modifier.align(Alignment.TopEnd).padding(end = 1.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold),
                                color = style.tag,
                            )
                        }
                    }
                }
            }
        }
    }
}
