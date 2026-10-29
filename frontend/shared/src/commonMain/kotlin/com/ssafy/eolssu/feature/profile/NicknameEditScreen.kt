package com.ssafy.eolssu.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.ImeAction
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuTextField
import com.ssafy.eolssu.feature.profile.component.ProfileScaffold
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.my_nickname
import eolssu.shared.generated.resources.my_nickname_edit
import eolssu.shared.generated.resources.my_nickname_help
import eolssu.shared.generated.resources.my_save
import org.jetbrains.compose.resources.stringResource

/** MY-02 닉네임 수정. 닉네임 규칙은 '[규칙 안내]' 자리다. */
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
        bottomBar = { EolssuButton(stringResource(Res.string.my_save), onClick = onSave) },
    ) {
        EolssuTextField(
            value = state.nickname,
            onValueChange = onNicknameChange,
            label = stringResource(Res.string.my_nickname),
            placeholder = stringResource(Res.string.my_nickname),
            help = stringResource(Res.string.my_nickname_help),
            error = state.error?.let { stringResource(it.message) },
            imeAction = ImeAction.Done,
            onImeAction = onSave,
        )
    }
}
