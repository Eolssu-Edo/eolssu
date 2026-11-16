package com.ssafy.eolssu.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuTextField
import com.ssafy.eolssu.core.ui.component.ProviderWaiting
import com.ssafy.eolssu.core.ui.component.TextFieldType
import com.ssafy.eolssu.data.user.UserProfile
import com.ssafy.eolssu.data.user.signsInWithPassword
import com.ssafy.eolssu.feature.profile.component.ProfileScaffold
import com.ssafy.eolssu.feature.profile.component.WithdrawConfirmDialog
import androidx.compose.foundation.text.TextAutoSize
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.my_password_current
import eolssu.shared.generated.resources.my_withdraw
import eolssu.shared.generated.resources.my_withdraw_done
import eolssu.shared.generated.resources.my_withdraw_heading
import eolssu.shared.generated.resources.my_withdraw_notice_1
import eolssu.shared.generated.resources.my_withdraw_notice_2
import eolssu.shared.generated.resources.my_withdraw_notice_3
import eolssu.shared.generated.resources.my_withdraw_or
import eolssu.shared.generated.resources.my_withdraw_password_help
import eolssu.shared.generated.resources.my_withdraw_social
import eolssu.shared.generated.resources.my_withdraw_social_help
import eolssu.shared.generated.resources.social_auth_progress
import eolssu.shared.generated.resources.social_auth_reopen
import eolssu.shared.generated.resources.social_auth_waiting_body
import eolssu.shared.generated.resources.social_auth_waiting_title
import org.jetbrains.compose.resources.stringResource

/**
 * MY-04 회원 탈퇴. 30일 안내를 보이고 본인인지 다시 확인한다.
 * 비밀번호로 로그인하는 회원([signsInWithPassword])은 현재 비밀번호 칸을 두고, 간편 로그인이 연결돼 있으면 제공자 재인증 버튼도 둔다.
 * 비밀번호가 없는 소셜 가입 회원은 재인증으로만 탈퇴한다. 재인증 중에는 안내 대신 제공자 화면 대기 표시를 보인다.
 * 재인증 화면은 와이어프레임에 없어서 MY-04 보드의 안내·버튼 모양을 따른다.
 */
@Composable
fun WithdrawScreen(
    state: WithdrawUiState,
    profile: UserProfile,
    widthClass: WindowWidthClass,
    onBack: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onReauth: (SocialProvider) -> Unit,
    onReopen: () -> Unit,
    onConfirm: () -> Unit = {},
    onDismissConfirm: () -> Unit = {},
) {
    val colors = EolssuTheme.colors
    val reauth = state.reauth
    when (val confirm = state.confirm) {
        WithdrawConfirm.Password -> WithdrawConfirmDialog(stringResource(Res.string.my_withdraw_done), onConfirm, onDismissConfirm)
        is WithdrawConfirm.Reauth -> WithdrawConfirmDialog(
            stringResource(Res.string.my_withdraw_social, stringResource(confirm.provider.displayName)),
            onConfirm,
            onDismissConfirm,
        )
        null -> Unit
    }
    val usePassword = profile.signsInWithPassword
    val providers = SocialProvider.entries.filter { it in profile.linkedProviders }
    ProfileScaffold(
        widthClass,
        stringResource(Res.string.my_withdraw),
        onBack,
        bottomBar = {
            when {
                reauth?.step == WithdrawReauth.Step.InBrowser ->
                    EolssuButton(stringResource(Res.string.social_auth_reopen, stringResource(reauth.provider.displayName)), onClick = onReopen)
                reauth?.busy == true -> EolssuButton(stringResource(Res.string.social_auth_progress), onClick = {}, busy = true)
                usePassword -> EolssuButton(stringResource(Res.string.my_withdraw_done), onClick = onSubmit, style = EolssuButtonStyle.Danger)
                // 비밀번호가 없으면 재인증 버튼이 탈퇴하기 버튼 자리에 온다.
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    providers.forEach { provider ->
                        EolssuButton(
                            stringResource(Res.string.my_withdraw_social, stringResource(provider.displayName)),
                            onClick = { onReauth(provider) },
                            style = EolssuButtonStyle.Danger,
                        )
                    }
                }
            }
        },
    ) {
        if (reauth?.busy == true) {
            val name = stringResource(reauth.provider.displayName)
            val waiting = reauth.step == WithdrawReauth.Step.InBrowser
            // 연결(MY-05) 대기 화면과 같이 남은 화면 높이의 가운데에 둔다(S15P31E102-972).
            ProviderWaiting(
                reauth.provider,
                title = if (waiting) stringResource(Res.string.social_auth_waiting_title, name) else null,
                body = stringResource(if (waiting) Res.string.social_auth_waiting_body else Res.string.social_auth_progress),
                busy = true,
                modifier = Modifier.weight(1f),
            )
            return@ProfileScaffold
        }
        // 제목은 한 줄로 둔다. 좁은 휴대폰에서 넘치면 줄 대신 글자를 줄인다.
        Text(
            stringResource(Res.string.my_withdraw_heading),
            style = EolssuTheme.serif.headline.copy(fontSize = if (widthClass == WindowWidthClass.Compact) 24.sp else 28.sp, lineHeight = 34.sp),
            color = colors.text,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = if (widthClass == WindowWidthClass.Compact) 24.sp else 28.sp),
        )
        // 안내 목록(`.my-notice`): 흰 바탕 · 옅은 테두리에 점 목록
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(14.dp))
                .border(1.dp, colors.lineSoft, RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(Res.string.my_withdraw_notice_1, Res.string.my_withdraw_notice_2, Res.string.my_withdraw_notice_3).forEach { notice ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = colors.text)
                    Text(stringResource(notice), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp), color = colors.text)
                }
            }
        }
        if (usePassword) {
            EolssuTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = stringResource(Res.string.my_password_current),
                placeholder = stringResource(Res.string.my_password_current),
                type = TextFieldType.Password,
                help = stringResource(Res.string.my_withdraw_password_help),
                error = state.error?.takeIf { it != ProfileFieldError.ProofExpired }?.let { stringResource(it.message) },
                imeAction = ImeAction.Done,
                onImeAction = onSubmit,
            )
        }
        if (providers.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(if (usePassword) Res.string.my_withdraw_or else Res.string.my_withdraw_social_help),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.5.sp),
                    color = colors.textMuted,
                )
                if (usePassword) {
                    providers.forEach { provider ->
                        EolssuButton(
                            stringResource(Res.string.my_withdraw_social, stringResource(provider.displayName)),
                            onClick = { onReauth(provider) },
                            style = EolssuButtonStyle.Secondary,
                        )
                    }
                }
                // 재인증이 멈춘 이유나 증명 만료는 버튼 아래에 적는다. 다시 누르면 처음부터 한다.
                val problem = reauth?.problem?.let { stringResource(it.message, stringResource(reauth.provider.displayName)) }
                    ?: state.error?.takeIf { it == ProfileFieldError.ProofExpired }?.let { stringResource(it.message) }
                if (problem != null) {
                    Text(problem, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.5.sp), color = colors.recText)
                }
            }
        }
    }
}
