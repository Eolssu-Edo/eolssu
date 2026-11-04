package com.ssafy.eolssu.feature.profile.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** 학습 화면과 같은 기기 저장 기록. 순서를 건너뛴 완료도 각 칸에 그대로 표시한다. */
@Composable
internal fun MyLearningCard(instrument: Instrument?, progress: MyLearningProgress?, loading: Boolean) {
    val colors = EolssuTheme.colors
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.accentSoft,
        border = BorderStroke(1.dp, colors.accentTintLine),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).background(colors.accentTintStrong, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(EolssuIcons.Book, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.accentText)
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(Res.string.my_learning_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.text)
                    if (instrument != null) Text(
                        stringResource(Res.string.my_learning_instrument, stringResource(instrument.label)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
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
                Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    (1..progress.totalStages).forEach { stage ->
                        val done = stage in progress.completedStages
                        val next = stage == progress.nextStage
                        Box(
                            Modifier.weight(1f).aspectRatio(1f)
                                .background(if (done) colors.primary else colors.surface, CircleShape)
                                .border(if (next) 2.dp else 1.dp, if (next) colors.play else colors.accentTintLine, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (done) Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.onPrimary)
                            else Text(stage.toString(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = if (next) colors.play else colors.textMuted)
                        }
                    }
                }
                Text(
                    progress.nextStage?.let { stringResource(Res.string.my_learning_next, it) } ?: stringResource(Res.string.my_learning_complete),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.accentText,
                )
                Text(stringResource(Res.string.my_learning_local), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = colors.textMuted)
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

@Composable
internal fun MyPageFooter(onOpenLegal: (PublishedLegalDocument) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        ProfileTextLink(stringResource(Res.string.legal_service), { onOpenLegal(PublishedLegalDocument.Service) })
        ProfileTextLink(stringResource(Res.string.legal_privacy), { onOpenLegal(PublishedLegalDocument.Privacy) })
        ProfileTextLink(stringResource(Res.string.legal_deletion), { onOpenLegal(PublishedLegalDocument.Deletion) })
    }
}
