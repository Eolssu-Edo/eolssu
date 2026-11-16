package com.ssafy.eolssu.feature.song.edit.component

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import com.ssafy.eolssu.core.model.InputLimit
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.BasicAccompanimentPreset
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.*
import com.ssafy.eolssu.feature.song.edit.SheetDraft
import com.ssafy.eolssu.feature.song.edit.basicAccompaniment
import com.ssafy.eolssu.feature.song.edit.basicAccompanimentStartCell
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** 적용 전 선택은 창 안에만 두고, 적용 한 번으로 실행 취소·자동 저장에 반영한다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SheetBasicAccompanimentDialog(
    draft: SheetDraft,
    selectedCell: Int,
    onApply: (BasicAccompanimentPreset, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = draft.basicAccompaniment
    var presetName by rememberSaveable { mutableStateOf(current?.preset?.name) }
    var firstCell by rememberSaveable {
        mutableStateOf((draft.basicAccompanimentStartCell + 1).toString())
    }
    val preset = BasicAccompanimentPreset.entries.firstOrNull { it.name == presetName }
    val cell = firstCell.toIntOrNull()?.minus(1)?.takeIf { it in draft.cells.indices }
    EolssuDialog(stringResource(Res.string.basic_accompaniment_title), onDismissRequest = onDismiss) {
        Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.basic_accompaniment_help), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicAccompanimentPreset.entries.forEach { option ->
                    // 앱 알약 칩: 회색 바탕, 고르면 연두(S15P31E102-970).
                    EolssuChip(option.label, selected = option == preset, onClick = UserActionLog.click(QaTarget.SHEET_BASIC_ACCOMPANIMENT_DIALOG_01, { presetName = option.name }),
                        bordered = false, containerColor = EolssuTheme.colors.groupedBackground)
                }
            }
            Text(stringResource(Res.string.basic_accompaniment_pickup_help), style = MaterialTheme.typography.bodySmall)
            // 악보 전체 정간 순서라 이 악보 정간 수의 자릿수까지만 받는다(S15P31E102-996).
            val cellDigits = remember(draft) { draft.cells.size.toString().length }
            EolssuTextField(firstCell, { firstCell = it },
                label = stringResource(Res.string.basic_accompaniment_first_cell, draft.cells.size), placeholder = "1",
                type = TextFieldType.Number, maxLength = cellDigits,
                error = if (cell == null) stringResource(Res.string.basic_accompaniment_cell_error, draft.cells.size) else null, qaTarget = QaTarget.SHEET_BASIC_ACCOMPANIMENT_DIALOG_FIELD_01)
            if (cell != null) Text(stringResource(Res.string.basic_accompaniment_cell_location,
                draft.columnOf(cell) + 1, draft.rowOf(cell) + 1), style = MaterialTheme.typography.bodySmall)
            EolssuButton(stringResource(Res.string.basic_accompaniment_use_selected, selectedCell + 1),
                { firstCell = (selectedCell + 1).toString() }, style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.SHEET_BASIC_ACCOMPANIMENT_DIALOG_BUTTON_01)
        }
        EolssuButton(stringResource(Res.string.sheet_details_apply),
            { if (preset != null && cell != null) onApply(preset, cell) }, enabled = preset != null && cell != null, qaTarget = QaTarget.SHEET_BASIC_ACCOMPANIMENT_DIALOG_BUTTON_02)
        EolssuButton(stringResource(Res.string.common_cancel), onDismiss, style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.SHEET_BASIC_ACCOMPANIMENT_DIALOG_BUTTON_03)
    }
}
