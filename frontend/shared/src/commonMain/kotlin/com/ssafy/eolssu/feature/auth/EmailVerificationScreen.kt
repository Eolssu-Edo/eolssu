package com.ssafy.eolssu.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
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
import eolssu.shared.generated.resources.auth_error_login_unavailable
import eolssu.shared.generated.resources.auth_code
import eolssu.shared.generated.resources.auth_code_confirm
import eolssu.shared.generated.resources.auth_code_error_expired
import eolssu.shared.generated.resources.auth_code_error_invalid
import eolssu.shared.generated.resources.auth_code_error_mismatch
import eolssu.shared.generated.resources.auth_code_error_no_longer_valid
import eolssu.shared.generated.resources.auth_code_error_too_many
import eolssu.shared.generated.resources.auth_code_help
import eolssu.shared.generated.resources.auth_code_new
import eolssu.shared.generated.resources.auth_code_new_wait
import eolssu.shared.generated.resources.auth_code_placeholder
import eolssu.shared.generated.resources.auth_code_resend
import eolssu.shared.generated.resources.auth_code_resend_wait
import eolssu.shared.generated.resources.auth_code_send
import eolssu.shared.generated.resources.auth_code_sent
import eolssu.shared.generated.resources.auth_code_sent_reset
import eolssu.shared.generated.resources.auth_email
import eolssu.shared.generated.resources.auth_email_placeholder
import eolssu.shared.generated.resources.auth_reset_title
import eolssu.shared.generated.resources.auth_reset_verify_heading
import eolssu.shared.generated.resources.auth_signup_title
import eolssu.shared.generated.resources.auth_signup_verify_heading
import org.jetbrains.compose.resources.stringResource

/** 이메일 가입 단계 수: 1 이메일 인증 · 2 비밀번호 설정 · 3 프로필 설정. */
internal const val SignupStepCount = 3

/** 비밀번호 찾기 단계 수: 1 이메일 인증 · 2 새 비밀번호. */
internal const val PasswordResetStepCount = 2

internal val VerificationPurpose.stepCount: Int
    get() = if (this == VerificationPurpose.SignUp) SignupStepCount else PasswordResetStepCount

/** 남은 초를 `m:ss`로 쓴다. */
internal fun formatMinutesSeconds(seconds: Long): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/**
 * AUTH-06 이메일 인증(가입 1/3)·AUTH-10 비밀번호 찾기(1/2). 이메일을 넣고 '인증번호 받기'를 누르면 같은 화면에 6자리 칸과
 * 남은 시간(3분)이 나오고, 60초 뒤부터 다시 받을 수 있다. 만료되거나 5번 틀리면 '확인'을 막고 새 인증번호를 받게 한다.
 */
@Composable
fun EmailVerificationScreen(
    purpose: VerificationPurpose,
    state: EmailVerificationUiState,
    now: Long,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEmailChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onResendCode: () -> Unit,
    onConfirm: () -> Unit,
) {
    val signUp = purpose == VerificationPurpose.SignUp
    val needsNewCode = state.needsNewCode(now)
    AuthScaffold(
        authFrameOf(widthClass, split = true),
        modifier = Modifier.imePadding(),
        snackbarHostState = snackbarHostState,
        topBar = {
            Column {
                AuthTopBar(
                    stringResource(if (signUp) Res.string.auth_signup_title else Res.string.auth_reset_title),
                    onBack = onBack,
                    right = { AuthStepLabel(1, purpose.stepCount) },
                )
                AuthStepProgress(1, purpose.stepCount)
            }
        },
        bottomBar = {
            if (state.codeSent) {
                EolssuButton(stringResource(Res.string.auth_code_confirm), onClick = onConfirm, enabled = !needsNewCode)
            } else {
                EolssuButton(stringResource(Res.string.auth_code_send), onClick = onSendCode)
            }
        },
    ) {
        AuthBody(topPadding = authBodyTopBelowProgress()) {
            AuthHeading(stringResource(if (signUp) Res.string.auth_signup_verify_heading else Res.string.auth_reset_verify_heading))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                EolssuTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = stringResource(Res.string.auth_email),
                    placeholder = stringResource(Res.string.auth_email_placeholder),
                    type = TextFieldType.Email,
                    help = when {
                        !state.codeSent -> null
                        signUp -> stringResource(Res.string.auth_code_sent, state.email)
                        else -> stringResource(Res.string.auth_code_sent_reset)
                    },
                    error = state.emailError?.let { stringResource(it.message) },
                    imeAction = if (state.codeSent) ImeAction.Next else ImeAction.Done,
                    onImeAction = if (state.codeSent) null else onSendCode,
                )
                if (state.codeSent) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EolssuTextField(
                        value = state.code,
                        onValueChange = onCodeChange,
                        label = stringResource(Res.string.auth_code),
                        placeholder = stringResource(Res.string.auth_code_placeholder),
                        type = TextFieldType.Number,
                        help = stringResource(Res.string.auth_code_help),
                        error = state.codeErrorAt(now)?.let { codeErrorText(it) },
                        imeAction = ImeAction.Done,
                        onImeAction = onConfirm,
                        // 새 번호가 필요하면 남은 시간은 의미가 없어 숨긴다(서버가 먼저 무효로 답한 경우 시간이 남아 보인다).
                        trailing = if (needsNewCode) null else formatMinutesSeconds(state.secondsLeft(now)),
                    )
                    ResendRow(
                        // 재발송 간격(60초)은 새 번호가 필요할 때도 서버가 지키므로, 남은 시간을 보여 막힌 이유를 알린다.
                        label = when {
                            state.resendSecondsLeft(now) > 0 -> stringResource(
                                if (needsNewCode) Res.string.auth_code_new_wait else Res.string.auth_code_resend_wait,
                                formatMinutesSeconds(state.resendSecondsLeft(now)),
                            )
                            needsNewCode -> stringResource(Res.string.auth_code_new)
                            else -> stringResource(Res.string.auth_code_resend)
                        },
                        enabled = state.resendSecondsLeft(now) == 0L,
                        onClick = onResendCode,
                    )
                }
            }
        }
    }
}

@Composable
private fun codeErrorText(error: CodeError): String = when (error) {
    CodeError.Unavailable -> stringResource(Res.string.auth_error_login_unavailable)
    CodeError.Invalid -> stringResource(Res.string.auth_code_error_invalid)
    is CodeError.Mismatch -> stringResource(Res.string.auth_code_error_mismatch, error.remainingAttempts)
    CodeError.Expired -> stringResource(Res.string.auth_code_error_expired)
    CodeError.TooManyAttempts -> stringResource(Res.string.auth_code_error_too_many)
    CodeError.NoLongerValid -> stringResource(Res.string.auth_code_error_no_longer_valid)
}

/** 인증번호 칸 바로 아래 오른쪽의 다시 받기 버튼(`.au-resend`). 재발송 간격이 남았으면 남은 시간을 보이고 막는다. */
@Composable
private fun ResendRow(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    val style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = if (enabled) colors.background else colors.surface,
            contentColor = if (enabled) colors.text else colors.textMuted,
            border = BorderStroke(1.dp, colors.line),
        ) {
            Text(
                label,
                modifier = Modifier.heightIn(min = 40.dp).padding(horizontal = 12.dp, vertical = 11.dp),
                style = style.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}
