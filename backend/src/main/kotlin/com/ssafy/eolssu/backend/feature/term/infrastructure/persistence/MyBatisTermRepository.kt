package com.ssafy.eolssu.backend.feature.term.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.term.domain.Term
import com.ssafy.eolssu.backend.feature.term.domain.TermRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class MyBatisTermRepository(private val mapper: TermMapper) : TermRepository {
    override fun findCurrent(asOf: Instant): List<Term> =
        mapper.findCurrent(LocalDateTime.ofInstant(asOf, ZoneOffset.UTC)).map {
            Term(it.id, it.code, it.title, it.isRequired, it.version, it.contentUrl)
        }
}
