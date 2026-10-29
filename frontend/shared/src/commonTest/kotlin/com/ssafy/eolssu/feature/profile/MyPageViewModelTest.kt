package com.ssafy.eolssu.feature.profile

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.PasswordCheckResult
import com.ssafy.eolssu.data.user.SampleUserRepository
import com.ssafy.eolssu.data.user.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 마이페이지(MY-01~04): 연습 악기·간편 로그인 해제·로그아웃과 입력 화면 검사. */
class MyPageViewModelTest {
    private val store = FakeKeyValueStore()
    private val instruments = InstrumentRepository(store)
    private val users = SampleUserRepository()
    private val timeQueue = PracticeTimeQueue(store, api = null, scope = CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun instrumentChangeIsSharedAndSurvivesRelaunch() {
        val viewModel = MyPageViewModel(users, instruments, timeQueue)

        assertFalse(viewModel.selectInstrument(Instrument.Danso))
        assertTrue(viewModel.selectInstrument(Instrument.Daegeum))
        assertEquals(Instrument.Daegeum, viewModel.instrument.value)
        // 앱을 다시 켜면 저장소는 남고 Repository는 새로 만들어진다.
        assertEquals(Instrument.Daegeum, InstrumentRepository(store).instrument.value)
    }

    @Test
    fun unlinkNeedsConfirmationAndCanRemoveTheLastProvider() {
        val viewModel = MyPageViewModel(users, instruments, timeQueue)
        assertEquals(setOf(SocialProvider.Kakao), viewModel.profile.value.linkedProviders)

        viewModel.askUnlink(SocialProvider.Kakao)
        assertEquals(MyPageDialog.Unlink(SocialProvider.Kakao), viewModel.dialog.value)
        viewModel.dismissDialog()
        assertEquals(setOf(SocialProvider.Kakao), viewModel.profile.value.linkedProviders)

        viewModel.askUnlink(SocialProvider.Kakao)
        assertEquals(SocialProvider.Kakao, viewModel.confirmUnlink())
        assertTrue(viewModel.profile.value.linkedProviders.isEmpty())
        assertNull(viewModel.dialog.value)
    }

    @Test
    fun logOutAsksFirst() {
        val viewModel = MyPageViewModel(users, instruments, timeQueue)
        viewModel.askLogOut()
        assertEquals(MyPageDialog.LogOut, viewModel.dialog.value)
        viewModel.confirmLogOut()
        assertNull(viewModel.dialog.value)
    }

    @Test
    fun nicknameStartsWithCurrentAndMustNotBeBlank() {
        val viewModel = NicknameEditViewModel(users)
        assertEquals("얼쑤연주자", viewModel.uiState.value.nickname)

        viewModel.changeNickname("  ")
        assertFalse(viewModel.save())
        assertEquals(ProfileFieldError.NicknameEmpty, viewModel.uiState.value.error)

        viewModel.changeNickname(" 새 닉네임 ")
        assertNull(viewModel.uiState.value.error)
        assertTrue(viewModel.save())
        assertEquals("새 닉네임", users.profile.value.nickname)
    }

    @Test
    fun passwordChangeChecksRuleMismatchAndCurrentPassword() {
        val viewModel = PasswordChangeViewModel(wrongPasswordRepository())
        viewModel.changeNew("secret")

        assertFalse(viewModel.submit())
        viewModel.uiState.value.let {
            assertEquals(ProfileFieldError.PasswordEmpty, it.currentError)
            assertEquals(ProfileFieldError.PasswordRule, it.newError)
            assertEquals(ProfileFieldError.PasswordMismatch, it.confirmError)
        }

        viewModel.changeCurrent("old-password")
        viewModel.changeNew("eolssu1!")
        viewModel.changeConfirm("eolssu1!")
        assertFalse(viewModel.submit())
        // 비밀번호 변경은 '현재 비밀번호'라고 짚어 말하고, 칸이 하나인 탈퇴는 그냥 '비밀번호'라고 한다.
        assertEquals(ProfileFieldError.CurrentPasswordWrong, viewModel.uiState.value.currentError)

        val ok = PasswordChangeViewModel(users)
        ok.changeCurrent("old-password")
        ok.changeNew("eolssu1!")
        ok.changeConfirm("eolssu1!")
        assertTrue(ok.submit())
    }

    @Test
    fun withdrawNeedsCorrectPassword() {
        val viewModel = WithdrawViewModel(wrongPasswordRepository())
        assertFalse(viewModel.submit())
        assertEquals(ProfileFieldError.PasswordEmpty, viewModel.uiState.value.error)

        viewModel.changePassword("password")
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.submit())
        assertEquals(ProfileFieldError.PasswordWrong, viewModel.uiState.value.error)

        val ok = WithdrawViewModel(users)
        ok.changePassword("password")
        assertTrue(ok.submit())
    }

    /** 현재 비밀번호를 늘 틀린 것으로 보는 저장소(서버 오류 응답 대신). */
    private fun wrongPasswordRepository(): UserRepository = object : UserRepository by SampleUserRepository() {
        override fun changePassword(current: String, new: String) = PasswordCheckResult.WrongPassword

        override fun withdraw(password: String) = PasswordCheckResult.WrongPassword
    }
}
