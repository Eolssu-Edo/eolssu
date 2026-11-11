package com.ssafy.eolssu.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuTextField
import com.ssafy.eolssu.core.ui.component.TextFieldType
import com.ssafy.eolssu.feature.profile.component.ProfileScaffold
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.my_password_change
import eolssu.shared.generated.resources.my_password_change_done
import eolssu.shared.generated.resources.my_password_confirm
import eolssu.shared.generated.resources.my_password_confirm_placeholder
import eolssu.shared.generated.resources.my_password_current
import eolssu.shared.generated.resources.my_password_help
import eolssu.shared.generated.resources.my_password_new
import org.jetbrains.compose.resources.stringResource

/** MY-03 비밀번호 변경. 현재 비밀번호 · 새 비밀번호(가입과 같은 규칙 안내) · 새 비밀번호 확인. */
@Composable
fun PasswordChangeScreen(
    state: PasswordChangeUiState,
    widthClass: WindowWidthClass,
    onBack: () -> Unit,
    onCurrentChange: (String) -> Unit,
    onNewChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    ProfileScaffold(
        widthClass,
        stringResource(Res.string.my_password_change),
        onBack,
        gap = 16.dp,
        // 마이페이지처럼 회색 바탕 위 테두리 없는 흰 입력칸(S15P31E102-768).
        containerColor = EolssuTheme.colors.groupedBackground,
        bottomDivider = false,
        bottomBar = { EolssuButton(stringResource(Res.string.my_password_change_done), onClick = onSubmit) },
    ) {
        EolssuTextField(
            value = state.current,
            onValueChange = onCurrentChange,
            label = stringResource(Res.string.my_password_current),
            placeholder = stringResource(Res.string.my_password_current),
            type = TextFieldType.Password,
            bordered = false,
            error = state.currentError?.let { stringResource(it.message) },
        )
        EolssuTextField(
            value = state.new,
            onValueChange = onNewChange,
            label = stringResource(Res.string.my_password_new),
            placeholder = stringResource(Res.string.my_password_new),
            type = TextFieldType.Password,
            bordered = false,
            help = stringResource(Res.string.my_password_help),
            error = state.newError?.let { stringResource(it.message) },
        )
        EolssuTextField(
            value = state.confirm,
            onValueChange = onConfirmChange,
            label = stringResource(Res.string.my_password_confirm),
            placeholder = stringResource(Res.string.my_password_confirm_placeholder),
            type = TextFieldType.Password,
            bordered = false,
            error = state.confirmError?.let { stringResource(it.message) },
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
        )
    }
}
