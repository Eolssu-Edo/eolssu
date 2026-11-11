package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.data.song.CatalogJangdanSummary
import com.ssafy.eolssu.data.song.ScoreDataException

/** 현재 편집 내용으로 다시 계산한 편집 완료 조건. 초안 저장에는 적용하지 않는다. */
data class SheetCompletionValidation(
    val emptyCellCount: Int = 0,
    val pendingReviewCount: Int = 0,
    val invalidPitchCount: Int = 0,
    val hasInvalidStructure: Boolean = false,
    val hasJangdanMismatch: Boolean = false,
    val catalogLoading: Boolean = false,
    val catalogFailed: Boolean = false,
    val firstCellIndex: Int? = null,
    internal val checkableIssueIds: Set<String> = emptySet(),
) {
    val hasEmptyCells: Boolean get() = emptyCellCount > 0
    val canComplete: Boolean get() = !hasEmptyCells && pendingReviewCount == 0 && invalidPitchCount == 0 &&
        !hasInvalidStructure && !hasJangdanMismatch && !catalogLoading && !catalogFailed
}

internal fun SheetEditUiState.validateCompletion(): SheetCompletionValidation {
    val empty = draft.unfilledCells
    val encoded = if (draft.original != null) runCatching { draft.toCommonScore() } else null
    val score = encoded?.getOrNull()
    val structureError = encoded?.exceptionOrNull() ?: score?.let { runCatching { it.toScore() }.exceptionOrNull() }
    val validStructure = empty.isEmpty() && structureError == null
    val events = score?.events?.associateBy { it.id }.orEmpty()
    val cells = score?.columns?.flatMap { it.cells }
    // 이음 칸도 실제로 가리키는 이벤트의 음높이를 검사한다.
    val pitchesByCell = draft.cells.indices.map { index ->
        cells?.getOrNull(index)?.units?.mapNotNull { unit ->
            events[unit.eventId]?.takeIf { it.kind == "NOTE" }?.let { event ->
                event.yul?.let { yul -> event.octave?.let { yul to it } }
            }
        } ?: draft.cells[index].mapNotNull { it.pitchKey()?.let { (yul, octave) -> yul.hangul to octave } }
    }
    val invalidPitches = pitchesByCell.indices.filter { index -> pitchesByCell[index].any { pitch ->
        availablePitches?.let { pitch !in it } == true || instrumentPitches?.let { pitch !in it } == true
    } }
    val checkable = reviewIssues.filter { issue ->
        val index = draft.sourceCellIds?.indexOf(issue.cellId)?.takeIf { it >= 0 }
        when (issue.kind) {
            "UNREGISTERED_PITCH", "OUT_OF_RANGE" -> {
                val allowed = if (issue.kind == "UNREGISTERED_PITCH") availablePitches else instrumentPitches
                index != null && validStructure && pitchesByCell[index].all { pitch -> allowed?.contains(pitch) == true }
            }
            else -> index != null
        }
    }.mapTo(mutableSetOf(), SheetReviewIssue::id)
    val pending = reviewIssues.filter { it.id !in checkedIssueIds || it.id !in checkable }
    val jangdan = availableJangdans?.firstOrNull { it.jangdanId == draft.jangdanId }
    val mismatch = draft.jangdanId != null && when {
        jangdan != null -> !draft.isCompatibleWith(jangdan)
        else -> reviewNotices.any { it.type == "JANGDAN_MISMATCH" }
    }
    val structureCell = (structureError as? ScoreDataException)?.location?.let { location ->
        cells?.indexOfFirst { it.id == location || location.startsWith("${it.id}-") }?.takeIf { it >= 0 }
    }
    return SheetCompletionValidation(
        emptyCellCount = empty.size,
        pendingReviewCount = pending.size,
        invalidPitchCount = invalidPitches.size,
        hasInvalidStructure = empty.isEmpty() && structureError != null,
        hasJangdanMismatch = mismatch,
        catalogLoading = completionCatalogState == SheetCompletionCatalogState.Loading,
        catalogFailed = completionCatalogState == SheetCompletionCatalogState.Failed,
        firstCellIndex = empty.firstOrNull() ?: invalidPitches.firstOrNull() ?: pending.firstNotNullOfOrNull { issue ->
            draft.sourceCellIds?.indexOf(issue.cellId)?.takeIf { it >= 0 }
        } ?: structureCell,
        checkableIssueIds = checkable,
    )
}

/** 카탈로그의 beatCount는 장구 패턴 길이이며 점4분음표 BPM에서는 3개가 한 박이다. */
internal fun SheetDraft.isCompatibleWith(jangdan: CatalogJangdanSummary): Boolean {
    val music = original?.music ?: return false
    if (jangdan.bpmUnit != music.bpmUnit || jangdan.beatCount <= 0) return false
    val ticks = music.ticksPerJeonggan.toLong()
    val beat = music.ticksPerBeat.toLong()
    if (ticks <= 0 || beat <= 0 || (ticks != beat && ticks * 2 != beat && ticks * 3 != beat)) return false
    val division = when (jangdan.bpmUnit) {
        "QUARTER" -> 1
        "DOTTED_QUARTER" -> 3
        else -> return false
    }
    val expected = jangdan.beatCount.toLong() * beat
    // 마지막의 짧은 각은 끝마침·못갖춘마디일 수 있으므로 새 오류로 만들지 않는다.
    return sizes.withIndex().all { (index, count) ->
        val actual = count.toLong() * ticks * division
        actual == expected || (index == sizes.lastIndex && actual < expected)
    }
}
