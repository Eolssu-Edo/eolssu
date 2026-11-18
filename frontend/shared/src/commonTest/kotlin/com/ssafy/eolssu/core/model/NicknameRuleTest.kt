package com.ssafy.eolssu.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** 닉네임 입력칸은 서버와 같은 10자(String.length)까지만 남긴다. */
class NicknameRuleTest {
    @Test
    fun keepsInputUpToTenCharacters() {
        assertEquals("얼쑤", NicknameRule.limit("얼", "얼쑤"))
        assertEquals("가".repeat(10), NicknameRule.limit("가".repeat(9), "가".repeat(10)))
        assertEquals("abcde12345", NicknameRule.limit("abcde1234", "abcde12345"))
        assertEquals("", NicknameRule.limit("얼쑤", ""))
    }

    @Test
    fun typingIntoAFullFieldKeepsThePreviousValue() {
        val full = "가나다라마바사아자차"
        assertEquals(full, NicknameRule.limit(full, full + "카"))
        // 가운데 끼워 넣어도 끝 글자가 밀려 지워지지 않는다.
        assertEquals(full, NicknameRule.limit(full, "가나다X라마바사아자차"))
    }

    @Test
    fun pastedTextIsCutToTenCharacters() {
        assertEquals("가".repeat(10), NicknameRule.limit("", "가".repeat(15)))
        assertEquals("얼쑤abcdefg1", NicknameRule.limit("얼쑤", "얼쑤abcdefg123"))
    }

    @Test
    fun pastedTextDoesNotSplitATwoUnitCharacter() {
        val emoji = "🎵" // 음표 이모지는 String.length 2칸이다.
        val limited = NicknameRule.limit("", "가".repeat(9) + emoji + "나")
        assertEquals("가".repeat(9), limited)
        assertEquals(false, NicknameRule.isTooLong(limited))
    }
}
