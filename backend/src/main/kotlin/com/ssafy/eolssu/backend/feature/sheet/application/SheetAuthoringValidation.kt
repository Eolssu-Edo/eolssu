package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.catalog.application.AuthoringCapabilities
import kotlin.math.log2
import kotlin.math.roundToInt
import tools.jackson.databind.json.JsonMapper

data class UnsupportedAuthoringPitch(val eventId: String, val cellId: String, val yul: String, val octave: Int, val reasonCodes: List<String>)
data class UnsupportedAuthoringPitches(val instrumentCode: String, val issues: List<UnsupportedAuthoringPitch>, val reason: String = "UNSUPPORTED_PITCHES")

/** Only final authoring/audio requests use this policy. Draft storage and published reads stay lossless. */
class SheetAuthoringValidation(private val capabilities: AuthoringCapabilities, private val json: JsonMapper) {
    fun validate(scoreJson: String, instrumentId: Long? = null): UnsupportedAuthoringPitches? {
        val score = json.readTree(scoreJson)
        val code = score.path("score").path("instrument").asString()
        val catalog = (instrumentId ?: capabilities.instrumentId(code))?.let(capabilities::get)
        val pitches = catalog?.items?.associateBy { it.yul to it.octave }.orEmpty()
        val hwang = score.path("music").path("hwangHz").asDouble(Double.NaN)
        val hwangMidi = if (hwang.isFinite() && hwang > 0) (69 + 12 * log2(hwang / 440)).roundToInt() else null
        val issues = score.path("events").filter { it.path("kind").asString() == "NOTE" }.mapNotNull { note ->
            val yul = note.path("yul").asString()
            val octave = note.path("octave").intValue()
            val pitch = pitches[yul to octave]
            val reasons = buildList {
                if (catalog == null) add("INSTRUMENT_UNSUPPORTED")
                else {
                    addAll(pitch?.reasonCodes ?: listOf("PITCH_UNREGISTERED"))
                    if (hwangMidi != catalog.nominalHwangMidi) add("REFERENCE_TUNING_MISMATCH")
                }
            }
            if (reasons.isEmpty()) null else UnsupportedAuthoringPitch(note.path("id").asString(), note.path("cellId").asString(), yul, octave, reasons)
        }
        // A rest-only score still cannot carry an unknown instrument or malformed tuning.
        if (issues.isEmpty() && catalog != null && hwangMidi == catalog.nominalHwangMidi) return null
        return UnsupportedAuthoringPitches(catalog?.instrumentCode ?: code, issues.ifEmpty {
            listOf(UnsupportedAuthoringPitch("", "", "", 0, listOf(if (catalog == null) "INSTRUMENT_UNSUPPORTED" else "REFERENCE_TUNING_MISMATCH")))
        })
    }
}
