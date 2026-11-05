package com.ssafy.eolssu.backend.feature.song.domain

/** Accompaniment uses current score ticks; catalog BPM never replaces the song clock. */
data class ScoreJangdanPlayback(
    val jangdanId: String,
    val name: String,
    val startTick: Long,
    val cycleTicks: Long,
    val pattern: JangdanPlaybackPattern,
)

data class JangdanPlaybackPattern(val beats: List<String>, val group: List<Int>)

fun interface SongJangdanPlaybackResolver {
    fun resolve(score: PublishedSongScore): ScoreJangdanPlayback?
}
