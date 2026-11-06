package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScoreCell
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongLevel
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
 * 편집 화면에서 정간 오른쪽에 작게 적는 시김새. [scoreName]은 공통 정간보 렌더러가 기호 그림을 찾는 이름이다.
 */
enum class Sigimsae(val scoreName: String) {
    Yoseong("요성표"),
    Toeseong("퇴성"),
    Chuseong("추성"),
    Jeonseong("전성"),
}

/** 편집기로 들어온 경로. 가져오기(EDIT-05·06)를 거쳤는지, 어떤 악보를 복사했는지에 따라 첫 악보와 안내가 다르다. */
sealed interface EditSource {
    /** 정간보·오선보 인식 결과에서 편집으로 왔다. [pendingChecks]는 아직 확인하지 않은 정간 수다. */
    data class Import(val kind: ImportKind, val hwang: HwangPitch, val pendingChecks: Int) : EditSource

    /** 제공 곡을 복사해 고친다. 원본은 그대로 둔다. */
    data class CopySong(val songId: String) : EditSource

    /** 내 악보를 복사해 고친다. 원본은 그대로 둔다. */
    data class CopySheet(val sheetId: String) : EditSource

    /** 내 악보 목록의 '편집'으로 그 악보를 직접 고친다. 이미 저장된 악보다. */
    data class Mine(val sheetId: String) : EditSource
}

/**
 * 편집 중인 정간보. `cells[i]`는 연주 순서 i번째 정간이고 원본의 가변 각 길이는 [columnSizes]로 보존한다.
 * 빈 목록은 빈 정간, 둘 이상이면 한 정간을 위→아래로 나눈 음이며 나눈 정간의 아직 비어 있는 자리는 [ScoreNote.Blank]다.
 *
 * @property sigimsae 정간 번호 → 그 정간 첫 음에 붙인 시김새. 율명이 있는 정간에만 붙는다.
 */
data class SheetDraft(
    val title: String,
    val jangdan: Jangdan?,
    val bpm: Int,
    val cells: List<List<ScoreNote>>,
    val sigimsae: Map<Int, Sigimsae> = emptyMap(),
    val columnSizes: List<Int>? = null,
    val sourceCellIds: List<String?>? = null,
    val original: CommonScoreData? = null,
) {
    init {
        require(cells.isNotEmpty() && (columnSizes?.let { it.all { n -> n > 0 } && it.sum() == cells.size } ?: (cells.size % requireNotNull(jangdan).gak == 0)))
        require(sourceCellIds == null || sourceCellIds.size == cells.size)
    }

    /** 한 각의 정간 수. */
    val gak: Int get() = columnSizes?.maxOrNull() ?: requireNotNull(jangdan).gak

    val sizes: List<Int> get() = columnSizes ?: List(cells.size / gak) { gak }

    val jangdanName: String get() = original?.music?.jangdan ?: jangdan?.name.orEmpty()

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
        solfege = null,
        score = if (original != null) renderEditingScore() else Score(cells.chunked(gak).map { column -> column.map { ScoreCell(it.ifEmpty { listOf(ScoreNote.Blank) }) } }),
    )

    /** 원본 시김새는 모든 시작 음 자리(row/col)에 보존하고, 예시 악보만 정간 첫 음에 붙인다. */
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
        }.getOrNull()?.let { return it }
        return sigimsae.mapKeys { (index, _) -> ScoreUnitKey("c${columnOf(index)}-j${rowOf(index)}", 0, 0) }
            .mapValues { (_, value) -> listOf(value.scoreName) }
    }

    /**
     * [index] 정간에 [note]를 적는다. 나눈 정간에 빈 자리가 있으면 위에서부터 채우고, 아니면 정간을 [note] 하나로 바꾼다.
     * 돌려주는 [Boolean]은 이 정간을 다 채워 다음 정간으로 넘어가도 되는지다.
     */
    fun put(index: Int, note: ScoreNote): Pair<SheetDraft, Boolean> {
        val current = cells[index]
        val blank = current.indexOf(ScoreNote.Blank)
        val updated = if (current.size > 1 && blank >= 0) current.toMutableList().also { it[blank] = note } else listOf(note)
        val keepSigimsae = updated.any { it.isPitch() }
        val next = copy(
            cells = cells.replace(index, updated),
            sigimsae = if (keepSigimsae) sigimsae else sigimsae - index,
        )
        return next to (ScoreNote.Blank !in updated)
    }

    /** [index] 정간을 비우고 시김새도 지운다. */
    fun erase(index: Int): SheetDraft = copy(cells = cells.replace(index, emptyList()), sigimsae = sigimsae - index)

    /** 율명이 있는 정간에만 시김새를 붙인다. [value]가 null이면 지운다. 바뀌지 않으면 그대로 돌려준다. */
    fun withSigimsae(index: Int, value: Sigimsae?): SheetDraft = when {
        value == null -> if (index in sigimsae) copy(sigimsae = sigimsae - index) else this
        cells[index].none { it.isPitch() } || sigimsae[index] == value -> this
        else -> copy(sigimsae = sigimsae + (index to value))
    }

    /** 한 정간을 하나 더 나눈다(최대 [MAX_SPLIT]). 새 자리는 비워 두고 위에서부터 채운다. */
    fun split(index: Int): SheetDraft {
        val current = cells[index]
        if (current.size >= MAX_SPLIT) return this
        val updated = if (current.isEmpty()) listOf(ScoreNote.Blank, ScoreNote.Blank) else current + ScoreNote.Blank
        return copy(cells = cells.replace(index, updated))
    }

    /** 나눈 정간을 첫 음 하나로 합친다. 첫 음이 비어 있으면 빈 정간이 된다. */
    fun merge(index: Int): SheetDraft {
        val current = cells[index]
        if (current.size <= 1) return this
        val first = current.first().takeIf { it != ScoreNote.Blank }
        return copy(cells = cells.replace(index, listOfNotNull(first)))
    }

    /** [column] 각 왼쪽(다음 순서)에 빈 각을 하나 넣는다. 뒤 정간의 시김새 번호도 함께 민다. */
    fun addColumnAfter(column: Int): SheetDraft {
        val count = sizes[column]
        val at = indexOf(column, 0) + count
        val inserted = cells.subList(0, at) + List(count) { emptyList() } + cells.subList(at, cells.size)
        return copy(cells = inserted,
            columnSizes = columnSizes?.toMutableList()?.also { it.add(column + 1, count) },
            sourceCellIds = sourceCellIds?.let { it.take(at) + List(count) { null } + it.drop(at) },
            sigimsae = sigimsae.mapKeys { (index, _) -> if (index >= at) index + count else index })
    }

    /** [column] 각을 지운다. 각이 하나뿐이면 지우지 않는다. */
    fun deleteColumn(column: Int): SheetDraft {
        if (columnCount <= 1) return this
        val from = indexOf(column, 0)
        val to = from + sizes[column]
        return copy(
            cells = cells.subList(0, from) + cells.subList(to, cells.size),
            columnSizes = columnSizes?.filterIndexed { i, _ -> i != column },
            sourceCellIds = sourceCellIds?.let { it.take(from) + it.drop(to) },
            sigimsae = sigimsae.filterKeys { it !in from until to }.mapKeys { (index, _) -> if (index >= to) index - (to - from) else index },
        )
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
        internal const val DRAFT_SONG_ID = "sheet-draft"
    }
}

/** 율명(옥타브 포함)인지. 지속(ㅡ)·쉼(△)·빈 자리는 아니다. */
internal fun ScoreNote.isPitch(): Boolean = this is ScoreNote.Pitch || this is ScoreNote.SourcePitch

private fun <T> List<T>.replace(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }
