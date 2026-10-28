package com.ssafy.eolssu.backend.feature.auth.infrastructure.mail

import com.ssafy.eolssu.backend.feature.auth.domain.VerificationMailSender
import com.ssafy.eolssu.backend.feature.auth.domain.VerificationPurpose

/** 로컬에서 명시적으로 선택하는 대역. HTTP 조회·로그 출력·파일 저장은 제공하지 않는다. */
class InMemoryVerificationMailSender : VerificationMailSender {
    private val mailbox = ArrayDeque<Message>()

    override fun send(email: String, purpose: VerificationPurpose, code: String) = synchronized(mailbox) {
        if (mailbox.size == 100) mailbox.removeFirst()
        mailbox.addLast(Message(email, purpose, code))
    }

    fun messages(): List<Message> = synchronized(mailbox) { mailbox.toList() }

    class Message(val email: String, val purpose: VerificationPurpose, val code: String)
}
