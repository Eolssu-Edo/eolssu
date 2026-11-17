package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.data.song.CatalogJangdanSummary
import kotlinx.serialization.json.*

internal fun SheetDraft.inferredJeongganColumnSizes(): List<Int>? {
    val source = (authoring?.get("source") as? JsonObject)?.get("score") as? JsonObject
    if (source != null) {
        val converter = ((source["provenance"] as? JsonObject)?.get("converter") as? JsonPrimitive)?.content
        if (converter?.startsWith("jeongganbo-text/") != true && converter?.startsWith("jeongganbo-omr/") != true) return null
        return (source["columns"] as? JsonArray)?.map { ((it as? JsonObject)?.get("cells") as? JsonArray)?.size ?: 0 }.orEmpty()
    }
    val score = original ?: return null
    if (!score.provenance.converter.startsWith("jeongganbo-text/") && !score.provenance.converter.startsWith("jeongganbo-omr/")) return null
    return score.columns.map { it.cells.size }
}

/**
 * 선택 후보는 인식 격자로 판단하고 확정 저장의 isCompatibleWith는 실제 시간값을 검사한다.
 * 구간 빠르기·반복은 각·정간 번호에 묶여 있고 재시간 보정은 칸을 바꾸지 않으므로 계획이 있어도 후보를 보인다(S15P31E102-1091).
 */
internal fun SheetDraft.canSelectJangdan(option: CatalogJangdanSummary): Boolean =
    if (inferredJeongganSizes == null) isCompatibleWith(option) else jangdanRetiming(option) != null

private val SheetDraft.hasPositionedTempoPlan: Boolean get() = tempoExpressions.hasPositionedTempoPlan()

internal fun SheetDraft.requiresJangdanRetiming(option: CatalogJangdanSummary): Boolean {
    val target = jangdanRetiming(option) ?: return false
    val music = original?.music ?: return false
    return music.bpmUnit != option.bpmUnit || music.ticksPerBeat != target.beat || music.ticksPerJeonggan != target.cell
}

internal fun SheetDraft.withJangdan(option: CatalogJangdanSummary): SheetDraft? {
    if (!canSelectJangdan(option)) return null
    val retiming = requiresJangdanRetiming(option)
    // 정간보는 장단이 빠르기 기준이라 고른 장단의 얼쑤 기본 빠르기로 맞춘다. 이전 숫자를 남기면 단위만 바뀌어
    // 4박 반주 160♩가 160♩.처럼 된다(S15P31E102-1054). 오선보와, 박 길이를 바꾸지 않는 빠르기 계획 악보는 적힌 빠르기를 지킨다.
    val keepsWrittenTempo = hasPositionedTempoPlan && !retiming
    val nextBpm = if (!keepsWrittenTempo && inferredJeongganSizes != null && option.defaultBpm in SheetBpmRange) option.defaultBpm else bpm
    if (!retiming)
        return copy(jangdanId = option.jangdanId, editedJangdanName = option.name, bpm = nextBpm)
    // 보정 전 현재 편집을 먼저 반영한다. 불변 authoring.source와 칸·음고·위치는 그대로 둔다.
    val score = runCatching { toCommonScore() }.getOrNull() ?: return null
    val target = jangdanRetiming(option, score.music.ticksPerJeonggan.toLong()) ?: return null
    if (score.events.any { it.startTick > Long.MAX_VALUE / target.factor || it.durationTick > Long.MAX_VALUE / target.factor }) return null
    val corrected = score.copy(
        music = score.music.copy(jangdan = option.name, bpmUnit = option.bpmUnit, ticksPerBeat = target.beat, ticksPerJeonggan = target.cell),
        events = score.events.map { it.copy(startTick = it.startTick * target.factor, durationTick = it.durationTick * target.factor) },
    )
    val retimed = corrected.editingDraft(title, nextBpm, jangdan).copy(jangdanId = option.jangdanId, editedJangdanName = option.name, authoring = authoring,
        adaptationBaseline = sourcePitchNotes().associate { it.eventId to it.midi })
    if (tempoExpressions == null) return retimed
    // 칸·ID가 그대로이므로 계획과 한배 기호는 같은 자리에 남는다. 숫자는 기준 빠르기 대비 배율을 유지한다(S15P31E102-1091).
    val planned = retimed.copy(tempoExpressions = retimedTempoExpressions(target.beat, target.cell), tempoReferenceBpm = tempoReferenceBpm ?: bpm)
    return planned.takeIf { it.tempoChangesWithinRange() }
}

/** 이 장단으로 박 길이를 바꾸면 구간 빠르기가 편집 범위(40~200)를 벗어나 고를 수 없는지 본다. */
internal fun SheetDraft.jangdanTempoOutOfRange(option: CatalogJangdanSummary): Boolean =
    hasPositionedTempoPlan && canSelectJangdan(option) && requiresJangdanRetiming(option) && withJangdan(option) == null

/** 박 길이 보정 전후의 구간 빠르기(시작 정간, 이전 목표, 새 목표). 확인 창에서 바뀌는 숫자를 보인다. */
internal fun SheetDraft.jangdanTempoPreview(option: CatalogJangdanSummary): List<Triple<Int, Double, Double>> {
    if (!hasPositionedTempoPlan || !requiresJangdanRetiming(option)) return emptyList()
    val after = withJangdan(option)?.tempoChanges?.associateBy { it.cell } ?: return emptyList()
    return tempoChanges.mapNotNull { cue ->
        val before = cue.targetBpm ?: return@mapNotNull null
        val next = after[cue.cell]?.targetBpm ?: return@mapNotNull null
        Triple(cue.cell, before, next)
    }
}

private data class JangdanRetiming(val beat: Int, val cell: Int, val factor: Long)

private fun SheetDraft.jangdanRetiming(option: CatalogJangdanSummary, cellTicks: Long? = null): JangdanRetiming? {
    val grid = inferredJeongganSizes?.distinct()?.singleOrNull()?.takeIf { it > 0 } ?: return null
    if (sizes.isEmpty() || sizes.any { it <= 0 || it % grid != 0 }) return null
    val first = original?.events?.firstOrNull() ?: return null
    if (first.cellId != "c0-j0" || first.startTick != 0L) return null
    val beatEighths = when (option.bpmUnit) { "QUARTER" -> 2; "DOTTED_QUARTER" -> 3; else -> return null }
    val patternCell = option.patternCellEighths() ?: return null
    if (option.beatCount !in 1..64) return null
    val top = option.beatCount.toLong() * patternCell
    val bottom = beatEighths.toLong() * grid
    val divisor = gcd(top, bottom)
    val numerator = top / divisor
    val denominator = bottom / divisor
    // OMR가 지원하는 한 각=한 주기 해석을 재사용한다. 모르는 음가를 새로 추측하지 않는다.
    if (!(numerator == 1L && denominator in 1L..3L) &&
        !(option.name == "세령산" && numerator == 3L && denominator == 2L)) return null
    val oldCell = (cellTicks ?: original.music.ticksPerJeonggan.toLong()).takeIf { it in 1L..1_000_000_000L } ?: return null
    val factor = numerator / gcd(oldCell, numerator)
    val cell = oldCell * factor
    val beat = cell * denominator / numerator
    if (cell !in 1L..1_000_000_000L || beat !in 1L..1_000_000_000L) return null
    return JangdanRetiming(beat.toInt(), cell.toInt(), factor)
}

private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
