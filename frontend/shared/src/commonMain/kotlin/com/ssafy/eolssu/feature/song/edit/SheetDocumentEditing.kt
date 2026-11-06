package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.*
import com.ssafy.eolssu.data.song.CommonScoreData

/** 서버의 가변 각과 정간 내부 배치를 유지하는 편집 상태. 장단을 알 수 없으면 추정하지 않는다. */
internal fun CommonScoreData.editingDraft(title: String, bpm: Int, jangdan: Jangdan?): SheetDraft {
    require(schemaVersion == 1 && columns.isNotEmpty() && columns.all { it.cells.isNotEmpty() })
    require(bpm > 0 && music.ticksPerJeonggan > 0 && music.ticksPerBeat > 0 && music.hwangHz.isFinite() && music.hwangHz > 0)
    val flat = columns.flatMap { it.cells }
    val byId = events.associateBy { it.id }
    require(columns.map { it.index } == columns.indices.toList())
    columns.forEachIndexed { c, column -> column.cells.forEachIndexed { j, cell -> require(cell.id == "c$c-j$j" && cell.index == j) } }
    require(flat.all { cell -> cell.units.map { it.row to it.col } == cell.units.map { it.row to it.col }.distinct().sortedWith(compareBy({ it.first }, { it.second })) })
    return SheetDraft(title, jangdan, bpm, flat.map { it.editingNotes() },
        flat.mapIndexedNotNull { i, cell ->
            val event = byId[cell.units.firstOrNull()?.eventId]?.takeIf { it.cellId == cell.id }
            Sigimsae.entries.firstOrNull { it.scoreName in event?.sigimsae.orEmpty() }?.let { i to it }
        }.toMap(), columns.map { it.cells.size }, flat.map { it.id }, this)
}

internal fun CommonScoreData.Cell.editingNotes(): List<ScoreNote> = units.map { unit ->
    when (unit.symbol) {
        "" -> ScoreNote.Blank
        "-" -> ScoreNote.Hold
        "쉼" -> ScoreNote.Rest
        else -> parseSourcePitch(unit.symbol)
    }
}

private fun parseSourcePitch(symbol: String): ScoreNote.SourcePitch {
    val prefixes = listOf("하배" to -2, "배" to -1, "중청" to 2, "청" to 1)
    val prefix = prefixes.firstOrNull { symbol.startsWith(it.first) }
    return ScoreNote.SourcePitch(if (prefix == null) symbol else symbol.removePrefix(prefix.first), prefix?.second ?: 0)
}

internal fun SheetDraft.originalCell(index: Int): CommonScoreData.Cell? =
    sourceCellIds?.getOrNull(index)?.let(originalCells::get)

private fun SheetDraft.layout(index: Int): List<CommonScoreData.Unit> {
    val cell = originalCell(index)
    if (cell != null && cell.editingNotes() == cells[index]) return cell.units
    return cells[index].mapIndexed { row, note ->
        val symbol = when (note) {
            ScoreNote.Blank -> ""
            ScoreNote.Hold -> "-"
            ScoreNote.Rest -> "쉼"
            is ScoreNote.SourcePitch -> note.hangul
            is ScoreNote.Pitch -> note.yul.hangul
        }
        CommonScoreData.Unit(row, 0, symbol, "")
    }
}

/** 바꾼 칸만 새 배치로 만들고, 같은 표기의 각·음 위치와 이벤트 참조를 함께 옮긴다. */
internal fun SheetDraft.toCommonScore(): CommonScoreData {
    val source = requireNotNull(original)
    val initial = source.editingDraft(title, bpm, jangdan)
    if (cells == initial.cells && sizes == initial.sizes && sourceCellIds == initial.sourceCellIds && sigimsae == initial.sigimsae) return source.copy(score = source.score.copy(title = title))
    val byId = source.events.associateBy { it.id }
    val initialSigns = initial.sigimsae.mapKeys { (index, _) -> initial.sourceCellIds?.get(index) }
    val layouts = cells.indices.map(::layout)
    var ticks = source.music.ticksPerJeonggan.toLong()
    fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
    layouts.forEach { units ->
        val rows = units.groupBy { it.row }
        rows.values.forEach { row ->
            val divisor = rows.size.toLong() * row.size
            ticks = ticks / gcd(ticks, divisor) * divisor
            require(ticks <= Int.MAX_VALUE)
        }
    }
    val factor = ticks / source.music.ticksPerJeonggan
    require(source.music.ticksPerBeat.toLong() * factor <= Int.MAX_VALUE)
    val events = mutableListOf<CommonScoreData.Event>()
    var flatIndex = 0
    val columns = sizes.mapIndexed { c, count ->
        CommonScoreData.Column(c, List(count) { j ->
            val index = flatIndex++
            val old = originalCell(index)
            val units = layouts[index]
            val rowGroups = units.groupBy { it.row }
            val result = units.map { unit ->
                val cellId = "c$c-j$j"
                val id = "$cellId-r${unit.row}-u${unit.col}"
                val start = index * ticks + ticks * unit.row / rowGroups.size + ticks * unit.col / rowGroups.size / rowGroups.getValue(unit.row).size
                val length = ticks / rowGroups.size / rowGroups.getValue(unit.row).size
                if (unit.symbol.isEmpty() && unit.eventId.isEmpty()) {
                    CommonScoreData.Unit(unit.row, unit.col, "", "") // 입력 중인 빈 자리. 초안에만 저장한다.
                } else if (unit.symbol in setOf("", "-")) {
                    val last = events.lastOrNull()
                    if (last != null && last.startTick + last.durationTick == start && (last.kind == "REST" || last.cellId.substringBefore('-') == "c$c")) {
                        events[events.lastIndex] = last.copy(durationTick = start + length - last.startTick)
                        CommonScoreData.Unit(unit.row, unit.col, unit.symbol, last.id)
                    } else CommonScoreData.Unit(unit.row, unit.col, unit.symbol, "") // 미완성 이음도 초안에는 보존한다.
                } else {
                    val note = if (unit.symbol == "쉼") null else parseSourcePitch(unit.symbol)
                    val prior = byId[unit.eventId] ?: old?.units?.firstOrNull { it.row == unit.row && it.col == unit.col }?.let { byId[it.eventId] }
                    val first = unit == units.first()
                    val signs = if (first && sigimsae[index] != initialSigns[old?.id]) prior?.sigimsae.orEmpty().filterNot { sign -> Sigimsae.entries.any { it.scoreName == sign } } + listOfNotNull(sigimsae[index]?.scoreName) else prior?.sigimsae.orEmpty()
                    events += CommonScoreData.Event(id, if (note == null) "REST" else "NOTE", note?.yul, note?.octave,
                        start, length, cellId, prior?.breathAfter ?: false, signs)
                    CommonScoreData.Unit(unit.row, unit.col, unit.symbol, id)
                }
            }
            CommonScoreData.Cell("c$c-j$j", j, result, old?.lyric)
        })
    }
    return source.copy(score = source.score.copy(title = title),
        music = source.music.copy(ticksPerJeonggan = ticks.toInt(), ticksPerBeat = (source.music.ticksPerBeat * factor).toInt()),
        columns = columns, events = events)
}

/** 유효한 악보는 원본 타임라인 그대로 표시한다. 미입력 초안도 같은 row/col과 가사를 보인다. */
internal fun SheetDraft.renderEditingScore(): Score {
    val encoded = runCatching { toCommonScore() }.getOrNull()
    if (unfilledCells.isEmpty() && encoded != null) runCatching { encoded.toScore() }.getOrNull()?.let { return it }
    return Score(sizes.mapIndexed { c, count -> List(count) { j ->
        val index = indexOf(c, j)
        val units = layout(index)
        ScoreCell(cells[index].ifEmpty { listOf(ScoreNote.Blank) }, "c$c-j$j",
            units.map { ScoreUnit(it.row, it.col, it.symbol, it.eventId) }, originalCell(index)?.lyric)
    } })
}
