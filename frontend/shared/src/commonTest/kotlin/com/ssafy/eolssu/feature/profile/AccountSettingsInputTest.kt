package com.ssafy.eolssu.feature.profile

import com.ssafy.eolssu.core.browser.ExternalBrowser
import com.ssafy.eolssu.core.browser.IncomingLinks
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.core.storage.FakeSecureStore
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.user.MemberResult
import com.ssafy.eolssu.data.user.OAuthAttempts
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.EXPIRES_AT
import com.ssafy.eolssu.data.user.OAuthAttemptsTest.Companion.authorizationUrl
import com.ssafy.eolssu.data.user.OAuthStartResult
import com.ssafy.eolssu.data.user.PasswordCheckResult
import com.ssafy.eolssu.data.user.SampleUserRepository
import com.ssafy.eolssu.data.user.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 계정 설정 입력 안내: 닉네임 10자(MY-02) · 현재와 같은 새 비밀번호(MY-03) · 탈퇴 확인 창(MY-04). */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountSettingsInputTest {
    private val nicknames = mutableListOf<String>()
    private val passwordChanges = mutableListOf<Pair<String, String>>()
    private val withdrawals = mutableListOf<String>()
    private val opened = mutableListOf<String>()
    private val users: UserRepository = object : UserRepository by SampleUserRepository() {
        override suspend fun changeNickname(nickname: String) = MemberResult.Done.also { nicknames += nickname }
        override suspend fun changePassword(current: String, new: String) = PasswordCheckResult.Done.also { passwordChanges += current to new }
        override suspend fun withdraw(password: String) = PasswordCheckResult.Done.also { withdrawals += password }
        override suspend fun startWithdrawalReauth(provider: SocialProvider) = OAuthStartResult.Started(authorizationUrl(), EXPIRES_AT)
    }

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun nicknameEditStopsAtTheAnnouncedTenCharacters() = runTest {
        val viewModel = NicknameEditViewModel(users)
        viewModel.changeNickname("가나다라마바사아자차")
        assertNull(viewModel.uiState.value.error)

        // 안내한 10자에서 더 치면 칸에 들어가지 않는다(S15P31E102-1081).
        viewModel.changeNickname("가나다라마바사아자차카")
        assertEquals("가나다라마바사아자차", viewModel.uiState.value.nickname)
        assertNull(viewModel.uiState.value.error)

        // 한 번에 넘치게 붙여넣으면 앞에서부터 10자만 남는다.
        viewModel.changeNickname("")
        viewModel.changeNickname("가나다라마바사아자차카타파하")
        assertEquals("가나다라마바사아자차", viewModel.uiState.value.nickname)

        assertTrue(viewModel.save())
        assertEquals(listOf("가나다라마바사아자차"), nicknames)
    }

    @Test
    fun legacyNicknameOverTenCharactersCanOnlyBeShortened() = runTest {
        val legacy = "가나다라마바사아자차카타"
        val repository = object : UserRepository by users {
            override val profile = kotlinx.coroutines.flow.MutableStateFlow(users.profile.value.copy(nickname = legacy))
        }
        val viewModel = NicknameEditViewModel(repository)
        assertEquals(legacy, viewModel.uiState.value.nickname)

        // 규칙 이전에 저장된 긴 닉네임에는 더 붙일 수 없고, 고치는 동안 '10자 이내' 오류를 보인다.
        viewModel.changeNickname(legacy + "파")
        assertEquals(legacy, viewModel.uiState.value.nickname)
        assertEquals(ProfileFieldError.NicknameTooLong, viewModel.uiState.value.error)
        assertFalse(viewModel.save())
        assertTrue(nicknames.isEmpty())

        // 지워서 10자 안으로 줄이면 오류가 사라지고 저장된다.
        viewModel.changeNickname("가나다라마바사아자차")
        assertNull(viewModel.uiState.value.error)
        assertTrue(viewModel.save())
        assertEquals(listOf("가나다라마바사아자차"), nicknames)
    }

    @Test
    fun newPasswordSameAsCurrentIsStoppedUnderTheNewPasswordField() = runTest {
        val viewModel = PasswordChangeViewModel(users)
        viewModel.changeCurrent("eolssu1!")
        viewModel.changeNew("eolssu1!")
        viewModel.changeConfirm("eolssu1!")
        assertFalse(viewModel.submit())
        assertEquals(ProfileFieldError.PasswordSameAsCurrent, viewModel.uiState.value.newError)
        assertNull(viewModel.uiState.value.currentError)
        assertTrue(passwordChanges.isEmpty())

        // 현재 비밀번호를 고치면 '현재와 같음'은 다시 판단하도록 지운다.
        viewModel.changeCurrent("eolssu2!")
        assertNull(viewModel.uiState.value.newError)
        assertTrue(viewModel.submit())
        assertEquals(listOf("eolssu2!" to "eolssu1!"), passwordChanges)
    }

    @Test
    fun newPasswordUsesTheSignupRuleOrder() = runTest {
        val viewModel = PasswordChangeViewModel(users)
        viewModel.changeCurrent("eolssu1!")
        viewModel.changeNew("eolssu12")
        viewModel.changeConfirm("eolssu12")
        assertFalse(viewModel.submit())
        assertEquals(ProfileFieldError.PasswordRule, viewModel.uiState.value.newError)

        val tooLong = "a1!" + "b".repeat(126)
        viewModel.changeNew(tooLong)
        viewModel.changeConfirm(tooLong)
        assertFalse(viewModel.submit())
        assertEquals(ProfileFieldError.PasswordTooLong, viewModel.uiState.value.newError)
        assertTrue(passwordChanges.isEmpty())
    }

    @Test
    fun withdrawAsksInTheConfirmDialogBeforeSendingThePassword() = runTest {
        val viewModel = withdrawViewModel()
        // 비밀번호를 비웠으면 창 없이 칸 오류만 보인다.
        viewModel.requestWithdraw()
        assertEquals(ProfileFieldError.PasswordEmpty, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.confirm)

        viewModel.changePassword("eolssu1!")
        viewModel.requestWithdraw()
        assertEquals(WithdrawConfirm.Password, viewModel.uiState.value.confirm)
        assertTrue(withdrawals.isEmpty())

        // '취소'하면 아무것도 보내지 않는다.
        viewModel.dismissConfirm()
        assertNull(viewModel.uiState.value.confirm)
        assertTrue(withdrawals.isEmpty())

        // 창에서 '탈퇴하기'를 누르면 창을 닫고 비밀번호 신청으로 이어 간다(Route가 submit을 부른다).
        viewModel.requestWithdraw()
        assertEquals(WithdrawConfirm.Password, viewModel.confirm())
        assertNull(viewModel.uiState.value.confirm)
        assertTrue(viewModel.submit())
        assertEquals(listOf("eolssu1!"), withdrawals)
    }

    @Test
    fun reauthOpensTheProviderOnlyAfterConfirming() = runTest {
        val viewModel = withdrawViewModel()
        viewModel.requestReauth(SocialProvider.Kakao)
        assertEquals(WithdrawConfirm.Reauth(SocialProvider.Kakao), viewModel.uiState.value.confirm)
        assertTrue(opened.isEmpty())

        assertEquals(WithdrawConfirm.Reauth(SocialProvider.Kakao), viewModel.confirm())
        assertNull(viewModel.uiState.value.confirm)
        assertEquals(listOf(authorizationUrl()), opened)
        assertEquals(WithdrawReauth.Step.InBrowser, viewModel.uiState.value.reauth?.step)

        // 재인증이 진행 중이면 다시 눌러도 창을 띄우지 않는다.
        viewModel.requestReauth(SocialProvider.Kakao)
        viewModel.changePassword("eolssu1!")
        viewModel.requestWithdraw()
        assertNull(viewModel.uiState.value.confirm)
    }

    private fun withdrawViewModel(): WithdrawViewModel {
        val store = FakeKeyValueStore()
        val attempts = OAuthAttempts(FakeSecureStore(), SignedInMember(store)) { 1_000L }
        val timeQueue = PracticeTimeQueue(store, api = null, SignedInMember(store), CoroutineScope(Dispatchers.Unconfined))
        return WithdrawViewModel(users, timeQueue, attempts, IncomingLinks(), browser = ExternalBrowser { opened += it; true })
    }
}
