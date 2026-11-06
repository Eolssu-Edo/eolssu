package com.ssafy.eolssu.feature.practice.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.ScoreDisplayUnit
import com.ssafy.eolssu.core.model.ScoreEvent
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.displayUnitAt
import com.ssafy.eolssu.core.model.key
import com.ssafy.eolssu.core.model.scoreLyricSlots
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.HoleState
import com.ssafy.eolssu.core.model.fingering.label
import com.ssafy.eolssu.core.model.fingering.baseYulOf
import com.ssafy.eolssu.core.model.fingering.octaveOf
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.score.FingeringFigure
import com.ssafy.eolssu.core.ui.score.ScoreNoteGlyph
import com.ssafy.eolssu.core.ui.score.rememberScoreCursor
import com.ssafy.eolssu.core.ui.component.EolssuIconButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.practice.PracticeMode
import com.ssafy.eolssu.feature.practice.PracticeUiState
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_finger_upcoming
import eolssu.shared.generated.resources.practice_finger_end
import eolssu.shared.generated.resources.practice_finger_gak
import eolssu.shared.generated.resources.practice_finger_head_fingering
import eolssu.shared.generated.resources.practice_finger_head_lyric
import eolssu.shared.generated.resources.practice_finger_head_yul
import eolssu.shared.generated.resources.practice_finger_missing
import eolssu.shared.generated.resources.practice_finger_rest
import eolssu.shared.generated.resources.score_rest_hangul
import eolssu.shared.generated.resources.practice_finger_previous_page
import eolssu.shared.generated.resources.practice_finger_next_page
import org.jetbrains.compose.resources.stringResource

/** 정간 하나의 위치(각·정간)와 운지 줄 내용. [units]는 이 정간에 적힌 표시 자리(율명·이음·쉼)다. */
data class FingerCell(
    val index: Int,
    val column: Int,
    val row: Int,
    val units: List<ScoreDisplayUnit>,
    val entries: List<FingerEntry>,
    val lyric: String? = null,
    val lyricSlots: List<String> = scoreLyricSlots(lyric, units.size),
) {
    val rest: Boolean get() = units.isNotEmpty() && units.all { it.note == ScoreNote.Rest }
    val parts: Int get() = units.size.coerceAtLeast(1)
    val firstEvent: ScoreEvent? get() = units.firstOrNull()?.event
}

/** 원본 음과 운지를 함께 보존한다. 자료 없는 음과 실제 쉼을 구분한다. */
data class FingerEntry(val note: ScoreNote?, val holes: List<HoleState>?)

/** 내장표의 이체자를 포함해 정확히 같은 율명·옥타브만 연결한다. */
internal fun GuideInstrument.fingeringFor(note: ScoreNote): List<HoleState>? {
    val base = when (note) {
        is ScoreNote.SourcePitch -> note.baseYul.symbol
        is ScoreNote.Pitch -> baseYulOf(note.symbol)
        else -> return null
    }
    val octave = if (note is ScoreNote.SourcePitch) note.octave else octaveOf(note.symbol)
    return blows.firstNotNullOfOrNull { blow ->
        notesByPitch(blow).firstOrNull { baseYulOf(it.hanja) == base && octaveOf(it.hanja) == octave }?.holes
    }
}

/** 곡의 원본 표시 자리마다 운지 칸을 보존한다. 장음·빈 자리·쉼에는 운지를 연결하지 않는다. */
fun fingerCells(song: Song, instrument: GuideInstrument): List<FingerCell> {
    val unitsByCell = song.displayUnits.groupBy { it.column to it.row }
    val cells = mutableListOf<FingerCell>()
    song.score.columns.forEachIndexed { column, rows ->
        rows.indices.forEach { row ->
            val units = unitsByCell[column to row].orEmpty().sortedWith(compareBy({ it.unitRow }, { it.unitCol }))
            val entries = units.map { unit ->
                when (val note = unit.note) {
                    ScoreNote.Rest, ScoreNote.Hold, ScoreNote.Blank -> FingerEntry(null, null)
                    else -> FingerEntry(note, instrument.fingeringFor(note))
                }
            }
            cells += FingerCell(cells.size, column, row, units, entries, rows[row].lyric)
        }
    }
    return cells
}

/** 한 각을 화면의 남은 높이에 맞추고 재생 위치가 다음 각에 들어가면 함께 넘긴다. */
@Composable
fun PracticeFingeringPage(state: PracticeUiState, onSelectEvent: (ScoreEvent) -> Unit, modifier: Modifier = Modifier) {
    val song = state.song ?: return
    val colors = EolssuTheme.colors
    val instrument = state.instrument
    val cells = remember(song, instrument) { fingerCells(song, instrument) }
    if (cells.isEmpty()) return
    val angles = remember(cells) { cells.groupBy { it.column }.values.toList() }
    val playback = state.scorePlayback()
    val live = playback != null
    val cursor = rememberScoreCursor(playback)
    val currentUnit = if (live) cursor.index?.let { song.displayUnitAt(it, cursor.fraction) }
        else song.displayUnits.firstOrNull { it.event.index == state.selected }
    var browsed by remember(song, state.selected, live) { mutableIntStateOf(currentUnit?.column ?: 0) }
    val angleIndex = if (live && state.follow) currentUnit?.column ?: browsed else browsed
    val angle = angles[angleIndex.coerceIn(0, angles.lastIndex)]
    val previewCount = remember(angle) { fingerPreviewCount(angle) }
    val preview = remember(song, cells, angleIndex, previewCount) {
        val lastEvent = angle.flatMap { it.units }.maxOfOrNull { it.event.index }
        if (lastEvent == null) emptyList() else nextFingerCells(song, cells, lastEvent, previewCount)
    }
    val instrumentName = stringResource(instrument.label)
    val fontScale = LocalDensity.current.fontScale
    val shape = RoundedCornerShape(3.dp)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val bounded = constraints.hasBoundedHeight
        Column(if (bounded) Modifier.fillMaxHeight() else Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.fillMaxWidth().then(if (bounded) Modifier.weight(1f) else Modifier)
                .clip(shape).background(colors.surface).border(1.dp, colors.lineStrong, shape)) {
                FingerHead(instrumentName)
                BoxWithConstraints(Modifier.fillMaxWidth().then(if (bounded) Modifier.weight(1f) else Modifier.height(
                    (FingerImageHeight * angle.sumOf { cell -> cell.units.sumOf { fingerSlotWeight(it).toDouble() } }.toFloat() + 4.dp * angle.size) * fontScale,
                ))) {
                    val weight = angle.sumOf { cell -> cell.units.sumOf { fingerSlotWeight(it).toDouble() } }.toFloat().coerceAtLeast(1f)
                    val imageHeight = ((maxHeight - 4.dp * angle.size) / weight).coerceAtLeast(28.dp * fontScale)
                    val scroll = remember(angleIndex) { ScrollState(0) }
                    val density = LocalDensity.current
                    androidx.compose.runtime.LaunchedEffect(currentUnit?.row, angleIndex, state.follow, scroll.maxValue) {
                        if (live && state.follow && scroll.maxValue > 0) {
                            val before = angle.takeWhile { it.row != currentUnit?.row }
                            val top = before.fold(0.dp) { acc, cell ->
                                acc + imageHeight * cell.units.sumOf { fingerSlotWeight(it).toDouble() }.toFloat() + 4.dp
                            }
                            scroll.animateScrollTo(with(density) { top.roundToPx() }.coerceIn(0, scroll.maxValue))
                        }
                    }
                    Column(Modifier.fillMaxWidth().verticalScroll(scroll)) {
                        angle.forEach { cell ->
                            FingerRow(
                                cell = cell, instrument = instrument,
                                fill = if (!live) null else { {
                                    val position = cursor.index?.let { song.timeline.getOrNull(it) }?.let { it.unitStart + it.unitDuration * cursor.fraction } ?: -1.0
                                    val start = cell.units.firstOrNull()?.unitStart ?: cell.index.toDouble()
                                    val end = cell.units.lastOrNull()?.let { it.unitStart + it.unitDuration } ?: start + 1
                                    ((position - start) / (end - start)).toFloat().coerceIn(0f, 1f)
                                } },
                                isSelected = !live && cell.units.any { it.event.index == state.selected },
                                playingKey = if (live) currentUnit?.key else null,
                                showHangul = state.settings.showHangul,
                                gakStart = cell.row == 0,
                                daegangStarts = cell.units.indices.filter { slot -> song.jangdan?.let { jangdan -> cell.row in jangdan.daegang.runningFold(0) { acc, n -> acc + n }.dropLast(1) } == true && slot == 0 },
                                imageHeight = imageHeight,
                                onSelectEvent = onSelectEvent.takeIf { state.mode == PracticeMode.Ready },
                            )
                        }
                    }
                }
            }
            Text(stringResource(Res.string.practice_finger_upcoming),
                style = MaterialTheme.typography.labelSmall, color = colors.accentText)
            // 마지막 각에서 미리보기가 줄어도 현재 악보의 공간은 늘어나지 않는다.
            Column(Modifier.fillMaxWidth().height(29.dp + (28.dp * fontScale + 4.dp) * previewCount)
                .clip(shape).background(colors.surface).border(1.dp, colors.lineStrong, shape)) {
                FingerHead(instrumentName)
                if (preview.isEmpty()) {
                    Text(stringResource(Res.string.practice_finger_end), modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                } else preview.forEach { cell ->
                    FingerRow(
                        cell = cell, instrument = instrument, fill = null, isSelected = false,
                        playingKey = null, showHangul = state.settings.showHangul,
                        gakStart = false, daegangStarts = emptyList(),
                        imageHeight = 28.dp * fontScale, onSelectEvent = null,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                EolssuIconButton(EolssuIcons.Back, stringResource(Res.string.practice_finger_previous_page), { browsed = (browsed - 1).coerceAtLeast(0) }, bordered = false, enabled = !live && browsed > 0)
                Text(stringResource(Res.string.practice_finger_gak, angleIndex + 1), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                EolssuIconButton(EolssuIcons.Next, stringResource(Res.string.practice_finger_next_page), { browsed = (browsed + 1).coerceAtMost(angles.lastIndex) }, bordered = false, enabled = !live && browsed < angles.lastIndex)
            }
        }
    }
}

private val FingerImageHeight = 40.dp
private val FingerNoteWidth = 76.dp
private val FingerLyricWidth = 30.dp

private fun fingerSlotWeight(unit: ScoreDisplayUnit): Float =
    if (unit.note == ScoreNote.Hold || unit.note == ScoreNote.Blank) 0.5f else 1f

/** 표 머리(29dp): ‘{악기} 운지 | 율명 | 가사’, 10sp 500 회색, 칸 사이 선. */
@Composable
private fun FingerHead(instrumentName: String) {
    val colors = EolssuTheme.colors
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium)
    Row(Modifier.fillMaxWidth().height(29.dp)) {
        Text(stringResource(Res.string.practice_finger_head_fingering, instrumentName), Modifier.weight(1f).fillMaxHeight().padding(horizontal = 2.dp, vertical = 5.dp), style = style, color = colors.textMuted, textAlign = TextAlign.Center)
        HeadCell(stringResource(Res.string.practice_finger_head_yul), FingerNoteWidth, style)
        HeadCell(stringResource(Res.string.practice_finger_head_lyric), FingerLyricWidth, style)
    }
}

@Composable
private fun HeadCell(text: String, width: Dp, style: androidx.compose.ui.text.TextStyle) {
    val colors = EolssuTheme.colors
    Box(
        Modifier.width(width).fillMaxHeight().drawBehind { drawRect(colors.line, size = Size(1.dp.toPx(), size.height)) }.padding(horizontal = 2.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = style, color = colors.textMuted, textAlign = TextAlign.Center) }
}

/**
 * 운지 줄 하나: 페이지에서 배정한 자리 높이 × 표시 자리 수 + 여백. 왼쪽 운지·가운데 율명·오른쪽 가사는 같은 자리를 쓴다.
 * 각 첫 줄은 2dp 먹색 윗선과 ‘N각’, 대강 첫 줄은 2dp 회색 윗선, 그 밖은 점선. 고른 줄은 2dp 안쪽 테두리.
 * 재생·연주 중에는 악보처럼 노래방 방식으로 [fill]만큼 위에서부터 회색으로 채우고, 지금 연주하는 율명([playingKey])을 붉게 한다.
 */
@Composable
private fun FingerRow(
    cell: FingerCell,
    instrument: GuideInstrument,
    fill: (() -> Float)?,
    isSelected: Boolean,
    playingKey: ScoreUnitKey?,
    showHangul: Boolean,
    gakStart: Boolean,
    daegangStarts: List<Int>,
    imageHeight: Dp = FingerImageHeight,
    onSelectEvent: ((ScoreEvent) -> Unit)?,
) {
    val colors = EolssuTheme.colors
    var rowModifier = Modifier
        .fillMaxWidth()
        .height(imageHeight * cell.units.sumOf { fingerSlotWeight(it).toDouble() }.toFloat() + 4.dp)
        .drawBehind {
            if (fill != null) drawRect(colors.doneSoft, size = Size(size.width, size.height * fill()))
            when {
                gakStart -> drawRect(colors.text, size = Size(size.width, 2.dp.toPx()))
                0 in daegangStarts -> drawRect(colors.lineStrong, size = Size(size.width, 2.dp.toPx()))
                else -> drawLine(colors.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())))
            }
            daegangStarts.filter { it > 0 }.forEach { slot ->
                val y = 2.dp.toPx() + imageHeight.toPx() * cell.units.take(slot).sumOf { fingerSlotWeight(it).toDouble() }.toFloat()
                drawLine(colors.lineStrong, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
            }
        }
    if (isSelected) rowModifier = rowModifier.border(2.dp, colors.accent)
    Row(rowModifier) {
        Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 6.dp, vertical = 2.dp)) {
            if (gakStart) {
                Text(
                    stringResource(Res.string.practice_finger_gak, cell.column + 1),
                    modifier = Modifier.align(Alignment.TopStart),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 10.sp),
                    color = colors.textMuted,
                )
            }
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                cell.entries.forEachIndexed { index, entry ->
                    Box(Modifier.fillMaxWidth().height(imageHeight * fingerSlotWeight(cell.units[index])), contentAlignment = Alignment.Center) {
                        when {
                            entry.note != null -> FingerDiagram(instrument, entry, imageHeight.coerceAtMost(FingerImageHeight * LocalDensity.current.fontScale))
                            cell.units[index].note == ScoreNote.Rest -> Text(stringResource(Res.string.practice_finger_rest), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                        }
                    }
                }
            }
        }
        Column(
            Modifier
                .width(FingerNoteWidth)
                .fillMaxHeight()
                .drawBehind { drawRect(colors.line, size = Size(1.dp.toPx(), size.height)) }
                .padding(horizontal = 3.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            cell.units.forEach { unit ->
                NoteLine(unit, showHangul, big = cell.parts == 1, playing = playingKey != null && unit.key == playingKey, height = imageHeight * fingerSlotWeight(unit),
                    onClick = onSelectEvent?.let { select -> { select(unit.event) } })
            }
        }
        Column(
            Modifier.width(FingerLyricWidth).fillMaxHeight()
                .drawBehind { drawRect(colors.line, size = Size(1.dp.toPx(), size.height)) },
            verticalArrangement = Arrangement.Center,
        ) {
            cell.lyricSlots.forEachIndexed { index, text ->
                Box(Modifier.fillMaxWidth().height(imageHeight * fingerSlotWeight(cell.units[index])), contentAlignment = Alignment.Center) {
                    if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.labelSmall, color = colors.text)
                }
            }
        }
    }
}

/** 가로 악기 그림 한 장. 자료가 없으면 그림 대신 ‘운지 자료 없음’. */
@Composable
private fun FingerDiagram(instrument: GuideInstrument, entry: FingerEntry, height: Dp) {
    val colors = EolssuTheme.colors
    val ratio = instrument.imageWidth.toFloat() / (instrument.imageHeight - 300)
    Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        if (entry.holes != null) FingeringFigure(
            instrument,
            entry.holes,
            Modifier.widthIn(max = height * ratio).height(height),
            description = when (val note = entry.note) { is ScoreNote.Pitch -> note.yul.fullName; is ScoreNote.SourcePitch -> note.fullName; else -> null },
            cropVertical = true,
        )
        if (entry.holes == null) {
            Text(
                stringResource(Res.string.practice_finger_missing),
                modifier = Modifier.align(Alignment.BottomEnd).background(colors.surface),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 10.sp),
                color = colors.textMuted,
            )
        }
    }
}

/** 율명 한 줄(`.note-line`): 글자와 오른쪽 작은 한글 읽는 법. 지금 연주하는 율명([playing])은 붉게 쓴다. */
@Composable
private fun NoteLine(unit: ScoreDisplayUnit, showHangul: Boolean, big: Boolean, playing: Boolean = false, height: Dp = FingerImageHeight, onClick: (() -> Unit)? = null) {
    val colors = EolssuTheme.colors
    val note = unit.note
    val isPitch = note is ScoreNote.Pitch || note is ScoreNote.SourcePitch
    val hangul = when {
        !showHangul -> null
        note is ScoreNote.Pitch -> note.yul.hangul
        note is ScoreNote.SourcePitch -> note.hangul
        note == ScoreNote.Rest -> stringResource(Res.string.score_rest_hangul)
        else -> null
    }
    Row(Modifier.fillMaxWidth().height(height).then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
        if (isPitch) {
            ScoreNoteGlyph(note, size = if (big) 25.sp else 19.sp, color = if (playing) colors.rec else colors.text)
        } else {
            val sustained = note == ScoreNote.Hold || note == ScoreNote.Blank
            Text(note.symbol, style = MaterialTheme.typography.bodyLarge.copy(fontSize = if (sustained) 12.sp else if (big) 21.sp else 18.sp, lineHeight = if (sustained) 14.sp else 24.sp, fontWeight = FontWeight.Normal), color = if (playing) colors.rec else colors.textSub)
        }
        if (hangul != null) {
            Text(hangul, style = MaterialTheme.typography.labelSmall.copy(fontSize = if (big) 10.sp else 8.sp, lineHeight = 11.sp), color = if (playing) colors.rec else colors.textMuted)
        }
    }
}
