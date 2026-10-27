package com.ssafy.eolssu.backend.feature.term.infrastructure.persistence

import java.time.LocalDateTime

data class UserTermAgreementRow(
    val id: Long,
    val userId: Long,
    val termId: Long,
    val agreed: Boolean,
    val agreedAt: LocalDateTime,
)
