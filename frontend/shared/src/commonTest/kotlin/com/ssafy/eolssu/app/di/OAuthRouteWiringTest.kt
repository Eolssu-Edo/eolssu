package com.ssafy.eolssu.app.di

import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.core.storage.KeyValueStore
import com.ssafy.eolssu.data.user.AuthRepository
import com.ssafy.eolssu.data.user.IosOAuthRelay
import com.ssafy.eolssu.data.user.OAuthAttempts
import com.ssafy.eolssu.data.user.OAuthAttemptsTest
import com.ssafy.eolssu.data.user.OAuthPurpose
import com.ssafy.eolssu.data.user.OAuthReturn
import com.ssafy.eolssu.data.user.OAuthRoute
import com.ssafy.eolssu.data.user.UserRepository
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 플랫폼 모듈이 등록한 복귀 경로(`OAuthRoute`)를 앱 모듈의 start·시도 상태가 모두 쓰는지. 없으면 Android 주소다. */
class OAuthRouteWiringTest {
    private val sent = mutableListOf<String>()

    private fun platform(route: OAuthRoute?): Module = module {
        single<KeyValueStore> { FakeKeyValueStore() }
        single { ServerAddress.Production }
        single<HttpClientEngine> {
            MockEngine { request ->
                sent += (request.body as TextContent).text
                respond(
                    """{"isSuccess":true,"result":{"authorizationUrl":"${OAuthAttemptsTest.authorizationUrl()}","expiresAt":"2026-10-30T01:10:00Z"}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        if (route != null) single<OAuthRoute> { route }
    }

    // appModule의 single 인스턴스는 모듈에 남으므로 테스트마다 앱을 닫아 다른 테스트로 새지 않게 한다.
    private suspend fun redirectsSentBy(route: OAuthRoute?): List<String> {
        val app = koinApplication { modules(platform(route), appModule) }
        try {
            return redirectsSentBy(route, app.koin)
        } finally {
            app.close()
        }
    }

    private suspend fun redirectsSentBy(route: OAuthRoute?, koin: Koin): List<String> {
        koin.get<AuthRepository>().startSocialLogIn(SocialProvider.Kakao)
        koin.get<UserRepository>().startLink(SocialProvider.Kakao)
        koin.get<UserRepository>().startWithdrawalReauth(SocialProvider.Kakao)
        val attempts = koin.get<OAuthAttempts>()
        assertTrue(attempts.begin(SocialProvider.Kakao, OAuthPurpose.LogIn, OAuthAttemptsTest.authorizationUrl(), Long.MAX_VALUE))
        val returned = if (route == null) OAuthAttemptsTest.callback()
        else "com.ssafy.eolssu://oauth/kakao/callback?code=code-1&state=${OAuthAttemptsTest.STATE}"
        sent += assertIs<OAuthReturn.Accepted>(attempts.accept(returned, OAuthPurpose.LogIn)).callback.redirectUri
        return sent.toList()
    }

    @Test
    fun registeredIosRouteReachesEveryStartAndAttempt() = runTest {
        val ios = """{"redirectUri":"https://k15e102.p.ssafy.io/api/auth/ios/oauth/kakao/callback"}"""
        assertEquals(listOf(ios, ios, ios, "https://k15e102.p.ssafy.io/api/auth/ios/oauth/kakao/callback"), redirectsSentBy(IosOAuthRelay))
    }

    @Test
    fun withoutARegisteredRouteTheAndroidAddressStays() = runTest {
        val android = """{"redirectUri":"https://k15e102.p.ssafy.io/auth/android/oauth/kakao/callback"}"""
        assertEquals(
            listOf(android, android, android, "https://k15e102.p.ssafy.io/auth/android/oauth/kakao/callback"),
            redirectsSentBy(null),
        )
    }
}
