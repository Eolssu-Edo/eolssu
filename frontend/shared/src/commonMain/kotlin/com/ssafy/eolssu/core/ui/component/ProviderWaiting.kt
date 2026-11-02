package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.theme.EolssuTheme

/**
 * 간편 로그인 제공자 화면(외부 브라우저)에 다녀오는 동안의 표시. 간편 로그인(AUTH-05)·연결(MY-05)·탈퇴 재인증(MY-04)이 함께 쓴다.
 * 큰 제공자 표시 아래에 [busy]면 진행 표시를 두고 [title]·[body]를 가운데에 놓는다. 멈췄을 때는 진행 표시 없이 [body]에 이유를 적는다.
 * 와이어프레임의 점선 상자(`.au-placeholder`)는 제공자 화면 자리 표시라 앱에서는 그리지 않는다.
 */
@Composable
internal fun ProviderWaiting(
    provider: SocialProvider,
    title: String?,
    body: String,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = EolssuTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ProviderMark(provider, size = 56.dp)
        Spacer(Modifier.height(20.dp))
        if (busy) {
            CircularProgressIndicator(Modifier.size(24.dp), color = colors.accent, trackColor = colors.lineSoft, strokeWidth = 2.5.dp)
            Spacer(Modifier.height(20.dp))
        }
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                color = colors.text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}
