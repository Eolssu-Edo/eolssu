package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.apple_logo
import org.jetbrains.compose.resources.painterResource

/** 제공자 표시 색(보드 `.au-mark` 인라인 스타일). 제공사 브랜드 색이라 테마에 두지 않는다. */
private class MarkColors(val background: Color, val text: Color, val bordered: Boolean = false)

private fun markColors(provider: SocialProvider): MarkColors = when (provider) {
    SocialProvider.Kakao -> MarkColors(Color(0xFFFEE500), Color(0xFF191919))
    SocialProvider.Naver -> MarkColors(Color(0xFF03C75A), Color.White)
    SocialProvider.Google -> MarkColors(Color.White, Color(0xFF4285F4), bordered = true)
    // Apple 로그인 버튼 지침의 검정 바탕·흰 로고.
    SocialProvider.Apple -> MarkColors(Color.Black, Color.White)
}

/**
 * 제공자 표시(`.au-mark`): 둥근 칸에 제공사 색과 글자(K·N·G). MY-01 간편 로그인 줄은 24dp, 간편 로그인 대기 화면은 크게 쓴다.
 * 모서리·글자 크기는 [size]에 비례한다(24dp일 때 모서리 6dp · 글자 11sp).
 * 제공사 공식 버튼 이미지를 붙이기 전까지 쓰는 표시이고, 이름은 옆 글자가 읽으므로 여기서는 읽지 않는다.
 */
@Composable
internal fun ProviderMark(provider: SocialProvider, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val colors = markColors(provider)
    val scale = size / 24.dp
    val shape = RoundedCornerShape(6.dp * scale)
    Box(
        modifier
            .size(size)
            .background(colors.background, shape)
            .then(if (colors.bordered) Modifier.border(1.dp, EolssuTheme.colors.lineSoft, shape) else Modifier)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        ProviderGlyph(provider, colors.text, 11.sp * scale, 16.dp * scale)
    }
}

/** 제공자 표시 안의 글자(K·N·G). Apple은 글자 대신 Apple 로고를 [logoSize]로 그린다. 장식이라 읽지 않는다. */
@Composable
internal fun ProviderGlyph(provider: SocialProvider, color: Color, fontSize: androidx.compose.ui.unit.TextUnit, logoSize: Dp) {
    if (provider == SocialProvider.Apple) {
        Image(painterResource(Res.drawable.apple_logo), contentDescription = null, modifier = Modifier.size(logoSize), colorFilter = ColorFilter.tint(color))
    } else {
        Text(provider.mark, style = MaterialTheme.typography.labelSmall.copy(fontSize = fontSize, fontWeight = FontWeight.Bold), color = color)
    }
}
