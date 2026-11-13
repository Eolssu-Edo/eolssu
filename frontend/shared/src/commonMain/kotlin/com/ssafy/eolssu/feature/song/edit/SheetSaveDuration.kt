package com.ssafy.eolssu.feature.song.edit

/** 미입력 정간도 포함한 저장 악보의 예상 길이. BPM 단위는 원본 tick 비율에 이미 반영돼 있다. */
internal fun SheetDraft.estimatedDurationSeconds(bpm: Int): Double? {
    if (bpm !in SheetBpmRange) return null
    val changesTiming = setOf("본래속도로", "점점느리게", "점점속하게", "조금느리게", "조금속하게", "반복표", "도돌이표", "덧길이표", "반길이표")
    if (sigimsae.values.flatten().any { it.name in changesTiming }) return null
    val music = original?.music
    val cellSeconds = if (music != null) {
        if (music.ticksPerJeonggan <= 0 || music.ticksPerBeat <= 0) return null
        60.0 / bpm * music.ticksPerJeonggan / music.ticksPerBeat
    } else {
        jangdan?.cellMillis(bpm)?.div(1_000) ?: return null
    }
    return (cells.size * cellSeconds).takeIf { it.isFinite() && it > 0 }
}
