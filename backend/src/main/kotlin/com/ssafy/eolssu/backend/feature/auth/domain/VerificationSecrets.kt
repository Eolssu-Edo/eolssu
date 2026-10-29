package com.ssafy.eolssu.backend.feature.auth.domain

interface VerificationSecrets {
    fun generateCode(): String
    fun hashCode(email: String, purpose: VerificationPurpose, code: String): String
    fun matchesCode(email: String, purpose: VerificationPurpose, code: String, expectedHash: String): Boolean
    fun generateToken(): String
    fun hashToken(token: String): String
}
