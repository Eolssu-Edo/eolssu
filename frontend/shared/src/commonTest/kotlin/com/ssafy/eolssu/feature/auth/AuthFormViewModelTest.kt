package com.ssafy.eolssu.feature.auth

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.SampleAuthRepository
import com.ssafy.eolssu.data.user.SampleUserRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 로그인(AUTH-04)·비밀번호 설정(AUTH-08·11)·프로필 설정(AUTH-07)·계정 연결(AUTH-09) 입력 흐름. */
class AuthFormViewModelTest {
    private val repository = SampleAuthRepository(now = { 0L })

    @Test
    fun loginFailureShowsErrorUntilAFieldChanges() {
        val viewModel = LoginViewModel(repository)

        assertFalse(viewModel.logIn())
        assertEquals(FieldError.LoginFailed, viewModel.uiState.value.error)

        viewModel.changeEmail("user@example.com")
        assertNull(viewModel.uiState.value.error)

        viewModel.changePassword("password")
        assertTrue(viewModel.logIn())
        assertNull(viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun lockedLoginShowsNoticeInsteadOfFieldError() {
        val viewModel = LoginViewModel(repository)
        viewModel.changeEmail(SampleAuthRepository.LockedEmail)
        viewModel.changePassword("password")

        assertFalse(viewModel.logIn())
        assertNull(viewModel.uiState.value.error)
        assertEquals(LoginNotice.Locked, viewModel.uiState.value.notice)

        viewModel.dismissNotice()
        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun withdrawalCancelledWaitsForNoticeBeforeHome() {
        val viewModel = LoginViewModel(repository)
        viewModel.changeEmail(SampleAuthRepository.WithdrawnEmail)
        viewModel.changePassword("password")

        assertFalse(viewModel.logIn())
        assertEquals(LoginNotice.WithdrawalCancelled, viewModel.uiState.value.notice)
    }

    @Test
    fun passwordSetupShowsRuleThenMismatch() {
        val viewModel = PasswordSetupViewModel()
        viewModel.changePassword("secret")

        assertFalse(viewModel.submit())
        assertEquals(FieldError.PasswordRule, viewModel.uiState.value.passwordError)

        viewModel.changePassword("eolssu1!")
        viewModel.changePasswordConfirm("eolssu1?")
        assertFalse(viewModel.submit())
        viewModel.uiState.value.let {
            assertNull(it.passwordError)
            assertEquals(FieldError.PasswordMismatch, it.passwordConfirmError)
        }

        viewModel.changePasswordConfirm("eolssu1!")
        assertTrue(viewModel.submit())
    }

    @Test
    fun profileStartsWithDansoAndOffersOnlyThreeInstruments() {
        val state = profileSetup().uiState.value

        assertEquals(Instrument.Danso, state.instrument)
        assertEquals(listOf(Instrument.Daegeum, Instrument.Sogeum, Instrument.Danso), state.instruments)
    }

    @Test
    fun profileNeedsNicknameAndSavesInstrumentAndNicknameOnComplete() {
        val instruments = InstrumentRepository(FakeKeyValueStore())
        val users = SampleUserRepository()
        val viewModel = ProfileSetupViewModel(instruments, users)
        viewModel.selectInstrument(Instrument.Daegeum)

        assertFalse(viewModel.complete())
        assertEquals(FieldError.NicknameEmpty, viewModel.uiState.value.nicknameError)
        assertEquals(Instrument.Danso, instruments.instrument.value)

        viewModel.changeNickname(" 얼쑤 ")
        assertNull(viewModel.uiState.value.nicknameError)
        assertTrue(viewModel.complete())
        assertEquals(Instrument.Daegeum, instruments.instrument.value)
        assertEquals("얼쑤", users.profile.value.nickname)
    }

    private fun profileSetup() = ProfileSetupViewModel(InstrumentRepository(FakeKeyValueStore()), SampleUserRepository())

    @Test
    fun accountLinkAsksPasswordOnlyWhenNeeded() {
        val viewModel = AccountLinkViewModel()

        assertTrue(viewModel.link(needsPassword = false))
        assertFalse(viewModel.link(needsPassword = true))
        assertEquals(FieldError.PasswordEmpty, viewModel.uiState.value.passwordError)

        viewModel.changePassword("eolssu1!")
        assertNull(viewModel.uiState.value.passwordError)
        assertTrue(viewModel.link(needsPassword = true))
    }
}
