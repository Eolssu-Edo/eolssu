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
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.user.MemberResult
import com.ssafy.eolssu.data.user.OAuthAttempts
import com.ssafy.eolssu.data.user.OAuthCallback
import com.ssafy.eolssu.data.user.OAuthPurpose
import com.ssafy.eolssu.data.user.OAuthRestartReason
import com.ssafy.eolssu.data.user.OAuthReturn
import com.ssafy.eolssu.data.user.OAuthStartResult
import com.ssafy.eolssu.data.user.PasswordCheckResult
import com.ssafy.eolssu.data.user.ReauthResult
import com.ssafy.eolssu.data.user.UserProfile
import com.ssafy.eolssu.data.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MY-04. 현재 비밀번호나 연결된 간편 로그인 재인증으로 본인인지 다시 확인한 뒤 탈퇴를 신청한다.
 * 재인증은 연결(MY-05)과 같이 start → 외부 브라우저의 제공자 화면 → 복귀 주소 → 완료 순서이고, 완료로 받은 탈퇴 증명으로 바로 탈퇴를 신청한다.
 * 시도 상태·복귀 주소 확인은 [attempts](목적 WITHDRAW)가, 복귀 주소 전달은 [links]가 맡는다. [browser]가 없는 플랫폼은 재인증을 하지 않는다.
 */
class WithdrawViewModel(
    private val userRepository: UserRepository,
    private val practiceTimeQueue: PracticeTimeQueue,
    private val attempts: OAuthAttempts? = null,
    private val links: IncomingLinks? = null,
    private val browser: ExternalBrowser? = null,
    member: SignedInMember? = null,
) : ViewModel() {
    private var submitting = false
    private val _uiState = MutableStateFlow(WithdrawUiState())
    val uiState: StateFlow<WithdrawUiState> = _uiState.asStateFlow()

    /** 비밀번호 칸과 재인증 버튼을 고르는 데 쓰는 내 정보. */
    val profile: StateFlow<UserProfile> = userRepository.profile

    /** 지금 재인증의 제공자 화면 주소. '다시 열기'에 쓰고 화면·로그에 내보내지 않는다. */
    private var authorizationUrl: String? = null

    private val accountScope = member.screenScope(viewModelScope, cancelWork = false) { clearAccount() }
    private fun clearAccount() {
        authorizationUrl = null
        attempts?.clear()
        _uiState.value = WithdrawUiState()
    }

    init {
        // 앱이 꺼진 사이 재인증 복귀가 왔으면 새로 시작하지 않고 그 주소를 처리한다.
        val pending = attempts?.pending()
        if (links?.pending?.value != null && pending?.purpose == OAuthPurpose.Withdraw) {
            _uiState.update { it.copy(reauth = WithdrawReauth(pending.provider, WithdrawReauth.Step.InBrowser)) }
        }
        links?.let { accountScope.launch { it.pending.filterNotNull().collect(::onReturn) } }
    }

    fun changePassword(value: String) = _uiState.update { it.copy(password = value, error = null) }

    /** 신청했으면 true. */
    suspend fun submit(): Boolean = accountScope.inAccount {
        if (submitting || _uiState.value.reauth?.busy == true) return@inAccount false
        val password = _uiState.value.password
        if (password.isEmpty()) {
            _uiState.update { it.copy(error = ProfileFieldError.PasswordEmpty) }
            return@inAccount false
        }
        submitting = true
        val result = try { userRepository.withdraw(password) } finally { submitting = false }
        return@inAccount when (result) {
            PasswordCheckResult.Done -> true.also {
                practiceTimeQueue.discard()
                _uiState.value = WithdrawUiState()
            }
            PasswordCheckResult.Unavailable -> false.also { _uiState.update { it.copy(error = ProfileFieldError.Unavailable) } }
            PasswordCheckResult.Unauthenticated -> false.also { _uiState.update { it.copy(error = ProfileFieldError.Unauthenticated) } }
            PasswordCheckResult.RateLimited -> false.also { _uiState.update { it.copy(error = ProfileFieldError.RateLimited) } }
            PasswordCheckResult.InvalidInput -> false.also { _uiState.update { it.copy(error = ProfileFieldError.InvalidInput) } }
            PasswordCheckResult.WrongPassword -> false.also { _uiState.update { it.copy(error = ProfileFieldError.PasswordWrong) } }
        }
    }

    /** [provider] 재인증을 처음부터 시작한다. 비밀번호 신청 중이거나 다른 재인증이 진행 중이면 무시한다. */
    fun startReauth(provider: SocialProvider) {
        if (!accountScope.isActive || accountScope.coroutineContext[AccountWork]?.isCurrent == false) return
        if (submitting || _uiState.value.reauth?.busy == true) return
        val attempts = attempts
        val browser = browser
        if (attempts == null || browser == null || links == null) return stop(provider, SocialLinkProblem.Unsupported)
        attempts.clear()
        authorizationUrl = null
        setReauth(provider, WithdrawReauth.Step.Starting)
        accountScope.launch {
            when (val result = userRepository.startWithdrawalReauth(provider)) {
                is OAuthStartResult.Started -> when {
                    !attempts.begin(provider, OAuthPurpose.Withdraw, result.authorizationUrl, result.expiresAtMillis) ->
                        stop(provider, SocialLinkProblem.Unavailable)
                    !browser.open(result.authorizationUrl) -> stop(provider, SocialLinkProblem.NoBrowser).also { attempts.clear() }
                    else -> {
                        authorizationUrl = result.authorizationUrl
                        setReauth(provider, WithdrawReauth.Step.InBrowser)
                    }
                }
                OAuthStartResult.Unauthenticated -> stop(provider, SocialLinkProblem.SessionExpired)
                OAuthStartResult.Unavailable -> stop(provider, SocialLinkProblem.Unavailable)
            }
        }
    }

    /** 브라우저를 닫았을 때 같은 제공자 화면을 다시 연다. 시도가 끝났거나 만료됐으면 처음부터 한다. */
    fun reopen() {
        if (!accountScope.isActive || accountScope.coroutineContext[AccountWork]?.isCurrent == false) return
        val reauth = _uiState.value.reauth?.takeIf { it.step == WithdrawReauth.Step.InBrowser } ?: return
        val url = authorizationUrl
        if (url == null || attempts?.pending() == null) {
            setReauth(reauth.provider, WithdrawReauth.Step.Stopped)
            return startReauth(reauth.provider)
        }
        if (browser?.open(url) != true) stop(reauth.provider, SocialLinkProblem.NoBrowser)
    }

    private fun onReturn(uri: String) {
        if (!accountScope.isActive || accountScope.coroutineContext[AccountWork]?.isCurrent == false) return
        if (links?.take(uri) != true) return
        val attempts = attempts ?: return
        when (val result = attempts.accept(uri, OAuthPurpose.Withdraw)) {
            OAuthReturn.Ignored -> Unit
            is OAuthReturn.Restart -> {
                authorizationUrl = null
                val provider = _uiState.value.reauth?.provider ?: return
                stop(
                    provider,
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
        setReauth(callback.provider, WithdrawReauth.Step.Completing)
        accountScope.launch {
            val proof = when (val result = userRepository.confirmWithdrawalReauth(callback)) {
                is ReauthResult.Proven -> result.proof
                ReauthResult.Restart -> return@launch stop(callback.provider, SocialLinkProblem.Invalid)
                ReauthResult.Unauthenticated -> return@launch stop(callback.provider, SocialLinkProblem.SessionExpired)
                ReauthResult.Unavailable -> return@launch stop(callback.provider, SocialLinkProblem.Unavailable)
            }
            when (userRepository.withdraw(proof)) {
                MemberResult.Done -> {
                    practiceTimeQueue.discard()
                    _uiState.value = WithdrawUiState(withdrawn = true)
                }
                MemberResult.TicketExpired -> {
                    setReauth(callback.provider, WithdrawReauth.Step.Stopped)
                    _uiState.update { it.copy(error = ProfileFieldError.ProofExpired) }
                }
                MemberResult.Unauthenticated -> stop(callback.provider, SocialLinkProblem.SessionExpired)
                else -> stop(callback.provider, SocialLinkProblem.Unavailable)
            }
        }
    }

    private fun setReauth(provider: SocialProvider, step: WithdrawReauth.Step) =
        _uiState.update { it.copy(reauth = WithdrawReauth(provider, step), error = null) }

    private fun stop(provider: SocialProvider, problem: SocialLinkProblem) =
        _uiState.update { it.copy(reauth = WithdrawReauth(provider, WithdrawReauth.Step.Stopped, problem)) }

    /** 화면을 나가면 진행 중 재인증 시도를 버린다. */
    override fun onCleared() {
        val step = _uiState.value.reauth?.step
        if (step == WithdrawReauth.Step.Starting || step == WithdrawReauth.Step.InBrowser) attempts?.clear()
    }
}
