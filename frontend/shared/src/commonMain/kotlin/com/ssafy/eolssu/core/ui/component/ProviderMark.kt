package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.apple_logo
import eolssu.shared.generated.resources.google_symbol
import eolssu.shared.generated.resources.kakao_symbol
import eolssu.shared.generated.resources.naver_symbol
import org.jetbrains.compose.resources.painterResource

/** 제공자 표시 바탕(보드 `.au-mark`). 제공사 브랜드 색이라 테마에 두지 않는다. */
private class MarkColors(val background: Color, val bordered: Boolean = false)

private fun markColors(provider: SocialProvider): MarkColors = when (provider) {
    SocialProvider.Kakao -> MarkColors(Color(0xFFFEE500))
    SocialProvider.Naver -> MarkColors(Color(0xFF03C75A))
    SocialProvider.Google -> MarkColors(Color.White, bordered = true)
    // Apple 로그인 버튼 지침의 검정 바탕·흰 로고.
    SocialProvider.Apple -> MarkColors(Color.Black)
}

/**
 * 제공자 표시(`.au-mark`): 둥근 칸에 제공사 색과 공식 심볼([ProviderGlyph]). 간편 로그인 대기 화면은 크게 쓴다.
 * 모서리·심볼 크기는 [size]에 비례한다(24dp일 때 모서리 6dp · 심볼 14dp). 이름은 옆 글자가 읽으므로 여기서는 읽지 않는다.
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
        ProviderGlyph(provider, 14.dp * scale)
    }
}

/**
 * 제공사 로그인 버튼 가이드의 심볼(카카오 말풍선·네이버 N·Google 4색 G·Apple 로고)을 [size]로 그린다.
 * 색은 각 가이드에 정해진 대로 고정이고, Apple 로고만 [appleColor]로 칠한다. 장식이라 읽지 않는다.
 */
@Composable
internal fun ProviderGlyph(provider: SocialProvider, size: Dp, appleColor: Color = Color.White) {
    Image(
        painterResource(
            when (provider) {
                SocialProvider.Kakao -> Res.drawable.kakao_symbol
                SocialProvider.Naver -> Res.drawable.naver_symbol
                SocialProvider.Google -> Res.drawable.google_symbol
                SocialProvider.Apple -> Res.drawable.apple_logo
            },
        ),
        contentDescription = null,
        modifier = Modifier.size(size),
        colorFilter = if (provider == SocialProvider.Apple) ColorFilter.tint(appleColor) else null,
    )
}
