package com.ssafy.eolssu.backend.feature.catalog.domain

enum class FingeringBlow { LOW, MID, HIGH }
enum class FingeringHole { CLOSED, OPEN, HALF, UNKNOWN }

/** Reference data from the builtin guide; it is not a measured tuning or scoring approval. */
data class FingeringPattern(
    val blow: FingeringBlow,
    val hanja: String,
    val name: String,
    val reading: String,
    val holes: List<FingeringHole>,
    val basic: Boolean,
    val source: String,
    val instrumentNote: String,
    val nominalHwangPitchClass: Int,
    val verificationStatus: String,
) {
    init {
        require(holes.size in 5..6)
        require(listOf(hanja, name, reading, source, instrumentNote).all { it.isNotBlank() })
        require(nominalHwangPitchClass in 0..11)
        require(verificationStatus == "BUILTIN_REFERENCE")
    }
}
