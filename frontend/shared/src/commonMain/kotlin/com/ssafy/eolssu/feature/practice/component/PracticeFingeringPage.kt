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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.ScoreDisplayUnit
import com.ssafy.eolssu.core.model.ScoreEvent
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.HoleState
import com.ssafy.eolssu.core.model.fingering.label
import com.ssafy.eolssu.core.model.fingeringYul
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.score.FingeringFigure
import com.ssafy.eolssu.core.ui.score.ScoreNoteGlyph
import com.ssafy.eolssu.feature.practice.PracticeMode
import com.ssafy.eolssu.feature.practice.PracticeUiState
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_finger_end_note
import eolssu.shared.generated.resources.practice_finger_gak
import eolssu.shared.generated.resources.practice_finger_head_fingering
import eolssu.shared.generated.resources.practice_finger_head_lyric
import eolssu.shared.generated.resources.practice_finger_head_yul
import eolssu.shared.generated.resources.practice_finger_heading
import eolssu.shared.generated.resources.practice_finger_jeonggan
import eolssu.shared.generated.resources.practice_finger_missing
import eolssu.shared.generated.resources.practice_finger_next_end
import eolssu.shared.generated.resources.practice_finger_next_other
import eolssu.shared.generated.resources.practice_finger_next_same
import eolssu.shared.generated.resources.practice_finger_page_count
import eolssu.shared.generated.resources.practice_finger_preview
import eolssu.shared.generated.resources.practice_finger_range
import eolssu.shared.generated.resources.practice_finger_rest
import eolssu.shared.generated.resources.practice_page_next
import eolssu.shared.generated.resources.practice_page_next_desc
import eolssu.shared.generated.resources.practice_page_prev
import eolssu.shared.generated.resources.practice_page_prev_desc
import eolssu.shared.generated.resources.score_rest_hangul
import org.jetbrains.compose.resources.stringResource

/** 한 페이지에 넣는 정간 수(보드 `fingerPages(total, gak, 6)`). 각 경계를 넘지 않는다. */
const val FINGER_PAGE_SIZE = 6

/** 정간 하나의 위치(각·정간)와 운지 줄 내용. [units]는 이 정간에 적힌 표시 자리(율명·이음·쉼)다. */
data class FingerCell(val index: Int, val column: Int, val row: Int, val units: List<ScoreDisplayUnit>, val entries: List<FingerEntry>) {
    val rest: Boolean get() = entries.isNotEmpty() && entries.all { it.yul == null }
    val parts: Int get() = units.size.coerceAtLeast(1)
    val firstEvent: ScoreEvent? get() = units.firstOrNull()?.event
}

/** 정간 안 표시 자리 하나의 운지. [yul]이 null이면 쉼(또는 앞 음이 없는 이음)이다. 이음은 앞 음의 율명을 따른다. */
data class FingerEntry(val yul: Yul?, val holes: List<HoleState>?)

/**
 * 운지 악보 페이지(보드 `fingerPages`): 같은 각 안에서 최대 [FINGER_PAGE_SIZE]정간, [preview]는 이어지는 두 정간.
 */
data class FingerPage(val start: Int, val end: Int, val column: Int, val fromRow: Int, val toRow: Int, val preview: List<Int>) {
    val count: Int get() = end - start
}

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
            cells += FingerCell(cells.size, column, row, units, entries)
        }
    }
    return cells
}

/** 각 경계를 넘지 않게 [FINGER_PAGE_SIZE]정간씩 자른 페이지. */
fun fingerPages(cells: List<FingerCell>, size: Int = FINGER_PAGE_SIZE): List<FingerPage> {
    val pages = mutableListOf<FingerPage>()
    var start = 0
    while (start < cells.size) {
        val column = cells[start].column
        var end = start
        while (end < cells.size && end - start < size && cells[end].column == column) end++
        val preview = (end until minOf(cells.size, end + 2)).toList()
        pages += FingerPage(start, end, column, cells[start].row, cells[end - 1].row, preview)
        start = end
    }
    return pages
}

/**
 * ‘운지법과 함께 보기’(보드 Practice-Fingering): 정간보 대신 페이지 단위 운지 악보.
 * ‘지금 읽는 부분 · N각 · a–b정간’ 머리, 표(‘{악기} 운지 | 율명 | 가사’), 줄마다 가로 악기 그림 위 구멍 표시와 율명·가사,
 * 다음 페이지 두 정간 미리보기, 페이지 이동. 재생·연주 중에는 현재 위치의 페이지로 따라가고 이동 버튼이 잠긴다.
 */
@Composable
fun PracticeFingeringPage(state: PracticeUiState, onSelectEvent: (ScoreEvent) -> Unit, modifier: Modifier = Modifier) {
    val song = state.song ?: return
    val colors = EolssuTheme.colors
    val instrument = state.instrument
    val cells = remember(song, instrument) { fingerCells(song, instrument) }
    val pages = remember(cells) { fingerPages(cells) }
    if (pages.isEmpty()) return
    val live = state.live
    val currentUnit = if (live) state.currentScoreUnit else song.displayUnits.firstOrNull { it.event.index == state.selected }
    val currentCell = remember(cells, currentUnit?.column, currentUnit?.row) {
        cells.indexOfFirst { it.column == currentUnit?.column && it.row == currentUnit.row }.coerceAtLeast(0)
    }
    fun pageAt(cell: Int) = pages.indexOfFirst { cell >= it.start && cell < it.end }.coerceAtLeast(0)
    var page by rememberSaveable(song.id) { mutableIntStateOf(pageAt(currentCell)) }
    LaunchedEffect(live, currentCell) { if (live) page = pageAt(currentCell) }
    page = page.coerceIn(0, pages.lastIndex)
    val current = pages[page]
    val recording = state.mode == PracticeMode.Recording || state.resuming
    val fraction = rememberLiveFraction(state)
    val instrumentName = stringResource(instrument.label)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.practice_finger_heading), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = colors.accentText)
            Text(
                stringResource(Res.string.practice_finger_range, current.column + 1, current.fromRow + 1, current.toRow + 1),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                color = colors.textMuted,
            )
        }
        val shape = RoundedCornerShape(3.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(colors.surface).border(1.dp, colors.lineStrong, shape)) {
            FingerHead(instrumentName)
            for (i in current.start until current.end) {
                val cell = cells[i]
                FingerRow(
                    cell = cell,
                    instrument = instrument,
                    isCurrent = live && i == currentCell,
                    isSelected = !live && i == currentCell,
                    recording = recording,
                    fraction = fraction,
                    showHangul = state.settings.showHangul,
                    gakStart = cell.row == 0,
                    daegang = cell.row > 0 && cell.row in song.jangdan?.daegangStarts.orEmpty() && song.score.columns[cell.column].size == song.jangdan?.gak,
                    onClick = if (state.mode == PracticeMode.Ready) cell.firstEvent?.let { event -> { onSelectEvent(event) } } else null,
                )
            }
        }
        Column(Modifier.fillMaxWidth().heightIn(min = FingerPreviewRowHeight * 2 + 28.dp)) {
            Text(
                nextLabel(current, pages, cells),
                modifier = Modifier.padding(bottom = 4.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 24.sp),
                color = colors.textMuted,
            )
            if (current.preview.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().height(56.dp).background(colors.surface, RoundedCornerShape(6.dp)).border(1.dp, colors.lineSoft, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(Res.string.practice_finger_end_note), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = colors.textMuted)
                }
            } else {
                current.preview.forEachIndexed { n, i -> FingerPreviewRow(cells[i], instrument, state.settings.showHangul, first = n == 0) }
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            PageButton(stringResource(Res.string.practice_page_prev), stringResource(Res.string.practice_page_prev_desc), enabled = !live && page > 0) { page-- }
            Text(
                stringResource(Res.string.practice_finger_page_count, page + 1, pages.size, current.count),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
            PageButton(stringResource(Res.string.practice_page_next), stringResource(Res.string.practice_page_next_desc), enabled = !live && page < pages.lastIndex) { page++ }
        }
    }
}

/** 미리보기 문구: ‘같은/다음 N각 · M정간부터 이어서’, 끝이면 ‘마지막 정간까지 이어서’. */
@Composable
private fun nextLabel(current: FingerPage, pages: List<FingerPage>, cells: List<FingerCell>): String {
    val next = cells.getOrNull(current.end) ?: return stringResource(Res.string.practice_finger_next_end)
    val same = next.column == current.column
    return stringResource(if (same) Res.string.practice_finger_next_same else Res.string.practice_finger_next_other, next.column + 1, next.row + 1)
}

/** 재생·연주 중 현재 음 안에서 지난 비율. 멈춰 있으면 [PracticeUiState.pausedFraction]. */
@Composable
private fun rememberLiveFraction(state: PracticeUiState): () -> Float {
    val fraction = remember { mutableFloatStateOf(0f) }
    val started = state.eventStartedAt
    val duration = state.eventDurationMs()
    LaunchedEffect(started, duration, state.pausedFraction, state.live) {
        if (!state.live) { fraction.floatValue = 0f; return@LaunchedEffect }
        if (started == null) { fraction.floatValue = state.pausedFraction; return@LaunchedEffect }
        while (true) {
            withFrameNanos { fraction.floatValue = (started.elapsedNow().inWholeMicroseconds / 1000.0 / duration).toFloat().coerceIn(0f, 1f) }
        }
    }
    return { fraction.floatValue }
}

private val FingerImageHeight = 40.dp
private val FingerNoteWidth = 76.dp
private val FingerLyricWidth = 30.dp
private val FingerPreviewRowHeight = 48.dp

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
 * 각 첫 줄은 2dp 먹색 윗선과 ‘N각’, 대강 첫 줄은 2dp 회색 윗선, 그 밖은 점선. 현재·고른 줄은 2dp 안쪽 테두리(연주 중 붉은색).
 */
@Composable
private fun FingerRow(
    cell: FingerCell,
    instrument: GuideInstrument,
    isCurrent: Boolean,
    isSelected: Boolean,
    recording: Boolean,
    fraction: () -> Float,
    showHangul: Boolean,
    gakStart: Boolean,
    daegang: Boolean,
    onClick: (() -> Unit)?,
) {
    val colors = EolssuTheme.colors
    val ring = if (recording) colors.rec else colors.accent
    var rowModifier = Modifier
        .fillMaxWidth()
        .height(FingerImageHeight * cell.parts + 8.dp)
        .drawBehind {
            when {
                gakStart -> drawRect(colors.text, size = Size(size.width, 2.dp.toPx()))
                daegang -> drawRect(colors.lineStrong, size = Size(size.width, 2.dp.toPx()))
                else -> drawLine(colors.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())))
            }
        }
    if (isCurrent || isSelected) rowModifier = rowModifier.border(2.dp, if (isSelected) colors.accent else ring)
    if (isCurrent) {
        // 보드 `.score-playhead`: 현재 줄 안에서 지난 만큼 내려온 붉은 선.
        rowModifier = rowModifier.drawWithContent {
            drawContent()
            val y = size.height * fraction()
            val band = 14.dp.toPx().coerceAtMost(y)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, colors.rec.copy(alpha = 0.16f)), startY = y - band, endY = y), topLeft = Offset(0f, y - band), size = Size(size.width, band))
            drawLine(colors.rec, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.5.dp.toPx())
        }
    }
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
                NoteLine(unit, showHangul, big = cell.parts == 1)
            }
        }
        Box(Modifier.width(FingerLyricWidth).fillMaxHeight().drawBehind { drawRect(colors.line, size = Size(1.dp.toPx(), size.height)) })
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

/** 율명 한 줄(`.note-line`): 글자와 오른쪽 작은 한글 읽는 법. */
@Composable
private fun NoteLine(unit: ScoreDisplayUnit, showHangul: Boolean, big: Boolean) {
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
            ScoreNoteGlyph(note, size = if (big) 25.sp else 19.sp, color = colors.text)
        } else {
            Text(note.symbol, style = MaterialTheme.typography.bodyLarge.copy(fontSize = if (big) 21.sp else 18.sp, fontWeight = FontWeight.Normal), color = colors.textSub)
        }
        if (hangul != null) {
            Text(hangul, style = MaterialTheme.typography.labelSmall.copy(fontSize = if (big) 10.sp else 8.sp, lineHeight = 11.sp), color = colors.textMuted)
        }
    }
}

/** 다음 페이지 미리보기 줄(`.finger-preview-row`): 옅은 테두리, ‘N정간’ 표시, 작은 그림과 18sp 율명. */
@Composable
private fun FingerPreviewRow(cell: FingerCell, instrument: GuideInstrument, showHangul: Boolean, first: Boolean) {
    val colors = EolssuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(FingerImageHeight * cell.parts + 8.dp)
            .background(colors.surface)
            .drawBehind {
                val w = 1.dp.toPx()
                drawRect(colors.lineSoft, size = Size(w, size.height))
                drawRect(colors.lineSoft, topLeft = Offset(size.width - w, 0f), size = Size(w, size.height))
                if (first) drawRect(colors.lineSoft, size = Size(size.width, w))
                drawRect(colors.lineSoft, topLeft = Offset(0f, size.height - w), size = Size(size.width, w))
            },
    ) {
        Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 6.dp, vertical = 3.dp)) {
            Text(
                stringResource(Res.string.practice_finger_jeonggan, cell.row + 1),
                modifier = Modifier.align(Alignment.TopStart),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 10.sp),
                color = colors.textMuted,
            )
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                if (cell.rest) {
                    Text(stringResource(Res.string.practice_finger_rest), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted, textAlign = TextAlign.Center)
                } else {
                    cell.entries.filter { it.yul != null }.forEach { entry -> FingerDiagram(instrument, entry, FingerImageHeight) }
                }
            }
        }
        Column(
            Modifier.width(FingerNoteWidth).fillMaxHeight().drawBehind { drawRect(colors.lineSoft, size = Size(1.dp.toPx(), size.height)) }.padding(horizontal = 3.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            cell.units.forEach { unit -> NoteLine(unit, showHangul, big = false) }
        }
        Box(Modifier.width(FingerLyricWidth).fillMaxHeight().drawBehind { drawRect(colors.lineSoft, size = Size(1.dp.toPx(), size.height)) })
    }
}

/** 페이지 이동 버튼(`.finger-page-nav button`, 44dp): 옅은 테두리, 반지름 7, 10sp 500. 잠기면 흐리다. */
@Composable
private fun PageButton(text: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier
            .alpha(if (enabled) 1f else 0.38f)
            .widthIn(min = 44.dp)
            .heightIn(min = 44.dp)
            .background(colors.surface, shape)
            .border(1.dp, colors.lineSoft, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium), color = colors.text)
    }
}
