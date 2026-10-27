package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.testing.runImmediately
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EvaluationAndRecordTest {

    // 2026-10-20 15:20 KST = 06:20 UTC
    private val oct20 = 1_792_477_200_000L

    @Test
    fun koreanDateTimeAddsNineHours() {
        assertEquals(KoreanDateTime(2026, 10, 20, 15, 20), KoreanDateTime.fromEpochMillis(oct20))
        assertEquals(KoreanDateTime(1970, 1, 1, 9, 0), KoreanDateTime.fromEpochMillis(0))
        // UTC 2024-02-28 15:30 → KST 2024-02-29 00:30 (윤일)
        assertEquals(KoreanDateTime(2024, 2, 29, 0, 30), KoreanDateTime.fromEpochMillis(1_709_134_200_000L))
    }

    @Test
    fun sampleErrorsClusterInColumnsThreeAndFour() = runImmediately {
        val result = SampleEvaluationRepository(now = { oct20 }, analysisMillis = 0).evaluate("arirang", 60, 48)
        assertEquals(14, result.errors.size)
        assertEquals(2 to 9, result.densestColumnPair())
        assertEquals(KoreanDateTime(2026, 10, 20, 15, 20), result.recordedAt)
    }

    @Test
    fun densestPairIsNullWithoutErrors() = runImmediately {
        val result = SampleEvaluationRepository(now = { oct20 }, analysisMillis = 0).evaluate("arirang", 60, 48)
        assertNull(result.copy(errors = emptyList()).densestColumnPair())
    }

    @Test
    fun savingSameResultTwiceKeepsOneRecordOnTop() = runImmediately {
        val evaluations = SampleEvaluationRepository(now = { oct20 }, analysisMillis = 0)
        val records = SamplePracticeRecordRepository()
        val before = records.records.value.size
        val result = evaluations.evaluate("arirang", 60, 48)
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
