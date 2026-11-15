package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.HanbaeMark
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.model.ScoreUnitKey
import kotlinx.serialization.json.*

/** 읽기 전용 투영: 원본·companion·편집용 기호 목록에는 파생 한배를 쓰지 않는다. */
internal fun Score.withImportedTempo(source: CommonScoreData, expressions: JsonObject?, baseBpm: Double?): Score {
    val starts = source.importedGradualTempoStarts(expressions, baseBpm)
    val derived = starts.mapNotNull { (start, mark) ->
        val cell = columns[start.column][start.row]
        val id = cell.id ?: return@mapNotNull null
        // 세로 한배 영역은 정간 단위다. 명시 기호가 다른 소박에 있어도 우선한다.
        if (tempoMarks.any { it.key.cellId == id && it.value.isNotEmpty() }) return@mapNotNull null
        val unit = cell.units.firstOrNull() ?: return@mapNotNull null
        ScoreUnitKey(id, unit.row, unit.col) to listOf(mark)
    }.toMap()
    return copy(tempoMarks = tempoMarks + derived)
}

private fun CommonScoreData.importedGradualTempoStarts(expressions: JsonObject?, baseBpm: Double?): List<Pair<ScorePosition, HanbaeMark>> {
    val plan = expressions?.get("performancePlan") as? JsonObject ?: return emptyList()
    val raw = plan["tempos"] as? JsonArray ?: return emptyList()
    if (music.ticksPerJeonggan <= 0 || music.ticksPerBeat <= 0) return emptyList()
    val factor = music.ticksPerJeonggan.toDouble() / music.ticksPerBeat
    fun JsonObject.bpm(key: String): Double? = (get(key) as? JsonPrimitive)?.doubleOrNull
        ?.times(factor)?.takeIf { it.isFinite() && it > 0 }
    val changes = raw.map { element ->
        val entry = element as? JsonObject ?: return emptyList()
        val cell = tempoCell(entry, "gak", "cell") ?: return emptyList()
        val target = entry.bpm("bpm") ?: return emptyList()
        val end = if ("end_gak" in entry || "end_cell" in entry) {
            tempoCell(entry, "end_gak", "end_cell", terminal = true) ?: return emptyList()
        } else null
        val start = if ("start_bpm" in entry) {
            if (end == null) return emptyList()
            entry.bpm("start_bpm") ?: return emptyList()
        } else null
        ModelTempoChange(cell, target, end, start)
    }.sortedBy { it.cell }
    if (changes.map { it.cell }.distinct().size != changes.size) return emptyList()
    val positions = columns.flatMapIndexed { column, value -> value.cells.indices.map { ScorePosition(column, it) } }
    var previous = baseBpm?.takeIf { it.isFinite() && it > 0 }
    var finish = 0
    val starts = mutableListOf<Pair<ScorePosition, HanbaeMark>>()
    for (change in changes) {
        if (change.cell < finish) return emptyList()
        val end = change.endCell
        if (end != null) {
            if (end !in (change.cell + 1)..positions.size) return emptyList()
            val initial = change.startBpm ?: previous
            val mark = when {
                initial == null -> null
                change.targetBpm > initial -> HanbaeMark.GraduallyFaster
                change.targetBpm < initial -> HanbaeMark.GraduallySlower
                else -> null
            }
            if (mark != null) starts += positions[change.cell] to mark
        }
        finish = end ?: change.cell
        previous = change.targetBpm
    }
    return starts
}
