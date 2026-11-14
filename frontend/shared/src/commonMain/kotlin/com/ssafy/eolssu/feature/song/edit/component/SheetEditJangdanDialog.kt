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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.feature.song.edit.SheetEditJangdanPicker
import com.ssafy.eolssu.feature.song.edit.SheetDraft
import com.ssafy.eolssu.feature.song.edit.SheetJangdanOptionLabel
import com.ssafy.eolssu.feature.song.edit.SheetJangdanNoMatchHelp
import com.ssafy.eolssu.feature.song.edit.canSelectJangdan
import com.ssafy.eolssu.feature.song.edit.requiresJangdanRetiming
import com.ssafy.eolssu.feature.song.edit.basicAccompaniment
import com.ssafy.eolssu.feature.song.edit.canSelectBasicAccompaniment
import com.ssafy.eolssu.core.model.BasicAccompanimentPreset
import eolssu.shared.generated.resources.basic_accompaniment_title
import eolssu.shared.generated.resources.basic_accompaniment_none
import eolssu.shared.generated.resources.basic_accompaniment_catalog
import eolssu.shared.generated.resources.basic_accompaniment_unsupported
import eolssu.shared.generated.resources.basic_accompaniment_source_unavailable
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_cancel
import eolssu.shared.generated.resources.sheetedit_cell_jangdan_change
import androidx.compose.foundation.layout.height
import eolssu.shared.generated.resources.sheetedit_jangdan_same_unit
import eolssu.shared.generated.resources.sheet_jangdan_correction_help
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
    onClear: () -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
    hasTempoPlan: Boolean = false,
    selectedCell: Int = 0,
    onBasic: (BasicAccompanimentPreset, Int) -> Unit,
) {
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    var basicOpen by rememberSaveable { mutableStateOf(false) }
    if (basicOpen) {
        SheetBasicAccompanimentDialog(draft, selectedCell, onApply = onBasic, onDismiss = { basicOpen = false })
        return
    }
    EolssuDialog(
        title = stringResource(Res.string.sheetedit_cell_jangdan_change),
        modifier = Modifier.verticalScroll(rememberScrollState()),
        message = stringResource(if (draft.inferredJeongganSizes != null && !hasTempoPlan)
            Res.string.sheet_jangdan_correction_help else Res.string.sheetedit_jangdan_same_unit),
        onDismissRequest = onDismiss,
    ) {
        EolssuButton(draft.basicAccompaniment?.let { stringResource(Res.string.basic_accompaniment_title) + " · " + it.preset.label }
            ?: stringResource(Res.string.basic_accompaniment_title), { basicOpen = true },
            style = EolssuButtonStyle.Secondary, enabled = draft.canSelectBasicAccompaniment)
        if (!draft.canSelectBasicAccompaniment) Text(stringResource(
            if (draft.original?.music?.bpmUnit !in setOf("QUARTER", "DOTTED_QUARTER")) Res.string.basic_accompaniment_unsupported
            else Res.string.basic_accompaniment_source_unavailable))
        Text(stringResource(Res.string.basic_accompaniment_catalog))
        val selectable = (picker as? SheetEditJangdanPicker.Ready)?.options.orEmpty()
            .filter { draft.canSelectJangdan(it, hasTempoPlan) }
        when (picker) {
            SheetEditJangdanPicker.Loading -> CircularProgressIndicator()
            SheetEditJangdanPicker.Failed -> {
                Text(stringResource(Res.string.sheetedit_jangdan_load_failed))
                EolssuButton(stringResource(Res.string.sheet_upload_reload), onReload, style = EolssuButtonStyle.Secondary)
            }
            is SheetEditJangdanPicker.Ready -> if (selectable.isEmpty()) SheetJangdanNoMatchHelp()
        }
        FlowRow(
            modifier = Modifier.heightIn(max = 230.dp).verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = selectedId == null && draft.basicAccompaniment == null, onClick = onClear,
                modifier = Modifier.height(56.dp),
                label = { Text(stringResource(Res.string.basic_accompaniment_none)) })
            selectable.forEach { option ->
                FilterChip(selected = option.jangdanId == selectedId, modifier = Modifier.height(56.dp),
                    onClick = {
                        if (!hasTempoPlan && draft.requiresJangdanRetiming(option)) pendingId = option.jangdanId
                        else onSelect(option.jangdanId)
                    }, label = { SheetJangdanOptionLabel(option) })
            }
        }
        EolssuButton(stringResource(Res.string.common_cancel), onDismiss, style = EolssuButtonStyle.Secondary)
    }
    (picker as? SheetEditJangdanPicker.Ready)?.options?.firstOrNull { it.jangdanId == pendingId }?.let { option ->
        SheetJangdanCorrectionDialog(option, onApply = { pendingId = null; onSelect(option.jangdanId) },
            onDismiss = { pendingId = null })
    }
}
