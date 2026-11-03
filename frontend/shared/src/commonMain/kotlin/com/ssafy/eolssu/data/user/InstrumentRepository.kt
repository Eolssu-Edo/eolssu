package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.storage.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.ssafy.eolssu.data.song.InstrumentCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.updateAndGet

/**
 * 지금 연습하는 악기. 가입(AUTH-07)에서 고르고 마이페이지(MY-01)에서 바꾸며, 홈은 보여 주기만 한다.
 * 앱에서는 활성 악기 목록의 code와 회원 `instrumentId`를 대조한다. 확인 전에는 미확인 상태다.
 * catalog를 생략한 테스트만 기존 로컬 선택과 단소 기본값을 사용한다.
 */
class InstrumentRepository(private val store: KeyValueStore, private val catalog: InstrumentCatalog? = null) {
    private val _instrument = MutableStateFlow<Instrument?>(
        if (catalog != null) null else store.getString(KEY)?.let { name -> Instrument.entries.firstOrNull { it.name == name } } ?: Instrument.Danso,
    )
    val instrument: StateFlow<Instrument?> = _instrument.asStateFlow()
    private val revision = MutableStateFlow(0L)

    /** 활성 목록의 code로 실제 ID를 찾는다. 순번·seed ID를 쓰지 않는다. */
    suspend fun serverIdFor(instrument: Instrument): String? = try {
        catalog?.load()?.firstOrNull { it.code == instrument.serverCode }?.instrumentId
    } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }

    /** 모르는 ID/code나 조회 실패를 이전 계정의 악기로 표시하지 않는다. */
    suspend fun applyServerId(id: String): Boolean {
        clear()
        val requestedRevision = revision.value
        val code = try { catalog?.load()?.firstOrNull { it.instrumentId == id }?.code }
            catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
        if (revision.value != requestedRevision) return false
        val instrument = Instrument.entries.firstOrNull { it.serverCode == code } ?: return false
        select(instrument)
        return true
    }

    fun clear() { revision.updateAndGet { it + 1 }; _instrument.value = null }

    fun select(instrument: Instrument) {
        revision.updateAndGet { it + 1 }
        store.putString(KEY, instrument.name)
        _instrument.value = instrument
    }

    private companion object {
        const val KEY = "practice_instrument"
    }
}
