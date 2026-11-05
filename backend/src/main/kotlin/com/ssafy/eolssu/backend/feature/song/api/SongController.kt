package com.ssafy.eolssu.backend.feature.song.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.SongMapper
import com.ssafy.eolssu.backend.feature.song.infrastructure.catalog.scoreJangdanName
import com.ssafy.eolssu.backend.feature.instrument.infrastructure.persistence.InstrumentMapper
import com.ssafy.eolssu.backend.feature.jangdan.infrastructure.persistence.JangdanMapper
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.JsonNode
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.SongRow
import io.swagger.v3.oas.annotations.media.Schema

@RestController
@RequestMapping("/api/songs")
@Profile("db | ops")
class SongController(private val songs: SongMapper, private val jangdans: JangdanMapper, private val json: JsonMapper,
    private val instruments: InstrumentMapper) {
    @GetMapping
    fun list(@RequestParam(required = false) instrumentId: String?): ResponseEntity<*> {
        val id = instrumentId?.let {
            it.takeIf { value -> value.matches(Regex("[1-9][0-9]{0,18}")) }?.toLongOrNull()
                ?: return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_REQUEST))
        }
        if (id != null && !instruments.activeExists(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND))
        }
        return ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK, "곡 목록 조회 성공",
            SongListResult(songs.listPublished(id).map(::listItem))))
    }

    @GetMapping("/{songId}")
    fun get(@PathVariable songId: Long): ResponseEntity<*> =
        songs.findPublished(songId)?.let { ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK, "곡 상세 조회 성공", item(it))) }
            ?: ResponseEntity.status(404).body(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND))

    // 목록은 악보 본문을 싣지 않는다. 악보는 상세·CAT-04에서 곡 단위로 조회한다.
    private fun listItem(song: SongRow) = SongListItem(song.id.toString(), song.instrumentId.toString(), song.title, song.description,
        song.difficulty.toInt(), song.defaultBpm.toInt(), song.isScorable, song.scoreVersion, song.noteCount, jangdan(song),
        scoreJangdanName(json.readTree(song.scoreJson), song.scoreVersion))

    private fun item(song: SongRow): SongItem {
        val score = json.readTree(song.scoreJson)
        return SongItem(song.id.toString(), song.instrumentId.toString(), song.title, song.description,
            song.difficulty.toInt(), song.defaultBpm.toInt(), song.isScorable, song.scoreVersion, song.noteCount, jangdan(song),
            score, scoreJangdanName(score, song.scoreVersion))
    }

    private fun jangdan(song: SongRow) = song.jangdanId?.let { requireNotNull(jangdans.findById(it)) }
        ?.let { SongJangdan(it.id.toString(), it.name, it.bpmUnit, json.readTree(it.pattern)) }
}

data class SongListResult(val items: List<SongListItem>)
data class SongListItem(val songId: String, val instrumentId: String, val title: String, val description: String?, val difficulty: Int,
    val defaultBpm: Int, val isScorable: Boolean, val scoreVersion: Int, val noteCount: Int,
    @get:Schema(nullable = true, description = "연결된 장단이 없으면 null. BPM 단위는 공통 악보 music.bpmUnit을 사용합니다.")
    val jangdan: SongJangdan?,
    @get:Schema(nullable = true, description = "악보에 표시할 장단 이름. 반주 및 박 단위는 별도 필드를 사용합니다.")
    val jangdanName: String?)
data class SongItem(val songId: String, val instrumentId: String, val title: String, val description: String?, val difficulty: Int,
    val defaultBpm: Int, val isScorable: Boolean, val scoreVersion: Int, val noteCount: Int,
    @get:Schema(nullable = true, description = "연결된 장단이 없으면 null. BPM 단위는 공통 악보 music.bpmUnit을 사용합니다.")
    val jangdan: SongJangdan?, val score: JsonNode,
    @get:Schema(nullable = true, description = "악보에 표시할 장단 이름.")
    val jangdanName: String?)
data class SongJangdan(val jangdanId: String, val name: String, val bpmUnit: String, val pattern: JsonNode)
