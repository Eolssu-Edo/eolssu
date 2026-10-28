package com.ssafy.eolssu.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.feature.auth.component.AuthBodyTopWithBar
import com.ssafy.eolssu.feature.auth.component.AuthFrame
import com.ssafy.eolssu.feature.auth.component.AuthScaffold
import com.ssafy.eolssu.feature.auth.component.AuthTopBar
import com.ssafy.eolssu.feature.auth.component.authFrameOf
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_oauth_existing_account
import eolssu.shared.generated.resources.auth_oauth_new_account
import eolssu.shared.generated.resources.auth_oauth_placeholder_body
import eolssu.shared.generated.resources.auth_oauth_placeholder_title
import eolssu.shared.generated.resources.auth_oauth_title
import org.jetbrains.compose.resources.stringResource

/**
 * AUTH-05 소셜 로그인. 실제로는 제공사 화면이 뜨는 자리라 와이어프레임처럼 점선 자리 표시만 둔다.
 * 처음 가입이면 [onNewAccount]로 프로필 설정, 이미 가입한 계정이면 [onExistingAccount]로 홈에 간다.
 */
@Composable
fun OAuthScreen(
    provider: SocialProvider,
    onBack: () -> Unit,
    onNewAccount: () -> Unit,
    onExistingAccount: () -> Unit,
    widthClass: WindowWidthClass = currentWindowLayout().widthClass,
) {
    val colors = EolssuTheme.colors
    val name = stringResource(provider.displayName)
    val frame = authFrameOf(widthClass, split = false)
    AuthScaffold(
        frame,
        topBar = { AuthTopBar(stringResource(Res.string.auth_oauth_title, name), onBack = onBack) },
        bottomBar = {
            EolssuButton(stringResource(Res.string.auth_oauth_new_account), onClick = onNewAccount)
            EolssuButton(stringResource(Res.string.auth_oauth_existing_account), onClick = onExistingAccount, style = EolssuButtonStyle.Secondary)
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 자리 표시는 본문 높이를 채우되 최소 420dp(`.au-placeholder`), 태블릿 세로 560dp · 가로 440dp.
            val minHeight = when (frame) {
                AuthFrame.Compact -> 420.dp
                AuthFrame.Portrait -> 560.dp
                AuthFrame.Landscape, AuthFrame.Split -> 440.dp
            }
            val boxHeight = maxOf(minHeight, maxHeight - AuthBodyTopWithBar - 24.dp)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = AuthBodyTopWithBar, bottom = 24.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = boxHeight)
                        .drawBehind {
                            val stroke = 1.5.dp.toPx()
                            drawRoundRect(
                                color = colors.lineStrong,
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
                    Text(
                        stringResource(Res.string.auth_oauth_placeholder_title, name),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(Res.string.auth_oauth_placeholder_body),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.8.sp),
                        color = colors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
