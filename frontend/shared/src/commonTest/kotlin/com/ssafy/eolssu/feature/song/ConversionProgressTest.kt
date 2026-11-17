package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.data.song.SheetConversion
import com.ssafy.eolssu.data.song.SheetNotation
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

    @Test
    fun pagesReadAreShownOnlyWhenTheServerReportsThem() {
        // 서버는 인식 중 읽은 쪽 수를 0으로 두고, 인식이 끝나 만들기로 넘어갈 때 한 번에 채운다.
        assertEquals(ConversionStep.Reading(pageCount = 3, pagesRead = null), conversionStep(running(SheetConversionStage.Recognizing, 0, 3)))
        assertEquals(ConversionStep.Reading(pageCount = 3, pagesRead = 1), conversionStep(running(SheetConversionStage.Recognizing, 1, 3)))
        assertEquals(ConversionStep.Reading(pageCount = 3, pagesRead = 3), conversionStep(running(SheetConversionStage.Recognizing, 3, 3)))
        assertEquals(ConversionStep.Reading(pageCount = 0, pagesRead = null), conversionStep(running(null)))
        assertEquals(ConversionStep.Queued, conversionStep(SheetConversion("s1", SheetConversionStatus.Queued, pageCount = 2)))
        assertEquals(ConversionStep.Building, conversionStep(running(SheetConversionStage.BuildingScore, 3, 3)))
        assertNull(conversionStep(SheetConversion("s1", SheetConversionStatus.Done)))
        assertNull(conversionStep(SheetConversion("s1", SheetConversionStatus.Failed)))
    }

    @Test
    fun readingBarCreepsWithElapsedTimeButStaysBelowBuilding() {
        val reading = running(SheetConversionStage.Recognizing, 0, 2)
        val start = assertNotNullProgress(reading)
        val later = conversionProgress(reading, readingElapsedMs = 30_000) ?: error("진행 값")
        val muchLater = conversionProgress(reading, readingElapsedMs = 3_600_000) ?: error("진행 값")
        val building = assertNotNullProgress(running(SheetConversionStage.BuildingScore, 2, 2))
        assertTrue(later > start, "읽는 중 시간이 지나면 막대가 조금씩 찬다: $start → $later")
        assertTrue(muchLater in later..<building, "오래 걸려도 만들기 단계 값을 넘지 않는다: $muchLater < $building")
    }

    @Test
    fun readingEstimateIsNearlyFullWhenReadingUsuallyEnds() {
        // 에뮬레이터 실측(2026-11-16): 2쪽 오선보가 읽기에 약 65초 걸렸는데 막대는 54%여서 끝날 때 확 차 보였다.
        val atTypicalEnd = readingEstimate(64_000, pageCount = 2, notation = SheetNotation.Staff)
        assertTrue(atTypicalEnd >= 0.8f, "평소 끝나는 때에는 읽기 구간을 거의 채운다: $atTypicalEnd")
        val halfway = readingEstimate(32_000, pageCount = 2, notation = SheetNotation.Staff)
        assertTrue(halfway in 0.4f..0.75f, "중간쯤에는 절반 안팎이다: $halfway")
        val reading = running(SheetConversionStage.Recognizing, 0, 2).copy(notation = SheetNotation.Staff)
        val nearEnd = conversionProgress(reading, readingElapsedMs = 64_000) ?: error("진행 값")
        assertTrue(nearEnd >= 0.7f, "막대 전체로도 끝나기 직전에 70% 이상이다: $nearEnd")
    }

    @Test
    fun readingEstimateIsSlowerForMorePagesAndForStaffScores() {
        val onePage = readingEstimate(20_000, pageCount = 1, notation = SheetNotation.Staff)
        val fivePages = readingEstimate(20_000, pageCount = 5, notation = SheetNotation.Staff)
        val jeongganbo = readingEstimate(20_000, pageCount = 1, notation = SheetNotation.Jeongganbo)
        assertTrue(fivePages < onePage, "쪽이 많으면 같은 시간에 덜 찬다")
        assertTrue(jeongganbo > onePage, "정간보는 오선보보다 빨리 읽는다")
        assertEquals(0f, readingEstimate(0, 1, null))
        assertTrue(readingEstimate(Long.MAX_VALUE / 2, 1, null) < 1f, "끝났다는 응답 전에는 다 차지 않는다")
    }

    @Test
    fun listClockCountsReadingTimePerSheetAndResetsAfterTheConversionEnds() {
        // 목록 막대가 '읽는 중' 내내 같은 값에 멈춰 있다가 끝날 때 확 차던 문제(S15P31E102-1064).
        var now = 1_000L
        val clock = ConversionReadingClock { now }
        val queued = SheetConversion("s1", SheetConversionStatus.Queued, pageCount = 2)
        val reading = running(SheetConversionStage.Recognizing, 0, 2)
        assertEquals(0, clock.elapsedMs(queued), "대기 중에는 세지 않는다")
        now = 5_000
        assertEquals(0, clock.elapsedMs(reading), "읽기를 처음 본 때부터 센다")
        now = 25_000
        assertEquals(20_000, clock.elapsedMs(reading))
        assertEquals(0, clock.elapsedMs(reading.copy(sheetId = "s2")), "악보마다 따로 센다")
        assertTrue(conversionProgress(reading, clock.elapsedMs(reading))!! > conversionProgress(reading)!!, "지난 시간만큼 막대가 찬다")
        assertEquals(0, clock.elapsedMs(running(SheetConversionStage.BuildingScore, 2, 2)), "만드는 중은 단계 값을 쓴다")

        clock.elapsedMs(SheetConversion("s1", SheetConversionStatus.Failed))
        now = 40_000
        assertEquals(0, clock.elapsedMs(reading), "실패 뒤 다시 시도하면 처음부터 센다")
    }

    private fun assertNotNullProgress(conversion: SheetConversion): Float =
        conversionProgress(conversion) ?: error("변환 중이면 진행 값이 있어야 한다: $conversion")
}
