package com.ssafy.eolssu.backend.feature.catalog.application

import com.ssafy.eolssu.backend.feature.catalog.domain.CatalogReferenceRepository
import org.springframework.core.io.ClassPathResource
import tools.jackson.databind.json.JsonMapper

data class AuthoringPhysicalRange(val minMidi: Int?, val maxMidi: Int?, val source: String?, val verificationStatus: String)
data class AuthoringPitchCapability(
    val pitchId: String, val yul: String, val octave: Int, val hanja: String, val hangul: String,
    val semitone: Int, val midi: Int, val physical: String, val physicalVerification: String = "UNKNOWN",
    val fingering: String, val notation: String, val synthesis: String,
    val selectable: Boolean, val reasonCodes: List<String>,
)
data class InstrumentAuthoringCapabilities(
    val instrumentId: String, val instrumentCode: String, val policyVersion: String, val nominalHwangMidi: Int,
    val physicalRange: AuthoringPhysicalRange, val items: List<AuthoringPitchCapability>,
)

/** CAT-06 stays a browsing range. Authoring uses registered guidance, notation and the actual CAT service policy. */
class AuthoringCapabilities(private val repository: CatalogReferenceRepository, private val json: JsonMapper = JsonMapper.builder().build()) {
    private val policy = resource("authoring-policy")
    private val profiles = listOf("DANSO", "SOGEUM", "JEONGAK_DAEGEUM", "SANJO_DAEGEUM")
        .associateWith { resource(it.lowercase().replace('_', '-')) }

    fun instrumentId(code: String): Long? = repository.activeInstruments().singleOrNull { it.code == code }?.id

    fun get(instrumentId: Long): InstrumentAuthoringCapabilities? {
        val instrument = repository.activeInstrument(instrumentId) ?: return null
        val profile = profiles[instrument.code] ?: return null
        val nominal = profile.path("nominal_hwang_midi").intValue()
        val range = profile.path("technical_range").takeIf { it.isArray && it.size() == 2 }
        val low = range?.get(0)?.intValue()
        val high = range?.get(1)?.intValue()
        val fingerings = repository.fingerings(instrumentId, null).map { it.pitchId }.toSet()
        val notationRange = policy.path("notation_octave_range")
        val midiRange = policy.path("midi_range")
        val pitches = repository.pitches(null, null).map { pitch ->
            val midi = nominal + pitch.semitone
            val notation = pitch.octave in notationRange[0].intValue()..notationRange[1].intValue() && pitch.yul.isNotBlank()
            val synthesis = policy.path("synthesis_extension").booleanValue() && midi in midiRange[0].intValue()..midiRange[1].intValue()
            val registered = pitch.id in fingerings
            val reasons = buildList {
                if (!registered) add("FINGERING_UNKNOWN")
                if (!notation) add("NOTATION_UNSUPPORTED")
                if (!synthesis) add("SYNTHESIS_UNSUPPORTED")
            }
            AuthoringPitchCapability(pitch.id.toString(), pitch.yul, pitch.octave, pitch.hanja, pitch.hangul,
                pitch.semitone, midi, when { low == null || high == null -> "UNKNOWN"
                    midi in low..high -> "WITHIN_REFERENCE_RANGE"; else -> "OUTSIDE_REFERENCE_RANGE" },
                fingering = if (registered) "REGISTERED" else "UNKNOWN",
                notation = if (notation) "SUPPORTED" else "UNSUPPORTED",
                synthesis = if (synthesis) "SUPPORTED" else "UNSUPPORTED", selectable = reasons.isEmpty(), reasonCodes = reasons)
        }
        return InstrumentAuthoringCapabilities(instrumentId.toString(), instrument.code, policy.path("policy_version").asString(), nominal,
            AuthoringPhysicalRange(low, high, profile.path("range_source").takeIf { it.isString }?.asString(),
                if (range == null) "UNKNOWN" else "REFERENCE_ONLY"), pitches)
    }

    private fun resource(name: String) = ClassPathResource("catalog/playback-profiles/$name.json").inputStream.use(json::readTree)
}
