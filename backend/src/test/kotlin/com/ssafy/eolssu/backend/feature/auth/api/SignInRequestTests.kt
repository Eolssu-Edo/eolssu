package com.ssafy.eolssu.backend.feature.auth.api

import com.ssafy.eolssu.backend.feature.auth.domain.PasswordPolicy
import jakarta.validation.Validation
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SignInRequestTests {
    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `accepts every password the sign up policy accepts even with supplementary characters`() {
        val password = "a1!" + "🎵".repeat(125)
        assertTrue(PasswordPolicy.isValid(password))
        assertTrue(validator.validate(SignInRequest("learner@example.com", password)).isEmpty())
    }
}
