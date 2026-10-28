package com.ssafy.eolssu.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.auth.component.AuthBody
import com.ssafy.eolssu.feature.auth.component.AuthFieldType
import com.ssafy.eolssu.feature.auth.component.AuthFrame
import com.ssafy.eolssu.feature.auth.component.AuthScaffold
import com.ssafy.eolssu.feature.auth.component.AuthTextField
import com.ssafy.eolssu.feature.auth.component.authBodyTopWithoutBar
import com.ssafy.eolssu.feature.auth.component.authFrameOf
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_brand
import eolssu.shared.generated.resources.auth_email
import eolssu.shared.generated.resources.auth_email_placeholder
import eolssu.shared.generated.resources.auth_find_password
import eolssu.shared.generated.resources.auth_confirm
import eolssu.shared.generated.resources.auth_locked_message
import eolssu.shared.generated.resources.auth_locked_title
import eolssu.shared.generated.resources.auth_login
import eolssu.shared.generated.resources.auth_or_email
import eolssu.shared.generated.resources.auth_password
import eolssu.shared.generated.resources.auth_password_placeholder
import eolssu.shared.generated.resources.auth_signup_with_email
import eolssu.shared.generated.resources.auth_social_login
import eolssu.shared.generated.resources.auth_social_members_only
import eolssu.shared.generated.resources.auth_tagline
import eolssu.shared.generated.resources.auth_withdrawal_cancelled_message
import eolssu.shared.generated.resources.auth_withdrawal_cancelled_title
import org.jetbrains.compose.resources.stringResource

/**
 * AUTH-04 로그인. 간편 로그인 버튼 3개(가입한 회원 전용), 이메일 로그인, 비밀번호 찾기·이메일 회원가입 링크.
 * 로그인 잠김·탈퇴 취소는 안내 창으로 알린다. 태블릿은 화면 가운데에 두고, 가로에서는 앱 이름을 왼쪽 브랜드 칸으로 옮긴다.
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
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
            gap = 24.dp,
            verticalAlignment = if (frame.isTablet) Alignment.CenterVertically else Alignment.Top,
        ) {
            if (frame != AuthFrame.Split) Brand()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialProvider.entries.forEach { provider -> SocialButton(provider, onClick = { onSocialLogIn(provider) }) }
                Text(
                    stringResource(Res.string.auth_social_members_only),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = EolssuTheme.colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
            OrDivider()
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AuthTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = stringResource(Res.string.auth_email),
                    placeholder = stringResource(Res.string.auth_email_placeholder),
                    type = AuthFieldType.Email,
                )
                AuthTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = stringResource(Res.string.auth_password),
                    placeholder = stringResource(Res.string.auth_password_placeholder),
                    type = AuthFieldType.Password,
                    error = state.error?.let { stringResource(it.message) },
                    imeAction = ImeAction.Done,
                    onImeAction = onLogIn,
                )
                EolssuButton(stringResource(Res.string.auth_login), onClick = onLogIn)
            }
            Links(onFindPassword, onSignUp)
        }
    }
    when (state.notice) {
        LoginNotice.Locked -> EolssuDialog(
            title = stringResource(Res.string.auth_locked_title),
            message = stringResource(Res.string.auth_locked_message),
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

/** 앱 이름과 한 줄 소개(`.au-brand`). */
@Composable
private fun Brand() {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(Res.string.auth_brand),
            style = EolssuTheme.serif.brand.copy(fontSize = 40.sp, lineHeight = 48.sp, letterSpacing = 0.sp),
            color = EolssuTheme.colors.text,
        )
        Text(
            stringResource(Res.string.auth_tagline),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = EolssuTheme.colors.textMuted,
        )
    }
}

/** 간편 로그인 버튼(`.au-social-btn`): 왼쪽 52dp 칸에 제공사 표시, 가운데 문구. 공식 버튼 가이드는 실제 연동 때 따른다. */
@Composable
private fun SocialButton(provider: SocialProvider, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = CircleShape,
        color = colors.background,
        contentColor = colors.text,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(52.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(24.dp).background(colors.surfaceStrong, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(provider.mark, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold))
                }
            }
            Text(
                stringResource(Res.string.auth_social_login, stringResource(provider.displayName)),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Box(Modifier.width(52.dp))
        }
    }
}

/** '또는 이메일로' 구분선(`.au-or`). */
@Composable
private fun OrDivider() {
    val colors = EolssuTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(Modifier.weight(1f), color = colors.line)
        Text(stringResource(Res.string.auth_or_email), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = colors.textMuted)
        HorizontalDivider(Modifier.weight(1f), color = colors.line)
    }
}

/** 비밀번호 찾기 | 이메일로 회원가입(`.au-links`). */
@Composable
private fun Links(onFindPassword: () -> Unit, onSignUp: () -> Unit) {
    val colors = EolssuTheme.colors
    val style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinkButton(stringResource(Res.string.auth_find_password), onFindPassword) {
            Text(it, style = style, color = colors.textMuted)
        }
        Text("|", style = style, color = colors.line)
        LinkButton(stringResource(Res.string.auth_signup_with_email), onSignUp) {
            Text(it, style = style.copy(fontWeight = FontWeight.Bold), color = colors.text)
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
