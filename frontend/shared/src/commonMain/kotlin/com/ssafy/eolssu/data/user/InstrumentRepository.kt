package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.storage.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 지금 연습하는 악기. 가입(AUTH-07)에서 고르고 마이페이지(MY-01)에서 바꾸며, 홈은 보여 주기만 한다.
 * 기기에 남겨 앱을 다시 켜도 유지하고, 고른 적이 없으면 서버 기본값과 같은 단소다.
 * 가입·회원 정보 API를 붙이면 서버의 `instrumentId`와 맞춘다.
 */
class InstrumentRepository(private val store: KeyValueStore) {
    private val _instrument = MutableStateFlow(
        store.getString(KEY)?.let { name -> Instrument.entries.firstOrNull { it.name == name } } ?: Instrument.Danso,
    )
    val instrument: StateFlow<Instrument> = _instrument.asStateFlow()

    fun select(instrument: Instrument) {
        store.putString(KEY, instrument.name)
        _instrument.value = instrument
    }

    private companion object {
        const val KEY = "practice_instrument"
    }
}
