package com.ssafy.eolssu.backend.feature.catalog

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.catalog.api.ReferenceAudioController
import com.ssafy.eolssu.backend.feature.catalog.domain.*
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.json.JsonMapper
import kotlin.test.*

class ReferenceAudioControllerTests {
    private val asset = referenceAudioFixture().copy(pitchId = 1)
    private var selected: ReferenceAudioFilter? = null
    private var signed: String? = null
    private val repository = object : ReferenceAudioRepository {
        override fun published(filter: ReferenceAudioFilter): List<ReferenceAudioAsset> {
            selected = filter
            return listOf(asset)
        }
        override fun published(assetId: String) = asset.takeIf { it.assetId == assetId }
    }
    private fun mvc(urls: ReferenceAudioReadUrls = ReferenceAudioReadUrls { key ->
        signed = key
        "https://media.example.test/presigned?expires=900"
    }) = MockMvcBuilders.standaloneSetup(ReferenceAudioController(repository, urls, JsonMapper.builder().build()))
        .setControllerAdvice(GlobalExceptionHandler()).build()

    @Test fun `list preserves source labels and selects all requested filters without a representative guess`() {
        mvc().perform(get("/api/reference-audios").param("datasetId", "danso")
            .param("category", "기본음계").param("technique", "평취 강").param("register", "평취").param("yulmyeong", "중"))
            .andExpect(status().isOk).andExpect(jsonPath("$.result.items[0].labelId").value("ngc-danso-2338.tone-001"))
            .andExpect(jsonPath("$.result.items[0].review").value("inferred"))
            .andExpect(jsonPath("$.result.items[0].eligibleForNoteScoring").value(false))
            .andExpect(jsonPath("$.result.items[0].soundUrl").value("/api/reference-audios/${asset.assetId}/play"))
            .andExpect(jsonPath("$.result.items[0].objectKey").doesNotExist())
        assertEquals(ReferenceAudioFilter("danso", "기본음계", "평취 강", "평취", "중"), selected)
        assertNull(signed)
    }

    @Test fun `unknown dataset missing dataset blank filters and malformed IDs return common 400`() {
        for (query in listOf("", "?datasetId=unknown", "?datasetId=danso&category=", "?datasetId=danso&technique=")) {
            mvc().perform(get("/api/reference-audios$query")).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
        }
        mvc().perform(get("/api/reference-audios/not-an-id/play")).andExpect(status().isBadRequest)
        assertNull(signed)
    }

    @Test fun `pitch filter rejects noncanonical or out of range identifiers`() {
        for (id in listOf("", "0", "-1", "01", "1.0", "9223372036854775808", "abc")) {
            mvc().perform(get("/api/reference-audios").param("datasetId", "danso").param("pitchId", id))
                .andExpect(status().isBadRequest).andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
        }
    }

    @Test fun `pitch identity is a nullable string and filter accepts the Long upper bound`() {
        mvc().perform(get("/api/reference-audios").param("datasetId", "danso").param("pitchId", "9223372036854775807"))
            .andExpect(status().isOk).andExpect(jsonPath("$.result.items[0].pitchId").value("1"))
        assertEquals(Long.MAX_VALUE, selected?.pitchId)
        mvc().perform(get("/api/reference-audios/${asset.assetId}"))
            .andExpect(status().isOk).andExpect(jsonPath("$.result.asset.pitchId").value("1"))
    }

    @Test fun `detail retains attribution and missing published asset does not sign anything`() {
        mvc().perform(get("/api/reference-audios/${asset.assetId}"))
            .andExpect(status().isOk).andExpect(jsonPath("$.result.metadata.source.license").value("KOGL-1"))
        mvc().perform(get("/api/reference-audios/${"b".repeat(64)}/play")).andExpect(status().isNotFound)
        assertNull(signed)
    }

    @Test fun `play signs only the repository key and prevents redirect caching`() {
        mvc().perform(get("/api/reference-audios/${asset.assetId}/play"))
            .andExpect(status().isFound).andExpect(header().string("Location", "https://media.example.test/presigned?expires=900"))
            .andExpect(header().string("Cache-Control", "no-store"))
        assertEquals(asset.objectKey, signed)
    }

    @Test fun `unconfigured signer returns safe common 503 with no stored URL or exception`() {
        mvc(ReferenceAudioReadUrls { throw ReferenceAudioUnavailable() })
            .perform(get("/api/reference-audios/${asset.assetId}/play"))
            .andExpect(status().isServiceUnavailable).andExpect(jsonPath("$.result.errorCode").value("SERVICE_UNAVAILABLE"))
            .andExpect(header().doesNotExist("Location"))
    }

    @Test fun `asset refuses recording keys and invented scoring eligibility`() {
        assertFailsWith<IllegalArgumentException> { asset.copy(objectKey = "recordings/private.m4a") }
        assertFailsWith<IllegalArgumentException> { asset.copy(eligibleForNoteScoring = true) }
        assertFailsWith<IllegalArgumentException> { asset.copy(contentType = "text/html") }
    }
}

internal fun referenceAudioFixture(): ReferenceAudioAsset {
    val id = "a".repeat(64)
    val sha = "c".repeat(64)
    return ReferenceAudioAsset(id, "danso", "ngc-danso-2338.tone-001", "기본음계", "평취 강", "평취", "중", "평취 중",
        "reference-audio/v1/danso/$id/$sha.m4a", "audio/mp4", sha, 12345, 5043, 44100, 1, "inferred", false,
        """{"source":{"license":"KOGL-1","attribution":"국립국악원"},"label":{"review":"inferred"}}""", null)
}
