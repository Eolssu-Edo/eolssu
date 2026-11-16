package com.ssafy.eolssu.feature.song.edit

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlinx.serialization.json.*
import com.ssafy.eolssu.data.song.CatalogInstrument
import com.ssafy.eolssu.data.song.CatalogAuthoringCapabilities
import com.ssafy.eolssu.core.model.ScoreNote

data class SheetAdaptationUi(
    val instruments: List<CatalogInstrument> = emptyList(), val selectedId: String? = null,
    val capabilities: CatalogAuthoringCapabilities? = null, val plans: List<PitchAdaptationPlan> = emptyList(),
    val selectedTranspose: Int? = null, val loading: Boolean = true, val failed: Boolean = false,
    val baseline: SheetDraft,
    val confirmationRequested: Boolean = false,
) {
    val selected: PitchAdaptationPlan? get() = plans.firstOrNull { it.transpose == selectedTranspose }

    /** 어느 높이로 옮겨도 악기로 낼 수 없는 음 수. 0보다 크면 높이만으로는 다 못 고치고 남은 음을 하나씩 바꿔야 한다. */
    val unavoidableUnresolved: Int get() = plans.minOfOrNull { it.unresolved } ?: 0
}

/** Immutable pitches captured before any adaptation. MIDI is sounding pitch, not a yul index. */
data class SourcePitchNote(val eventId: String, val cellId: String, val midi: Int)
data class AdaptationPitch(val midi: Int, val yul: String, val octave: Int)
data class AdaptedPitch(val source: SourcePitchNote, val target: AdaptationPitch?, val octaveShift: Int?)
data class PitchAdaptationPlan(val transpose: Int, val notes: List<AdaptedPitch>) {
    val unresolved: Int get() = notes.count { it.target == null }
    val octaveChanges: Int get() = notes.count { it.octaveShift != null && it.octaveShift != 0 }
    val octaveDistance: Int get() = notes.sumOf { abs(it.octaveShift ?: 0) }

    /** 곡 전체가 함께 옮겨진 옥타브. 가장 많은 음이 쓴 이동이며 같으면 0에 가까운 쪽이다. */
    val commonOctaveShift: Int get() = notes.mapNotNull { it.octaveShift }.groupingBy { it }.eachCount().entries
        .minWithOrNull(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { abs(it.key) }.thenBy { it.key })?.key ?: 0

    /** 공통 옥타브와 다르게 접혀 선율 윤곽을 끊는 음. 곡 전체를 한 옥타브 옮기는 것은 끊김이 아니다. */
    val octaveSplits: Int get() = commonOctaveShift.let { common -> notes.count { it.octaveShift != null && it.octaveShift != common } }
    val splitDistance: Int get() = commonOctaveShift.let { common -> notes.sumOf { it.octaveShift?.let { shift -> abs(shift - common) } ?: 0 } }
}

/**
 * 반음 이조마다 곡 전체의 공통 옥타브를 정하고, 각 음은 그 옥타브에 가장 가까운 지원음으로 맞춘다.
 * 음마다 가까운 옥타브로 따로 접으면 선율이 오르내리므로, 순위도 옮긴 음 수가 아니라 공통 옥타브와 갈린 음 수로 매긴다.
 */
fun planPitchAdaptation(source: List<SourcePitchNote>, supported: List<AdaptationPitch>): List<PitchAdaptationPlan> =
    (-6..6).map { transpose ->
        val options = source.map { note ->
            val transposed = note.midi + transpose
            note to supported.filter { (it.midi - transposed) % 12 == 0 }.map { it to (it.midi - transposed) / 12 }
        }
        val commons = options.flatMap { (_, targets) -> targets.map { it.second } }.distinct().ifEmpty { listOf(0) }
        commons.map { common ->
            PitchAdaptationPlan(transpose, options.map { (note, targets) ->
                val pick = targets.minWithOrNull(compareBy<Pair<AdaptationPitch, Int>> { abs(it.second - common) }
                    .thenBy { abs(it.second) }.thenBy { it.first.midi })
                AdaptedPitch(note, pick?.first, pick?.second)
            })
        }.minWith(compareBy<PitchAdaptationPlan> { it.octaveSplits }.thenBy { it.splitDistance }
            .thenBy { abs(it.commonOctaveShift) }.thenBy { it.commonOctaveShift })
    }.sortedWith(compareBy<PitchAdaptationPlan> { it.unresolved }.thenBy { it.octaveSplits }.thenBy { it.splitDistance }
        .thenBy { abs(it.transpose) }.thenBy { abs(it.commonOctaveShift) }.thenBy { it.transpose })

/** The default keeps the melody contour; the alternative preserves the key as closely as possible. */
internal fun recommendedAdaptations(plans: List<PitchAdaptationPlan>): List<PitchAdaptationPlan> =
    listOfNotNull(plans.firstOrNull(), plans.minWithOrNull(compareBy<PitchAdaptationPlan> { it.unresolved }
        .thenBy { abs(it.transpose) }.thenBy { it.octaveSplits }.thenBy { it.splitDistance }.thenBy { it.transpose }))
        .distinctBy { it.transpose }

/** Last applied original-relative offset belongs to this draft, never to a shared slider preference. */
internal val SheetDraft.appliedTranspose: Int
    get() = ((authoring?.get("adaptation") as? JsonObject)?.get("transpose") as? JsonPrimitive)
        ?.takeUnless { it.isString }?.intOrNull?.takeIf { it in -6..6 } ?: 0

/** Missing/null is not a server-side clear: send zero when undo restores a draft without an adaptation. */
internal fun SheetDraft.authoringForSave(instrumentCode: String): JsonObject? = authoring?.let { metadata ->
    if (metadata["adaptation"] != null && metadata["adaptation"] != JsonNull) metadata
    else JsonObject(metadata + ("adaptation" to buildJsonObject {
        put("instrument", instrumentCode); put("transpose", 0); put("changes", JsonArray(emptyList()))
    }))
}

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
    val metadataSource = authoring?.get("source")?.jsonObject
    val saved = metadataSource?.get("notes")?.jsonArray.orEmpty()
        .associate { it.jsonObject.getValue("eventId").jsonPrimitive.content to it.jsonObject }
    val score = toCommonScore()
    val hwang = (69 + 12 * log2(score.music.hwangHz / 440.0)).roundToInt()
    val originalEvents = original?.events.orEmpty().associateBy { it.id }
    val originalHwang = (69 + 12 * log2(requireNotNull(original).music.hwangHz / 440.0)).roundToInt()
    val originalCellTicks = original.music.ticksPerJeonggan.toLong()
    val sourceCellTicks = (metadataSource?.get("score") as? JsonObject)?.get("music")?.jsonObject
        ?.get("ticksPerJeonggan")?.jsonPrimitive?.intOrNull?.toLong() ?: originalCellTicks
    val baselines = buildMap<String, Int> {
        score.columns.flatMap { it.cells }.forEachIndexed { index, cell ->
            val oldCell = originalCell(index) ?: return@forEachIndexed
            cell.units.forEachIndexed units@ { sub, unit ->
                // Splitting appends slots. Only a former NOTE start can supply an original pitch.
                val oldUnit = oldCell.units.getOrNull(sub) ?: return@units
                val old = originalEvents[oldUnit.eventId]?.takeIf { it.kind == "NOTE" &&
                    it.cellId == oldCell.id && it.id == "${oldCell.id}-r${oldUnit.row}-u${oldUnit.col}" }
                    ?: return@units
                val source = saved[old.id]?.takeIf {
                    it["cellId"]?.jsonPrimitive?.content == old.cellId &&
                        sourceCellTicks > 0 && originalCellTicks > 0 &&
                        it["startTick"]?.jsonPrimitive?.longOrNull?.let { tick -> sameGridTime(tick, sourceCellTicks, old.startTick, originalCellTicks) } == true &&
                        it["durationTick"]?.jsonPrimitive?.longOrNull?.let { tick -> sameGridTime(tick, sourceCellTicks, old.durationTick, originalCellTicks) } == true
                }
                if (unit.eventId == "${cell.id}-r${unit.row}-u${unit.col}") put(unit.eventId,
                    adaptationBaseline[old.id] ?: source?.get("midi")?.jsonPrimitive?.int
                    ?: (originalHwang + "황대태협고중유임이남무응".indexOf(old.yul.orEmpty()) + 12 * (old.octave ?: 0)))
            }
        }
    }
    return score.events.filter { it.kind == "NOTE" }.map { event ->
        SourcePitchNote(event.id, event.cellId, baselines[event.id]
            ?: (hwang + "황대태협고중유임이남무응".indexOf(event.yul.orEmpty()) + 12 * (event.octave ?: 0)))
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
                unit.copy(symbol = symbols[pitch.yul to pitch.octave]
                    ?: ScoreNote.SourcePitch(pitch.yul, pitch.octave).hangul) else unit
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
        editedJangdanName = editedJangdanName, authoring = metadata,
        tempoExpressions = tempoExpressions, tempoReferenceBpm = tempoReferenceBpm,
        adaptationBaseline = plan.notes.associate { it.source.eventId to it.source.midi })
}
