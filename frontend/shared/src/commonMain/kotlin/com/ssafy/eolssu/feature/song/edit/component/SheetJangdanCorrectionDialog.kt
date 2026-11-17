package com.ssafy.eolssu.feature.song.edit.component

import com.ssafy.eolssu.core.diagnostics.QaTarget

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
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SheetJangdanCorrectionDialog(option: CatalogJangdanSummary, onApply: () -> Unit, onDismiss: () -> Unit) {
    EolssuDialog(title = stringResource(Res.string.sheet_jangdan_correction_title),
        message = stringResource(Res.string.sheet_jangdan_correction_message), onDismissRequest = onDismiss) {
        SheetJangdanOptionLabel(option)
        EolssuButton(stringResource(Res.string.sheet_details_apply), onApply, qaTarget = QaTarget.SHEET_JANGDAN_CORRECTION_DIALOG_BUTTON_01)
        EolssuButton(stringResource(Res.string.common_cancel), onDismiss, style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.SHEET_JANGDAN_CORRECTION_DIALOG_BUTTON_02)
    }
}
