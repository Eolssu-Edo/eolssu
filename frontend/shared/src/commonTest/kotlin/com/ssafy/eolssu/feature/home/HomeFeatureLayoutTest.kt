package com.ssafy.eolssu.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals

/** 홈 카드 배치: 가로 카드 하나와 격자가 모든 기능을 한 번씩 덮고, 요청한 순서를 지킨다. */
class HomeFeatureLayoutTest {
    @Test
    fun highlightAndGridCoverEveryFeatureOnce() {
        val placed = listOf(HomeFeatureHighlight) + HomeFeatureGrid
        assertEquals(HomeFeature.entries.toSet(), placed.toSet())
        assertEquals(placed.size, placed.toSet().size)
    }

    @Test
    fun gridFollowsRequestedOrder() {
        assertEquals(HomeFeature.Learn, HomeFeatureHighlight)
        assertEquals(
            listOf(HomeFeature.SongSelect, HomeFeature.Records, HomeFeature.Reading, HomeFeature.Fingering),
            HomeFeatureGrid,
        )
    }
}
