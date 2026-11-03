package com.ssafy.eolssu.backend.feature.song

import com.ssafy.eolssu.backend.feature.song.api.SongController
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.*
import com.ssafy.eolssu.backend.feature.jangdan.infrastructure.persistence.*
import com.ssafy.eolssu.backend.feature.instrument.infrastructure.persistence.*
import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import java.time.LocalDateTime
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.json.JsonMapper

class SongControllerTests {
    @Test fun `instrument filter rejects malformed identifiers and missing instruments`() {
        for (id in listOf("", "0", "-1", "01", "+1", " 1", "1 ", "1.0", "abc", "9223372036854775808")) {
            mvc.perform(get("/api/songs").param("instrumentId", id))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
        }
        mvc.perform(get("/api/songs").param("instrumentId", "999999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.result.errorCode").value("RESOURCE_NOT_FOUND"))
    }
    private val time = LocalDateTime.parse("2026-10-29T00:00:00")
    private val song = SongRow(7, 1, 2, "테스트", null, 1, 96, "{\"schemaVersion\":1,\"events\":[{\"id\":\"original-note\"}]}", 3, null, 1, null, true, "PUBLISHED", time, time, null)
    private val mapper = object : SongMapper {
        override fun findById(id: Long) = song.takeIf { it.id == id }
        override fun findPublished(id: Long) = findById(id)
        override fun listPublished(instrumentId: Long?) = listOf(song).filter { instrumentId == null || it.instrumentId == instrumentId }
    }
    private val jangdans = object : JangdanMapper {
        override fun listAll() = emptyList<JangdanRow>()
        override fun findById(id: Long) = JangdanRow(2, "세마치", "{\"group\":[3,3,3]}", 96, "DOTTED_QUARTER", null, time, time)
    }
    private val instruments = object : InstrumentMapper {
        override fun listActive() = emptyList<InstrumentRow>()
        override fun findById(id: Long): InstrumentRow? = null
        override fun activeExists(id: Long) = id in 1L..4L
        override fun findActiveIdByCode(code: String): Long? = null
    }
    private val mvc = MockMvcBuilders.standaloneSetup(SongController(mapper, jangdans, JsonMapper.builder().build(), instruments))
        .setControllerAdvice(GlobalExceptionHandler()).build()

    @Test fun `list carries database identity and original score JSON as an object`() {
        mvc.perform(get("/api/songs")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.items[0].songId").value("7"))
            .andExpect(jsonPath("$.result.items[0].scoreVersion").value(3))
            .andExpect(jsonPath("$.result.items[0].score.events[0].id").value("original-note"))
            .andExpect(jsonPath("$.result.items[0].jangdan.bpmUnit").value("DOTTED_QUARTER"))
    }
    @Test fun `detail uses the same contract and missing songs use common 404`() {
        mvc.perform(get("/api/songs/7")).andExpect(status().isOk).andExpect(jsonPath("$.result.songId").value("7"))
        mvc.perform(get("/api/songs/999")).andExpect(status().isNotFound).andExpect(jsonPath("$.result.errorCode").value("RESOURCE_NOT_FOUND"))
    }
    @Test fun `active instrument filter preserves matching songs and permits an empty result`() {
        mvc.perform(get("/api/songs").param("instrumentId", "1"))
            .andExpect(status().isOk).andExpect(jsonPath("$.result.items[0].songId").value("7"))
        mvc.perform(get("/api/songs").param("instrumentId", "2"))
            .andExpect(status().isOk).andExpect(jsonPath("$.result.items").isEmpty)
    }
}
