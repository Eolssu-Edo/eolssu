package com.ssafy.eolssu.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.diagnostics.QaTarget
import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuTextField
import com.ssafy.eolssu.feature.profile.component.ProfileScaffold
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.my_inquiry
import eolssu.shared.generated.resources.my_inquiry_content_label
import eolssu.shared.generated.resources.my_inquiry_content_placeholder
import eolssu.shared.generated.resources.my_inquiry_help
import eolssu.shared.generated.resources.my_inquiry_send
import eolssu.shared.generated.resources.my_inquiry_title_label
import eolssu.shared.generated.resources.my_inquiry_title_placeholder
import org.jetbrains.compose.resources.stringResource

/** 마이페이지 문의하기(S15P31E102-1240). 제목 한 줄과 여러 줄 내용을 받아 개발자 메일로 보낸다. */
@Composable
fun InquiryScreen(
    state: InquiryUiState,
    widthClass: WindowWidthClass,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    ProfileScaffold(
        widthClass,
        stringResource(Res.string.my_inquiry),
        onBack,
        bottomBar = {
            EolssuButton(
                stringResource(Res.string.my_inquiry_send),
                onClick = UserActionLog.click(QaTarget.INQUIRY_SCREEN_01, onSend),
                busy = state.submitting,
                qaTarget = QaTarget.INQUIRY_SCREEN_BUTTON_01,
            )
        },
    ) {
        EolssuTextField(
            qaTarget = QaTarget.INQUIRY_SCREEN_02,
            value = state.title,
            onValueChange = onTitleChange,
            label = stringResource(Res.string.my_inquiry_title_label),
            placeholder = stringResource(Res.string.my_inquiry_title_placeholder),
            error = state.titleError?.let { stringResource(it.message) },
            maxLength = InquiryRule.TITLE_MAX_LENGTH,
            imeAction = ImeAction.Next,
        )
        EolssuTextField(
            qaTarget = QaTarget.INQUIRY_SCREEN_03,
            value = state.content,
            onValueChange = onContentChange,
            label = stringResource(Res.string.my_inquiry_content_label),
            placeholder = stringResource(Res.string.my_inquiry_content_placeholder),
            help = stringResource(Res.string.my_inquiry_help),
            error = state.error?.let { stringResource(it.message) },
            counter = "${state.content.length}/${InquiryRule.CONTENT_MAX_LENGTH}",
            maxLength = InquiryRule.CONTENT_MAX_LENGTH,
            fieldHeight = 200.dp,
            singleLine = false,
            imeAction = ImeAction.Default,
        )
    }
}
