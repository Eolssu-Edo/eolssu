package com.ssafy.eolssu.feature.profile.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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

/** 마이페이지 카드 안 글자의 좌우 여백. 카드 밖 섹션 제목·도움말도 이 여백에서 시작해 한 세로선에 맞춘다. */
internal val MyPageCardInset: Dp = 18.dp

/** 마이페이지 카드: 홈 카드 격자(`FeatureSection`)와 같은 흰 바탕 · 초록빛 테두리 · 모서리 13dp. */
@Composable
internal fun MyPageCard(onClick: (() -> Unit)? = null, enabled: Boolean = true, content: @Composable () -> Unit) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(13.dp)
    val border = BorderStroke(1.dp, colors.cardLine)
    if (onClick != null) {
        Surface(onClick = onClick, enabled = enabled, shape = shape, color = colors.surface, border = border, modifier = Modifier.fillMaxWidth(), content = content)
    } else {
        Surface(shape = shape, color = colors.surface, border = border, modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/** 줄 앞 아이콘 타일: 연두 10% 바탕 · 모서리 10dp 안에 짙은 연두 선 아이콘. 옆 글자가 뜻을 읽으므로 장식으로 둔다. */
@Composable
internal fun MyPageIconTile(icon: ImageVector, size: Dp = 36.dp) {
    val colors = EolssuTheme.colors
    Box(Modifier.size(size).background(colors.accentSoft, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(size / 2), tint = colors.play)
    }
}

/** 프로필 얼굴 자리: 홈 '기초 배우기' 카드의 호랑이 얼굴을 연두 10% 원 안에 둔다. */
@Composable
internal fun MyPageAvatar() {
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(EolssuTheme.colors.accentSoft).border(1.dp, EolssuTheme.colors.accentTintLine, CircleShape),
        contentAlignment = Alignment.TopCenter,
    ) {
        // 홈과 공유하는 전신 그림에서 얼굴과 갓이 원형 테두리 안에 들어오게 배치한다.
        Image(
            painterResource(Res.drawable.home_tiger),
            contentDescription = null,
            modifier = Modifier.requiredSize(74.dp).offset(x = 5.dp, y = 13.dp),
            alignment = Alignment.TopCenter,
        )
    }
}

/** 닉네임 옆 '기초부터 배우기' 진도 뱃지: 연두 20% 바탕 알약에 다음에 배울 단계(모두 마치면 완료)를 적는다. */
@Composable
internal fun LearningBadge(progress: MyLearningProgress) {
    val colors = EolssuTheme.colors
    val stage = progress.nextStage
    val text = if (stage != null) stringResource(Res.string.my_learning_badge, stage) else stringResource(Res.string.my_learning_badge_done)
    val label = stringResource(Res.string.my_learning_badge_label, text)
    Row(
        Modifier
            .clearAndSetSemantics { contentDescription = label }
            .background(colors.accentTint, RoundedCornerShape(50))
            .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(EolssuIcons.Book, contentDescription = null, modifier = Modifier.size(13.dp), tint = colors.accentText)
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
            color = colors.accentText,
            maxLines = 1,
        )
    }
}

/**
 * '최근 연주 돌아보기' 카드([MyPageCard]). 제목 줄 오른쪽에 마지막 연주 날짜를 두고, 그 아래 왼쪽에 곡 이름과 바로 옆
 * 연주 범위 태그, 그 밑에 이번 주 연습 횟수를, 오른쪽에 점수 뱃지와 조금 띄운 화살표를 둔다. 카드 전체를 누르면 연습 기록(RECORD-01)으로 간다.
 * 기록이 없거나 받지 못하면 제목 아래에 그 상태를 한 줄로 적는다.
 */
@Composable
internal fun MyRecentPracticeCard(recent: MyRecentPractice, status: MyRecentPracticeStatus, today: KoreanDateTime, onOpenRecords: () -> Unit) {
    val colors = EolssuTheme.colors
    val latest = recent.latest
    val shown = status == MyRecentPracticeStatus.Shown && latest != null
    MyPageCard(onClick = onOpenRecords, enabled = status != MyRecentPracticeStatus.Unavailable) {
        Column(Modifier.padding(start = MyPageCardInset, end = if (shown) 12.dp else MyPageCardInset, top = 16.dp, bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.my_recent_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.textMuted,
                )
                if (shown) {
                    val day = if (latest!!.recordedAt.isSameDay(today)) stringResource(Res.string.records_today) else monthDayLabel(latest.recordedAt)
                    Text(day, modifier = Modifier.padding(end = 6.dp), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = colors.textMuted)
                } else if (status != MyRecentPracticeStatus.Unavailable) {
                    Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textMuted)
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
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                latest!!.songTitle,
                                modifier = Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                                color = colors.cardInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val kind = latest.section?.let { stringResource(Res.string.records_section, it) } ?: stringResource(Res.string.records_whole)
                            Text(
                                kind,
                                modifier = Modifier
                                    .border(1.dp, colors.cardLine, RoundedCornerShape(50))
                                    .padding(horizontal = 9.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
                                color = colors.cardInk,
                                maxLines = 1,
                            )
                        }
                        if (recent.weekCount > 0) {
                            Text(stringResource(Res.string.my_recent_meta, recent.weekCount), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
                        }
                    }
                    ScoreBadge(latest.score)
                    Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.padding(start = 12.dp).size(18.dp), tint = colors.textMuted)
                }
            }
        }
    }
}

/** 점수 뱃지: 연두 20% 바탕 둥근 칸에 큰 숫자와 같은 기준선의 작은 '점'. 화면 낭독은 "85점"처럼 한 덩어리로 읽는다. */
@Composable
private fun ScoreBadge(score: Int) {
    val colors = EolssuTheme.colors
    val label = stringResource(Res.string.my_recent_score, score)
    Row(
        Modifier
            .padding(start = 12.dp)
            .clearAndSetSemantics { contentDescription = label }
            .background(colors.accentTint, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            score.toString(),
            modifier = Modifier.alignByBaseline(),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
            color = colors.accentText,
        )
        Text(
            stringResource(Res.string.my_recent_score_unit),
            modifier = Modifier.alignByBaseline().padding(start = 2.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
            color = colors.accentText,
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
        modifier = Modifier.size(32.dp),
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
 * 마이페이지 맨 아래 푸터: 본문과 구분선으로 나누고, '자료 및 데이터 출처'와 법적 문서 링크를 작은 글씨 · 차분한 색으로 둔다.
 */
@Composable
internal fun MyPageFooter(onOpenSources: () -> Unit, onOpenLegal: (PublishedLegalDocument) -> Unit) {
    val colors = EolssuTheme.colors
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(horizontal = MyPageCardInset), color = colors.sectionLine)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onOpenSources).padding(horizontal = MyPageCardInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.my_sources),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = colors.textMuted,
            )
            Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textMuted)
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            ProfileTextLink(stringResource(Res.string.legal_service), { onOpenLegal(PublishedLegalDocument.Service) })
            ProfileTextLink(stringResource(Res.string.legal_privacy), { onOpenLegal(PublishedLegalDocument.Privacy) })
            ProfileTextLink(stringResource(Res.string.legal_deletion), { onOpenLegal(PublishedLegalDocument.Deletion) })
        }
    }
}
