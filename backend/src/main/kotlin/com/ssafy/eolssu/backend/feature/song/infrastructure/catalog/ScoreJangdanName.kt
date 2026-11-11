package com.ssafy.eolssu.backend.feature.song.infrastructure.catalog

import tools.jackson.databind.JsonNode

internal fun declaredScoreJangdanName(score: JsonNode): String? =
    score.path("music").path("jangdan").textOrNull()?.takeIf { it.isNotBlank() }

internal fun scoreJangdanName(score: JsonNode, version: Int, requirePlayback: Boolean = false): String? {
    declaredScoreJangdanName(score)?.let { return it }
    val reviewed = reviewedScoreJangdan(score, version) ?: return null
    if (requirePlayback && !reviewed.playback) return null
    return reviewed.name
}

internal fun allowsReviewedInitialRest(score: JsonNode, version: Int): Boolean {
    val reviewed = reviewedScoreJangdan(score, version) ?: return false
    return reviewed.playback && reviewed.allowInitialRest && declaredScoreJangdanName(score) == reviewed.name
}

private fun reviewedScoreJangdan(score: JsonNode, version: Int): ReviewedJangdan? {
    val identity = score.path("score")
    val sourceId = identity.path("sourceId").textOrNull() ?: return null
    val reviewed = reviewedJangdans[sourceId] ?: return null
    if (version != reviewed.version) return null
    val jgbHash = score.path("provenance").path("jgbSha256").textOrNull()
    if (identity.path("instrument").textOrNull() != reviewed.instrument || jgbHash != reviewed.jgbHash) return null
    return reviewed
}

private data class ReviewedJangdan(
    val instrument: String,
    val name: String,
    val jgbHash: String,
    val playback: Boolean = false,
    val version: Int = 1,
    val allowInitialRest: Boolean = false,
)

// Source and rhythm evidence: docs/knowledge/data/jangdan-song-review.md (503, 506),
// docs/knowledge/data/airplane-beginner-score.md (707, 77 BPM practice accompaniment).
private val reviewedJangdans = mapOf(
    // S15P31E102-746: all 118 bars are complete 4/4 bars, including the opening 1.5-beat rest.
    "user-blue-valentine-20261111-sogeum" to ReviewedJangdan("SOGEUM", "4박 기본 반주",
        "971be444d01a313ce5bdfd3f03915ea9b01b314430743d5d4172ab1241e818a4",
        playback = true, version = 3, allowInitialRest = true),
    "mr203-fixed-jeongak-daegeum-006-p1" to ReviewedJangdan("JEONGAK_DAEGEUM", "단모리",
        "b8960383360b471a20214c05b2a37b3f511911aaa38f8db358e5e03a28a77e02", playback = true, version = 2),
    "airplane-beginner-danso-v1" to ReviewedJangdan("DANSO", "단모리",
        "893185e48fc87c300d591f5e4f0774291f21da17828fdaea5f0f865705ae4b18", playback = true),
    "airplane-beginner-sogeum-v1" to ReviewedJangdan("SOGEUM", "단모리",
        "b6dd52ce1fe0a3349ceb7453975f88db84c9bfb7192b9bdb4bd22bf2ab41a972", playback = true),
    "airplane-beginner-sanjo-daegeum-v1" to ReviewedJangdan("SANJO_DAEGEUM", "단모리",
        "b6dd52ce1fe0a3349ceb7453975f88db84c9bfb7192b9bdb4bd22bf2ab41a972", playback = true),
    "mr203-fixed-danso-003-p1" to ReviewedJangdan("DANSO", "자진타령",
        "e0a5b4f02b9378865d2a6fbc59be65d8883276dfdb97e0f6444ce19a179063bc", playback = true),
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
