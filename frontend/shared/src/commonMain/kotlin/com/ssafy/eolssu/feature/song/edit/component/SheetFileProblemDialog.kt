package com.ssafy.eolssu.feature.song.edit.component

import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.runtime.Composable
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_cancel
import eolssu.shared.generated.resources.common_confirm
import eolssu.shared.generated.resources.sheet_upload_dialog_format_body
import eolssu.shared.generated.resources.sheet_upload_dialog_format_title
import eolssu.shared.generated.resources.sheet_upload_dialog_repick
import eolssu.shared.generated.resources.sheet_upload_dialog_unreadable_body
import eolssu.shared.generated.resources.sheet_upload_dialog_unreadable_title
import org.jetbrains.compose.resources.stringResource

/** 악보 파일을 고르거나 인식할 때 안내 창으로 알리는 문제. */
enum class SheetFileProblem {
    /** JPG·PNG·PDF가 아닌 파일을 골랐다. '확인'만 둔다. */
    Format,

    /** 파일을 열거나 해석하지 못했다. */
    Unreadable,
}

/** [SheetFileProblem] 안내 창. 형식 오류는 '확인', 나머지는 '다시 선택하기'([onRepick])와 '취소'를 둔다. */
@Composable
internal fun SheetFileProblemDialog(problem: SheetFileProblem, onRepick: () -> Unit, onDismiss: () -> Unit) {
    val (title, body) = when (problem) {
        SheetFileProblem.Format -> Res.string.sheet_upload_dialog_format_title to Res.string.sheet_upload_dialog_format_body
        SheetFileProblem.Unreadable -> Res.string.sheet_upload_dialog_unreadable_title to Res.string.sheet_upload_dialog_unreadable_body
    }
    EolssuDialog(title = stringResource(title), message = stringResource(body), onDismissRequest = onDismiss) {
        if (problem == SheetFileProblem.Format) {
            EolssuButton(stringResource(Res.string.common_confirm), onDismiss, qaTarget = QaTarget.SHEET_FILE_PROBLEM_DIALOG_BUTTON_01)
        } else {
            EolssuButton(stringResource(Res.string.sheet_upload_dialog_repick), onRepick, qaTarget = QaTarget.SHEET_FILE_PROBLEM_DIALOG_BUTTON_02)
            EolssuButton(stringResource(Res.string.common_cancel), onDismiss, style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.SHEET_FILE_PROBLEM_DIALOG_BUTTON_03)
        }
    }
}
