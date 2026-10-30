package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.network.ApiJson
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.SecureStore
import io.ktor.http.Url
import kotlinx.serialization.Serializable

/** 복귀 주소를 받을 화면을 정할 때 쓰는 진행 중 시도의 제공자·목적. */
data class OAuthTarget(val provider: SocialProvider, val purpose: OAuthPurpose)

/** 복귀 주소 확인 결과. */
sealed interface OAuthReturn {
    /** 진행 중 시도의 복귀다. 시도는 소비했으므로 [callback]으로 완료 요청을 한 번만 보낸다. */
    class Accepted(val callback: OAuthCallback) : OAuthReturn {
        override fun toString() = "Accepted($callback)"
    }

    /** 이미 처리한 복귀(중복·재생)거나 진행 중 시도와 상관없는 주소다. 아무것도 하지 않는다. */
    data object Ignored : OAuthReturn

    /** 진행할 수 없는 복귀다. 시도를 지웠으므로 처음부터 다시 한다. */
    data class Restart(val reason: OAuthRestartReason) : OAuthReturn
}

enum class OAuthRestartReason {
    /** 제공자 화면에서 취소했거나 제공자가 오류로 돌려보냈다(`error` 매개변수). */
    Cancelled,

    /** start 응답의 만료 시각(10분)이 지났다. */
    Expired,

    /** 진행 중 시도가 없거나(앱을 지운 뒤 등), 제공자·목적·회원이 시작할 때와 다르다. */
    Invalid,
}

/**
 * 간편 로그인 시도 상태(제공자·목적·정확한 redirectUri·state·만료·연결 회원)를 보관하고 복귀 주소를 확인한다.
 * 시도는 [store]에 암호화해 남겨 브라우저에 다녀오는 동안 앱 프로세스가 끝나도 이어 갈 수 있다(웹·iOS처럼 없으면 메모리만).
 * 복귀를 받아들이면 시도를 먼저 지워 같은 state로 완료 요청을 두 번 보내지 않는다.
 * 연결(LINK)은 시작한 회원과 지금 회원이 다르면 받아들이지 않는다(다른 회원의 흐름으로 복구하지 않음).
 * 간편 로그인이 409로 돌려준 계정 연결 제안([MergeOffer])도 계정 연결 확인 화면에 넘길 때까지 여기 메모리에만 둔다.
 */
class OAuthAttempts(
    private val store: SecureStore?,
    private val member: SignedInMember?,
    private val now: () -> Long,
) {
    private var memory: Attempt? = null

    /** 마지막으로 처리한 복귀의 state. 같은 주소가 다시 와도 오류 대신 무시한다. 프로세스 안에서만 기억한다. */
    private var handledState: String? = null

    private var merge: MergeOffer? = null

    /**
     * start 응답으로 새 시도를 남긴다. 이전 시도는 버린다. state는 [authorizationUrl]의 `state` 매개변수다.
     * 주소가 https가 아니거나 state가 서버 형식(43자 base64url)이 아니면 남기지 않고 false.
     */
    fun begin(provider: SocialProvider, purpose: OAuthPurpose, authorizationUrl: String, expiresAtMillis: Long): Boolean {
        val url = runCatching { Url(authorizationUrl) }.getOrNull() ?: return false
        val state = url.parameters["state"]
        if (url.protocol.name != "https" || state == null || !statePattern.matches(state)) return false
        save(Attempt(provider.name, purpose.name, OAuthRedirect.uriFor(provider), state, expiresAtMillis, member?.id))
        return true
    }

    /** 진행 중인 시도. 만료됐으면 지우고 null. */
    fun pending(): OAuthTarget? {
        val attempt = load() ?: return null
        if (now() >= attempt.expiresAtMillis) {
            clear()
            return null
        }
        return attempt.target()
    }

    /** [purpose] 화면이 받은 복귀 주소 [uri]를 확인한다. 받아들이거나 다시 시작해야 하면 시도를 지운다. */
    fun accept(uri: String, purpose: OAuthPurpose): OAuthReturn {
        val attempt = load()
        val url = runCatching { Url(uri) }.getOrNull()
        val provider = url?.let(::callbackProvider)
        // 우리 복귀 주소가 아니면 진행 중 시도는 그대로 둔다.
        if (url == null || provider == null) return if (attempt != null) OAuthReturn.Ignored else restart(OAuthRestartReason.Invalid)
        val state = url.parameters["state"]
        if (state != null && state == handledState) return OAuthReturn.Ignored
        if (attempt == null) return restart(OAuthRestartReason.Invalid)
        if (now() >= attempt.expiresAtMillis) return restart(OAuthRestartReason.Expired)
        // 다른(이전) 시도의 복귀. 지금 시도는 그대로 둔다.
        if (state != attempt.state) return OAuthReturn.Ignored
        handledState = state
        val target = attempt.target()
        if (target == null || target.provider != provider || target.purpose != purpose) return restart(OAuthRestartReason.Invalid)
        if (purpose == OAuthPurpose.Link && attempt.memberId != member?.id) return restart(OAuthRestartReason.Invalid)
        if (url.parameters["error"] != null) return restart(OAuthRestartReason.Cancelled)
        val code = url.parameters["code"]
        if (code.isNullOrBlank()) return restart(OAuthRestartReason.Invalid)
        clear()
        return OAuthReturn.Accepted(OAuthCallback(provider, code, attempt.redirectUri, state))
    }

    /** 계정 연결 확인(AUTH-09)에 넘길 제안. 저장소에 남기지 않는다. */
    fun offerMerge(offer: MergeOffer) {
        merge = offer
    }

    /** 남은 계정 연결 제안. 만료됐으면 지우고 null. */
    fun mergeOffer(): MergeOffer? {
        val offer = merge ?: return null
        if (now() < offer.expiresAtMillis) return offer
        merge = null
        return null
    }

    fun clearMerge() {
        merge = null
    }

    /** 진행 중 시도를 버린다(화면에서 나가거나 다시 시작할 때). */
    fun clear() {
        memory = null
        store?.remove(KEY)
    }

    private fun restart(reason: OAuthRestartReason): OAuthReturn {
        clear()
        return OAuthReturn.Restart(reason)
    }

    /** https·정확한 호스트·기본 포트·`/auth/android/oauth/<제공자>/callback` 경로일 때 그 제공자. */
    private fun callbackProvider(url: Url): SocialProvider? {
        if (url.protocol.name != "https" || !url.host.equals(OAuthRedirect.HOST, ignoreCase = true)) return null
        if (url.specifiedPort != 0 && url.specifiedPort != 443) return null
        if (url.user != null || url.password != null) return null
        return SocialProvider.entries.firstOrNull { url.encodedPath == OAuthRedirect.pathFor(it) }
    }

    private fun save(attempt: Attempt) {
        memory = attempt
        store?.putString(KEY, ApiJson.encodeToString(Attempt.serializer(), attempt))
    }

    private fun load(): Attempt? = memory ?: store?.getString(KEY)
        ?.let { runCatching { ApiJson.decodeFromString(Attempt.serializer(), it) }.getOrNull() }
        ?.also { memory = it }

    @Serializable
    private class Attempt(
        val provider: String,
        val purpose: String,
        val redirectUri: String,
        val state: String,
        val expiresAtMillis: Long,
        val memberId: String? = null,
    ) {
        fun target(): OAuthTarget? {
            val provider = SocialProvider.entries.firstOrNull { it.name == provider } ?: return null
            val purpose = OAuthPurpose.entries.firstOrNull { it.name == purpose } ?: return null
            return OAuthTarget(provider, purpose)
        }

        override fun toString() = "Attempt(provider=$provider, purpose=$purpose)"
    }

    private companion object {
        const val KEY = "auth.oauth_attempt"
        val statePattern = Regex("[A-Za-z0-9_-]{43}")
    }
}
