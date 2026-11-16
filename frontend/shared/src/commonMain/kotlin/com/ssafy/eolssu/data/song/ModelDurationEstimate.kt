package com.ssafy.eolssu.data.song

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.ln
import kotlin.math.ln1p

/** 정간 위치는 0부터, 끝은 변화가 끝나는 정간 시작이며 악보 끝도 허용한다. BPM은 곡의 표시 단위다. */
internal data class ModelTempoChange(
    val cell: Int,
    val targetBpm: Double,
    val endCell: Int? = null,
    val startBpm: Double? = null,
)

/** 선형 BPM 변화의 역수를 적분한다. 원본 tick·음표와 녹음/평가 시계는 바꾸지 않는다. */
internal fun estimatedModelDurationSeconds(
    cellCount: Int,
    beatsPerCell: Double,
    bpm: Double,
    changes: List<ModelTempoChange>,
): Double? {
    if (cellCount <= 0 || !beatsPerCell.isFinite() || beatsPerCell <= 0 || !bpm.isFinite() || bpm <= 0) return null
    var cursor = 0
    var currentBpm = bpm
    var seconds = 0.0
    for (change in changes) {
        if (change.cell !in 0 until cellCount || change.cell < cursor) return null
        val target = change.targetBpm.takeIf { it.isFinite() && it > 0 } ?: return null
        seconds += (change.cell - cursor) * 60.0 * beatsPerCell / currentBpm
        val end = change.endCell
        if (end != null) {
            if (end !in (change.cell + 1)..cellCount) return null
            val initial = (change.startBpm ?: currentBpm).takeIf { it.isFinite() && it > 0 } ?: return null
            val delta = (target - initial) / initial
            val inverseBpm = if (delta == 0.0) 1.0 / initial else {
                val logRatio = if (delta > -1.0) ln1p(delta) else ln(target) - ln(initial)
                logRatio / (target - initial)
            }
            seconds += (end - change.cell) * 60.0 * beatsPerCell * inverseBpm
        }
        cursor = end ?: change.cell
        currentBpm = target
    }
    seconds += (cellCount - cursor) * 60.0 * beatsPerCell / currentBpm
    return seconds.takeIf { it.isFinite() && it > 0 }
}

/** 확정 악보의 구간 빠르기로 모범 연주 길이만 예상한다. 반복·길이 기호·잘못된 계획은 기존 시간 계산에 맡긴다. */
internal fun CommonScoreData.estimatedModelDurationMs(expressions: JsonObject?, bpm: Int): Double? {
    val plan = expressions?.get("performancePlan") as? JsonObject ?: return null
    if (events.any { event -> event.sigimsae.any { it.substringBefore('@') in UnestimatedTimingSymbols } }) return null
    val repeats = plan["repeats"]
    if (repeats != null && (repeats !is JsonArray || repeats.isNotEmpty())) return null
    if (music.ticksPerJeonggan <= 0 || music.ticksPerBeat <= 0) return null
    val beatsPerCell = music.ticksPerJeonggan.toDouble() / music.ticksPerBeat
    val raw = plan["tempos"]
    if (raw != null && raw !is JsonArray) return null
    val changes = (raw as? JsonArray).orEmpty().map { element ->
        val entry = element as? JsonObject ?: return null
        val cell = tempoCell(entry, "gak", "cell") ?: return null
        val target = (entry["bpm"] as? JsonPrimitive)?.doubleOrNull ?: return null
        val end = if ("end_gak" in entry || "end_cell" in entry) {
            tempoCell(entry, "end_gak", "end_cell", terminal = true) ?: return null
        } else null
        val start = if ("start_bpm" in entry) {
            if (end == null) return null
            (entry["start_bpm"] as? JsonPrimitive)?.doubleOrNull ?: return null
        } else null
        // performancePlan은 정간/분, 화면의 BPM은 music.bpmUnit 기준이다.
        ModelTempoChange(cell, target * beatsPerCell, end, start?.times(beatsPerCell))
    }.sortedBy { it.cell }
    if (changes.map { it.cell }.distinct().size != changes.size) return null
    val seconds = estimatedModelDurationSeconds(columns.sumOf { it.cells.size }, beatsPerCell, bpm.toDouble(), changes) ?: return null
    return (seconds * 1000).takeIf { it.isFinite() }
}

internal fun CommonScoreData.tempoCell(entry: JsonObject, columnKey: String, cellKey: String, terminal: Boolean = false): Int? {
    val column = (entry[columnKey] as? JsonPrimitive)?.intOrNull ?: return null
    val cell = (entry[cellKey] as? JsonPrimitive)?.intOrNull ?: return null
    if (terminal && column == columns.size + 1 && cell == 1) return columns.sumOf { it.cells.size }
    val cells = columns.getOrNull(column - 1)?.cells ?: return null
    if (cell !in 1..cells.size) return null
    return columns.take(column - 1).sumOf { it.cells.size } + cell - 1
}

private val UnestimatedTimingSymbols = setOf("반복표", "도돌이표", "덧길이표", "반길이표")
