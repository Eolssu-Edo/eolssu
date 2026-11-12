package com.ssafy.eolssu.core.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals

/** 스낵바 문구는 문장마다 한 줄로 나뉜다(S15P31E102-803). */
class SnackbarLinesTest {
    @Test
    fun oneSentenceStaysOneLine() {
        assertEquals("한 번 더 누르면 앱이 종료돼요.", snackbarLines("한 번 더 누르면 앱이 종료돼요."))
    }

    @Test
    fun sentencesAfterPeriodBreakIntoLines() {
        assertEquals("로그인이 만료됐어요.\n다시 로그인해 주세요.", snackbarLines("로그인이 만료됐어요. 다시 로그인해 주세요."))
    }

    @Test
    fun middleDotBetweenSentencesBreaksLine() {
        assertEquals("악보를 저장했어요\n내 악보에서 연습할 수 있어요", snackbarLines("악보를 저장했어요 · 내 악보에서 연습할 수 있어요"))
    }

    @Test
    fun middleDotInsideWordAndQuotesStay() {
        val message = "음원이 준비됐어요. ‘편집 내용 확인’에서 재생·저장할 수 있어요."
        assertEquals("음원이 준비됐어요.\n‘편집 내용 확인’에서 재생·저장할 수 있어요.", snackbarLines(message))
    }

    @Test
    fun trailingSpaceIsTrimmed() {
        assertEquals("닉네임을 바꿨어요.", snackbarLines("닉네임을 바꿨어요. "))
    }
}
