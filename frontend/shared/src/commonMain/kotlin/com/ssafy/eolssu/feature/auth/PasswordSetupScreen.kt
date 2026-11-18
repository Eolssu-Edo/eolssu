package com.ssafy.eolssu.feature.auth

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget
import com.ssafy.eolssu.core.diagnostics.QaKind

import com.ssafy.eolssu.core.model.InputLimit
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.ssafy.eolssu.core.ui.component.EolssuTextField
import com.ssafy.eolssu.core.ui.component.PasswordChecklist
import com.ssafy.eolssu.feature.auth.component.SignupTopBar
import com.ssafy.eolssu.feature.auth.component.authBodyTopBelowProgress
import com.ssafy.eolssu.feature.auth.component.authFrameOf
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_email_verified
import eolssu.shared.generated.resources.auth_new_password
import eolssu.shared.generated.resources.auth_next
import eolssu.shared.generated.resources.auth_password
import eolssu.shared.generated.resources.auth_password_confirm
import eolssu.shared.generated.resources.auth_password_confirm_placeholder
import eolssu.shared.generated.resources.auth_password_placeholder
import eolssu.shared.generated.resources.auth_reset_done
import eolssu.shared.generated.resources.auth_reset_password_heading
import eolssu.shared.generated.resources.auth_signup_password_heading
import org.jetbrains.compose.resources.stringResource

/** AUTH-08 비밀번호 설정(이메일 가입 3/4)·AUTH-11 새 비밀번호(비밀번호 찾기 2/2). 인증한 이메일과 비밀번호 규칙을 함께 보인다. */
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
        topBar = {
            val path = if (signUp) SignupPath.Email else SignupPath.PasswordReset
            SignupTopBar(SignupProgress(path, SignupStep.Password), onBack = onBack)
        },
        bottomBar = { EolssuButton(stringResource(if (signUp) Res.string.auth_next else Res.string.auth_reset_done), onClick = UserActionLog.click(QaTarget.PASSWORD_SETUP_SCREEN_01, onSubmit), busy = state.submitting, qaTarget = QaTarget.PASSWORD_SETUP_SCREEN_BUTTON_01) },
    ) {
        AuthBody(topPadding = authBodyTopBelowProgress()) {
            AuthHeading(stringResource(if (signUp) Res.string.auth_signup_password_heading else Res.string.auth_reset_password_heading))
            VerifiedEmail(email)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 규칙 미충족은 문장 대신 체크리스트로 보이므로, 입력칸 오류 문장은 그 밖의 오류(미입력·64자 초과·재인증)만 쓴다.
                    val ruleError = state.passwordError == FieldError.PasswordRule
                    EolssuTextField(qaTarget = QaTarget.PASSWORD_SETUP_SCREEN_02,
                        bordered = false,
                        labelInside = true,
                        value = state.password,
                        onValueChange = UserActionLog.change(QaTarget.PASSWORD_SETUP_SCREEN_02, onPasswordChange),
                        label = stringResource(if (signUp) Res.string.auth_password else Res.string.auth_new_password),
                        placeholder = stringResource(Res.string.auth_password_placeholder),
                        type = TextFieldType.Password,
                        maxLength = InputLimit.PASSWORD,
                        error = state.passwordError?.takeUnless { ruleError }?.let { stringResource(it.message) },
                    )
                    PasswordChecklist(state.password, highlightUnmet = ruleError)
                }
                EolssuTextField(qaTarget = QaTarget.PASSWORD_SETUP_SCREEN_03,
                    bordered = false,
                    labelInside = true,
                    value = state.passwordConfirm,
                    onValueChange = UserActionLog.change(QaTarget.PASSWORD_SETUP_SCREEN_03, onPasswordConfirmChange),
                    label = stringResource(Res.string.auth_password_confirm),
                    placeholder = stringResource(Res.string.auth_password_confirm_placeholder),
                    type = TextFieldType.Password,
                    maxLength = InputLimit.PASSWORD,
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
        FlowOnTapText(
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
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * 한 줄로 두고 넘치면 말줄임한다. 잘린 글자를 누르면 왼쪽으로 한 번 흘러 전체를 보여 준 뒤 말줄임으로 돌아온다(S15P31E102-972).
 * 흐르는 시간은 글자 폭과 [basicMarquee] 기본 속도(초당 30dp)·간격(칸 너비의 1/3)으로 셈한다.
 */
@Composable
private fun FlowOnTapText(text: AnnotatedString, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val velocity = with(LocalDensity.current) { 30.dp.toPx() }
    var cut by remember(text) { mutableStateOf(false) }
    var flowMillis by remember(text) { mutableLongStateOf(0L) }
    var flowing by remember(text) { mutableStateOf(false) }
    LaunchedEffect(flowing) {
        if (flowing) {
            delay(flowMillis)
            flowing = false
        }
    }
    Text(
        text,
        modifier = modifier
            .clickable(enabled = cut && !flowing, interactionSource = null, indication = null) { UserActionLog.event(QaKind.CLICK, QaTarget.PASSWORD_SETUP_SCREEN_04); flowing = true }
            .then(if (flowing) Modifier.basicMarquee(iterations = 1, initialDelayMillis = 0) else Modifier),
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = if (flowing) TextOverflow.Clip else TextOverflow.Ellipsis,
        onTextLayout = { layout ->
            if (!flowing) {
                cut = layout.hasVisualOverflow
                val travel = layout.multiParagraph.intrinsics.maxIntrinsicWidth + layout.size.width / 3f
                flowMillis = (travel / velocity * 1000).toLong()
            }
        },
    )
}
