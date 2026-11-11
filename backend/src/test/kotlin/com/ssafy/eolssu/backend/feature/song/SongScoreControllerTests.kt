package com.ssafy.eolssu.backend.feature.song

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.song.api.SongScoreController
import com.ssafy.eolssu.backend.feature.song.application.GetSongScore
import com.ssafy.eolssu.backend.feature.song.domain.*
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.CommonScoreTimelineDecoder
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.json.JsonMapper

class SongScoreControllerTests {
    @Test fun `requested score revision never falls back and malformed versions are rejected`() {
        mvc.perform(get("/api/songs/7/score").param("scoreVersion", "3")).andExpect(status().isOk)
        mvc.perform(get("/api/songs/7/score").param("scoreVersion", "99")).andExpect(status().isNotFound)
        for (version in listOf("", "0", "-1", "01", "1.0", "2147483648", "bad")) {
            mvc.perform(get("/api/songs/7/score").param("scoreVersion", version)).andExpect(status().isBadRequest)
        }
    }
    private val json = JsonMapper.builder().build()
    private val pitches = listOf(ScorePitch(9007199254740993, "중", 0, 5, "仲", "중"), ScorePitch(3, "임", 0, 7, "林", "임"))
    private var stored: PublishedSongScore? = PublishedSongScore(7, 3, 2, 96, "DOTTED_QUARTER", score)
    private var currentPitches = pitches
    private var playback: ScoreJangdanPlayback? = null
    private val repository = object : SongScoreRepository {
        override fun findPublished(songId: Long) = stored?.takeIf { it.songId == songId }
        override fun pitches() = currentPitches
    }
    private val mvc = MockMvcBuilders.standaloneSetup(SongScoreController(
        GetSongScore(repository, CommonScoreTimelineDecoder(json)) { playback }, json,
    )).setControllerAdvice(GlobalExceptionHandler()).build()

    @Test fun `keeps score source and projects NOTE identity after rests and ties`() {
        val response = mvc.perform(get("/api/songs/7/score")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.songId").value("7"))
            .andExpect(jsonPath("$.result.scoreVersion").value(3))
            .andExpect(jsonPath("$.result.schemaVersion").value(1))
            .andExpect(jsonPath("$.result.events[0].pitchId").value("9007199254740993"))
            .andExpect(jsonPath("$.result.events[0].durationBeat").value(2.0))
            .andExpect(jsonPath("$.result.events[1].noteIdx").value(null as Any?))
            .andExpect(jsonPath("$.result.events[1].pitchId").value(null as Any?))
            .andExpect(jsonPath("$.result.events[2].noteIdx").value(1))
            .andExpect(jsonPath("$.result.events[2].onsetBeat").value(2.5))
            .andExpect(jsonPath("$.result.events[2].columnIdx").value(0))
            .andExpect(jsonPath("$.result.events[2].cellIdx").value(2))
            .andExpect(jsonPath("$.result.events[2].subIdx").value(1))
            .andExpect(jsonPath("$.result.events[2].startTick").value(10))
            .andExpect(jsonPath("$.result.notationCapabilities.hanja").value(true))
            .andExpect(jsonPath("$.result.notationCapabilities.hangul").value(true))
            .andExpect(jsonPath("$.result.notationCapabilities.solfege").value(false))
            .andReturn().response.contentAsString
        assertEquals(json.readTree(score), json.readTree(response).path("result").path("score"))
    }

    @Test fun `accepts full positive Long IDs without floating point conversion`() {
        stored = stored!!.copy(songId = Long.MAX_VALUE)
        mvc.perform(get("/api/songs/${Long.MAX_VALUE}/score")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.songId").value(Long.MAX_VALUE.toString()))
    }

    @Test fun `supplemental playback does not change the old field or the original score`() {
        val original = stored!!
        val source = json.readTree(original.scoreJson) as tools.jackson.databind.node.ObjectNode
        (source.path("music") as tools.jackson.databind.node.ObjectNode).put("jangdan", "")
        stored = original.copy(scoreJson = source.toString())
        playback = ScoreJangdanPlayback("4", "자진모리", 0, 12, JangdanPlaybackPattern(List(12) { "덩" }, List(4) { 3 }))

        val response = mvc.perform(get("/api/songs/7/score")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.jangdanPlayback").doesNotExist())
            .andExpect(jsonPath("$.result.supplementalJangdanPlayback.name").value("자진모리"))
            .andReturn().response.contentAsString
        assertEquals(source, json.readTree(response).path("result").path("score"))
    }

    @Test fun `catalog tempo preserves song range instead of recording session limits`() {
        val original = stored!!
        for (bpm in listOf(30, 300, 400)) {
            stored = original.copy(defaultBpm = bpm)
            mvc.perform(get("/api/songs/7/score")).andExpect(status().isOk)
                .andExpect(jsonPath("$.result.defaultBpm").value(bpm))
        }
        stored = original.copy(defaultBpm = 401)
        conflict()
    }

    @Test fun `timeline end must match the complete visible layout duration`() {
        val original = stored!!
        for (invalid in listOf(
            score.replace("\"startTick\":10,\"durationTick\":2", "\"startTick\":10,\"durationTick\":3"),
            score.replace("\"startTick\":10,\"durationTick\":2", "\"startTick\":10,\"durationTick\":1"),
            score.replace("\"ticksPerJeonggan\":4", "\"ticksPerJeonggan\":5"),
        )) {
            stored = original.copy(scoreJson = invalid)
            conflict()
        }
    }

    @Test fun `rejects malformed IDs before reading repository`() {
        for (id in listOf("0", "-1", "+1", "01", "1.5", "1e2", "9223372036854775808", "letters")) {
            mvc.perform(get("/api/songs/$id/score")).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
        }
    }

    @Test fun `missing score uses common 404`() {
        mvc.perform(get("/api/songs/9/score")).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.result.errorCode").value("RESOURCE_NOT_FOUND"))
    }

    @Test fun `rejects inconsistent versions counts units and missing pitch metadata`() {
        val original = stored!!
        val invalid = listOf(
            original.copy(scoreVersion = 4), original.copy(noteCount = 3), original.copy(bpmUnit = "QUARTER"),
            original.copy(scoreJson = score.replace("\"schemaVersion\":1", "\"schemaVersion\":2")),
            original.copy(scoreJson = score.replace("\"startTick\":10", "\"startTick\":9")),
            original.copy(scoreJson = score.replace("\"durationTick\":8", "\"durationTick\":0")),
            original.copy(scoreJson = score.replace("\"kind\":\"REST\"", "\"kind\":\"UNKNOWN\"")),
            original.copy(scoreJson = score.replace("\"ticksPerBeat\":4", "\"ticksPerBeat\":0")),
            original.copy(scoreJson = "not JSON"),
        )
        for (candidate in invalid) {
            stored = candidate
            conflict()
        }
        stored = original
        currentPitches = pitches.take(1)
        conflict()
    }

    @Test fun `rejects dangling or ambiguous layout references without renumbering`() {
        val original = stored!!
        for (invalid in listOf(
            score.replace("\"cellId\":\"c0-j2\"", "\"cellId\":\"c0-j1\""),
            score.replace("\"symbol\":\"-\",\"eventId\":\"c0-j0-r0-u0\"", "\"symbol\":\"-\",\"eventId\":\"missing\""),
            score.replace("\"row\":1,\"col\":0", "\"row\":0,\"col\":0"),
        )) {
            stored = original.copy(scoreJson = invalid)
            conflict()
        }
    }

    @Test fun `does not declare missing pitch text supported`() {
        currentPitches = pitches.map { it.copy(hanja = "", hangul = "") }
        mvc.perform(get("/api/songs/7/score")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.notationCapabilities.hanja").value(false))
            .andExpect(jsonPath("$.result.notationCapabilities.hangul").value(false))
    }

    @Test fun `zero note score does not create a fabricated scoring note`() {
        stored = stored!!.copy(noteCount = 0, scoreJson = """{"schemaVersion":1,"score":{"scoreVersion":3},"music":{"ticksPerBeat":4,"ticksPerJeonggan":4,"bpmUnit":"DOTTED_QUARTER"},"columns":[],"events":[]}""")
        mvc.perform(get("/api/songs/7/score")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.events").isEmpty)
            .andExpect(jsonPath("$.result.noteCount").value(0))
    }

    private fun conflict() {
        mvc.perform(get("/api/songs/7/score")).andExpect(status().isConflict)
            .andExpect(jsonPath("$.result.errorCode").value("STATE_CONFLICT"))
    }

    private companion object {
        val score = """{
          "schemaVersion":1,"score":{"scoreVersion":3},
          "music":{"ticksPerBeat":4,"ticksPerJeonggan":4,"bpmUnit":"DOTTED_QUARTER"},
          "columns":[{"index":0,"cells":[
            {"id":"c0-j0","index":0,"units":[{"row":0,"col":0,"symbol":"중","eventId":"c0-j0-r0-u0"}]},
            {"id":"c0-j1","index":1,"units":[{"row":0,"col":0,"symbol":"-","eventId":"c0-j0-r0-u0"}]},
            {"id":"c0-j2","index":2,"units":[{"row":0,"col":0,"symbol":"쉼","eventId":"c0-j2-r0-u0"},{"row":1,"col":0,"symbol":"임","eventId":"c0-j2-r1-u0"}]}
          ]}],
          "events":[
            {"id":"c0-j0-r0-u0","kind":"NOTE","yul":"중","octave":0,"startTick":0,"durationTick":8,"cellId":"c0-j0","breathAfter":true,"sigimsae":["추성"]},
            {"id":"c0-j2-r0-u0","kind":"REST","yul":null,"octave":null,"startTick":8,"durationTick":2,"cellId":"c0-j2","breathAfter":false,"sigimsae":[]},
            {"id":"c0-j2-r1-u0","kind":"NOTE","yul":"임","octave":0,"startTick":10,"durationTick":2,"cellId":"c0-j2","breathAfter":false,"sigimsae":[]}
          ]}
        """.trimIndent()
    }
}
