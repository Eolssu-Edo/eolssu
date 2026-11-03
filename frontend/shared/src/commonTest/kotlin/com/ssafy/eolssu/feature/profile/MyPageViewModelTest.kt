package com.ssafy.eolssu.feature.profile

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.PracticeTimeSegment
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.MemberResult
import com.ssafy.eolssu.data.user.PasswordCheckResult
import com.ssafy.eolssu.data.user.SampleAuthRepository
import com.ssafy.eolssu.data.user.SampleUserRepository
import com.ssafy.eolssu.data.user.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 마이페이지(MY-01~04): 연습 악기·간편 로그인 해제·로그아웃과 입력 화면 검사. */
class MyPageViewModelTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun logoutCompletionWaitsForQueueAndRegistersAuthLogoutBeforeNavigation() = runTest {
        val member = SignedInMember(store)
        member.changing { member.set("42") }
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val queue = PracticeTimeQueue(store, object : com.ssafy.eolssu.data.practice.ScoreViewApi {
            override suspend fun post(segment: PracticeTimeSegment): Int { gate.await(); return 201 }
        }, member, backgroundScope)
        queue.close(PracticeTimeSegment("pending", "1", 0, 60_000, 60, 60))
        var authLogoutRegistered = false
        val auth = object : com.ssafy.eolssu.data.user.AuthRepository by SampleAuthRepository({ 0L }) {
            override fun logOut() { authLogoutRegistered = true }
        }
        val completion = MyPageViewModel(users, instruments, queue, auth).confirmLogOut()
        runCurrent()
        assertFalse(completion.isCompleted)
        assertFalse(authLogoutRegistered)
        gate.complete(Unit)
        completion.join()
        assertTrue(authLogoutRegistered)
        assertTrue(queue.pending.isEmpty())
    }
    private val store = FakeKeyValueStore()
    private val instruments = InstrumentRepository(store)
    private val users = SampleUserRepository()
    private val timeQueue = PracticeTimeQueue(store, api = null, SignedInMember(store), CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun instrumentChangeDoesNotClaimSuccessWithoutServerMapping() = runTest {
        val viewModel = MyPageViewModel(users, instruments, timeQueue, SampleAuthRepository(now = { 0L }))

        assertFalse(viewModel.selectInstrument(Instrument.Danso))
        assertFalse(viewModel.selectInstrument(Instrument.DaegeumJeongak))
        assertEquals(Instrument.Danso, viewModel.instrument.value)
        // 앱을 다시 켜면 저장소는 남고 Repository는 새로 만들어진다.
        assertEquals(Instrument.Danso, InstrumentRepository(store).instrument.value)
    }

    @Test
    fun unlinkNeedsConfirmationAndCanRemoveTheLastProvider() = runTest {
        val viewModel = MyPageViewModel(users, instruments, timeQueue, SampleAuthRepository(now = { 0L }))
        assertEquals(setOf(SocialProvider.Kakao), viewModel.profile.value.linkedProviders)

        viewModel.askUnlink(SocialProvider.Kakao)
        assertEquals(MyPageDialog.Unlink(SocialProvider.Kakao), viewModel.dialog.value)
        viewModel.dismissDialog()
        assertEquals(setOf(SocialProvider.Kakao), viewModel.profile.value.linkedProviders)

        viewModel.askUnlink(SocialProvider.Kakao)
        assertEquals(MemberResult.Done, viewModel.confirmUnlink())
        assertTrue(viewModel.profile.value.linkedProviders.isEmpty())
        assertNull(viewModel.dialog.value)
    }

    @Test
    fun logOutAsksFirst() = runTest {
        val viewModel = MyPageViewModel(users, instruments, timeQueue, SampleAuthRepository(now = { 0L }))
        viewModel.askLogOut()
        assertEquals(MyPageDialog.LogOut, viewModel.dialog.value)
        viewModel.confirmLogOut()
        assertNull(viewModel.dialog.value)
    }

    @Test
    fun nicknameStartsWithCurrentAndMustNotBeBlank() = runTest {
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
    fun passwordChangeChecksRuleMismatchAndCurrentPassword() = runTest {
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
    fun withdrawNeedsCorrectPassword() = runTest {
        timeQueue.close(PracticeTimeSegment("a", "12", 0, 60_000, 60, 60))
        val viewModel = WithdrawViewModel(wrongPasswordRepository(), timeQueue)
        assertFalse(viewModel.submit())
        assertEquals(ProfileFieldError.PasswordEmpty, viewModel.uiState.value.error)

        viewModel.changePassword("password")
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.submit())
        assertEquals(ProfileFieldError.PasswordWrong, viewModel.uiState.value.error)
        assertEquals(1, timeQueue.pending.size)

        val ok = WithdrawViewModel(users, timeQueue)
        ok.changePassword("password")
        assertTrue(ok.submit())
        assertTrue(timeQueue.pending.isEmpty())
    }

    /** 현재 비밀번호를 늘 틀린 것으로 보는 저장소(서버 오류 응답 대신). */
    private fun wrongPasswordRepository(): UserRepository = object : UserRepository by SampleUserRepository() {
        override suspend fun changePassword(current: String, new: String) = PasswordCheckResult.WrongPassword

        override suspend fun withdraw(password: String) = PasswordCheckResult.WrongPassword
    }
}
