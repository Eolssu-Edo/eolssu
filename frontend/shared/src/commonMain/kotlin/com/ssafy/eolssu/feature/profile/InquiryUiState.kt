package com.ssafy.eolssu.feature.profile

/** 마이페이지 문의하기 입력 상태(S15P31E102-1240). 보내기 실패는 내용 칸 아래 [error]로 알린다. */
data class InquiryUiState(
    val title: String = "",
    val content: String = "",
    val titleError: ProfileFieldError? = null,
    val error: ProfileFieldError? = null,
    /** 보내는 중. 보내기 버튼에 진행 표시를 보인다. */
    val submitting: Boolean = false,
)

/** 서버(POST /api/users/me/inquiries)와 같은 글자 수 상한이다. */
object InquiryRule {
    const val TITLE_MAX_LENGTH = 100
    const val CONTENT_MAX_LENGTH = 2000
}
