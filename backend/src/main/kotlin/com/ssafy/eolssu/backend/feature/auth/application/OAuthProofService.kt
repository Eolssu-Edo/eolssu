package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.*
import java.time.Clock
import java.time.Instant

class OAuthStarted(val state: String, val binding: String, val expiresAt: Instant)

class OAuthProofService(private val repository: OAuthProofRepository, private val secrets: VerificationSecrets, private val clock: Clock) {
    fun start(provider: String, purpose: String, userId: Long?, redirectUri: String, socialAccountId: Long? = null): OAuthStarted {
        val state = secrets.generateToken()
        val binding = secrets.generateToken()
        val expiresAt = clock.instant().plusSeconds(600)
        repository.insert(OAuthProof(secrets.hashToken(state), "STATE", provider, purpose,
            secrets.hashToken(binding), userId, redirectUri, null, null, false, expiresAt, flowStartedAt=expiresAt.minusSeconds(600),socialAccountId=socialAccountId))
        return OAuthStarted(state, binding, expiresAt)
    }

    fun consumeState(state: String, binding: String?, provider: String, purpose: String, userId: Long?, redirectUri: String): Boolean {
        if (binding.isNullOrBlank()) return false
        val hash = secrets.hashToken(state)
        val proof = repository.find(hash) ?: return false
        val now = clock.instant()
        if (proof.kind != "STATE" || proof.provider != provider || proof.purpose != purpose || proof.userId != userId ||
            proof.redirectUri != redirectUri || proof.bindingHash != secrets.hashToken(binding) ||
            proof.consumedAt != null || !now.isBefore(proof.expiresAt)) return false
        return repository.consume(hash, now)
    }
}
