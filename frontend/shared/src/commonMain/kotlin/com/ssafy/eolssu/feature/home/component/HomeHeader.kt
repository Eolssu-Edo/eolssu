package com.ssafy.eolssu.feature.home.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_my_page
import eolssu.shared.generated.resources.home_my_page_jeolssu
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 홈 마이페이지 판(`.plate`): 그림이 비치는 반투명 흰 바탕의 38dp 원에 마스코트 절쑤 얼굴 32dp, 옅은 그림자.
 * 얼굴 그림은 기술 블로그 탭 아이콘과 같은 64px 원본이다.
 * 누르면 [onClick]으로 마이페이지(MY-01)에 간다. 누르는 영역은 Compose의 최소 터치 영역(48dp)으로 원 바깥까지 넓어진다.
 */
@Composable
internal fun MyPagePlate(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.size(38.dp),
        shape = CircleShape,
        color = colors.background.copy(alpha = 0.62f),
        contentColor = colors.text,
        shadowElevation = 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(Res.drawable.home_my_page_jeolssu),
                contentDescription = stringResource(Res.string.home_my_page),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

/** 태블릿 홈 오른쪽 위 마이페이지 입구: 48dp 터치 영역. */
@Composable
internal fun MyPageButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = colors.background,
        contentColor = colors.text,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Icon(EolssuIcons.User, contentDescription = stringResource(Res.string.home_my_page), modifier = Modifier.padding(12.dp))
    }
}
