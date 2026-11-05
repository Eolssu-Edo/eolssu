package com.ssafy.eolssu.backend.feature.song.infrastructure.catalog

import tools.jackson.databind.JsonNode

internal fun declaredScoreJangdanName(score: JsonNode): String? =
    score.path("music").path("jangdan").textOrNull()?.takeIf { it.isNotBlank() }

internal fun scoreJangdanName(score: JsonNode, version: Int, requirePlayback: Boolean = false): String? {
    declaredScoreJangdanName(score)?.let { return it }
    if (version != 1) return null
    val identity = score.path("score")
    val sourceId = identity.path("sourceId").textOrNull() ?: return null
    val reviewed = reviewedJangdans[sourceId] ?: return null
    val jgbHash = score.path("provenance").path("jgbSha256").textOrNull()
    if (identity.path("instrument").textOrNull() != reviewed.instrument || jgbHash != reviewed.jgbHash) return null
    if (requirePlayback && !reviewed.playback) return null
    return reviewed.name
}

private data class ReviewedJangdan(
    val instrument: String,
    val name: String,
    val jgbHash: String,
    val playback: Boolean = false,
)

// Source and rhythm evidence: docs/knowledge/data/jangdan-song-review.md (503, 506).
private val reviewedJangdans = mapOf(
    "mr203-fixed-danso-003-p1" to ReviewedJangdan("DANSO", "자진타령",
        "e0a5b4f02b9378865d2a6fbc59be65d8883276dfdb97e0f6444ce19a179063bc"),
    "mr203-fixed-danso-030-p1" to ReviewedJangdan("DANSO", "자진모리",
        "46620fa702b0d5f52264841ac0d28493bf17920b0cfbdbbe885ca3aa7976ce77", playback = true),
    "mr203-fixed-jeongak-daegeum-004-p1" to ReviewedJangdan("JEONGAK_DAEGEUM", "굿거리",
        "822af585665c14f9347566ee5af72b4c98defa44f7a1aec2323ce5c988c785c6"),
    "mr203-fixed-jeongak-daegeum-005-p1" to ReviewedJangdan("JEONGAK_DAEGEUM", "굿거리",
        "9f11944f4302ff4a76d403ac3b2cbbd7ccfa9ee73887a7c4f1dabf8a44c22feb"),
    "mr203-fixed-sogeum-018-p1" to ReviewedJangdan("SOGEUM", "세마치",
        "ff8006a86cca16135b28100c5aa51c86eddf26fb30334cfea1932d7827da6c3f", playback = true),
    "mr203-fixed-sogeum-020-p1" to ReviewedJangdan("SOGEUM", "취타",
        "c5d75a1a8d6922a48e22e8963385c185f7657c7d0b744630bb33b4335cb22828"),
    "mr203-fixed-sogeum-021-p1" to ReviewedJangdan("SOGEUM", "취타",
        "cb539538253e882c96439a64b0b4f8db860117b94a668859a21e9d6c82785a94"),
    "mr203-fixed-sogeum-022-p1" to ReviewedJangdan("SOGEUM", "취타",
        "53a78ea42d0416ac07b4a8852b40562c28664f365634db554e9b1acb2405c5ee"),
)

private fun JsonNode.textOrNull(): String? = takeIf { it.isString }?.stringValue()
