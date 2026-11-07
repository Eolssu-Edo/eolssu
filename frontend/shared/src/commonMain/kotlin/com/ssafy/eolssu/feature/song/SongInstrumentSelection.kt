package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.network.inAccount
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.MemberResult
import com.ssafy.eolssu.data.user.UserRepository
import com.ssafy.eolssu.data.user.changePracticeInstrument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 평가할 곡 고르기(SONG-01) · 연습곡 고르기(SONG-03) 상단 바의 연습 악기 드롭다운.
 * 고르면 마이페이지(MY-01)와 같은 절차([changePracticeInstrument])로 서버·기기 선택을 바꾸고, 바뀐 악기는 [InstrumentRepository]를 통해
 * 곡 목록과 다른 화면(기초부터 배우기·연습 등)이 함께 받는다. [accountScope]는 화면의 계정 범위라 계정이 바뀌면 저장을 이어 가지 않는다.
 */
internal class SongInstrumentSelection(
    private val instrumentRepository: InstrumentRepository,
    private val userRepository: UserRepository,
    private val accountScope: CoroutineScope,
) {
    val instrument: StateFlow<Instrument?> = instrumentRepository.instrument

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    fun clear() {
        _saving.value = false
    }

    /** 같은 선택과 저장 중 재선택은 무시하며(null), 서버의 실패 종류를 화면에 전달한다. */
    suspend fun select(instrument: Instrument): MemberResult? = accountScope.inAccount {
        if (_saving.value) return@inAccount null
        if (instrumentRepository.instrument.value == instrument) return@inAccount null
        _saving.value = true
        return@inAccount try { userRepository.changePracticeInstrument(instrumentRepository, instrument) }
        finally { _saving.value = false }
    }
}
