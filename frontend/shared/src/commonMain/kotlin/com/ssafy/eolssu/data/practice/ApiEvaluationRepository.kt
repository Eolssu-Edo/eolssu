package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.diagnostics.PerfSource
import com.ssafy.eolssu.core.diagnostics.PerfReason
import com.ssafy.eolssu.core.diagnostics.PerformanceReason
import com.ssafy.eolssu.core.diagnostics.PerfTarget
import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.diagnostics.measureLoad
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.EvaluationNote
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MetricScore
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.inSession
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
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/**
 * 서버의 분석 상태(PRA-03 `GET /api/practice-sessions/{sessionId}`)와 평가 결과(`GET .../{sessionId}/evaluation`).
 * 분석 다시 시도는 `POST .../{sessionId}/retry`다. 서버 분석은 연습 세션 ID로 묻는다. 그래서 분석 ID와 결과를 찾는 키가 모두 세션 ID이고, 서버 ID는 숫자 문자열이다.
 * 출시 DI에는 [fallback]을 주입하지 않는다. 테스트에서만 예시 분석 대역을 명시적으로 넣는다.
 */
class ApiEvaluationRepository(
    private val client: HttpClient,
    private val songs: SongRepository,
    private val fallback: EvaluationRepository? = null,
    private val member: SignedInMember? = null,
    private val newKey: () -> String = { Uuid.random().toString() },
) : EvaluationRepository {
    override suspend fun requestAnalysis(songId: String, bpm: Int, recordedSeconds: Int): String =
        requireNotNull(fallback) { "실제 녹음 세션이 필요합니다" }.requestAnalysis(songId, bpm, recordedSeconds)

    /** 상태. 응답을 받지 못했거나 200이 아니면 예외를 던진다(분석 대기 화면은 응답 없음으로 센다). */
    override suspend fun analysisStatus(analysisId: String): AnalysisStatus = member.inSession {
        if (!analysisId.isServerId()) return@inSession requireNotNull(fallback).analysisStatus(analysisId)
        val response = client.sendOrNull { get("/api/practice-sessions/$analysisId") }
        if (response == null || response.status != HttpStatusCode.OK) error("상태 응답 없음: ${response?.status}")
        val session = checkNotNull(response.apiResponse<SessionStatus>()?.result) { "상태 형식이 다름" }
        return@inSession when (session.status) {
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
    override suspend fun retryAnalysis(analysisId: String): RetryOutcome = member.inSession {
        if (!analysisId.isServerId()) return@inSession requireNotNull(fallback).retryAnalysis(analysisId)
        val response = client.sendOrNull {
            post("/api/practice-sessions/$analysisId/retry") { header(IDEMPOTENCY_KEY, newKey()) }
        } ?: error("다시 시도 응답 없음")
        return@inSession when (response.status) {
            HttpStatusCode.Accepted -> RetryOutcome.Started
            HttpStatusCode.Conflict -> RetryOutcome.Conflict
            HttpStatusCode.NotFound -> RetryOutcome.NotFound
            HttpStatusCode.Unauthorized -> RetryOutcome.Unauthorized
            HttpStatusCode.ServiceUnavailable -> RetryOutcome.Busy
            else -> error("다시 시도 응답: ${response.status}")
        }
    }

    /** 조회마다 서버 소유권을 확인한다. 다른 로그인 세션에 이전 회원의 캐시를 노출하지 않는다. */
    override suspend fun result(evaluationId: String): EvaluationResult? = member.inSession {
        if (!evaluationId.isServerId()) return@inSession fallback?.result(evaluationId)
        val evaluation = fetchEvaluation(evaluationId) ?: return@inSession null
        loadDetails(evaluation, initialSong = null)?.first
    }

    override suspend fun resultForScreen(evaluationId: String, initialSong: Song?): ScreenEvaluation? = member.inSession {
        if (!evaluationId.isServerId()) return@inSession fallback?.resultForScreen(evaluationId, initialSong)
        val account = member?.session?.value
        val evaluation = fetchEvaluation(evaluationId) ?: return@inSession null
        ScreenEvaluation(evaluation.toResult()) {
            member.inSession {
                if (member?.session?.value != account) throw CancellationException("로그인 세션이 변경됨")
                loadDetails(evaluation, initialSong)
            }
        }
    }

    private suspend fun fetchEvaluation(evaluationId: String): Evaluation? =
        measureLoad(PerfTarget.EVALUATION, evaluationId, { it.scoreVersion }) { loadEvaluation(evaluationId) }

    private suspend fun loadEvaluation(evaluationId: String): Evaluation? {
        val response = client.sendOrNull { get("/api/practice-sessions/$evaluationId/evaluation") }
        if (response?.status != HttpStatusCode.OK) return null
        val evaluation = response.apiResponse<Evaluation>()?.result ?: return null
        return evaluation.takeIf { it.sessionId == evaluationId && it.songId.isServerId() }
    }

    private suspend fun loadDetails(evaluation: Evaluation, initialSong: Song?): Pair<EvaluationResult, Song>? {
        val carried = initialSong?.takeIf { it.id == evaluation.songId && it.scoreVersion == evaluation.scoreVersion }
        if (carried != null) currentCoroutineContext()[PerformanceVisit]?.reuse(
            PerfTarget.SONG_BUNDLE, carried.id, PerfSource.NAVIGATION, carried.scoreVersion,
        )
        val song = carried ?: if (initialSong?.id == evaluation.songId && currentCoroutineContext()[PerformanceVisit] != null) {
            withContext(PerformanceReason(PerfReason.VERSION_MISMATCH)) { songs.getSong(evaluation.songId, evaluation.scoreVersion) }
        } else songs.getSong(evaluation.songId, evaluation.scoreVersion)
        if (song == null) return null
        if (song.id != evaluation.songId || song.scoreVersion != evaluation.scoreVersion) return null
        return evaluation.toResult().withScore(song) to song
    }

    private fun Evaluation.toResult(): EvaluationResult {
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
            errors = emptyList(),
            bpm = bpm,
            recordedSeconds = recordingDurationMs?.let { (it / 1000.0).roundToInt() }
                ?: endedMillis?.let { ((it - startedMillis) / 1000.0).roundToInt() } ?: 0,
            recordedAt = KoreanDateTime.fromEpochMillis(startedMillis),
            notes = notes.map { EvaluationNote(it.noteIdx, it.verdict) },
            sample = false,
            scoreVersion = scoreVersion,
        )
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
        val scoreVersion: Int,
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
