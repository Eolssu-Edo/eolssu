package com.ssafy.eolssu.core.model

import kotlin.math.abs
import kotlin.math.expm1
import kotlin.math.ln
import kotlin.math.ln1p

/** A written display slot, including holds belonging to an earlier event. */
data class ScoreUnitKey(val cellId: String, val row: Int, val col: Int)

val ScoreDisplayUnit.key: ScoreUnitKey
    get() = ScoreUnitKey("c$column-j$row", unitRow, unitCol)

class ScorePerformanceSegment internal constructor(
    val occurrenceId: String,
    val unit: ScoreDisplayUnit,
    val startTick: Double,
    val endTick: Double,
    val startMs: Double,
    val endMs: Double,
    val sigimsae: List<String>,
    /** End/start BPM for a linear tempo ramp in this written slot; old sidecars are linear in time. */
    val tempoRatio: Double = 1.0,
    val accompanimentStartTick: Double = startTick,
    val accompanimentEndTick: Double = endTick,
) {
    fun sourceTickAt(mediaMs: Double): Double {
        val timeFraction = ((mediaMs - startMs) / (endMs - startMs)).coerceIn(0.0, 1.0)
        if (timeFraction == 0.0) return startTick
        if (timeFraction == 1.0) return endTick
        val fraction = if (abs(tempoRatio - 1) < 1e-9) timeFraction
            else expm1(ln(tempoRatio) * timeFraction) / (tempoRatio - 1)
        return startTick + (endTick - startTick) * fraction.coerceIn(0.0, 1.0)
    }

    fun mediaMsAt(sourceTick: Double): Double {
        val fraction = ((sourceTick - startTick) / (endTick - startTick)).coerceIn(0.0, 1.0)
        if (fraction == 0.0) return startMs
        if (fraction == 1.0) return endMs
        val timeFraction = if (abs(tempoRatio - 1) < 1e-9) fraction
            else ln1p((tempoRatio - 1) * fraction) / ln(tempoRatio)
        return startMs + (endMs - startMs) * timeFraction.coerceIn(0.0, 1.0)
    }
}

data class ScorePerformancePosition(val segment: ScorePerformanceSegment, val sourceTick: Double) {
    /** Imported balanced length marks can move the beat clock inside an unchanged display cell. */
    val accompanimentTick: Double
        get() = segment.accompanimentStartTick + (segment.accompanimentEndTick - segment.accompanimentStartTick) *
            ((sourceTick - segment.startTick) / (segment.endTick - segment.startTick))
}

/** Validated local preview sidecar. Written score events and scoring time remain unchanged. */
class ScorePerformance internal constructor(
    val sourceSha256: String,
    val audioSha256: String,
    val durationMs: Double,
    val segments: List<ScorePerformanceSegment>,
    private val sourceSongId: String,
    private val sourceScore: Score,
    private val sourceBpm: Int,
    internal val audioDurationMs: Long,
    internal val audioStartOffsetMs: Long,
) {
    val annotations: Map<ScoreUnitKey, List<String>> = segments.associate { it.unit.key to it.sigimsae }

    internal fun matches(song: Song): Boolean = song.id == sourceSongId && song.score == sourceScore && song.baseBpm == sourceBpm

    /** Media time is measured after the declared leading offset; trailing audio holds the final slot. */
    fun at(mediaMs: Double): ScorePerformancePosition {
        require(mediaMs.isFinite()) { "Preview position must be finite" }
        val time = mediaMs.coerceIn(0.0, durationMs)
        var low = 0
        var high = segments.lastIndex
        while (low < high) {
            val middle = (low + high) / 2
            if (time >= segments[middle].endMs) low = middle + 1 else high = middle
        }
        val segment = segments[low]
        return ScorePerformancePosition(segment, segment.sourceTickAt(time))
    }
}
