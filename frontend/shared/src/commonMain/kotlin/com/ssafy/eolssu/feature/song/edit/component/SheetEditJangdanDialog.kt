package com.ssafy.eolssu.feature.song.edit.component

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import com.ssafy.eolssu.feature.song.edit.dialogHelpStyle
import com.ssafy.eolssu.feature.song.edit.canSelectJangdan
import com.ssafy.eolssu.feature.song.edit.requiresJangdanRetiming
import com.ssafy.eolssu.feature.song.edit.jangdanTempoOutOfRange
import com.ssafy.eolssu.feature.song.edit.jangdanTempoPreview
import com.ssafy.eolssu.feature.song.edit.basicAccompaniment
import eolssu.shared.generated.resources.basic_accompaniment_title
import eolssu.shared.generated.resources.basic_accompaniment_none
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_cancel
import eolssu.shared.generated.resources.sheetedit_cell_jangdan_change
import eolssu.shared.generated.resources.sheetedit_jangdan_same_unit
import eolssu.shared.generated.resources.sheet_jangdan_correction_help
import eolssu.shared.generated.resources.sheetedit_jangdan_load_failed
import eolssu.shared.generated.resources.sheet_upload_reload
import org.jetbrains.compose.resources.stringResource

/** 장단 하나를 고르는 네모 버튼(최소 52dp · 모서리 14). 고른 장단은 연두 채움, 나머지는 회색 바탕이다. */
@Composable
private fun JangdanOptionButton(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = UserActionLog.click(QaTarget.SHEET_EDIT_JANGDAN_DIALOG_01, onClick),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { this.selected = selected },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.primary else colors.groupedBackground,
        contentColor = if (selected) colors.onPrimary else colors.text,
    ) {
        // 위아래 기본 박 반주·취소 버튼과 같이 글자를 가운데에 둔다(S15P31E102-979).
        Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)) { label() }
        }
    }
}

@Composable
internal fun SheetEditJangdanDialog(
    picker: SheetEditJangdanPicker,
    selectedId: String?,
    draft: SheetDraft,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    EolssuDialog(
        title = stringResource(Res.string.sheetedit_cell_jangdan_change),
        // 대화상자가 제목·안내를 스크롤하므로 바깥에 스크롤을 또 두지 않는다. 겹치면 제목·안내가 0 높이로 접힌다(S15P31E102-1080).
        message = stringResource(if (draft.inferredJeongganSizes != null)
            Res.string.sheet_jangdan_correction_help else Res.string.sheetedit_jangdan_same_unit),
        onDismissRequest = onDismiss,
    ) {
        // 악보 올리기처럼 장단만 고른다. 기본 박 반주는 고르는 곳을 빼고, 이미 고른 악보에서만 지금 상태로 보인다(S15P31E102-1042).
        val selectable = (picker as? SheetEditJangdanPicker.Ready)?.options.orEmpty()
            .filter { draft.canSelectJangdan(it) }
        when (picker) {
            SheetEditJangdanPicker.Loading -> CircularProgressIndicator()
            SheetEditJangdanPicker.Failed -> {
                Text(stringResource(Res.string.sheetedit_jangdan_load_failed), style = dialogHelpStyle(), color = EolssuTheme.colors.textSub)
                EolssuButton(stringResource(Res.string.sheet_upload_reload), onReload, style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.SHEET_EDIT_JANGDAN_DIALOG_BUTTON_02)
            }
            is SheetEditJangdanPicker.Ready -> if (selectable.isEmpty()) SheetJangdanNoMatchHelp()
        }
        // 아래 버튼들과 같은 네모 버튼을 한 줄에 하나씩 같은 너비로 쌓는다(S15P31E102-889).
        Column(
            modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            JangdanOptionButton(selected = selectedId == null && draft.basicAccompaniment == null, onClick = UserActionLog.click(QaTarget.SHEET_EDIT_JANGDAN_DIALOG_02, onClear)) {
                Text(stringResource(Res.string.basic_accompaniment_none))
            }
            draft.basicAccompaniment?.let { basic ->
                JangdanOptionButton(selected = true, onClick = {}) {
                    Text(stringResource(Res.string.basic_accompaniment_title) + " · " + basic.preset.label)
                }
            }
            selectable.forEach { option ->
                JangdanOptionButton(selected = option.jangdanId == selectedId, onClick = UserActionLog.click(QaTarget.SHEET_EDIT_JANGDAN_DIALOG_03, {
                    if (draft.requiresJangdanRetiming(option) && !draft.jangdanTempoOutOfRange(option)) pendingId = option.jangdanId
                    else onSelect(option.jangdanId)
                })) { SheetJangdanOptionLabel(option) }
            }
        }
        EolssuButton(stringResource(Res.string.common_cancel), onDismiss, style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.SHEET_EDIT_JANGDAN_DIALOG_BUTTON_03)
    }
    (picker as? SheetEditJangdanPicker.Ready)?.options?.firstOrNull { it.jangdanId == pendingId }?.let { option ->
        SheetJangdanCorrectionDialog(option, draft.jangdanTempoPreview(option).map { (cell, before, after) ->
            SheetJangdanTempoPreview(draft.columnOf(cell) + 1, draft.rowOf(cell) + 1, before, after)
        }, onApply = { pendingId = null; onSelect(option.jangdanId) },
            onDismiss = { pendingId = null })
    }
}
