package com.ssafy.eolssu.backend.feature.inquiry.application

import com.ssafy.eolssu.backend.feature.inquiry.domain.InquiryMail
import com.ssafy.eolssu.backend.feature.inquiry.domain.InquiryMailSender
import com.ssafy.eolssu.backend.feature.inquiry.domain.InquiryMailUnavailable
import com.ssafy.eolssu.backend.feature.inquiry.domain.InquiryPolicy
import com.ssafy.eolssu.backend.feature.user.domain.MyInfoRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * 문의를 개발자 메일로 보낸다. 문의 내용은 DB에 저장하지 않고 메일로만 남긴다.
 * 발송 횟수 제한은 서버 한 대의 메모리 안에서만 센다. 배포로 다시 뜨거나 블루·그린 두 대가 함께 받으면 각각 센다.
 */
class InquiryService(
    private val members: MyInfoRepository,
    private val sender: InquiryMailSender,
    private val clock: Clock,
) {
    private val sent = HashMap<Long, ArrayDeque<Instant>>()

    fun send(userId: Long, title: String, content: String): InquiryResult {
        val member = members.findActive(userId) ?: return InquiryResult.MemberNotFound
        val now = clock.instant()
        if (!reserve(userId, now)) return InquiryResult.RateLimited
        return try {
            sender.send(InquiryMail(userId, member.nickname, member.email, title.trim(), content.trim(), now))
            InquiryResult.Sent(now)
        } catch (_: InquiryMailUnavailable) {
            // 보내지 못한 시도는 횟수에서 빼 다시 보낼 수 있게 한다.
            release(userId, now)
            InquiryResult.Unavailable
        }
    }

    private fun reserve(userId: Long, now: Instant): Boolean = synchronized(sent) {
        val since = now.minus(Duration.ofMinutes(InquiryPolicy.WINDOW_MINUTES))
        val times = sent.getOrPut(userId) { ArrayDeque() }
        while (times.isNotEmpty() && !times.first().isAfter(since)) times.removeFirst()
        if (times.size >= InquiryPolicy.MAX_PER_WINDOW) return false
        times.addLast(now)
        true
    }

    private fun release(userId: Long, at: Instant) = synchronized(sent) {
        sent[userId]?.let { times ->
            times.remove(at)
            if (times.isEmpty()) sent.remove(userId)
        }
    }
}

sealed interface InquiryResult {
    class Sent(val sentAt: Instant) : InquiryResult
    data object MemberNotFound : InquiryResult
    data object RateLimited : InquiryResult
    data object Unavailable : InquiryResult
}
