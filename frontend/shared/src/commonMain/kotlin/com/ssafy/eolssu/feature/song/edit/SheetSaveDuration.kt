package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.data.song.ModelTempoChange
import com.ssafy.eolssu.data.song.estimatedModelDurationSeconds
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

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
    // 입력 중인 전체 BPM에도 기존 reference 비율 변환을 한 번만 적용한다.
    val changes = copy(bpm = bpm).tempoChanges.filter { it.resolved }.map { change ->
        ModelTempoChange(change.cell, requireNotNull(change.targetBpm), change.endCell, change.startBpm)
    }
    return estimatedModelDurationSeconds(cells.size, beatsPerCell, bpm.toDouble(), changes)
}
