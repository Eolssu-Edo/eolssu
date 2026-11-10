package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.song.edit.CommonStaffGlyph
import com.ssafy.eolssu.feature.song.edit.CommonStaffProjection
import com.ssafy.eolssu.feature.song.edit.SheetEditUiState
import com.ssafy.eolssu.feature.song.edit.StaffNoteKey
import com.ssafy.eolssu.feature.song.edit.commonStaffProjection
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CommonStaffEditor(
    state: SheetEditUiState,
    onPick: (StaffNoteKey) -> Unit,
    onShift: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EolssuTheme.colors
    val projection = remember(state.draft) { state.draft.commonStaffProjection() }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (projection) {
            is CommonStaffProjection.Unsupported -> Text(stringResource(when (projection) {
                CommonStaffProjection.Unsupported.Incomplete -> Res.string.sheet_staff_incomplete
                CommonStaffProjection.Unsupported.Document -> Res.string.sheet_staff_invalid
                CommonStaffProjection.Unsupported.Tuning -> Res.string.sheet_staff_tuning
                CommonStaffProjection.Unsupported.PitchRange -> Res.string.sheet_staff_pitch_range
                CommonStaffProjection.Unsupported.TimingUnit -> Res.string.sheet_staff_timing_unit
                CommonStaffProjection.Unsupported.Duration -> Res.string.sheet_staff_duration
                CommonStaffProjection.Unsupported.Size -> Res.string.sheet_staff_size
            }), style = MaterialTheme.typography.bodyMedium)
            is CommonStaffProjection.Ready -> {
                val glyphs = projection.glyphs
                val selected = remember(glyphs, state.staffKey) { glyphs.indexOfFirst { it.key == state.staffKey } }
                val scroll = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceAtLeast(0))
                val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
                val step = 6.dp * scale
                val highest = remember(glyphs) { maxOf(38, glyphs.maxOf { it.pitch?.step ?: 38 }) }
                val lowest = remember(glyphs) { minOf(30, glyphs.minOf { it.pitch?.step ?: 30 }) }
                val bottom = 54.dp * scale + step * (highest - 30)
                val height = bottom + step * (30 - lowest) + 48.dp * scale
                Text(stringResource(Res.string.sheet_staff_generated), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                Row(Modifier.fillMaxWidth().background(colors.background)) {
                    Canvas(Modifier.width(52.dp * scale).height(height)) {
                        drawStaffLines(bottom.toPx(), step.toPx(), colors.lineStrong)
                        drawTrebleClef(bottom.toPx() - 2 * step.toPx(), scale, colors.text)
                    }
                    LazyRow(Modifier.weight(1f), state = scroll) {
                        items(glyphs, key = { "${it.event.id}:${it.sliceIndex}" }) { glyph ->
                            val chosen = glyph.key == state.staffKey
                            val pitch = glyph.pitch?.name ?: stringResource(Res.string.score_rest)
                            val label = buildList {
                                add(stringResource(Res.string.sheet_staff_value, pitch, glyph.denominator))
                                if (glyph.dots > 0) add(stringResource(Res.string.sheet_staff_dots, glyph.dots))
                                if (glyph.tuplet) add(stringResource(Res.string.sheet_staff_triplet))
                                if (glyph.tieFromPrevious || glyph.tieToNext) add(stringResource(Res.string.sheet_staff_tie))
                            }.joinToString(", ")
                            Column(Modifier.width(78.dp * scale)
                                .background(if (chosen) colors.accentTint else colors.background)
                                .then(if (chosen) Modifier.border(1.dp, colors.accentTintStrong) else Modifier)
                                .clickable(role = Role.Button) { onPick(glyph.key) }
                                .semantics { contentDescription = label; this.selected = chosen },
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Canvas(Modifier.fillMaxWidth().height(height)) {
                                    drawStaffLines(bottom.toPx(), step.toPx(), colors.lineStrong)
                                    drawStaffGlyph(glyph, bottom.toPx(), step.toPx(), scale, colors.text)
                                }
                                Text(pitch, style = MaterialTheme.typography.labelMedium)
                                if (glyph.tuplet) Text("3:2", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                val glyph = glyphs.getOrNull(selected)
                if (glyph == null) {
                    Text(stringResource(Res.string.sheet_staff_pick_note), style = MaterialTheme.typography.bodySmall)
                } else {
                    val note = glyph.event.note as? ScoreNote.SourcePitch
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            val cell = stringResource(Res.string.sheetedit_where_short, glyph.event.column + 1, glyph.event.row + 1)
                            val location = if (state.draft.cells[glyph.key.start].size > 1)
                                stringResource(Res.string.sheetedit_staff_where_sub, cell, glyph.key.sub + 1) else cell
                            Text(location,
                                style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                            Text(note?.let { "${it.fullName} · ${glyph.pitch?.name}" } ?: stringResource(Res.string.score_rest),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        SquareIconButton(SheetEditIcons.ChevronUp, stringResource(Res.string.sheetedit_up), { onShift(1) },
                            enabled = state.canShiftCommonStaffPitch(1), size = 36.dp)
                        SquareIconButton(EolssuIcons.ChevronDown, stringResource(Res.string.sheetedit_down), { onShift(-1) },
                            enabled = state.canShiftCommonStaffPitch(-1), size = 36.dp)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawStaffLines(bottom: Float, step: Float, color: Color) {
    repeat(5) { line ->
        val y = bottom - line * 2 * step
        drawLine(color, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
    }
}

/** 번들 글꼴에 없는 오선 기호는 벡터로 그린다. */
private fun DrawScope.drawStaffGlyph(glyph: CommonStaffGlyph, bottom: Float, step: Float, scale: Float, color: Color) {
    fun px(value: Float) = (value.dp * scale).toPx()
    val x = size.width * 0.5f
    val stroke = px(1.6f)
    val pitch = glyph.pitch
    val index = pitch?.step ?: 34
    val y = bottom - (index - 30) * step
    val denominator = glyph.denominator
    if (pitch != null) {
        val ledgerLines = when {
            index < 30 -> 28 downTo index step 2
            index > 38 -> 40..index step 2
            else -> IntRange.EMPTY
        }
        for (line in ledgerLines) {
            val ledgerY = bottom - (line - 30) * step
            drawLine(color, Offset(x - px(15f), ledgerY), Offset(x + px(15f), ledgerY), stroke)
        }
        drawOval(color, Offset(x - px(9f), y - px(5.5f)), Size(px(18f), px(11f)), style = if (denominator <= 2) Stroke(stroke) else Fill)
        if (denominator >= 2) {
            val down = index >= 34
            val stem = x + if (down) -px(8f) else px(8f)
            val end = y + if (down) px(34f) else -px(34f)
            drawLine(color, Offset(stem, y), Offset(stem, end), stroke)
            for (flag in 0 until flagCount(denominator)) {
                val top = end + if (down) -px(flag * 7f) else px(flag * 7f)
                val direction = if (down) -1 else 1
                drawPath(Path().apply {
                    moveTo(stem, top)
                    cubicTo(stem + px(17f), top + direction * px(6f), stem + px(13f), top + direction * px(17f), stem + px(9f), top + direction * px(20f))
                }, color, style = Stroke(px(2.3f)))
            }
        }
        val left = x - px(27f)
        if (pitch.flat) {
            drawLine(color, Offset(left, y - px(21f)), Offset(left, y + px(7f)), stroke)
            drawPath(Path().apply {
                moveTo(left, y - px(2f))
                cubicTo(left + px(13f), y - px(10f), left + px(13f), y + px(3f), left, y + px(7f))
            }, color, style = Stroke(stroke))
        } else {
            val right = left + px(7f)
            drawLine(color, Offset(left, y - px(13f)), Offset(left, y + px(6f)), stroke)
            drawLine(color, Offset(right, y - px(6f)), Offset(right, y + px(13f)), stroke)
            drawLine(color, Offset(left, y - px(2f)), Offset(right, y - px(6f)), px(2.4f))
            drawLine(color, Offset(left, y + px(6f)), Offset(right, y + px(2f)), px(2.4f))
        }
    } else if (denominator <= 2) {
        val restY = if (denominator == 1) bottom - 6 * step else bottom - 4 * step - px(5f)
        drawRect(color, Offset(x - px(9f), restY), Size(px(18f), px(5f)))
    } else if (denominator == 4) {
        drawPath(Path().apply {
            moveTo(x - px(4f), y - px(16f))
            lineTo(x + px(5f), y - px(7f))
            lineTo(x - px(3f), y + px(1f))
            lineTo(x + px(6f), y + px(9f))
            cubicTo(x - px(9f), y + px(3f), x - px(8f), y + px(14f), x - px(1f), y + px(17f))
        }, color, style = Stroke(px(3f)))
    } else {
        val flags = flagCount(denominator)
        for (flag in 0 until flags) {
            val restY = y - px(10f) + px(flag * 8f)
            drawCircle(color, px(3.5f), Offset(x - px(4f), restY))
            drawLine(color, Offset(x - px(4f), restY + px(3f)), Offset(x + px(7f), restY - px(3f)), stroke)
        }
        drawLine(color, Offset(x + px(7f), y - px(13f)), Offset(x - px(1f), y + px(15f + (flags - 1) * 8)), stroke)
    }
    repeat(glyph.dots) { dot ->
        drawCircle(color, px(2f), Offset(x + px(16f + dot * 6), y - if (index % 2 == 0) step else 0f))
    }
    val tieY = y + px(18f)
    if (glyph.tieFromPrevious) drawPath(Path().apply {
        moveTo(0f, tieY + px(4f))
        quadraticTo(x * 0.45f, tieY + px(4f), x - px(7f), tieY)
    }, color, style = Stroke(stroke))
    if (glyph.tieToNext) drawPath(Path().apply {
        moveTo(x + px(7f), tieY)
        quadraticTo(size.width * 0.8f, tieY + px(4f), size.width, tieY + px(4f))
    }, color, style = Stroke(stroke))
}

private fun flagCount(denominator: Int): Int = when (denominator) { 8 -> 1; 16 -> 2; 32 -> 3; 64 -> 4; else -> 0 }

private fun DrawScope.drawTrebleClef(gLine: Float, scale: Float, color: Color) {
    fun x(value: Float) = (value.dp * scale).toPx()
    fun y(value: Float) = gLine + x(value)
    drawPath(Path().apply {
        moveTo(x(35f), y(38f))
        cubicTo(x(21f), y(46f), x(18f), y(29f), x(29f), y(30f))
        cubicTo(x(40f), y(32f), x(30f), y(-40f), x(29f), y(-51f))
        cubicTo(x(20f), y(-43f), x(24f), y(-29f), x(35f), y(-25f))
        cubicTo(x(52f), y(-11f), x(40f), y(17f), x(23f), y(11f))
        cubicTo(x(5f), y(5f), x(18f), y(-17f), x(34f), y(-10f))
        cubicTo(x(44f), y(-4f), x(38f), y(9f), x(28f), y(4f))
    }, color, style = Stroke(x(2f)))
}
