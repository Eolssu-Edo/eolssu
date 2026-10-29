package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MetricScore
import com.ssafy.eolssu.core.model.ScoreError
import com.ssafy.eolssu.core.model.ScoreEvent
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.sendOrNull
import com.ssafy.eolssu.data.song.SongRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

/**
 * 서버의 분석 상태(PRA-03 `GET /api/practice-sessions/{sessionId}`)와 평가 결과(`GET .../{sessionId}/evaluation`).
 * 분석 다시 시도는 `POST .../{sessionId}/retry`다. 서버 분석은 연습 세션 ID로 묻는다. 그래서 분석 ID와 결과를 찾는 키가 모두 세션 ID이고, 서버 ID는 숫자 문자열이다.
 * 숫자가 아닌 ID는 녹음 없이 진행하는 플랫폼(현재 iOS·Web)의 예시 분석이라 [fallback]이 맡는다.
 */
class ApiEvaluationRepository(
    private val client: HttpClient,
    private val songs: SongRepository,
    private val fallback: EvaluationRepository,
    private val newKey: () -> String = { Uuid.random().toString() },
) : EvaluationRepository {
    private val results = mutableMapOf<String, EvaluationResult>()

    override suspend fun requestAnalysis(songId: String, bpm: Int, recordedSeconds: Int): String =
        fallback.requestAnalysis(songId, bpm, recordedSeconds)

    /** 상태. 응답을 받지 못했거나 200이 아니면 예외를 던진다(분석 대기 화면은 응답 없음으로 센다). */
    override suspend fun analysisStatus(analysisId: String): AnalysisStatus {
        if (!analysisId.isServerId()) return fallback.analysisStatus(analysisId)
        val response = client.sendOrNull { get("/api/practice-sessions/$analysisId") }
        if (response == null || response.status != HttpStatusCode.OK) error("상태 응답 없음: ${response?.status}")
        val session = checkNotNull(response.apiResponse<SessionStatus>()?.result) { "상태 형식이 다름" }
        return when (session.status) {
            "DONE" -> AnalysisStatus.Done(analysisId)
            "FAILED" -> AnalysisStatus.Failed(AnalysisFailure.fromCode(session.failReason))
            // RECORDING은 업로드가 아직 반영되지 않은 순간이라 기다린다.
            else -> AnalysisStatus.Pending
        }
    }

    /**
     * 다시 시도. 본문은 없고 `Idempotency-Key`는 필수다. 서버는 키를 저장하지 않고(이미 `PENDING`이면 같은 202) 버튼을 누를 때마다 새 키를 쓴다.
     * 응답을 받지 못하면 예외를 던진다.
     */
    override suspend fun retryAnalysis(analysisId: String): RetryOutcome {
        if (!analysisId.isServerId()) return fallback.retryAnalysis(analysisId)
        val response = client.sendOrNull {
            post("/api/practice-sessions/$analysisId/retry") { header(IDEMPOTENCY_KEY, newKey()) }
        } ?: error("다시 시도 응답 없음")
        return when (response.status) {
            HttpStatusCode.Accepted -> RetryOutcome.Started
            HttpStatusCode.Conflict -> RetryOutcome.Conflict
            HttpStatusCode.NotFound -> RetryOutcome.NotFound
            HttpStatusCode.Unauthorized -> RetryOutcome.Unauthorized
            HttpStatusCode.ServiceUnavailable -> RetryOutcome.Busy
            else -> error("다시 시도 응답: ${response.status}")
        }
    }

    /** 결과. 서버 결과는 한 번 받아 두고, 받지 못하면 null이다. */
    override suspend fun result(evaluationId: String): EvaluationResult? {
        if (!evaluationId.isServerId()) return fallback.result(evaluationId)
        results[evaluationId]?.let { return it }
        val response = client.sendOrNull { get("/api/practice-sessions/$evaluationId/evaluation") }
        if (response?.status != HttpStatusCode.OK) return null
        val evaluation = response.apiResponse<Evaluation>()?.result ?: return null
        return evaluation.toResult(songs.getSong(evaluation.songId)?.timeline).also { results[evaluationId] = it }
    }

    private fun Evaluation.toResult(timeline: List<ScoreEvent>?): EvaluationResult {
        val startedMillis = Instant.parse(startedAt).toEpochMilliseconds()
        val endedMillis = endedAt?.let { Instant.parse(it).toEpochMilliseconds() }
        return EvaluationResult(
            id = sessionId,
            songId = songId,
            total = totalScore,
            // 서버는 총평을 주지 않고, 앱이 임의로 만들지 않는다.
            feedback = "",
            metrics = listOf(
                MetricScore(EvaluationMetric.Pitch, pitchScore),
                MetricScore(EvaluationMetric.Rhythm, timingScore),
                MetricScore(EvaluationMetric.Length, durationScore),
            ),
            errors = timeline?.let { noteErrors(it) }.orEmpty(),
            bpm = bpm,
            recordedSeconds = recordingDurationMs?.let { (it / 1000.0).roundToInt() }
                ?: endedMillis?.let { ((it - startedMillis) / 1000.0).roundToInt() } ?: 0,
            recordedAt = KoreanDateTime.fromEpochMillis(startedMillis),
            sample = false,
        )
    }

    /**
     * 틀린 음표를 악보 칸에 놓는다. `noteIdx`는 같은 악보에서 율명 음(지속·쉼 제외)만 연주 순서로 센 번호다.
     * `PITCH`는 음높이, `TIMING`은 박자로 보인다. `MISSED`(소리 없음)는 앱 항목에 맞는 것이 없어 칸에 표시하지 않는다.
     */
    private fun Evaluation.noteErrors(timeline: List<ScoreEvent>): List<ScoreError> {
        val pitches = timeline.filter { it.note is ScoreNote.Pitch }
        return notes.mapNotNull { note ->
            val metric = when (note.verdict) {
                "PITCH" -> EvaluationMetric.Pitch
                "TIMING" -> EvaluationMetric.Rhythm
                else -> return@mapNotNull null
            }
            pitches.getOrNull(note.noteIdx)?.let { ScoreError(column = it.column, row = it.row, metric = metric) }
        }.distinct()
    }

    private companion object {
        const val IDEMPOTENCY_KEY = "Idempotency-Key"
    }

    @Serializable
    private class SessionStatus(val status: String, val failReason: String? = null)

    @Serializable
    private class Evaluation(
        val sessionId: String,
        val songId: String,
        val bpm: Int,
        val startedAt: String,
        val endedAt: String? = null,
        val totalScore: Int,
        val pitchScore: Int,
        val timingScore: Int,
        val durationScore: Int,
        val notes: List<Note> = emptyList(),
        val recordingDurationMs: Int? = null,
    )

    @Serializable
    private class Note(val noteIdx: Int, val verdict: String)
}

/** 서버 DB ID(양의 정수 문자열)인지. */
private fun String.isServerId(): Boolean = isNotEmpty() && all { it in '0'..'9' }
