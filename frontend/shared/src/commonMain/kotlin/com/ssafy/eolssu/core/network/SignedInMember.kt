package com.ssafy.eolssu.core.network

import com.ssafy.eolssu.core.storage.KeyValueStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 로그인 쿠키의 주인인 회원 ID(로그인 응답의 `userId`). 앱을 다시 켜도 로그인이 이어지므로 기기에 남긴다.
 * 비밀 값이 아니라서 [KeyValueStore]에 둔다. 모르면(로그아웃, 이 값을 남기기 전에 로그인함) null이다.
 *
 * 회원별 기록을 보내는 쪽은 [whileSignedIn] 안에서 보내고, 로그인·로그아웃은 [changing] 안에서 한다.
 * 그래서 이전 회원의 요청이 새 회원의 쿠키로 나가거나(토큰 갱신 포함) 새 회원의 요청이 이전 회원 ID로 판단되지 않는다.
 * 로그인 경로(간편 로그인·가입 직후 로그인)를 더하면 그 경로도 [changing]에서 [set]을 불러야 한다.
 */
class SignedInMember(private val store: KeyValueStore) {
    private val lock = Mutex()

    val id: String? get() = store.getString(KEY)?.ifEmpty { null }

    /** 지금 회원 ID로 [block]을 한다. 끝날 때까지 회원이 바뀌지 않는다. */
    suspend fun <T> whileSignedIn(block: suspend (memberId: String?) -> T): T = lock.withLock { block(id) }

    /** 회원을 바꾸는 일(로그인·로그아웃 요청)을 한다. [whileSignedIn]으로 하던 요청이 끝난 뒤 시작하고, 그동안 새 요청은 기다린다. */
    suspend fun <T> changing(block: suspend () -> T): T = lock.withLock { block() }

    /** [changing] 안에서 새 회원을 정한다. null이면 지운다. */
    fun set(memberId: String?) = store.putString(KEY, memberId.orEmpty())

    private companion object {
        const val KEY = "auth.member_id"
    }
}
