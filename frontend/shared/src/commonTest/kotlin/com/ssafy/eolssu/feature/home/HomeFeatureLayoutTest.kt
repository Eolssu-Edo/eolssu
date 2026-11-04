package com.ssafy.eolssu.feature.home

import com.ssafy.eolssu.core.model.Instrument
import kotlin.test.Test
import kotlin.test.assertEquals

/** 홈 카드 격자가 모든 기능을 한 번씩 덮고 정한 순서를 지키며, 악기 드롭다운은 단소부터 네 악기를 보인다. */
class HomeFeatureLayoutTest {
    @Test
    fun gridCoversEveryFeatureOnce() {
        assertEquals(HomeFeature.entries.toSet(), HomeFeatureGrid.toSet())
        assertEquals(HomeFeature.entries.size, HomeFeatureGrid.size)
    }

    @Test
    fun gridFollowsDecidedOrderInTwoByThree() {
        assertEquals(
            listOf(
                listOf(HomeFeature.Learn, HomeFeature.SongSelect),
                listOf(HomeFeature.Fingering, HomeFeature.Reading),
                listOf(HomeFeature.EditSheets, HomeFeature.Records),
            ),
            HomeFeatureGrid.chunked(2),
        )
    }

    @Test
    fun instrumentMenuStartsWithDansoAndCoversEveryInstrument() {
        assertEquals(Instrument.Danso, HomeInstruments.first())
        assertEquals(Instrument.entries.toSet(), HomeInstruments.toSet())
        assertEquals(Instrument.entries.size, HomeInstruments.size)
    }
}
