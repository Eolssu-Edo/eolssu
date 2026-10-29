package com.ssafy.eolssu.backend.feature.song

import com.ssafy.eolssu.backend.feature.song.api.SongController
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.*
import com.ssafy.eolssu.backend.feature.jangdan.infrastructure.persistence.*
import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import java.time.LocalDateTime
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.json.JsonMapper

class SongControllerTests {
    private val time = LocalDateTime.parse("2026-10-29T00:00:00")
    private val song = SongRow(7, 1, 2, "테스트", null, 1, 96, "{\"schemaVersion\":1,\"events\":[{\"id\":\"original-note\"}]}", 3, null, 1, null, true, "PUBLISHED", time, time, null)
    private val mapper = object : SongMapper {
        override fun findById(id: Long) = song.takeIf { it.id == id }
        override fun findPublished(id: Long) = findById(id)
        override fun listPublished() = listOf(song)
    }
    private val jangdans = object : JangdanMapper {
        override fun findById(id: Long) = JangdanRow(2, "세마치", "{\"group\":[3,3,3]}", 96, "DOTTED_QUARTER", null, time, time)
    }
    private val mvc = MockMvcBuilders.standaloneSetup(SongController(mapper, jangdans, JsonMapper.builder().build()))
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
}
