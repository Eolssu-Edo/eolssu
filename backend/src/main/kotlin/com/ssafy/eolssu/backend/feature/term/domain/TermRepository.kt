package com.ssafy.eolssu.backend.feature.term.domain

import java.time.Instant

interface TermRepository {
    /** code별 시행된 최신 약관. 시행 시각이 같으면 큰 ID, 표시 순서는 seq·ID 오름차순. */
    fun findCurrent(asOf: Instant): List<Term>
}
