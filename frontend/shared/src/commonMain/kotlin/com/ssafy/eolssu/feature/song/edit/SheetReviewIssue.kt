package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.data.song.CommonScoreData
import com.ssafy.eolssu.data.song.SheetDocument
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** 변환 결과에서 편집기 정간에 연결할 수 있는 확인 항목. */
data class SheetReviewIssue(val id: String, val cellId: String, val kind: String, val pitch: Pair<String, Int>? = null, val eventId: String? = null, val reason: String? = null)

internal data class SheetReviewSelection(val cell: Int, val sub: Int? = null)

internal fun SheetEditUiState.reviewIssuesAt(cell: Int, sub: Int? = null): List<SheetReviewIssue> {
    val cellId = draft.sourceCellIds?.getOrNull(cell) ?: return emptyList()
    if (sub != null && sub !in draft.cells.getOrNull(cell).orEmpty().indices) return emptyList()
    val units = runCatching { draft.toCommonScore().columns.flatMap { it.cells }.getOrNull(cell)?.units }.getOrNull().orEmpty()
    val eventIds = (if (sub == null) units else listOfNotNull(units.getOrNull(sub))).map { it.eventId }.toSet()
    return reviewIssues.filter { issue ->
        issue.id !in validatedCheckedIssueIds &&
            if (issue.eventId != null) issue.eventId in eventIds else issue.cellId == cellId
    }
}

internal fun SheetDocument.editorReviewIssues(score: CommonScoreData): List<SheetReviewIssue> {
    val eventCells = score.events.associate { it.id to it.cellId }
    return issues.mapNotNull { issue ->
        val id = issue.text("issueId") ?: return@mapNotNull null
        val cellId = issue.text("cellId") ?: issue.text("eventId")?.let(eventCells::get) ?: return@mapNotNull null
        val detail = issue["detail"] as? JsonObject
        val kind = if (detail?.text("reason") == "UNREGISTERED_PITCH") "UNREGISTERED_PITCH" else issue.text("type").orEmpty()
        val yul = detail?.text("yul")
        val octave = (detail?.get("octave") as? JsonPrimitive)?.intOrNull
        SheetReviewIssue(id, cellId, kind, if (yul != null && octave != null) yul to octave else null, issue.text("eventId"), detail?.text("reason"))
    }.distinctBy(SheetReviewIssue::id)
}

/** Information about the conversion as a whole, outside the per-cell review checklist. */
data class SheetReviewNotice(
    val type: String,
    val page: Int?,
    val reason: String?,
    val semitones: Int?,
    val jangdan: String?,
)

internal fun SheetDocument.editorReviewNotices(): List<SheetReviewNotice> =
    notices.map { notice ->
        val detail = notice["detail"] as? JsonObject
        SheetReviewNotice(
            type = notice.text("type").orEmpty(),
            page = (notice["pageIdx"] as? JsonPrimitive)?.intOrNull
                ?.takeIf { it in 0 until Int.MAX_VALUE }?.plus(1),
            reason = detail?.text("reason"),
            semitones = (detail?.get("semitones") as? JsonPrimitive)?.intOrNull?.takeIf { it in -127..127 },
            jangdan = detail?.text("jangdan")?.takeIf { it.length <= 30 },
        )
    }

private fun JsonObject.text(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
