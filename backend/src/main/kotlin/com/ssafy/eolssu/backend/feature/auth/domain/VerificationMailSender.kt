package com.ssafy.eolssu.backend.feature.auth.domain

interface VerificationMailSender {
    fun send(email: String, purpose: VerificationPurpose, code: String)
}

class VerificationMailUnavailable : RuntimeException("Verification mail delivery unavailable")
class VerificationStorageBusy : RuntimeException("Verification storage busy")
