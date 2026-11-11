package com.ssafy.eolssu.feature.song.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.ssafy.eolssu.data.song.CatalogJangdanSummary
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheet_jangdan_beat_quarter
import eolssu.shared.generated.resources.sheet_jangdan_beat_dotted_quarter
import eolssu.shared.generated.resources.sheet_jangdan_current_timing
import eolssu.shared.generated.resources.sheet_jangdan_gak_calculation
import eolssu.shared.generated.resources.sheet_jangdan_other_lengths
import eolssu.shared.generated.resources.sheet_jangdan_option_beats
import eolssu.shared.generated.resources.sheet_jangdan_timing_unavailable
import eolssu.shared.generated.resources.sheet_jangdan_no_match_help
import eolssu.shared.generated.resources.sheet_jangdan_cell_length_help
import eolssu.shared.generated.resources.sheet_jangdan_reimport_help
import org.jetbrains.compose.resources.stringResource

internal data class SheetGakBeats(val cells: Int, val beats: String)
internal data class SheetJangdanTiming(
    val bpmUnit: String,
    val beatsPerCell: String,
    val gakLengths: List<SheetGakBeats>,
    val unsupportedCellDuration: Boolean,
)

/** 저장된 시간값을 설명한다. 표시 각을 박자로 추정하거나 음의 길이를 바꾸지 않는다. */
internal fun SheetDraft.jangdanTiming(): SheetJangdanTiming? {
    val music = original?.music ?: return null
    if (music.bpmUnit !in setOf("QUARTER", "DOTTED_QUARTER")) return null
    val cell = music.ticksPerJeonggan.toLong()
    val beat = music.ticksPerBeat.toLong()
    if (cell <= 0 || beat <= 0) return null
    return SheetJangdanTiming(
        bpmUnit = music.bpmUnit,
        beatsPerCell = beatFraction(cell, beat),
        gakLengths = sizes.distinct().map { SheetGakBeats(it, beatFraction(it.toLong() * cell, beat)) },
        unsupportedCellDuration = cell != beat && cell * 2 != beat && cell * 3 != beat && cell * 2 != beat * 3,
    )
}

internal fun CatalogJangdanSummary.jangdanBeats(): String? {
    val cell = patternCellEighths() ?: return null
    val beat = when (bpmUnit) { "QUARTER" -> 2; "DOTTED_QUARTER" -> 3; else -> return null }
    return if (beatCount > 0) beatFraction(beatCount.toLong() * cell, beat.toLong()) else null
}

/** data/reference/jangdans.json의 장구 패턴 한 칸 음가. 호환 판정과 안내가 같은 값을 쓴다. */
internal fun CatalogJangdanSummary.patternCellEighths(): Int? = when (name) {
    "중모리" -> 2
    "세령산" -> 3
    "세마치", "굿거리", "자진모리", "중중모리", "자진타령", "엇모리", "단모리", "타령", "계면가락도드리" -> 1
    else -> null
}

private fun beatFraction(numerator: Long, denominator: Long): String {
    fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
    val divisor = gcd(numerator, denominator)
    val bottom = denominator / divisor
    return if (bottom == 1L) (numerator / divisor).toString() else "${numerator / divisor}/$bottom"
}

@Composable
internal fun SheetJangdanTimingText(draft: SheetDraft) {
    val timing = draft.jangdanTiming()
    if (timing == null) {
        Text(stringResource(Res.string.sheet_jangdan_timing_unavailable), style = MaterialTheme.typography.bodySmall)
        return
    }
    val parts = timing.gakLengths.take(4).map {
        stringResource(Res.string.sheet_jangdan_gak_calculation, it.cells, timing.beatsPerCell, it.beats)
    }.toMutableList()
    if (timing.gakLengths.size > 4) parts += stringResource(Res.string.sheet_jangdan_other_lengths)
    Text(stringResource(Res.string.sheet_jangdan_current_timing, beatUnitName(timing.bpmUnit), parts.joinToString(" / ")),
        style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun SheetJangdanOptionLabel(option: CatalogJangdanSummary) {
    Column {
        Text(option.name)
        option.jangdanBeats()?.let { beats ->
            Text(stringResource(Res.string.sheet_jangdan_option_beats, beats, beatUnitName(option.bpmUnit)),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun SheetJangdanNoMatchHelp(draft: SheetDraft) {
    val timing = draft.jangdanTiming()
    if (timing?.unsupportedCellDuration == true) {
        Text(stringResource(Res.string.sheet_jangdan_cell_length_help, timing.beatsPerCell), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(Res.string.sheet_jangdan_reimport_help), style = MaterialTheme.typography.bodySmall)
    }
    Text(stringResource(Res.string.sheet_jangdan_no_match_help), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun beatUnitName(unit: String): String = stringResource(
    if (unit == "DOTTED_QUARTER") Res.string.sheet_jangdan_beat_dotted_quarter else Res.string.sheet_jangdan_beat_quarter,
)
