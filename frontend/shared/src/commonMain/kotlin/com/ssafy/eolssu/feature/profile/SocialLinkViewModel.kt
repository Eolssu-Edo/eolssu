package com.ssafy.eolssu.feature.profile

import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.network.AccountWork
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import com.ssafy.eolssu.core.network.inAccount
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.core.browser.ExternalBrowser
import com.ssafy.eolssu.core.browser.IncomingLinks
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.data.user.LinkResult
import com.ssafy.eolssu.data.user.OAuthAttempts
import com.ssafy.eolssu.data.user.OAuthCallback
import com.ssafy.eolssu.data.user.OAuthPurpose
import com.ssafy.eolssu.data.user.OAuthRestartReason
import com.ssafy.eolssu.data.user.OAuthReturn
import com.ssafy.eolssu.data.user.OAuthStartResult
import com.ssafy.eolssu.data.user.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * MY-05 간편 로그인 연결. 로그인한 회원의 start → 외부 브라우저의 제공자 화면 → 복귀 주소(App Links) → 연결 완료 순서로 진행한다.
 * 로그인(AUTH-05)과 같은 [OAuthAttempts]를 쓰지만 목적이 LINK라 로그인 시도의 복귀는 받아들이지 않는다.
 * 화면에 들어오면 바로 시작하고, 복귀 주소가 이미 와 있으면(앱이 꺼진 사이 돌아온 경우) 새로 시작하지 않고 그 주소를 처리한다.
 */
class SocialLinkViewModel(
    private val provider: SocialProvider,
    private val userRepository: UserRepository,
    private val attempts: OAuthAttempts,
    private val links: IncomingLinks?,
    private val browser: ExternalBrowser?,
    member: SignedInMember? = null,
) : ViewModel() {
    private val _step = MutableStateFlow<SocialLinkStep>(SocialLinkStep.Starting)
    val step: StateFlow<SocialLinkStep> = _step.asStateFlow()

    /** 지금 시도의 제공자 화면 주소. '다시 열기'에 쓰고 화면·로그에 내보내지 않는다. */
    private var authorizationUrl: String? = null

    private var startingJob: Job? = null

    private val accountScope = member.screenScope(viewModelScope) { clearAccount() }
    private fun clearAccount() {
        authorizationUrl = null
        attempts.clear()
        _step.value = SocialLinkStep.Stopped(SocialLinkProblem.SessionExpired)
    }

    init {
        when {
            browser == null || links == null -> _step.value = SocialLinkStep.Stopped(SocialLinkProblem.Unsupported)
            links.pending.value != null && attempts.pending()?.provider == provider -> _step.value = SocialLinkStep.InBrowser
            else -> start()
        }
        links?.let { accountScope.launch { it.pending.filterNotNull().collect(::onReturn) } }
    }

    /** 처음부터 다시 한다. start 요청 중·완료 요청 중에는 무시한다. */
    fun start() {
        if (!accountScope.isActive || accountScope.coroutineContext[AccountWork]?.isCurrent == false) return
        val browser = browser ?: return
        if (_step.value == SocialLinkStep.Completing || startingJob?.isActive == true) return
        attempts.clear()
        authorizationUrl = null
        _step.value = SocialLinkStep.Starting
        startingJob = accountScope.launch {
            _step.value = when (val result = userRepository.startLink(provider)) {
                is OAuthStartResult.Started -> when {
                    !attempts.begin(provider, OAuthPurpose.Link, result.authorizationUrl, result.expiresAtMillis) ->
                        SocialLinkStep.Stopped(SocialLinkProblem.Unavailable)
                    !browser.open(result.authorizationUrl) -> SocialLinkStep.Stopped(SocialLinkProblem.NoBrowser).also { attempts.clear() }
                    else -> SocialLinkStep.InBrowser.also { authorizationUrl = result.authorizationUrl }
                }
                OAuthStartResult.Unauthenticated -> SocialLinkStep.Stopped(SocialLinkProblem.SessionExpired)
                OAuthStartResult.Unavailable -> SocialLinkStep.Stopped(SocialLinkProblem.Unavailable)
            }
        }
    }

    /** 브라우저를 닫았을 때 같은 제공자 화면을 다시 연다. 시도가 끝났거나 만료됐으면 처음부터 한다. */
    fun reopen() {
        if (!accountScope.isActive || accountScope.coroutineContext[AccountWork]?.isCurrent == false) return
        val url = authorizationUrl
        if (_step.value != SocialLinkStep.InBrowser || url == null || attempts.pending() == null) return start()
        if (browser?.open(url) != true) _step.value = SocialLinkStep.Stopped(SocialLinkProblem.NoBrowser)
    }

    private fun onReturn(uri: String) {
        if (!accountScope.isActive || accountScope.coroutineContext[AccountWork]?.isCurrent == false) return
        if (links?.take(uri) != true) return
        when (val result = attempts.accept(uri, OAuthPurpose.Link)) {
            OAuthReturn.Ignored -> Unit
            is OAuthReturn.Restart -> {
                authorizationUrl = null
                _step.value = SocialLinkStep.Stopped(
                    when (result.reason) {
                        OAuthRestartReason.Cancelled -> SocialLinkProblem.Cancelled
                        OAuthRestartReason.Expired -> SocialLinkProblem.Expired
                        OAuthRestartReason.Invalid -> SocialLinkProblem.Invalid
                    },
                )
            }
            is OAuthReturn.Accepted -> complete(result.callback)
        }
    }

    private fun complete(callback: OAuthCallback) {
        if (!accountScope.isActive || accountScope.coroutineContext[AccountWork]?.isCurrent == false) return
        authorizationUrl = null
        _step.value = SocialLinkStep.Completing
        accountScope.launch {
            _step.value = when (userRepository.link(callback)) {
                LinkResult.Linked -> SocialLinkStep.Linked
                LinkResult.InUse -> SocialLinkStep.InUse
                LinkResult.Restart -> SocialLinkStep.Stopped(SocialLinkProblem.Invalid)
                LinkResult.Unauthenticated -> SocialLinkStep.Stopped(SocialLinkProblem.SessionExpired)
                LinkResult.Unavailable -> SocialLinkStep.Stopped(SocialLinkProblem.Unavailable)
            }
        }
    }

    /** 화면을 나가면 진행 중 시도를 버린다. */
    override fun onCleared() {
        if (_step.value == SocialLinkStep.Starting || _step.value == SocialLinkStep.InBrowser) attempts.clear()
    }
}
