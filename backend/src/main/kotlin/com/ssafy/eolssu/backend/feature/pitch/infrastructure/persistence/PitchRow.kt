package com.ssafy.eolssu.backend.feature.pitch.infrastructure.persistence

data class PitchRow(
    val id: Long,
    val yul: String,
    val octave: Short,
    val hanja: String,
    val hangul: String,
    val semitone: Short,
)
