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
            // 오류 본문은 요청(서명 URL)을 되돌려 줄 수 있어 예외에 넣지 않는다.
            .onStatus({ it.isError }) { _, response -> throw AnalysisResponseException("AI responded ${response.statusCode.value()}") }
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
        RequestBody(sessionId, attempt, audioUrl, bpm, bpmUnit, notes.map { RequestNote(it.idx, it.semitone, it.startBeat, it.beats, it.sigimsae) })

    private fun ResponseBody.toResult(requestedIdx: List<Int>): AnalysisResult = when (status) {
        "DONE" -> {
            val version = modelVersion?.takeIf { it.isNotBlank() && it.length <= MODEL_VERSION_MAX }
                ?: throw AnalysisResponseException("modelVersion must be 1..$MODEL_VERSION_MAX characters")
            val judged = notes ?: throw AnalysisResponseException("DONE without notes")
            if (judged.map { it.idx } != requestedIdx) throw AnalysisResponseException("note idx differ from the request")
            if ((tuningOffsetCent == null) != (tuningOutOfRange == null) || tuningOffsetCent?.isFinite() == false) {
                throw AnalysisResponseException("tuning metadata must be a finite offset and a flag, or both absent")
            }
            AnalysisResult.Done(
                version, score(total), score(pitch), score(timing), score(duration), judged.map { it.toJudgement() },
                tuningOffsetCent, tuningOutOfRange,
            )
        }
        "FAILED" -> AnalysisResult.Failed(
            AnalysisFailReason.entries.find { it.name == failReason }
                ?: throw AnalysisResponseException("unknown failReason $failReason"),
        )
        else -> throw AnalysisResponseException("unknown status $status")
    }

    private fun score(value: Int?) = value?.takeIf { it in 0..100 } ?: throw AnalysisResponseException("scores must be 0..100")

    private fun ResponseNote.toJudgement(): NoteJudgement {
        val judged = NoteVerdict.entries.find { it.name == verdict } ?: throw AnalysisResponseException("unknown verdict $verdict")
        if (recordingOffsetMs != null && recordingOffsetMs < 0) throw AnalysisResponseException("recordingOffsetMs must not be negative")
        // 새 필드는 이전 AI 응답에 없어 null로 받는다. MISSED는 소리를 찾지 못한 음표라 인식 음·녹음 위치를 저장하지 않는다.
        val heard = judged != NoteVerdict.MISSED
        return NoteJudgement(
            idx ?: throw AnalysisResponseException("note without idx"),
            judged,
            pitchErrorCent,
            onsetErrorMs,
            durationErrorMs,
            detectedSemitone.takeIf { heard },
            recordingOffsetMs.takeIf { heard },
        )
    }

    internal data class RequestBody(
        val sessionId: Long,
        val attempt: Int,
        val audioUrl: String,
        val bpm: Int,
        val bpmUnit: String,
        val notes: List<RequestNote>,
    )

    internal data class RequestNote(val idx: Int, val semitone: Int, val startBeat: Double, val beats: Double, val sigimsae: Boolean)

    // 이전 AI 응답의 미수집 값은 null로 보존한다. false나 보정 적용량 0으로 바꾸지 않는다.
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
        val tuningOffsetCent: Double? = null,
        val tuningOutOfRange: Boolean? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class ResponseNote(
        val idx: Int? = null,
        val verdict: String? = null,
        val pitchErrorCent: Double? = null,
        val onsetErrorMs: Int? = null,
        val durationErrorMs: Int? = null,
        val detectedSemitone: Int? = null,
        val recordingOffsetMs: Int? = null,
    )

    private companion object {
        const val MODEL_VERSION_MAX = 30 // evaluations.model_version VARCHAR(30)
    }
}
