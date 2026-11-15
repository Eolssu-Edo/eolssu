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
    return SheetDraft(title, jangdan, bpm, flat.map { cell ->
        cell.editingNotes().mapIndexed { sub, note ->
            val unit = cell.units[sub]
            val event = byId[unit.eventId]?.takeIf { it.kind == "NOTE" && it.octave !in -2..2 &&
                it.cellId == cell.id && it.id == "${cell.id}-r${unit.row}-u${unit.col}" }
            // 표기 범위를 벗어난 초안도 실제 음고를 유지한다. 축약된 글자를 기본 옥타브로 다시 읽지 않는다.
            event?.let { ScoreNote.SourcePitch(requireNotNull(it.yul), requireNotNull(it.octave)) } ?: note
        }
    },
        flat.flatMapIndexed { i, cell ->
            // 이 정간에서 시작하는 이벤트만, 그 이벤트가 처음 나오는 소박 자리에 둔다(이음 자리는 앞 이벤트를 가리킨다).
            val seen = mutableSetOf<String>()
            cell.units.mapIndexedNotNull { sub, unit ->
                val event = byId[unit.eventId]?.takeIf { it.cellId == cell.id && seen.add(unit.eventId) }
                event?.sigimsae?.filter { HanbaeMark.fromSymbol(it) == null }?.takeIf { it.isNotEmpty() }?.let { signs -> NoteSlot(i, sub) to signs.map(::Sigimsae) }
            }
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

/** [index] 정간의 표시 자리(row/col) 목록. 순서는 [SheetDraft.cells]의 음 순서와 같다. */
internal fun SheetDraft.layout(index: Int): List<CommonScoreData.Unit> {
    originalCell(index)?.withEditedPitches(cells[index])?.let { return it }
    return cells[index].mapIndexed { row, note ->
        CommonScoreData.Unit(row, 0, note.editingSymbol(), "")
    }
}

private fun ScoreNote.editingSymbol(): String = when (this) {
    ScoreNote.Blank -> ""
    ScoreNote.Hold -> "-"
    ScoreNote.Rest -> "쉼"
    is ScoreNote.SourcePitch -> hangul
    is ScoreNote.Pitch -> yul.hangul
}

private fun ScoreNote.sourcePitch(): ScoreNote.SourcePitch? = when (this) {
    is ScoreNote.SourcePitch -> this
    is ScoreNote.Pitch -> parseSourcePitch(yul.hangul)
    else -> null
}

/** 음높이만 바꾼 소박의 row·col과 시작 이벤트를 그대로 둔다. */
private fun CommonScoreData.Cell.withEditedPitches(notes: List<ScoreNote>): List<CommonScoreData.Unit>? {
    if (units.size != notes.size) return null
    val before = editingNotes()
    if (before == notes) return units
    return units.mapIndexed { sub, unit ->
        val note = notes[sub]
        when {
            note == before[sub] -> unit
            note.isPitch() && (before[sub].isPitch() ||
                (before[sub] == ScoreNote.Blank && note is ScoreNote.SourcePitch && note.octave !in -2..2)) &&
                unit.eventId == "$id-r${unit.row}-u${unit.col}" ->
                unit.copy(symbol = note.editingSymbol())
            else -> return null
        }
    }
}

/** 원본 배치의 음높이만 달라졌으면 원본 이벤트 길이와 참조를 재계산하지 않는다. */
private fun SheetDraft.pitchOnlyScore(source: CommonScoreData, music: CommonScoreData.Music, initial: SheetDraft): CommonScoreData? {
    val replacements = mutableMapOf<String, ScoreNote.SourcePitch>()
    var index = 0
    val columns = source.columns.map { column -> column.copy(cells = column.cells.map { cell ->
        val notes = cells[index]
        val before = initial.cells[index++]
        val units = cell.withEditedPitches(notes) ?: return null
        units.forEachIndexed { sub, unit ->
            if (notes[sub] != before[sub]) replacements[unit.eventId] = notes[sub].sourcePitch() ?: return null
        }
        cell.copy(units = units)
    }) }
    val events = source.events.map { event ->
        val note = replacements.remove(event.id)
        when {
            note == null -> event
            event.kind == "NOTE" -> event.copy(yul = note.yul, octave = note.octave)
            else -> return null
        }
    }
    if (replacements.isNotEmpty()) return null
    return source.copy(score = source.score.copy(title = title), music = music, columns = columns, events = events)
}

/** 바꾼 칸만 새 배치로 만들고, 같은 표기의 각·음 위치와 이벤트 참조를 함께 옮긴다. */
internal fun SheetDraft.toCommonScore(): CommonScoreData = scoreWithTempo(toCommonScoreWithoutTempo())

internal fun SheetDraft.toCommonScoreWithoutTempo(): CommonScoreData {
    val source = requireNotNull(original)
    val initial = source.editingDraft(title, bpm, jangdan)
    val music = source.music.copy(jangdan = jangdanName)
    if (sizes == initial.sizes && sourceCellIds == initial.sourceCellIds && sigimsae == initial.sigimsae) {
        if (cells == initial.cells) return source.copy(score = source.score.copy(title = title), music = music)
        pitchOnlyScore(source, music, initial)?.let { return it }
    }
    val byId = source.events.associateBy { it.id }
    val initialSigns = initial.sigimsae.mapKeys { (slot, _) -> initial.sourceCellIds?.get(slot.cell) to slot.sub }
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
    var lastSourceEventId: String? = null
    var flatIndex = 0
    val columns = sizes.mapIndexed { c, count ->
        CommonScoreData.Column(c, List(count) { j ->
            val index = flatIndex++
            val old = originalCell(index)
            val units = layouts[index]
            val rowGroups = units.groupBy { it.row }
            val result = units.mapIndexed { sub, unit ->
                val cellId = "c$c-j$j"
                val id = "$cellId-r${unit.row}-u${unit.col}"
                val start = index * ticks + ticks * unit.row / rowGroups.size + ticks * unit.col / rowGroups.size / rowGroups.getValue(unit.row).size
                val length = ticks / rowGroups.size / rowGroups.getValue(unit.row).size
                val sourceEvent = byId[unit.eventId]
                val originalRestStart = sourceEvent?.kind == "REST" &&
                    unit.eventId == "${old?.id}-r${unit.row}-u${unit.col}"
                if (unit.symbol.isEmpty() && unit.eventId.isEmpty()) {
                    CommonScoreData.Unit(unit.row, unit.col, "", "") // 입력 중인 빈 자리. 초안에만 저장한다.
                } else if (unit.symbol in setOf("", "-") && originalRestStart) {
                    // 기존 JGB는 각 첫 '-'를 별도 쉼 이벤트로 확정하기도 한다. 표시만 보고 소리로 바꾸지 않는다.
                    events += requireNotNull(sourceEvent).copy(id = id, startTick = start, durationTick = length, cellId = cellId)
                    lastSourceEventId = sourceEvent.id
                    CommonScoreData.Unit(unit.row, unit.col, unit.symbol, id)
                } else if (unit.symbol in setOf("", "-") && !cells[index][sub].isPitch()) {
                    val last = events.lastOrNull()
                    // 명시적 지속과 원본에서 같은 이벤트를 가리키는 이음은 각 경계를 넘어도 유지한다.
                    // 새 각의 입력 중 빈칸을 앞 음에 임의로 붙이지 않는다.
                    val sourceContinuation = unit.eventId.isNotEmpty() && unit.eventId == lastSourceEventId
                    if (last != null && last.startTick + last.durationTick == start &&
                        (last.kind == "REST" || last.cellId.substringBefore('-') == "c$c" ||
                            (unit.symbol == "-" && unit.eventId.isEmpty()) || sourceContinuation)) {
                        events[events.lastIndex] = last.copy(durationTick = start + length - last.startTick)
                        CommonScoreData.Unit(unit.row, unit.col, unit.symbol, last.id)
                    } else CommonScoreData.Unit(unit.row, unit.col, unit.symbol, "") // 미완성 이음도 초안에는 보존한다.
                } else {
                    val note = cells[index][sub].sourcePitch()
                    val prior = byId[unit.eventId] ?: old?.units?.firstOrNull { it.row == unit.row && it.col == unit.col }?.let { byId[it.eventId] }
                    lastSourceEventId = prior?.id
                    // 편집 상태는 소박마다 원본 시김새를 모두 담으므로, 바뀐 소박은 그 목록으로 통째로 바꾼다.
                    val slot = NoteSlot(index, sub)
                    val signs = if (sigimsae[slot] != initialSigns[old?.id to sub]) sigimsae[slot].orEmpty().map { it.scoreName } else prior?.sigimsae.orEmpty()
                    events += CommonScoreData.Event(id, if (note == null) "REST" else "NOTE", note?.yul, note?.octave,
                        start, length, cellId, prior?.breathAfter ?: false, signs)
                    CommonScoreData.Unit(unit.row, unit.col, unit.symbol, id)
                }
            }
            CommonScoreData.Cell("c$c-j$j", j, result, old?.lyric)
        })
    }
    return source.copy(score = source.score.copy(title = title),
        music = music.copy(ticksPerJeonggan = ticks.toInt(), ticksPerBeat = (music.ticksPerBeat * factor).toInt()),
        columns = columns, events = events)
}

/** 유효한 악보는 원본 타임라인 그대로 표시한다. 미입력 초안도 같은 row/col과 가사를 보인다. */
internal fun SheetDraft.renderEditingScore(): Score {
    val encoded = runCatching { toCommonScore() }.getOrNull()
    val reference = tempoReferenceBpm ?: bpm
    fun CommonScoreData.displayOrNull() = runCatching {
        toScore(tempoExpressions, baseBpm = reference.toDouble(), tempoScale = bpm.toDouble() / reference)
    }.getOrNull()
    val rendered = encoded?.displayOrNull()
    val displayed = rendered ?: original?.displayOrNull()
    if (unfilledCells.isEmpty() && rendered != null) return rendered
    return Score(sizes.mapIndexed { c, count -> List(count) { j ->
        val index = indexOf(c, j)
        val units = layout(index)
        // 표기할 수 없던 원본 NOTE도 실제 빈칸과 구분한다. 표시만 복원하고 초안·원본 JSON은 바꾸지 않는다.
        val displayNotes = cells[index].mapIndexed { sub, note ->
            val unit = units.getOrNull(sub)
            val event = original?.events?.firstOrNull { it.id == unit?.eventId && it.kind == "NOTE" &&
                it.id == "c$c-j$j-r${unit.row}-u${unit.col}" }
            if (note == ScoreNote.Blank && event != null) runCatching {
                ScoreNote.SourcePitch(requireNotNull(event.yul), requireNotNull(event.octave))
            }.getOrDefault(note) else note
        }
        ScoreCell(displayNotes.ifEmpty { listOf(ScoreNote.Blank) }, "c$c-j$j",
            units.map { ScoreUnit(it.row, it.col, it.symbol, it.eventId) }, originalCell(index)?.lyric)
    } }, tempoMarks = displayed?.tempoMarks.orEmpty(), tempoRanges = displayed?.tempoRanges.orEmpty())
}
