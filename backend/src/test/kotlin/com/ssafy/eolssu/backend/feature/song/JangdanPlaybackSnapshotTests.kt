package com.ssafy.eolssu.backend.feature.song

import com.ssafy.eolssu.backend.feature.jangdan.infrastructure.persistence.JangdanRow
import com.ssafy.eolssu.backend.feature.song.domain.PublishedSongScore
import com.ssafy.eolssu.backend.feature.song.infrastructure.catalog.ScoreGridJangdanPlayback
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import tools.jackson.databind.json.JsonMapper

/** Opt-in evidence check: directory contains the 107 original CAT-04 responses as {songId}.json. */
@EnabledIfEnvironmentVariable(named = "JANGDAN_SNAPSHOT_DIR", matches = ".+")
class JangdanPlaybackSnapshotTests {
    @Test fun `current grid rules preserve decisions and cycles for the 107 reviewed snapshots`() {
        val json = JsonMapper.builder().build()
        val manifest = json.readTree(requireNotNull(javaClass.getResourceAsStream("/catalog/jangdan-playback.json")))
        val resolver = ScoreGridJangdanPlayback(json) { name ->
            manifest.path("patterns").firstOrNull { it.path("name").asString() == name }?.let {
                JangdanRow(it.path("jangdanId").asString().toLong(), name, it.path("pattern").toString(), 66,
                    it.path("bpmUnit").asString(), null, LocalDateTime.MIN, LocalDateTime.MIN)
            }
        }
        val rows = manifest.path("scores").toList()
        assertEquals(107, rows.size)
        for (review in rows) {
            val id = review.path("songId").asString()
            val response = json.readTree(Path.of(System.getenv("JANGDAN_SNAPSHOT_DIR"), "$id.json").toFile()).path("result")
            val source = response.path("score")
            val stored = PublishedSongScore(id.toLong(), response.path("scoreVersion").intValue(), response.path("noteCount").intValue(),
                response.path("defaultBpm").intValue(), response.path("bpmUnit").asString(), source.toString())
            assertEquals(review.path("scoreVersion").intValue(), stored.scoreVersion, id)
            val playback = resolver.resolve(stored)
            if (review.path("status").asString() == "READY") {
                assertNotNull(playback, id)
                assertEquals(review.path("jangdanId").asString(), playback.jangdanId, id)
                assertEquals(review.path("startTick").longValue(), playback.startTick, id)
                assertEquals(review.path("cycleTicks").longValue(), playback.cycleTicks, id)
            } else {
                assertNull(playback, id)
            }
        }
    }
}
