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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.auth.component.AuthBodyTopWithBar
import com.ssafy.eolssu.feature.auth.component.AuthFrame
import com.ssafy.eolssu.feature.auth.component.AuthScaffold
import com.ssafy.eolssu.feature.auth.component.AuthTopBar
import com.ssafy.eolssu.feature.auth.component.authFrameOf
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_close
import eolssu.shared.generated.resources.auth_confirm
import eolssu.shared.generated.resources.auth_no_account_message
import eolssu.shared.generated.resources.auth_no_account_signup
import eolssu.shared.generated.resources.auth_no_account_title
import eolssu.shared.generated.resources.auth_oauth_result_hint
import eolssu.shared.generated.resources.auth_oauth_result_link
import eolssu.shared.generated.resources.auth_oauth_result_linked
import eolssu.shared.generated.resources.auth_oauth_result_none
import eolssu.shared.generated.resources.auth_oauth_result_withdrawn
import eolssu.shared.generated.resources.auth_oauth_placeholder_body
import eolssu.shared.generated.resources.auth_oauth_placeholder_title
import eolssu.shared.generated.resources.auth_oauth_title
import eolssu.shared.generated.resources.auth_withdrawal_cancelled_message
import eolssu.shared.generated.resources.auth_withdrawal_cancelled_title
import org.jetbrains.compose.resources.stringResource

/**
 * AUTH-05 간편 로그인(로그인 전용). 실제로는 제공자 화면이 뜨는 자리라 와이어프레임처럼 점선 자리 표시만 두고,
 * 제공자 로그인 뒤 서버 결과는 아래 버튼 셋으로 고른다(예시).
 * 연결된 계정이 있으면 [onLinked]로 홈, 같은 이메일로 가입한 계정이 있으면(409 `ACCOUNT_MERGE_AVAILABLE`) [onLinkAvailable]로 계정 연결 확인,
 * 둘 다 없으면 '가입된 계정이 없어요' 창을 띄우고 [onSignUp]으로 이메일 가입에 간다.
 * 탈퇴 대기 계정이면(로그인 응답의 `withdrawalCancelled`) 이메일 로그인처럼 '탈퇴 신청이 취소됐어요' 창의 확인 뒤 [onLinked]로 간다.
 * 와이어프레임은 이 결과를 도구 막대의 예시 결과로 고르지만 앱에는 도구 막대가 없어 네 번째 예시 버튼으로 둔다.
 */
@Composable
fun OAuthScreen(
    provider: SocialProvider,
    onBack: () -> Unit,
    onLinked: () -> Unit,
    onLinkAvailable: () -> Unit,
    onSignUp: () -> Unit,
    widthClass: WindowWidthClass = currentWindowLayout().widthClass,
) {
    val colors = EolssuTheme.colors
    val name = stringResource(provider.displayName)
    val frame = authFrameOf(widthClass, split = false)
    var noAccount by rememberSaveable { mutableStateOf(false) }
    var withdrawalCancelled by rememberSaveable { mutableStateOf(false) }
    AuthScaffold(
        frame,
        topBar = { AuthTopBar(stringResource(Res.string.auth_oauth_title, name), onBack = onBack) },
        bottomBar = {
            Text(
                stringResource(Res.string.auth_oauth_result_hint),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
            EolssuButton(stringResource(Res.string.auth_oauth_result_linked), onClick = onLinked)
            EolssuButton(stringResource(Res.string.auth_oauth_result_link), onClick = onLinkAvailable, style = EolssuButtonStyle.Secondary)
            EolssuButton(stringResource(Res.string.auth_oauth_result_none), onClick = { noAccount = true }, style = EolssuButtonStyle.Secondary)
            EolssuButton(
                stringResource(Res.string.auth_oauth_result_withdrawn),
                onClick = { withdrawalCancelled = true },
                style = EolssuButtonStyle.Secondary,
            )
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 자리 표시는 본문 높이를 채우되 최소 360dp(`.au-placeholder`), 태블릿 세로 560dp · 가로 440dp.
            val minHeight = when (frame) {
                AuthFrame.Compact -> 360.dp
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
    if (noAccount) {
        EolssuDialog(
            title = stringResource(Res.string.auth_no_account_title),
            message = stringResource(Res.string.auth_no_account_message, name),
            icon = EolssuIcons.Info,
        ) {
            EolssuButton(stringResource(Res.string.auth_no_account_signup), onClick = onSignUp)
            EolssuButton(stringResource(Res.string.auth_close), onClick = onBack, style = EolssuButtonStyle.Text)
        }
    }
    if (withdrawalCancelled) {
        EolssuDialog(
            title = stringResource(Res.string.auth_withdrawal_cancelled_title),
            message = stringResource(Res.string.auth_withdrawal_cancelled_message),
            icon = EolssuIcons.Check,
        ) {
            EolssuButton(stringResource(Res.string.auth_confirm), onClick = onLinked)
        }
    }
}
