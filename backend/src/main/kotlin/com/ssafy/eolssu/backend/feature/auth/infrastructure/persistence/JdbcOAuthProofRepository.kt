package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.auth.domain.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.JdbcTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class JdbcOAuthProofRepository(
    private val jdbc: JdbcTemplate,
    manager: PlatformTransactionManager = JdbcTransactionManager(checkNotNull(jdbc.dataSource)),
) : OAuthProofRepository {
    private val transaction=TransactionTemplate(manager)
    override fun insert(proof: OAuthProof) {
        jdbc.update("""INSERT INTO oauth_proofs(token_hash,kind,provider,purpose,binding_hash,user_id,redirect_uri,
            provider_subject,email,email_verified,expires_at,flow_started_at,social_account_id,provider_token) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            proof.hash,proof.kind,proof.provider,proof.purpose,proof.bindingHash,proof.userId,proof.redirectUri,
            proof.subject,proof.email,proof.emailVerified,proof.expiresAt.utc(),proof.flowStartedAt?.utc(),proof.socialAccountId,proof.providerToken)
    }

    override fun find(hash: String): OAuthProof? = jdbc.query("SELECT * FROM oauth_proofs WHERE token_hash=?", { row, _ ->
        OAuthProof(row.getString("token_hash"),row.getString("kind"),row.getString("provider"),row.getString("purpose"),
            row.getString("binding_hash"),row.getObject("user_id",java.lang.Long::class.java)?.toLong(),row.getString("redirect_uri"),
            row.getString("provider_subject"),row.getString("email"),row.getBoolean("email_verified"),
            row.getObject("expires_at",LocalDateTime::class.java).toInstant(ZoneOffset.UTC),
            row.getObject("consumed_at",LocalDateTime::class.java)?.toInstant(ZoneOffset.UTC),
            row.getObject("flow_started_at",LocalDateTime::class.java)?.toInstant(ZoneOffset.UTC),
            row.getObject("social_account_id",java.lang.Long::class.java)?.toLong(),
            row.getString("provider_token"))
    },hash).singleOrNull()

    override fun consume(hash: String, now: Instant):Boolean = transaction.execute {
        // MySQL UTC_TIMESTAMP는 문장 시작 시각이다. 먼저 잠금을 얻고 새 UPDATE 문장에서
        // 시각을 읽어야 잠금 대기 중 만료를 거절할 수 있다. 기존 회원 트랜잭션이 있으면 참여한다.
        val locked=jdbc.queryForList("SELECT token_hash FROM oauth_proofs WHERE token_hash=? FOR UPDATE",String::class.java,hash)
        if(locked.isEmpty()) return@execute false
        jdbc.update(
            "UPDATE oauth_proofs SET consumed_at=GREATEST(?,UTC_TIMESTAMP(6)) WHERE token_hash=? AND consumed_at IS NULL AND expires_at>GREATEST(?,UTC_TIMESTAMP(6))",
            now.utc(),hash,now.utc()) == 1
    } == true

    override fun deleteExpired(now: Instant) = jdbc.update("DELETE FROM oauth_proofs WHERE expires_at<=? ORDER BY expires_at LIMIT 500",now.utc())

    private fun Instant.utc() = LocalDateTime.ofInstant(this,ZoneOffset.UTC)
}
