package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.model.ScoreNote
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
    get() = when (completionCatalogState) {
        SheetCompletionCatalogState.Loading, SheetCompletionCatalogState.Failed -> emptySet()
        else -> authoringCapabilities?.let { capabilities ->
            if (draft.original?.music?.hwangHz?.let(capabilities::matchesTuning) == false) emptySet() else capabilities.selectablePitches
        } ?: availablePitches?.let { registered -> instrumentPitches?.let(registered::intersect) ?: registered }
    }

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
        val pitch = draft.cells.getOrNull(originCell)?.getOrNull(originSub)?.pitchKey()
            ?: event?.let { e -> BaseYuls.firstOrNull { it.hangul == e.yul }?.let { it to (e.octave ?: 0) } }
            ?: return@mapNotNull null
        val mismatched = event != null && event.yul to event.octave != pitch.first.hangul to pitch.second
        if (pitch.first.hangul to pitch.second in supported && !mismatched) return@mapNotNull null
        SheetPitchCorrection(cell, index, originCell, originSub, pitch, event?.id)
    }.distinctBy { it.originCell to it.originSub }
}

/** A missing glyph is still a real NOTE; replacing it must preserve its original duration and ties. */
internal fun SheetDraft.correctPitch(target: SheetPitchCorrection, pitch: Pair<Yul, Int>): SheetDraft {
    val id = target.eventId ?: return putAt(target.originCell, target.originSub, pitchNote(pitch.first, pitch.second))
    val score = toCommonScore()
    val symbol = ScoreNote.SourcePitch(pitch.first.hangul, pitch.second).hangul
    val corrected = score.copy(events = score.events.map { if (it.id == id) it.copy(yul = pitch.first.hangul, octave = pitch.second) else it },
        columns = score.columns.map { column -> column.copy(cells = column.cells.map { cell ->
            cell.copy(units = cell.units.map { unit ->
                if (unit.eventId == id && "${cell.id}-r${unit.row}-u${unit.col}" == id) unit.copy(symbol = symbol) else unit
            })
        }) })
    return corrected.editingDraft(title, bpm, jangdan).copy(jangdanId = jangdanId,
        editedJangdanName = editedJangdanName, authoring = authoring,
        tempoExpressions = tempoExpressions, tempoReferenceBpm = tempoReferenceBpm,
        adaptationBaseline = sourcePitchNotes().associate { it.eventId to it.midi })
}

internal fun SheetEditUiState.pitchAlternatives(pitch: Pair<Yul, Int>): List<Pair<Yul, Int>> {
    val height = pitch.first.ordinal + 12 * pitch.second
    val sorted = supportedInputPitches.orEmpty().mapNotNull { (name, octave) ->
        BaseYuls.firstOrNull { it.hangul == name }?.takeIf { octave in -2..2 }?.let { it to octave }
    }.filterNot { it == pitch }.sortedWith(compareBy({ abs(it.first.ordinal + it.second * 12 - height) }, { it.first.ordinal + it.second * 12 }))
    val sameYul = sorted.firstOrNull { it.first == pitch.first }
    return (sorted.take(3) + listOfNotNull(sameYul)).distinct()
}
