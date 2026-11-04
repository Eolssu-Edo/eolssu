package com.ssafy.eolssu.backend.feature.song

import com.ssafy.eolssu.backend.feature.jangdan.infrastructure.persistence.JangdanRow
import com.ssafy.eolssu.backend.feature.song.domain.PublishedSongScore
import com.ssafy.eolssu.backend.feature.song.infrastructure.catalog.ReviewedJangdanPlayback
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import tools.jackson.databind.json.JsonMapper

/** Opt-in evidence check: directory contains the 107 original CAT-04 responses as {songId}.json. */
@EnabledIfEnvironmentVariable(named = "JANGDAN_SNAPSHOT_DIR", matches = ".+")
class JangdanPlaybackSnapshotTests {
    @Test fun `all original score revisions and accompaniment decisions match the production manifest`() {
        val json = JsonMapper.builder().build()
        val manifest = json.readTree(requireNotNull(javaClass.getResourceAsStream("/catalog/jangdan-playback.json")))
        val resolver = ReviewedJangdanPlayback(json, { id ->
            manifest.path("patterns").firstOrNull { it.path("jangdanId").asString() == id.toString() }?.let {
                JangdanRow(id, it.path("name").asString(), it.path("pattern").toString(), 66,
                    it.path("bpmUnit").asString(), null, LocalDateTime.MIN, LocalDateTime.MIN)
            }
        })
        val rows = manifest.path("scores").toList()
        assertEquals(107, rows.size)
        for (review in rows) {
            val id = review.path("songId").asString()
            val response = json.readTree(Path.of(System.getenv("JANGDAN_SNAPSHOT_DIR"), "$id.json").toFile()).path("result")
            val source = response.path("score")
            val stored = PublishedSongScore(id.toLong(), response.path("scoreVersion").intValue(), response.path("noteCount").intValue(),
                response.path("defaultBpm").intValue(), response.path("bpmUnit").asString(), source.toString())
            assertEquals(review.path("scoreVersion").intValue(), stored.scoreVersion, id)
            assertEquals(review.path("scoreFingerprint").asString(), ReviewedJangdanPlayback.fingerprint(source), id)
            assertEquals(review.path("status").asString() == "READY", resolver.resolve(stored) != null, id)
        }
    }
}
