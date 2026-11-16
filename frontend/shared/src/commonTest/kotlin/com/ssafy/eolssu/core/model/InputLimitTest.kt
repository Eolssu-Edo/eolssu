package com.ssafy.eolssu.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** 입력칸은 서버와 같은 글자 수(코드 포인트)까지만 남긴다(S15P31E102-996). */
class InputLimitTest {
    @Test
    fun countsCodePoints() {
        assertEquals(0, InputLimit.length(""))
        assertEquals(3, InputLimit.length("가😀\n"))
        assertEquals(1, InputLimit.length("\uDC00"))
    }

    @Test
    fun keepsInputWithinTheLimit() {
        assertEquals("abc", InputLimit.limit("ab", "abc", 3))
        assertEquals("", InputLimit.limit("abc", "", 3))
    }

    @Test
    fun typingIntoAFullFieldKeepsThePreviousValue() {
        assertEquals("abc", InputLimit.limit("abc", "abcd", 3))
        // 가운데 끼워 넣어도 끝 글자가 밀려 지워지지 않는다.
        assertEquals("abc", InputLimit.limit("abc", "aXbc", 3))
    }

    @Test
    fun pastedTextIsCutWithoutSplittingSurrogatePairs() {
        assertEquals("가나다", InputLimit.limit("", "가나다라마", 3))
        assertEquals("a😀b", InputLimit.limit("a", "a😀bc", 3))
        assertEquals("😀😀", InputLimit.limit("", "😀😀😀", 2))
    }

    @Test
    fun serverLengthsAreKept() {
        assertEquals(100, InputLimit.EMAIL)
        assertEquals(64, InputLimit.PASSWORD)
        assertEquals(50, InputLimit.SHEET_TITLE)
        assertEquals(500, InputLimit.MEMO)
        val longEmail = "a".repeat(400) + "@eolssu.kr"
        assertEquals(100, InputLimit.limit("", longEmail, InputLimit.EMAIL).length)
    }
}
