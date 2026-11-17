package com.ssafy.eolssu.feature.song.edit.component

import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.data.song.CatalogJangdanSummary
import com.ssafy.eolssu.feature.song.edit.SheetJangdanOptionLabel
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_cancel
import eolssu.shared.generated.resources.sheet_details_apply
import eolssu.shared.generated.resources.sheet_jangdan_correction_title
import eolssu.shared.generated.resources.sheet_jangdan_correction_message
import eolssu.shared.generated.resources.sheet_jangdan_correction_tempo
import eolssu.shared.generated.resources.sheet_jangdan_correction_tempo_line
import org.jetbrains.compose.resources.stringResource

/** 박 길이 보정으로 바뀌는 구간 빠르기 한 줄. 각·정간은 1부터 센다. */
internal data class SheetJangdanTempoPreview(val gak: Int, val cell: Int, val before: Double, val after: Double)

private fun Double.tempoText(): String {
    val rounded = kotlin.math.round(this * 10) / 10
    return if (rounded == kotlin.math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
}

@Composable
internal fun SheetJangdanCorrectionDialog(option: CatalogJangdanSummary, tempos: List<SheetJangdanTempoPreview> = emptyList(),
    onApply: () -> Unit, onDismiss: () -> Unit) {
    EolssuDialog(title = stringResource(Res.string.sheet_jangdan_correction_title),
        message = stringResource(Res.string.sheet_jangdan_correction_message), onDismissRequest = onDismiss) {
        SheetJangdanOptionLabel(option)
        // 구간 빠르기는 기본 빠르기 대비 배율을 지키며 숫자가 바뀐다. 적용 전에 바뀌는 숫자를 보인다(S15P31E102-1091).
        if (tempos.isNotEmpty()) {
            Text(stringResource(Res.string.sheet_jangdan_correction_tempo), style = MaterialTheme.typography.bodySmall)
            tempos.forEach { line ->
                Text(stringResource(Res.string.sheet_jangdan_correction_tempo_line, line.gak, line.cell, line.before.tempoText(), line.after.tempoText()),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        EolssuButton(stringResource(Res.string.sheet_details_apply), onApply, qaTarget = QaTarget.SHEET_JANGDAN_CORRECTION_DIALOG_BUTTON_01)
        EolssuButton(stringResource(Res.string.common_cancel), onDismiss, style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.SHEET_JANGDAN_CORRECTION_DIALOG_BUTTON_02)
    }
}
