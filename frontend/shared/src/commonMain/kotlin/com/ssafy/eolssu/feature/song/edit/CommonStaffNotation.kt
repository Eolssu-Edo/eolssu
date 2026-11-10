package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.ScoreBpmUnit
import com.ssafy.eolssu.core.model.ScoreEvent
import com.ssafy.eolssu.core.model.ScoreNote
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

internal sealed interface CommonStaffProjection {
    data class Ready(val glyphs: List<CommonStaffGlyph>) : CommonStaffProjection
    enum class Unsupported : CommonStaffProjection { Incomplete, Document, Tuning, PitchRange, TimingUnit, Duration, Size }
}

internal data class CommonStaffGlyph(
    val event: ScoreEvent,
    val key: StaffNoteKey,
    val pitch: StaffPitch?,
    val denominator: Int,
    val dots: Int,
    val tuplet: Boolean,
    val sliceIndex: Int,
    val sliceCount: Int,
) {
    val tieFromPrevious: Boolean get() = pitch != null && sliceIndex > 0
    val tieToNext: Boolean get() = pitch != null && sliceIndex < sliceCount - 1
}

/** 공통 악보의 실음과 tick만 오선보로 옮긴다. 원본 오선의 마디와 조표는 추정하지 않는다. */
internal fun SheetDraft.commonStaffProjection(): CommonStaffProjection {
    if (unfilledCells.isNotEmpty()) return CommonStaffProjection.Unsupported.Incomplete
    val score = runCatching { toCommonScore().toScore() }.getOrNull() ?: return CommonStaffProjection.Unsupported.Document
    val timing = requireNotNull(score.timing)
    val quarterMultiplier = when (timing.bpmUnit) {
        ScoreBpmUnit.QUARTER -> 1
        ScoreBpmUnit.DOTTED_QUARTER -> 3
        else -> return CommonStaffProjection.Unsupported.TimingUnit
    }
    val quarterDivisor = timing.ticksPerBeat.toLong() * if (quarterMultiplier == 3) 2 else 1
    val hwangMidi = staffHwangMidi(timing.hwangHz) ?: return CommonStaffProjection.Unsupported.Tuning

    val columnStarts = score.columns.runningFold(0) { start, column -> start + column.size }
    val glyphs = mutableListOf<CommonStaffGlyph>()
    for (event in requireNotNull(score.events)) {
        val duration = requireNotNull(event.durationTick)
        if (duration > MaxGlyphs.toLong() * 7 * timing.ticksPerBeat) return CommonStaffProjection.Unsupported.Size
        // 정수로만 계산한다. 64분음표와 겹점을 모두 표현하는 4분음표당 64단위다.
        val numerator = duration * quarterMultiplier * UnitsPerQuarter
        val tuplet = numerator % quarterDivisor != 0L
        val writtenNumerator = if (tuplet) numerator * 3 else numerator
        val writtenDivisor = if (tuplet) quarterDivisor * 2 else quarterDivisor
        if (writtenNumerator % writtenDivisor != 0L) return CommonStaffProjection.Unsupported.Duration
        val writtenUnits = writtenNumerator / writtenDivisor
        val remaining = MaxGlyphs - glyphs.size
        if (writtenUnits > remaining.toLong() * LargestValue.units) return CommonStaffProjection.Unsupported.Size
        val shapes = writtenShapes(writtenUnits.toInt()) ?: return CommonStaffProjection.Unsupported.Duration
        if (shapes.size > remaining) return CommonStaffProjection.Unsupported.Size

        val pitch = when (val note = event.note) {
            ScoreNote.Rest -> null
            is ScoreNote.SourcePitch -> {
                val midi = hwangMidi + note.baseYul.ordinal + note.octave * 12
                if (midi !in 0..127) return CommonStaffProjection.Unsupported.PitchRange
                staffPitch(midi)
            }
            else -> return CommonStaffProjection.Unsupported.Document
        }
        val cell = score.columns[event.column][event.row]
        val sub = cell.units.indexOfFirst { it.eventId == event.id }
        if (sub < 0) return CommonStaffProjection.Unsupported.Document
        val key = StaffNoteKey(columnStarts[event.column] + event.row, sub)
        shapes.forEachIndexed { index, shape ->
            glyphs += CommonStaffGlyph(event, key, pitch, shape.denominator, shape.dots, tuplet, index, shapes.size)
        }
    }
    return CommonStaffProjection.Ready(glyphs)
}

/** Hz 반올림 오차로 1센트까지 허용하고, 다른 조율을 가까운 서양 음으로 바꾸지 않는다. */
internal fun staffHwangMidi(hz: Double): Int? {
    val midi = 69 + 12 * log2(hz / 440)
    if (!midi.isFinite()) return null
    val nearest = midi.roundToInt()
    return nearest.takeIf { it in 0..127 && abs(midi - it) <= 0.01 }
}

private const val MaxGlyphs = 4096
private const val UnitsPerQuarter = 64
private data class WrittenShape(val denominator: Int, val dots: Int) {
    val units = UnitsPerQuarter * 4 / denominator * ((1 shl (dots + 1)) - 1) / (1 shl dots)
}

private val WrittenShapes = listOf(1, 2, 4, 8, 16, 32, 64).flatMap { denominator ->
    (0..2).map { dots -> WrittenShape(denominator, dots) }
}.sortedByDescending { it.units }
private val LargestValue = WrittenShapes.first()

/** 짧은 나머지는 필요한 음표 수가 가장 적게 분할하고, 동률이면 점이 적은 표기를 쓴다. */
private val ShortShapes: Array<WrittenShape?> = run {
    val limit = LargestValue.units * 2
    val result = arrayOfNulls<WrittenShape>(limit + 1)
    val counts = IntArray(limit + 1) { Int.MAX_VALUE }
    val dots = IntArray(limit + 1) { Int.MAX_VALUE }
    counts[0] = 0
    dots[0] = 0
    for (units in 1..limit) for (shape in WrittenShapes) {
        val before = units - shape.units
        if (before < 0 || counts[before] == Int.MAX_VALUE) continue
        val count = counts[before] + 1
        val dotCount = dots[before] + shape.dots
        if (count < counts[units] || count == counts[units] && dotCount < dots[units]) {
            counts[units] = count
            dots[units] = dotCount
            result[units] = shape
        }
    }
    result
}

private fun writtenShapes(units: Int): List<WrittenShape>? {
    val full = ((units - LargestValue.units) / LargestValue.units).coerceAtLeast(0)
    val result = MutableList(full) { LargestValue }
    var rest = units - full * LargestValue.units
    while (rest > 0) {
        val shape = ShortShapes[rest] ?: return null
        result += shape
        rest -= shape.units
    }
    return result
}
