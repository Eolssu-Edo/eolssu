package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.auth.domain.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.jdbc.core.JdbcTemplate

class JdbcOAuthProofRepository(private val jdbc: JdbcTemplate) : OAuthProofRepository {
    override fun insert(proof: OAuthProof) {
        jdbc.update("""INSERT INTO oauth_proofs(token_hash,kind,provider,purpose,binding_hash,user_id,redirect_uri,
            provider_subject,email,email_verified,expires_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
            proof.hash,proof.kind,proof.provider,proof.purpose,proof.bindingHash,proof.userId,proof.redirectUri,
            proof.subject,proof.email,proof.emailVerified,proof.expiresAt.utc())
    }

    override fun find(hash: String): OAuthProof? = jdbc.query("SELECT * FROM oauth_proofs WHERE token_hash=?", { row, _ ->
        OAuthProof(row.getString("token_hash"),row.getString("kind"),row.getString("provider"),row.getString("purpose"),
            row.getString("binding_hash"),row.getObject("user_id",java.lang.Long::class.java)?.toLong(),row.getString("redirect_uri"),
            row.getString("provider_subject"),row.getString("email"),row.getBoolean("email_verified"),
            row.getObject("expires_at",LocalDateTime::class.java).toInstant(ZoneOffset.UTC),
            row.getObject("consumed_at",LocalDateTime::class.java)?.toInstant(ZoneOffset.UTC))
    },hash).singleOrNull()

    override fun consume(hash: String, now: Instant) = jdbc.update(
        "UPDATE oauth_proofs SET consumed_at=? WHERE token_hash=? AND consumed_at IS NULL AND expires_at>?",
        now.utc(),hash,now.utc()) == 1

    override fun deleteExpired(now: Instant) = jdbc.update("DELETE FROM oauth_proofs WHERE expires_at<=? ORDER BY expires_at LIMIT 500",now.utc())

    private fun Instant.utc() = LocalDateTime.ofInstant(this,ZoneOffset.UTC)
}
