package com.ssafy.eolssu.feature.profile

import com.ssafy.eolssu.data.user.MemberResult
import com.ssafy.eolssu.data.user.SampleUserRepository
import com.ssafy.eolssu.data.user.UserRepository
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

/** 마이페이지 문의하기(S15P31E102-1240): 빈 칸 안내 · 앞뒤 공백 정리 · 서버 실패 안내. */
@OptIn(ExperimentalCoroutinesApi::class)
class InquiryViewModelTest {
    private val sent = mutableListOf<Pair<String, String>>()
    private var result = MemberResult.Done
    private val users: UserRepository = object : UserRepository by SampleUserRepository() {
        override suspend fun sendInquiry(title: String, content: String) = result.also { sent += title to content }
    }

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun blankTitleOrContentIsNotSent() = runTest {
        val viewModel = InquiryViewModel(users)
        viewModel.changeContent("  \n ")

        assertFalse(viewModel.send())
        assertEquals(ProfileFieldError.InquiryTitleEmpty, viewModel.uiState.value.titleError)
        assertEquals(ProfileFieldError.InquiryContentEmpty, viewModel.uiState.value.error)
        assertTrue(sent.isEmpty())

        // 다시 입력하면 그 칸의 안내를 지운다.
        viewModel.changeTitle("제목")
        assertNull(viewModel.uiState.value.titleError)
    }

    @Test
    fun sendsTrimmedTextAndKeepsTheTitleOnOneLine() = runTest {
        val viewModel = InquiryViewModel(users)
        viewModel.changeTitle(" 녹음이\n저장되지 않아요 ")
        assertEquals(" 녹음이 저장되지 않아요 ", viewModel.uiState.value.title)
        viewModel.changeContent("\n저장을 눌렀는데\n기록에 없어요.  ")

        assertTrue(viewModel.send())
        assertEquals(listOf("녹음이 저장되지 않아요" to "저장을 눌렀는데\n기록에 없어요."), sent)
        assertFalse(viewModel.uiState.value.submitting)
    }

    @Test
    fun serverFailuresStayOnTheScreenWithAMessage() = runTest {
        val viewModel = InquiryViewModel(users)
        viewModel.changeTitle("제목")
        viewModel.changeContent("내용")
        for ((server, shown) in listOf(
            MemberResult.RateLimited to ProfileFieldError.RateLimited,
            MemberResult.Unauthenticated to ProfileFieldError.Unauthenticated,
            MemberResult.Unavailable to ProfileFieldError.InquiryUnavailable,
        )) {
            result = server
            assertFalse(viewModel.send())
            assertEquals(shown, viewModel.uiState.value.error)
            assertEquals("내용", viewModel.uiState.value.content)
        }
    }
}
