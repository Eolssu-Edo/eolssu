package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MetricScore
import com.ssafy.eolssu.core.model.ScoreError
import com.ssafy.eolssu.core.model.Song

/**
 * 연주 평가. 녹음을 분석에 맡긴 뒤 상태를 물어 결과를 받는다(PRA-03 상태 조회).
 * 서버 분석은 [ApiEvaluationRepository]가, 녹음 없이 진행하는 플랫폼의 예시 분석은 [SampleEvaluationRepository]가 맡는다.
 */
interface EvaluationRepository {
    /** 녹음 없이 연주를 분석에 맡기고 분석 ID를 돌려준다(예시 분석). 녹음은 업로드가 분석을 시작한다. */
    suspend fun requestAnalysis(songId: String, bpm: Int, recordedSeconds: Int): String

    /** 분석 상태. 연결 오류는 예외로 던진다. */
    suspend fun analysisStatus(analysisId: String): AnalysisStatus

    /** 같은 녹음으로 다시 분석한다. 아직 분석 중이면 그대로 둔다. 응답을 받지 못하면 예외를 던진다. */
    suspend fun retryAnalysis(analysisId: String): RetryOutcome

    /** 평가 결과. 찾지 못하면 null. */
    suspend fun result(evaluationId: String): EvaluationResult?

    /** 화면에서 점수를 먼저 표시하고 상세 조회를 다시 시도할 수 있게 한다. */
    suspend fun resultForScreen(evaluationId: String, initialSong: Song?): ScreenEvaluation? =
        result(evaluationId)?.let { result -> ScreenEvaluation(result) { result to null } }
}

/** 이미 받은 점수와, 같은 평가 응답을 악보에 매핑하는 작업. */
class ScreenEvaluation(
    val scores: EvaluationResult,
    val loadDetails: suspend () -> Pair<EvaluationResult, Song?>?,
)

/** 분석 다시 시도(PRA-04 `POST /api/practice-sessions/{sessionId}/retry`)의 응답. */
sealed interface RetryOutcome {
    /** 시도 구분 헤더가 없는 이전 서버의 202 응답이다. */
    data object Started : RetryOutcome

    /** 서버가 응답에 명시한 시도다. [timingId]는 실제로 접수된 작업의 진단 ID다. */
    data class Accepted(val started: Boolean, val timingId: String?) : RetryOutcome

    /** 다시 시도할 수 없는 상태다(409: 재녹음이 필요한 실패·완료·업로드 전). 상태를 다시 물어 그 상태의 화면을 보인다. */
    data object Conflict : RetryOutcome

    /** 서버가 지금 받지 못한다(503). 버튼을 그대로 두고 잠시 후 다시 시도하게 한다. */
    data object Busy : RetryOutcome

    /** 세션이 없다(404). */
    data object NotFound : RetryOutcome

    /** 토큰을 갱신해도 401이다. 다시 로그인해야 한다. */
    data object Unauthorized : RetryOutcome
}

/** 서버의 분석 상태(`PENDING`·`DONE`·`FAILED`). */
sealed interface AnalysisStatus {
    data object Pending : AnalysisStatus

    /** [evaluationId]는 결과 화면이 [EvaluationRepository.result]로 찾는 키다. 서버 결과는 세션 ID로 찾는다. */
    data class Done(val evaluationId: String, val timingId: String? = null) : AnalysisStatus

    /** [reason]은 서버 `fail_reason` 코드에 맞는 사유다. */
    data class Failed(val reason: AnalysisFailure) : AnalysisStatus
}

/** 와이어프레임 예시 결과(84점, 틀린 칸 14개). 분석을 맡긴 뒤 [analysisMillis]가 지나면 끝난다. */
class SampleEvaluationRepository(
    private val now: () -> Long,
    private val analysisMillis: Long = 2600,
) : EvaluationRepository {
    private class Analysis(val songId: String, val bpm: Int, val recordedSeconds: Int, val startedAt: Long, var evaluationId: String? = null)

    private val analyses = mutableMapOf<String, Analysis>()
    private val results = mutableMapOf<String, EvaluationResult>()
    private var nextId = 1

    override suspend fun requestAnalysis(songId: String, bpm: Int, recordedSeconds: Int): String {
        val id = "analysis-${nextId++}"
        analyses[id] = Analysis(songId, bpm, recordedSeconds, startedAt = now())
        return id
    }

    override suspend fun analysisStatus(analysisId: String): AnalysisStatus {
        val analysis = analyses.getValue(analysisId)
        analysis.evaluationId?.let { return AnalysisStatus.Done(it) }
        if (now() - analysis.startedAt < analysisMillis) return AnalysisStatus.Pending
        val result = sampleResult(analysis)
        results[result.id] = result
        analysis.evaluationId = result.id
        return AnalysisStatus.Done(result.id)
    }

    /** 예시 분석은 실패하지 않으므로 다시 시도할 일은 늘 '아직 분석 중'이고, 그대로 둔다. */
    override suspend fun retryAnalysis(analysisId: String): RetryOutcome {
        analyses.getValue(analysisId)
        return RetryOutcome.Started
    }

    private fun sampleResult(analysis: Analysis): EvaluationResult =
        EvaluationResult(
            id = "evaluation-${nextId++}",
            songId = analysis.songId,
            total = 84,
            feedback = "좋아요! 박자를 조금 더 맞춰볼까요?",
            metrics = listOf(
                MetricScore(EvaluationMetric.Pitch, 88),
                MetricScore(EvaluationMetric.Rhythm, 80),
                MetricScore(EvaluationMetric.Length, 84),
            ),
            errors = SampleErrors,
            bpm = analysis.bpm,
            recordedSeconds = analysis.recordedSeconds,
            recordedAt = KoreanDateTime.fromEpochMillis(now()),
            sample = true,
        )

    override suspend fun result(evaluationId: String): EvaluationResult? = results[evaluationId]
}

/** 와이어프레임 예시 오류 위치(9정간 각 기준 칸 번호 → 각·정간). 3–4각에 9개가 몰려 있다. */
internal val SampleErrors: List<ScoreError> = listOf(
    20 to EvaluationMetric.Pitch, 22 to EvaluationMetric.Rhythm, 24 to EvaluationMetric.Pitch, 25 to EvaluationMetric.Length,
    29 to EvaluationMetric.Pitch, 30 to EvaluationMetric.Pitch, 31 to EvaluationMetric.Length, 32 to EvaluationMetric.Rhythm,
    33 to EvaluationMetric.Pitch, 39 to EvaluationMetric.Rhythm, 50 to EvaluationMetric.Pitch, 57 to EvaluationMetric.Pitch,
    77 to EvaluationMetric.Pitch, 86 to EvaluationMetric.Rhythm,
).map { (cell, metric) -> ScoreError(column = cell / 9, row = cell % 9, metric = metric) }
