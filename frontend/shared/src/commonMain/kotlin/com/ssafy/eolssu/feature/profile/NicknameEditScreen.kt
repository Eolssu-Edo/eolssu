package com.ssafy.eolssu.feature.profile

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.ImeAction
import com.ssafy.eolssu.core.model.NicknameRule
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuTextField
import com.ssafy.eolssu.feature.profile.component.ProfileScaffold
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.my_nickname
import eolssu.shared.generated.resources.my_nickname_edit
import eolssu.shared.generated.resources.member_api_nickname_rule
import eolssu.shared.generated.resources.my_save
import org.jetbrains.compose.resources.stringResource

/** MY-02 닉네임 수정. 닉네임은 서버와 같은 길이 제한을 안내한다. */
@Composable
fun NicknameEditScreen(
    state: NicknameEditUiState,
    widthClass: WindowWidthClass,
    onBack: () -> Unit,
    onNicknameChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    ProfileScaffold(
        widthClass,
        stringResource(Res.string.my_nickname_edit),
        onBack,
        bottomBar = { EolssuButton(stringResource(Res.string.my_save), onClick = UserActionLog.click(QaTarget.NICKNAME_EDIT_SCREEN_01, onSave), busy = state.submitting, qaTarget = QaTarget.NICKNAME_EDIT_SCREEN_BUTTON_01) },
    ) {
        EolssuTextField(qaTarget = QaTarget.NICKNAME_EDIT_SCREEN_02,
            value = state.nickname,
            onValueChange = UserActionLog.change(QaTarget.NICKNAME_EDIT_SCREEN_02, onNicknameChange),
            label = stringResource(Res.string.my_nickname),
            placeholder = stringResource(Res.string.my_nickname),
            help = stringResource(Res.string.member_api_nickname_rule),
            error = state.error?.let { stringResource(it.message) },
            // 가입 닉네임 칸과 같은 글자 수 표시(`4/10`). 10자에서 더 받지 않고, 규칙 이전의 긴 닉네임만 `12/10`처럼 보인다.
            counter = "${state.nickname.length}/${NicknameRule.MAX_LENGTH}",
            imeAction = ImeAction.Done,
            onImeAction = onSave,
        )
    }
}
