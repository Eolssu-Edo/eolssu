package com.ssafy.eolssu.feature.auth

import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.core.storage.FakeSecureStore
import com.ssafy.eolssu.data.user.AuthRepository
import com.ssafy.eolssu.data.user.LoginPreferences
import com.ssafy.eolssu.data.user.LoginResult
import com.ssafy.eolssu.data.user.SampleAuthRepository
import com.ssafy.eolssu.testing.runImmediately
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** AUTH-04 보내기 전 입력 검사·중복 요청 방지와 아이디 기억하기·자동 로그인. */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginOptionsTest {
    private val sample = SampleAuthRepository(now = { 0L })

    /** 서버에 보낸 로그인 요청 수를 센다. */
    private class CountingAuth(private val delegate: AuthRepository) : AuthRepository by delegate {
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun logIn(email: String, password: String): LoginResult {
            calls++
            gate?.await()
            return delegate.logIn(email, password)
        }
    }

    @BeforeTest
    @AfterTest
    fun resetLaunch() = LoginPreferences.resetLaunchCheck()

    @Test
    fun emptyOrMalformedInputIsBlockedBeforeTheServer() {
        val auth = CountingAuth(sample)
        val viewModel = LoginViewModel(auth)

        assertFalse(runImmediately { viewModel.logIn() })
        assertEquals(FieldError.EmailEmpty, viewModel.uiState.value.emailError)
        assertEquals(FieldError.PasswordEmpty, viewModel.uiState.value.passwordError)

        viewModel.changeEmail("user@example")
        assertNull(viewModel.uiState.value.emailError, "고치기 시작하면 그 칸의 오류를 지운다")
        viewModel.changePassword("password")
        assertFalse(runImmediately { viewModel.logIn() })
        assertEquals(FieldError.EmailInvalid, viewModel.uiState.value.emailError)
        assertNull(viewModel.uiState.value.passwordError)

        viewModel.changeEmail(" user@example.com ")
        viewModel.changePassword("")
        assertFalse(runImmediately { viewModel.logIn() })
        assertNull(viewModel.uiState.value.emailError)
        assertEquals(FieldError.PasswordEmpty, viewModel.uiState.value.passwordError)

        assertEquals(0, auth.calls, "검사에 걸린 입력은 서버에 보내지 않는다")
    }

    @Test
    fun secondTapWhileWaitingIsIgnoredAndShowsLoading() = runTest {
        val auth = CountingAuth(sample).apply { gate = CompletableDeferred() }
        val viewModel = LoginViewModel(auth)
        viewModel.changeEmail("user@example.com")
        viewModel.changePassword("password")

        val first = async { viewModel.logIn() }
        runCurrent()
        assertTrue(viewModel.uiState.value.loggingIn)
        assertFalse(viewModel.logIn())

        auth.gate!!.complete(Unit)
        assertTrue(first.await())
        assertFalse(viewModel.uiState.value.loggingIn)
        assertEquals(1, auth.calls)
    }

    @Test
    fun rememberedEmailFillsTheNextVisitWithoutThePassword() {
        val store = FakeKeyValueStore()
        val secure = FakeSecureStore()
        val first = LoginViewModel(sample, preferences = LoginPreferences(store, secure))
        assertFalse(first.uiState.value.rememberEmail, "기억한 이메일이 없으면 꺼진 채 시작한다")
        first.changeEmail(" user@example.com ")
        first.changePassword("password")
        first.changeRememberEmail(true)
        assertTrue(runImmediately { first.logIn() })

        val next = LoginViewModel(sample, preferences = LoginPreferences(store, secure)).uiState.value
        assertEquals("user@example.com", next.email)
        assertTrue(next.rememberEmail)
        assertEquals("", next.password)
        assertTrue((store.values.values + secure.values.values).none { it.contains("password") }, "비밀번호는 저장하지 않는다")
        assertTrue(store.values.values.none { it.contains("user@example.com") }, "앱은 이메일을 암호화 저장소에만 둔다")
    }

    @Test
    fun uncheckingRememberEmailRemovesTheSavedEmail() {
        val store = FakeKeyValueStore()
        val secure = FakeSecureStore()
        LoginPreferences(store, secure).saveEmailLogin("user@example.com", rememberEmail = true, keepSignedIn = true)
        val viewModel = LoginViewModel(sample, preferences = LoginPreferences(store, secure))
        assertTrue(viewModel.uiState.value.rememberEmail)

        viewModel.changeRememberEmail(false)

        assertNull(LoginPreferences(store, secure).rememberedEmail)
    }

    @Test
    fun webWithoutSecureStoreKeepsTheEmailInPlainStorage() {
        val store = FakeKeyValueStore()
        LoginPreferences(store, null).saveEmailLogin("user@example.com", rememberEmail = true, keepSignedIn = true)
        assertEquals("user@example.com", LoginPreferences(store, null).rememberedEmail)

        LoginPreferences(store, null).forgetEmail()
        assertNull(LoginPreferences(store, null).rememberedEmail)
    }

    @Test
    fun loginWithoutAutoLoginEndsTheSessionOnTheNextLaunchOnly() {
        val store = FakeKeyValueStore()
        val member = SignedInMember(store)
        val preferences = LoginPreferences(store, FakeSecureStore(), member)
        val signIn = object : AuthRepository by sample {
            override suspend fun logIn(email: String, password: String) = sample.logIn(email, password).also { member.set("7") }
        }
        val viewModel = LoginViewModel(signIn, preferences = preferences)
        assertTrue(viewModel.uiState.value.keepSignedIn, "처음에는 지금처럼 로그인을 유지한다")
        viewModel.changeEmail("user@example.com")
        viewModel.changePassword("password")
        viewModel.changeKeepSignedIn(false)
        assertTrue(runImmediately { viewModel.logIn() })

        // 다음 실행: 같은 기기 저장소로 다시 만든다.
        val relaunched = LoginPreferences(store, FakeSecureStore(), SignedInMember(store))
        assertFalse(relaunched.keepSignedIn, "마지막 선택을 기억한다")
        assertTrue(relaunched.endSessionOnLaunch())
        assertFalse(relaunched.endSessionOnLaunch(), "같은 실행에서 화면이 다시 만들어져도 다시 로그아웃하지 않는다")
    }

    @Test
    fun anyOtherLoginOrLogoutClearsTheEndOfSessionMark() {
        val store = FakeKeyValueStore()
        val member = SignedInMember(store)
        val preferences = LoginPreferences(store, FakeSecureStore(), member)
        member.set("7")
        preferences.saveEmailLogin("user@example.com", rememberEmail = false, keepSignedIn = false)

        // 간편 로그인처럼 체크 없이 다른 회원으로 로그인했다.
        member.set("8")
        LoginPreferences.resetLaunchCheck()
        assertFalse(LoginPreferences(store, null, SignedInMember(store)).endSessionOnLaunch())

        preferences.saveEmailLogin("user@example.com", rememberEmail = false, keepSignedIn = false)
        member.set(null)
        LoginPreferences.resetLaunchCheck()
        assertFalse(LoginPreferences(store, null, SignedInMember(store)).endSessionOnLaunch(), "로그아웃한 기기는 지울 세션이 없다")
    }
}
