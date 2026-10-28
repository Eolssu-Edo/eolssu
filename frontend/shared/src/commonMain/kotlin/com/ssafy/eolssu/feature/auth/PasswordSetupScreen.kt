package com.ssafy.eolssu.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.data.user.VerificationPurpose
import com.ssafy.eolssu.feature.auth.component.AuthBody
import com.ssafy.eolssu.core.ui.component.TextFieldType
import com.ssafy.eolssu.feature.auth.component.AuthHeading
import com.ssafy.eolssu.feature.auth.component.AuthScaffold
import com.ssafy.eolssu.feature.auth.component.AuthStepLabel
import com.ssafy.eolssu.feature.auth.component.AuthStepProgress
import com.ssafy.eolssu.core.ui.component.EolssuTextField
import com.ssafy.eolssu.feature.auth.component.AuthTopBar
import com.ssafy.eolssu.feature.auth.component.authBodyTopBelowProgress
import com.ssafy.eolssu.feature.auth.component.authFrameOf
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_email_verified
import eolssu.shared.generated.resources.auth_new_password
import eolssu.shared.generated.resources.auth_next
import eolssu.shared.generated.resources.auth_password
import eolssu.shared.generated.resources.auth_password_confirm
import eolssu.shared.generated.resources.auth_password_confirm_placeholder
import eolssu.shared.generated.resources.auth_password_help
import eolssu.shared.generated.resources.auth_password_placeholder
import eolssu.shared.generated.resources.auth_reset_done
import eolssu.shared.generated.resources.auth_reset_password_heading
import eolssu.shared.generated.resources.auth_reset_title
import eolssu.shared.generated.resources.auth_signup_password_heading
import eolssu.shared.generated.resources.auth_signup_title
import org.jetbrains.compose.resources.stringResource

/** AUTH-08 비밀번호 설정(가입 2/3)·AUTH-11 새 비밀번호(비밀번호 찾기 2/2). 인증한 이메일과 비밀번호 규칙을 함께 보인다. */
@Composable
fun PasswordSetupScreen(
    purpose: VerificationPurpose,
    email: String,
    state: PasswordSetupUiState,
    widthClass: WindowWidthClass,
    onBack: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordConfirmChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val signUp = purpose == VerificationPurpose.SignUp
    AuthScaffold(
        authFrameOf(widthClass, split = true),
        modifier = Modifier.imePadding(),
        topBar = {
            Column {
                AuthTopBar(
                    stringResource(if (signUp) Res.string.auth_signup_title else Res.string.auth_reset_title),
                    onBack = onBack,
                    right = { AuthStepLabel(2, purpose.stepCount) },
                )
                AuthStepProgress(2, purpose.stepCount)
            }
        },
        bottomBar = { EolssuButton(stringResource(if (signUp) Res.string.auth_next else Res.string.auth_reset_done), onClick = onSubmit) },
    ) {
        AuthBody(topPadding = authBodyTopBelowProgress()) {
            AuthHeading(stringResource(if (signUp) Res.string.auth_signup_password_heading else Res.string.auth_reset_password_heading))
            VerifiedEmail(email)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                EolssuTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = stringResource(if (signUp) Res.string.auth_password else Res.string.auth_new_password),
                    placeholder = stringResource(Res.string.auth_password_placeholder),
                    type = TextFieldType.Password,
                    help = stringResource(Res.string.auth_password_help),
                    error = state.passwordError?.let { stringResource(it.message) },
                )
                EolssuTextField(
                    value = state.passwordConfirm,
                    onValueChange = onPasswordConfirmChange,
                    label = stringResource(Res.string.auth_password_confirm),
                    placeholder = stringResource(Res.string.auth_password_confirm_placeholder),
                    type = TextFieldType.Password,
                    error = state.passwordConfirmError?.let { stringResource(it.message) },
                    imeAction = ImeAction.Done,
                    onImeAction = onSubmit,
                )
            }
        }
    }
}

/** 인증을 마친 이메일(`.au-verified`): 회색 바탕 줄에 체크와 굵은 이메일. */
@Composable
private fun VerifiedEmail(email: String) {
    val colors = EolssuTheme.colors
    val text = stringResource(Res.string.auth_email_verified, email)
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.text)
        Text(
            buildAnnotatedString {
                val start = text.indexOf(email)
                if (start < 0) {
                    append(text)
                } else {
                    append(text.substring(0, start))
                    withStyle(SpanStyle(color = colors.text, fontWeight = FontWeight.SemiBold)) { append(email) }
                    append(text.substring(start + email.length))
                }
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
            color = colors.textMuted,
        )
    }
}
