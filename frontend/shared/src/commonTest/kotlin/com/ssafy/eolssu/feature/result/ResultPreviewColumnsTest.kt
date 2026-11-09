package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.feature.result.component.previewColumns
import kotlin.test.Test
import kotlin.test.assertEquals

/** 결과 카드가 카드 폭에 들어가는 만큼 틀린 칸이 몰린 각을 보여 주는지. */
class ResultPreviewColumnsTest {
    @Test
    fun showsTheDensestPairAndEarlierGakWithinTheWidth() {
        // 틀린 칸이 4·5각(0부터 3·4)에 몰렸고 세 줄이 들어간다.
        assertEquals(2..4, previewColumns(clusterStart = 3, stripsPerColumn = List(8) { 1 }, slots = 3))
    }

    @Test
    fun fillsLaterGakWhenTheClusterIsAtTheStart() {
        assertEquals(0..2, previewColumns(clusterStart = 0, stripsPerColumn = List(8) { 1 }, slots = 3))
        assertEquals(0..2, previewColumns(clusterStart = null, stripsPerColumn = List(8) { 1 }, slots = 3))
    }

    @Test
    fun countsEveryStripOfAGakAndAlwaysShowsOne() {
        // 각마다 두 줄이면 세 줄 폭에는 한 각만 들어간다.
        assertEquals(4..4, previewColumns(clusterStart = 3, stripsPerColumn = List(8) { 2 }, slots = 3))
        assertEquals(4..4, previewColumns(clusterStart = 3, stripsPerColumn = List(8) { 2 }, slots = 1))
        assertEquals(0..1, previewColumns(clusterStart = 5, stripsPerColumn = List(2) { 1 }, slots = 4))
    }
}
