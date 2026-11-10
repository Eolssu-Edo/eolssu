package com.ssafy.eolssu.core.model

import com.ssafy.eolssu.core.model.PasswordCheck.Digit
import com.ssafy.eolssu.core.model.PasswordCheck.Letter
import com.ssafy.eolssu.core.model.PasswordCheck.MinLength
import com.ssafy.eolssu.core.model.PasswordCheck.Special
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordRuleTest {
    @Test
    fun checksFollowEachCharacterAsTyped() {
        assertEquals(emptySet(), PasswordRule.checks(""))
        assertEquals(setOf(Letter), PasswordRule.checks("e"))
        assertEquals(setOf(Letter, Digit), PasswordRule.checks("e1"))
        assertEquals(setOf(Letter, Digit, Special), PasswordRule.checks("e1!"))
        assertEquals(setOf(Letter, Digit, Special, MinLength), PasswordRule.checks("eolssu1!"))
    }

    @Test
    fun deletingCharactersUnchecksImmediately() {
        val full = "eolssu1!"
        assertEquals(setOf(Letter, Digit), PasswordRule.checks(full.dropLast(1).drop(1)))
        assertEquals(setOf(Letter, Digit, Special), PasswordRule.checks(full.drop(1)))
        assertEquals(setOf(Letter, Special, MinLength), PasswordRule.checks("eolssu!!"))
        assertEquals(setOf(Letter, Digit, MinLength), PasswordRule.checks("eolssu12"))
    }

    /** 서버와 같은 판정: 한글은 영문이 아니고 공백은 특수문자가 아니다. */
    @Test
    fun onlyAsciiLettersCountAndWhitespaceIsNotSpecial() {
        assertEquals(setOf(Digit, Special), PasswordRule.checks("가나1!"))
        assertEquals(setOf(Letter, Digit, MinLength), PasswordRule.checks("abc 1234"))
    }

    /** 체크리스트 네 조건을 모두 채우는 것과 제출 가능(128자 이하)이 같은 기준이다. */
    @Test
    fun validMeansAllChecksWithin128() {
        assertTrue(PasswordRule.meetsAllChecks("eolssu1!"))
        assertTrue(PasswordRule.isValid("eolssu1!"))
        assertFalse(PasswordRule.isValid("eols1!"))
        assertTrue(PasswordRule.isValid("a1!" + "x".repeat(125)))
        val tooLong = "a1!" + "x".repeat(126)
        assertTrue(PasswordRule.meetsAllChecks(tooLong))
        assertTrue(PasswordRule.isTooLong(tooLong))
        assertFalse(PasswordRule.isValid(tooLong))
    }
}
