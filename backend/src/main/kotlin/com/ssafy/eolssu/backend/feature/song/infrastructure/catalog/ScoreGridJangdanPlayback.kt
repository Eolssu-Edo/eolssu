package com.ssafy.eolssu.backend.feature.song.infrastructure.catalog

import com.ssafy.eolssu.backend.feature.jangdan.infrastructure.persistence.JangdanRow
import com.ssafy.eolssu.backend.feature.song.domain.JangdanPlaybackPattern
import com.ssafy.eolssu.backend.feature.song.domain.PublishedSongScore
import com.ssafy.eolssu.backend.feature.song.domain.ScoreJangdanPlayback
import com.ssafy.eolssu.backend.feature.song.domain.SongJangdanPlaybackResolver
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

/** Aligns the declared or separately reviewed jangdan to full cycles on the score grid. */
class ScoreGridJangdanPlayback(
    private val json: JsonMapper,
    private val findJangdan: (String) -> JangdanRow?,
) : SongJangdanPlaybackResolver {
    override fun resolve(score: PublishedSongScore): ScoreJangdanPlayback? {
        val source = json.readTree(score.scoreJson)
        val music = source.path("music")
        val name = scoreJangdanName(source, score.scoreVersion, requirePlayback = true) ?: return null
        val ticksPerBeat = music.path("ticksPerBeat").longValue()
        val ticksPerJeonggan = music.path("ticksPerJeonggan").longValue()
        if (ticksPerBeat <= 0 || ticksPerJeonggan <= 0) return null

        val cycleTicks = when (name) {
            "굿거리", "자진모리", "중중모리" -> when (score.bpmUnit) {
                "QUARTER" -> 6 * ticksPerBeat
                "DOTTED_QUARTER" -> 4 * ticksPerBeat
                else -> return null
            }
            "세마치" -> 3 * ticksPerJeonggan
            "중모리", "타령", "계면가락도드리" -> 12 * ticksPerJeonggan
            "엇모리" -> 10 * ticksPerJeonggan
            "단모리" -> 4 * ticksPerJeonggan
            else -> return null
        }

        if (cycleTicks !in 1..1_000_000_000) return null

        // The current score format has no separate downbeat marker for a pickup.
        val first = source.path("events").path(0)
        if (first.path("kind").stringValue() != "NOTE" || first.path("cellId").stringValue() != "c0-j0" ||
            !first.path("startTick").isIntegralNumber || first.path("startTick").longValue() != 0L) return null
        val columns = source.path("columns")
        if (!columns.isArray || columns.isEmpty || columns.any {
            val cells = it.path("cells")
            !cells.isArray || cells.isEmpty || cells.size() * ticksPerJeonggan % cycleTicks != 0L
        }) return null

        val jangdan = findJangdan(name)?.takeIf { it.id > 0 } ?: return null
        val pattern = try {
            json.readTree(jangdan.pattern)
        } catch (_: JacksonException) {
            return null
        }
        val beats = pattern.path("beats")
        val group = pattern.path("group")
        if (!beats.isArray || beats.size() !in 1..64 || beats.any { !it.isString } || beats.none { it.asString().isNotBlank() } ||
            !group.isArray || group.any { !it.isInt || it.intValue() <= 0 } ||
            group.sumOf { it.longValue() } != beats.size().toLong() || cycleTicks % beats.size() != 0L) return null

        return ScoreJangdanPlayback(jangdan.id.toString(), jangdan.name, 0, cycleTicks,
            JangdanPlaybackPattern(beats.toList().map { it.asString() }, group.toList().map { it.intValue() }))
    }
}
