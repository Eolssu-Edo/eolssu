package com.ssafy.eolssu.feature.profile

import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.network.AccountWork
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import com.ssafy.eolssu.core.network.inAccount
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.user.AuthRepository
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.MemberResult
import com.ssafy.eolssu.data.user.UserProfile
import com.ssafy.eolssu.data.user.UserRepository
import com.ssafy.eolssu.data.user.changePracticeInstrument
import com.ssafy.eolssu.data.user.signsInWithPassword
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
    member: SignedInMember? = null,
) : ViewModel() {
    val profile: StateFlow<UserProfile> = userRepository.profile
    val instrument: StateFlow<Instrument?> = instrumentRepository.instrument

    private val _dialog = MutableStateFlow<MyPageDialog?>(null)
    val dialog: StateFlow<MyPageDialog?> = _dialog.asStateFlow()

    /** 실제 내 정보를 다시 읽는다. */
    private val accountScope = member.screenScope(viewModelScope) { clearAccount() }
    private fun clearAccount() {
        _dialog.value = null
        lastProgress = null
        _loadingProfile.value = false
        _savingInstrument.value = false
    }

    suspend fun refresh(): MemberResult = accountScope.inAccount {
        _loadingProfile.value = true
        return@inAccount try { handleSession(userRepository.refresh()) }
        finally { _loadingProfile.value = false }
    }

    /**
     * 마지막으로 받은 학습 진도와 그 악기. 다른 화면에서 돌아와 진도를 다시 받는 동안 같은 악기면 이 뱃지를 그대로 보여,
     * 뱃지가 잠깐 사라졌다 나타나며 화면이 흔들리지 않게 한다(S15P31E102-731).
     */
    private var lastProgress: MyLearningProgress? = null

    /** 마지막으로 받은 진도 뱃지. 다른 화면에서 돌아와 다시 받는 동안 그대로 보인다. */
    fun lastLearningProgress(): MyLearningProgress? = lastProgress

    fun rememberLearningProgress(progress: MyLearningProgress?) {
        lastProgress = progress
    }

    private val _loadingProfile = MutableStateFlow(true)
    val loadingProfile: StateFlow<Boolean> = _loadingProfile.asStateFlow()
    private val _savingInstrument = MutableStateFlow(false)
    val savingInstrument: StateFlow<Boolean> = _savingInstrument.asStateFlow()

    /** 같은 선택과 저장 중 재탭은 무시하며, 서버의 실패 종류를 화면에 전달한다. 절차는 홈 드롭다운과 같다([changePracticeInstrument]). */
    suspend fun selectInstrument(instrument: Instrument): MemberResult? = accountScope.inAccount {
        if (_savingInstrument.value) return@inAccount null
        if (instrumentRepository.instrument.value == instrument) return@inAccount null
        _savingInstrument.value = true
        return@inAccount try { userRepository.changePracticeInstrument(instrumentRepository, instrument) }
        finally { _savingInstrument.value = false }
    }

    private fun handleSession(result: MemberResult): MemberResult {
        if (result == MemberResult.Unauthenticated) {
            userRepository.logOut()
            instrumentRepository.clear()
        }
        return result
    }

    /**
     * 해제 확인 창을 띄운다. 비밀번호 없는 회원의 마지막 간편 로그인이면 서버가 거절할 것이라 창 없이 false를 돌려준다.
     * 비밀번호 유무를 짐작할 때([signsInWithPassword])는 서버의 409 `LAST_LOGIN_METHOD`가 마지막으로 막는다.
     */
    fun askUnlink(provider: SocialProvider): Boolean {
        val profile = userRepository.profile.value
        if (!profile.signsInWithPassword && profile.linkedProviders == setOf(provider)) return false
        _dialog.value = MyPageDialog.Unlink(provider)
        return true
    }

    /** 비밀번호 변경을 열 수 있는지. 비밀번호 없는 소셜 가입 회원은 서버가 401로 거절하므로 열지 않는다. */
    fun canChangePassword(): Boolean = userRepository.profile.value.signsInWithPassword

    private var unlinking = false

    /**
     * 확인 창의 제공자 연결을 서버에서 해제한다. 해제했거나 마지막 로그인 수단이라 거절되면 창을 닫고, 그 밖의 실패는 창을 남긴다.
     * 창이 없거나 요청 중 다시 누르면 null.
     */
    suspend fun confirmUnlink(): MemberResult? = accountScope.inAccount {
        val provider = (_dialog.value as? MyPageDialog.Unlink)?.provider ?: return@inAccount null
        if (unlinking) return@inAccount null
        unlinking = true
        return@inAccount try {
            userRepository.unlink(provider).also { if (it == MemberResult.Done || it == MemberResult.LastLoginMethod) _dialog.value = null }
        } finally { unlinking = false }
    }

    fun askLogOut() {
        _dialog.value = MyPageDialog.LogOut
    }

    fun confirmLogOut(): kotlinx.coroutines.Job {
        accountScope.coroutineContext[AccountWork]?.check()
        val job = practiceTimeQueue.logOut {
            authRepository.logOut()
            userRepository.logOut()
        }
        _dialog.value = null
        return job
    }

    fun dismissDialog() {
        _dialog.value = null
    }
}
