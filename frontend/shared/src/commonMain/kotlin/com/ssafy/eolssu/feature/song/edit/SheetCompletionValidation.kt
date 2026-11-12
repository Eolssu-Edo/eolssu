package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.data.song.CatalogJangdanSummary
import com.ssafy.eolssu.data.song.CommonScoreData
import com.ssafy.eolssu.data.song.ScoreDataException
import com.ssafy.eolssu.data.song.jangdanCycleTicks
import com.ssafy.eolssu.core.model.ScoreNote
import kotlin.math.abs

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
    val correctionCellIndices: Set<Int> = emptySet(),
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
    val mismatch = if (draft.jangdanId == null) completionCatalogState == SheetCompletionCatalogState.Ready && draft.jangdanName.isNotEmpty() else when {
        jangdan != null -> jangdan.name != draft.jangdanName || !draft.isCompatibleWith(jangdan)
        else -> completionCatalogState == SheetCompletionCatalogState.Ready || reviewNotices.any { it.type == "JANGDAN_MISMATCH" }
    }
    val structureCell = (structureError as? ScoreDataException)?.location?.let { location ->
        cells?.indexOfFirst { it.id == location || location.startsWith("${it.id}-") }?.takeIf { it >= 0 }
    }
    val corrections = buildSet {
        addAll(empty)
        addAll(invalidPitches)
        pending.forEach { issue -> draft.sourceCellIds?.indexOf(issue.cellId)?.takeIf { it >= 0 }?.let(::add) }
        if (structureError != null) {
            val structuralCells = score?.structuralCellIndices().orEmpty()
            addAll(structuralCells)
            // 앞의 빈 칸 때문에 다음 정상 이벤트에서 시간 검증이 실패할 수 있다.
            if (structuralCells.isEmpty() && empty.isEmpty()) structureCell?.let(::add)
        }
    }.sorted().toSet()
    return SheetCompletionValidation(
        emptyCellCount = empty.size,
        pendingReviewCount = pending.size,
        invalidPitchCount = invalidPitches.size,
        hasInvalidStructure = empty.isEmpty() && structureError != null,
        hasJangdanMismatch = mismatch,
        catalogLoading = completionCatalogState == SheetCompletionCatalogState.Loading,
        catalogFailed = completionCatalogState == SheetCompletionCatalogState.Failed,
        firstCellIndex = corrections.firstOrNull(),
        correctionCellIndices = corrections,
        checkableIssueIds = checkable,
    )
}

/** 저장 검증은 첫 오류에서 멈추므로, 편집 가능한 자리의 끊어진 연결·배치를 모두 찾아 표시한다. */
private fun CommonScoreData.structuralCellIndices(): Set<Int> {
    val cells = columns.flatMap { it.cells }
    val cellsById = cells.associateBy { it.id }
    val byId = events.associateBy { it.id }
    val duplicates = events.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
    data class Coverage(val cell: Int, val unit: CommonScoreData.Unit, val start: Double, val end: Double)
    val invalidCells = mutableSetOf<Int>()
    val coverage = cells.flatMapIndexed { index, cell ->
        val rows = cell.units.groupBy { it.row }
        if (rows.keys.toList() != (0 until rows.size).toList() ||
            rows.values.any { row -> row.map { it.col } != row.indices.toList() }) invalidCells += index
        cell.units.map { unit ->
            val rowSize = rows.getValue(unit.row).size
            val start = index.toDouble() * music.ticksPerJeonggan +
                music.ticksPerJeonggan.toDouble() * (unit.row + unit.col.toDouble() / rowSize) / rows.size
            Coverage(index, unit, start, start + music.ticksPerJeonggan.toDouble() / rows.size / rowSize)
        }
    }
    val byEvent = coverage.groupBy { it.unit.eventId }
    val invalidEvents = duplicates.toMutableSet()
    events.forEach { event ->
        val origin = cellsById[event.cellId]
        val spans = byEvent[event.id].orEmpty().sortedBy { it.start }
        val validKind = when (event.kind) {
            "NOTE" -> BaseYuls.any { it.hangul == event.yul } && event.octave in -2..2
            "REST" -> event.yul == null && event.octave == null
            else -> false
        }
        // 길이가 짧거나 긴 음은 그 음의 시작·이음 자리만 표시한다. 다음 정상 음의 시작 시간에 전파하지 않는다.
        val matchesCoverage = spans.isNotEmpty() && abs(spans.first().start - event.startTick) < 1e-7 &&
            abs(spans.last().end - (event.startTick.toDouble() + event.durationTick)) < 1e-7 &&
            spans.zipWithNext().all { (before, after) -> abs(before.end - after.start) < 1e-7 }
        if (!matchesCoverage || event.durationTick <= 0 || !validKind ||
            origin?.units?.none { "${origin.id}-r${it.row}-u${it.col}" == event.id && it.eventId == event.id } != false) {
            invalidEvents += event.id
        }
    }
    events.zipWithNext().filter { (before, after) -> before.startTick > after.startTick }.forEach { (before, after) ->
        invalidEvents += before.id
        invalidEvents += after.id
    }
    cells.forEachIndexed { index, cell ->
        if (events.any { it.id in invalidEvents && it.cellId == cell.id }) invalidCells += index
    }
    coverage.forEach { span ->
        val unit = span.unit
        val event = byId[unit.eventId]
        val invalid = if (event == null || event.id in invalidEvents) true else {
            val startsHere = event.id == "${cells[span.cell].id}-r${unit.row}-u${unit.col}"
            val startsTogether = abs(span.start - event.startTick) < 1e-7
            val invalidSymbol = when (unit.symbol) {
                "", "-" -> startsHere && event.kind != "REST"
                "쉼" -> event.kind != "REST" || !startsTogether
                else -> {
                    val pitch = runCatching { ScoreNote.SourcePitch(requireNotNull(event.yul), requireNotNull(event.octave)) }.getOrNull()
                    event.kind != "NOTE" || pitch?.hangul != unit.symbol || !startsTogether
                }
            }
            invalidSymbol || (startsHere && !startsTogether)
        }
        if (invalid) invalidCells += span.cell
    }
    return invalidCells
}

/** beatCount는 장구 패턴 칸 수다. 각 칸의 음가를 BPM 박으로 환산해 원본 tick과 비교한다. */
internal fun SheetDraft.isCompatibleWith(jangdan: CatalogJangdanSummary): Boolean {
    val music = original?.music ?: return false
    if (jangdan.bpmUnit != music.bpmUnit || jangdan.beatCount <= 0) return false
    val ticks = music.ticksPerJeonggan.toLong()
    if (ticks !in 1..1_000_000_000L) return false
    val cycle = jangdanCycleTicks(jangdan.beatCount, jangdan.patternCellEighths, music.bpmUnit, music.ticksPerBeat.toLong()) ?: return false
    val first = original.events.firstOrNull() ?: return false
    if (first.cellId != "c0-j0" || first.startTick != 0L) return false
    return sizes.isNotEmpty() && sizes.all { it > 0 && it.toLong() * ticks % cycle == 0L }
}
