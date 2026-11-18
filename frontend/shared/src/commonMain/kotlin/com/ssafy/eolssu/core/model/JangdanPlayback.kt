package com.ssafy.eolssu.core.model

/** 검수한 악보 판본의 tick과 장단 한 주기를 잇는다. BPM/정간/구음 칸은 서로 다른 단위다. */
data class JangdanPlayback(
    val jangdanId: String,
    val name: String,
    val startTick: Long,
    val cycleTicks: Long,
    val beats: List<String>,
    val groups: List<Int>,
) {
    init {
        require(jangdanId.toLongOrNull()?.let { it > 0 } == true && name.isNotBlank())
        require(startTick >= 0 && cycleTicks in 1..1_000_000_000)
        require(beats.size in 1..64 && groups.isNotEmpty() && groups.all { it > 0 })
        require(groups.sumOf { it.toLong() } == beats.size.toLong())
    }

    val cellTicks: Double get() = cycleTicks.toDouble() / beats.size
    val displayPattern: Jangdan get() = Jangdan(name, groups, beats.size, 1, beats)

    fun cellAt(tick: Double): Int = if (tick < startTick) -1 else
        (((tick - startTick) % cycleTicks) / cellTicks).toInt().coerceIn(beats.indices)
}
