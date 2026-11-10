package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Url
import kotlinx.serialization.Serializable

/**
 * 회원 요청 결과. [TicketExpired]는 소셜 가입·탈퇴 티켓의 만료·재사용(410), [SocialAccountInUse]는 그 제공자 계정이 다른 회원에게 연결됨(409),
 * [LastLoginMethod]는 비밀번호 없는 회원의 마지막 간편 로그인 해제 거절(409)이다.
 */
enum class MemberResult {
    Done, InvalidInput, VerificationRequired, TermsRequired, EmailExists, Unauthenticated, RateLimited, Unavailable,
    TicketExpired, SocialAccountInUse, LastLoginMethod,
}

@Serializable
data class TermAgreement(val termId: String, val version: String, val agreed: Boolean)

@Serializable
data class Registration(val email: String, val password: String, val nickname: String, val verificationToken: String, val agreements: List<TermAgreement>, val instrumentId: String? = null)

/** 가입 도중의 비밀값은 저장 상태/URL/디스크에 남기지 않는다. 프로세스가 종료되면 다시 인증한다. */
class AccountDraft {
    var email: String = ""
    var token: String = ""
    var password: String = ""
    var purpose: VerificationPurpose? = null
    var agreements: List<TermAgreement> = emptyList()
    fun clearVerification() { email = ""; token = ""; password = ""; purpose = null }
    fun clear() { clearVerification(); agreements = emptyList() }
}

@Serializable
data class AccountTerm(val termId: String, val code: String, val title: String, val required: Boolean, val version: String, val contentUrl: String)

class TermsRepository(private val client: HttpClient) {
    suspend fun load(): List<AccountTerm>? {
        val response = client.sendOrNull { get("/api/terms") } ?: return null
        if (response.status.value != 200) return null
        val body = response.apiResponse<TermList>()?.takeIf { it.isSuccess }?.result ?: return null
        return body.items.takeIf { items ->
            items.any { it.required } && items.map { it.termId }.distinct().size == items.size &&
                items.all { it.termId.matches(Regex("[1-9][0-9]*")) && it.version.isNotBlank() && it.title.isNotBlank() && isHttpsDocumentUrl(it.contentUrl) }
        }
    }
    /**
     * 약관 본문([AccountTerm.contentUrl]의 HTML)을 받아 [TermsBlock] 목록으로 바꾼다. 공개 문서라 화면에서 바로 보여 준다.
     * HTTPS 문서 주소가 아니거나, 200이 아니거나(공개 준비 중 503 등), 본문이 비었거나, 응답을 받지 못하면 null.
     */
    suspend fun loadDocument(contentUrl: String): List<TermsBlock>? {
        if (!isHttpsDocumentUrl(contentUrl)) return null
        // 문서 주소는 text/html만 내준다. 공통 클라이언트는 JSON만 받겠다고 보내 406이 나므로 HTML을 받겠다고 밝힌다.
        val response = client.sendOrNull { get(contentUrl) { accept(ContentType.Text.Html) } } ?: return null
        if (response.status.value != 200) return null
        val html = runCatching { response.bodyAsText() }.getOrNull() ?: return null
        return parseTermsDocument(html).takeIf { it.isNotEmpty() }
    }

    @Serializable private class TermList(val items: List<AccountTerm>)
}

internal fun isHttpsDocumentUrl(value: String): Boolean {
    val url = runCatching { Url(value) }.getOrNull() ?: return false
    return url.protocol.name == "https" && url.host.isNotBlank() && url.user == null && url.password == null
}
