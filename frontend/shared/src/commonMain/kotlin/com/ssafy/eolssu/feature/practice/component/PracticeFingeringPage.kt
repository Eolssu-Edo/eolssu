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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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
import eolssu.shared.generated.resources.practice_finger_gak
import eolssu.shared.generated.resources.practice_finger_head_fingering
import eolssu.shared.generated.resources.practice_finger_head_lyric
import eolssu.shared.generated.resources.practice_finger_head_yul
import eolssu.shared.generated.resources.practice_finger_heading
import eolssu.shared.generated.resources.practice_finger_missing
import eolssu.shared.generated.resources.practice_finger_rest
import eolssu.shared.generated.resources.score_rest_hangul
import eolssu.shared.generated.resources.practice_finger_page
import eolssu.shared.generated.resources.practice_finger_previous_page
import eolssu.shared.generated.resources.practice_finger_next_page
import eolssu.shared.generated.resources.practice_finger_upcoming
import eolssu.shared.generated.resources.practice_finger_end
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

/**
 * ‘운지법과 함께 보기’: 정간보 대신 운지 악보. ‘지금 읽는 부분’과 현재/전체 페이지 머리,
 * 표(‘{악기} 운지 | 율명 | 가사’), 줄마다 가로 악기 그림 위 구멍 표시와 율명·가사.
 * 실제 발음 6개 이상을 담은 고정 페이지다. 페이지 내부는 움직이지 않고 경계에서만 교체한다.
 */
@Composable
fun PracticeFingeringPage(state: PracticeUiState, onSelectEvent: (ScoreEvent) -> Unit, modifier: Modifier = Modifier) {
    val song = state.song ?: return
    val colors = EolssuTheme.colors
    val instrument = state.instrument
    val cells = remember(song, instrument) { fingerCells(song, instrument) }
    if (cells.isEmpty()) return
    val cellIndex = remember(cells) { cells.associate { (it.column to it.row) to it.index } }
    val pages = remember(song, cells) { fingerPages(song, cells) }
    val viewportHeight = remember(pages) {
        pages.maxOf { page -> FingerImageHeight * page.cells.sumOf { it.parts } + 8.dp * page.cells.size }.coerceIn(160.dp, 320.dp)
    }

    // 재생·연주 중에는 악보와 같은 진행 위치를 매 프레임 읽어, 음 순서와 비율이 함께 바뀌고 모범 연주도 끊기지 않는다.
    val playback = state.scorePlayback()
    val live = playback != null
    val cursor = rememberScoreCursor(playback)
    // 지금 진행 위치: 줄 번호와 그 줄에서 지난 몫. 그리기 단계와 프레임마다 읽는다.
    val livePosition: () -> Double? = {
        cursor.index?.let { index ->
            val event = song.timeline.getOrNull(index)
            event?.let { it.unitStart + it.unitDuration * cursor.fraction }
        }
    }
    val liveUnit by remember(song) { derivedStateOf { cursor.index?.let { song.displayUnitAt(it, cursor.fraction) } } }
    val currentUnit = if (live) liveUnit else song.displayUnits.firstOrNull { it.event.index == state.selected }
    val currentCell = currentUnit?.let { cellIndex[it.column to it.row] } ?: 0
    val playingKey = if (live) currentUnit?.key else null
    val instrumentName = stringResource(instrument.label)
    val fontScale = LocalDensity.current.fontScale

    val livePage by remember(song, pages) { derivedStateOf { pages.pageAt(livePosition() ?: 0.0) } }
    var browsedPage by remember(song, state.selected, live) { mutableIntStateOf(pages.pageAt(currentUnit?.unitStart ?: 0.0)) }
    val pageIndex = if (live) livePage else browsedPage
    val page = pages[pageIndex]
    val previewIndex = page.cells.last().units.last().event.index
    val preview = remember(song, previewIndex) { nextFingerUnits(song, previewIndex) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.practice_finger_heading), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = colors.accentText)
            Row(verticalAlignment = Alignment.CenterVertically) {
                EolssuIconButton(EolssuIcons.Back, stringResource(Res.string.practice_finger_previous_page), { browsedPage = (browsedPage - 1).coerceAtLeast(0) }, bordered = false, enabled = !live && pageIndex > 0)
                Text(stringResource(Res.string.practice_finger_page, pageIndex + 1, pages.size), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                EolssuIconButton(EolssuIcons.Next, stringResource(Res.string.practice_finger_next_page), { browsedPage = (browsedPage + 1).coerceAtMost(pages.lastIndex) }, bordered = false, enabled = !live && pageIndex < pages.lastIndex)
            }
        }
        val shape = RoundedCornerShape(3.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(colors.surface).border(1.dp, colors.lineStrong, shape)) {
            FingerHead(instrumentName)
            BoxWithConstraints(Modifier.fillMaxWidth().height(viewportHeight)) {
                val imageHeight = ((maxHeight - 4.dp * page.cells.size) / page.cells.sumOf { cell -> cell.units.sumOf { fingerSlotWeight(it).toDouble() } }.toFloat()).coerceAtLeast(28.dp * fontScale)
                Column(Modifier.fillMaxWidth().verticalScroll(remember(pageIndex) { ScrollState(0) })) {
                    page.cells.forEach { cell ->
                        FingerRow(
                            cell = cell,
                            instrument = instrument,
                            fill = if (!live) null else {
                                {
                                    val position = livePosition() ?: -1.0
                                    val start = cell.units.firstOrNull()?.unitStart ?: cell.index.toDouble()
                                    val end = cell.units.lastOrNull()?.let { it.unitStart + it.unitDuration } ?: start + 1
                                    ((position - start) / (end - start)).toFloat().coerceIn(0f, 1f)
                                }
                            },
                            isSelected = !live && cell.index == currentCell,
                            playingKey = playingKey,
                            showHangul = state.settings.showHangul,
                            gakStart = cell.row == 0 && cell.units.firstOrNull()?.unitStart == cell.index.toDouble(),
                            daegangStarts = cell.units.indices.filter { slot ->
                                page.groups.any { kotlin.math.abs(it.start - cell.units[slot].unitStart) < 0.000001 }
                            },
                            imageHeight = imageHeight,
                            onSelectEvent = onSelectEvent.takeIf { state.mode == PracticeMode.Ready },
                        )
                    }
                }
            }
        }
        Text(stringResource(Res.string.practice_finger_upcoming), style = MaterialTheme.typography.labelSmall, color = colors.accentText)
        Row(Modifier.fillMaxWidth().heightIn(min = FingerImageHeight * fontScale + FingerImageHeight + 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (preview.isEmpty()) Text(stringResource(Res.string.practice_finger_end), color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
            preview.forEach { unit ->
                Column(Modifier.weight(1f).border(1.dp, colors.line, shape).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    NoteLine(unit, showHangul = true, big = false, height = FingerImageHeight * fontScale)
                    FingerDiagram(instrument, FingerEntry(unit.note, instrument.fingeringFor(unit.note)), FingerImageHeight)
                }
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
