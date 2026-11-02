package com.ssafy.eolssu.backend.feature.catalog.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.catalog.domain.*
import java.net.URI
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@RestController
@RequestMapping("/api/reference-audios")
@Profile("db | ops")
class ReferenceAudioController(
    private val repository: ReferenceAudioRepository,
    private val urls: ReferenceAudioReadUrls,
    private val json: JsonMapper,
) {
    @GetMapping
    fun list(@RequestParam datasetId: String, @RequestParam(required = false) category: String?,
        @RequestParam(required = false) technique: String?, @RequestParam(required = false) register: String?,
        @RequestParam(required = false) yulmyeong: String?,
        @RequestParam(required = false) pitchId: String? = null,
        @RequestParam(required = false) relatedPitchId: String? = null): ResponseEntity<*> {
        if (datasetId !in REFERENCE_AUDIO_DATASETS || !filterValid(category,32) || !filterValid(technique,100) ||
            !filterValid(register,32) || !filterValid(yulmyeong,32)) return error(ErrorCode.INVALID_REQUEST)
        if (!pitchIdValid(pitchId) || !pitchIdValid(relatedPitchId))
            return error(ErrorCode.INVALID_REQUEST)
        val assets = repository.published(ReferenceAudioFilter(datasetId,category,technique,register,yulmyeong,
            pitchId?.toLong(),relatedPitchId?.toLong()))
        val relations = repository.pitchRelations(assets.map { it.assetId }).groupBy { it.assetId }
        val items = assets.map { it.item(relations[it.assetId].orEmpty()) }
        return ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK,"기본음·시김새 음원 목록 조회 성공",ReferenceAudioListResult(items)))
    }

    @GetMapping("/{assetId}")
    fun detail(@PathVariable assetId: String): ResponseEntity<*> {
        if (!isReferenceAudioId(assetId)) return error(ErrorCode.INVALID_REQUEST)
        val asset = repository.published(assetId) ?: return error(ErrorCode.RESOURCE_NOT_FOUND)
        val metadata = json.readTree(asset.metadataJson)
        check(metadata.isObject)
        return ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK,"기본음·시김새 음원 상세 조회 성공",
            ReferenceAudioDetailResult(asset.item(repository.pitchRelations(listOf(assetId))),metadata)))
    }

    @GetMapping("/{assetId}/play")
    fun play(@PathVariable assetId: String): ResponseEntity<*> {
        if (!isReferenceAudioId(assetId)) return error(ErrorCode.INVALID_REQUEST)
        val asset = repository.published(assetId) ?: return error(ErrorCode.RESOURCE_NOT_FOUND)
        return try {
            ResponseEntity.status(HttpStatus.FOUND).location(URI(urls.readUrl(asset.objectKey)))
                .header("Cache-Control","no-store").build<Void>()
        } catch (_: ReferenceAudioUnavailable) {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Cache-Control","no-store")
                .body(ErrorResponse.of(ErrorCode.SERVICE_UNAVAILABLE))
        }
    }

    private fun filterValid(value: String?, max: Int) = value == null ||
        (value.isNotBlank() && value.length <= max && value.none(Char::isISOControl))
    private fun pitchIdValid(value: String?) = value == null ||
        (Regex("[1-9][0-9]{0,18}").matches(value) && value.toLongOrNull() != null)
    private fun error(code: ErrorCode) = ResponseEntity.status(code.status).body(ErrorResponse.of(code))
    private fun ReferenceAudioAsset.item(relations: List<ReferenceAudioPitchRelation>) = ReferenceAudioItem(assetId,datasetId,labelId,category,technique,registerName,
        yulmyeong,displayLabel,contentType,sha256,byteSize,durationMs,sampleRateHz,channels.toInt(),reviewStatus,eligibleForNoteScoring,
        "/api/reference-audios/$assetId/play", pitchId?.toString(),relations.map {
            ReferenceAudioPitchRelationItem(it.role,if (it.role=="SEQUENCE") it.anchorIndex else null,it.pitchId.toString(),it.evidencePath)
        })
}

data class ReferenceAudioListResult(val items: List<ReferenceAudioItem>)
data class ReferenceAudioDetailResult(val asset: ReferenceAudioItem, val metadata: JsonNode)
data class ReferenceAudioItem(val assetId: String, val datasetId: String, val labelId: String, val category: String,
    val technique: String, val register: String, val yulmyeong: String, val displayLabel: String, val contentType: String,
    val sha256: String, val bytes: Long, val durationMs: Int, val sampleRateHz: Int, val channels: Int, val review: String,
    val eligibleForNoteScoring: Boolean, val soundUrl: String,
    @field:io.swagger.v3.oas.annotations.media.Schema(types = ["string", "null"], description = "명목상 기준 음 ID. 미연결은 null이며 검수/채점 정답 여부와 독립적입니다.")
    val pitchId: String?, val pitchRelations: List<ReferenceAudioPitchRelationItem>)

data class ReferenceAudioPitchRelationItem(val role: String,
    @field:io.swagger.v3.oas.annotations.media.Schema(types = ["integer", "null"], description = "SEQUENCE의 0 기반 관측 순서. 다른 역할은 null.")
    val sequenceIndex: Int?, val pitchId: String, val evidencePath: String)
