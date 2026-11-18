package com.ssafy.eolssu.backend.feature.catalog.application

import com.ssafy.eolssu.backend.feature.catalog.domain.CatalogReferenceRepository

class CatalogReferenceQueries(private val repository: CatalogReferenceRepository) {
    private val authoring = AuthoringCapabilities(repository)
    fun authoringCapabilities(id: Long) = authoring.get(id)

    fun instruments() = repository.activeInstruments()

    /** Null means the requested instrument is unavailable; an empty list is valid content. */
    fun pitches(instrumentId: Long?) = if (instrumentId == null) {
        repository.pitches(null, null)
    } else {
        repository.activeInstrument(instrumentId)?.let { instrument ->
            val low = checkNotNull(repository.pitch(instrument.rangeLowPitchId)).semitone
            val high = checkNotNull(repository.pitch(instrument.rangeHighPitchId)).semitone
            check(low <= high) { "Stored instrument pitch range is reversed" }
            repository.pitches(low, high)
        }
    }

    fun fingerings(instrumentId: Long, pitchId: Long?) =
        repository.activeInstrument(instrumentId)?.let { repository.fingerings(instrumentId, pitchId) }

    fun jangdans() = repository.jangdans()
    fun jangdan(id: Long) = repository.jangdan(id)
    fun notationSymbols() = repository.notationSymbols()
}
