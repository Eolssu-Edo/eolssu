package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuTextField
import com.ssafy.eolssu.core.ui.component.TextFieldType
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.feature.song.edit.SheetBpmRange
import com.ssafy.eolssu.feature.song.edit.SheetDraft
import com.ssafy.eolssu.feature.song.edit.estimatedDurationSeconds
import com.ssafy.eolssu.feature.song.edit.tempoUnitLabel
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_cancel
import eolssu.shared.generated.resources.sheet_details_apply
import eolssu.shared.generated.resources.sheet_details_bpm
import eolssu.shared.generated.resources.sheet_details_bpm_error
import eolssu.shared.generated.resources.sheet_save_estimated_duration
import eolssu.shared.generated.resources.sheet_editor_duration_unavailable
import eolssu.shared.generated.resources.sheet_settings_title
import eolssu.shared.generated.resources.sheet_tempo_profile_note
import org.jetbrains.compose.resources.stringResource

/** 상단 편집은 빠르기만 바꾼다. 장단·음역은 각각의 입력 도구에서 고른다. */
@Composable
internal fun SheetEditSettingsDialog(
    draft: SheetDraft,
    onApply: (Int) -> Unit,
    onDismiss: () -> Unit,
    saving: Boolean = false,
    hasTempoPlan: Boolean = false,
) {
    var bpm by rememberSaveable { mutableStateOf(draft.bpm.toString()) }
    val validBpm = bpm.toIntOrNull()?.takeIf { it in SheetBpmRange }
    val estimate = if (hasTempoPlan) null else validBpm?.let(draft::estimatedDurationSeconds)
    EolssuDialog(
        title = stringResource(Res.string.sheet_settings_title),
        onDismissRequest = { if (!saving) onDismiss() },
    ) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Text(
                if (estimate != null) stringResource(Res.string.sheet_save_estimated_duration, formatClock(estimate))
                else stringResource(Res.string.sheet_editor_duration_unavailable),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        EolssuButton(stringResource(Res.string.sheet_details_apply),
            onClick = { validBpm?.let(onApply) }, enabled = validBpm != null && !saving)
        EolssuButton(stringResource(Res.string.common_cancel), onDismiss,
            style = EolssuButtonStyle.Secondary, enabled = !saving,
            containerColor = EolssuTheme.colors.groupedBackground, borderColor = Color.Transparent)
    }
}
