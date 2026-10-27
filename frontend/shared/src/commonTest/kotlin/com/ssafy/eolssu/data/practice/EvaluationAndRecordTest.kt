package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.testing.runImmediately
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class EvaluationAndRecordTest {

    // 2026-10-20 15:20 KST = 06:20 UTC
    private val oct20 = 1_792_477_200_000L

    /** 분석을 맡기고 바로 상태를 물어 결과를 받는다. 분석 시간이 0인 예시 저장소에서만 쓴다. */
    private suspend fun SampleEvaluationRepository.evaluateNow(): EvaluationResult {
        val done = assertIs<AnalysisStatus.Done>(analysisStatus(requestAnalysis("arirang", 60, 48)))
        return result(done.evaluationId)!!
    }

    @Test
    fun sampleAnalysisIsPendingUntilItsTimePassesAndRetryStartsOver() = runImmediately {
        var now = oct20
        val evaluations = SampleEvaluationRepository(now = { now }, analysisMillis = 2_600)
        val id = evaluations.requestAnalysis("arirang", 60, 48)
        now += 2_599
        assertEquals(AnalysisStatus.Pending, evaluations.analysisStatus(id))
        evaluations.retryAnalysis(id)
        now += 2_599
        assertEquals(AnalysisStatus.Pending, evaluations.analysisStatus(id))
        now += 1
        val done = assertIs<AnalysisStatus.Done>(evaluations.analysisStatus(id))
        assertEquals(done, evaluations.analysisStatus(id))
        assertEquals(84, evaluations.result(done.evaluationId)?.total)
    }

    @Test
    fun koreanDateTimeAddsNineHours() {
        assertEquals(KoreanDateTime(2026, 10, 20, 15, 20), KoreanDateTime.fromEpochMillis(oct20))
        assertEquals(KoreanDateTime(1970, 1, 1, 9, 0), KoreanDateTime.fromEpochMillis(0))
        // UTC 2024-02-28 15:30 → KST 2024-02-29 00:30 (윤일)
        assertEquals(KoreanDateTime(2024, 2, 29, 0, 30), KoreanDateTime.fromEpochMillis(1_709_134_200_000L))
    }

    @Test
    fun sampleErrorsClusterInColumnsThreeAndFour() = runImmediately {
        val result = SampleEvaluationRepository(now = { oct20 }, analysisMillis = 0).evaluateNow()
        assertEquals(14, result.errors.size)
        assertEquals(2 to 9, result.densestColumnPair())
        assertEquals(KoreanDateTime(2026, 10, 20, 15, 20), result.recordedAt)
    }

    @Test
    fun densestPairIsNullWithoutErrors() = runImmediately {
        val result = SampleEvaluationRepository(now = { oct20 }, analysisMillis = 0).evaluateNow()
        assertNull(result.copy(errors = emptyList()).densestColumnPair())
    }

    @Test
    fun savingSameResultTwiceKeepsOneRecordOnTop() = runImmediately {
        val evaluations = SampleEvaluationRepository(now = { oct20 }, analysisMillis = 0)
        val records = SamplePracticeRecordRepository()
        val before = records.records.value.size
        val result = evaluations.evaluateNow()
        val first = records.saveEvaluation(result, "아리랑")
        records.saveEvaluation(result, "아리랑")
        assertEquals(before + 1, records.records.value.size)
        assertEquals(first.id, records.records.value.first().id)
        assertEquals(84, first.score)
    }

    @Test
    fun memoIsUpdated() = runImmediately {
        val records = SamplePracticeRecordRepository()
        val id = records.records.value.first().id
        records.updateMemo(id, "3–4각에서 박자가 밀림")
        assertEquals("3–4각에서 박자가 밀림", records.records.value.first().memo)
    }
}
