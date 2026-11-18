package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.core.storage.FakeSecureStore
import com.ssafy.eolssu.core.browser.IncomingLinks
import com.ssafy.eolssu.core.browser.ExternalBrowser
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.data.practice.HttpPracticeSessionRepository
import com.ssafy.eolssu.data.practice.SessionStart
import com.ssafy.eolssu.data.song.InstrumentCatalog
import com.ssafy.eolssu.feature.auth.LoginViewModel
import com.ssafy.eolssu.feature.auth.AccountLinkViewModel
import com.ssafy.eolssu.feature.auth.OAuthViewModel
import com.ssafy.eolssu.feature.auth.OAuthStep
import com.ssafy.eolssu.feature.auth.ProfileSetupViewModel
import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AuthSessionLifecycleRegressionTest {
    @Test
    fun successfulLoginKeepsItsOwnerForTheNextPersonalRequest() = runTest {
        val member = SignedInMember(FakeKeyValueStore())
        val paths = mutableListOf<String>()
        val client = createHttpClient(MockEngine {
            paths += it.url.encodedPath
            val (status, body) = when (it.url.encodedPath) {
                "/api/auth/sign-in" -> HttpStatusCode.OK to """{"isSuccess":true,"result":{"userId":"7","withdrawalCancelled":false}}"""
                "/api/users/me" -> HttpStatusCode.OK to """{"isSuccess":true,"result":{"nickname":"new user","instrumentId":"1","notationMode":"HANGUL","email":"new@example.com"}}"""
                "/api/practice-sessions" -> HttpStatusCode.Created to """{"isSuccess":true,"result":{"sessionId":"41","startedAt":"2026-11-04T01:00:00Z"}}"""
                else -> error("unexpected request ${it.url.encodedPath}")
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val auth = ApiAuthRepository(client, null, member, this)
            val user = ApiUserRepository(client, null, member = member)
            val vm = LoginViewModel(auth, user)
            vm.changeEmail("new@example.com")
            vm.changePassword("secret")
            assertTrue(vm.logIn())
            val started = HttpPracticeSessionRepository(client, member).create("2", 60, "new-take")
            val creates = paths.count { it == "/api/practice-sessions" }
            println("LOGIN_LIFECYCLE_METRIC member=${member.id} epoch=${member.session.value.epoch} personalCreates=$creates")
            assertEquals("7", member.id)
            assertEquals(1, creates)
            assertIs<SessionStart.Created>(started)
        } finally { client.close() }
    }

    @Test
    fun oauthMergeAndBothSignUpsKeepTheNewSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            for (flow in listOf("oauth", "merge", "sign-up", "social-sign-up")) {
                val fixture = Fixture(this)
                val models = ViewModelStore()
                try {
                    val attempts = OAuthAttempts(FakeSecureStore(), fixture.member) { 1_000L }
                    when (flow) {
                        "oauth" -> {
                            val links = IncomingLinks()
                            assertTrue(attempts.begin(SocialProvider.Kakao, OAuthPurpose.LogIn,
                                OAuthAttemptsTest.authorizationUrl(), OAuthAttemptsTest.EXPIRES_AT))
                            links.deliver(OAuthAttemptsTest.callback())
                            val vm = OAuthViewModel(SocialProvider.Kakao, fixture.auth, attempts, links,
                                object : ExternalBrowser { override fun open(url: String) = true }, fixture.users)
                            models.put("oauth", vm)
                            assertEquals(OAuthStep.LoggedIn, vm.step.first {
                                it != OAuthStep.InBrowser && it != OAuthStep.Completing
                            })
                        }
                        "merge" -> {
                            attempts.offerMerge(MergeOffer(SocialProvider.Kakao, "n***@example.com", false,
                                OAuthAttemptsTest.TICKET, OAuthAttemptsTest.EXPIRES_AT))
                            assertTrue(AccountLinkViewModel(attempts, fixture.auth, fixture.users).link())
                        }
                        else -> {
                            val social = flow == "social-sign-up"
                            if (social) attempts.offerSignUp(SignUpTicket(SocialProvider.Kakao, OAuthAttemptsTest.TICKET, OAuthAttemptsTest.EXPIRES_AT))
                            val draft = AccountDraft().apply {
                                email = "new@example.com"; password = "secret1!"; token = "verified"
                                purpose = VerificationPurpose.SignUp; agreements = listOf(TermAgreement("1", "v1", true))
                            }
                            val vm = ProfileSetupViewModel(fixture.instruments, fixture.users, fixture.auth, draft, attempts, social)
                            vm.changeNickname("새회원")
                            assertTrue(vm.complete(), flow)
                        }
                    }
                    assertEquals("7", fixture.member.id, flow)
                    assertEquals(1L, fixture.member.session.value.epoch, flow)
                    assertIs<SessionStart.Created>(fixture.sessions.create("2", 60, flow))
                } finally { models.clear(); fixture.client.close() }
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun logoutThenSameMemberLoginAndTokenRefreshKeepWorking() = runTest {
        val fixture = Fixture(this)
        try {
            val first = fixture.login()
            assertTrue(first.logIn())
            val firstSession = fixture.member.session.value
            fixture.auth.logOut()
            runCurrent()
            assertNull(fixture.member.id)
            assertTrue(fixture.member.session.value.epoch > firstSession.epoch)
            assertTrue(fixture.login().logIn())
            val secondSession = fixture.member.session.value
            assertEquals("7", secondSession.memberId)
            assertTrue(secondSession.epoch > firstSession.epoch)
            fixture.requireRefresh = true
            assertIs<SessionStart.Created>(fixture.sessions.create("2", 60, "after-relogin"))
            assertEquals(secondSession, fixture.member.session.value)
            assertEquals(1, fixture.refreshes)
        } finally { fixture.client.close() }
    }

    @Test
    fun profileClearPreservesSessionButExpiredStartupAndWithdrawalInvalidateIt() = runTest {
        val fixture = Fixture(this)
        try {
            assertTrue(fixture.login().logIn())
            val signedIn = fixture.member.session.value
            fixture.users.clearProfile()
            assertEquals(signedIn, fixture.member.session.value)
            assertEquals("", fixture.users.profile.value.nickname)
            fixture.expired = true
            // App의 startup401 경계와 같은 호출 순서.
            assertEquals(MemberResult.Unauthenticated, fixture.users.refresh().also {
                if (it == MemberResult.Unauthenticated) fixture.users.logOut()
            })
            assertNull(fixture.member.id)
            fixture.expired = false
            assertTrue(fixture.login().logIn())
            val beforeWithdrawal = fixture.member.session.value
            assertEquals(PasswordCheckResult.Done, fixture.users.withdraw("password"))
            assertNull(fixture.member.id)
            assertTrue(fixture.member.session.value.epoch > beforeWithdrawal.epoch)
        } finally { fixture.client.close() }
    }

    private class Fixture(scope: CoroutineScope) {
        /** 보내기 전 입력 검사를 통과하는 로그인 화면. 계정 확인은 목 서버가 한다. */
        fun login() = LoginViewModel(auth, users).apply {
            changeEmail("member@example.com")
            changePassword("password")
        }

        val member = SignedInMember(FakeKeyValueStore())
        var requireRefresh = false
        var expired = false
        var refreshes = 0
        val client = createHttpClient(MockEngine {
            val (status, body) = when (it.url.encodedPath) {
                "/api/auth/sign-in", "/api/auth/oauth/kakao", "/api/auth/oauth/merge" ->
                    HttpStatusCode.OK to """{"isSuccess":true,"result":{"userId":"7","withdrawalCancelled":false}}"""
                "/api/auth/sign-up", "/api/auth/oauth/sign-up" ->
                    HttpStatusCode.Created to """{"isSuccess":true,"result":{"userId":"7"}}"""
                "/api/instruments" -> HttpStatusCode.OK to """{"isSuccess":true,"result":{"items":[{"instrumentId":"1","code":"DANSO","name":"단소"}]}}"""
                "/api/users/me" -> when {
                    it.method.value == "DELETE" -> HttpStatusCode.OK to """{"isSuccess":true,"result":{"withdrawn":true}}"""
                    expired -> HttpStatusCode.Unauthorized to "{}"
                    else -> HttpStatusCode.OK to """{"isSuccess":true,"result":{"nickname":"new user","instrumentId":"1","notationMode":"HANGUL","email":"new@example.com"}}"""
                }
                "/api/auth/refresh" -> {
                    refreshes++
                    requireRefresh = false
                    (if (expired) HttpStatusCode.Unauthorized else HttpStatusCode.OK) to "{}"
                }
                "/api/practice-sessions" -> if (requireRefresh) HttpStatusCode.Unauthorized to "{}" else
                    HttpStatusCode.Created to """{"isSuccess":true,"result":{"sessionId":"41","startedAt":"2026-11-04T01:00:00Z"}}"""
                "/api/auth/sign-out" -> HttpStatusCode.NoContent to ""
                else -> error("unexpected request ${it.url.encodedPath}")
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        val auth = ApiAuthRepository(client, null, member, scope)
        val instruments = InstrumentRepository(FakeKeyValueStore(), InstrumentCatalog(client))
        val users = ApiUserRepository(client, null, instruments, member)
        val sessions = HttpPracticeSessionRepository(client, member)
    }
}
