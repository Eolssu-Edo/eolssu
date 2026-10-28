package com.ssafy.eolssu.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.feature.auth.component.AuthBody
import com.ssafy.eolssu.feature.auth.component.AuthFieldType
import com.ssafy.eolssu.feature.auth.component.AuthHeading
import com.ssafy.eolssu.feature.auth.component.AuthScaffold
import com.ssafy.eolssu.feature.auth.component.AuthStepLabel
import com.ssafy.eolssu.feature.auth.component.AuthStepProgress
import com.ssafy.eolssu.feature.auth.component.AuthTextField
import com.ssafy.eolssu.feature.auth.component.AuthTopBar
import com.ssafy.eolssu.feature.auth.component.authBodyTopBelowProgress
import com.ssafy.eolssu.feature.auth.component.authFrameOf
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_email
import eolssu.shared.generated.resources.auth_email_placeholder
import eolssu.shared.generated.resources.auth_next
import eolssu.shared.generated.resources.auth_password
import eolssu.shared.generated.resources.auth_password_confirm
import eolssu.shared.generated.resources.auth_password_confirm_placeholder
import eolssu.shared.generated.resources.auth_password_help
import eolssu.shared.generated.resources.auth_password_placeholder
import eolssu.shared.generated.resources.auth_signup_heading
import eolssu.shared.generated.resources.auth_signup_title
import org.jetbrains.compose.resources.stringResource

/** 이메일 가입 단계 수. 1단계 이메일·비밀번호, 2단계 프로필. */
internal const val SignupStepCount = 2

/** AUTH-06 이메일 가입 1/2. */
@Composable
fun SignupScreen(
    state: SignupUiState,
    widthClass: WindowWidthClass,
    onBack: () -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordConfirmChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    AuthScaffold(
        authFrameOf(widthClass, split = true),
        modifier = Modifier.imePadding(),
        topBar = {
            Column {
                AuthTopBar(stringResource(Res.string.auth_signup_title), onBack = onBack, right = { AuthStepLabel(1, SignupStepCount) })
                AuthStepProgress(1, SignupStepCount)
            }
        },
        bottomBar = { EolssuButton(stringResource(Res.string.auth_next), onClick = onNext) },
    ) {
        AuthBody(topPadding = authBodyTopBelowProgress()) {
            AuthHeading(stringResource(Res.string.auth_signup_heading))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AuthTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = stringResource(Res.string.auth_email),
                    placeholder = stringResource(Res.string.auth_email_placeholder),
                    type = AuthFieldType.Email,
                    error = state.emailError?.let { stringResource(it.message) },
                )
                AuthTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = stringResource(Res.string.auth_password),
                    placeholder = stringResource(Res.string.auth_password_placeholder),
                    type = AuthFieldType.Password,
                    help = stringResource(Res.string.auth_password_help),
                    error = state.passwordError?.let { stringResource(it.message) },
                )
                AuthTextField(
                    value = state.passwordConfirm,
                    onValueChange = onPasswordConfirmChange,
                    label = stringResource(Res.string.auth_password_confirm),
                    placeholder = stringResource(Res.string.auth_password_confirm_placeholder),
                    type = AuthFieldType.Password,
                    error = state.passwordConfirmError?.let { stringResource(it.message) },
                    imeAction = ImeAction.Done,
                    onImeAction = onNext,
                )
            }
        }
    }
}
