package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.AccountSession
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.ensureAccountSession
import com.ssafy.eolssu.core.network.inSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 악보 작성에서만 공유한다. 업로드 화면을 닫아도 분석 중 준비를 이어 가며 로그인 세대마다 버린다. */
class SheetAuthoringCatalog(
    private val loadInstruments: suspend () -> List<CatalogInstrument>,
    catalog: ReferenceCatalogRepository,
    private val scope: CoroutineScope,
    private val member: SignedInMember? = null,
) {
    private data class Values(
        val instruments: List<CatalogInstrument>? = null,
        val pitches: List<CatalogPitch>? = null,
        val jangdans: List<CatalogJangdanSummary>? = null,
        val capabilities: Map<String, CatalogAuthoringCapabilities> = emptyMap(),
    )

    private class Session(val account: AccountSession?, scope: CoroutineScope) {
        val job = SupervisorJob(scope.coroutineContext[Job])
        val scope = CoroutineScope(scope.coroutineContext + job)
        val values = MutableStateFlow(Values())
        val lock = Mutex()
    }

    private val session = MutableStateFlow(Session(member?.session?.value, scope))

    init {
        val removeListener = member?.onChange {
            val previous = session.value
            session.value = Session(member.session.value, scope)
            previous.job.cancel()
        }
        scope.coroutineContext[Job]?.invokeOnCompletion {
            removeListener?.invoke()
            session.value.job.cancel()
        }
    }

    /** 일반 카탈로그의 정책은 바꾸지 않고 작성에 필요한 성공 응답만 재사용한다. */
    val references: ReferenceCatalogRepository = object : ReferenceCatalogRepository by catalog {
        override suspend fun allPitches() = cached({ it.pitches }, { values, result -> values.copy(pitches = result) }) {
            catalog.allPitches()
        }
        override suspend fun jangdans() = cached({ it.jangdans }, { values, result -> values.copy(jangdans = result) }) {
            catalog.jangdans()
        }
        override suspend fun authoringCapabilities(instrumentId: String) = cached(
            { it.capabilities[instrumentId] },
            { values, result -> values.copy(capabilities = values.capabilities + (instrumentId to result)) },
        ) { catalog.authoringCapabilities(instrumentId) }
    }

    suspend fun instruments(): List<CatalogInstrument> = cached(
        { it.instruments }, { values, result -> values.copy(instruments = result) }, loadInstruments,
    )

    fun cachedInstruments(): List<CatalogInstrument>? = session.value.values.value.instruments
    fun cachedCapabilities(id: String): CatalogAuthoringCapabilities? = session.value.values.value.capabilities[id]

    /** 한 악기의 실패 때문에 다른 악기 준비를 포기하지 않으며 실패한 응답은 보관하지 않는다. */
    fun warmUp() {
        val expected = session.value
        expected.scope.launch {
            member.inSession {
                checkCurrent(expected)
                val instruments = try { instruments() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { return@inSession }
                prepare { references.allPitches() }
                instruments.forEach { instrument -> prepare { references.authoringCapabilities(instrument.instrumentId) } }
                prepare { references.jangdans() }
            }
        }
    }

    private suspend fun prepare(load: suspend () -> Any) {
        try { load() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* 필요한 화면에서 다시 시도한다. */ }
    }

    private fun checkCurrent(expected: Session) {
        if (session.value !== expected || expected.account != member?.session?.value) {
            throw CancellationException("악보 작성 카탈로그의 로그인 세대가 변경됨")
        }
    }

    private suspend fun <T : Any> cached(read: (Values) -> T?, write: (Values, T) -> Values, load: suspend () -> T): T {
        ensureAccountSession()
        val expected = session.value
        checkCurrent(expected)
        read(expected.values.value)?.let { return it }
        // 기다리던 호출자가 취소돼도 다른 호출자의 작업은 취소하지 않는다.
        return expected.lock.withLock {
            ensureAccountSession()
            checkCurrent(expected)
            read(expected.values.value) ?: load().also { result ->
                ensureAccountSession()
                checkCurrent(expected)
                expected.values.update { write(it, result) }
            }
        }
    }
}
