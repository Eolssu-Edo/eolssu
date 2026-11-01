package com.ssafy.eolssu.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.profile.component.ProfileScaffold
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_confirm
import eolssu.shared.generated.resources.member_api_retry
import eolssu.shared.generated.resources.my_link_taken_message
import eolssu.shared.generated.resources.my_link_taken_title
import eolssu.shared.generated.resources.my_link_title
import eolssu.shared.generated.resources.social_auth_progress
import eolssu.shared.generated.resources.social_auth_reopen
import eolssu.shared.generated.resources.social_auth_waiting_body
import eolssu.shared.generated.resources.social_auth_waiting_title
import org.jetbrains.compose.resources.stringResource

/** 제공자 화면 자리의 점선 색(보드 `.au-placeholder` #B9B3A6). 테마에 없는 옅은 선이라 이 화면에만 둔다. */
private val PlaceholderDash = Color(0xFFB9B3A6)

/**
 * MY-05 간편 로그인 연결. 제공자 화면은 외부 브라우저에 뜨고, 이 화면은 그동안 와이어프레임의 점선 자리에 진행 상태를 보여 준다.
 * 이미 다른 얼쑤 계정에 연결된 제공자 계정이면(409 `SOCIAL_ACCOUNT_IN_USE`) 안내 창을 띄운 뒤 [onBack]으로 돌아간다.
 */
@Composable
fun SocialLinkScreen(
    provider: SocialProvider,
    step: SocialLinkStep,
    widthClass: WindowWidthClass,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onReopen: () -> Unit,
) {
    val colors = EolssuTheme.colors
    val name = stringResource(provider.displayName)
    val (title, body) = when (step) {
        is SocialLinkStep.Stopped -> null to stringResource(step.problem.message, name)
        SocialLinkStep.Starting, SocialLinkStep.Completing -> null to stringResource(Res.string.social_auth_progress)
        else -> stringResource(Res.string.social_auth_waiting_title, name) to stringResource(Res.string.social_auth_waiting_body)
    }
    ProfileScaffold(
        widthClass,
        stringResource(Res.string.my_link_title, name),
        onBack,
        bottomBar = {
            when (step) {
                SocialLinkStep.InBrowser -> EolssuButton(stringResource(Res.string.social_auth_reopen, name), onClick = onReopen)
                is SocialLinkStep.Stopped -> if (step.problem.retryable) {
                    EolssuButton(stringResource(Res.string.member_api_retry), onClick = onRetry)
                } else {
                    EolssuButton(stringResource(Res.string.common_confirm), onClick = onBack)
                }
                SocialLinkStep.Starting, SocialLinkStep.Completing ->
                    EolssuButton(stringResource(Res.string.social_auth_progress), onClick = {}, busy = true)
                else -> Unit
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // 자리 표시 최소 높이(`.au-placeholder`): 휴대폰 360dp, 태블릿 세로 560dp · 가로 440dp.
                .heightIn(
                    min = when (widthClass) {
                        WindowWidthClass.Compact -> 360.dp
                        WindowWidthClass.Medium -> 560.dp
                        WindowWidthClass.Expanded -> 440.dp
                    },
                )
                .drawBehind {
                    val stroke = 1.5.dp.toPx()
                    drawRoundRect(
                        color = PlaceholderDash,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                    )
                }
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                body,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.8.sp),
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
    if (step == SocialLinkStep.InUse) {
        EolssuDialog(
            title = stringResource(Res.string.my_link_taken_title, name),
            message = stringResource(Res.string.my_link_taken_message, name),
            icon = EolssuIcons.Info,
        ) {
            EolssuButton(stringResource(Res.string.common_confirm), onClick = onBack)
        }
    }
}
