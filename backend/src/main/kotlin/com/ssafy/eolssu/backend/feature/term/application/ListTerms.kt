package com.ssafy.eolssu.backend.feature.term.application

import com.ssafy.eolssu.backend.feature.term.domain.TermRepository
import java.time.Clock

class ListTerms(private val repository: TermRepository, private val clock: Clock) {
    fun execute() = repository.findCurrent(clock.instant())
}
