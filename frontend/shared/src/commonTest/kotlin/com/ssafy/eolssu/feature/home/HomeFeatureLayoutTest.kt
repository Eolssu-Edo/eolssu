package com.ssafy.eolssu.feature.home

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.ui.component.PracticeInstruments
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 홈 카드 격자가 모든 기능을 한 번씩 덮고, 2열로 왼쪽은 학습 · 오른쪽은 연습·관리를 지키며,
 * 곡 고르기 화면의 연습 악기 메뉴는 단소부터 네 악기를 보인다.
 */
class HomeFeatureLayoutTest {
    @Test
    fun gridCoversEveryFeatureOnce() {
        assertEquals(HomeFeature.entries.toSet(), HomeFeatureGrid.toSet())
        assertEquals(HomeFeature.entries.size, HomeFeatureGrid.size)
    }

    @Test
    fun gridPutsLearningLeftAndPracticeRightInTwoByThree() {
        assertEquals(2, HomeFeatureColumns)
        assertEquals(
            listOf(
                listOf(HomeFeature.Learn, HomeFeature.SongSelect),
                listOf(HomeFeature.Fingering, HomeFeature.Records),
                listOf(HomeFeature.Reading, HomeFeature.EditSheets),
            ),
            HomeFeatureGrid.chunked(HomeFeatureColumns),
        )
    }

    @Test
    fun instrumentMenuStartsWithDansoAndCoversEveryInstrument() {
        assertEquals(Instrument.Danso, PracticeInstruments.first())
        assertEquals(Instrument.entries.toSet(), PracticeInstruments.toSet())
        assertEquals(Instrument.entries.size, PracticeInstruments.size)
    }
}
