package com.ssafy.eolssu.feature.practice.component

import com.ssafy.eolssu.core.model.ScoreDisplayUnit
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import kotlin.math.max
import kotlin.math.min

data class FingerGroup(val start: Double, val end: Double)

/** 원본 이벤트의 시간 위에서 대강 둘을 묶는다. 마지막 페이지는 한 대강일 수 있다. */
data class FingerPage(val groups: List<FingerGroup>, val cells: List<FingerCell>) {
    val start: Double get() = groups.first().start
}

fun fingerPages(song: Song, cells: List<FingerCell>): List<FingerPage> {
    if (cells.isEmpty()) return emptyList()
    val boundaries = fingerBoundaries(song) + song.score.totalUnits.toDouble()
    val groups = boundaries.zipWithNext().filter { (start, end) -> end > start }.map { (start, end) -> FingerGroup(start, end) }
    return groups.chunked(2).map { pair ->
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

/** 이음·빈 자리·쉼을 제외한 다음 발음 둘. 같은 높이의 반복 음은 별개 이벤트로 남긴다. */
fun nextFingerUnits(song: Song, afterEventIndex: Int): List<ScoreDisplayUnit> = song.displayUnits.asSequence()
    .filter { it.event.index > afterEventIndex && (it.note is ScoreNote.Pitch || it.note is ScoreNote.SourcePitch) }
    .distinctBy { it.event.id }.take(2).toList()

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
