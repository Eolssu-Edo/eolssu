package com.ssafy.eolssu.backend.feature.song.infrastructure.catalog

import tools.jackson.databind.JsonNode

internal fun declaredScoreJangdanName(score: JsonNode): String? =
    score.path("music").path("jangdan").textOrNull()?.takeIf { it.isNotBlank() }

internal fun scoreJangdanName(score: JsonNode, version: Int): String? {
    declaredScoreJangdanName(score)?.let { return it }
    val identity = score.path("score")
    if (version != 1 || identity.path("instrument").textOrNull() != "DANSO") return null

    // PDF and source matches are recorded in the S15P31E102-503 song review.
    val sourceId = identity.path("sourceId").textOrNull()
    val jgbHash = score.path("provenance").path("jgbSha256").textOrNull()
    return when (sourceId to jgbHash) {
        "mr203-fixed-danso-003-p1" to "e0a5b4f02b9378865d2a6fbc59be65d8883276dfdb97e0f6444ce19a179063bc" -> "자진타령"
        "mr203-fixed-danso-030-p1" to "46620fa702b0d5f52264841ac0d28493bf17920b0cfbdbbe885ca3aa7976ce77" -> "자진모리"
        else -> null
    }
}

private fun JsonNode.textOrNull(): String? = takeIf { it.isString }?.stringValue()
