package com.ssafy.eolssu.feature.song.edit

/** The beat symbol always comes from the written score's BPM unit. */
internal val SheetDraft.tempoUnitLabel: String
    get() = when (original?.music?.bpmUnit) {
        "DOTTED_QUARTER" -> "♩."
        "JEONGGAN" -> "정간"
        "JANGDAN_BEAT" -> "장단박"
        else -> "♩"
    }
