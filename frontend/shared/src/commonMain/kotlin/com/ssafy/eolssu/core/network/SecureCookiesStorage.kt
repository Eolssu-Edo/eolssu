package com.ssafy.eolssu.core.network

import com.ssafy.eolssu.core.storage.SecureStore
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.fillDefaults
import io.ktor.client.plugins.cookies.matches
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.Url
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * 앱(Android·iOS)의 로그인 쿠키(`access_token`, `refresh_token`)를 [store]에 암호화해 남긴다.
 * 앱을 다시 켜도 리프레시 토큰이 살아 있는 동안(최대 14일) 로그인이 이어진다. 만료된 쿠키는 보내지 않는다.
 */
class SecureCookiesStorage(private val store: SecureStore, private val now: () -> Long) : CookiesStorage {
    private val mutex = Mutex()
    private var loaded: List<StoredCookie>? = null

    override suspend fun get(requestUrl: Url): List<Cookie> = mutex.withLock {
        val now = now()
        cookies().filter { it.alive(now) }.map { it.toCookie() }.filter { it.matches(requestUrl) }
    }

    /** 서버가 보낸 쿠키를 남긴다. 같은 이름·도메인·경로의 쿠키는 바꾸고, 이미 만료됐으면(로그아웃의 `Max-Age=0`) 지운다. */
    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) = mutex.withLock {
        val filled = cookie.fillDefaults(requestUrl)
        val now = now()
        val expiresAt = filled.maxAge?.let { now + it * 1000L } ?: filled.expires?.timestamp
        val stored = StoredCookie(
            name = filled.name,
            value = filled.value,
            encoding = filled.encoding.name,
            domain = filled.domain.orEmpty().trimStart('.').lowercase(),
            path = filled.path ?: "/",
            expiresAt = expiresAt,
            secure = filled.secure,
            httpOnly = filled.httpOnly,
        )
        val others = cookies().filterNot { it.name == stored.name && it.domain == stored.domain && it.path == stored.path }
        save(if (stored.alive(now)) others + stored else others)
    }

    /** 로그아웃: 남은 쿠키를 모두 지운다. */
    suspend fun clear() = mutex.withLock { save(emptyList()) }

    override fun close() = Unit

    private fun cookies(): List<StoredCookie> = loaded ?: run {
        val saved = store.getString(KEY)?.let { runCatching { ApiJson.decodeFromString(Serializer, it) }.getOrNull() }
        saved.orEmpty().also { loaded = it }
    }

    private fun save(cookies: List<StoredCookie>) {
        loaded = cookies
        if (cookies.isEmpty()) store.remove(KEY) else store.putString(KEY, ApiJson.encodeToString(Serializer, cookies))
    }

    @Serializable
    private class StoredCookie(
        val name: String,
        val value: String,
        val encoding: String,
        val domain: String,
        val path: String,
        val expiresAt: Long?,
        val secure: Boolean,
        val httpOnly: Boolean,
    ) {
        fun alive(now: Long) = expiresAt == null || expiresAt > now

        fun toCookie() = Cookie(
            name = name,
            value = value,
            encoding = CookieEncoding.entries.firstOrNull { it.name == encoding } ?: CookieEncoding.RAW,
            domain = domain,
            path = path,
            secure = secure,
            httpOnly = httpOnly,
        )
    }

    private companion object {
        const val KEY = "auth.cookies"
        val Serializer = ListSerializer(StoredCookie.serializer())
    }
}
