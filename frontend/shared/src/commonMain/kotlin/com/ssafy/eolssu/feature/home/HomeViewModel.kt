package com.ssafy.eolssu.feature.home

import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import com.ssafy.eolssu.core.network.inAccount
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.MemberResult
import com.ssafy.eolssu.data.user.UserRepository
import com.ssafy.eolssu.data.user.changePracticeInstrument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** HOME-01. 현재 악기를 보여 주고, 상단 드롭다운에서 고르면 마이페이지(MY-01)와 같은 절차로 서버·로컬 선택을 바꾼다. */
class HomeViewModel(
    private val instrumentRepository: InstrumentRepository,
    private val userRepository: UserRepository,
    member: SignedInMember? = null,
) : ViewModel() {
    val instrument: StateFlow<Instrument?> = instrumentRepository.instrument

    private val _savingInstrument = MutableStateFlow(false)
    val savingInstrument: StateFlow<Boolean> = _savingInstrument.asStateFlow()

    /** 같은 선택과 저장 중 재선택은 무시하며(null), 서버의 실패 종류를 화면에 전달한다. */
    private val accountScope = member.screenScope(viewModelScope) { clearAccount() }
    private fun clearAccount() {
        _savingInstrument.value = false
    }

    suspend fun selectInstrument(instrument: Instrument): MemberResult? = accountScope.inAccount {
        if (_savingInstrument.value) return@inAccount null
        if (instrumentRepository.instrument.value == instrument) return@inAccount null
        _savingInstrument.value = true
        return@inAccount try { userRepository.changePracticeInstrument(instrumentRepository, instrument) }
        finally { _savingInstrument.value = false }
    }
}
