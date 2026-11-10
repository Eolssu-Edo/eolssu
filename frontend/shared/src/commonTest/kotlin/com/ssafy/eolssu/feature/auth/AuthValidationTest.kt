package com.ssafy.eolssu.feature.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthValidationTest {
    @Test
    fun emailNeedsLocalPartAtAndDottedDomain() {
        assertTrue(AuthValidation.isEmail("user@example.com"))
        assertTrue(AuthValidation.isEmail("  user@example.co.kr "))
        assertFalse(AuthValidation.isEmail(""))
        assertFalse(AuthValidation.isEmail("user"))
        assertFalse(AuthValidation.isEmail("user@example"))
        assertFalse(AuthValidation.isEmail("@example.com"))
        assertFalse(AuthValidation.isEmail("us er@example.com"))
    }

    /** 서버 인증번호 발송(S15P31E102-686)과 같은 형식 규칙. 앞뒤 공백만 잘라 검사한다. */
    @Test
    fun emailFollowsTheServerVerificationRule() {
        for (email in listOf("user@naver.com", "first.last+tag@sub.example.co.kr", "UPPER.Case@Example.COM", "a_b%c-d@my-domain.io")) {
            assertTrue(AuthValidation.isEmail(email), email)
            assertNull(AuthValidation.emailError(email), email)
        }
        for (email in listOf(
            "a@b", "a@b.", "@b.com", "a@.com", "a..b@c.com", ".a@b.com", "a.@b.com", "a@b..com", "a b@c.com",
            "a@-b.com", "a@b-.com", "a@b.c", "a@b.123", "a,b@c.com", "a@b@c.com", "a@b_c.com", "mémber@example.com",
            "a".repeat(65) + "@example.com",
        )) {
            assertFalse(AuthValidation.isEmail(email), email)
            assertEquals(FieldError.EmailInvalid, AuthValidation.emailError(email), email)
        }
    }

    @Test
    fun codeIsExactlySixDigitsKeepingLeadingZero() {
        assertTrue(AuthValidation.isCode("012345"))
        assertFalse(AuthValidation.isCode("12345"))
        assertFalse(AuthValidation.isCode("1234567"))
        assertFalse(AuthValidation.isCode("12a456"))
    }

    @Test
    fun passwordNeedsLetterDigitAndSymbolWithin8To128() {
        assertNull(AuthValidation.passwordError("eolssu1!"))
        assertEquals(FieldError.PasswordRule, AuthValidation.passwordError("eols1!"))
        assertEquals(FieldError.PasswordRule, AuthValidation.passwordError("eolssu12"))
        assertEquals(FieldError.PasswordRule, AuthValidation.passwordError("eolssu!!"))
        assertEquals(FieldError.PasswordRule, AuthValidation.passwordError("12345678!"))
        assertNull(AuthValidation.passwordError("a1!" + "x".repeat(125)))
        assertEquals(FieldError.PasswordTooLong, AuthValidation.passwordError("a1!" + "x".repeat(126)))
        // 조건을 덜 채운 채 128자를 넘으면 체크리스트로 보이는 조건 오류가 먼저다.
        assertEquals(FieldError.PasswordRule, AuthValidation.passwordError("x".repeat(129)))
    }

    @Test
    fun signupEmailDistinguishesEmptyAndMalformed() {
        assertEquals(FieldError.EmailEmpty, AuthValidation.emailError(" "))
        assertEquals(FieldError.EmailInvalid, AuthValidation.emailError("user@"))
        assertNull(AuthValidation.emailError("user@example.com"))
    }

    @Test
    fun passwordConfirmIsCheckedOnlyWhenPasswordExists() {
        assertEquals(FieldError.PasswordEmpty, AuthValidation.passwordError(""))
        assertNull(AuthValidation.passwordConfirmError("", ""))
        assertNull(AuthValidation.passwordConfirmError("", "abc"))
        assertEquals(FieldError.PasswordMismatch, AuthValidation.passwordConfirmError("abc", ""))
        assertEquals(FieldError.PasswordMismatch, AuthValidation.passwordConfirmError("abc", "abd"))
        assertNull(AuthValidation.passwordConfirmError("abc", "abc"))
    }

    @Test
    fun nicknameMustNotBeBlank() {
        assertEquals(FieldError.NicknameEmpty, AuthValidation.nicknameError("  "))
        assertNull(AuthValidation.nicknameError("얼쑤"))
    }

    @Test
    fun nicknameAllowsTenCharactersAfterTrim() {
        assertNull(AuthValidation.nicknameError("가".repeat(10)))
        assertNull(AuthValidation.nicknameError(" ${"가".repeat(10)} "))
        assertEquals(FieldError.InvalidInput, AuthValidation.nicknameError("가".repeat(11)))
    }
}
