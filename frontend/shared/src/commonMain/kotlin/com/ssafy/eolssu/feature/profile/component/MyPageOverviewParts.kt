package com.ssafy.eolssu.feature.profile.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.data.user.PublishedLegalDocument
import com.ssafy.eolssu.feature.profile.MyLearningProgress
import com.ssafy.eolssu.feature.profile.LearningMascot
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** 학습 화면과 같은 서버 진도. 완료한 단계도 숫자를 눌러 다시 열 수 있다. */
@Composable
internal fun MyLearningCard(instrument: Instrument?, progress: MyLearningProgress?, loading: Boolean, onOpenStage: (Int) -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.accentSoft,
        border = BorderStroke(1.dp, colors.accentTintLine),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(Res.string.my_learning_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.text)
                    if (instrument != null) Text(
                        stringResource(Res.string.my_learning_instrument, stringResource(instrument.label)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
                // 원본 프레임의 빈 가장자리 대신 캐릭터의 실제 높이를 기준으로 제목 옆에 배치한다.
                Box(Modifier.size(width = 112.dp, height = 72.dp)) {
                    LearningMascotImage(
                        progress?.mascot ?: LearningMascot.Study,
                        Modifier.requiredSize(112.dp),
                    )
                }
            }
            if (progress == null) {
                Text(
                    stringResource(if (loading) Res.string.my_learning_loading else Res.string.my_learning_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
            } else {
                Text(
                    stringResource(Res.string.my_learning_count, progress.completedCount, progress.totalStages),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                    color = colors.accentText,
                )
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // 아래 줄의 중심을 위 줄 각 숫자의 중간에 맞춘다(위 5개·아래 4개).
                    val staggerInset = (maxWidth - 48.dp) / 8
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(1..5, 6..9).forEachIndexed { row, stages ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = if (row == 0) 0.dp else staggerInset),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                stages.forEach { stage ->
                                    val done = stage in progress.completedStages
                                    val next = stage == progress.nextStage
                                    val label = stringResource(Res.string.my_learning_open_stage, stage)
                                    val status = stringResource(when {
                                        done -> Res.string.my_learning_stage_done
                                        next -> Res.string.my_learning_stage_next
                                        else -> Res.string.my_learning_stage_not_done
                                    })
                                    Box(
                                        Modifier.size(48.dp)
                                            .clickable(enabled = instrument != null, role = Role.Button, onClickLabel = label) { onOpenStage(stage) }
                                            .semantics { contentDescription = label; stateDescription = status }
                                            .padding(5.dp)
                                            .background(if (done) colors.primary else colors.surface, CircleShape)
                                            .border(if (next) 2.dp else 1.dp, if (next) colors.play else colors.accentTintLine, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(stage.toString(), modifier = Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = if (done || next) FontWeight.Bold else FontWeight.Normal), color = if (done) colors.onPrimary else if (next) colors.play else colors.textMuted)
                                        if (done) Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.align(Alignment.TopEnd).size(12.dp), tint = colors.onPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    progress.nextStage?.let { stringResource(Res.string.my_learning_next, it) } ?: stringResource(Res.string.my_learning_complete),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.accentText,
                )
            }
        }
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
        modifier = Modifier.size(36.dp),
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
        HorizontalDivider(color = colors.lineSoft)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onOpenSources),
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
