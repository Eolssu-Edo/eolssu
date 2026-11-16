package com.ssafy.eolssu.feature.history.component

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

/**
 * 상단 바 오른쪽 끝 회색 휴지통(48dp 누르는 칸, 이름은 ‘기록 삭제’로 읽힌다). 지우는 중에는 누를 수 없다.
 * 머리에서 가장 진한 색이 되지 않도록 빨강은 삭제 확인 창에만 쓴다(S15P31E102-849).
 */
@Composable
internal fun RecordDeleteButton(busy: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = UserActionLog.click(QaTarget.RECORD_DELETE_PARTS_01, onClick),
        enabled = !busy,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = if (busy) colors.textDisabled else colors.textMuted,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(EolssuIcons.Trash, contentDescription = stringResource(Res.string.record_delete), modifier = Modifier.size(20.dp))
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
        EolssuButton(stringResource(Res.string.record_delete_confirm), actions.onConfirm, style = EolssuButtonStyle.Danger, qaTarget = QaTarget.RECORD_DELETE_PARTS_BUTTON_01)
        EolssuButton(stringResource(Res.string.common_cancel), actions.onCancel, style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.RECORD_DELETE_PARTS_BUTTON_02)
    }
}

@Composable
internal fun RecordDeleteConfirmation(state: RecordDetailUiState, actions: RecordDetailActions) {
    if (state.delete == RecordDeleteState.Confirm) {
        RecordDeleteDialog(RecordDeleteActions(actions.onAskDelete, actions.onConfirmDelete, actions.onCancelDelete))
    }
}
