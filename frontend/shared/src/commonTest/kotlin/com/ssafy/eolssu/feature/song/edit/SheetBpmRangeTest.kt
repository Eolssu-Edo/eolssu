package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.HanbaeMark
import com.ssafy.eolssu.data.song.smallScoreData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 악보 빠르기는 어느 연습 빠르기(0.6~1.2배)로도 평가(20~240)가 거절되지 않는 40~200만 받는다(S15P31E102-1018). */
internal class SheetBpmRangeTest {
    private fun draft() = smallScoreData().editingDraft("빠르기 범위", 60, null)

    @Test fun rangeKeepsEveryPracticeStepInsideTheEvaluationRange() {
        assertEquals(40..200, SheetBpmRange)
        assertTrue(SheetBpmRange.first * 0.6 >= 20)
        assertTrue(SheetBpmRange.last * 1.2 <= 240)
    }

    @Test fun boundariesAreInclusive() {
        assertTrue(40 in SheetBpmRange && 200 in SheetBpmRange)
        assertTrue(39 !in SheetBpmRange && 201 !in SheetBpmRange)
        // 저장 길이 추정도 같은 범위를 쓴다.
        assertNull(draft().estimatedDurationSeconds(201))
        assertNotNull(draft().estimatedDurationSeconds(200))
    }

    @Test fun sectionTargetUsesTheSameRange() {
        val draft = draft()
        assertNotNull(draft.tempoChangeError(SheetTempoChange(1, HanbaeMark.Slower, 39.0, 2)))
        assertNotNull(draft.tempoChangeError(SheetTempoChange(1, HanbaeMark.Faster, 201.0, 2)))
        assertNull(draft.tempoChangeError(SheetTempoChange(1, HanbaeMark.Faster, 200.0, 2)))
    }
}
