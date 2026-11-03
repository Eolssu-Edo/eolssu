package com.ssafy.eolssu.backend.feature.song

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.song.api.ModelAudioController
import com.ssafy.eolssu.backend.feature.song.application.GetModelAudio
import com.ssafy.eolssu.backend.feature.song.domain.ModelAudio
import com.ssafy.eolssu.backend.feature.song.domain.ModelAudioRepository
import com.ssafy.eolssu.backend.feature.song.domain.ModelAudioFiles
import com.ssafy.eolssu.backend.feature.song.domain.ModelAudioUnavailable
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ModelAudioTests {
    private val key = "model-audio/drafts/${"b".repeat(64)}/cases/danso-001-p1/danso.m4a"
    private val audio = ModelAudio(9_007_199_254_740_993, 3, key, 96,
        "DOTTED_QUARTER", 53050, 0, "audio/mp4", "a".repeat(64))
    private var stored: ModelAudio? = audio
    private val query = GetModelAudio(object : ModelAudioRepository {
        override fun findCurrent(songId: Long) = stored?.takeIf { it.songId == songId }
    })
    private var storageCalls = 0
    private var unavailable = false
    private val files = object : ModelAudioFiles {
        override fun playbackUrl(audio: ModelAudio): String {
            storageCalls++
            if (unavailable) throw ModelAudioUnavailable()
            return "https://private.example.test/signed?token=temporary"
        }
        override fun performance(audio: ModelAudio): ByteArray {
            storageCalls++
            if (unavailable) throw ModelAudioUnavailable()
            return "{\"schemaVersion\":1}".encodeToByteArray()
        }
    }
    private val mvc = MockMvcBuilders.standaloneSetup(ModelAudioController(query, files))
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

    @Test fun `metadata returns a stable application playback path instead of storage location`() {
        mvc.perform(get("/api/songs/${audio.songId}/model-audio"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result.audioUrl").value("/api/songs/${audio.songId}/model-audio/play"))
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
            audio.copy(objectKey="recordings/member/session.m4a"),
            audio.copy(objectKey=key.replace("danso.m4a", "sogeum.m4a")),
            audio.copy(objectKey=key + "?signature=value"),
            audio.copy(objectKey=key.replace("/cases/", "/../cases/")),
            audio.copy(objectKey="https://private.example.test/$key"),
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

    @Test fun `media rechecks availability without signing unpublished or invalid metadata`() {
        for (suffix in listOf("play", "performance")) {
            stored = null
            mvc.perform(get("/api/songs/${audio.songId}/model-audio/$suffix")).andExpect(status().isNotFound)
            stored = audio.copy(objectKey="recordings/user.m4a")
            mvc.perform(get("/api/songs/${audio.songId}/model-audio/$suffix")).andExpect(status().isNotFound)
        }
        assertEquals(0, storageCalls)
        stored = audio
        mvc.perform(get("/api/songs/${audio.songId}/model-audio/play"))
            .andExpect(status().isFound).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Location", "https://private.example.test/signed?token=temporary"))
        stored = null
        mvc.perform(get("/api/songs/${audio.songId}/model-audio/play")).andExpect(status().isNotFound)
        assertEquals(1, storageCalls)
    }

    @Test fun `performance requires matching source hash and is returned as raw same origin JSON`() {
        stored = audio.copy(performanceObjectKey=key.substringBeforeLast('/')+"/performance.common.json", sourceCommonSha256="c".repeat(64))
        mvc.perform(get("/api/songs/${audio.songId}/model-audio"))
            .andExpect(jsonPath("$.result.performanceUrl").value("/api/songs/${audio.songId}/model-audio/performance"))
            .andExpect(jsonPath("$.result.sourceCommonSha256").value("c".repeat(64)))
        mvc.perform(get("/api/songs/${audio.songId}/model-audio/performance"))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().json("{\"schemaVersion\":1}"))
        stored = stored!!.copy(sourceCommonSha256=null)
        mvc.perform(get("/api/songs/${audio.songId}/model-audio/performance")).andExpect(status().isNotFound)
    }

    @Test fun `storage failure is retryable and contains no storage address`() {
        unavailable = true
        mvc.perform(get("/api/songs/${audio.songId}/model-audio/play"))
            .andExpect(status().isServiceUnavailable).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.result.errorCode").value("SERVICE_UNAVAILABLE"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private.example"))))
    }
}
