package com.ssafy.eolssu.backend.feature.song.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.SongMapper
import com.ssafy.eolssu.backend.feature.jangdan.infrastructure.persistence.JangdanMapper
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.JsonNode
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.SongRow

@RestController
@RequestMapping("/api/songs")
@Profile("db | ops")
class SongController(private val songs: SongMapper, private val jangdans: JangdanMapper, private val json: JsonMapper) {
    @GetMapping
    fun list() = SuccessResponse.of(HttpStatus.OK, "곡 목록 조회 성공", SongListResult(songs.listPublished().map(::item)))

    @GetMapping("/{songId}")
    fun get(@PathVariable songId: Long): ResponseEntity<*> =
        songs.findPublished(songId)?.let { ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK, "곡 상세 조회 성공", item(it))) }
            ?: ResponseEntity.status(404).body(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND))

    private fun item(song: SongRow): SongItem {
        val jangdan = requireNotNull(jangdans.findById(song.jangdanId))
        return SongItem(song.id.toString(), song.instrumentId.toString(), song.title, song.description, song.difficulty.toInt(),
            song.defaultBpm.toInt(), song.isScorable, song.scoreVersion, song.noteCount,
            SongJangdan(jangdan.id.toString(), jangdan.name, jangdan.bpmUnit, json.readTree(jangdan.pattern)), json.readTree(song.scoreJson))
    }
}

data class SongListResult(val items: List<SongItem>)
data class SongItem(val songId: String, val instrumentId: String, val title: String, val description: String?, val difficulty: Int,
    val defaultBpm: Int, val isScorable: Boolean, val scoreVersion: Int, val noteCount: Int, val jangdan: SongJangdan, val score: JsonNode)
data class SongJangdan(val jangdanId: String, val name: String, val bpmUnit: String, val pattern: JsonNode)
