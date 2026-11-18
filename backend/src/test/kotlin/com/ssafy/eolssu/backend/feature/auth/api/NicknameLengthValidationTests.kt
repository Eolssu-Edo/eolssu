package com.ssafy.eolssu.backend.feature.auth.api

import jakarta.validation.Validation
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** 가입·소셜 가입 요청의 닉네임은 Kotlin String.length(UTF-16) 기준 10자까지다. 위반은 MethodArgumentNotValid → 400 INVALID_REQUEST. */
class NicknameLengthValidationTests {
    private val validator = Validation.buildDefaultValidatorFactory().validator
    private val agreements = listOf(AgreementRequest("1", "v1", true))
    private val ticket = "a".repeat(43)

    private fun signUp(nickname: String) = SignUpRequest("learner@example.com", "Password!234", nickname, "token", agreements)
    private fun social(nickname: String) = SocialSignUpRequest(ticket, nickname, agreements)

    @Test
    fun `sign up accepts ten characters and rejects eleven`() {
        assertTrue(validator.validate(signUp("가".repeat(10))).isEmpty())
        assertEquals(setOf("nickname"), validator.validate(signUp("가".repeat(11))).map { it.propertyPath.toString() }.toSet())
    }

    @Test
    fun `social sign up accepts ten characters and rejects eleven`() {
        assertTrue(validator.validate(social("가".repeat(10))).isEmpty())
        assertEquals(setOf("nickname"), validator.validate(social("가".repeat(11))).map { it.propertyPath.toString() }.toSet())
    }
}
