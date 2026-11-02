package com.ssafy.eolssu.backend.feature.song.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.song.application.GetModelAudio
import com.ssafy.eolssu.backend.feature.song.domain.ModelAudio
import com.ssafy.eolssu.backend.feature.song.domain.ModelAudioFiles
import com.ssafy.eolssu.backend.feature.song.domain.ModelAudioUnavailable
import java.net.URI
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("db | ops")
class ModelAudioController(private val query: GetModelAudio, private val files: ModelAudioFiles) {
    @GetMapping("/api/songs/{songId}/model-audio")
    fun get(@PathVariable songId: String): ResponseEntity<*> {
        val id = songId.takeIf { it.matches(Regex("[1-9][0-9]*")) }?.toLongOrNull()
            ?: return error(ErrorCode.INVALID_REQUEST)
        val audio = query.find(id) ?: return error(ErrorCode.RESOURCE_NOT_FOUND)
        return ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK, "모범 연주 음원 조회 성공", ModelAudioResult(audio)))
    }

    @GetMapping("/api/songs/{songId}/model-audio/play")
    fun play(@PathVariable songId: String): ResponseEntity<*> = media(songId) { audio ->
        ResponseEntity.status(HttpStatus.FOUND).location(URI(files.playbackUrl(audio)))
            .header("Cache-Control", "no-store").build<Void>()
    }

    @GetMapping("/api/songs/{songId}/model-audio/performance")
    fun performance(@PathVariable songId: String): ResponseEntity<*> = media(songId) { audio ->
        if (audio.performanceObjectKey == null) error(ErrorCode.RESOURCE_NOT_FOUND)
        else ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header("Cache-Control", "no-store")
            .body(files.performance(audio))
    }

    private fun media(songId: String, serve: (ModelAudio) -> ResponseEntity<*>): ResponseEntity<*> {
        val id = songId.takeIf { it.matches(Regex("[1-9][0-9]*")) }?.toLongOrNull()
            ?: return error(ErrorCode.INVALID_REQUEST)
        val audio = query.find(id) ?: return error(ErrorCode.RESOURCE_NOT_FOUND)
        return try { serve(audio) } catch (_: ModelAudioUnavailable) {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Cache-Control", "no-store")
                .body(ErrorResponse.of(ErrorCode.SERVICE_UNAVAILABLE))
        }
    }

    private fun error(code: ErrorCode) = ResponseEntity.status(code.status).body(ErrorResponse.of(code))
}

data class ModelAudioResult(
    val songId: String, val scoreVersion: Int, val audioUrl: String, val bpm: Int, val bpmUnit: String,
    val durationMs: Int, val startOffsetMs: Int, val contentType: String, val sha256: String,
    @field:io.swagger.v3.oas.annotations.media.Schema(types = ["string", "null"], description = "생성 수행 시간표의 같은 서버 경로. 없으면 null.")
    val performanceUrl: String?,
    @field:io.swagger.v3.oas.annotations.media.Schema(types = ["string", "null"], description = "수행 시간표와 결합한 공통 악보 원문 바이트 SHA-256.")
    val sourceCommonSha256: String?,
) {
    constructor(audio: ModelAudio) : this(audio.songId.toString(), audio.scoreVersion, "/api/songs/${audio.songId}/model-audio/play",
        audio.bpm, audio.bpmUnit, audio.durationMs, audio.startOffsetMs, audio.contentType, audio.sha256,
        audio.performanceObjectKey?.let { "/api/songs/${audio.songId}/model-audio/performance" }, audio.sourceCommonSha256)
}
