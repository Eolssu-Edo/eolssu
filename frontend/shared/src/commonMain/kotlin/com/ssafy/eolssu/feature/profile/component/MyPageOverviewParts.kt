package com.ssafy.eolssu.feature.profile.component

import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.indication
import androidx.compose.foundation.Image
import com.ssafy.eolssu.core.model.Instrument
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.role
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.border
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
import com.ssafy.eolssu.core.ui.component.InstrumentPicture
import com.ssafy.eolssu.core.ui.component.ProfileImageCircle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.data.user.PublishedLegalDocument
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.ui.component.monthDayLabel
import com.ssafy.eolssu.data.practice.RecentPracticeSummary
import com.ssafy.eolssu.feature.profile.MyLearningProgress
import com.ssafy.eolssu.feature.profile.MyRecentPracticeStatus
import eolssu.shared.generated.resources.*
import com.ssafy.eolssu.core.model.ProfileImage
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** 마이페이지 카드 안 글자의 좌우 여백. 카드 안 제목·줄·구분선이 모두 이 여백에서 시작한다. */
internal val MyPageCardInset: Dp = 20.dp

/**
 * 마이페이지 카드: 옅은 회색 바탕(`groupedBackground`) 위의 흰 카드 · 모서리 20dp.
 * 테두리 없이 바탕과의 명도 차이로 묶음을 나눈다(S15P31E102-759).
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

/** 절쑤 그림 12종의 이름(고르기 화면·화면 낭독). */
internal val ProfileImage.label: StringResource
    get() = when (this) {
        ProfileImage.Hello -> Res.string.my_profile_image_hello
        ProfileImage.Danso -> Res.string.my_profile_image_danso
        ProfileImage.Study -> Res.string.my_profile_image_study
        ProfileImage.Look -> Res.string.my_profile_image_look
        ProfileImage.Great -> Res.string.my_profile_image_great
        ProfileImage.Jangdan -> Res.string.my_profile_image_jangdan
        ProfileImage.Again -> Res.string.my_profile_image_again
        ProfileImage.Surprise -> Res.string.my_profile_image_surprise
        ProfileImage.Proud -> Res.string.my_profile_image_proud
        ProfileImage.Okay -> Res.string.my_profile_image_okay
        ProfileImage.Rest -> Res.string.my_profile_image_rest
        ProfileImage.Done -> Res.string.my_profile_image_done
    }

/**
 * 마이페이지 얼굴 원(84dp): 고른 절쑤 그림 오른쪽 아래에 작은 연필 뱃지를 두고, 원 전체를 누르면 [onEdit]으로 그림 고르기를 연다.
 * 뱃지는 바탕색 테두리로 원과 떨어져 보이게 한다.
 */
@Composable
internal fun MyPageAvatar(image: ProfileImage, onEdit: () -> Unit) {
    val colors = EolssuTheme.colors
    val description = stringResource(Res.string.my_profile_image_edit)
    // 원과 뱃지는 버튼 하나다. 화면 읽기·웹 키보드도 이 버튼 하나로 그림 고르기를 열고,
    // 어디를 눌러도 같은 눌림 효과가 원 안에만 보인다(S15P31E102-969).
    val interaction = remember { MutableInteractionSource() }
    // 부모 칸이 좁아도 타원이 되지 않게 정사각형을 강제한다(S15P31E102-768).
    Box(
        Modifier
            .requiredSize(84.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onEdit)
            .clearAndSetSemantics { contentDescription = description; role = Role.Button; onClick { onEdit(); true } },
    ) {
        Box(Modifier.matchParentSize().clip(CircleShape).indication(interaction, ripple())) {
            ProfileImageCircle(image, 84.dp)
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 2.dp)
                .size(30.dp)
                .background(colors.groupedBackground, CircleShape)
                .padding(2.dp)
                .clip(CircleShape)
                .background(colors.cardInk),
            contentAlignment = Alignment.Center,
        ) {
            Icon(EolssuIcons.Pencil, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.onInk)
        }
    }
}

/** 닉네임 아래 '기초부터 배우기' 진도 뱃지: 흰 알약에 가장 최근에 배운 악기 그림(기초부터 배우기와 같은 그림, S15P31E102-768)과 먹초록 글자로 다음에 배울 단계(모두 마치면 완료)를 적는다. */
@Composable
internal fun LearningBadge(progress: MyLearningProgress) {
    val colors = EolssuTheme.colors
    val stage = progress.nextStage
    val text = if (stage != null) stringResource(Res.string.my_learning_badge, stage) else stringResource(Res.string.my_learning_badge_done)
    val label = stringResource(Res.string.my_learning_badge_label, stringResource(progress.instrument.label), text)
    Row(
        Modifier
            .clearAndSetSemantics { contentDescription = label }
            .background(colors.surface, RoundedCornerShape(50))
            .padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        InstrumentPicture(progress.instrument, size = 22.dp)
        FlowOnTapText(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
            color = colors.cardInk,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * 한 줄로 두고 넘치면 말줄임한다. 잘린 글자를 누르면 왼쪽으로 한 번 흘러 전체를 보여 준 뒤 말줄임으로 돌아온다(S15P31E102-972).
 * 흐르는 시간은 글자 폭과 [basicMarquee] 기본 속도(초당 30dp)·간격(칸 너비의 1/3)으로 셈한다.
 */
@Composable
private fun FlowOnTapText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
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
            .clickable(enabled = cut && !flowing, interactionSource = null, indication = null) { flowing = true }
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

/**
 * '최근 연주 돌아보기' 카드([MyPageCard]). 굵은 제목 오른쪽에 마지막 연주 날짜를 두고, 그 아래 왼쪽에 곡 이름과
 * 연주 범위·이번 주 연습 횟수를, 오른쪽에 큰 점수 숫자와 화살표를 둔다. 카드 전체를 누르면 연습 기록(RECORD-01)으로 간다.
 * 기록이 없거나 받지 못하면 제목 아래에 그 상태를 한 줄로 적는다.
 */
@Composable
internal fun MyRecentPracticeCard(recent: RecentPracticeSummary?, status: MyRecentPracticeStatus, today: KoreanDateTime, onOpenRecords: () -> Unit) {
    val colors = EolssuTheme.colors
    val latest = recent?.latest
    val weekCount = recent?.week?.recordCount ?: 0
    val shown = status == MyRecentPracticeStatus.Shown && latest != null
    MyPageCard(onClick = onOpenRecords, enabled = status != MyRecentPracticeStatus.Unavailable) {
        Column(Modifier.padding(horizontal = MyPageCardInset, vertical = 20.dp)) {
            MyPageCardTitle(stringResource(Res.string.my_recent_title)) {
                if (status != MyRecentPracticeStatus.Unavailable) {
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
                            latest.songTitle,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val day = if (latest.recordedAt.isSameDay(today)) stringResource(Res.string.records_today) else monthDayLabel(latest.recordedAt)
                        val kind = latest.section?.let { stringResource(Res.string.records_section, it) } ?: stringResource(Res.string.records_whole)
                        val week = if (weekCount > 0) stringResource(Res.string.my_recent_meta, weekCount) else null
                        Text(
                            listOfNotNull(day, kind, week).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    ScoreValue(latest.score)
                }
            }
        }
    }
}

/** 점수: 바탕 없이 큰 먹초록 숫자와 같은 기준선의 작은 '점'. 화면 낭독은 "85점"처럼 한 덩어리로 읽는다. */
@Composable
private fun ScoreValue(score: Int) {
    val colors = EolssuTheme.colors
    val label = stringResource(Res.string.my_recent_score, score)
    Row(Modifier.padding(start = 12.dp).clearAndSetSemantics { contentDescription = label }) {
        Text(
            score.toString(),
            modifier = Modifier.alignByBaseline(),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                // 숫자 글꼴의 좌우 여백을 줄여 '점'이 숫자에 바로 붙게 한다.
                letterSpacing = (-0.5).sp,
            ),
            color = colors.cardInk,
        )
        Text(
            stringResource(Res.string.my_recent_score_unit),
            modifier = Modifier.alignByBaseline().padding(start = 1.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
            color = colors.cardInk,
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
 * 마이페이지 맨 아래 푸터: 구분선 없이 회색 바탕 위에 '자료 및 데이터 출처'와 법적 문서 세 개(서비스 이용약관 ·
 * 개인정보 처리방침 · 계정·데이터 삭제 안내)를 같은 모양의 한 줄(작은 회색 글씨 · 옅은 화살표)로 하나씩 쌓는다.
 * 모두 앱 안 화면으로 연다(S15P31E102-731, S15P31E102-833).
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
