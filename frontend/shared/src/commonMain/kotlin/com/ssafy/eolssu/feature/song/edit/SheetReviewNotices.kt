package com.ssafy.eolssu.feature.song.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.KoreanParticle
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.core.ui.component.withParticle
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheet_review_notice_grid
import eolssu.shared.generated.resources.sheet_review_notice_jangdan
import eolssu.shared.generated.resources.sheet_review_notice_jangdan_mismatch
import eolssu.shared.generated.resources.sheet_review_notice_other
import eolssu.shared.generated.resources.sheet_review_notice_page
import eolssu.shared.generated.resources.sheet_review_notice_staff
import eolssu.shared.generated.resources.sheet_review_notice_text
import eolssu.shared.generated.resources.sheet_review_notice_transposed
import eolssu.shared.generated.resources.sheet_review_notice_transposed_unknown
import eolssu.shared.generated.resources.sheet_review_notice_unreadable
import eolssu.shared.generated.resources.sheet_tempo_position_unresolved
import eolssu.shared.generated.resources.sheet_review_meter_signature
import eolssu.shared.generated.resources.sheet_review_meter_unknown
import eolssu.shared.generated.resources.sheet_review_measure_short
import eolssu.shared.generated.resources.sheet_review_measure_long
import eolssu.shared.generated.resources.sheet_review_measure_empty
import eolssu.shared.generated.resources.sheet_review_notice_tempo_pdf
import eolssu.shared.generated.resources.sheet_review_notice_tempo_pdf_count
import eolssu.shared.generated.resources.sheet_review_notice_voice
import eolssu.shared.generated.resources.sheet_review_notice_occurrences
import eolssu.shared.generated.resources.sheet_review_notice_measure
import eolssu.shared.generated.resources.sheet_review_notice_global
import eolssu.shared.generated.resources.sheet_review_notice_information
import org.jetbrains.compose.resources.stringResource

/** 변환 당시 참고 정보는 저장 화면에 바로 표시한다. 현재 오류는 SheetCompletionNotice에서 다룬다. */
@Composable
internal fun SheetConversionNotes(notices: List<SheetReviewNotice>) {
    val information = notices.filterNot { it.type == "JANGDAN_MISMATCH" }
    if (information.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().heightIn(max = 96.dp).verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(Res.string.sheet_review_notice_information), style = MaterialTheme.typography.bodySmall)
        information.forEach { notice ->
            val reason = notice.label()
            val repeated = if (notice.occurrences > 1 && !(notice.type == "LOW_CONFIDENCE" && notice.reason == "TEMPO_PDF_TEXT" && notice.count != null))
                stringResource(Res.string.sheet_review_notice_occurrences, reason, notice.occurrences) else reason
            val label = notice.measure?.let { stringResource(Res.string.sheet_review_notice_measure, it, repeated) } ?: repeated
            Text(
                notice.page?.let { stringResource(Res.string.sheet_review_notice_page, it, label) }
                    ?: if (notice.measure != null) label else stringResource(Res.string.sheet_review_notice_global, label),
                style = MaterialTheme.typography.bodySmall,
                color = EolssuTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun SheetReviewNotice.label(): String = rhythmReview?.label()
    ?: if (reason == "TEMPO_CUE_POSITION_UNRESOLVED") stringResource(Res.string.sheet_tempo_position_unresolved)
    else if (type == "LOW_CONFIDENCE" && reason == "TEMPO_PDF_TEXT") count?.let {
        stringResource(Res.string.sheet_review_notice_tempo_pdf_count, it)
    } ?: stringResource(Res.string.sheet_review_notice_tempo_pdf)
    else when (type) {
    "UNREADABLE" -> stringResource(
        when (reason) {
            "NO_GRID" -> Res.string.sheet_review_notice_grid
            "NO_STAFF" -> Res.string.sheet_review_notice_staff
            "NO_TEXT" -> Res.string.sheet_review_notice_text
            else -> Res.string.sheet_review_notice_unreadable
        },
    )
    "JANGDAN_GUESSED" -> stringResource(Res.string.sheet_review_notice_jangdan, jangdanLabel(jangdan.orEmpty()).withParticle(KoreanParticle.EuroRo))
    "JANGDAN_MISMATCH" -> stringResource(Res.string.sheet_review_notice_jangdan_mismatch)
    "TRANSPOSED" -> semitones?.let { stringResource(Res.string.sheet_review_notice_transposed, if (it > 0) "+$it" else it.toString()) }
        ?: stringResource(Res.string.sheet_review_notice_transposed_unknown)
    "VOICE_GUESSED" -> stringResource(Res.string.sheet_review_notice_voice)
    else -> stringResource(Res.string.sheet_review_notice_other)
}

@Composable
internal fun SheetRhythmReview.label(): String = when (reason) {
    "TIME_SIGNATURE_REVIEW_REQUIRED" -> timeSignature?.let { stringResource(Res.string.sheet_review_meter_signature, it.withParticle(KoreanParticle.EuroRo)) }
        ?: stringResource(Res.string.sheet_review_meter_unknown)
    "REPEATED_UNDERFULL_MEASURE" -> stringResource(Res.string.sheet_review_measure_short)
    "OVERFULL_MEASURE" -> stringResource(Res.string.sheet_review_measure_long)
    "EMPTY_MEASURE_REVIEW_REQUIRED" -> stringResource(Res.string.sheet_review_measure_empty, measure.orEmpty())
    else -> stringResource(Res.string.sheet_review_notice_other)
}
