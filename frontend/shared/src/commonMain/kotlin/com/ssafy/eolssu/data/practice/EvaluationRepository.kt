package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MetricScore
import com.ssafy.eolssu.core.model.ScoreError

/**
 * 연주 평가. 녹음을 분석에 맡긴 뒤 상태를 물어 결과를 받는다(PRA-03 상태 조회).
 * 상태 조회·재시도 API 모양은 아직 정해지지 않았다. 지금은 [SampleEvaluationRepository]가 녹음 없이 예시 결과를 돌려준다.
 */
interface EvaluationRepository {
    /** 연주를 분석에 맡기고 분석 ID를 돌려준다. */
    suspend fun requestAnalysis(songId: String, bpm: Int, recordedSeconds: Int): String

    /** 분석 상태. 연결 오류는 예외로 던진다. */
    suspend fun analysisStatus(analysisId: String): AnalysisStatus

    /** 같은 녹음으로 다시 분석한다. 아직 분석 중이면 그대로 둔다. */
    suspend fun retryAnalysis(analysisId: String)

    /** 이미 받은 평가 결과. */
    fun result(evaluationId: String): EvaluationResult?
}

/** 서버의 분석 상태(`PENDING`·`DONE`·`FAILED`). */
sealed interface AnalysisStatus {
    data object Pending : AnalysisStatus

    data class Done(val evaluationId: String) : AnalysisStatus

    /** [reason]은 서버 `fail_reason` 코드에 맞는 사유다. */
    data class Failed(val reason: AnalysisFailure) : AnalysisStatus
}

/** 와이어프레임 예시 결과(84점, 틀린 칸 14개). 분석을 맡긴 뒤 [analysisMillis]가 지나면 끝난다. */
class SampleEvaluationRepository(
    private val now: () -> Long,
    private val analysisMillis: Long = 2600,
) : EvaluationRepository {
    private class Analysis(val songId: String, val bpm: Int, val recordedSeconds: Int, var startedAt: Long, var evaluationId: String? = null)

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

    override suspend fun retryAnalysis(analysisId: String) {
        val analysis = analyses.getValue(analysisId)
        if (analysis.evaluationId == null) analysis.startedAt = now()
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

    override fun result(evaluationId: String): EvaluationResult? = results[evaluationId]
}

/** 와이어프레임 예시 오류 위치(9정간 각 기준 칸 번호 → 각·정간). 3–4각에 9개가 몰려 있다. */
internal val SampleErrors: List<ScoreError> = listOf(
    20 to EvaluationMetric.Pitch, 22 to EvaluationMetric.Rhythm, 24 to EvaluationMetric.Pitch, 25 to EvaluationMetric.Length,
    29 to EvaluationMetric.Pitch, 30 to EvaluationMetric.Pitch, 31 to EvaluationMetric.Length, 32 to EvaluationMetric.Rhythm,
    33 to EvaluationMetric.Pitch, 39 to EvaluationMetric.Rhythm, 50 to EvaluationMetric.Pitch, 57 to EvaluationMetric.Pitch,
    77 to EvaluationMetric.Pitch, 86 to EvaluationMetric.Rhythm,
).map { (cell, metric) -> ScoreError(column = cell / 9, row = cell % 9, metric = metric) }
