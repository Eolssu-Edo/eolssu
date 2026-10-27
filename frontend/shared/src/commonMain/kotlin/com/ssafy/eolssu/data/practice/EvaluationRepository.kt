package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MetricScore
import com.ssafy.eolssu.core.model.ScoreError
import kotlinx.coroutines.delay

/**
 * 연주 평가. 와이어프레임 API 정리의 `POST /evaluations`·`GET /evaluations/{id}/result`에 대응한다.
 * 지금은 [SampleEvaluationRepository]가 녹음 없이 예시 결과를 돌려준다.
 */
interface EvaluationRepository {
    /** 연주를 평가한다. 분석이 실패하면 예외를 던진다. */
    suspend fun evaluate(songId: String, bpm: Int, recordedSeconds: Int): EvaluationResult

    /** 이미 받은 평가 결과. */
    fun result(evaluationId: String): EvaluationResult?
}

/** 와이어프레임 예시 결과(84점, 틀린 칸 14개). 분석 시간처럼 잠깐 기다린 뒤 돌려준다. */
class SampleEvaluationRepository(
    private val now: () -> Long,
    private val analysisMillis: Long = 2600,
) : EvaluationRepository {
    private val results = mutableMapOf<String, EvaluationResult>()
    private var nextId = 1

    override suspend fun evaluate(songId: String, bpm: Int, recordedSeconds: Int): EvaluationResult {
        delay(analysisMillis)
        val result = EvaluationResult(
            id = "evaluation-${nextId++}",
            songId = songId,
            total = 84,
            feedback = "좋아요! 박자를 조금 더 맞춰볼까요?",
            metrics = listOf(
                MetricScore(EvaluationMetric.Pitch, 88),
                MetricScore(EvaluationMetric.Rhythm, 80),
                MetricScore(EvaluationMetric.Length, 84),
            ),
            errors = SampleErrors,
            bpm = bpm,
            recordedSeconds = recordedSeconds,
            recordedAt = KoreanDateTime.fromEpochMillis(now()),
            sample = true,
        )
        results[result.id] = result
        return result
    }

    override fun result(evaluationId: String): EvaluationResult? = results[evaluationId]
}

/** 와이어프레임 예시 오류 위치(9정간 각 기준 칸 번호 → 각·정간). 3–4각에 9개가 몰려 있다. */
internal val SampleErrors: List<ScoreError> = listOf(
    20 to EvaluationMetric.Pitch, 22 to EvaluationMetric.Rhythm, 24 to EvaluationMetric.Pitch, 25 to EvaluationMetric.Length,
    29 to EvaluationMetric.Pitch, 30 to EvaluationMetric.Pitch, 31 to EvaluationMetric.Length, 32 to EvaluationMetric.Rhythm,
    33 to EvaluationMetric.Pitch, 39 to EvaluationMetric.Rhythm, 50 to EvaluationMetric.Pitch, 57 to EvaluationMetric.Pitch,
    77 to EvaluationMetric.Pitch, 86 to EvaluationMetric.Rhythm,
).map { (cell, metric) -> ScoreError(column = cell / 9, row = cell % 9, metric = metric) }
