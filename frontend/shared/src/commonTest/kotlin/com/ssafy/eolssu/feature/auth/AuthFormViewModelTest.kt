package com.ssafy.eolssu.feature.auth

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.SampleAuthRepository
import com.ssafy.eolssu.data.user.SampleUserRepository
import com.ssafy.eolssu.testing.runImmediately
import kotlinx.coroutines.test.runTest
import com.ssafy.eolssu.data.user.AccountDraft
import com.ssafy.eolssu.data.user.MemberResult
import com.ssafy.eolssu.data.user.Registration
import com.ssafy.eolssu.data.user.TermAgreement
import com.ssafy.eolssu.data.user.AuthRepository
import com.ssafy.eolssu.data.user.VerificationPurpose
import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 로그인(AUTH-04)·비밀번호 설정(AUTH-08·11)·프로필 설정(AUTH-07)·계정 연결(AUTH-09) 입력 흐름. */
class AuthFormViewModelTest {
    private val repository = SampleAuthRepository(now = { 0L })

    @Test
    fun completedLoginDoesNotCarryConsentIntoTheNextAccountsSignup() = runTest {
        for (email in listOf("user@example.com", SampleAuthRepository.WithdrawnEmail)) {
            val draft = AccountDraft().apply {
                this.email = "abandoned@example.test"
                token = "verified"
                password = "test-pass1!"
                purpose = VerificationPurpose.SignUp
                agreements = listOf(TermAgreement("1", "v1", true), TermAgreement("2", "v1", true))
            }
            val viewModel = LoginViewModel(repository, draft = draft)
            viewModel.changeEmail(email)
            viewModel.changePassword("password")

            runImmediately { viewModel.logIn() }

            assertTrue(draft.agreements.isEmpty())
            assertEquals("", draft.email)
            assertEquals("", draft.token)
            assertEquals("", draft.password)
            assertNull(draft.purpose)
        }
    }

    @Test
    fun failedLoginKeepsTheCurrentAnonymousConsentChoices() = runTest {
        val agreements = listOf(TermAgreement("1", "v1", true), TermAgreement("2", "v1", false))
        val draft = AccountDraft().apply { this.agreements = agreements }
        val viewModel = LoginViewModel(repository, draft = draft)

        assertFalse(runImmediately { viewModel.logIn() })

        assertEquals(agreements, draft.agreements)
    }

    @Test
    fun loginFailureShowsErrorUntilAFieldChanges() = runTest {
        val viewModel = LoginViewModel(repository)

        assertFalse(runImmediately { viewModel.logIn() })
        assertEquals(FieldError.LoginFailed, viewModel.uiState.value.error)

        viewModel.changeEmail("user@example.com")
        assertNull(viewModel.uiState.value.error)

        viewModel.changePassword("password")
        assertTrue(runImmediately { viewModel.logIn() })
        assertNull(viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun lockedLoginShowsNoticeInsteadOfFieldError() = runTest {
        val viewModel = LoginViewModel(repository)
        viewModel.changeEmail(SampleAuthRepository.LockedEmail)
        viewModel.changePassword("password")

        assertFalse(runImmediately { viewModel.logIn() })
        assertNull(viewModel.uiState.value.error)
        assertEquals(LoginNotice.Locked, viewModel.uiState.value.notice)
        assertNotNull(viewModel.uiState.value.lockedUntil, "예시 저장소도 다시 시도할 시각을 알려 준다")

        viewModel.dismissNotice()
        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun withdrawalCancelledWaitsForNoticeBeforeHome() = runTest {
        val viewModel = LoginViewModel(repository)
        viewModel.changeEmail(SampleAuthRepository.WithdrawnEmail)
        viewModel.changePassword("password")

        assertFalse(runImmediately { viewModel.logIn() })
        assertEquals(LoginNotice.WithdrawalCancelled, viewModel.uiState.value.notice)
    }

    @Test
    fun passwordSetupShowsRuleThenMismatch() = runTest {
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
    fun profileStartsWithDansoAndOffersFourInstruments() = runTest {
        val state = profileSetup().uiState.value

        assertEquals(Instrument.Danso, state.instrument)
        assertEquals(listOf(Instrument.DaegeumJeongak, Instrument.DaegeumSanjo, Instrument.Sogeum, Instrument.Danso), state.instruments)
    }

    @Test
    fun profileRejectsUnavailableInstrumentBeforeRegistering() = runTest {
        val catalogClient = com.ssafy.eolssu.core.network.createHttpClient(io.ktor.client.engine.mock.MockEngine {
            respond("""{"isSuccess":true,"result":{"items":[{"instrumentId":"73","code":"DANSO","name":"단소"}]}}""", headers = io.ktor.http.headersOf(io.ktor.http.HttpHeaders.ContentType, "application/json"))
        }, com.ssafy.eolssu.core.network.ServerAddress.Production)
        val instruments = InstrumentRepository(FakeKeyValueStore(), com.ssafy.eolssu.data.song.InstrumentCatalog(catalogClient))
        val users = SampleUserRepository()
        val viewModel = ProfileSetupViewModel(instruments, users,
            object : AuthRepository by SampleAuthRepository({ 0L }) { override suspend fun signUp(request: Registration) = MemberResult.Done },
            AccountDraft().apply { email = "member@example.test"; token = "verified"; password = "test-pass1!"; purpose = VerificationPurpose.SignUp; agreements = listOf(TermAgreement("1", "v1", true)) })
        viewModel.selectInstrument(Instrument.DaegeumJeongak)

        assertFalse(viewModel.complete())
        assertEquals(FieldError.NicknameEmpty, viewModel.uiState.value.nicknameError)
        assertNull(instruments.instrument.value)

        viewModel.changeNickname(" 얼쑤 ")
        assertNull(viewModel.uiState.value.nicknameError)
        assertFalse(viewModel.complete())
        assertEquals(FieldError.InstrumentUnavailable, viewModel.uiState.value.nicknameError)
        assertNull(instruments.instrument.value)
        viewModel.selectInstrument(Instrument.Danso)
        assertTrue(viewModel.complete())
        catalogClient.close()
    }

    private fun profileSetup() = ProfileSetupViewModel(InstrumentRepository(FakeKeyValueStore()), SampleUserRepository(),
        object : AuthRepository by SampleAuthRepository({ 0L }) { override suspend fun signUp(request: Registration) = MemberResult.Done },
        AccountDraft().apply { email = "member@example.test"; token = "verified"; password = "test-pass1!"; purpose = VerificationPurpose.SignUp; agreements = listOf(TermAgreement("1", "v1", true)) })
}
