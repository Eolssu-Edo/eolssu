package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SheetEditDetailsDialog(initialTitle: String, initialBpm: Int, onApply: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var bpm by rememberSaveable { mutableStateOf(initialBpm.toString()) }
    val validTitle = normalizedSheetTitle(title)
    val validBpm = bpm.toIntOrNull()?.takeIf { it in SheetBpmRange }

    EolssuDialog(
        title = stringResource(Res.string.sheet_details_edit),
        modifier = Modifier.verticalScroll(rememberScrollState()),
        onDismissRequest = onDismiss,
    ) {
        EolssuTextField(
            value = title,
            onValueChange = { title = it },
            label = stringResource(Res.string.sheet_details_title),
            placeholder = "",
            error = if (validTitle == null) stringResource(Res.string.sheet_details_title_error) else null,
        )
        EolssuTextField(
            value = bpm,
            onValueChange = { bpm = it },
            label = stringResource(Res.string.sheet_details_bpm),
            placeholder = "",
            type = TextFieldType.Number,
            imeAction = ImeAction.Done,
            error = if (validBpm == null) stringResource(Res.string.sheet_details_bpm_error) else null,
        )
        EolssuButton(
            text = stringResource(Res.string.sheet_details_apply),
            onClick = { if (validTitle != null && validBpm != null) onApply(validTitle, validBpm) },
            enabled = validTitle != null && validBpm != null,
        )
        EolssuButton(stringResource(Res.string.common_cancel), onDismiss, style = EolssuButtonStyle.Secondary)
    }
}
