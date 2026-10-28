package com.ssafy.eolssu.feature.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 로그인(AUTH-04)·이메일 가입(AUTH-06)·프로필 설정(AUTH-07) 입력 흐름. */
class AuthFormViewModelTest {
    @Test
    fun loginFailureShowsErrorUntilAFieldChanges() {
        val viewModel = LoginViewModel()

        assertFalse(viewModel.logIn())
        assertEquals(FieldError.LoginFailed, viewModel.uiState.value.error)

        viewModel.changeEmail("user@example.com")
        assertNull(viewModel.uiState.value.error)

        viewModel.changePassword("password")
        assertTrue(viewModel.logIn())
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun signupShowsErrorsPerFieldAndClearsOnlyTheEditedOne() {
        val viewModel = SignupViewModel()
        viewModel.changeEmail("user@")
        viewModel.changePasswordConfirm("x")

        assertFalse(viewModel.next())
        viewModel.uiState.value.let {
            assertEquals(FieldError.EmailInvalid, it.emailError)
            assertEquals(FieldError.PasswordEmpty, it.passwordError)
            assertNull(it.passwordConfirmError)
        }

        viewModel.changeEmail("user@example.com")
        viewModel.uiState.value.let {
            assertNull(it.emailError)
            assertEquals(FieldError.PasswordEmpty, it.passwordError)
        }
    }

    @Test
    fun signupMismatchThenFixMovesOn() {
        val viewModel = SignupViewModel()
        viewModel.changeEmail("user@example.com")
        viewModel.changePassword("secret")
        viewModel.changePasswordConfirm("secrets")

        assertFalse(viewModel.next())
        assertEquals(FieldError.PasswordMismatch, viewModel.uiState.value.passwordConfirmError)

        viewModel.changePasswordConfirm("secret")
        assertTrue(viewModel.next())
    }

    @Test
    fun profileStartsWithDaegeumAndOffersOnlyThreeInstruments() {
        val state = ProfileSetupViewModel().uiState.value

        assertEquals(ProfileInstrument.Daegeum, state.instrument)
        assertEquals(listOf(ProfileInstrument.Daegeum, ProfileInstrument.Sogeum, ProfileInstrument.Danso), state.instruments)
    }

    @Test
    fun profileNeedsNicknameAndKeepsChosenInstrument() {
        val viewModel = ProfileSetupViewModel()
        viewModel.selectInstrument(ProfileInstrument.Danso)

        assertFalse(viewModel.complete())
        assertEquals(FieldError.NicknameEmpty, viewModel.uiState.value.nicknameError)

        viewModel.changeNickname("얼쑤")
        assertNull(viewModel.uiState.value.nicknameError)
        assertTrue(viewModel.complete())
        assertEquals(ProfileInstrument.Danso, viewModel.uiState.value.instrument)
    }
}
