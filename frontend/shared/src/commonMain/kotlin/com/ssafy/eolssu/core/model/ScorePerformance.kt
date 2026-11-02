package com.ssafy.eolssu.core.model

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
)

data class ScorePerformancePosition(val segment: ScorePerformanceSegment, val sourceTick: Double)

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
        val fraction = ((time - segment.startMs) / (segment.endMs - segment.startMs)).coerceIn(0.0, 1.0)
        return ScorePerformancePosition(segment, segment.startTick + (segment.endTick - segment.startTick) * fraction)
    }
}
