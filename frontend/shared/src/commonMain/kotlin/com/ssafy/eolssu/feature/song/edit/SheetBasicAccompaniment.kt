package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.BasicAccompaniment
import com.ssafy.eolssu.data.song.basicAccompaniment
import com.ssafy.eolssu.data.song.withBasicAccompaniment
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal val SheetDraft.basicAccompaniment: BasicAccompaniment?
    get() = original?.let { runCatching { authoring?.basicAccompaniment(toCommonScore()) }.getOrNull() }

internal val SheetDraft.canSelectBasicAccompaniment: Boolean
    get() = original?.music?.bpmUnit in setOf("QUARTER", "DOTTED_QUARTER") &&
        (authoring?.get("source") as? JsonObject)?.let { source ->
            (source["scoreSha256"] as? JsonPrimitive)?.content?.matches(Regex("[0-9a-f]{64}"))
        } == true

internal fun SheetDraft.withBasicAccompaniment(setting: BasicAccompaniment?): SheetDraft = copy(
    authoring = authoring?.let { if (setting == null && "accompaniment" !in it) it else it.withBasicAccompaniment(setting) },
)

internal val SheetDraft.accompanimentName: String
    get() = basicAccompaniment?.displayPattern?.name ?: jangdanName

/** 위치·tick 해상도 편집 뒤에도 같은 정간의 첫 강박을 따라간다. 반주를 직접 바꾼 편집은 그대로 둔다. */
internal fun SheetDraft.remapBasicAccompaniment(next: SheetDraft, atCell: Int = 0, removedCells: Int = 0, insertedCells: Int = 0): SheetDraft {
    if (authoring?.get("accompaniment") != next.authoring?.get("accompaniment")) return next
    val setting = basicAccompaniment ?: return next
    val oldCellTicks = toCommonScore().music.ticksPerJeonggan.toLong()
    val from = atCell.toLong() * oldCellTicks
    val removedEnd = (atCell.toLong() + removedCells) * oldCellTicks
    if (setting.startTick in from until removedEnd) return next.withBasicAccompaniment(null)
    val shifted = if (setting.startTick < from) setting.startTick
        else setting.startTick + (insertedCells.toLong() - removedCells) * oldCellTicks
    val nextCellTicks = runCatching { next.toCommonScore().music.ticksPerJeonggan.toLong() }.getOrNull() ?: return next
    fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
    val divisor = gcd(oldCellTicks, nextCellTicks)
    val denominator = oldCellTicks / divisor
    // 삭제한 각에만 있던 소박 나눔 때문에 tick 해상도가 줄어도 임의 반올림하지 않는다.
    if (shifted % denominator != 0L) return next.withBasicAccompaniment(null)
    val start = shifted / denominator * (nextCellTicks / divisor)
    return next.withBasicAccompaniment(setting.copy(startTick = start))
}
