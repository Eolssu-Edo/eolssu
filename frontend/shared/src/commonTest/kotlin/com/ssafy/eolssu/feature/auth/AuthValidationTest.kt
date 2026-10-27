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

    @Test
    fun loginFailsUnlessEmailIsValidAndPasswordIsPresent() {
        assertNull(AuthValidation.loginError("user@example.com", "password"))
        assertEquals(FieldError.LoginFailed, AuthValidation.loginError("", ""))
        assertEquals(FieldError.LoginFailed, AuthValidation.loginError("user", "password"))
        assertEquals(FieldError.LoginFailed, AuthValidation.loginError("user@example.com", ""))
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
}
