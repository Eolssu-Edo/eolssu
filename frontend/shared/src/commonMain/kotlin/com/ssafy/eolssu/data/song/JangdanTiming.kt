package com.ssafy.eolssu.data.song

/** Catalog pattern cells and BPM beats are distinct musical durations. Missing data stays unknown. */
fun jangdanCycleTicks(beatCount: Int, patternCellEighths: Int?, bpmUnit: String, ticksPerBeat: Long): Long? {
    val beatEighths = when (bpmUnit) { "QUARTER" -> 2; "DOTTED_QUARTER" -> 3; else -> return null }
    val cell = patternCellEighths?.takeIf { it in 1..64 } ?: return null
    if (beatCount !in 1..64 || ticksPerBeat !in 1..1_000_000_000L) return null
    val numerator = beatCount.toLong() * cell * ticksPerBeat
    return (numerator / beatEighths).takeIf { numerator % beatEighths == 0L && it in 1..1_000_000_000L }
}
