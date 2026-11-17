package com.ssafy.eolssu.backend.feature.song.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.song.application.GetSongScore
import com.ssafy.eolssu.backend.feature.song.application.SongScoreLookup
import com.ssafy.eolssu.backend.feature.song.domain.ScoreNotationCapabilities
import com.ssafy.eolssu.backend.feature.song.domain.SigimsaePlacement
import com.ssafy.eolssu.backend.feature.song.domain.ScoreTimelineEvent
import com.ssafy.eolssu.backend.feature.song.domain.ScoreJangdanPlayback
import com.ssafy.eolssu.backend.feature.song.infrastructure.catalog.declaredScoreJangdanName
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestParam
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@RestController
@Profile("db | ops")
class SongScoreController(private val service: GetSongScore, private val json: JsonMapper) {
    @GetMapping("/api/songs/{songId}/score")
    fun get(@PathVariable songId: String, @RequestParam(required = false) scoreVersion: String? = null): ResponseEntity<*> {
        val id = songId.takeIf { it.matches(Regex("[1-9][0-9]*")) }?.toLongOrNull()
            ?: return error(ErrorCode.INVALID_REQUEST)
        val revision = scoreVersion?.let { requestedScoreVersion(it) ?: return error(ErrorCode.INVALID_REQUEST) }
        return when (val result = service.get(id, revision)) {
            SongScoreLookup.NotFound -> error(ErrorCode.RESOURCE_NOT_FOUND)
            SongScoreLookup.Conflict -> error(ErrorCode.STATE_CONFLICT)
            is SongScoreLookup.Found -> {
                val score = json.readTree(result.stored.scoreJson)
                // Older clients require jangdanPlayback to match the name stored inside the score.
                val declaredName = declaredScoreJangdanName(score)
                val playback = result.jangdanPlayback
                ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK, "정간보·평가 기준 타임라인 조회 성공", SongScoreResult(
                    result.stored.songId.toString(), result.stored.scoreVersion, result.decoded.schemaVersion,
                    result.stored.noteCount, result.stored.defaultBpm, result.stored.bpmUnit,
                    result.decoded.events, result.decoded.notationCapabilities, score,
                    playback?.takeIf { it.name == declaredName },
                    playback?.takeIf { declaredName == null },
                    result.decoded.sigimsaePlacements,
                )))
            }
        }
    }

    private fun error(code: ErrorCode) = ResponseEntity.status(code.status).body(ErrorResponse.of(code))
}

/** `score` is the unchanged common score; projected events do not replace its layout or integer ticks. */
data class SongScoreResult(
    val songId: String,
    val scoreVersion: Int,
    val schemaVersion: Int,
    val noteCount: Int,
    val defaultBpm: Int,
    val bpmUnit: String,
    val events: List<ScoreTimelineEvent>,
    val notationCapabilities: ScoreNotationCapabilities,
    val score: JsonNode,
    @get:Schema(nullable = true, description = "현재 악보와 패턴이 검수된 판본과 일치할 때만 제공하는 장단 반주. 시작점과 주기는 score tick 단위입니다.")
    val jangdanPlayback: ScoreJangdanPlayback? = null,
    @get:Schema(nullable = true, description = "원문 장단명이 비어 있고 해당 판본에 반주 연결이 따로 검수된 경우에만 제공하는 장단 반주.")
    val supplementalJangdanPlayback: ScoreJangdanPlayback? = null,
    @get:Schema(nullable = true, description = "시김새가 적힌 표시 자리(cellId·row·col)와 그 자리의 기호. 이벤트마다 자리 순서대로 이으면 score의 event.sigimsae와 같습니다. 없거나 악보와 맞지 않으면 null이며, 이때는 이벤트 단위 시김새만 있습니다.")
    val sigimsaePlacements: List<SigimsaePlacement>? = null,
)
