package com.ssafy.eolssu.feature.song.edit.component

import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.runtime.Composable
import com.ssafy.eolssu.data.song.SheetConversionFailure
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
import eolssu.shared.generated.resources.sheet_upload_dialog_unrecognized_body
import eolssu.shared.generated.resources.sheet_upload_dialog_unrecognized_title
import org.jetbrains.compose.resources.stringResource

/** 악보 파일을 고르거나 인식할 때 안내 창으로 알리는 문제. */
enum class SheetFileProblem {
    /** JPG·PNG·PDF가 아닌 파일을 골랐다. '확인'만 둔다. */
    Format,

    /** 파일을 열거나 해석하지 못했다. */
    Unreadable,

    /** 인식기가 정간·오선을 찾지 못했다(업로드 뒤 변환 실패). */
    Unrecognized,
    ;

    companion object {
        /**
         * 내 악보 '다시 불러오기'에서 보일 안내. 정간·오선을 못 찾았으면 인식 실패, 인식기가 못 읽은 파일이면 읽기 실패다.
         * 시간 초과·서버 오류처럼 파일을 바꿔도 소용없는 실패는 안내하지 않는다(null).
         */
        fun ofConversionFailure(failure: SheetConversionFailure?): SheetFileProblem? = when (failure) {
            SheetConversionFailure.NoScoreFound -> Unrecognized
            SheetConversionFailure.UnsupportedFile -> Unreadable
            else -> null
        }
    }
}

/** [SheetFileProblem] 안내 창. 형식 오류는 '확인', 나머지는 '다시 선택하기'([onRepick])와 '취소'를 둔다. */
@Composable
internal fun SheetFileProblemDialog(problem: SheetFileProblem, onRepick: () -> Unit, onDismiss: () -> Unit) {
    val (title, body) = when (problem) {
        SheetFileProblem.Format -> Res.string.sheet_upload_dialog_format_title to Res.string.sheet_upload_dialog_format_body
        SheetFileProblem.Unreadable -> Res.string.sheet_upload_dialog_unreadable_title to Res.string.sheet_upload_dialog_unreadable_body
        SheetFileProblem.Unrecognized -> Res.string.sheet_upload_dialog_unrecognized_title to Res.string.sheet_upload_dialog_unrecognized_body
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
