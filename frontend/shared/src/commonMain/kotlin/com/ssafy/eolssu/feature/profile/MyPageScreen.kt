package com.ssafy.eolssu.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.InstrumentPicker
import com.ssafy.eolssu.data.user.UserProfile
import com.ssafy.eolssu.data.user.PublishedLegalDocument
import eolssu.shared.generated.resources.legal_information
import eolssu.shared.generated.resources.legal_service
import eolssu.shared.generated.resources.legal_privacy
import eolssu.shared.generated.resources.legal_deletion
import com.ssafy.eolssu.feature.profile.component.ProfileScaffold
import com.ssafy.eolssu.core.ui.component.ProviderMark
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_cancel
import eolssu.shared.generated.resources.my_account
import eolssu.shared.generated.resources.my_instrument
import eolssu.shared.generated.resources.my_instrument_help
import eolssu.shared.generated.resources.my_instrument_saving
import eolssu.shared.generated.resources.my_link
import eolssu.shared.generated.resources.my_linked
import eolssu.shared.generated.resources.my_logout
import eolssu.shared.generated.resources.my_logout_confirm_message
import eolssu.shared.generated.resources.my_logout_confirm_title
import eolssu.shared.generated.resources.my_nickname_edit
import eolssu.shared.generated.resources.my_not_linked
import eolssu.shared.generated.resources.my_password_change
import eolssu.shared.generated.resources.my_social
import eolssu.shared.generated.resources.my_email_none
import eolssu.shared.generated.resources.my_social_help
import eolssu.shared.generated.resources.my_social_help_social
import eolssu.shared.generated.resources.my_title
import eolssu.shared.generated.resources.my_unlink
import eolssu.shared.generated.resources.my_unlink_confirm
import eolssu.shared.generated.resources.my_unlink_confirm_message
import eolssu.shared.generated.resources.my_unlink_confirm_message_social
import com.ssafy.eolssu.data.user.signsInWithPassword
import eolssu.shared.generated.resources.my_unlink_confirm_title
import eolssu.shared.generated.resources.my_withdraw
import org.jetbrains.compose.resources.stringResource

/** MY-01에서 여는 화면. */
enum class MyPageTarget { Nickname, Password, Withdraw }

/**
 * MY-01 마이페이지. 프로필(닉네임·이메일) · 연습 악기 · 간편 로그인 연결/해제 · 계정(비밀번호 변경·로그아웃·회원 탈퇴).
 * 연습 악기는 서버 저장 성공 후 홈과 곡 목록에도 반영된다.
 */
@Composable
fun MyPageScreen(
    profile: UserProfile,
    instrument: Instrument?,
    instrumentEnabled: Boolean,
    savingInstrument: Boolean,
    dialog: MyPageDialog?,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (MyPageTarget) -> Unit,
    onSelectInstrument: (Instrument) -> Unit,
    onLink: (SocialProvider) -> Unit,
    onUnlink: (SocialProvider) -> Unit,
    onConfirmUnlink: () -> Unit,
    onLogOut: () -> Unit,
    onConfirmLogOut: () -> Unit,
    onDismissDialog: () -> Unit,
    onOpenLegal: (PublishedLegalDocument) -> Unit,
) {
    ProfileScaffold(widthClass, stringResource(Res.string.my_title), onBack, snackbarHostState, gap = 28.dp, topPadding = 8.dp) {
        ProfileCard(profile, onEdit = { onOpen(MyPageTarget.Nickname) })
        Section(stringResource(Res.string.my_instrument)) {
            InstrumentPicker(selected = instrument, onSelect = onSelectInstrument, enabled = instrumentEnabled,
                help = stringResource(if (savingInstrument) Res.string.my_instrument_saving else Res.string.my_instrument_help))
        }
        Section(stringResource(Res.string.my_social)) {
            Column {
                HorizontalDivider(color = EolssuTheme.colors.lineSoft)
                SocialProvider.entries.forEach { provider ->
                    val linked = provider in profile.linkedProviders
                    ProviderRow(provider, linked, onClick = { if (linked) onUnlink(provider) else onLink(provider) })
                }
            }
            HelpText(stringResource(if (profile.signsInWithPassword) Res.string.my_social_help else Res.string.my_social_help_social))
        }
        Section(stringResource(Res.string.legal_information)) {
            Column {
                HorizontalDivider(color = EolssuTheme.colors.lineSoft)
                AccountRow(stringResource(Res.string.legal_service), onClick = { onOpenLegal(PublishedLegalDocument.Service) })
                AccountRow(stringResource(Res.string.legal_privacy), onClick = { onOpenLegal(PublishedLegalDocument.Privacy) })
                AccountRow(stringResource(Res.string.legal_deletion), onClick = { onOpenLegal(PublishedLegalDocument.Deletion) })
            }
        }
        Section(stringResource(Res.string.my_account)) {
            Column {
                HorizontalDivider(color = EolssuTheme.colors.lineSoft)
                AccountRow(stringResource(Res.string.my_password_change), onClick = { onOpen(MyPageTarget.Password) })
                AccountRow(stringResource(Res.string.my_logout), onClick = onLogOut)
                AccountRow(stringResource(Res.string.my_withdraw), onClick = { onOpen(MyPageTarget.Withdraw) }, muted = true)
            }
        }
    }
    when (dialog) {
        is MyPageDialog.Unlink -> {
            val name = stringResource(dialog.provider.displayName)
            EolssuDialog(
                title = stringResource(Res.string.my_unlink_confirm_title, name),
                message = stringResource(
                    if (profile.signsInWithPassword) Res.string.my_unlink_confirm_message else Res.string.my_unlink_confirm_message_social,
                    name,
                ),
            ) {
                EolssuButton(stringResource(Res.string.my_unlink_confirm), onClick = onConfirmUnlink)
                EolssuButton(stringResource(Res.string.common_cancel), onClick = onDismissDialog, style = EolssuButtonStyle.Text)
            }
        }
        MyPageDialog.LogOut -> EolssuDialog(
            title = stringResource(Res.string.my_logout_confirm_title),
            message = stringResource(Res.string.my_logout_confirm_message),
        ) {
            EolssuButton(stringResource(Res.string.my_logout), onClick = onConfirmLogOut)
            EolssuButton(stringResource(Res.string.common_cancel), onClick = onDismissDialog, style = EolssuButtonStyle.Text)
        }
        null -> Unit
    }
}

/** 프로필 칸(`.my-profile`): 흰 바탕 · 옅은 테두리에 얼굴 자리 · 닉네임·이메일 · 닉네임 수정. */
@Composable
private fun ProfileCard(profile: UserProfile, onEdit: () -> Unit) {
    val colors = EolssuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, colors.lineSoft, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(52.dp).background(colors.surface, CircleShape), contentAlignment = Alignment.Center) {
            Icon(EolssuIcons.User, contentDescription = null, modifier = Modifier.size(26.dp), tint = colors.text)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(profile.nickname, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = colors.text)
            // 이메일이 없는 소셜 가입 회원은 빈 칸 대신 가입 방식을 적는다. 닉네임이 비면(아직 못 읽음) 아무것도 적지 않는다.
            val email = profile.email.ifBlank { if (profile.nickname.isNotBlank()) stringResource(Res.string.my_email_none) else "" }
            if (email.isNotBlank()) Text(email, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = colors.textMuted)
        }
        SmallButton(stringResource(Res.string.my_nickname_edit), onEdit)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = EolssuTheme.colors.text)
        content()
    }
}

@Composable
private fun HelpText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), color = EolssuTheme.colors.textMuted)
}

/** 간편 로그인 한 줄(`.my-row`): 제공자 표시 · 이름·연결 상태 · 해제/연결 버튼. */
@Composable
private fun ProviderRow(provider: SocialProvider, linked: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProviderMark(provider)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(provider.displayName), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
                Text(
                    stringResource(if (linked) Res.string.my_linked else Res.string.my_not_linked),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = colors.textMuted,
                )
            }
            SmallButton(stringResource(if (linked) Res.string.my_unlink else Res.string.my_link), onClick)
        }
        HorizontalDivider(color = colors.lineSoft)
    }
}

/** 계정 한 줄(`.my-link`): 글자와 오른쪽 화살표. 회원 탈퇴는 회색([muted])이다. */
@Composable
private fun AccountRow(text: String, onClick: () -> Unit, muted: Boolean = false) {
    val colors = EolssuTheme.colors
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = if (muted) colors.textMuted else colors.text)
            Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textMuted)
        }
        HorizontalDivider(color = colors.lineSoft)
    }
}

/** 작은 테두리 버튼(`.btn.secondary.sm`): 흰 바탕, 높이 40dp, 모서리 12dp. */
@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        contentColor = colors.text,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Text(
            text,
            modifier = Modifier.heightIn(min = 40.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}
