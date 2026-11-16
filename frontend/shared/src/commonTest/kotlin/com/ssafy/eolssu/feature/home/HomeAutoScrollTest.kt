package com.ssafy.eolssu.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals

/** 홈 첫 화면 자동 펼침: 첫 화면에서만 기다리고, 그 전에 입력이 있으면 취소하며, 한 진입에 한 번만 움직인다. */
class HomeAutoScrollTest {
    @Test
    fun waitsOnlyAtTopWithMotionAllowed() {
        assertEquals(HomeAutoScroll.Waiting, HomeAutoScroll.start(atTop = true, reduceMotion = false))
        assertEquals(HomeAutoScroll.Skipped, HomeAutoScroll.start(atTop = false, reduceMotion = false))
        assertEquals(HomeAutoScroll.Skipped, HomeAutoScroll.start(atTop = true, reduceMotion = true))
    }

    @Test
    fun firesWhenDelayElapsesWithoutInput() {
        assertEquals(HomeAutoScroll.Fired, HomeAutoScroll.Waiting.onScrolled(atTop = true).onElapsed())
    }

    @Test
    fun touchOrScrollBeforeDelayCancels() {
        assertEquals(HomeAutoScroll.Cancelled, HomeAutoScroll.Waiting.onUserInput().onElapsed())
        assertEquals(HomeAutoScroll.Cancelled, HomeAutoScroll.Waiting.onScrolled(atTop = false).onElapsed())
    }

    @Test
    fun movesAtMostOncePerEntry() {
        val fired = HomeAutoScroll.Waiting.onElapsed()
        // 자동 펼침이 만든 스크롤·그 뒤의 입력은 상태를 바꾸지 않고, 다시 대기하지도 않는다.
        assertEquals(HomeAutoScroll.Fired, fired.onScrolled(atTop = false).onUserInput().onElapsed())
        assertEquals(HomeAutoScroll.Skipped, HomeAutoScroll.Skipped.onElapsed())
    }
}
