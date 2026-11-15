package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.ui.graphics.Color
import com.ssafy.eolssu.core.theme.EolssuTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
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
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuTextField
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.feature.song.edit.normalizedSheetTitle
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_cancel
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
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
    tempoIssue: com.ssafy.eolssu.data.song.SheetTempoIssue? = null,
    saving: Boolean = false,
    canApply: Boolean = true,
    durationSeconds: Double? = null,
    notice: @Composable () -> Unit = {},
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    val validTitle = normalizedSheetTitle(title)

    EolssuDialog(
        title = stringResource(Res.string.sheet_save_dialog_title),
        onDismissRequest = { if (!saving) onDismiss() },
    ) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EolssuTextField(
                value = title,
                onValueChange = { if (!saving) title = it },
                label = stringResource(Res.string.sheet_details_title),
                placeholder = "",
                error = if (validTitle == null) stringResource(Res.string.sheet_details_title_error) else null,
                bordered = false,
                containerColor = EolssuTheme.colors.groupedBackground,
            )
            if (tempoIssue != null) Text(tempoIssue.guidance(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (durationSeconds != null) {
                Text(
                    stringResource(Res.string.sheet_save_estimated_duration, formatClock(durationSeconds)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            notice()
        }
        EolssuButton(
            text = stringResource(if (saving) Res.string.sheet_completion_saving else Res.string.sheetedit_preview_save),
            onClick = { if (validTitle != null) onApply(validTitle) },
            enabled = validTitle != null && canApply && !saving,
        )
        EolssuButton(stringResource(Res.string.common_cancel), onDismiss, style = EolssuButtonStyle.Secondary, enabled = !saving,
            containerColor = EolssuTheme.colors.groupedBackground, borderColor = Color.Transparent)
    }
}
