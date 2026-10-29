package com.ssafy.eolssu.feature.profile

import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.user.AuthRepository
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.UserProfile
import com.ssafy.eolssu.data.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MY-01. 내 정보·간편 로그인 연결은 [UserRepository], 연습 악기는 [InstrumentRepository]에서 바로 받아 보여 준다.
 * 연결 해제와 로그아웃은 확인 창([dialog])을 거친다. 로그아웃하면 남은 연습 시간 기록을 한 번 보내 본 뒤
 * [AuthRepository]가 서버에 알리고 로그인 쿠키를 지운다.
 */
class MyPageViewModel(
    private val userRepository: UserRepository,
    private val instrumentRepository: InstrumentRepository,
    private val practiceTimeQueue: PracticeTimeQueue,
    private val authRepository: AuthRepository,
) : ViewModel() {
    val profile: StateFlow<UserProfile> = userRepository.profile
    val instrument: StateFlow<Instrument?> = instrumentRepository.instrument

    private val _dialog = MutableStateFlow<MyPageDialog?>(null)
    val dialog: StateFlow<MyPageDialog?> = _dialog.asStateFlow()

    /** 실제 내 정보를 다시 읽는다. */
    suspend fun refresh() = userRepository.refresh()

    private var savingInstrument = false

    suspend fun selectInstrument(instrument: Instrument): Boolean {
        if (savingInstrument) return false
        if (instrumentRepository.instrument.value == instrument) return false
        savingInstrument = true
        return try {
            val id = instrumentRepository.serverIdFor(instrument) ?: return false
            if (userRepository.changeInstrument(id) != com.ssafy.eolssu.data.user.MemberResult.Done) return false
            if (userRepository.profile.value.instrumentId != id) return false
            instrumentRepository.select(instrument)
            true
        } finally { savingInstrument = false }
    }

    fun askUnlink(provider: SocialProvider) {
        _dialog.value = MyPageDialog.Unlink(provider)
    }

    /** 확인 창의 제공자 연결을 해제하고 그 제공자를 돌려준다. */
    fun confirmUnlink(): SocialProvider? {
        val provider = (_dialog.value as? MyPageDialog.Unlink)?.provider ?: return null
        if (!userRepository.unlink(provider)) return null
        _dialog.value = null
        return provider
    }

    fun askLogOut() {
        _dialog.value = MyPageDialog.LogOut
    }

    fun confirmLogOut() {
        practiceTimeQueue.logOut {
            authRepository.logOut()
            userRepository.logOut()
        }
        _dialog.value = null
    }

    fun dismissDialog() {
        _dialog.value = null
    }
}
