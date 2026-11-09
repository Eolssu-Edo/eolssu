package com.ssafy.eolssu.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.ssafy.eolssu.feature.profile.component.MyPageIconTile
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
enum class MyPageTarget { Nickname, Password, Withdraw, Sources, Records }

/**
 * MY-01 마이페이지. 프로필(닉네임 · 기초부터 배우기 진도 뱃지) · 최근 연주 돌아보기 · 간편 로그인 · 의견/문의 · 계정 ·
 * 푸터(자료 및 데이터 출처 · 법적 문서). 카드·구분선 색은 홈 카드 격자와 같은 초록빛 톤을 쓴다.
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
    ProfileScaffold(widthClass, stringResource(Res.string.my_title), onBack, snackbarHostState, gap = 24.dp, topPadding = 8.dp) {
        ProfileCard(profile, learningProgress, onEdit = { onOpen(MyPageTarget.Nickname) })
        MyRecentPracticeCard(recentPractice, recentStatus, today, onOpenRecords = { onOpen(MyPageTarget.Records) })
        // 이 플랫폼에서 연결할 수 있는 제공자와, 다른 기기에서 연결해 둔 제공자(해제만 가능)를 함께 보여 준다.
        val shownProviders = providers + profile.linkedProviders.filter { it !in providers }
        Section(stringResource(Res.string.my_social)) {
            shownProviders.forEachIndexed { index, provider ->
                if (index > 0) RowDivider()
                val linked = provider in profile.linkedProviders
                ProviderRow(provider, linked, onClick = { if (linked) onUnlink(provider) else onLink(provider) })
            }
        }
        // 의견 폼(구글 폼) 하나로 문의와 의견을 함께 받는다.
        Section(stringResource(Res.string.my_feedback_section)) {
            AccountRow(EolssuIcons.Chat, stringResource(Res.string.my_inquiry), onClick = onOpenFeedback)
        }
        Section(stringResource(Res.string.my_account)) {
            AccountRow(EolssuIcons.Lock, stringResource(Res.string.my_password_change), onClick = { onOpen(MyPageTarget.Password) })
            RowDivider()
            AccountRow(EolssuIcons.LogOut, stringResource(Res.string.my_logout), onClick = onLogOut)
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
 * 프로필 칸([MyPageCard]): 얼굴 자리 · 닉네임과 기초부터 배우기 진도 뱃지 · 닉네임 수정. 세 요소를 한 줄 가운데에 맞춘다.
 * 이메일은 보이지 않는다. 진도를 받지 못하면 뱃지를 비워 둔다.
 */
@Composable
private fun ProfileCard(profile: UserProfile, progress: MyLearningProgress?, onEdit: () -> Unit) {
    val colors = EolssuTheme.colors
    MyPageCard {
        Row(
            Modifier.fillMaxWidth().padding(start = MyPageCardInset, end = 4.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MyPageAvatar()
            Row(
                Modifier.weight(1f).padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    profile.nickname,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = colors.cardInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (progress != null) LearningBadge(progress)
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                Icon(EolssuIcons.Pencil, contentDescription = stringResource(Res.string.my_nickname_edit), modifier = Modifier.size(20.dp), tint = colors.textMuted)
            }
        }
    }
}

/** 제목과 카드 한 장에 묶은 줄들. 제목은 카드 안 글자와 같은 [MyPageCardInset]에서 시작한다. */
@Composable
private fun Section(title: String, rows: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = MyPageCardInset),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = EolssuTheme.colors.textMuted,
        )
        MyPageCard { Column { rows() } }
    }
}

/** 카드 안 줄 사이 구분선: 카드 글자와 같은 안쪽 여백에서 시작하고 끝난다. */
@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(horizontal = MyPageCardInset), color = EolssuTheme.colors.sectionLine)
}

/** 간편 로그인 한 줄: 공식 로고 · 이름 · 해제/연결 버튼. 연결 상태 문구는 반복하지 않는다. */
@Composable
private fun ProviderRow(provider: SocialProvider, linked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = MyPageCardInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileProviderLogo(provider)
        Text(
            stringResource(provider.displayName),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = EolssuTheme.colors.cardInk,
        )
        SmallButton(stringResource(if (linked) Res.string.my_unlink else Res.string.my_link), onClick)
    }
}

/** 계정 한 줄: 아이콘 타일 · 글자 · 오른쪽 화살표. 줄 전체가 누르는 영역이다. */
@Composable
private fun AccountRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = MyPageCardInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MyPageIconTile(icon, size = 32.dp)
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = colors.cardInk)
        Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textMuted)
    }
}

/** 작은 테두리 버튼: 흰 바탕 · 초록빛 테두리 · 높이 36dp 알약 모양. 누르는 영역은 Compose 최소 터치 영역(48dp)으로 넓어진다. */
@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = colors.surface,
        contentColor = colors.cardInk,
        border = BorderStroke(1.dp, colors.cardLine),
    ) {
        Box(Modifier.heightIn(min = 36.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}
