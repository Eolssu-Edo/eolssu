package com.ssafy.eolssu.feature.song.component

import kotlin.test.Test
import kotlin.test.assertEquals

class CoverTitleTest {

    @Test
    fun dropsEverythingFromFirstParenthesisAndTrims() {
        assertEquals("영산회상", coverTitle("영산회상 (상령산)"))
        assertEquals("수제천", coverTitle("수제천(壽齊天) (정악)"))
    }

    @Test
    fun dropsFromFullWidthParenthesisToo() {
        assertEquals("청성곡", coverTitle("청성곡（요천순일지곡）"))
    }

    @Test
    fun keepsTitleWithoutParenthesis() {
        assertEquals("아리랑", coverTitle("아리랑"))
    }

    @Test
    fun fallsBackToFullTitleWhenNothingIsLeft() {
        assertEquals("(연습곡)", coverTitle("(연습곡)"))
        assertEquals(" （가） ", coverTitle(" （가） "))
    }
}
