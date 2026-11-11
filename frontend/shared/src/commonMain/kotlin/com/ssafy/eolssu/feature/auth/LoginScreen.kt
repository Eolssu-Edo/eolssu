package com.ssafy.eolssu.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuLogo
import com.ssafy.eolssu.core.ui.component.EolssuTextField
import com.ssafy.eolssu.core.ui.component.mountainRidges
import com.ssafy.eolssu.core.ui.component.ProviderGlyph
import com.ssafy.eolssu.core.ui.component.TextFieldType
import com.ssafy.eolssu.feature.auth.component.AuthBody
import com.ssafy.eolssu.feature.auth.component.AuthCheckBox
import com.ssafy.eolssu.feature.auth.component.AuthFrame
import com.ssafy.eolssu.feature.auth.component.AuthScaffold
import com.ssafy.eolssu.feature.auth.component.authBodyTopWithoutBar
import com.ssafy.eolssu.feature.auth.component.authFrameOf
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_confirm
import eolssu.shared.generated.resources.auth_email
import eolssu.shared.generated.resources.auth_email_placeholder
import eolssu.shared.generated.resources.auth_find_password
import eolssu.shared.generated.resources.auth_keep_signed_in
import eolssu.shared.generated.resources.auth_locked_message
import eolssu.shared.generated.resources.auth_locked_message_until
import eolssu.shared.generated.resources.auth_locked_title
import eolssu.shared.generated.resources.auth_login
import eolssu.shared.generated.resources.auth_password
import eolssu.shared.generated.resources.auth_password_placeholder
import eolssu.shared.generated.resources.auth_remember_email
import eolssu.shared.generated.resources.auth_signup_with_email
import eolssu.shared.generated.resources.auth_social_login
import eolssu.shared.generated.resources.auth_tagline
import eolssu.shared.generated.resources.auth_withdrawal_cancelled_message
import eolssu.shared.generated.resources.auth_withdrawal_cancelled_title
import org.jetbrains.compose.resources.stringResource

/**
 * AUTH-04 로그인. 간편 로그인 버튼, 이메일·비밀번호 입력, 아이디 기억하기·자동 로그인 체크, 비밀번호 찾기·이메일 회원가입 링크.
 * 로그인 잠김·탈퇴 취소는 안내 창으로 알린다. 내용은 [FormMaxWidth]까지만 넓어지고, 태블릿 가로에서는 앱 이름을 왼쪽 브랜드 칸으로 옮긴다.
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    providers: List<SocialProvider>,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRememberEmailChange: (Boolean) -> Unit,
    onKeepSignedInChange: (Boolean) -> Unit,
    onLogIn: () -> Unit,
    onSocialLogIn: (SocialProvider) -> Unit,
    onFindPassword: () -> Unit,
    onSignUp: () -> Unit,
    onDismissNotice: () -> Unit,
    onWithdrawalCancelledConfirmed: () -> Unit,
) {
    val frame = authFrameOf(widthClass, split = true)
    AuthScaffold(frame, modifier = Modifier.imePadding(), snackbarHostState = snackbarHostState) {
        AuthBody(
            topPadding = authBodyTopWithoutBar(),
            modifier = if (frame == AuthFrame.Compact) Modifier.mountainRidges(EolssuTheme.colors.radical) else Modifier,
            gap = 20.dp,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = if (frame.isTablet) Alignment.CenterVertically else Alignment.Top,
        ) {
            val formWidth = Modifier.widthIn(max = FormMaxWidth).fillMaxWidth()
            if (frame != AuthFrame.Split) Brand(formWidth)
            Column(formWidth, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                providers.forEach { provider ->
                    SocialButton(provider, enabled = !state.loggingIn, onClick = { onSocialLogIn(provider) })
                }
            }
            Column(formWidth.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EolssuTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = stringResource(Res.string.auth_email),
                    placeholder = stringResource(Res.string.auth_email_placeholder),
                    type = TextFieldType.Email,
                    error = state.emailError?.let { stringResource(it.message) },
                    fieldHeight = FieldHeight,
                )
                EolssuTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = stringResource(Res.string.auth_password),
                    placeholder = stringResource(Res.string.auth_password_placeholder),
                    type = TextFieldType.Password,
                    error = (state.passwordError ?: state.error)?.let { stringResource(it.message) },
                    imeAction = ImeAction.Done,
                    onImeAction = onLogIn,
                    fieldHeight = FieldHeight,
                )
                LoginOptions(state, onRememberEmailChange, onKeepSignedInChange)
                EolssuButton(
                    stringResource(Res.string.auth_login),
                    onClick = onLogIn,
                    busy = state.loggingIn,
                    minHeight = FieldHeight,
                )
            }
            Links(onFindPassword, onSignUp, formWidth)
        }
    }
    when (state.notice) {
        LoginNotice.Locked -> EolssuDialog(
            title = stringResource(Res.string.auth_locked_title),
            message = state.lockedUntil?.let { stringResource(Res.string.auth_locked_message_until, clockLabel(it)) }
                ?: stringResource(Res.string.auth_locked_message),
            icon = EolssuIcons.Lock,
        ) {
            EolssuButton(stringResource(Res.string.auth_confirm), onClick = onDismissNotice)
            EolssuButton(stringResource(Res.string.auth_find_password), onClick = onFindPassword, style = EolssuButtonStyle.Text)
        }
        LoginNotice.WithdrawalCancelled -> EolssuDialog(
            title = stringResource(Res.string.auth_withdrawal_cancelled_title),
            message = stringResource(Res.string.auth_withdrawal_cancelled_message),
            icon = EolssuIcons.Check,
        ) {
            EolssuButton(stringResource(Res.string.auth_confirm), onClick = onWithdrawalCancelledConfirmed)
        }
        null -> Unit
    }
}

/** 로그인 내용의 최대 너비. 넓은 화면에서도 입력칸·버튼이 길게 늘어나지 않게 한다. */
private val FormMaxWidth = 400.dp

/** 입력칸·로그인 버튼·간편 로그인 버튼의 높이. 48dp 터치 영역을 지키면서 기본(52dp)보다 낮춘다. */
private val FieldHeight = 48.dp

/** 앱 이름과 한 줄 소개(`.au-brand`). */
@Composable
private fun Brand(modifier: Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        EolssuLogo()
        Text(
            stringResource(Res.string.auth_tagline),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
            color = EolssuTheme.colors.textSub,
        )
    }
}

/**
 * 간편 로그인 버튼(`.au-social-btn`): 왼쪽 48dp 칸 가운데에 제공사 공식 심볼(20dp), 가운데 문구.
 * 카카오는 노랑, 네이버는 초록, Google은 흰 바탕·옅은 테두리, Apple은 검정 바탕이다(각 제공사 로그인 버튼 가이드).
 */
@Composable
private fun SocialButton(provider: SocialProvider, enabled: Boolean, onClick: () -> Unit) {
    val look = socialLook(provider)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(FieldHeight),
        shape = CircleShape,
        color = look.background,
        contentColor = look.content,
        border = BorderStroke(1.dp, look.border),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(SymbolSlot), contentAlignment = Alignment.Center) {
                ProviderGlyph(provider, size = if (provider == SocialProvider.Naver) 14.dp else 20.dp, appleColor = look.content)
            }
            Text(
                stringResource(Res.string.auth_social_login, stringResource(provider.displayName)),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Box(Modifier.width(SymbolSlot))
        }
    }
}

/** 심볼 칸 너비. 문구가 버튼 가운데에 오도록 오른쪽에도 같은 너비를 비운다. */
private val SymbolSlot = 48.dp

/** 간편 로그인 버튼 색. */
private class SocialLook(val background: Color, val content: Color, val border: Color)

@Composable
private fun socialLook(provider: SocialProvider): SocialLook {
    val colors = EolssuTheme.colors
    return when (provider) {
        SocialProvider.Kakao -> SocialLook(KakaoYellow, KakaoInk, KakaoYellow)
        SocialProvider.Naver -> SocialLook(NaverGreen, Color.White, NaverGreen)
        SocialProvider.Google -> SocialLook(Color.White, GoogleInk, colors.line)
        // Apple 로그인 버튼 지침: 검정 바탕·흰 글자·흰 로고("Apple로 로그인").
        SocialProvider.Apple -> SocialLook(Color.Black, Color.White, Color.Black)
    }
}

private val KakaoYellow = Color(0xFFFEE500)
private val KakaoInk = Color(0xD9000000)
private val NaverGreen = Color(0xFF03C75A)
private val GoogleInk = Color(0xFF1F1F1F)

/** 아이디 기억하기 · 자동 로그인 체크(한 줄). 체크 칸은 작게 두고 누르는 영역은 44dp 높이를 지킨다. */
@Composable
private fun LoginOptions(state: LoginUiState, onRememberEmailChange: (Boolean) -> Unit, onKeepSignedInChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LoginOption(stringResource(Res.string.auth_remember_email), state.rememberEmail, onRememberEmailChange)
        LoginOption(stringResource(Res.string.auth_keep_signed_in), state.keepSignedIn, onKeepSignedInChange)
    }
}

@Composable
private fun LoginOption(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AuthCheckBox(checked, Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = EolssuTheme.colors.textSub)
    }
}

/** 비밀번호 찾기 | 이메일로 회원가입(`.au-links`). 두 링크는 같은 글자 크기·굵기·색이다. */
@Composable
private fun Links(onFindPassword: () -> Unit, onSignUp: () -> Unit, modifier: Modifier) {
    val colors = EolssuTheme.colors
    val style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinkButton(stringResource(Res.string.auth_find_password), onFindPassword) {
            Text(it, style = style, color = colors.textMuted)
        }
        Text("|", style = style, color = colors.lineSoft)
        LinkButton(stringResource(Res.string.auth_signup_with_email), onSignUp) {
            Text(it, style = style, color = colors.textMuted)
        }
    }
}

@Composable
private fun LinkButton(text: String, onClick: () -> Unit, content: @Composable (String) -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { content(text) }
}
