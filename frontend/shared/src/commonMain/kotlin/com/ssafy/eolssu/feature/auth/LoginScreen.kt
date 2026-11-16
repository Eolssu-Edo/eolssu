package com.ssafy.eolssu.feature.auth

import eolssu.shared.generated.resources.auth_withdrawn_title
import eolssu.shared.generated.resources.auth_withdrawn_message
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import eolssu.shared.generated.resources.auth_signup_link
import eolssu.shared.generated.resources.auth_social_divider
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
            // 이메일 로그인을 먼저, 간편 로그인은 아래 동그란 버튼 줄로 둔다(S15P31E102-798).
            Column(formWidth.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EolssuTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = stringResource(Res.string.auth_email),
                    placeholder = stringResource(Res.string.auth_email_placeholder),
                    type = TextFieldType.Email,
                    bordered = false,
                    labelInside = true,
                    fieldHeight = 48.dp,
                    error = state.emailError?.let { stringResource(it.message) },
                )
                EolssuTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = stringResource(Res.string.auth_password),
                    placeholder = stringResource(Res.string.auth_password_placeholder),
                    type = TextFieldType.Password,
                    bordered = false,
                    labelInside = true,
                    fieldHeight = 48.dp,
                    error = (state.passwordError ?: state.error)?.let { stringResource(it.message) },
                    imeAction = ImeAction.Done,
                    onImeAction = onLogIn,
                )
                LoginOptions(state, onRememberEmailChange, onKeepSignedInChange)
                EolssuButton(
                    stringResource(Res.string.auth_login),
                    onClick = onLogIn,
                    busy = state.loggingIn,
                )
            }
            Links(onFindPassword, onSignUp, formWidth)
            if (providers.isNotEmpty()) {
                SocialDivider(formWidth.padding(top = 8.dp))
                Row(formWidth, horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                    providers.forEach { provider ->
                        SocialButton(provider, enabled = !state.loggingIn, onClick = { onSocialLogIn(provider) })
                    }
                }
            }
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
        LoginNotice.Withdrawn -> EolssuDialog(
            title = stringResource(Res.string.auth_withdrawn_title),
            message = stringResource(Res.string.auth_withdrawn_message),
            icon = EolssuIcons.Check,
            onDismissRequest = onDismissNotice,
        ) {
            EolssuButton(stringResource(Res.string.auth_confirm), onClick = onDismissNotice)
        }
        null -> Unit
    }
}

/** 로그인 내용의 최대 너비. 넓은 화면에서도 입력칸·버튼이 길게 늘어나지 않게 한다. */
private val FormMaxWidth = 400.dp

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
 * 간편 로그인 버튼: 지름 [SocialSize] 동그라미 가운데 제공사 공식 심볼.
 * 카카오는 노랑, 네이버는 초록, Google은 흰 바탕, Apple은 검정 바탕이다(각 제공사 로그인 버튼 가이드의 심볼형 버튼).
 * 화면 읽기는 'OO로 로그인'을 읽는다(S15P31E102-798).
 */
@Composable
private fun SocialButton(provider: SocialProvider, enabled: Boolean, onClick: () -> Unit) {
    val look = socialLook(provider)
    val description = stringResource(Res.string.auth_social_login, stringResource(provider.displayName))
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(SocialSize).semantics { contentDescription = description },
        shape = CircleShape,
        color = look.background,
        contentColor = look.content,
    ) {
        Box(contentAlignment = Alignment.Center) {
            ProviderGlyph(provider, size = if (provider == SocialProvider.Naver) 20.dp else 26.dp, appleColor = look.content)
        }
    }
}

/** 간편 로그인 동그라미 지름. */
private val SocialSize = 56.dp

/** 이메일 로그인과 간편 로그인 사이의 '── 간편 로그인 ──' 구분 줄. */
@Composable
private fun SocialDivider(modifier: Modifier) {
    val colors = EolssuTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.lineSoft))
        Text(
            stringResource(Res.string.auth_social_divider),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = colors.textMuted,
        )
        Box(Modifier.weight(1f).height(1.dp).background(colors.lineSoft))
    }
}

/** 간편 로그인 버튼 색. */
private class SocialLook(val background: Color, val content: Color)

@Composable
private fun socialLook(provider: SocialProvider): SocialLook {
    return when (provider) {
        SocialProvider.Kakao -> SocialLook(KakaoYellow, KakaoInk)
        SocialProvider.Naver -> SocialLook(NaverGreen, Color.White)
        SocialProvider.Google -> SocialLook(Color.White, GoogleInk)
        // Apple 로그인 버튼 지침: 검정 바탕·흰 글자·흰 로고("Apple로 로그인").
        SocialProvider.Apple -> SocialLook(Color.Black, Color.White)
    }
}

private val KakaoYellow = Color(0xFFFEE500)
private val KakaoInk = Color(0xD9000000)
private val NaverGreen = Color(0xFF03C75A)
private val GoogleInk = Color(0xFF1F1F1F)

/**
 * 아이디 기억하기 · 자동 로그인 체크. 체크 칸은 작게 두고 누르는 영역은 44dp 높이를 지킨다.
 * 큰 글꼴·좁은 화면에서 두 개가 한 줄에 안 들어가면 글자를 쪼개지 않고 두 번째를 다음 줄로 내린다(S15P31E102-972).
 */
@Composable
private fun LoginOptions(state: LoginUiState, onRememberEmailChange: (Boolean) -> Unit, onKeepSignedInChange: (Boolean) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        LoginOption(stringResource(Res.string.auth_remember_email), state.rememberEmail, onRememberEmailChange)
        LoginOption(stringResource(Res.string.auth_keep_signed_in), state.keepSignedIn, onKeepSignedInChange)
    }
}

@Composable
private fun LoginOption(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AuthCheckBox(checked, label, onChange, Modifier.size(20.dp))
        // 글자는 누르지 않는다. 화면 읽기는 체크 상자에서 이름을 읽는다(S15P31E102-798).
        Text(label, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = EolssuTheme.colors.textSub)
    }
}

/**
 * 비밀번호 찾기 | 회원가입(`.au-links`). 두 링크는 같은 글자 크기·굵기·색이다.
 * 글자 길이가 달라도 '|'가 화면 가운데(간편 로그인 줄·가운데 버튼과 같은 축)에 오도록 양쪽을 같은 너비로 나눠 안쪽으로 붙인다(S15P31E102-798).
 */
@Composable
private fun Links(onFindPassword: () -> Unit, onSignUp: () -> Unit, modifier: Modifier) {
    val colors = EolssuTheme.colors
    val style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            LinkButton(stringResource(Res.string.auth_find_password), onFindPassword) {
                Text(it, style = style, color = colors.textMuted)
            }
        }
        Text("|", style = style, color = colors.lineSoft)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            LinkButton(stringResource(Res.string.auth_signup_link), onSignUp) {
                Text(it, style = style, color = colors.textMuted)
            }
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
