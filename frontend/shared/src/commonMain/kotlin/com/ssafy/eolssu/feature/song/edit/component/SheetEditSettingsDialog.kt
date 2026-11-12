package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.ui.graphics.Color
import com.ssafy.eolssu.core.theme.EolssuTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuTextField
import com.ssafy.eolssu.core.ui.component.TextFieldType
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.feature.song.edit.SheetBpmRange
import com.ssafy.eolssu.feature.song.edit.tempoUnitLabel
import com.ssafy.eolssu.feature.song.edit.SheetDraft
import com.ssafy.eolssu.feature.song.edit.SheetEditJangdanPicker
import com.ssafy.eolssu.feature.song.edit.SheetJangdanTimingText
import com.ssafy.eolssu.feature.song.edit.SheetJangdanOptionLabel
import com.ssafy.eolssu.feature.song.edit.SheetJangdanNoMatchHelp
import com.ssafy.eolssu.feature.song.edit.isCompatibleWith
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_cancel
import eolssu.shared.generated.resources.sheet_details_apply
import eolssu.shared.generated.resources.sheet_details_bpm
import eolssu.shared.generated.resources.sheet_details_bpm_error
import eolssu.shared.generated.resources.sheet_tempo_profile_note
import eolssu.shared.generated.resources.sheet_settings_current_jangdan
import eolssu.shared.generated.resources.sheet_settings_jangdan
import eolssu.shared.generated.resources.sheet_settings_jangdan_loading
import eolssu.shared.generated.resources.sheet_settings_jangdan_none
import eolssu.shared.generated.resources.sheet_settings_title
import eolssu.shared.generated.resources.sheetedit_jangdan_load_failed
import eolssu.shared.generated.resources.sheetedit_jangdan_same_unit
import eolssu.shared.generated.resources.sheet_upload_reload
import eolssu.shared.generated.resources.sheet_adapt_title
import org.jetbrains.compose.resources.stringResource

/** 빠르기와 장단은 대화상자 안에서만 고르고 적용할 때 한 번에 전달한다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SheetEditSettingsDialog(
    draft: SheetDraft,
    picker: SheetEditJangdanPicker?,
    onLoadJangdans: () -> Unit,
    onApply: (Int, String?) -> Unit,
    onDismiss: () -> Unit,
    onAdapt: () -> Unit,
    saving: Boolean = false,
    hasTempoPlan: Boolean = false,
) {
    var bpm by rememberSaveable { mutableStateOf(draft.bpm.toString()) }
    var selectedId by rememberSaveable { mutableStateOf(draft.jangdanId) }
    val validBpm = bpm.toIntOrNull()?.takeIf { it in SheetBpmRange }
    val options = (picker as? SheetEditJangdanPicker.Ready)?.options.orEmpty()
    val validSelection = selectedId == null || selectedId == draft.jangdanId ||
        options.any { it.jangdanId == selectedId && draft.isCompatibleWith(it) }
    LaunchedEffect(Unit) {
        if (picker == null) onLoadJangdans()
    }

    EolssuDialog(
        title = stringResource(Res.string.sheet_settings_title),
        onDismissRequest = { if (!saving) onDismiss() },
    ) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EolssuButton(stringResource(Res.string.sheet_adapt_title), onAdapt,
                style = EolssuButtonStyle.Secondary, enabled = !saving,
            containerColor = EolssuTheme.colors.groupedBackground, borderColor = Color.Transparent)
            EolssuTextField(
                value = bpm,
                onValueChange = { if (!saving) bpm = it },
                label = stringResource(Res.string.sheet_details_bpm) + " · " + draft.tempoUnitLabel,
                placeholder = "",
                type = TextFieldType.Number,
                imeAction = ImeAction.Done,
                error = if (validBpm == null) stringResource(Res.string.sheet_details_bpm_error) else null,
                bordered = false,
                containerColor = EolssuTheme.colors.groupedBackground,
            )
            if (hasTempoPlan) Text(stringResource(Res.string.sheet_tempo_profile_note), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(Res.string.sheet_settings_jangdan), style = MaterialTheme.typography.labelLarge)
            SheetJangdanTimingText(draft)
            Text(stringResource(Res.string.sheetedit_jangdan_same_unit), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedId == null,
                    enabled = !saving,
                    onClick = { selectedId = null },
                    label = { Text(stringResource(Res.string.sheet_settings_jangdan_none)) },
                )
                if (draft.jangdanId != null && options.none { it.jangdanId == draft.jangdanId }) {
                    val currentName = if (draft.jangdanName.isBlank()) stringResource(Res.string.sheet_settings_current_jangdan)
                        else jangdanLabel(draft.jangdanName)
                    FilterChip(
                        selected = selectedId == draft.jangdanId,
                        enabled = !saving,
                        onClick = { selectedId = draft.jangdanId },
                        label = { Text(currentName) },
                    )
                }
                options.forEach { option ->
                    FilterChip(
                        selected = selectedId == option.jangdanId,
                        enabled = !saving && draft.isCompatibleWith(option),
                        onClick = { selectedId = option.jangdanId },
                        label = { SheetJangdanOptionLabel(option) },
                    )
                }
            }
            when (picker) {
                null, SheetEditJangdanPicker.Loading -> {
                    CircularProgressIndicator()
                    Text(stringResource(Res.string.sheet_settings_jangdan_loading), style = MaterialTheme.typography.bodySmall)
                }
                SheetEditJangdanPicker.Failed -> {
                    Text(stringResource(Res.string.sheetedit_jangdan_load_failed))
                    EolssuButton(stringResource(Res.string.sheet_upload_reload), onLoadJangdans,
                        style = EolssuButtonStyle.Secondary, enabled = !saving,
            containerColor = EolssuTheme.colors.groupedBackground, borderColor = Color.Transparent)
                }
                is SheetEditJangdanPicker.Ready -> {
                    if (options.none { draft.isCompatibleWith(it) }) {
                        SheetJangdanNoMatchHelp()
                    }
                }
            }
        }
        EolssuButton(
            text = stringResource(Res.string.sheet_details_apply),
            onClick = { if (validBpm != null && validSelection) onApply(validBpm, selectedId) },
            enabled = validBpm != null && validSelection && !saving,
        )
        EolssuButton(stringResource(Res.string.common_cancel), onDismiss,
            style = EolssuButtonStyle.Secondary, enabled = !saving,
            containerColor = EolssuTheme.colors.groupedBackground, borderColor = Color.Transparent)
    }
}
