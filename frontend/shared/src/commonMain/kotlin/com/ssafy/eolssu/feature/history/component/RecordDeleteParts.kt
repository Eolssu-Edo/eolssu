package com.ssafy.eolssu.feature.history.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.*
import com.ssafy.eolssu.feature.history.RecordDeleteState
import com.ssafy.eolssu.feature.history.RecordDetailUiState
import com.ssafy.eolssu.feature.history.RecordDetailActions
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** 이전 연주 결과의 '기록 삭제' 동작. */
class RecordDeleteActions(val onAsk: () -> Unit, val onConfirm: () -> Unit, val onCancel: () -> Unit)

/** 상단 바 오른쪽 끝 휴지통 + ‘기록 삭제’(둘 다 붉은색 #C8402F). 지우는 중에는 누를 수 없다. */
@Composable
internal fun RecordDeleteButton(busy: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    val tint = if (busy) colors.textDisabled else colors.rec
    Surface(onClick = onClick, enabled = !busy, shape = RoundedCornerShape(10.dp), color = Color.Transparent, contentColor = tint) {
        Row(
            Modifier.heightIn(min = 44.dp).padding(start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(EolssuIcons.Trash, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(Res.string.record_delete),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
        }
    }
}

/** 삭제 확인 창. 기록과 녹음이 함께 지워지고 되돌릴 수 없음을 알린다. */
@Composable
internal fun RecordDeleteDialog(actions: RecordDeleteActions) {
    val colors = EolssuTheme.colors
    EolssuDialog(
        title = stringResource(Res.string.record_delete_title),
        message = stringResource(Res.string.record_delete_message),
        icon = EolssuIcons.Trash,
        iconBackground = colors.recSoft,
        iconTint = colors.recText,
        onDismissRequest = actions.onCancel,
    ) {
        EolssuButton(stringResource(Res.string.record_delete_confirm), actions.onConfirm, style = EolssuButtonStyle.Danger)
        EolssuButton(stringResource(Res.string.common_cancel), actions.onCancel, style = EolssuButtonStyle.Secondary)
    }
}

@Composable
internal fun RecordDeleteConfirmation(state: RecordDetailUiState, actions: RecordDetailActions) {
    if (state.delete == RecordDeleteState.Confirm) {
        RecordDeleteDialog(RecordDeleteActions(actions.onAskDelete, actions.onConfirmDelete, actions.onCancelDelete))
    }
}
