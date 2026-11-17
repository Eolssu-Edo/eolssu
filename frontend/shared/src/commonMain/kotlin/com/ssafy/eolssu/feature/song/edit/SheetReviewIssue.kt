package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.data.song.CommonScoreData
import com.ssafy.eolssu.data.song.SheetDocument
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** 변환 결과에서 편집기 정간에 연결할 수 있는 확인 항목. */
data class SheetReviewIssue(val id: String, val cellId: String, val kind: String, val pitch: Pair<String, Int>? = null,
    val eventId: String? = null, val rhythmReview: SheetRhythmReview? = null, val reason: String? = null) {
    /**
     * 인식 신뢰도가 낮다는 참고 항목. 저장을 막지 않고 노란 칸으로만 보이며, 눌러서 표시를 지울 수 있다.
     * 확정 저장하면 남은 항목도 확인한 것으로 보낸다(S15P31E102-1100).
     */
    val advisory: Boolean get() = kind == "LOW_CONFIDENCE"
}

data class SheetRhythmReview(val reason: String, val timeSignature: String? = null, val measure: String? = null)

internal data class SheetReviewSelection(val cell: Int, val sub: Int? = null)

internal fun SheetEditUiState.reviewIssuesAt(cell: Int, sub: Int? = null): List<SheetReviewIssue> {
    val cellId = draft.sourceCellIds?.getOrNull(cell) ?: return emptyList()
    if (sub != null && sub !in draft.cells.getOrNull(cell).orEmpty().indices) return emptyList()
    val cells = runCatching { draft.toCommonScore().columns.flatMap { it.cells } }.getOrNull().orEmpty()
    // 현재 이음이 가리키는 시작 소박을 찾은 뒤, 재배치 전 원본 이벤트에 연결한다.
    val sourceEvents = buildMap {
        cells.forEachIndexed { index, current ->
            val original = draft.originalCell(index) ?: return@forEachIndexed
            current.units.forEachIndexed { unitIndex, unit ->
                if (unit.eventId == "${current.id}-r${unit.row}-u${unit.col}") {
                    val source = original.units.getOrNull(unitIndex)
                    if (source != null && source.eventId == "${original.id}-r${source.row}-u${source.col}") {
                        put(unit.eventId, source.eventId)
                    }
                }
            }
        }
    }
    val units = cells.getOrNull(cell)?.units.orEmpty()
    val eventIds = (if (sub == null) units else listOfNotNull(units.getOrNull(sub)))
        .mapNotNull { sourceEvents[it.eventId] }.toSet()
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
        val kind = detail?.text("reason")?.takeIf { it in setOf("UNREGISTERED_PITCH", "TEMPO_CUE_UNRESOLVED") } ?: issue.text("type").orEmpty()
        val yul = detail?.text("yul")
        val octave = (detail?.get("octave") as? JsonPrimitive)?.intOrNull
        SheetReviewIssue(id, cellId, kind, if (yul != null && octave != null) yul to octave else null, issue.text("eventId"),
            rhythmReview = detail?.rhythmReview(), reason = detail?.text("reason"))
    }.distinctBy(SheetReviewIssue::id)
}

/** Information about the conversion as a whole, outside the per-cell review checklist. */
data class SheetReviewNotice(
    val type: String,
    val page: Int?,
    val reason: String?,
    val semitones: Int?,
    val jangdan: String?,
    val rhythmReview: SheetRhythmReview? = null,
    val measure: String? = null,
    val count: Int? = null,
    val occurrences: Int = 1,
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
            rhythmReview = detail?.rhythmReview(),
            measure = detail?.text("measure")?.takeIf { it.matches(Regex("[1-9][0-9]{0,8}")) },
            count = detail?.positiveInt("count"),
        ) to notice
    }.groupBy { (notice, raw) ->
        // Only a known positive tempo count is additive. Keep all other source
        // detail and bounds in the identity so different locations never merge.
        val additive = notice.type == "LOW_CONFIDENCE" && notice.reason == "TEMPO_PDF_TEXT" && notice.count != null
        additive to if (additive) JsonObject(raw + ("detail" to JsonObject((raw["detail"] as JsonObject) - "count"))) else raw
    }.values.map { group ->
        val first = group.first().first
        first.copy(
            count = if (first.type == "LOW_CONFIDENCE" && first.reason == "TEMPO_PDF_TEXT" && first.count != null)
                group.sumOf { requireNotNull(it.first.count).toLong() }.takeIf { it <= Int.MAX_VALUE }?.toInt()
            else first.count,
            occurrences = group.size,
        )
    }

private fun JsonObject.rhythmReview(): SheetRhythmReview? {
    val reason = text("reason")?.takeIf { it in setOf("TIME_SIGNATURE_REVIEW_REQUIRED", "REPEATED_UNDERFULL_MEASURE", "OVERFULL_MEASURE", "EMPTY_MEASURE_REVIEW_REQUIRED") }
        ?: return null
    return SheetRhythmReview(reason, text("timeSignature")?.takeIf { it.matches(Regex("[0-9]{1,3}/[0-9]{1,3}")) },
        text("measure")?.takeIf { it.length <= 16 })
}

private fun JsonObject.text(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.positiveInt(key: String): Int? =
    (get(key) as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 }
