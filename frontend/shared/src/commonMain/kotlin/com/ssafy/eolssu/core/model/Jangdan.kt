package com.ssafy.eolssu.core.model

import kotlin.math.ceil

/**
 * 장단 구음 패턴. 공통 악보의 각 길이·정간 시간은 [Score] 원본이 정하며 이 패턴과 다를 수 있다.
 *
 * @property daegang 구음 칸의 묶음. 기존 샘플 악보에만 정간 대강 분절로도 사용한다.
 * @property beats 한 주기의 박 수. 속도(BPM)는 박 기준이다.
 * @property subdivision 한 박의 정간 수. 대강이 고르지 않은 장단은 1(BPM = 분당 정간 수)이다.
 * @property gueum 정간별 구음. 데이터가 없으면 null.
 * @property aliases 같은 장단을 쓰는 다른 곡·장단 이름(예: '타령 · 군악 · 계면가락도드리'). 없으면 null.
 */
data class Jangdan(
    val name: String,
    val daegang: List<Int>,
    val beats: Int,
    val subdivision: Int,
    val gueum: List<String>? = null,
    val aliases: String? = null,
) {
    init {
        require(daegang.isNotEmpty() && daegang.all { it > 0 }) { "대강은 1정간 이상이어야 한다: $daegang" }
        require(beats > 0 && subdivision > 0) { "박 수와 한 박의 정간 수는 1 이상이어야 한다" }
    }

    /** 한 각의 정간 수. */
    val gak: Int get() = daegang.sum()

    /** 각 대강이 시작하는 정간 번호. */
    val daegangStarts: List<Int> by lazy { daegang.runningFold(0) { acc, n -> acc + n }.dropLast(1) }

    /** 한 각을 세로 스크롤 없이 보이도록 나눠 그릴 때의 줄별 정간 수. [stripsOf] 참고. */
    val strips: List<Int> by lazy { stripsOf(daegang) }

    /** [row]번째 정간이 속한 대강 번호(0부터). */
    fun daegangIndexOfRow(row: Int): Int = daegangStarts.indexOfLast { row >= it }.coerceAtLeast(0)

    /** 구음 패턴 한 칸의 길이(ms). 공통 악보의 정간에는 Song.cellDurationMs를 쓴다. */
    fun cellMillis(bpm: Int): Double = 60_000.0 / bpm / subdivision

    companion object {
        /** 한 줄에 그리는 최대 정간 수. */
        const val MAX_STRIP_CELLS = 12
    }
}

/**
 * 한 각을 줄로 나눈 줄별 정간 수. 한 줄은 최대 [Jangdan.MAX_STRIP_CELLS] 정간이고, 대강 경계에서만 나누며,
 * 줄 수는 최소로 하되 가장 긴 줄이 가장 짧아지게 고른다. 대강 하나가 최대보다 길면 나누지 않는다.
 *
 * 예: 6 → [6], 10(3·2·2·3) → [10], 12(3·3·3·3) → [12], 16(11·5) → [11, 5], 20(6·4·4·6) → [10, 10]
 */
internal fun stripsOf(daegang: List<Int>): List<Int> {
    val total = daegang.sum()
    val stripCount = ceil(total.toDouble() / Jangdan.MAX_STRIP_CELLS).toInt()
    if (stripCount <= 1) return listOf(total)

    var best = listOf(total)
    var bestMax = Int.MAX_VALUE
    fun search(start: Int, left: Int, acc: List<Int>) {
        if (left == 1) {
            val sizes = acc + daegang.subList(start, daegang.size).sum()
            val max = sizes.max()
            if (max < bestMax) {
                best = sizes
                bestMax = max
            }
            return
        }
        for (end in start + 1..daegang.size - (left - 1)) {
            search(end, left - 1, acc + daegang.subList(start, end).sum())
        }
    }
    search(0, stripCount, emptyList())
    return best
}
