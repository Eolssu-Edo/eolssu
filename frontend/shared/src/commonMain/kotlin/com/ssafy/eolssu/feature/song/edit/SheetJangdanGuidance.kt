package com.ssafy.eolssu.feature.song.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.data.song.CatalogJangdanSummary
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheet_jangdan_beat_quarter
import eolssu.shared.generated.resources.sheet_jangdan_beat_dotted_quarter
import eolssu.shared.generated.resources.sheet_jangdan_option_beats
import eolssu.shared.generated.resources.sheet_jangdan_no_match_help
import org.jetbrains.compose.resources.stringResource

internal data class SheetGakBeats(val cells: Int, val beats: String)
internal data class SheetJangdanTiming(
    val bpmUnit: String,
    val beatsPerCell: String,
    val gakLengths: List<SheetGakBeats>,
)

/** 저장된 시간값을 설명한다. 표시 각을 박자로 추정하거나 음의 길이를 바꾸지 않는다. */
internal fun SheetDraft.jangdanTiming(): SheetJangdanTiming? {
    val music = original?.music ?: return null
    if (music.bpmUnit !in setOf("QUARTER", "DOTTED_QUARTER")) return null
    val cell = music.ticksPerJeonggan.toLong()
    val beat = music.ticksPerBeat.toLong()
    if (cell !in 1..1_000_000_000L || beat !in 1..1_000_000_000L) return null
    return SheetJangdanTiming(
        bpmUnit = music.bpmUnit,
        beatsPerCell = beatFraction(cell, beat),
        gakLengths = sizes.distinct().map { SheetGakBeats(it, beatFraction(it.toLong() * cell, beat)) },
    )
}

internal fun CatalogJangdanSummary.jangdanBeats(): String? {
    val cell = patternCellEighths() ?: return null
    val beat = when (bpmUnit) { "QUARTER" -> 2; "DOTTED_QUARTER" -> 3; else -> return null }
    return if (beatCount > 0) beatFraction(beatCount.toLong() * cell, beat.toLong()) else null
}

/** data/reference/jangdans.json의 장구 패턴 한 칸 음가. 호환 판정과 안내가 같은 값을 쓴다. */
internal fun CatalogJangdanSummary.patternCellEighths(): Int? = patternCellEighths?.takeIf { it in 1..64 }

private fun beatFraction(numerator: Long, denominator: Long): String {
    fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
    val divisor = gcd(numerator, denominator)
    val bottom = denominator / divisor
    return if (bottom == 1L) (numerator / divisor).toString() else "${numerator / divisor}/$bottom"
}

@Composable
internal fun SheetJangdanOptionLabel(option: CatalogJangdanSummary) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(option.name)
        option.jangdanBeats()?.let { beats ->
            Text(stringResource(Res.string.sheet_jangdan_option_beats, beats, beatUnitName(option.bpmUnit)),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun SheetJangdanNoMatchHelp() {
    Text(stringResource(Res.string.sheet_jangdan_no_match_help), style = dialogHelpStyle(), color = EolssuTheme.colors.textSub)
}

@Composable
private fun beatUnitName(unit: String): String = stringResource(
    if (unit == "DOTTED_QUARTER") Res.string.sheet_jangdan_beat_dotted_quarter else Res.string.sheet_jangdan_beat_quarter,
)

/** 다이얼로그 본문 안내와 같은 크기·색으로 맞춘다. */
@Composable
internal fun dialogHelpStyle() = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp)
