package com.ssafy.eolssu.backend.feature.catalog

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.catalog.api.CatalogReferenceController
import com.ssafy.eolssu.backend.feature.catalog.application.CatalogReferenceQueries
import com.ssafy.eolssu.backend.feature.catalog.application.JangdanPreviewAudio
import com.ssafy.eolssu.backend.feature.catalog.domain.*
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.*

class CatalogReferenceControllerTests {
    private val repository = ReferenceFixture()
    private val queries = CatalogReferenceQueries(repository)
    private val previewHash = "a".repeat(64)
    private var signingAvailable = true
    private val previews = JangdanPreviewAudio(mapOf(1L to previewHash)) {
        if (signingAvailable) "https://r2.example.test/$it?signature=test" else null
    }
    private val mvc = MockMvcBuilders.standaloneSetup(CatalogReferenceController(queries, previews))
        .setControllerAdvice(GlobalExceptionHandler()).build()

    @Test fun `instrument response preserves bigint identities and stored tolerance`() {
        mvc.perform(get("/api/instruments")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.items[0].instrumentId").value("9007199254740993"))
            .andExpect(jsonPath("$.result.items[0].pitchToleranceCent").value(50))
            .andExpect(jsonPath("$.result.items[0].rangeLowPitchId").value("91"))
            .andExpect(jsonPath("$.result.items[0].rangeHighPitchId").value("3"))
    }

    @Test fun `pitch filter resolves semitones and never assumes pitch ID ordering`() {
        mvc.perform(get("/api/pitches").param("instrumentId", "9007199254740993")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.items.length()").value(2))
            .andExpect(jsonPath("$.result.items[0].pitchId").value("91"))
            .andExpect(jsonPath("$.result.items[0].semitone").value(5))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"description\":null")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"soundUrl\":null")))
        assertEquals(5 to 19, repository.lastPitchRange)
    }

    @Test fun `pitch response without instrument returns all registered pitches`() {
        mvc.perform(get("/api/pitches")).andExpect(status().isOk).andExpect(jsonPath("$.result.items.length()").value(3))
        assertEquals(null to null, repository.lastPitchRange)
    }

    @Test fun `invalid ID spelling and overflow use common 400 instead of broadening a filter`() {
        for (id in listOf("", "0", "-1", "+1", "01", " 1", "1.0", "9223372036854775808", "abc")) {
            mvc.perform(get("/api/pitches").param("instrumentId", id)).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
            mvc.perform(get("/api/instruments/9007199254740993/fingerings").param("pitchId", id))
                .andExpect(status().isBadRequest)
        }
        for (path in listOf("/api/instruments/0/fingerings", "/api/jangdans/01", "/api/jangdans/9223372036854775808")) {
            mvc.perform(get(path)).andExpect(status().isBadRequest)
        }
    }

    @Test fun `missing instruments and jangdans use common 404`() {
        for (path in listOf("/api/pitches?instrumentId=2", "/api/instruments/2/fingerings", "/api/jangdans/2")) {
            mvc.perform(get(path)).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.result.errorCode").value("RESOURCE_NOT_FOUND"))
        }
    }

    @Test fun `missing fingering content returns empty list and preserves nullable description`() {
        mvc.perform(get("/api/instruments/9007199254740993/fingerings").param("pitchId", "3"))
            .andExpect(status().isOk).andExpect(jsonPath("$.result.items").isEmpty)
        mvc.perform(get("/api/instruments/9007199254740993/fingerings").param("pitchId", "91"))
            .andExpect(status().isOk).andExpect(jsonPath("$.result.items[0].fingeringId").value("8"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"description\":null")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"structureImageUrl\":null")))
    }

    @Test fun `jangdan lists count the stored pattern and detail preserves unit and empty continuation cells`() {
        mvc.perform(get("/api/jangdans")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.items[0].beatCount").value(3))
            .andExpect(jsonPath("$.result.items[0].groupCount").value(1))
        mvc.perform(get("/api/jangdans/1")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.defaultBpm").value(96))
            .andExpect(jsonPath("$.result.bpmUnit").value("DOTTED_QUARTER"))
            .andExpect(jsonPath("$.result.pattern.beats[1]").value(""))
            .andExpect(jsonPath("$.result.soundUrl").value("/api/jangdans/1/audio"))
    }

    @Test fun `public jangdan audio redirects without caching and rejects unknown ids`() {
        mvc.perform(get("/api/jangdans/1/audio").header("Range", "bytes=0-63"))
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "https://r2.example.test/jangdan-preview/v1/1/$previewHash.m4a?signature=test"))
            .andExpect(header().string("Cache-Control", "no-store"))
        mvc.perform(get("/api/jangdans/01/audio")).andExpect(status().isBadRequest)
        mvc.perform(get("/api/jangdans/2/audio")).andExpect(status().isNotFound)
        signingAvailable = false
        mvc.perform(get("/api/jangdans/1/audio")).andExpect(status().isServiceUnavailable)
            .andExpect(header().string("Cache-Control", "no-store"))
    }

    @Test fun `notation preserves glyph and null image without inventing semantic codes`() {
        mvc.perform(get("/api/notation-symbols")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.items[0].symbol").value("－"))
            .andExpect(jsonPath("$.result.items[0].semanticCode").doesNotExist())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"imageUrl\":null")))
    }

    @Test fun `empty catalog lists remain successful`() {
        repository.empty = true
        for (path in listOf("/api/instruments", "/api/pitches", "/api/jangdans", "/api/notation-symbols")) {
            mvc.perform(get(path)).andExpect(status().isOk).andExpect(jsonPath("$.result.items").isEmpty)
        }
    }

    @Test fun `invalid stored ranges fail without leaking details into response`() {
        repository.reversedRange = true
        mvc.perform(get("/api/pitches?instrumentId=9007199254740993")).andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.result.errorCode").value("INTERNAL_SERVER_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("reversed"))))
    }

    @Test fun `jangdan grouping must be positive and sum to actual cells`() {
        for (groups in listOf(listOf(2), listOf(0, 3), listOf(-1, 4), listOf(Int.MAX_VALUE, Int.MAX_VALUE))) {
            assertFailsWith<IllegalArgumentException> { CatalogJangdanPattern(listOf("떵", "", "덕"), groups) }
        }
        assertEquals(emptyList(), CatalogJangdanPattern(emptyList(), emptyList()).beats)
    }
}

private class ReferenceFixture : CatalogReferenceRepository {
    var empty = false
    var reversedRange = false
    var lastPitchRange: Pair<Int?, Int?>? = null
    private val instrument = CatalogInstrument(9007199254740993, "DANSO", "단소", 50, 91, 3)
    private val pitches = listOf(CatalogPitch(91, "중", 0, "仲", "중", 5), CatalogPitch(3, "임", 1, "淋", "청임", 19),
        CatalogPitch(2, "중", 2, "㴢", "중청중", 29))
    override fun activeInstruments() = if (empty) emptyList() else listOf(instrument)
    override fun activeInstrument(id: Long) = activeInstruments().find { it.id == id }?.let {
        if (reversedRange) it.copy(rangeLowPitchId = 3, rangeHighPitchId = 91) else it
    }
    override fun pitch(id: Long) = pitches.find { it.id == id }
    override fun pitches(lowSemitone: Int?, highSemitone: Int?): List<CatalogPitch> {
        lastPitchRange = lowSemitone to highSemitone
        return if (empty) emptyList() else pitches.filter { (lowSemitone == null || it.semitone >= lowSemitone) &&
            (highSemitone == null || it.semitone <= highSemitone) }
    }
    override fun fingerings(instrumentId: Long, pitchId: Long?) =
        listOf(CatalogFingering(8, instrumentId, 91, "https://example.test/chart.png", null)).filter { pitchId == null || it.pitchId == pitchId }
    override fun jangdans() = if (empty) emptyList() else listOf(CatalogJangdan(1, "세마치", 96, "DOTTED_QUARTER",
        CatalogJangdanPattern(listOf("떵", "", "덕"), listOf(3)), null))
    override fun jangdan(id: Long) = jangdans().find { it.id == id }
    override fun notationSymbols() = if (empty) emptyList() else listOf(CatalogNotationSymbol(3, "－", "연장", "검증된 설명", null))
}
