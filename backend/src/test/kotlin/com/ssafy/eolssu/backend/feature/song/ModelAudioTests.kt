package com.ssafy.eolssu.backend.feature.song

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.song.api.ModelAudioController
import com.ssafy.eolssu.backend.feature.song.application.GetModelAudio
import com.ssafy.eolssu.backend.feature.song.domain.ModelAudio
import com.ssafy.eolssu.backend.feature.song.domain.ModelAudioRepository
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ModelAudioTests {
    private val audio = ModelAudio(9_007_199_254_740_993, 3, "https://media.example.test/p171.m4a", 96,
        "DOTTED_QUARTER", 53050, 0, "audio/mp4", "a".repeat(64))
    private var stored: ModelAudio? = audio
    private val query = GetModelAudio(object : ModelAudioRepository {
        override fun findCurrent(songId: Long) = stored?.takeIf { it.songId == songId }
    })
    private val mvc = MockMvcBuilders.standaloneSetup(ModelAudioController(query))
        .setControllerAdvice(GlobalExceptionHandler()).build()

    @Test fun `returns exact song identity media duration tempo and version`() {
        mvc.perform(get("/api/songs/${audio.songId}/model-audio"))
            .andExpect(status().isOk).andExpect(jsonPath("$.result.songId").value(audio.songId.toString()))
            .andExpect(jsonPath("$.result.scoreVersion").value(3))
            .andExpect(jsonPath("$.result.durationMs").value(53050))
            .andExpect(jsonPath("$.result.startOffsetMs").value(0))
            .andExpect(jsonPath("$.result.bpmUnit").value("DOTTED_QUARTER"))
            .andExpect(jsonPath("$.result.contentType").value("audio/mp4"))
            .andExpect(jsonPath("$.result.sha256").value("a".repeat(64)))
    }

    @Test fun `missing audio is 404 and malformed ids are common 400`() {
        mvc.perform(get("/api/songs/1/model-audio")).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.result.errorCode").value("RESOURCE_NOT_FOUND"))
        for (id in listOf("0", "-1", "01", "1.5", "abc", "9223372036854775808")) {
            mvc.perform(get("/api/songs/$id/model-audio")).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
        }
    }

    @Test fun `unusable stored metadata never produces a playback address`() {
        val invalid = listOf(
            audio.copy(audioUrl="http://media.example.test/test.m4a"),
            audio.copy(audioUrl="https://user:password@media.example.test/test.m4a"),
            audio.copy(audioUrl="https://media.example.test/test.m4a#fragment"),
            audio.copy(audioUrl="javascript:alert(1)"), audio.copy(audioUrl="https:///test.m4a"),
            audio.copy(durationMs=0), audio.copy(startOffsetMs=-1), audio.copy(startOffsetMs=53050),
            audio.copy(scoreVersion=0), audio.copy(bpm=0), audio.copy(bpmUnit="SECONDS"),
            audio.copy(contentType="text/html"), audio.copy(sha256="unknown"),
        )
        for (candidate in invalid) {
            stored = candidate
            assertNull(query.find(audio.songId), candidate.toString())
        }
        stored = audio.copy(bpm=400)
        assertNotNull(query.find(audio.songId)) // playback uses songs' 30..400 range, not recording session limits
    }
}
