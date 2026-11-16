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

    @Test fun picksArtByOrientationAndShortSide() {
        assertEquals(HomeArtKind.PhonePortrait, homeArtKind(390f, 844f))
        assertEquals(HomeArtKind.PhoneLandscape, homeArtKind(844f, 390f))
        assertEquals(HomeArtKind.TabletPortrait, homeArtKind(800f, 1280f))
        assertEquals(HomeArtKind.TabletLandscape, homeArtKind(1280f, 800f))
        // 넓지만 낮은 창은 짧은 변이 휴대폰 크기라 휴대폰 가로 그림을 쓴다.
        assertEquals(HomeArtKind.PhoneLandscape, homeArtKind(1200f, 500f))
    }

    @Test fun artCoversTheBoxAndKeepsItsRatio() {
        // 그림과 같은 비율의 창은 넘침 없이 꼭 맞는다.
        val fit = homeArtPlacement(HomeArtKind.PhonePortrait, 1080f, 2340f, .5f, .5f)
        assertEquals(1080f, fit.width, 1e-2f)
        assertEquals(0f, fit.x, 1e-2f)
        assertEquals(0f, fit.y, 1e-2f)
        // 펼친 그림 띠(390×376): 너비에 맞추고 세로로 넘친 만큼 초점 위치를 남긴다.
        val band = homeArtPlacement(HomeArtKind.PhonePortrait, 390f, 376f, .5f, .8f)
        assertEquals(390f, band.width, 1e-3f)
        assertEquals(845f, band.height, 1e-2f)
        assertEquals((376f - 845f) * .8f, band.y, 1e-2f)
        // 그림보다 넓적한 칸·길쭉한 칸 모두 빈틈이 없다.
        for (kind in HomeArtKind.entries) for ((bw, bh) in listOf(300f to 900f, 900f to 300f)) {
            val p = homeArtPlacement(kind, bw, bh, kind.openFocusX, kind.openFocusY)
            assertTrue(p.width >= bw - 1e-3f && p.height >= bh - 1e-3f)
            assertTrue(p.x <= 0f && p.y <= 0f)
            assertTrue(p.x + p.width >= bw - 1e-3f && p.y + p.height >= bh - 1e-3f)
            assertEquals(kind.aspect, p.height / p.width, 1e-4f)
        }
    }
}
