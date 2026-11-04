package com.ssafy.eolssu.feature.practice.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
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
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.model.displayUnitAt
import com.ssafy.eolssu.core.model.key
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.HoleState
import com.ssafy.eolssu.core.model.fingering.label
import com.ssafy.eolssu.core.model.fingeringYul
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.score.FingeringFigure
import com.ssafy.eolssu.core.ui.score.ScoreNoteGlyph
import com.ssafy.eolssu.core.ui.score.rememberScoreCursor
import com.ssafy.eolssu.feature.practice.PracticeMode
import com.ssafy.eolssu.feature.practice.PracticeUiState
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_finger_gak
import eolssu.shared.generated.resources.practice_finger_head_fingering
import eolssu.shared.generated.resources.practice_finger_head_lyric
import eolssu.shared.generated.resources.practice_finger_head_yul
import eolssu.shared.generated.resources.practice_finger_heading
import eolssu.shared.generated.resources.practice_finger_missing
import eolssu.shared.generated.resources.practice_finger_range
import eolssu.shared.generated.resources.practice_finger_rest
import eolssu.shared.generated.resources.score_rest_hangul
import org.jetbrains.compose.resources.stringResource

/** 정간 하나의 위치(각·정간)와 운지 줄 내용. [units]는 이 정간에 적힌 표시 자리(율명·이음·쉼)다. */
data class FingerCell(
    val index: Int,
    val column: Int,
    val row: Int,
    val units: List<ScoreDisplayUnit>,
    val entries: List<FingerEntry>,
    val lyric: String? = null,
) {
    val rest: Boolean get() = entries.isNotEmpty() && entries.all { it.yul == null }
    val parts: Int get() = units.size.coerceAtLeast(1)
    val firstEvent: ScoreEvent? get() = units.firstOrNull()?.event
}

/** 정간 안 표시 자리 하나의 운지. [yul]이 null이면 쉼(또는 앞 음이 없는 이음)이다. 이음은 앞 음의 율명을 따른다. */
data class FingerEntry(val yul: Yul?, val holes: List<HoleState>?)

/** 곡의 정간을 연주 순서대로 펼친 운지 줄. 이음(ㅡ)·빈 자리는 앞 음의 운지를 이어 받고, 악기를 바꾸면 다시 만든다. */
fun fingerCells(song: Song, instrument: GuideInstrument): List<FingerCell> {
    val unitsByCell = song.displayUnits.groupBy { it.column to it.row }
    var previous: Yul? = null
    val cells = mutableListOf<FingerCell>()
    song.score.columns.forEachIndexed { column, rows ->
        rows.indices.forEach { row ->
            val units = unitsByCell[column to row].orEmpty().sortedWith(compareBy({ it.unitRow }, { it.unitCol }))
            val entries = units.map { unit ->
                when (val note = unit.note) {
                    ScoreNote.Rest -> { previous = null; FingerEntry(null, null) }
                    ScoreNote.Hold, ScoreNote.Blank -> FingerEntry(previous, previous?.let { instrument.noteOf(it.symbol)?.holes })
                    else -> {
                        val yul = note.fingeringYul
                        previous = yul
                        FingerEntry(yul, yul?.let { instrument.noteOf(it.symbol)?.holes })
                    }
                }
            }
            cells += FingerCell(cells.size, column, row, units, entries, rows[row].lyric)
        }
    }
    return cells
}

/**
 * ‘운지법과 함께 보기’(보드 Practice-Fingering): 정간보 대신 운지 악보. ‘지금 읽는 부분 · N각 · 1–k정간’ 머리,
 * 표(‘{악기} 운지 | 율명 | 가사’), 줄마다 가로 악기 그림 위 구멍 표시와 율명·가사.
 * 곡의 모든 정간을 연주 순서대로 한 줄로 이어 [FingerViewportHeight]만큼(두 각 안팎) 보여 준다. 재생·연주 중에는 진행 위치가
 * 위에서 [FingerFollowAnchor] 지점에 머물도록 목록이 매 프레임 위로 흘러가고, 준비 상태에서는 손으로 넘겨 보며 음을 고른다.
 */
@Composable
fun PracticeFingeringPage(state: PracticeUiState, onSelectEvent: (ScoreEvent) -> Unit, modifier: Modifier = Modifier) {
    val song = state.song ?: return
    val colors = EolssuTheme.colors
    val instrument = state.instrument
    val cells = remember(song, instrument) { fingerCells(song, instrument) }
    if (cells.isEmpty()) return
    val cellIndex = remember(cells) { cells.associate { (it.column to it.row) to it.index } }
    val cellStarts = remember(cells) { cells.map { cell -> cell.units.minOfOrNull { it.unitStart } ?: 0.0 } }
    val density = LocalDensity.current
    val heights = remember(cells, density) { cells.map { with(density) { (FingerImageHeight * it.parts + 8.dp).toPx() } } }
    val tops = remember(heights) { heights.runningFold(0f) { top, height -> top + height } }

    // 재생·연주 중에는 악보와 같은 진행 위치를 매 프레임 읽어, 음 순서와 비율이 함께 바뀌고 모범 연주도 끊기지 않는다.
    val playback = state.scorePlayback()
    val live = playback != null
    val cursor = rememberScoreCursor(playback)
    // 지금 진행 위치: 줄 번호와 그 줄에서 지난 몫. 그리기 단계와 프레임마다 읽는다.
    val livePosition: () -> Pair<Int, Float>? = {
        cursor.index?.let { index ->
            val event = song.timeline.getOrNull(index)
            val unit = song.displayUnitAt(index, cursor.fraction)
            val cell = unit?.let { cellIndex[it.column to it.row] }
            if (event == null || cell == null) null else
                cell to (event.unitStart + event.unitDuration * cursor.fraction - cellStarts[cell]).toFloat().coerceIn(0f, 1f)
        }
    }
    val liveUnit by remember(song) { derivedStateOf { cursor.index?.let { song.displayUnitAt(it, cursor.fraction) } } }
    val currentUnit = if (live) liveUnit else song.displayUnits.firstOrNull { it.event.index == state.selected }
    val currentCell = currentUnit?.let { cellIndex[it.column to it.row] } ?: 0
    val playingKey = if (live) currentUnit?.key else null
    val instrumentName = stringResource(instrument.label)

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentCell)
    // 준비 상태에서 고른 음이 화면 밖이면 그 줄로 옮긴다.
    LaunchedEffect(live, currentCell) {
        if (!live && listState.layoutInfo.visibleItemsInfo.none { it.index == currentCell }) listState.animateScrollToItem(currentCell)
    }
    val latestPosition by rememberUpdatedState(livePosition)
    LaunchedEffect(live, tops) {
        if (!live) return@LaunchedEffect
        while (true) {
            withFrameNanos {
                val viewport = listState.layoutInfo.viewportSize.height
                val (cell, done) = latestPosition() ?: return@withFrameNanos
                if (viewport == 0) return@withFrameNanos
                val target = (tops[cell] + heights[cell] * done - viewport * FingerFollowAnchor).coerceIn(0f, (tops.last() - viewport).coerceAtLeast(0f))
                val shown = tops[listState.firstVisibleItemIndex] + listState.firstVisibleItemScrollOffset
                listState.dispatchRawDelta(target - shown)
            }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.practice_finger_heading), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = colors.accentText)
            val column = cells[currentCell].column
            Text(
                stringResource(Res.string.practice_finger_range, column + 1, 1, song.score.columns[column].size),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                color = colors.textMuted,
            )
        }
        val shape = RoundedCornerShape(3.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(colors.surface).border(1.dp, colors.lineStrong, shape)) {
            FingerHead(instrumentName)
            LazyColumn(Modifier.fillMaxWidth().height(FingerViewportHeight), state = listState, userScrollEnabled = !live) {
                items(cells.size, key = { it }) { i ->
                    val cell = cells[i]
                    FingerRow(
                        cell = cell,
                        instrument = instrument,
                        // 노래방처럼 지난 줄은 다 채우고 현재 줄은 지난 만큼 위에서부터 채운다. 그릴 때 진행 위치를 읽는다.
                        fill = if (!live) null else {
                            {
                                val (now, done) = livePosition() ?: (-1 to 0f)
                                when {
                                    i < now -> 1f
                                    i == now -> done
                                    else -> 0f
                                }
                            }
                        },
                        isSelected = !live && i == currentCell,
                        playingKey = playingKey,
                        showHangul = state.settings.showHangul,
                        gakStart = cell.row == 0,
                        daegang = cell.row > 0 && cell.row in song.jangdan?.daegangStarts.orEmpty() && song.score.columns[cell.column].size == song.jangdan?.gak,
                        onClick = if (state.mode == PracticeMode.Ready) cell.firstEvent?.let { event -> { onSelectEvent(event) } } else null,
                    )
                }
            }
        }
    }
}

/** 운지 목록이 보이는 높이. 6정간 각이면 두 각 가까이 들어간다. */
private val FingerViewportHeight = 440.dp

/** 재생·연주 중 진행 위치를 둘 자리(목록 위에서부터의 비율). */
private const val FingerFollowAnchor = 0.3f

private val FingerImageHeight = 40.dp
private val FingerNoteWidth = 76.dp
private val FingerLyricWidth = 30.dp

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
 * 운지 줄 하나(`.finger-list-row`): 높이 40dp × 음 수 + 8dp. 왼쪽에 음마다 가로 악기 그림(쉼은 ‘쉼’), 가운데 율명 버튼, 오른쪽 가사.
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
    daegang: Boolean,
    onClick: (() -> Unit)?,
) {
    val colors = EolssuTheme.colors
    var rowModifier = Modifier
        .fillMaxWidth()
        .height(FingerImageHeight * cell.parts + 8.dp)
        .drawBehind {
            if (fill != null) drawRect(colors.doneSoft, size = Size(size.width, size.height * fill()))
            when {
                gakStart -> drawRect(colors.text, size = Size(size.width, 2.dp.toPx()))
                daegang -> drawRect(colors.lineStrong, size = Size(size.width, 2.dp.toPx()))
                else -> drawLine(colors.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())))
            }
        }
    if (isSelected) rowModifier = rowModifier.border(2.dp, colors.accent)
    Row(rowModifier) {
        Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 6.dp, vertical = 3.dp)) {
            if (gakStart) {
                Text(
                    stringResource(Res.string.practice_finger_gak, cell.column + 1),
                    modifier = Modifier.align(Alignment.TopStart),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 10.sp),
                    color = colors.textMuted,
                )
            }
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                if (cell.rest) {
                    Text(stringResource(Res.string.practice_finger_rest), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted, textAlign = TextAlign.Center)
                } else {
                    cell.entries.filter { it.yul != null }.forEach { entry -> FingerDiagram(instrument, entry, FingerImageHeight) }
                }
            }
        }
        Column(
            Modifier
                .width(FingerNoteWidth)
                .fillMaxHeight()
                .drawBehind { drawRect(colors.line, size = Size(1.dp.toPx(), size.height)) }
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 3.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            cell.units.forEach { unit ->
                NoteLine(unit, showHangul, big = cell.parts == 1, playing = playingKey != null && unit.key == playingKey)
            }
        }
        Box(
            Modifier.width(FingerLyricWidth).fillMaxHeight()
                .drawBehind { drawRect(colors.line, size = Size(1.dp.toPx(), size.height)) },
            contentAlignment = Alignment.Center,
        ) {
            cell.lyric?.let {
                Text(it.replace("{세로표}", ""), style = MaterialTheme.typography.labelSmall, color = colors.text)
            }
        }
    }
}

/** 가로 악기 그림 한 장(40dp 높이). 자료가 없으면 오른쪽 아래에 ‘운지 자료 없음’. */
@Composable
private fun FingerDiagram(instrument: GuideInstrument, entry: FingerEntry, height: Dp) {
    val colors = EolssuTheme.colors
    val ratio = instrument.imageWidth.toFloat() / (instrument.imageHeight - 300)
    Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        FingeringFigure(
            instrument,
            entry.holes ?: emptyList(),
            Modifier.widthIn(max = height * ratio).height(height),
            description = entry.yul?.fullName,
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
private fun NoteLine(unit: ScoreDisplayUnit, showHangul: Boolean, big: Boolean, playing: Boolean = false) {
    val colors = EolssuTheme.colors
    val note = unit.note
    val isPitch = note.fingeringYul != null
    val hangul = when {
        !showHangul -> null
        note is ScoreNote.Pitch -> note.yul.hangul
        note is ScoreNote.SourcePitch -> note.hangul
        note == ScoreNote.Rest -> stringResource(Res.string.score_rest_hangul)
        else -> null
    }
    Row(Modifier.fillMaxWidth().heightIn(min = FingerImageHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
        if (isPitch) {
            ScoreNoteGlyph(note, size = if (big) 25.sp else 19.sp, color = if (playing) colors.rec else colors.text)
        } else {
            Text(note.symbol, style = MaterialTheme.typography.bodyLarge.copy(fontSize = if (big) 21.sp else 18.sp, fontWeight = FontWeight.Normal), color = if (playing) colors.rec else colors.textSub)
        }
        if (hangul != null) {
            Text(hangul, style = MaterialTheme.typography.labelSmall.copy(fontSize = if (big) 10.sp else 8.sp, lineHeight = 11.sp), color = if (playing) colors.rec else colors.textMuted)
        }
    }
}
