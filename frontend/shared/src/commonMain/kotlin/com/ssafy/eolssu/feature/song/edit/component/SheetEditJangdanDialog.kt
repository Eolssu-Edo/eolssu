package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.feature.song.edit.SheetEditJangdanPicker
import com.ssafy.eolssu.feature.song.edit.SheetDraft
import com.ssafy.eolssu.feature.song.edit.SheetJangdanTimingText
import com.ssafy.eolssu.feature.song.edit.SheetJangdanOptionLabel
import com.ssafy.eolssu.feature.song.edit.SheetJangdanNoMatchHelp
import com.ssafy.eolssu.feature.song.edit.isCompatibleWith
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_cancel
import eolssu.shared.generated.resources.sheetedit_cell_jangdan_change
import eolssu.shared.generated.resources.sheetedit_jangdan_same_unit
import eolssu.shared.generated.resources.sheetedit_jangdan_load_failed
import eolssu.shared.generated.resources.sheet_upload_reload
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SheetEditJangdanDialog(
    picker: SheetEditJangdanPicker,
    selectedId: String?,
    draft: SheetDraft,
    onSelect: (String) -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
) {
    EolssuDialog(
        title = stringResource(Res.string.sheetedit_cell_jangdan_change),
        modifier = Modifier.verticalScroll(rememberScrollState()),
        message = stringResource(Res.string.sheetedit_jangdan_same_unit),
        onDismissRequest = onDismiss,
    ) {
        SheetJangdanTimingText(draft)
        when (picker) {
            SheetEditJangdanPicker.Loading -> CircularProgressIndicator()
            SheetEditJangdanPicker.Failed -> {
                Text(stringResource(Res.string.sheetedit_jangdan_load_failed))
                EolssuButton(stringResource(Res.string.sheet_upload_reload), onReload, style = EolssuButtonStyle.Secondary)
            }
            is SheetEditJangdanPicker.Ready -> {
                if (picker.options.none { draft.isCompatibleWith(it) }) SheetJangdanNoMatchHelp()
                FlowRow(
                    modifier = Modifier.heightIn(max = 230.dp).verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    picker.options.forEach { option ->
                        FilterChip(selected = option.jangdanId == selectedId, enabled = draft.isCompatibleWith(option),
                            onClick = { onSelect(option.jangdanId) }, label = { SheetJangdanOptionLabel(option) })
                    }
                }
            }
        }
        EolssuButton(stringResource(Res.string.common_cancel), onDismiss, style = EolssuButtonStyle.Secondary)
    }
}
