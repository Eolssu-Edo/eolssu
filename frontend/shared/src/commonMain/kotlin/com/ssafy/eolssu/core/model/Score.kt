package com.ssafy.eolssu.core.model

/** 정간 하나에 적힌 음 하나. 율명이거나 앞 음 지속(ㅡ)·쉼(△) 기호다. */
sealed interface ScoreNote {
    /** 악보에 적는 글자. 서버·데이터의 정간보 값과 같다. */
    val symbol: String

    data class Pitch(val yul: Yul) : ScoreNote {
        override val symbol: String get() = yul.symbol
    }

    /** 원본 음고의 표시 모델. 선택·확정 저장 가능 여부는 악기별 지원음 계약에서 별도로 검증한다. */
    data class SourcePitch(val yul: String, val octave: Int) : ScoreNote {
        init {
            require(yul in BaseYulNames && octave in -6..5) { "지원하지 않는 율명·옥타브: $yul/$octave" }
        }
        val baseYul: Yul get() = Yul.entries[BaseYulNames.indexOf(yul)]
        val hangul: String get() = octavePrefix(octave) + yul
        val fullName: String get() = if (octave in -2..2) octavePrefix(octave) + baseYul.fullName
            else "${baseYul.fullName} · 옥타브 ${if (octave > 0) "+" else ""}$octave"
        override val symbol: String get() = SourceHanja[octave]?.get(BaseYulNames.indexOf(yul))?.ifEmpty { hangul } ?: "?"
    }

    /** 앞 음을 끊지 않고 이어서 소리 낸다. */
    data object Hold : ScoreNote {
        override val symbol = "ㅡ"
    }

    /** 이 자리에서는 소리를 내지 않는다. */
    data object Rest : ScoreNote {
        override val symbol = "△"
    }

    /** 원본의 빈 자리. 앞 이벤트와의 연결은 ScoreUnit.eventId가 보존한다. */
    data object Blank : ScoreNote { override val symbol = "" }

    companion object {
        fun fromSymbol(symbol: String): ScoreNote? = when (symbol) {
            Hold.symbol -> Hold
            Rest.symbol -> Rest
            else -> Yul.fromSymbol(symbol)?.let(::Pitch)
        }
    }
}

private val BaseYulNames = listOf("황", "대", "태", "협", "고", "중", "유", "임", "이", "남", "무", "응")
private fun octavePrefix(octave: Int): String = when (octave) { -2 -> "하배"; -1 -> "배"; 1 -> "청"; 2 -> "중청"; else -> "" }

// data/src/scores/review_page.html HANJA + 보충 평면의 하배이·배유·중청대. 표시는 음고를 바꾸지 않는다.
private val SourceHanja = mapOf(
    0 to "黃大太夾姑仲蕤林夷南無應".map(Char::toString),
    1 to listOf("潢", "汏", "汰", "浹", "㴌", "㳞", "㶋", "淋", "洟", "湳", "潕", "㶐"),
    2 to listOf("㶂", "𣴘", "㳲", "㴺", "㵈", "㴢", "㶙", "㵉", "㴣", "㵜", "㶃", "㶝"),
    -1 to listOf("僙", "㐲", "㑀", "俠", "㑬", "㑖", "𠐭", "㑣", "侇", "㑲", "㒇", "㒣"),
    -2 to listOf("㣴", "㣕", "㣖", "㣣", "㣨", "㣡", "㣸", "㣩", "𢓡", "㣮", "㣳", "㣹"),
)

/** 기존 운지/계이름 데이터가 정확히 같은 옥타브를 지원할 때만 연결한다. */
val ScoreNote.fingeringYul: Yul? get() = when (this) {
    is ScoreNote.Pitch -> yul
    is ScoreNote.SourcePitch -> Yul.fromSymbol(symbol)
    else -> null
}

/** 원본 정간 내부의 위→아래 row, 같은 줄 왼→오른쪽 col. 이벤트와 표시 자리는 다대일이다. */
data class ScoreUnit(val row: Int, val col: Int, val symbol: String, val eventId: String)

/** 정간 하나. 공통 악보는 [units]의 row/col로, 기존 샘플은 [notes]를 위→아래로 나눈다. */
data class ScoreCell(
    val notes: List<ScoreNote>,
    val id: String? = null,
    val units: List<ScoreUnit> = emptyList(),
    val lyric: String? = null,
) {
    init {
        require(notes.isNotEmpty()) { "정간에는 음이 하나 이상 있어야 한다" }
    }

    constructor(vararg notes: ScoreNote) : this(notes.toList())
}

/**
 * 정간보. `columns[c]`는 원본의 각 하나, `columns[c][r]`은 그 각의 r번째 정간(위 → 아래)이다.
 * 원본 각의 길이는 가변이며 장단 구음 한 주기의 칸 수와 같다고 추정하지 않는다.
 *
 * 각 0이 가장 오른쪽에 그려지고 다음 각은 그 왼쪽에 놓인다. 화면 배치만 오른쪽 → 왼쪽이고
 * 데이터와 연주 순서(각 0 위 → 아래, 각 1 위 → 아래, …)는 뒤집지 않는다.
 */
data class Score(
    val columns: List<List<ScoreCell>>,
    val timing: ScoreTiming? = null,
    val source: ScoreSource? = null,
    val events: List<ScoreEvent>? = null,
    val tempoMarks: Map<ScoreUnitKey, List<HanbaeMark>> = emptyMap(),
    /** 저장 표현과 분리된 숫자 가속 안내. 음표·재생 타임라인에는 영향을 주지 않는다. */
    val tempoRanges: List<ScoreTempoRange> = emptyList(),
) {
    /** 정간 수. 연주 시간 계산의 단위다. */
    val totalUnits: Int get() = columns.sumOf { it.size }
}

/** 끝 위치는 목표 속도에 도달하는 정간 시작이며 null이면 악보 끝이다. */
data class ScoreTempoRange(
    val start: ScorePosition,
    val end: ScorePosition?,
    val startBpm: Double,
    val targetBpm: Double,
)

enum class ScoreBpmUnit { QUARTER, DOTTED_QUARTER, JEONGGAN, JANGDAN_BEAT }
data class ScoreTiming(val hwangHz: Double, val jangdan: String, val bpmUnit: ScoreBpmUnit, val ticksPerBeat: Int, val ticksPerJeonggan: Int)
data class ScoreSource(
    val schemaVersion: Int, val title: String, val instrument: String, val part: Int,
    val sourceId: String, val scoreVersion: Int, val provenance: ScoreProvenance,
)
data class ScoreProvenance(val sourceId: String, val jgbSha256: String, val review: String, val converter: String)

/**
 * 연주 순서로 펼친 음 하나. 화면 위치(각·정간·정간 안 순서)와 시간상 위치를 함께 가진다.
 *
 * @property index 연주 순서(0부터).
 * @property id 공통 악보의 원본 `c{각}-j{정간}-r{줄}-u{칸}`. 기존 샘플만 songId 접두사를 쓴다.
 * @property unitStart 곡 처음부터 이 음이 시작하기까지의 정간 수. 나눈 정간의 음은 소수가 된다.
 * @property unitDuration 표시 위치 계산용 정간 길이. 공통 악보는 durationTick/ticksPerJeonggan이고 이음으로 여러 정간에 걸칠 수 있다.
 */
data class ScoreEvent(
    val index: Int,
    val id: String,
    val column: Int,
    val row: Int,
    val sub: Int,
    val subCount: Int,
    val note: ScoreNote,
    val unitStart: Double,
    val unitDuration: Double,
    /** 원본 events의 정수 tick. 샘플 악보에는 없다. */
    val startTick: Long? = null,
    val durationTick: Long? = null,
    /** NOTE만 세는 0 기반 인덱스. REST는 null이며 재생 index와 다르다. */
    val noteIndex: Int? = null,
    val cellId: String? = null,
    val breathAfter: Boolean = false,
    val sigimsae: List<String> = emptyList(),
)

fun scoreEventId(songId: String, column: Int, row: Int, sub: Int): String = "$songId-c$column-r$row-s$sub"

/** 연주 순서대로 펼친 음 목록. */
fun Score.timeline(songId: String): List<ScoreEvent> {
    events?.let { return it }
    val events = mutableListOf<ScoreEvent>()
    var unit = 0.0
    columns.forEachIndexed { column, cells ->
        cells.forEachIndexed { row, cell ->
            val duration = 1.0 / cell.notes.size
            cell.notes.forEachIndexed { sub, note ->
                events += ScoreEvent(
                    index = events.size,
                    id = scoreEventId(songId, column, row, sub),
                    column = column,
                    row = row,
                    sub = sub,
                    subCount = cell.notes.size,
                    note = note,
                    unitStart = unit,
                    unitDuration = duration,
                )
                unit += duration
            }
        }
    }
    return events
}

/** 표시 자리 하나. [event]는 원본 재생 이벤트이며 이음마다 새 이벤트를 만들지 않는다. */
data class ScoreDisplayUnit(
    val column: Int, val row: Int, val unitRow: Int, val unitCol: Int,
    val symbol: String, val note: ScoreNote, val event: ScoreEvent,
    val unitStart: Double, val unitDuration: Double,
)

fun Score.displayUnits(timeline: List<ScoreEvent>): List<ScoreDisplayUnit> {
    if (events == null) return timeline.map { e ->
        ScoreDisplayUnit(e.column, e.row, e.sub, 0, e.note.symbol, e.note, e, e.unitStart, e.unitDuration)
    }
    val byId = timeline.associateBy { it.id }
    var cellOffset = 0
    return buildList {
        columns.forEachIndexed { column, cells ->
            cells.forEachIndexed { row, cell ->
                val rows = cell.units.groupBy { it.row }.entries.sortedBy { it.key }
                rows.forEachIndexed { r, entry ->
                    val units = entry.value
                    units.sortedBy { it.col }.forEachIndexed { c, unit ->
                        val event = byId.getValue(unit.eventId)
                        val note = when (unit.symbol) { "" -> ScoreNote.Blank; "-" -> ScoreNote.Hold; "쉼" -> ScoreNote.Rest; else -> event.note }
                        add(ScoreDisplayUnit(column, row, unit.row, unit.col, unit.symbol, note, event,
                            cellOffset + r.toDouble() / rows.size + c.toDouble() / rows.size / units.size,
                            1.0 / rows.size / units.size))
                    }
                }
                cellOffset++
            }
        }
    }
}

/** 원본 이벤트가 여러 정간에 걸쳐 이어져도 현재 표시 자리를 찾는다. */
fun Song.displayUnitAt(index: Int, fraction: Float = 0f): ScoreDisplayUnit? {
    val event = timeline.getOrNull(index) ?: return null
    val position = event.unitStart + event.unitDuration * fraction.coerceIn(0f, 1f)
    return displayUnits.lastOrNull { it.unitStart <= position && it.event.index == index }
        ?: displayUnits.firstOrNull { it.event.index == index }
}
