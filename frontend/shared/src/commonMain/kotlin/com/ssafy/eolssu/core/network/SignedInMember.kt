package com.ssafy.eolssu.core.network

import com.ssafy.eolssu.core.storage.KeyValueStore
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 같은 회원의 재로그인도 이전 로그인과 구별한다. 토큰 갱신은 세대를 바꾸지 않는다. */
data class AccountSession(val memberId: String?, val epoch: Long)

/** 로그인 쿠키의 소유자와 로그인 세대. 인증 변경과 개별 HTTP 요청은 같은 Mutex를 사용한다. */
class SignedInMember(private val store: KeyValueStore) {
    private val lock = Mutex()
    private val state = MutableStateFlow(AccountSession(store.getString(KEY)?.ifEmpty { null }, 0))
    val session = state.asStateFlow()
    val id: String? get() = state.value.memberId
    private val listeners = MutableStateFlow<List<() -> Unit>>(emptyList())

    /** 대기열에 들어가기 전에 소유자를 잡는다. 대기 중 로그인 변경 시 새 쿠키로 보내지 않는다. */
    suspend fun <T> whileSignedIn(block: suspend (String?) -> T): T {
        val work = currentCoroutineContext()[AccountWork] ?: AccountWork(this, session.value)
        return withContext(work) { request { block(id) } }
    }

    suspend fun <T> changing(block: suspend () -> T): T {
        val expected = currentCoroutineContext()[AccountWork]
        return lock.withLock {
            expected?.check()
            withContext(AuthenticationLock(this)) { block() }
        }
    }

    /** 탈퇴처럼 현재 회원을 변경하는 요청. 대기 중 다른 로그인이 되면 실행하지 않는다. */
    suspend fun <T> changingCurrent(block: suspend () -> T): T {
        val work = currentCoroutineContext()[AccountWork] ?: AccountWork(this, session.value)
        return withContext(work) { changing(block) }
    }

    /** [changing] 안에서 호출한다. 앱 시작의 세션 만료 처리에도 사용한다. */
    fun set(memberId: String?) {
        store.putString(KEY, memberId.orEmpty())
        state.update { AccountSession(memberId, it.epoch + 1) }
        listeners.value.forEach { it() }
    }

    fun onChange(clear: () -> Unit): () -> Unit {
        listeners.update { it + clear }
        return { listeners.update { it - clear } }
    }

    internal suspend fun <T> request(block: suspend () -> T): T {
        val context = currentCoroutineContext()
        val work = context[AccountWork]
        if (context[AuthenticationLock]?.member === this) {
            work?.check()
            return block().also { work?.check() }
        }
        return lock.withLock {
            work?.check()
            withContext(AuthenticationLock(this)) { block().also { work?.check() } }
        }
    }

    companion object { private const val KEY = "auth.member_id" }
}

private class AuthenticationLock(val member: SignedInMember) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AuthenticationLock>
}

class AccountWork internal constructor(val member: SignedInMember, private val expected: AccountSession) :
    AbstractCoroutineContextElement(Key) {
    val isCurrent: Boolean get() = member.session.value == expected
    fun check() {
        if (!isCurrent) throw CancellationException("로그인 세션이 변경됨")
    }
    companion object Key : CoroutineContext.Key<AccountWork>
}

/** 여러 요청의 작업은 세대를 유지하되 인증 잠금은 각 HTTP 요청 동안만 잡는다. */
suspend fun <T> SignedInMember?.inSession(block: suspend () -> T): T {
    val work = currentCoroutineContext()[AccountWork] ?: this?.let { AccountWork(it, it.session.value) }
    work?.check()
    return if (work == null) block() else withContext(work) {
        block().also { ensureAccountSession() }
    }
}

suspend fun ensureAccountSession() {
    currentCoroutineContext().ensureActive()
    currentCoroutineContext()[AccountWork]?.check()
}

/** 이전 화면의 콜백은 새 계정에서 다시 살아나지 않는다. */
fun SignedInMember?.screenScope(parent: CoroutineScope, cancelWork: Boolean = true, clear: () -> Unit): CoroutineScope {
    if (this == null) return parent
    val job = SupervisorJob(parent.coroutineContext[Job])
    val scope = CoroutineScope(parent.coroutineContext + job + AccountWork(this, session.value))
    val remove = onChange { if (cancelWork) job.cancel(); clear() }
    job.invokeOnCompletion { remove() }
    return scope
}

/** Route에서 suspend 메서드를 부를 때도 화면 생성 시의 계정을 유지한다. */
suspend fun <T> CoroutineScope.inAccount(block: suspend () -> T): T = withContext(coroutineContext) {
    ensureAccountSession()
    block()
}
