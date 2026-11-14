package com.ssafy.eolssu.feature.song.edit

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.math.ln
import kotlin.math.ln1p

/** 미입력 정간도 포함한다. 구간 BPM은 표시 단위이며 점진 변화는 기보 위치에 선형이다. */
internal fun SheetDraft.estimatedDurationSeconds(bpm: Int): Double? {
    if (bpm !in SheetBpmRange) return null
    val changesTiming = setOf("반복표", "도돌이표", "덧길이표", "반길이표")
    if (sigimsae.values.flatten().any { it.name in changesTiming }) return null
    val plan = tempoExpressions?.get("performancePlan") as? JsonObject
    if (!(plan?.get("repeats") as? JsonArray).isNullOrEmpty()) return null
    val music = original?.music
    val beatsPerCell = if (music != null) {
        if (music.ticksPerJeonggan <= 0 || music.ticksPerBeat <= 0) return null
        music.ticksPerJeonggan.toDouble() / music.ticksPerBeat
    } else {
        jangdan?.let { 1.0 / it.subdivision } ?: return null
    }
    var cursor = 0
    var currentBpm = bpm.toDouble()
    var seconds = 0.0
    // 입력 중인 전체 BPM에도 기존 reference 비율 변환을 한 번만 적용한다.
    for (change in copy(bpm = bpm).tempoChanges) {
        if (!change.resolved) continue
        if (change.cell !in cells.indices || change.cell < cursor) return null
        val target = change.targetBpm?.takeIf { it.isFinite() && it > 0 } ?: return null
        seconds += (change.cell - cursor) * 60.0 * beatsPerCell / currentBpm
        val end = change.endCell
        if (end != null) {
            if (end !in (change.cell + 1)..cells.size) return null
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
    seconds += (cells.size - cursor) * 60.0 * beatsPerCell / currentBpm
    return seconds.takeIf { it.isFinite() && it > 0 }
}
