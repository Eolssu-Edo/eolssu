package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.auth.application.KakaoEventUnavailable
import com.ssafy.eolssu.backend.feature.auth.application.KakaoUnlinkEvent
import java.security.MessageDigest
import java.time.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.transaction.support.TransactionSynchronizationManager

/** 모든 호출자는 회원 행을 먼저 잠근다. 회원이 없는 통지는 subject 잠금만 사용한다. */
class KakaoSubjectLocks(private val jdbc:JdbcTemplate) {
    fun lock(subject:String):Instant? {
        check(TransactionSynchronizationManager.isActualTransactionActive())
        val hash=hash(subject)
        jdbc.update("INSERT IGNORE INTO kakao_unlink_subjects(subject_hash,touched_at) VALUES (?,UTC_TIMESTAMP(6))",hash)
        return jdbc.query("SELECT unlinked_at FROM kakao_unlink_subjects WHERE subject_hash=? FOR UPDATE",{row,_->
            row.getObject("unlinked_at",LocalDateTime::class.java)?.toInstant(ZoneOffset.UTC)
        },hash).singleOrNull()
    }
    fun record(subject:String,at:Instant) {
        jdbc.update("UPDATE kakao_unlink_subjects SET unlinked_at=GREATEST(COALESCE(unlinked_at,?),?),touched_at=UTC_TIMESTAMP(6) WHERE subject_hash=?",utc(at),utc(at),hash(subject))
    }
    companion object {
        fun hash(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
        fun utc(value:Instant)=LocalDateTime.ofInstant(value,ZoneOffset.UTC)
    }
}

class KakaoUnlinkStore(private val jdbc:JdbcTemplate,manager:PlatformTransactionManager,private val subjects:KakaoSubjectLocks) {
    private val transaction=TransactionTemplate(manager).apply{timeout=1}
    fun accept(event:KakaoUnlinkEvent) {
        // 조회와 잠금 사이 소유자가 바뀌면 추가 회원 잠금을 잡지 않고 재전송을 요청한다.
        val owner=owner(event.subject,false)
        transaction.executeWithoutResult {
            if(owner!=null) jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE",Long::class.java,owner)
            subjects.lock(event.subject)
            val claimed=jdbc.update("INSERT IGNORE INTO kakao_event_receipts(event_hash,transaction_hash,received_at) VALUES (?,?,UTC_TIMESTAMP(6))",
                KakaoSubjectLocks.hash(event.id),KakaoSubjectLocks.hash(event.transactionId))
            if(claimed==0) return@executeWithoutResult
            if(owner(event.subject,true)!=owner) throw KakaoEventUnavailable()
            subjects.record(event.subject,event.occurredAt)
            // 초 단위 toe의 끝까지를 이전 흐름으로 본다. 이후 시작한 재연결은 보존한다.
            if(owner!=null) jdbc.update("""DELETE FROM user_social_accounts WHERE user_id=? AND provider='KAKAO' AND provider_user_id=?
                AND COALESCE(oauth_started_at,created_at)<?""",owner,event.subject,KakaoSubjectLocks.utc(event.occurredAt.plusSeconds(1)))
        }
    }
    private fun owner(subject:String,lock:Boolean)=jdbc.queryForList(
        "SELECT user_id FROM user_social_accounts WHERE provider='KAKAO' AND provider_user_id=?"+(if(lock)" FOR UPDATE" else ""),Long::class.java,subject).singleOrNull()
    fun cleanup() {
        jdbc.update("DELETE FROM kakao_event_receipts WHERE received_at<DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 48 HOUR) ORDER BY received_at LIMIT 500")
        jdbc.update("DELETE FROM kakao_unlink_subjects WHERE touched_at<DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 48 HOUR) ORDER BY touched_at LIMIT 500")
    }
}
