package com.ssafy.eolssu.feature.profile.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.data.user.PublishedLegalDocument
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.ui.component.monthDayLabel
import com.ssafy.eolssu.feature.profile.MyLearningProgress
import com.ssafy.eolssu.feature.profile.MyRecentPractice
import com.ssafy.eolssu.feature.profile.MyRecentPracticeStatus
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** 마이페이지 카드 안 글자의 좌우 여백. 카드 안 제목·줄·구분선이 모두 이 여백에서 시작한다. */
internal val MyPageCardInset: Dp = 20.dp

/**
 * 마이페이지 카드: 회색 바탕(`groupedBackground`) 위의 테두리 없는 흰 카드 · 모서리 20dp.
 * 선 대신 바탕과의 명도 차이로 묶음을 나눈다(S15P31E102-731).
 */
@Composable
internal fun MyPageCard(onClick: (() -> Unit)? = null, enabled: Boolean = true, content: @Composable () -> Unit) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(20.dp)
    if (onClick != null) {
        Surface(onClick = onClick, enabled = enabled, shape = shape, color = colors.surface, modifier = Modifier.fillMaxWidth(), content = content)
    } else {
        Surface(shape = shape, color = colors.surface, modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/** 카드 안 첫 줄 제목: 진한 16sp 굵은 글씨. 오른쪽에 [trailing](날짜 등)을 둘 수 있다. */
@Composable
internal fun MyPageCardTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
            color = EolssuTheme.colors.text,
        )
        trailing()
    }
}

/** 프로필 얼굴 자리: 홈 '기초 배우기' 카드의 호랑이 얼굴을 흰 원 안에 둔다. 테두리 없이 회색 바탕과의 명도 차이로 원을 드러낸다. */
@Composable
internal fun MyPageAvatar() {
    Box(
        Modifier.size(84.dp).clip(CircleShape).background(EolssuTheme.colors.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        // 홈과 공유하는 전신 그림에서 얼굴과 갓이 원 안에 들어오게 배치한다(56dp 원의 74dp·5,13 배치를 같은 비율로 키움).
        Image(
            painterResource(Res.drawable.home_tiger),
            contentDescription = null,
            modifier = Modifier.requiredSize(111.dp).offset(x = 7.5.dp, y = 19.5.dp),
            alignment = Alignment.TopCenter,
        )
    }
}

/** 닉네임 아래 '기초부터 배우기' 진도 뱃지: 흰 알약에 짙은 연두 글자로 다음에 배울 단계(모두 마치면 완료)를 적는다. */
@Composable
internal fun LearningBadge(progress: MyLearningProgress) {
    val colors = EolssuTheme.colors
    val stage = progress.nextStage
    val text = if (stage != null) stringResource(Res.string.my_learning_badge, stage) else stringResource(Res.string.my_learning_badge_done)
    val label = stringResource(Res.string.my_learning_badge_label, text)
    Row(
        Modifier
            .clearAndSetSemantics { contentDescription = label }
            .background(colors.surface, RoundedCornerShape(50))
            .padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(EolssuIcons.Book, contentDescription = null, modifier = Modifier.size(13.dp), tint = colors.play)
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
            color = colors.accentText,
            maxLines = 1,
        )
    }
}

/**
 * '최근 연주 돌아보기' 카드([MyPageCard]). 굵은 제목 오른쪽에 마지막 연주 날짜를 두고, 그 아래 왼쪽에 곡 이름과
 * 연주 범위·이번 주 연습 횟수를, 오른쪽에 큰 점수 숫자와 화살표를 둔다. 카드 전체를 누르면 연습 기록(RECORD-01)으로 간다.
 * 기록이 없거나 받지 못하면 제목 아래에 그 상태를 한 줄로 적는다.
 */
@Composable
internal fun MyRecentPracticeCard(recent: MyRecentPractice, status: MyRecentPracticeStatus, today: KoreanDateTime, onOpenRecords: () -> Unit) {
    val colors = EolssuTheme.colors
    val latest = recent.latest
    val shown = status == MyRecentPracticeStatus.Shown && latest != null
    MyPageCard(onClick = onOpenRecords, enabled = status != MyRecentPracticeStatus.Unavailable) {
        Column(Modifier.padding(horizontal = MyPageCardInset, vertical = 20.dp)) {
            MyPageCardTitle(stringResource(Res.string.my_recent_title)) {
                if (shown) {
                    val day = if (latest!!.recordedAt.isSameDay(today)) stringResource(Res.string.records_today) else monthDayLabel(latest.recordedAt)
                    Text(day, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = colors.textMuted)
                } else if (status != MyRecentPracticeStatus.Unavailable) {
                    Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textDisabled)
                }
            }
            if (!shown) {
                Text(
                    stringResource(when (status) {
                        MyRecentPracticeStatus.Loading -> Res.string.my_recent_loading
                        MyRecentPracticeStatus.Failed -> Res.string.records_load_failed
                        MyRecentPracticeStatus.Unavailable -> Res.string.records_unavailable
                        else -> Res.string.my_recent_empty
                    }),
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = colors.textMuted,
                )
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            latest!!.songTitle,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val kind = latest.section?.let { stringResource(Res.string.records_section, it) } ?: stringResource(Res.string.records_whole)
                        val week = if (recent.weekCount > 0) stringResource(Res.string.my_recent_meta, recent.weekCount) else null
                        Text(
                            if (week != null) "$kind · $week" else kind,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    ScoreValue(latest.score)
                    Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.padding(start = 8.dp).size(16.dp), tint = colors.textDisabled)
                }
            }
        }
    }
}

/** 점수: 바탕 없이 큰 짙은 연두 숫자와 같은 기준선의 작은 '점'. 화면 낭독은 "85점"처럼 한 덩어리로 읽는다. */
@Composable
private fun ScoreValue(score: Int) {
    val colors = EolssuTheme.colors
    val label = stringResource(Res.string.my_recent_score, score)
    Row(Modifier.padding(start = 12.dp).clearAndSetSemantics { contentDescription = label }) {
        Text(
            score.toString(),
            modifier = Modifier.alignByBaseline(),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
            color = colors.play,
        )
        Text(
            stringResource(Res.string.my_recent_score_unit),
            modifier = Modifier.alignByBaseline().padding(start = 2.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
            color = colors.play,
        )
    }
}

/** 제공사 원본 로고. 이름은 옆 문구가 읽으므로 이미지는 장식으로 둔다. */
@Composable
internal fun ProfileProviderLogo(provider: SocialProvider) {
    Image(
        painterResource(when (provider) {
            SocialProvider.Kakao -> Res.drawable.profile_kakao_logo
            SocialProvider.Naver -> Res.drawable.profile_naver_logo
            SocialProvider.Google -> Res.drawable.profile_google_logo
            SocialProvider.Apple -> Res.drawable.profile_apple_logo
        }),
        contentDescription = null,
        modifier = Modifier.size(28.dp),
    )
}

/** 글자는 작게 두고 누르는 영역은 48dp 이상 유지한다. */
@Composable
internal fun ProfileTextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, textDecoration = TextDecoration.Underline), color = EolssuTheme.colors.textMuted)
    }
}

/**
 * 마이페이지 맨 아래 푸터: 구분선 없이 회색 바탕 위에 '자료 및 데이터 출처'와 법적 문서(서비스 이용약관 · 개인정보 처리방침 ·
 * 계정·데이터 삭제 안내)를 같은 모양의 줄(작은 회색 글씨 · 옅은 화살표)로 쌓는다. 모두 앱 안 화면으로 연다(S15P31E102-731).
 */
@Composable
internal fun MyPageFooter(onOpenSources: () -> Unit, onOpenLegal: (PublishedLegalDocument) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        FooterRow(stringResource(Res.string.my_sources), onOpenSources)
        FooterRow(stringResource(Res.string.legal_service)) { onOpenLegal(PublishedLegalDocument.Service) }
        FooterRow(stringResource(Res.string.legal_privacy)) { onOpenLegal(PublishedLegalDocument.Privacy) }
        FooterRow(stringResource(Res.string.legal_deletion)) { onOpenLegal(PublishedLegalDocument.Deletion) }
    }
}

/** 푸터 한 줄: 누르는 영역 48dp 이상. */
@Composable
private fun FooterRow(text: String, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MyPageCardInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = colors.textMuted)
        Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.textDisabled)
    }
}
