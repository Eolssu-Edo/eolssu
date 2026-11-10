package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.data.song.CommonScoreData
import com.ssafy.eolssu.data.song.SheetDocument
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** 변환 결과에서 편집기 정간에 연결할 수 있는 확인 항목. */
data class SheetReviewIssue(val id: String, val cellId: String, val kind: String, val pitch: Pair<String, Int>? = null)

internal fun SheetDocument.editorReviewIssues(score: CommonScoreData): List<SheetReviewIssue> {
    val eventCells = score.events.associate { it.id to it.cellId }
    return issues.mapNotNull { issue ->
        val id = issue.text("issueId") ?: return@mapNotNull null
        val cellId = issue.text("cellId") ?: issue.text("eventId")?.let(eventCells::get) ?: return@mapNotNull null
        val detail = issue["detail"] as? JsonObject
        val kind = if (detail?.text("reason") == "UNREGISTERED_PITCH") "UNREGISTERED_PITCH" else issue.text("type").orEmpty()
        val yul = detail?.text("yul")
        val octave = (detail?.get("octave") as? JsonPrimitive)?.intOrNull
        SheetReviewIssue(id, cellId, kind, if (yul != null && octave != null) yul to octave else null)
    }.distinctBy(SheetReviewIssue::id)
}

private fun JsonObject.text(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
