package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScoreCell
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.ui.score.RegisteredSigimsaeNames
import com.ssafy.eolssu.core.ui.score.scoreSigimsaeName
import com.ssafy.eolssu.data.song.CommonScoreData

/** EDIT-01에서 고르는 가져오기 종류. [routeId]는 화면 이동 경로에 쓴다. */
enum class ImportKind(val routeId: String) {
    Jeongganbo("jg"),
    Staff("staff");

    companion object {
        fun fromRouteId(id: String?): ImportKind = entries.firstOrNull { it.routeId == id } ?: Jeongganbo
    }
}

/**
 * 오선보를 율명으로 바꿀 때 황(黃)으로 읽을 음. [midi]는 MIDI 음 번호다.
 * 단소 기본값은 E♭4이고, 대금·소금의 기본값은 아직 정하지 않았다(2차 와이어프레임 인계).
 */
enum class HwangPitch(val midi: Int, val label: String) {
    Eb4(63, "E♭4"),
    D4(62, "D4"),
    E4(64, "E4"),
    F4(65, "F4"),
    G4(67, "G4");

    companion object {
        val Default = Eb4

        fun fromRouteId(id: String?): HwangPitch = entries.firstOrNull { it.name == id } ?: Default
    }
}

/**
 * 편집 화면에서 율명 오른쪽에 작게 적는 시김새. [scoreName]은 공통 악보 `events[].sigimsae`의 글자 그대로이고,
 * 공통 정간보 렌더러가 이 이름([name])으로 기호 그림을 찾는다.
 */
data class Sigimsae(val scoreName: String) {
    /** 렌더러 표기 이름. 원본의 `이름@변형` 꼴은 앞부분만 본다. */
    val name: String get() = scoreSigimsaeName(scoreName)

    companion object {
        val Yoseong = Sigimsae("요성표")
        val Toeseong = Sigimsae("퇴성")
        val Chuseong = Sigimsae("추성")
        val Jeonseong = Sigimsae("전성")

        /** 입력판 목록: 처음부터 있던 네 가지 뒤에 공통 렌더러에 그림이 등록된 나머지 시김새를 등록 순서대로 둔다. */
        val entries: List<Sigimsae> = (listOf(Yoseong, Toeseong, Chuseong, Jeonseong) + RegisteredSigimsaeNames.map(::Sigimsae)).distinct()
    }
}

/** 정간 [cell]의 [sub]번째 소박(위→아래, 같은 줄은 왼→오른쪽). 나누지 않은 정간은 0이다. */
data class NoteSlot(val cell: Int, val sub: Int = 0)

/** 편집기로 들어온 경로. 가져오기(EDIT-05·06)를 거쳤는지, 어떤 악보를 복사했는지에 따라 첫 악보와 안내가 다르다. */
sealed interface EditSource {
    /** 정간보·오선보 인식 결과에서 편집으로 왔다. [pendingChecks]는 아직 확인하지 않은 정간 수다. */
    data class Import(val kind: ImportKind, val hwang: HwangPitch, val pendingChecks: Int) : EditSource

    /** 제공 곡을 복사해 고친다. 원본은 그대로 둔다. */
    data class CopySong(val songId: String) : EditSource

    /** 내 악보를 복사해 고친다. 원본은 그대로 둔다. */
    data class CopySheet(val sheetId: String) : EditSource

    /** 내 악보 편집. [practiceVersion]이 있으면 연습 중인 확정 악보로 음원 받기 화면을 연다. */
    data class Mine(val sheetId: String, val practiceVersion: Int? = null) : EditSource
}

/**
 * 편집 중인 정간보. `cells[i]`는 연주 순서 i번째 정간이고 원본의 가변 각 길이는 [columnSizes]로 보존한다.
 * 빈 목록은 빈 정간, 둘 이상이면 한 정간을 위→아래로 나눈 음이며 나눈 정간의 아직 비어 있는 자리는 [ScoreNote.Blank]다.
 *
 * @property sigimsae 소박 자리 → 그 소박에 붙인 시김새(붙인 순서, 최대 [MAX_SIGIMSAE]개). 율명이 있는 소박에만 붙는다.
 */
data class SheetDraft(
    val title: String,
    val jangdan: Jangdan?,
    val bpm: Int,
    val cells: List<List<ScoreNote>>,
    val sigimsae: Map<NoteSlot, List<Sigimsae>> = emptyMap(),
    val columnSizes: List<Int>? = null,
    val sourceCellIds: List<String?>? = null,
    val original: CommonScoreData? = null,
    val jangdanId: String? = null,
    internal val editedJangdanName: String? = null,
    val authoring: kotlinx.serialization.json.JsonObject? = null,
    /** Original-event IDs in [original] to pre-adaptation pitches for repeated edits in this session. */
    internal val adaptationBaseline: Map<String, Int> = emptyMap(),
    val tempoExpressions: kotlinx.serialization.json.JsonObject? = null,
    val tempoReferenceBpm: Int? = null,
) {
    init {
        require(cells.isNotEmpty() && (columnSizes?.let { it.all { n -> n > 0 } && it.sum() == cells.size } ?: (cells.size % requireNotNull(jangdan).gak == 0)))
        require(sourceCellIds == null || sourceCellIds.size == cells.size)
    }

    /** 한 각의 정간 수. */
    val gak: Int get() = columnSizes?.maxOrNull() ?: requireNotNull(jangdan).gak

    val sizes: List<Int> get() = columnSizes ?: List(cells.size / gak) { gak }

    val jangdanName: String get() = editedJangdanName ?: original?.music?.jangdan ?: jangdan?.name.orEmpty()

    /** 요청 장단으로 시간을 추정한 정간보만 인식 원본의 격자로 다시 판단한다. */
    internal val inferredJeongganSizes: List<Int>? by lazy { inferredJeongganColumnSizes() }

    internal val originalCells: Map<String, CommonScoreData.Cell> by lazy {
        original?.columns?.flatMap { it.cells }?.associateBy { it.id }.orEmpty()
    }

    val columnCount: Int get() = sizes.size

    /** 빈 정간 번호(연주 순서). 나눈 정간은 모든 자리가 비었을 때만 빈 정간이다. */
    val emptyCells: List<Int> get() = cells.indices.filter { isEmptyCell(it) && !originalCellIsComplete(it) }

    /** 부분 입력 중인 분할도 확정 저장 전에는 채워야 한다. 원본의 빈 이음 표기와 구별한다. */
    val unfilledCells: List<Int> get() = cells.indices.filter { cells[it].isEmpty() ||
        (ScoreNote.Blank in cells[it] && !originalCellIsComplete(it)) }

    private fun originalCellIsComplete(index: Int): Boolean = originalCell(index)?.let { cell ->
        cell.units.isNotEmpty() && cell.units.all { it.symbol.isNotEmpty() || it.eventId.isNotEmpty() } && cell.editingNotes() == cells[index]
    } == true

    fun isEmptyCell(index: Int): Boolean = cells[index].all { it == ScoreNote.Blank }

    fun columnOf(index: Int): Int = sizes.runningFold(0, Int::plus).dropLast(1).indexOfLast { index >= it }.coerceAtLeast(0)

    fun rowOf(index: Int): Int = index - sizes.take(columnOf(index)).sum()

    fun indexOf(column: Int, row: Int): Int = sizes.take(column).sum() + row

    /** 공통 정간보 렌더러에 넘길 곡. 빈 정간은 [ScoreNote.Blank] 한 자리로 그린다. */
    fun toSong(): Song = Song(
        id = DRAFT_SONG_ID,
        title = title,
        level = SongLevel.Intro,
        jangdan = jangdan,
        baseBpm = bpm,
        description = "",
        evaluable = false,
        score = if (original != null) renderEditingScore() else Score(cells.chunked(gak).map { column -> column.map { ScoreCell(it.ifEmpty { listOf(ScoreNote.Blank) }) } }),
    )

    /** 원본 시김새는 모든 시작 음 자리(row/col)에 보존하고, 예시 악보는 소박 자리마다 붙인다. */
    fun annotations(): Map<ScoreUnitKey, List<String>> {
        if (original != null) runCatching {
            val document = toCommonScore()
            val events = document.events.associateBy { it.id }
            buildMap {
                document.columns.forEach { column -> column.cells.forEach { cell -> cell.units.forEach { unit ->
                    val id = "${cell.id}-r${unit.row}-u${unit.col}"
                    val signs = events[id]?.sigimsae.orEmpty()
                    if (unit.eventId == id && signs.isNotEmpty()) put(ScoreUnitKey(cell.id, unit.row, unit.col), signs)
                } } }
            }
        }.getOrNull()?.let { regular ->
            return (regular.keys + tempoAnnotations().keys).associateWith { key ->
                regular[key].orEmpty().filter { com.ssafy.eolssu.core.model.HanbaeMark.fromSymbol(it) == null } + tempoAnnotations()[key].orEmpty()
            }.filterValues { it.isNotEmpty() }
        }
        return sigimsae.entries.mapNotNull { (slot, value) -> unitKeyOf(slot.cell, slot.sub)?.let { it to value.map(Sigimsae::scoreName) } }.toMap()
    }

    /**
     * [index] 정간에 [note]를 적는다. 나눈 정간에 빈 자리가 있으면 위에서부터 채우고, 아니면 정간을 [note] 하나로 바꾼다.
     * 돌려주는 [Boolean]은 이 정간을 다 채워 다음 정간으로 넘어가도 되는지다.
     */
    fun put(index: Int, note: ScoreNote): Pair<SheetDraft, Boolean> {
        val current = cells[index]
        val blank = current.indexOf(ScoreNote.Blank)
        val updated = if (current.size > 1 && blank >= 0) current.toMutableList().also { it[blank] = note } else listOf(note)
        return withCell(index, updated) to (ScoreNote.Blank !in updated)
    }

    /**
     * 나눈 정간 [index]의 [sub]번째 소박만 [note]로 바꾼다. 다른 소박과 나눔은 그대로다.
     * 율명이 아니게 된 소박의 시김새는 지운다. 나누지 않은 정간이나 범위 밖이면 [put]과 같다.
     */
    fun putAt(index: Int, sub: Int, note: ScoreNote): SheetDraft {
        val current = cells[index]
        if (current.size <= 1 || sub !in current.indices) return put(index, note).first
        return withCell(index, current.toMutableList().also { it[sub] = note })
    }

    /** 나눈 정간의 [sub]번째 소박만 비운다(빈 자리로 남겨 나눔을 유지). 나누지 않은 정간이면 [erase]와 같다. */
    fun eraseAt(index: Int, sub: Int): SheetDraft {
        val current = cells[index]
        if (current.size <= 1 || sub !in current.indices) return erase(index)
        return withCell(index, current.toMutableList().also { it[sub] = ScoreNote.Blank })
    }

    /** [index] 정간을 [notes]로 바꾸고, 없어졌거나 율명이 아니게 된 소박의 시김새를 지운다. */
    private fun withCell(index: Int, notes: List<ScoreNote>): SheetDraft = copy(
        cells = cells.replace(index, notes),
        sigimsae = sigimsae.filterKeys { it.cell != index || notes.getOrNull(it.sub)?.isPitch() == true },
    )

    /** [index] 정간 [sub]번째 소박에 붙인 시김새. */
    fun sigimsaeAt(index: Int, sub: Int = 0): List<Sigimsae> = sigimsae[NoteSlot(index, sub)].orEmpty()

    /**
     * 시김새를 붙일 소박. 소박을 골랐으면 그 소박, 아니면 정간의 첫 율명 소박(없으면 0)이다.
     * 나누지 않은 정간은 항상 0이다.
     */
    fun sigimsaeSub(index: Int, selectedSub: Int?): Int =
        selectedSub?.takeIf { it in cells[index].indices } ?: cells[index].indexOfFirst { it.isPitch() }.coerceAtLeast(0)

    /** 악보에서 누른 표시 자리(row/col)가 [index] 정간의 몇 번째 소박인지. 찾지 못하면 0. */
    fun subIndexOf(index: Int, row: Int, col: Int): Int =
        layout(index).indexOfFirst { it.row == row && it.col == col }.coerceAtLeast(0)

    /** [index] 정간 [sub]번째 소박의 렌더러 표시 키. */
    fun unitKeyOf(index: Int, sub: Int): ScoreUnitKey? = layout(index).getOrNull(sub)?.let {
        ScoreUnitKey("c${columnOf(index)}-j${rowOf(index)}", it.row, it.col)
    }

    /** [index] 정간을 비우고 시김새도 지운다. */
    fun erase(index: Int): SheetDraft = copy(cells = cells.replace(index, emptyList()), sigimsae = sigimsae.filterKeys { it.cell != index })

    /** 율명이 있는 소박에만 시김새를 붙인다. [values]가 비면 지운다. 바뀌지 않으면 그대로 돌려준다. */
    fun withSigimsae(index: Int, values: List<Sigimsae>, sub: Int = 0): SheetDraft {
        val slot = NoteSlot(index, sub)
        return when {
            values.isEmpty() -> if (slot in sigimsae) copy(sigimsae = sigimsae - slot) else this
            cells[index].getOrNull(sub)?.isPitch() != true || sigimsae[slot] == values -> this
            else -> copy(sigimsae = sigimsae + (slot to values.take(MAX_SIGIMSAE)))
        }
    }

    /**
     * 고른 소박에 [value]가 없으면 끝에 붙이고(최대 [MAX_SIGIMSAE]개), 있으면 뗀다. 율명이 아닌 소박은 그대로다.
     * 음원 생성기가 함께 해석하지 못하는 시김새는 새로 고른 것으로 바꾼다(S15P31E102-1080).
     */
    fun toggleSigimsae(index: Int, value: Sigimsae, sub: Int = 0): SheetDraft {
        val current = sigimsaeAt(index, sub)
        val kept = current.filter { it.name == value.name || sigimsaeCompatible(it.name, value.name) }
        val next = if (current.any { it.name == value.name }) current.filterNot { it.name == value.name }
            else if (kept.size >= MAX_SIGIMSAE) return this else kept + value
        return withSigimsae(index, next, sub)
    }

    /** 고른 소박의 [position]번째 시김새만 뗀다. */
    fun removeSigimsae(index: Int, position: Int, sub: Int = 0): SheetDraft {
        val current = sigimsaeAt(index, sub)
        if (position !in current.indices) return this
        return withSigimsae(index, current.filterIndexed { i, _ -> i != position }, sub)
    }

    /** [index] 정간을 더 나눌 수 있는지. [split]이 바꾸지 않는 경우(이미 [MAX_SPLIT]개)는 false다. */
    fun canSplit(index: Int): Boolean = cells.getOrNull(index)?.let { it.size < MAX_SPLIT } == true

    /** [index] 정간이 나눠져 있어 [merge]로 합칠 수 있는지. */
    fun canMerge(index: Int): Boolean = cells.getOrNull(index)?.let { it.size > 1 } == true

    /** 한 정간을 하나 더 나눈다(최대 [MAX_SPLIT]). 새 자리는 비워 두고 위에서부터 채운다. */
    fun split(index: Int): SheetDraft {
        val current = cells[index]
        if (current.size >= MAX_SPLIT) return this
        val updated = if (current.isEmpty()) listOf(ScoreNote.Blank, ScoreNote.Blank) else current + ScoreNote.Blank
        return withCell(index, updated)
    }

    /** 나눈 정간을 첫 음 하나로 합친다. 첫 음이 비어 있으면 빈 정간이 된다. */
    fun merge(index: Int): SheetDraft {
        val current = cells[index]
        if (current.size <= 1) return this
        val first = current.first().takeIf { it != ScoreNote.Blank }
        return withCell(index, listOfNotNull(first))
    }

    /** [column] 각 왼쪽(다음 순서)에 빈 각을 하나 넣는다. 뒤 정간의 시김새 번호도 함께 민다. */
    fun addColumnAfter(column: Int): SheetDraft {
        val count = sizes[column]
        val at = indexOf(column, 0) + count
        val inserted = cells.subList(0, at) + List(count) { emptyList() } + cells.subList(at, cells.size)
        val next = copy(cells = inserted,
            columnSizes = columnSizes?.toMutableList()?.also { it.add(column + 1, count) },
            sourceCellIds = sourceCellIds?.let { it.take(at) + List(count) { null } + it.drop(at) },
            sigimsae = sigimsae.mapKeys { (slot, _) -> if (slot.cell >= at) slot.copy(cell = slot.cell + count) else slot },
            // 구간 빠르기·반복·각 경계 지속의 각 번호를 함께 민다(S15P31E102-1095).
            tempoExpressions = tempoExpressionsAfterColumnInsert(column))
        return remapBasicAccompaniment(next, atCell = at, insertedCells = count)
    }

    /** [column] 각을 지운다. 각이 하나뿐이면 지우지 않는다. */
    fun deleteColumn(column: Int): SheetDraft {
        if (columnCount <= 1) return this
        val from = indexOf(column, 0)
        val to = from + sizes[column]
        val next = copy(
            cells = cells.subList(0, from) + cells.subList(to, cells.size),
            columnSizes = columnSizes?.filterIndexed { i, _ -> i != column },
            sourceCellIds = sourceCellIds?.let { it.take(from) + it.drop(to) },
            sigimsae = sigimsae.filterKeys { it.cell !in from until to }
                .mapKeys { (slot, _) -> if (slot.cell >= to) slot.copy(cell = slot.cell - (to - from)) else slot },
            // 빠르기 구간이 걸친 각은 편집 상태가 미리 막는다. 나머지는 뒤 각 번호를 당긴다(S15P31E102-1095).
            tempoExpressions = tempoExpressionsAfterColumnDelete(column),
        )
        return remapBasicAccompaniment(next, atCell = from, removedCells = to - from)
    }

    /** 오선보에서 고른 음([index] 정간의 [sub]번째)을 [StaffPitchOrder] 안에서 [steps]만큼 올리거나 내린다. */
    fun shiftPitch(index: Int, sub: Int, steps: Int): SheetDraft {
        val current = cells[index]
        val note = current.getOrNull(sub) as? ScoreNote.Pitch ?: return this
        val position = StaffPitchOrder.indexOf(note.yul).takeIf { it >= 0 }
            ?: StaffPitchOrder.indexOfLast { it.ordinal <= note.yul.ordinal }.coerceAtLeast(0)
        val target = StaffPitchOrder[(position + steps).coerceIn(0, StaffPitchOrder.lastIndex)]
        if (target == note.yul) return this
        return copy(cells = cells.replace(index, current.toMutableList().also { it[sub] = ScoreNote.Pitch(target) }))
    }

    companion object {
        /** 한 정간을 나눌 수 있는 최대 음 수. */
        const val MAX_SPLIT = 3

        /** 한 정간 첫 음에 붙일 수 있는 시김새 수. 율명 오른쪽에 세로로 쌓아도 정간 높이 안에 들어가는 만큼이다. */
        const val MAX_SIGIMSAE = 3
        internal const val DRAFT_SONG_ID = "sheet-draft"
    }
}

/** 율명(옥타브 포함)인지. 지속(ㅡ)·쉼(△)·빈 자리는 아니다. */
internal fun ScoreNote.isPitch(): Boolean = this is ScoreNote.Pitch || this is ScoreNote.SourcePitch

private fun <T> List<T>.replace(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }
