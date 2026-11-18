package com.ssafy.eolssu.backend.feature.catalog.domain

interface CatalogReferenceRepository {
    fun activeInstruments(): List<CatalogInstrument>
    fun activeInstrument(id: Long): CatalogInstrument?
    fun pitch(id: Long): CatalogPitch?
    fun pitches(lowSemitone: Int?, highSemitone: Int?): List<CatalogPitch>
    fun fingerings(instrumentId: Long, pitchId: Long?): List<CatalogFingering>
    fun jangdans(): List<CatalogJangdan>
    fun jangdan(id: Long): CatalogJangdan?
    fun notationSymbols(): List<CatalogNotationSymbol>
}

data class CatalogInstrument(
    val id: Long, val code: String, val name: String, val pitchToleranceCent: Int,
    val rangeLowPitchId: Long, val rangeHighPitchId: Long,
)

data class CatalogPitch(
    val id: Long, val yul: String, val octave: Int, val hanja: String, val hangul: String, val semitone: Int,
)

data class CatalogFingering(
    val id: Long, val instrumentId: Long, val pitchId: Long, val imageUrl: String?, val description: String?,
    val pattern: FingeringPattern? = null,
)

data class CatalogJangdan(
    val id: Long, val name: String, val defaultBpm: Int, val bpmUnit: String,
    val pattern: CatalogJangdanPattern, val source: String?,
    val patternCellEighths: Int? = null,
)

data class CatalogJangdanPattern(val beats: List<String>, val group: List<Int>) {
    init {
        require(group.all { it > 0 } && group.sumOf { it.toLong() } == beats.size.toLong()) {
            "Stored jangdan pattern has inconsistent beat groups"
        }
    }
}

data class CatalogNotationSymbol(
    val id: Long, val symbol: String, val name: String, val description: String, val imageUrl: String?,
)
