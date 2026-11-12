package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.ui.graphics.Color
import com.ssafy.eolssu.core.theme.EolssuTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuTextField
import com.ssafy.eolssu.core.ui.component.TextFieldType
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.feature.song.edit.SheetBpmRange
import com.ssafy.eolssu.feature.song.edit.normalizedSheetTitle
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_cancel
import eolssu.shared.generated.resources.sheet_details_apply
import eolssu.shared.generated.resources.sheet_details_bpm
import eolssu.shared.generated.resources.sheet_details_bpm_error
import eolssu.shared.generated.resources.sheet_details_edit
import eolssu.shared.generated.resources.sheet_details_title
import eolssu.shared.generated.resources.sheet_details_title_error
import eolssu.shared.generated.resources.sheet_save_dialog_title
import eolssu.shared.generated.resources.sheet_save_estimated_duration
import eolssu.shared.generated.resources.sheetedit_preview_save
import eolssu.shared.generated.resources.sheet_completion_saving
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SheetEditDetailsDialog(
    initialTitle: String,
    initialBpm: Int,
    onApply: (String, Int) -> Unit,
    onDismiss: () -> Unit,
    saveMode: Boolean = false,
    saving: Boolean = false,
    canApply: Boolean = true,
    durationSeconds: ((Int) -> Double?)? = null,
    notice: @Composable () -> Unit = {},
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var bpm by rememberSaveable { mutableStateOf(initialBpm.toString()) }
    val validTitle = normalizedSheetTitle(title)
    val validBpm = bpm.toIntOrNull()?.takeIf { it in SheetBpmRange }

    EolssuDialog(
        title = stringResource(if (saveMode) Res.string.sheet_save_dialog_title else Res.string.sheet_details_edit),
        onDismissRequest = { if (!saving) onDismiss() },
    ) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            EolssuTextField(
                value = title,
                onValueChange = { if (!saving) title = it },
                label = stringResource(Res.string.sheet_details_title),
                placeholder = "",
                error = if (validTitle == null) stringResource(Res.string.sheet_details_title_error) else null,
                bordered = false,
                containerColor = EolssuTheme.colors.groupedBackground,
            )
            EolssuTextField(
                value = bpm,
                onValueChange = { if (!saving) bpm = it },
                label = stringResource(Res.string.sheet_details_bpm),
                placeholder = "",
                type = TextFieldType.Number,
                imeAction = ImeAction.Done,
                error = if (validBpm == null) stringResource(Res.string.sheet_details_bpm_error) else null,
                bordered = false,
                containerColor = EolssuTheme.colors.groupedBackground,
            )
            val estimate = validBpm?.let { durationSeconds?.invoke(it) }
            if (estimate != null) {
                Text(
                    stringResource(Res.string.sheet_save_estimated_duration, formatClock(estimate)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            notice()
        }
        EolssuButton(
            text = stringResource(when {
                saving -> Res.string.sheet_completion_saving
                saveMode -> Res.string.sheetedit_preview_save
                else -> Res.string.sheet_details_apply
            }),
            onClick = { if (validTitle != null && validBpm != null) onApply(validTitle, validBpm) },
            enabled = validTitle != null && validBpm != null && canApply && !saving,
        )
        EolssuButton(stringResource(Res.string.common_cancel), onDismiss, style = EolssuButtonStyle.Secondary, enabled = !saving,
            containerColor = EolssuTheme.colors.groupedBackground, borderColor = Color.Transparent)
    }
}
