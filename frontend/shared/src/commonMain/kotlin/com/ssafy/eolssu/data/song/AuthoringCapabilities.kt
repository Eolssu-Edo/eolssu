package com.ssafy.eolssu.data.song

import kotlinx.serialization.Serializable
import kotlin.math.log2
import kotlin.math.roundToInt

@Serializable data class CatalogAuthoringCapabilities(
    val instrumentId: String, val instrumentCode: String, val policyVersion: String, val nominalHwangMidi: Int,
    val physicalRange: AuthoringPhysicalRange, val items: List<AuthoringPitchCapability>,
) {
    val selectablePitches: Set<Pair<String, Int>> get() = items.filter { it.selectable }.map { it.yul to it.octave }.toSet()
    fun matchesTuning(hwangHz: Double): Boolean = hwangHz.isFinite() && hwangHz > 0 &&
        (69 + 12 * log2(hwangHz / 440)).roundToInt() == nominalHwangMidi
    fun issues(score: CommonScoreData): List<AuthoringPitchIssue> {
        val byPitch = items.associateBy { it.yul to it.octave }
        return score.events.filter { it.kind == "NOTE" }.mapNotNull { note ->
            val reasons = buildList {
                addAll(byPitch[note.yul to note.octave]?.reasonCodes ?: listOf("PITCH_UNREGISTERED"))
                if (!matchesTuning(score.music.hwangHz)) add("REFERENCE_TUNING_MISMATCH")
            }
            if (reasons.isEmpty()) null else AuthoringPitchIssue(note.id, note.cellId, note.yul.orEmpty(), note.octave ?: 0, reasons)
        }
    }
}
@Serializable data class AuthoringPhysicalRange(val minMidi: Int? = null, val maxMidi: Int? = null,
    val source: String? = null, val verificationStatus: String)
@Serializable data class AuthoringPitchCapability(val pitchId: String, val yul: String, val octave: Int,
    val hanja: String, val hangul: String, val semitone: Int, val midi: Int, val physical: String,
    val physicalVerification: String = "UNKNOWN", val fingering: String, val notation: String, val synthesis: String,
    val selectable: Boolean, val reasonCodes: List<String>)
@Serializable data class AuthoringPitchIssue(val eventId: String, val cellId: String, val yul: String,
    val octave: Int, val reasonCodes: List<String>)
@Serializable data class AuthoringPitchRejection(val instrumentCode: String, val issues: List<AuthoringPitchIssue>, val reason: String)
