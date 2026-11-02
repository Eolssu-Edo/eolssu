package com.ssafy.eolssu.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.auth.component.AuthBody
import com.ssafy.eolssu.feature.auth.component.AuthFrame
import com.ssafy.eolssu.feature.auth.component.AuthScaffold
import com.ssafy.eolssu.feature.auth.component.authFrameOf
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_mic_allow
import eolssu.shared.generated.resources.auth_mic_deny
import eolssu.shared.generated.resources.auth_mic_lead
import eolssu.shared.generated.resources.auth_mic_title
import org.jetbrains.compose.resources.stringResource

/**
 * AUTH-03 마이크 안내(첫 실행). '허용'을 누르면 화면 이동 쪽(`authGraph`)이 OS 권한 창을 띄운다.
 * 와이어프레임의 OS 권한 창 예시는 흉내 내지 않고 같은 선택(허용·허용 안 함)을 하단 버튼으로 둔다.
 */
@Composable
fun MicPermissionScreen(onAllow: () -> Unit, onDeny: () -> Unit, widthClass: WindowWidthClass = currentWindowLayout().widthClass) {
    val frame = authFrameOf(widthClass, split = false)
    AuthScaffold(
        frame,
        bottomBar = {
            EolssuButton(stringResource(Res.string.auth_mic_allow), onClick = onAllow)
            EolssuButton(stringResource(Res.string.auth_mic_deny), onClick = onDeny, style = EolssuButtonStyle.Secondary)
        },
    ) {
        // 태블릿도 휴대폰처럼 위쪽에 두고, 세로 120dp · 가로 64dp 내린다(`.tabapp.sc-auth-mic`).
        val top = when (frame) {
            AuthFrame.Compact -> 120.dp
            AuthFrame.Portrait -> 120.dp
            AuthFrame.Landscape, AuthFrame.Split -> 64.dp
        }
        AuthBody(topPadding = top, horizontalAlignment = Alignment.CenterHorizontally) {
            // 보드 `.ico-ring.lg`: 72dp 연두 원, 아이콘 34dp. 제목은 `.h1` 26sp(줄 높이 1.3), 설명은 `.lead` 15sp.
            Box(
                Modifier.size(72.dp).background(EolssuTheme.colors.accentTint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(EolssuIcons.Mic, contentDescription = null, modifier = Modifier.size(34.dp), tint = EolssuTheme.colors.text)
            }
            Text(
                stringResource(Res.string.auth_mic_title),
                style = EolssuTheme.serif.headline.copy(fontSize = 26.sp, lineHeight = 33.8.sp),
                color = EolssuTheme.colors.text,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(Res.string.auth_mic_lead),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = EolssuTheme.colors.textSub,
                textAlign = TextAlign.Center,
            )
        }
    }
}
