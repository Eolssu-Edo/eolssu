package com.ssafy.eolssu.backend.feature.inquiry.domain

import java.time.Instant

/** 마이페이지 문의하기. 로그인한 회원이 쓴 제목·내용과 보낸 회원 정보를 개발자 메일로 보낸다. */
class InquiryMail(
    val userId: Long,
    val nickname: String,
    /** 소셜 가입 회원은 이메일이 없을 수 있다. 있으면 받은 사람이 바로 답장하도록 Reply-To로 둔다. */
    val email: String?,
    val title: String,
    val content: String,
    val sentAt: Instant,
)

fun interface InquiryMailSender {
    fun send(mail: InquiryMail)
}

class InquiryMailUnavailable : RuntimeException("Inquiry mail delivery unavailable")

object InquiryPolicy {
    const val TITLE_MAX_LENGTH = 100
    const val CONTENT_MAX_LENGTH = 2000

    /** 회원마다 [WINDOW_MINUTES]분 동안 [MAX_PER_WINDOW]번까지 보낸다. 실수로 여러 번 누르거나 반복 발송으로 수신함이 넘치지 않게 한다. */
    const val MAX_PER_WINDOW = 5
    const val WINDOW_MINUTES = 60L
}
