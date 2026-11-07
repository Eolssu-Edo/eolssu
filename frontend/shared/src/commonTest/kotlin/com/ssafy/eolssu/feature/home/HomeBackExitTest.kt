package com.ssafy.eolssu.feature.home

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 홈의 Android 뒤로 가기: 첫 입력은 안내만, 2초 안의 두 번째 입력은 종료, 2초가 지나면 다시 첫 입력이다. */
class HomeBackExitTest {
    @Test
    fun firstPressOnlyArmsAndSecondWithinWindowExits() {
        val exit = HomeBackExit()
        assertFalse(exit.press(1_000))
        assertTrue(exit.press(1_000 + HomeBackExitWindowMillis - 1))
    }

    @Test
    fun pressAfterWindowStartsOverWithNotice() {
        val exit = HomeBackExit()
        assertFalse(exit.press(0))
        assertFalse(exit.press(HomeBackExitWindowMillis))
        assertTrue(exit.press(HomeBackExitWindowMillis + 500))
    }

    @Test
    fun exitResetsSoNextPressShowsNoticeAgain() {
        val exit = HomeBackExit()
        exit.press(0)
        assertTrue(exit.press(100))
        assertFalse(exit.press(200))
    }
}
