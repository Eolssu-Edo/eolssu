package com.ssafy.eolssu.core.ui.score

import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.data.song.CommonScoreData
import com.ssafy.eolssu.feature.practice.PracticeMode
import com.ssafy.eolssu.feature.practice.PracticeUiState
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Offline replay of captured CAT-04 responses; does not request production or commit original scores. */
class SanjoJindoCapturedScoreTest {
    @Test fun capturedCatalogAnnotationsReachGlyphsInEveryPracticeMode() {
        val directory = System.getenv("EOLSSU_SIGIMSAE_FIXTURE_DIR")
        assumeTrue("Captured CAT-04 responses not supplied", !directory.isNullOrBlank())
        val expected = mapOf("85" to 49, "81" to 5, "94" to 0)
        for ((songId, count) in expected) {
            val response = Json.parseToJsonElement(File(requireNotNull(directory), "song-$songId.json").readText()).jsonObject
            val result = response.getValue("result").jsonObject
            val scoreJson = result.getValue("score").toString()
            val common = CommonScoreData.decode(scoreJson)
            val song = Song(songId, common.score.title, SongLevel.Intro, null, result.getValue("defaultBpm").jsonPrimitive.int, "", false,
                common.toScore(), scoreVersion = common.score.scoreVersion)
            assertEquals(count, common.events.sumOf { it.sigimsae.size }, songId)
            for (mode in PracticeMode.entries) {
                val annotations = PracticeUiState(song = song, mode = mode).scoreAnnotations
                assertEquals(count, annotations.values.sumOf { it.size }, "$songId $mode")
                annotations.forEach { (slot, marks) ->
                    marks.forEach { assertNotNull(scoreSigimsaeImage(it), "$songId $mode $slot $it") }
                }
            }
            assertEquals(common.events.map { it.id to it.sigimsae }, song.timeline.map { it.id to it.sigimsae })
            assertEquals(common.events.map { it.startTick to it.durationTick }, song.timeline.map { it.startTick to it.durationTick })
        }
    }
}
