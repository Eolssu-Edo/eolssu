package com.ssafy.eolssu.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeScrollTest {
    @Test fun startsWithFullArtAndWhiteInkAboveTheFold() {
        val frame = homeScrollFrame(390f, 844f, 0f, 56.5f)
        assertEquals(844f, frame.groundTop)
        assertEquals(699.5f, frame.buttonTop)
        assertEquals(1.65f, frame.artScale)
        assertTrue(frame.darkBehindButton)
        assertEquals(1f, frame.arrowAlpha)
        assertEquals(0f, frame.headerAlpha)
    }

    @Test fun opensToOriginalBandAndDarkInk() {
        val frame = homeScrollFrame(390f, 844f, 400f, 56.5f)
        assertEquals(376f, frame.groundTop)
        assertEquals(388f, frame.buttonTop)
        assertEquals(1f, frame.artScale)
        assertFalse(frame.darkBehindButton)
        assertEquals(0f, frame.arrowAlpha)
        assertEquals(1f, frame.headerAlpha)
    }

    @Test fun inkChangesWhenTheButtonCrossesTheRisingGround() {
        assertTrue(homeScrollFrame(390f, 844f, 160f, 56.5f).darkBehindButton)
        assertFalse(homeScrollFrame(390f, 844f, 320f, 56.5f).darkBehindButton)
    }

    @Test fun headerPlatesAppearOnlyAfterHalfOfTheScroll() {
        assertEquals(0f, homeScrollFrame(390f, 844f, 200f, 56.5f).headerAlpha)
        assertEquals(.5f, homeScrollFrame(390f, 844f, 280f, 56.5f).headerAlpha, 1e-5f)
        assertEquals(1f, homeScrollFrame(390f, 844f, 380f, 56.5f).headerAlpha)
    }

    @Test fun snapHasDifferentOpenAndCloseThresholds() {
        assertEquals(0f, homeSnapTarget(47f, wasOpen = false))
        assertEquals(400f, homeSnapTarget(49f, wasOpen = false))
        assertEquals(400f, homeSnapTarget(341f, wasOpen = true))
        assertEquals(0f, homeSnapTarget(339f, wasOpen = true))
        assertEquals(520f, homeSnapTarget(520f, wasOpen = true))
    }

    @Test fun clampsOverscrollAndKeepsShortScreensUsable() {
        assertEquals(0f, homeScrollFrame(390f, 600f, -20f, 64f).progress)
        assertEquals(1f, homeScrollFrame(390f, 600f, 900f, 64f).progress)
        val frame = homeScrollFrame(390f, 320f, 0f, 80f)
        assertTrue(frame.buttonTop >= 80f)
        assertTrue(frame.bandHeight < 320f)
    }

    @Test fun expandedStartFramesTheArtLikeTheOpenedBandWithoutOverZoom() {
        val frame = homeScrollFrame(1280f, 800f, 0f, 58f)
        val hero = homeExpandedHero(1280f, 800f, 582.4f, frame, 58f, 140f, 430f)
        assertEquals(1f, hero.artScale)
        // 그림 높이 1280×1448/1086 중 위 62% 비율을 넘긴 만큼 올려 정자가 보이는 아래쪽을 남긴다.
        assertEquals((800f - 1280f * HomeArtAspect) * .62f, hero.artY, 1e-3f)
        assertEquals(0f, hero.heroOffset)
    }

    @Test fun expandedTallWindowStillCoversTheHeightAtStart() {
        val frame = homeScrollFrame(900f, 1400f, 0f, 58f)
        val hero = homeExpandedHero(900f, 1400f, 400f, frame, 58f, 140f, 430f)
        assertTrue(900f * hero.artScale * HomeArtAspect >= 1400f)
        assertEquals(0f, hero.artY, 1e-3f)
    }

    @Test fun expandedOpenedHeroCentersOnCardsWithinTheWindow() {
        val frame = homeScrollFrame(1280f, 800f, 400f, 58f)
        val hero = homeExpandedHero(1280f, 800f, 582.4f, frame, 58f, 140f, 430f)
        assertEquals(1f, hero.artScale)
        // 무대 묶음 376+12+58=446의 가운데를 카드 140+215=355에 맞춘다.
        assertEquals(355f - 223f, hero.heroOffset, 1e-3f)
        val short = homeScrollFrame(1280f, 600f, 400f, 58f)
        val clamped = homeExpandedHero(1280f, 600f, 582.4f, short, 58f, 140f, 600f)
        assertTrue(short.bandHeight + clamped.heroOffset + 58f + 58f <= 600f + 1e-3f)
    }
}
