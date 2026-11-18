package com.ssafy.eolssu.backend.feature.sheet.application

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import tools.jackson.databind.JsonNode

data class SheetAudioView(
    val sheetId: String,val status: String,val scoreVersion: String,val currentScoreVersion: String,val outdated: Boolean,
    val instrumentId: String,val withJangdan: Boolean,val bpm: Int,val failureCode: String?,val pollAfterMs: Int?,
    val audio: SheetAudioFileView?,val midi: SheetAudioFileView?,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SheetAudioFileView(
    val url: String,val urlExpiresAt: Instant,val contentType: String,val byteSize: Long,val fileName: String,
    val sha256: String?=null,val durationMs: Long?=null,
    val performance: SheetAudioPerformanceView? = null,
)

data class SheetAudioPerformanceView(val sourceCommonSha256: String, val timeline: JsonNode)
