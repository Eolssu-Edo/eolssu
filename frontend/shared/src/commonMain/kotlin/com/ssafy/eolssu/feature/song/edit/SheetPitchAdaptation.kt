package com.ssafy.eolssu.feature.song.edit

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlinx.serialization.json.*
import com.ssafy.eolssu.data.song.CatalogInstrument
import com.ssafy.eolssu.data.song.CatalogAuthoringCapabilities

data class SheetAdaptationUi(
    val instruments: List<CatalogInstrument> = emptyList(), val selectedId: String? = null,
    val capabilities: CatalogAuthoringCapabilities? = null, val plans: List<PitchAdaptationPlan> = emptyList(),
    val selectedTranspose: Int? = null, val loading: Boolean = true, val failed: Boolean = false,
    val baseline: SheetDraft,
) { val selected: PitchAdaptationPlan? get() = plans.firstOrNull { it.transpose == selectedTranspose } }

/** Immutable pitches captured before any adaptation. MIDI is sounding pitch, not a yul index. */
data class SourcePitchNote(val eventId: String, val cellId: String, val midi: Int)
data class AdaptationPitch(val midi: Int, val yul: String, val octave: Int)
data class AdaptedPitch(val source: SourcePitchNote, val target: AdaptationPitch?, val octaveShift: Int?)
data class PitchAdaptationPlan(val transpose: Int, val notes: List<AdaptedPitch>) {
    val unresolved: Int get() = notes.count { it.target == null }
    val octaveChanges: Int get() = notes.count { it.octaveShift != null && it.octaveShift != 0 }
    val octaveDistance: Int get() = notes.sumOf { abs(it.octaveShift ?: 0) }
}

/** One representative of every pitch class; larger shifts are equivalent octave moves and rank worse. */
fun planPitchAdaptation(source: List<SourcePitchNote>, supported: List<AdaptationPitch>): List<PitchAdaptationPlan> =
    (-6..6).map { transpose ->
        PitchAdaptationPlan(transpose, source.map { note ->
            val transposed = note.midi + transpose
            val target = supported.filter { (it.midi - transposed) % 12 == 0 }
                .minWithOrNull(compareBy<AdaptationPitch> { abs(it.midi - transposed) }.thenBy { it.midi })
            AdaptedPitch(note, target, target?.let { (it.midi - transposed) / 12 })
        })
    }.sortedWith(compareBy<PitchAdaptationPlan> { it.unresolved }.thenBy { abs(it.transpose) }
        .thenBy { it.octaveChanges }.thenBy { it.octaveDistance }.thenBy { it.transpose })

internal fun SheetDraft.originalPitchLayoutMatches(): Boolean {
    val source = authoring?.get("source")?.jsonObject
    val saved = source?.get("notes")?.jsonArray.orEmpty()
        .associate { it.jsonObject.getValue("eventId").jsonPrimitive.content to it.jsonObject }
    // IDs encode positions. Inserting/deleting cells can reuse an old ID for a different note.
    val score = toCommonScore()
    val cell = score.music.ticksPerJeonggan.toLong()
    val sourceCell = (source?.get("score") as? JsonObject)?.get("music")?.jsonObject
        ?.get("ticksPerJeonggan")?.jsonPrimitive?.intOrNull?.toLong() ?: cell
    if (sourceCell <= 0 || cell <= 0) return false
    val notes = score.events.filter { it.kind == "NOTE" }
    return saved.isNotEmpty() && notes.size == saved.size && notes.all { event -> saved[event.id]?.let { old ->
        old["cellId"]?.jsonPrimitive?.content == event.cellId &&
            old["startTick"]?.jsonPrimitive?.longOrNull?.let { sameGridTime(it, sourceCell, event.startTick, cell) } == true &&
            old["durationTick"]?.jsonPrimitive?.longOrNull?.let { sameGridTime(it, sourceCell, event.durationTick, cell) } == true
    } == true }
}

/** tick 배율이 달라도 원본의 같은 음을 찾는다. 긴 곡의 곱셈 넘침을 피하려고 정수부와 나머지를 비교한다. */
private fun sameGridTime(before: Long, beforeCell: Long, after: Long, afterCell: Long): Boolean =
    before / beforeCell == after / afterCell && before % beforeCell * afterCell == after % afterCell * beforeCell

internal fun SheetDraft.sourcePitchNotes(): List<SourcePitchNote> {
    val saved = authoring?.get("source")?.jsonObject?.get("notes")?.jsonArray.orEmpty()
        .associate { it.jsonObject.getValue("eventId").jsonPrimitive.content to it.jsonObject }
    val score = toCommonScore()
    val sameLayout = originalPitchLayoutMatches()
    val hwang = (69 + 12 * log2(score.music.hwangHz / 440.0)).roundToInt()
    return score.events.filter { it.kind == "NOTE" }.map {
        val source = saved[it.id]?.takeIf { sameLayout }
        SourcePitchNote(it.id, it.cellId, source?.get("midi")?.jsonPrimitive?.int
            ?: (hwang + "황대태협고중유임이남무응".indexOf(it.yul.orEmpty()) + 12 * (it.octave ?: 0)))
    }
}

/** Pitch-only adaptation preserves timing, IDs, rests, ties and immutable source notes. */
internal fun SheetDraft.applyAdaptation(plan: PitchAdaptationPlan, capability: CatalogAuthoringCapabilities): SheetDraft {
    val current = toCommonScore()
    val targets = plan.notes.associate { item ->
        val midi = item.source.midi + plan.transpose
        val relative = midi - capability.nominalHwangMidi
        item.source.eventId to (item.target ?: AdaptationPitch(midi,
            "황대태협고중유임이남무응"[relative.mod(12)].toString(), kotlin.math.floor(relative / 12.0).toInt()))
    }
    val events = current.events.map { event -> targets[event.id]?.let { event.copy(yul = it.yul, octave = it.octave) } ?: event }
    val symbols = capability.items.filter { it.notation == "SUPPORTED" }.associate { (it.yul to it.octave) to it.hangul }
    val columns = current.columns.map { column -> column.copy(cells = column.cells.map { cell ->
        cell.copy(units = cell.units.map { unit ->
            val pitch = targets[unit.eventId]
            if (pitch != null && unit.eventId == "${cell.id}-r${unit.row}-u${unit.col}")
                unit.copy(symbol = symbols[pitch.yul to pitch.octave].orEmpty()) else unit
        })
    }) }
    val adapted = current.copy(score = current.score.copy(instrument = capability.instrumentCode),
        music = current.music.copy(hwangHz = 440.0 * 2.0.pow((capability.nominalHwangMidi - 69) / 12.0)),
        events = events, columns = columns)
    val metadata = authoring?.let { base -> JsonObject(base + ("adaptation" to buildJsonObject {
        put("instrument", capability.instrumentCode); put("transpose", plan.transpose)
        put("changes", buildJsonArray { plan.notes.filter { it.target != null && it.target.midi != it.source.midi }.forEach {
            add(buildJsonObject { put("eventId", it.source.eventId); put("fromMidi", it.source.midi)
                put("toMidi", it.target!!.midi); put("octaveShift", it.octaveShift ?: 0) })
        } })
    })) }
    return adapted.editingDraft(title, bpm, jangdan).copy(jangdanId = jangdanId,
        editedJangdanName = editedJangdanName, authoring = metadata)
}
