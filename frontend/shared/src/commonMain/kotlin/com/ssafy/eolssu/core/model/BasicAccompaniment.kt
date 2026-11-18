package com.ssafy.eolssu.core.model

/** 악보의 BPM 단위와 독립적인 반주 한 칸의 절대 음가. */
enum class BasicPulseUnit(val eighths: Int, val symbol: String) {
    QUARTER(2, "♩"), DOTTED_QUARTER(3, "♩."), EIGHTH(1, "♪")
}

enum class BasicAccompanimentPreset(val pulseUnit: BasicPulseUnit, val groups: List<Int>, val label: String) {
    TwoQuarter(BasicPulseUnit.QUARTER, listOf(2), "2박 · ♩"),
    ThreeQuarter(BasicPulseUnit.QUARTER, listOf(3), "3박 · ♩"),
    TwoDottedQuarter(BasicPulseUnit.DOTTED_QUARTER, listOf(2), "2박 · ♩."),
    FiveTwoThree(BasicPulseUnit.EIGHTH, listOf(2, 3), "5/8 · 2+3"),
    FiveThreeTwo(BasicPulseUnit.EIGHTH, listOf(3, 2), "5/8 · 3+2"),
    SevenTwoTwoThree(BasicPulseUnit.EIGHTH, listOf(2, 2, 3), "7/8 · 2+2+3"),
    SevenTwoThreeTwo(BasicPulseUnit.EIGHTH, listOf(2, 3, 2), "7/8 · 2+3+2"),
    SevenThreeTwoTwo(BasicPulseUnit.EIGHTH, listOf(3, 2, 2), "7/8 · 3+2+2"),
}

/** 카탈로그 장단의 ID를 만들지 않고 악보마다 저장하는 기본 박 반주. */
data class BasicAccompaniment(val pulseUnit: BasicPulseUnit, val groups: List<Int>, val startTick: Long) {
    init {
        require(startTick >= 0)
        require(BasicAccompanimentPreset.entries.any { it.pulseUnit == pulseUnit && it.groups == groups })
    }

    val preset: BasicAccompanimentPreset get() = BasicAccompanimentPreset.entries.first { it.pulseUnit == pulseUnit && it.groups == groups }
    val displayPattern: Jangdan get() {
        val starts = groups.runningFold(0, Int::plus).dropLast(1)
        return Jangdan("기본 박 반주 · ${preset.label}", groups, groups.sum(), 1,
            List(groups.sum()) { index -> if (index == 0) "덩" else if (index in starts) "쿵" else "덕" })
    }

    /** 분수 tick을 허용한다. 반주 때문에 원본 악보의 정수 tick을 늘리지 않는다. */
    fun pulseTicks(timing: ScoreTiming): Double? {
        val sourceEighths = when (timing.bpmUnit) {
            ScoreBpmUnit.QUARTER -> 2
            ScoreBpmUnit.DOTTED_QUARTER -> 3
            else -> return null
        }
        return timing.ticksPerBeat.takeIf { it > 0 }?.toDouble()?.times(pulseUnit.eighths)?.div(sourceEighths)
    }

    fun cellAt(tick: Double, timing: ScoreTiming): Int {
        val pulse = pulseTicks(timing) ?: return -1
        if (!tick.isFinite() || tick < startTick) return -1
        return (((tick - startTick) / pulse) % groups.sum()).toInt().coerceIn(0, groups.sum() - 1)
    }
}
