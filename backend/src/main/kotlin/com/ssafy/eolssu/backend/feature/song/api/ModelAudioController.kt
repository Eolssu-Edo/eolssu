package com.ssafy.eolssu.backend.feature.song.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.song.application.GetModelAudio
import com.ssafy.eolssu.backend.feature.song.domain.ModelAudio
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("db | ops")
class ModelAudioController(private val query: GetModelAudio) {
    @GetMapping("/api/songs/{songId}/model-audio")
    fun get(@PathVariable songId: String): ResponseEntity<*> {
        val id = songId.takeIf { it.matches(Regex("[1-9][0-9]*")) }?.toLongOrNull()
            ?: return error(ErrorCode.INVALID_REQUEST)
        val audio = query.find(id) ?: return error(ErrorCode.RESOURCE_NOT_FOUND)
        return ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK, "모범 연주 음원 조회 성공", ModelAudioResult(audio)))
    }

    private fun error(code: ErrorCode) = ResponseEntity.status(code.status).body(ErrorResponse.of(code))
}

data class ModelAudioResult(
    val songId: String, val scoreVersion: Int, val audioUrl: String, val bpm: Int, val bpmUnit: String,
    val durationMs: Int, val startOffsetMs: Int, val contentType: String, val sha256: String,
) {
    constructor(audio: ModelAudio) : this(audio.songId.toString(), audio.scoreVersion, audio.audioUrl,
        audio.bpm, audio.bpmUnit, audio.durationMs, audio.startOffsetMs, audio.contentType, audio.sha256)
}
