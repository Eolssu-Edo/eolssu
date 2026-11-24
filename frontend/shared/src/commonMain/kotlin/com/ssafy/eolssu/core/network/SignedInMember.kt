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
    private val listeners = MutableStateFlow<List<Listener>>(emptyList())

    private class Listener(val ownChangesOnly: Boolean, val action: () -> Unit)

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
        change(memberId, own = true)
    }

    /**
     * 다른 탭(웹)이 저장소의 로그인 회원을 바꿨을 때 이 탭도 따른다. 세대가 바뀌어 이전 계정의 진행 중 작업은 결과를 버린다.
     * 저장소는 이미 바뀌었으므로 다시 쓰지 않고, 저장소를 고치는 리스너([onChange]의 ownChangesOnly)는 부르지 않는다.
     */
    fun follow(memberId: String?) {
        if (memberId != id) change(memberId, own = false)
    }

    private fun change(memberId: String?, own: Boolean) {
        state.update { AccountSession(memberId, it.epoch + 1) }
        listeners.value.forEach { if (own || !it.ownChangesOnly) it.action() }
    }

    /** 회원이 바뀌면 [clear]를 부른다. [ownChangesOnly]면 이 실행이 바꿨을 때만 부른다([follow]는 제외). */
    fun onChange(ownChangesOnly: Boolean = false, clear: () -> Unit): () -> Unit {
        val listener = Listener(ownChangesOnly, clear)
        listeners.update { it + listener }
        return { listeners.update { it - listener } }
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

    companion object {
        /** 로그인 회원을 남기는 [KeyValueStore] 키. 웹은 다른 탭의 변경을 이 키로 알아챈다. */
        const val KEY = "auth.member_id"
    }
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
