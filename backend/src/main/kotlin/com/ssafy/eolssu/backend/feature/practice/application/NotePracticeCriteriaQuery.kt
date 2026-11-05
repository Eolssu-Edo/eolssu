package com.ssafy.eolssu.backend.feature.practice.application

import com.ssafy.eolssu.backend.feature.practice.domain.NotePracticeCriteria

class NotePracticeCriteriaQuery(
    private val activeInstrumentCode: (Long) -> String?,
    private val criteriaByInstrumentCode: Map<String, NotePracticeCriteria>,
) {
    /** 없는·비활성 악기와 기준이 등록되지 않은 악기는 null이다. */
    fun find(instrumentId: Long): NotePracticeCriteria? =
        activeInstrumentCode(instrumentId)?.let(criteriaByInstrumentCode::get)
}
