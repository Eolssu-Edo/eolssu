package com.ssafy.eolssu.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.feature.auth.component.AuthBody
import com.ssafy.eolssu.feature.auth.component.AuthBodyTopWithoutBar
import com.ssafy.eolssu.feature.auth.component.AuthHeading
import com.ssafy.eolssu.feature.auth.component.AuthLead
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_mic_allow
import eolssu.shared.generated.resources.auth_mic_deny
import eolssu.shared.generated.resources.auth_mic_lead
import eolssu.shared.generated.resources.auth_mic_title
import org.jetbrains.compose.resources.stringResource

/**
 * AUTH-03 마이크 안내(첫 실행). 실제 권한 요청은 녹음 기능을 붙일 때 연습 화면에서 한다.
 * 와이어프레임의 OS 권한 창 예시는 흉내 내지 않고 같은 선택(허용·허용 안 함)을 하단 버튼으로 둔다.
 */
@Composable
fun MicPermissionScreen(onAllow: () -> Unit, onDeny: () -> Unit) {
    EolssuScaffold(
        bottomBar = {
            EolssuButton(stringResource(Res.string.auth_mic_allow), onClick = onAllow)
            EolssuButton(stringResource(Res.string.auth_mic_deny), onClick = onDeny, style = EolssuButtonStyle.Secondary)
        },
    ) {
        AuthBody(topPadding = AuthBodyTopWithoutBar, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.padding(top = 16.dp).size(88.dp).background(EolssuTheme.colors.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(EolssuIcons.Mic, contentDescription = null, modifier = Modifier.size(40.dp), tint = EolssuTheme.colors.text)
            }
            AuthHeading(stringResource(Res.string.auth_mic_title), textAlign = TextAlign.Center)
            AuthLead(stringResource(Res.string.auth_mic_lead), textAlign = TextAlign.Center)
        }
    }
}
