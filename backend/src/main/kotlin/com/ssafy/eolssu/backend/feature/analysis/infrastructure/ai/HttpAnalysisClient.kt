package com.ssafy.eolssu.backend.feature.analysis.infrastructure.ai

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisClient
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisFailReason
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisRequest
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisResponseException
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisResult
import com.ssafy.eolssu.backend.feature.analysis.domain.NoteJudgement
import com.ssafy.eolssu.backend.feature.analysis.domain.NoteVerdict
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

/** AI 서비스의 `POST /v1/analyses`(PRA-03 '분석 요청과 상태 조회 계약'). 계약에 맞지 않는 응답은 [AnalysisResponseException]. */
class HttpAnalysisClient(
    private val restClient: RestClient,
    private val jsonMapper: JsonMapper,
) : AnalysisClient {
    override fun analyze(request: AnalysisRequest): AnalysisResult {
        val body = restClient.post()
            .uri("/v1/analyses")
            .contentType(MediaType.APPLICATION_JSON)
            .body(jsonMapper.writeValueAsString(request.toBody()))
            .retrieve()
            .body(String::class.java)
            ?: throw AnalysisResponseException("empty analysis response")

        val response = try {
            jsonMapper.readValue<ResponseBody>(body)
        } catch (e: JacksonException) {
            throw AnalysisResponseException("analysis response is not the agreed JSON", e)
        }
        return response.toResult(request.notes.map { it.idx })
    }

    private fun AnalysisRequest.toBody() =
        RequestBody(sessionId, attempt, audioUrl, bpm, bpmUnit, notes.map { RequestNote(it.idx, it.semitone, it.beats, it.sigimsae) })

    private fun ResponseBody.toResult(requestedIdx: List<Int>): AnalysisResult = when (status) {
        "DONE" -> {
            val version = modelVersion?.takeIf { it.isNotBlank() && it.length <= MODEL_VERSION_MAX }
                ?: throw AnalysisResponseException("modelVersion must be 1..$MODEL_VERSION_MAX characters")
            val judged = notes ?: throw AnalysisResponseException("DONE without notes")
            if (judged.map { it.idx } != requestedIdx) throw AnalysisResponseException("note idx differ from the request")
            AnalysisResult.Done(version, score(total), score(pitch), score(timing), score(duration), judged.map { it.toJudgement() })
        }
        "FAILED" -> AnalysisResult.Failed(
            AnalysisFailReason.entries.find { it.name == failReason }
                ?: throw AnalysisResponseException("unknown failReason $failReason"),
        )
        else -> throw AnalysisResponseException("unknown status $status")
    }

    private fun score(value: Int?) = value?.takeIf { it in 0..100 } ?: throw AnalysisResponseException("scores must be 0..100")

    private fun ResponseNote.toJudgement() = NoteJudgement(
        idx ?: throw AnalysisResponseException("note without idx"),
        NoteVerdict.entries.find { it.name == verdict } ?: throw AnalysisResponseException("unknown verdict $verdict"),
        pitchErrorCent,
        onsetErrorMs,
        durationErrorMs,
    )

    internal data class RequestBody(
        val sessionId: Long,
        val attempt: Int,
        val audioUrl: String,
        val bpm: Int,
        val bpmUnit: String,
        val notes: List<RequestNote>,
    )

    internal data class RequestNote(val idx: Int, val semitone: Int, val beats: Double, val sigimsae: Boolean)

    // tuningOffsetCent·tuningOutOfRange는 저장할 칸이 없어 읽지 않는다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class ResponseBody(
        val status: String? = null,
        val failReason: String? = null,
        val modelVersion: String? = null,
        val total: Int? = null,
        val pitch: Int? = null,
        val timing: Int? = null,
        val duration: Int? = null,
        val notes: List<ResponseNote>? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class ResponseNote(
        val idx: Int? = null,
        val verdict: String? = null,
        val pitchErrorCent: Double? = null,
        val onsetErrorMs: Int? = null,
        val durationErrorMs: Int? = null,
    )

    private companion object {
        const val MODEL_VERSION_MAX = 30 // evaluations.model_version VARCHAR(30)
    }
}
