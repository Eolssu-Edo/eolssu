package com.ssafy.eolssu.backend.feature.song.api

internal fun requestedScoreVersion(value: String): Int? =
    value.takeIf { it.matches(Regex("[1-9][0-9]*")) }?.toIntOrNull()
