package com.ssafy.eolssu.backend.feature.catalog.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.catalog.domain.*
import com.ssafy.eolssu.backend.feature.fingering.infrastructure.persistence.FingeringChartMapper
import com.ssafy.eolssu.backend.feature.instrument.infrastructure.persistence.InstrumentMapper
import com.ssafy.eolssu.backend.feature.instrument.infrastructure.persistence.InstrumentRow
import com.ssafy.eolssu.backend.feature.jangdan.infrastructure.persistence.JangdanMapper
import com.ssafy.eolssu.backend.feature.jangdan.infrastructure.persistence.JangdanRow
import com.ssafy.eolssu.backend.feature.notation.infrastructure.persistence.NotationSymbolMapper
import com.ssafy.eolssu.backend.feature.pitch.infrastructure.persistence.PitchMapper
import com.ssafy.eolssu.backend.feature.pitch.infrastructure.persistence.PitchRow
import tools.jackson.databind.json.JsonMapper

class MyBatisCatalogReferenceRepository(
    private val instruments: InstrumentMapper,
    private val pitches: PitchMapper,
    private val fingerings: FingeringChartMapper,
    private val jangdans: JangdanMapper,
    private val symbols: NotationSymbolMapper,
    private val json: JsonMapper,
) : CatalogReferenceRepository {
    override fun activeInstruments() = instruments.listActive().map(::instrument)
    override fun activeInstrument(id: Long) = instruments.findById(id)?.takeIf { it.isActive }?.let(::instrument)
    override fun pitch(id: Long) = pitches.findById(id)?.let(::pitch)
    override fun pitches(lowSemitone: Int?, highSemitone: Int?) = pitches.listInRange(lowSemitone, highSemitone).map(::pitch)
    override fun fingerings(instrumentId: Long, pitchId: Long?) = fingerings.listForInstrument(instrumentId, pitchId).map {
        CatalogFingering(it.id, it.instrumentId, it.pitchId, it.imageUrl, it.description)
    }
    override fun jangdans() = jangdans.listAll().map(::jangdan)
    override fun jangdan(id: Long) = jangdans.findById(id)?.let(::jangdan)
    override fun notationSymbols() = symbols.listAll().map {
        CatalogNotationSymbol(it.id, it.symbol, it.name, it.description, it.imageUrl)
    }

    private fun instrument(row: InstrumentRow) = CatalogInstrument(
        row.id, row.code, row.name, row.pitchToleranceCent.toInt(), row.rangeLowPitchId, row.rangeHighPitchId,
    )
    private fun pitch(row: PitchRow) = CatalogPitch(row.id, row.yul, row.octave.toInt(), row.hanja, row.hangul, row.semitone.toInt())
    private fun jangdan(row: JangdanRow): CatalogJangdan {
        val pattern = json.readTree(row.pattern)
        check(pattern.isObject && pattern.path("beats").isArray && pattern.path("group").isArray) {
            "Stored jangdan pattern must contain beats and group arrays"
        }
        val beats = pattern.path("beats")
        val group = pattern.path("group")
        check(beats.all { it.isString } && group.all { it.isIntegralNumber && it.canConvertToInt() }) {
            "Stored jangdan pattern has invalid element types"
        }
        check(row.bpmUnit in setOf("DOTTED_QUARTER", "QUARTER", "JEONGGAN", "JANGDAN_BEAT")) {
            "Stored jangdan BPM unit is invalid"
        }
        return CatalogJangdan(
            row.id, row.name, row.defaultBpm.toInt(), row.bpmUnit,
            CatalogJangdanPattern(beats.toList().map { it.asString() }, group.toList().map { it.asInt() }), row.source,
        )
    }
}
