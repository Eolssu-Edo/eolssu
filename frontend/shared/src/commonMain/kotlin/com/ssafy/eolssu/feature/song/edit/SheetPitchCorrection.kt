package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.Yul
import kotlin.math.abs

/** 누른 위치와 실제 소리가 시작한 위치를 함께 보관한다. 지속 칸 자체를 새 음으로 바꾸지 않는다. */
data class SheetPitchCorrection(
    val cell: Int,
    val sub: Int,
    val originCell: Int,
    val originSub: Int,
    val pitch: Pair<Yul, Int>,
    val eventId: String?,
) {
    val isContinuation: Boolean get() = cell != originCell || sub != originSub
}

internal val SheetEditUiState.supportedInputPitches: Set<Pair<String, Int>>?
    get() = availablePitches?.let { registered -> instrumentPitches?.let(registered::intersect) ?: registered }

internal fun SheetEditUiState.pitchCorrectionsAt(cell: Int, sub: Int? = null): List<SheetPitchCorrection> {
    val supported = supportedInputPitches ?: return emptyList()
    val notes = draft.cells.getOrNull(cell) ?: return emptyList()
    val score = if (draft.original != null) runCatching { draft.toCommonScore() }.getOrNull() else null
    val cells = score?.columns?.flatMap { it.cells }
    return notes.indices.filter { sub == null || it == sub }.mapNotNull { index ->
        val eventId = cells?.getOrNull(cell)?.units?.getOrNull(index)?.eventId
        val event = score?.events?.firstOrNull { it.id == eventId && it.kind == "NOTE" }
        val originCell = event?.let { e -> cells?.indexOfFirst { it.id == e.cellId }?.takeIf { it >= 0 } } ?: cell
        val originSub = event?.let { e -> cells?.getOrNull(originCell)?.let { origin ->
            origin.units.indexOfFirst { "${origin.id}-r${it.row}-u${it.col}" == e.id && it.eventId == e.id }.takeIf { it >= 0 }
        } } ?: index
        val pitch = draft.cells.getOrNull(originCell)?.getOrNull(originSub)?.pitchKey() ?: return@mapNotNull null
        if (pitch.first.hangul to pitch.second in supported) return@mapNotNull null
        SheetPitchCorrection(cell, index, originCell, originSub, pitch, event?.id)
    }.distinctBy { it.originCell to it.originSub }
}

internal fun SheetEditUiState.pitchAlternatives(pitch: Pair<Yul, Int>): List<Pair<Yul, Int>> {
    val height = pitch.first.ordinal + 12 * pitch.second
    val sorted = supportedInputPitches.orEmpty().mapNotNull { (name, octave) ->
        BaseYuls.firstOrNull { it.hangul == name }?.takeIf { octave in -2..2 }?.let { it to octave }
    }.filterNot { it == pitch }.sortedWith(compareBy({ abs(it.first.ordinal + it.second * 12 - height) }, { it.first.ordinal + it.second * 12 }))
    val sameYul = sorted.firstOrNull { it.first == pitch.first }
    return (sorted.take(3) + listOfNotNull(sameYul)).distinct()
}
