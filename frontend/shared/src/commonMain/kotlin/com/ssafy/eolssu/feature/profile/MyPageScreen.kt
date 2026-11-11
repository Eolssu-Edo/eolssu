package com.ssafy.eolssu.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.data.user.UserProfile
import com.ssafy.eolssu.data.user.PublishedLegalDocument
import com.ssafy.eolssu.feature.profile.component.ProfileScaffold
import com.ssafy.eolssu.feature.profile.component.LearningBadge
import com.ssafy.eolssu.feature.profile.component.MyPageAvatar
import com.ssafy.eolssu.feature.profile.component.MyPageCard
import com.ssafy.eolssu.feature.profile.component.MyPageCardTitle
import com.ssafy.eolssu.feature.profile.component.MyPageCardInset
import com.ssafy.eolssu.feature.profile.component.MyRecentPracticeCard
import com.ssafy.eolssu.feature.profile.component.MyPageFooter
import com.ssafy.eolssu.feature.profile.component.ProfileProviderLogo
import com.ssafy.eolssu.feature.profile.component.ProfileTextLink
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_cancel
import eolssu.shared.generated.resources.my_account
import eolssu.shared.generated.resources.my_inquiry
import eolssu.shared.generated.resources.my_link
import eolssu.shared.generated.resources.my_logout
import eolssu.shared.generated.resources.my_logout_confirm_message
import eolssu.shared.generated.resources.my_logout_confirm_title
import eolssu.shared.generated.resources.my_nickname_edit
import eolssu.shared.generated.resources.my_password_change
import eolssu.shared.generated.resources.my_social
import eolssu.shared.generated.resources.my_feedback_section
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
enum class MyPageTarget { Nickname, ProfileImage, Password, Withdraw, Sources, Records }

/**
 * MY-01 마이페이지. 프로필(닉네임 · 기초부터 배우기 진도 뱃지) · 최근 연주 돌아보기 · 간편 로그인 · 의견/문의 · 계정 ·
 * 푸터(자료 및 데이터 출처 · 법적 문서). 옅은 회색 바탕 위에 테두리 없는 흰 카드를 쌓고, 각 카드 안 첫 줄에 굵은 제목을 둔다(S15P31E102-731).
 */
@Composable
fun MyPageScreen(
    profile: UserProfile,
    providers: List<SocialProvider>,
    learningProgress: MyLearningProgress?,
    recentPractice: MyRecentPractice,
    recentStatus: MyRecentPracticeStatus,
    today: KoreanDateTime,
    dialog: MyPageDialog?,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (MyPageTarget) -> Unit,
    onLink: (SocialProvider) -> Unit,
    onUnlink: (SocialProvider) -> Unit,
    onConfirmUnlink: () -> Unit,
    onLogOut: () -> Unit,
    onConfirmLogOut: () -> Unit,
    onDismissDialog: () -> Unit,
    onOpenLegal: (PublishedLegalDocument) -> Unit,
    onOpenFeedback: () -> Unit,
) {
    ProfileScaffold(
        widthClass,
        stringResource(Res.string.my_title),
        onBack,
        snackbarHostState,
        gap = 12.dp,
        topPadding = 8.dp,
        containerColor = EolssuTheme.colors.groupedBackground,
    ) {
        ProfileCard(
            profile,
            learningProgress,
            onEdit = { onOpen(MyPageTarget.Nickname) },
            onEditImage = { onOpen(MyPageTarget.ProfileImage) },
        )
        MyRecentPracticeCard(recentPractice, recentStatus, today, onOpenRecords = { onOpen(MyPageTarget.Records) })
        // 이 플랫폼에서 연결할 수 있는 제공자와, 다른 기기에서 연결해 둔 제공자(해제만 가능)를 함께 보여 준다.
        val shownProviders = providers + profile.linkedProviders.filter { it !in providers }
        Section(stringResource(Res.string.my_social)) {
            shownProviders.forEach { provider ->
                val linked = provider in profile.linkedProviders
                ProviderRow(provider, linked, onClick = { if (linked) onUnlink(provider) else onLink(provider) })
            }
        }
        // 의견 폼(구글 폼) 하나로 문의와 의견을 함께 받는다.
        Section(stringResource(Res.string.my_feedback_section)) {
            AccountRow(stringResource(Res.string.my_inquiry), onClick = onOpenFeedback)
        }
        Section(stringResource(Res.string.my_account)) {
            AccountRow(stringResource(Res.string.my_password_change), onClick = { onOpen(MyPageTarget.Password) })
            AccountRow(stringResource(Res.string.my_logout), onClick = onLogOut)
        }
        MyPageFooter(onOpenSources = { onOpen(MyPageTarget.Sources) }, onOpenLegal = onOpenLegal)
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
            ProfileTextLink(
                stringResource(Res.string.my_withdraw),
                onClick = { onDismissDialog(); onOpen(MyPageTarget.Withdraw) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        null -> Unit
    }
}

/**
 * 프로필 머리: 카드 없이 회색 바탕 가운데에 얼굴 원 · 닉네임(옆에 연필 버튼) · 기초부터 배우기 진도 뱃지를 세로로 둔다.
 * 이메일은 보이지 않는다. 진도를 받지 못하면 뱃지를 비워 둔다.
 */
@Composable
private fun ProfileCard(profile: UserProfile, progress: MyLearningProgress?, onEdit: () -> Unit, onEditImage: () -> Unit) {
    val colors = EolssuTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MyPageAvatar(profile.profileImage, onEditImage)
        Row(
            Modifier.padding(top = 12.dp, start = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                profile.nickname,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                Icon(EolssuIcons.Pencil, contentDescription = stringResource(Res.string.my_nickname_edit), modifier = Modifier.size(18.dp), tint = colors.textMuted)
            }
        }
        if (progress != null) {
            Box(Modifier.padding(top = 2.dp)) { LearningBadge(progress) }
        }
    }
}

/** 카드 한 장: 첫 줄에 굵은 제목([MyPageCardTitle]), 그 아래 구분선 없이 줄들을 둔다. */
@Composable
private fun Section(title: String, rows: @Composable () -> Unit) {
    MyPageCard {
        Column(Modifier.padding(top = 20.dp, bottom = 8.dp)) {
            MyPageCardTitle(title, Modifier.padding(horizontal = MyPageCardInset).padding(bottom = 4.dp))
            rows()
        }
    }
}

/** 간편 로그인 한 줄: 공식 로고 · 이름 · 해제/연결 버튼. 연결 상태 문구는 반복하지 않는다. */
@Composable
private fun ProviderRow(provider: SocialProvider, linked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = MyPageCardInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileProviderLogo(provider)
        Text(
            stringResource(provider.displayName),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
            color = EolssuTheme.colors.text,
        )
        SmallButton(stringResource(if (linked) Res.string.my_unlink else Res.string.my_link), primary = !linked, onClick = onClick)
    }
}

/** 카드 안 한 줄: 글자 · 오른쪽 옅은 화살표. 줄 전체가 누르는 영역이다. */
@Composable
private fun AccountRow(text: String, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = MyPageCardInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = colors.text)
        Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textDisabled)
    }
}

/**
 * 작은 채움 버튼: 테두리 없는 높이 34dp 알약. '연결'([primary])은 홈 카드와 같은 먹초록 바탕에 흰 글자, '해제'는 회색 바탕에 회색 글자.
 * 누르는 영역은 Compose 최소 터치 영역(48dp)으로 넓어진다.
 */
@Composable
private fun SmallButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (primary) colors.cardInk else colors.groupedBackground,
        contentColor = if (primary) colors.onInk else colors.textSub,
    ) {
        Box(Modifier.heightIn(min = 34.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}
