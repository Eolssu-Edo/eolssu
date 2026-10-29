package com.ssafy.eolssu.feature.auth

import com.ssafy.eolssu.data.user.AuthRepository
import com.ssafy.eolssu.data.user.ConfirmCodeResult
import com.ssafy.eolssu.data.user.LoginResult
import com.ssafy.eolssu.data.user.SampleAuthRepository
import com.ssafy.eolssu.data.user.SendCodeResult
import com.ssafy.eolssu.data.user.VerificationCodeValidMillis
import com.ssafy.eolssu.data.user.VerificationPurpose
import com.ssafy.eolssu.data.user.VerificationResendMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 이메일 인증(AUTH-06·10): 보내기·재발송 간격·만료·틀린 횟수. */
class EmailVerificationViewModelTest {
    private var now = 1_000L

    private fun viewModel(purpose: VerificationPurpose, repository: AuthRepository = SampleAuthRepository { now }) =
        EmailVerificationViewModel(purpose, repository) { now }

    @Test
    fun signupBlocksRegisteredEmailButResetHidesIt() {
        val signUp = viewModel(VerificationPurpose.SignUp)
        signUp.changeEmail(SampleAuthRepository.RegisteredEmail)
        assertFalse(signUp.sendCode())
        assertEquals(FieldError.EmailAlreadyExists, signUp.uiState.value.emailError)
        assertFalse(signUp.uiState.value.codeSent)

        val reset = viewModel(VerificationPurpose.PasswordReset)
        reset.changeEmail(SampleAuthRepository.RegisteredEmail)
        assertTrue(reset.sendCode())
        assertTrue(reset.uiState.value.codeSent)
    }

    @Test
    fun sentCodeCountsDownAndConfirmsSixDigits() {
        val viewModel = viewModel(VerificationPurpose.SignUp)
        viewModel.changeEmail(" new@example.com ")
        assertTrue(viewModel.sendCode())
        val state = viewModel.uiState.value
        assertEquals("new@example.com", state.email)
        assertEquals(180, state.secondsLeft(now))
        assertEquals(60, state.resendSecondsLeft(now))

        viewModel.changeCode("01a23")
        assertEquals("0123", viewModel.uiState.value.code)
        assertFalse(viewModel.confirmCode())
        assertEquals(CodeError.Invalid, viewModel.uiState.value.codeError)

        viewModel.changeCode("012345")
        assertNull(viewModel.uiState.value.codeError)
        assertTrue(viewModel.confirmCode())
        assertTrue(viewModel.uiState.value.verificationToken != null)
    }

    @Test
    fun resendWaitsSixtySecondsAndExpiryNeedsNewCode() {
        val viewModel = viewModel(VerificationPurpose.SignUp)
        viewModel.changeEmail("new@example.com")
        viewModel.sendCode()

        now += VerificationResendMillis - 1
        assertFalse(viewModel.resendCode())
        now += 1
        assertTrue(viewModel.resendCode())

        now += VerificationCodeValidMillis
        assertEquals(CodeError.Expired, viewModel.uiState.value.codeErrorAt(now))
        assertTrue(viewModel.uiState.value.needsNewCode(now))
        viewModel.changeCode("123456")
        assertFalse(viewModel.confirmCode())
        assertEquals(CodeError.Expired, viewModel.uiState.value.codeError)

        assertTrue(viewModel.resendCode())
        assertNull(viewModel.uiState.value.codeError)
        assertFalse(viewModel.uiState.value.needsNewCode(now))
    }

    @Test
    fun mismatchShowsRemainingAttemptsAndTooManyKeepsBlocking() {
        var answer: ConfirmCodeResult = ConfirmCodeResult.Mismatch(remainingAttempts = 4)
        val repository = object : AuthRepository {
            override suspend fun logIn(email: String, password: String) = LoginResult.WrongCredentials

            override fun logOut() = Unit
            override fun sendCode(email: String, purpose: VerificationPurpose) = SendCodeResult.Sent("v1", now + 180_000, now + 60_000)
            override fun confirmCode(verificationId: String, code: String) = answer
        }
        val viewModel = viewModel(VerificationPurpose.SignUp, repository)
        viewModel.changeEmail("new@example.com")
        viewModel.sendCode()
        viewModel.changeCode("000000")

        assertFalse(viewModel.confirmCode())
        assertEquals(CodeError.Mismatch(4), viewModel.uiState.value.codeError)

        answer = ConfirmCodeResult.TooManyAttempts
        viewModel.changeCode("000001")
        assertFalse(viewModel.confirmCode())
        assertEquals(CodeError.TooManyAttempts, viewModel.uiState.value.codeError)
        viewModel.changeCode("000002")
        assertEquals(CodeError.TooManyAttempts, viewModel.uiState.value.codeError)
        assertTrue(viewModel.uiState.value.needsNewCode(now))
    }

    @Test
    fun editingEmailAfterSendingDropsTheCode() {
        val viewModel = viewModel(VerificationPurpose.SignUp)
        viewModel.changeEmail("new@example.com")
        viewModel.sendCode()
        viewModel.changeCode("123456")

        viewModel.changeEmail("other@example.com")
        viewModel.uiState.value.let {
            assertFalse(it.codeSent)
            assertEquals("", it.code)
            assertEquals("other@example.com", it.email)
        }
    }

    @Test
    fun minutesSecondsFormat() {
        assertEquals("3:00", formatMinutesSeconds(180))
        assertEquals("0:09", formatMinutesSeconds(9))
    }
}
