package com.ssafy.eolssu.backend.feature.auth.api

import com.ssafy.eolssu.backend.feature.auth.domain.PasswordPolicy
import jakarta.validation.Validation
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SignInRequestTests {
    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `accepts every password the sign up policy accepts even with supplementary characters`() {
        val password = "a1!" + "🎵".repeat(PasswordPolicy.MAX_LENGTH - 3)
        assertTrue(PasswordPolicy.isValid(password))
        assertTrue(validator.validate(SignInRequest("learner@example.com", password)).isEmpty())
    }

    /** 예전 규칙(8~128자)으로 가입한 계정도 로그인할 수 있어야 한다(S15P31E102-1014). */
    @Test
    fun `still accepts passwords created under the previous 128 character rule`() {
        val legacy = "a1!" + "🎵".repeat(125)
        assertFalse(PasswordPolicy.isValid(legacy))
        assertTrue(validator.validate(SignInRequest("learner@example.com", legacy)).isEmpty())
    }
}
