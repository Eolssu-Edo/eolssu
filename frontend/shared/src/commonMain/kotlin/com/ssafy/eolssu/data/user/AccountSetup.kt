package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.Url
import kotlinx.serialization.Serializable

enum class MemberResult { Done, InvalidInput, VerificationRequired, TermsRequired, EmailExists, Unauthenticated, RateLimited, Unavailable }

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
    @Serializable private class TermList(val items: List<AccountTerm>)
}

internal fun isHttpsDocumentUrl(value: String): Boolean {
    val url = runCatching { Url(value) }.getOrNull() ?: return false
    return url.protocol.name == "https" && url.host.isNotBlank() && url.user == null && url.password == null
}
