package com.ssafy.eolssu.feature.practice.component

import com.ssafy.eolssu.core.model.ScoreDisplayUnit
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.key
import kotlin.math.max
import kotlin.math.min

data class FingerGroup(val start: Double, val end: Double)

/** 실제 발음 6개 이상이 되도록 원본 대강을 묶는다. 마지막 페이지는 남은 만큼 표시한다. */
data class FingerPage(val groups: List<FingerGroup>, val cells: List<FingerCell>) {
    val start: Double get() = groups.first().start
}

fun fingerPages(song: Song, cells: List<FingerCell>): List<FingerPage> {
    if (cells.isEmpty()) return emptyList()
    val boundaries = fingerBoundaries(song) + song.score.totalUnits.toDouble()
    val groups = boundaries.zipWithNext().filter { (start, end) -> end > start }.map { (start, end) -> FingerGroup(start, end) }
    val pageGroups = mutableListOf<List<FingerGroup>>()
    var pending = mutableListOf<FingerGroup>()
    var pitchCount = 0
    for (group in groups) {
        val onsets = song.displayUnits.count { unit ->
            unit.unitStart >= group.start && unit.unitStart < group.end &&
                (unit.note is ScoreNote.Pitch || unit.note is ScoreNote.SourcePitch)
        }
        if (pitchCount >= 6 && onsets > 0) {
            pageGroups += pending
            pending = mutableListOf()
            pitchCount = 0
        }
        pending += group
        pitchCount += onsets
    }
    if (pending.isNotEmpty()) pageGroups += pending
    return pageGroups.map { pair ->
        val start = pair.first().start
        val end = pair.last().end
        FingerPage(pair, cells.mapNotNull { cell -> cell.onPage(start, end) })
    }
}

/** 페이지 경계만 표시용으로 자른다. 원본 Score·event·ID·tick은 그대로 둔다. */
private fun FingerCell.onPage(start: Double, end: Double): FingerCell? {
    val slots = units.indices.filter { units[it].unitStart < end && units[it].unitStart + units[it].unitDuration > start }
    if (slots.isEmpty()) return null
    val notes = slots.map { slot ->
        val unit = units[slot]
        val from = max(start, unit.unitStart)
        val to = min(end, unit.unitStart + unit.unitDuration)
        val continued = from > unit.unitStart + 0.000001 && (unit.note is ScoreNote.Pitch || unit.note is ScoreNote.SourcePitch)
        unit.copy(unitStart = from, unitDuration = to - from, note = if (continued) ScoreNote.Hold else unit.note,
            symbol = if (continued) "-" else unit.symbol)
    }
    return copy(
        units = notes,
        entries = slots.mapIndexed { part, slot -> if (notes[part].note == ScoreNote.Hold || notes[part].note == ScoreNote.Blank) FingerEntry(null, null) else entries[slot] },
        lyricSlots = slots.mapIndexed { part, slot -> if (notes[part].unitStart > units[slot].unitStart + 0.000001) "" else lyricSlots[slot] },
    )
}

/** 같은 이벤트가 이어지는 동안에도 절대 표시 위치로 전환하고 역방향 탐색도 같은 규칙을 쓴다. */
fun List<FingerPage>.pageAt(position: Double): Int = indexOfLast { it.start <= position }.coerceAtLeast(0)

/** 한 페이지에 보일 운지 수. 쉼·장음·빈 자리는 세지 않는다. 다음에 부를 두 음은 따로다. */
internal const val FingerPageFingerings = 5

/**
 * 운지 페이지. 원본 순서대로 표시 자리를 담고, 운지(음이 있는 자리)가 [FingerPageFingerings]개 찬 뒤 다음 음이 오면 넘긴다.
 * 운지 뒤에 붙는 쉼·장음은 같은 페이지에 남는다. 정확히 다섯 개를 맞추려고 한 정간을 두 페이지에 나눌 수 있으며,
 * 뒤쪽 조각은 [FingerCell.continued]로 표시한다. 원본 정간·이벤트·식별자는 바꾸지 않는다.
 */
@Suppress("UNUSED_PARAMETER")
internal fun fingerAnglePages(song: Song, cells: List<FingerCell>): List<List<FingerCell>> {
    val pages = mutableListOf<List<Pair<FingerCell, Int>>>()
    var current = mutableListOf<Pair<FingerCell, Int>>()
    var count = 0
    for (cell in cells) {
        if (cell.units.isEmpty()) {
            current += cell to -1
            continue
        }
        cell.units.indices.forEach { slot ->
            val fingering = cell.entries[slot].note != null
            if (fingering && count >= FingerPageFingerings) {
                pages += current
                current = mutableListOf()
                count = 0
            }
            current += cell to slot
            if (fingering) count++
        }
    }
    if (current.isNotEmpty()) pages += current
    return pages.map { page ->
        val slices = mutableListOf<Pair<FingerCell, MutableList<Int>>>()
        page.forEach { (cell, slot) ->
            val last = slices.lastOrNull()
            if (last != null && last.first.index == cell.index && slot >= 0) last.second += slot else slices += cell to mutableListOf(slot)
        }
        slices.map { (cell, slots) ->
            if (slots == listOf(-1) || slots.size == cell.units.size) cell
            else cell.copy(
                units = slots.map { cell.units[it] },
                entries = slots.map { cell.entries[it] },
                lyricSlots = slots.map { cell.lyricSlots[it] },
                continued = slots.first() > 0,
            )
        }
    }
}

/** 이음·빈 자리·쉼을 제외한 다음 발음 둘. 같은 높이의 반복 음은 별개 이벤트로 남긴다. */
fun nextFingerUnits(song: Song, afterEventIndex: Int): List<ScoreDisplayUnit> = song.displayUnits.asSequence()
    .filter { it.event.index > afterEventIndex && (it.note is ScoreNote.Pitch || it.note is ScoreNote.SourcePitch) }
    .distinctBy { it.event.id }.take(2).toList()

/** 다음 발음만 한 행씩 만들되, 운지와 가사는 원본 표시 슬롯에서 함께 가져온다. */
internal fun nextFingerCells(song: Song, cells: List<FingerCell>, afterEventIndex: Int): List<FingerCell> =
    nextFingerUnits(song, afterEventIndex).map { unit ->
        val cell = cells.first { source -> source.units.any { it.key == unit.key } }
        val slot = cell.units.indexOfFirst { it.key == unit.key }
        cell.copy(units = listOf(unit), entries = listOf(cell.entries[slot]), lyricSlots = listOf(cell.lyricSlots[slot]))
    }

private fun fingerBoundaries(song: Song): List<Double> {
    val total = song.score.totalUnits
    val timing = song.score.timing
    val mapping = song.jangdanPlayback
    if (timing != null && mapping != null) {
        val ticks = timing.ticksPerJeonggan.toDouble()
        val boundaries = mutableListOf(0.0)
        var cycle = mapping.startTick.toDouble()
        while (cycle < total * ticks) {
            var at = cycle
            for (size in mapping.groups) {
                val position = at / ticks
                if (position > 0 && position < total) boundaries += position
                at += size * mapping.cellTicks
            }
            cycle += mapping.cycleTicks
        }
        return boundaries.distinct().sorted()
    }
    val pattern = song.jangdan ?: return song.score.columns.runningFold(0.0) { sum, rows -> sum + rows.size }.dropLast(1).distinct()
    val scale = timing?.let { it.ticksPerBeat.toDouble() / it.ticksPerJeonggan / pattern.subdivision } ?: 1.0
    val lengths = pattern.daegang.map { it * scale }
    val result = mutableListOf<Double>()
    var offset = 0
    song.score.columns.forEach { column ->
        var row = 0.0
        var group = 0
        while (row < column.size - 0.000001) {
            result += offset + row
            row += lengths[group % lengths.size]
            group++
        }
        offset += column.size
    }
    return result.distinct().sorted()
}
