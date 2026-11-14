package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.data.song.SheetConversion
import com.ssafy.eolssu.data.song.SheetConversionStage
import com.ssafy.eolssu.data.song.SheetConversionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversionProgressTest {
    private fun running(stage: SheetConversionStage?, processed: Int = 0, pages: Int = 0) =
        SheetConversion("s1", SheetConversionStatus.Running, stage, processedPageCount = processed, pageCount = pages)

    @Test
    fun barMovesForwardThroughQueuedReadingAndBuilding() {
        val steps = listOf(
            SheetConversion("s1", SheetConversionStatus.Queued),
            running(SheetConversionStage.Recognizing, 0, 3),
            running(SheetConversionStage.Recognizing, 1, 3),
            running(SheetConversionStage.Recognizing, 3, 3),
            running(SheetConversionStage.BuildingScore, 3, 3),
        ).map { assertNotNullProgress(it) }
        steps.zipWithNext().forEach { (before, after) -> assertTrue(after > before, "진행 막대는 단계가 나아갈 때 앞으로만 간다: $steps") }
        assertTrue(steps.first() > 0f, "대기 중에도 빈 막대가 아니다")
        assertTrue(steps.last() < 1f, "만드는 중에는 끝까지 차지 않는다")
    }

    @Test
    fun readingFillsByPagesReadBetweenStartAndBuilding() {
        val start = assertNotNullProgress(running(SheetConversionStage.Recognizing, 0, 4))
        val half = assertNotNullProgress(running(SheetConversionStage.Recognizing, 2, 4))
        val end = assertNotNullProgress(running(SheetConversionStage.Recognizing, 4, 4))
        assertTrue(end - start > 0.5f, "읽는 단계가 막대의 대부분을 차지한다: $start → $end")
        assertEquals((start + end) / 2, half, 0.0001f, "읽은 쪽 수에 비례해 찬다")
    }

    @Test
    fun unknownPagesOrStageStayAtReadingStart() {
        val start = conversionProgress(running(SheetConversionStage.Recognizing, 0, 3))
        assertEquals(start, conversionProgress(running(SheetConversionStage.Recognizing, 0, 0)))
        assertEquals(start, conversionProgress(running(null, 0, 0)))
    }

    @Test
    fun finishedConversionsHaveNoBar() {
        assertNull(conversionProgress(SheetConversion("s1", SheetConversionStatus.Done)))
        assertNull(conversionProgress(SheetConversion("s1", SheetConversionStatus.Failed)))
    }

    private fun assertNotNullProgress(conversion: SheetConversion): Float =
        conversionProgress(conversion) ?: error("변환 중이면 진행 값이 있어야 한다: $conversion")
}
