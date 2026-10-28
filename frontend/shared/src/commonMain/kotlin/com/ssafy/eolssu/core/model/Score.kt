package com.ssafy.eolssu.core.model

/** 정간 하나에 적힌 음 하나. 율명이거나 앞 음 지속(ㅡ)·쉼(△) 기호다. */
sealed interface ScoreNote {
    /** 악보에 적는 글자. 서버·데이터의 정간보 값과 같다. */
    val symbol: String

    data class Pitch(val yul: Yul) : ScoreNote {
        override val symbol: String get() = yul.symbol
    }

    /** 앞 음을 끊지 않고 이어서 소리 낸다. */
    data object Hold : ScoreNote {
        override val symbol = "ㅡ"
    }

    /** 이 자리에서는 소리를 내지 않는다. */
    data object Rest : ScoreNote {
        override val symbol = "△"
    }

    companion object {
        fun fromSymbol(symbol: String): ScoreNote? = when (symbol) {
            Hold.symbol -> Hold
            Rest.symbol -> Rest
            else -> Yul.fromSymbol(symbol)?.let(::Pitch)
        }
    }
}

/** 정간 하나. 음이 여럿이면 정간을 나눠 위 → 아래 순서로 연주한다. */
data class ScoreCell(val notes: List<ScoreNote>) {
    init {
        require(notes.isNotEmpty()) { "정간에는 음이 하나 이상 있어야 한다" }
    }

    constructor(vararg notes: ScoreNote) : this(notes.toList())
}

/**
 * 정간보. `columns[c]`는 각(장단 한 주기) 하나, `columns[c][r]`은 그 각의 r번째 정간(위 → 아래)이다.
 *
 * 각 0이 가장 오른쪽에 그려지고 다음 각은 그 왼쪽에 놓인다. 화면 배치만 오른쪽 → 왼쪽이고
 * 데이터와 연주 순서(각 0 위 → 아래, 각 1 위 → 아래, …)는 뒤집지 않는다.
 */
data class Score(val columns: List<List<ScoreCell>>) {
    /** 정간 수. 연주 시간 계산의 단위다. */
    val totalUnits: Int get() = columns.sumOf { it.size }
}

/**
 * 연주 순서로 펼친 음 하나. 화면 위치(각·정간·정간 안 순서)와 시간상 위치를 함께 가진다.
 *
 * @property index 연주 순서(0부터).
 * @property id 평가 오류 위치·구간 선택·기록 재생 위치가 함께 쓰는 식별자 `{songId}-c{각}-r{정간}-s{정간 안 순서}`.
 * @property unitStart 곡 처음부터 이 음이 시작하기까지의 정간 수. 나눈 정간의 음은 소수가 된다.
 * @property unitDuration 이 음의 길이(정간 수). 정간 하나를 음 수로 똑같이 나눈다.
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
)

fun scoreEventId(songId: String, column: Int, row: Int, sub: Int): String = "$songId-c$column-r$row-s$sub"

/** 연주 순서대로 펼친 음 목록. */
fun Score.timeline(songId: String): List<ScoreEvent> {
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
