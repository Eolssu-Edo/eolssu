package com.ssafy.eolssu.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.browser.ExternalBrowser
import com.ssafy.eolssu.core.browser.IncomingLinks
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.data.user.AccountDraft
import com.ssafy.eolssu.data.user.AuthRepository
import com.ssafy.eolssu.data.user.OAuthAttempts
import com.ssafy.eolssu.data.user.OAuthCallback
import com.ssafy.eolssu.data.user.OAuthPurpose
import com.ssafy.eolssu.data.user.OAuthRestartReason
import com.ssafy.eolssu.data.user.OAuthReturn
import com.ssafy.eolssu.data.user.OAuthStartResult
import com.ssafy.eolssu.data.user.SocialLoginResult
import com.ssafy.eolssu.data.user.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * AUTH-05 간편 로그인. 서버 start → 외부 브라우저의 제공자 화면 → 복귀 주소(App Links) → 완료 요청 순서로 진행한다.
 * 시도 상태와 복귀 주소 확인은 [attempts]가, 복귀 주소 전달은 [links]가 맡는다. [browser]가 없는 플랫폼은 진행하지 않는다.
 * 화면에 들어오면 바로 시작하지만, 복귀 주소가 이미 와 있으면(앱이 꺼진 사이 돌아온 경우) 새로 시작하지 않고 그 주소를 처리한다.
 */
class OAuthViewModel(
    private val provider: SocialProvider,
    private val authRepository: AuthRepository,
    private val attempts: OAuthAttempts,
    private val links: IncomingLinks?,
    private val browser: ExternalBrowser?,
    private val userRepository: UserRepository? = null,
    private val draft: AccountDraft = AccountDraft(),
) : ViewModel() {
    private val _step = MutableStateFlow<OAuthStep>(OAuthStep.Starting)
    val step: StateFlow<OAuthStep> = _step.asStateFlow()

    /** 지금 시도의 제공자 화면 주소. '다시 열기'에 쓴다. 코드·state 원문이 들어 있어 화면·로그에 내보내지 않는다. */
    private var authorizationUrl: String? = null

    private var startingJob: Job? = null

    init {
        when {
            browser == null || links == null -> _step.value = OAuthStep.Stopped(OAuthProblem.Unsupported)
            links.pending.value != null && attempts.pending()?.provider == provider -> _step.value = OAuthStep.InBrowser
            else -> start()
        }
        links?.let { viewModelScope.launch { it.pending.filterNotNull().collect(::onReturn) } }
    }

    /** 처음부터 다시 한다. start 요청 중·완료 요청 중에는 무시한다. */
    fun start() {
        val browser = browser ?: return
        if (_step.value == OAuthStep.Completing || startingJob?.isActive == true) return
        attempts.clear()
        authorizationUrl = null
        _step.value = OAuthStep.Starting
        startingJob = viewModelScope.launch {
            _step.value = when (val result = authRepository.startSocialLogIn(provider)) {
                is OAuthStartResult.Started -> when {
                    !attempts.begin(provider, OAuthPurpose.LogIn, result.authorizationUrl, result.expiresAtMillis) ->
                        OAuthStep.Stopped(OAuthProblem.Unavailable)
                    !browser.open(result.authorizationUrl) -> OAuthStep.Stopped(OAuthProblem.NoBrowser).also { attempts.clear() }
                    else -> OAuthStep.InBrowser.also { authorizationUrl = result.authorizationUrl }
                }
                OAuthStartResult.Unauthenticated, OAuthStartResult.Unavailable -> OAuthStep.Stopped(OAuthProblem.Unavailable)
            }
        }
    }

    /** 브라우저를 닫았을 때 같은 제공자 화면을 다시 연다. 시도가 끝났거나 만료됐으면 처음부터 한다. */
    fun reopen() {
        val url = authorizationUrl
        if (_step.value != OAuthStep.InBrowser || url == null || attempts.pending() == null) return start()
        if (browser?.open(url) != true) _step.value = OAuthStep.Stopped(OAuthProblem.NoBrowser)
    }

    private fun onReturn(uri: String) {
        if (links?.take(uri) != true) return
        when (val result = attempts.accept(uri, OAuthPurpose.LogIn)) {
            OAuthReturn.Ignored -> Unit
            is OAuthReturn.Restart -> {
                authorizationUrl = null
                _step.value = OAuthStep.Stopped(result.reason.problem())
            }
            is OAuthReturn.Accepted -> complete(result.callback)
        }
    }

    private fun complete(callback: OAuthCallback) {
        authorizationUrl = null
        _step.value = OAuthStep.Completing
        viewModelScope.launch {
            _step.value = when (val result = authRepository.completeSocialLogIn(callback)) {
                is SocialLoginResult.Success -> {
                    // 이메일 로그인과 같이 남은 가입 흐름과 이전 회원 정보를 지우고 새 회원 정보를 읽는다.
                    draft.clear()
                    userRepository?.logOut()
                    userRepository?.refresh()
                    if (result.withdrawalCancelled) OAuthStep.WithdrawalCancelled else OAuthStep.LoggedIn
                }
                is SocialLoginResult.MergeAvailable -> OAuthStep.MergeAvailable.also { attempts.offerMerge(result.offer) }
                SocialLoginResult.NotRegistered -> OAuthStep.NoAccount
                SocialLoginResult.Restart -> OAuthStep.Stopped(OAuthProblem.Invalid)
                SocialLoginResult.Unavailable -> OAuthStep.Stopped(OAuthProblem.Unavailable)
            }
        }
    }

    /** 화면을 나가면 진행 중 시도를 버린다. 늦게 돌아온 복귀 주소는 받을 화면이 없어 버려진다. */
    override fun onCleared() {
        if (_step.value == OAuthStep.Starting || _step.value == OAuthStep.InBrowser) attempts.clear()
    }
}

internal fun OAuthRestartReason.problem(): OAuthProblem = when (this) {
    OAuthRestartReason.Cancelled -> OAuthProblem.Cancelled
    OAuthRestartReason.Expired -> OAuthProblem.Expired
    OAuthRestartReason.Invalid -> OAuthProblem.Invalid
}
