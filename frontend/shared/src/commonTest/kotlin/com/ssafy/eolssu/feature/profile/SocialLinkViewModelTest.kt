package com.ssafy.eolssu.feature.profile

import com.ssafy.eolssu.core.browser.IncomingLinks
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.core.storage.FakeSecureStore
import com.ssafy.eolssu.data.user.LinkResult
import com.ssafy.eolssu.data.user.OAuthAttempts
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.EXPIRES_AT
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.authorizationUrl
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.callback
import com.ssafy.eolssu.data.user.OAuthCallback
import com.ssafy.eolssu.data.user.OAuthPurpose
import com.ssafy.eolssu.data.user.OAuthStartResult
import com.ssafy.eolssu.data.user.SampleUserRepository
import com.ssafy.eolssu.data.user.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** MY-05 간편 로그인 연결 흐름. */
@OptIn(ExperimentalCoroutinesApi::class)
class SocialLinkViewModelTest {
    private val links = IncomingLinks()
    private val opened = mutableListOf<String>()
    private val attempts = OAuthAttempts(FakeSecureStore(), SignedInMember(FakeKeyValueStore())) { 1_000L }
    private var startResult: OAuthStartResult = OAuthStartResult.Started(authorizationUrl(), EXPIRES_AT)
    private var linkResult: LinkResult = LinkResult.Linked
    private val linked = mutableListOf<OAuthCallback>()
    private val users: UserRepository = object : UserRepository by SampleUserRepository() {
        override suspend fun startLink(provider: SocialProvider) = startResult
        override suspend fun link(callback: OAuthCallback) = linkResult.also { linked += callback }
    }

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SocialLinkViewModel(SocialProvider.Kakao, users, attempts, links) { opened += it; true }

    @Test
    fun linksOnceThroughTheBrowserReturn() = runTest {
        val viewModel = viewModel()
        assertEquals(SocialLinkStep.InBrowser, viewModel.step.value)
        assertEquals(listOf(authorizationUrl()), opened)

        links.deliver(callback())
        links.deliver(callback())
        assertEquals(SocialLinkStep.Linked, viewModel.step.value)
        assertEquals(1, linked.size)
    }

    @Test
    fun providerAccountOwnedByAnotherMemberShowsTheNotice() = runTest {
        linkResult = LinkResult.InUse
        val viewModel = viewModel()
        links.deliver(callback())
        assertEquals(SocialLinkStep.InUse, viewModel.step.value)
    }

    @Test
    fun loginReturnIsNotUsedForLinking() = runTest {
        val viewModel = viewModel()
        // 로그인 화면이 시작한 시도의 복귀가 마이페이지 연결로 섞이지 않는다.
        attempts.begin(SocialProvider.Kakao, OAuthPurpose.LogIn, authorizationUrl(), EXPIRES_AT)
        links.deliver(callback())
        assertEquals(SocialLinkStep.Stopped(SocialLinkProblem.Invalid), viewModel.step.value)
        assertEquals(0, linked.size)
        assertNull(attempts.pending())
    }

    @Test
    fun expiredSessionAtStartIsNotRetried() = runTest {
        startResult = OAuthStartResult.Unauthenticated
        val step = viewModel().step.value
        assertEquals(SocialLinkStep.Stopped(SocialLinkProblem.SessionExpired), step)
        assertEquals(false, (step as SocialLinkStep.Stopped).problem.retryable)
        assertEquals(emptyList(), opened)
    }
}
