package com.ssafy.eolssu.feature.profile.component

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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.theme.EolssuTheme

/** 제공자 표시 색(보드 `.au-mark` 인라인 스타일). 제공사 브랜드 색이라 테마에 두지 않는다. */
private class MarkColors(val background: Color, val text: Color, val bordered: Boolean = false)

private fun markColors(provider: SocialProvider): MarkColors = when (provider) {
    SocialProvider.Kakao -> MarkColors(Color(0xFFFEE500), Color(0xFF191919))
    SocialProvider.Naver -> MarkColors(Color(0xFF03C75A), Color.White)
    SocialProvider.Google -> MarkColors(Color.White, Color(0xFF4285F4), bordered = true)
}

/**
 * MY-01 간편 로그인 줄의 제공자 표시(`.au-mark`): 24dp 둥근 칸에 제공사 색과 글자(K·N·G).
 * 제공사 공식 버튼 이미지를 붙이기 전까지 쓰는 표시이고, 이름은 옆 글자가 읽으므로 여기서는 읽지 않는다.
 */
@Composable
internal fun ProviderMark(provider: SocialProvider, modifier: Modifier = Modifier) {
    val colors = markColors(provider)
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .size(24.dp)
            .background(colors.background, shape)
            .then(if (colors.bordered) Modifier.border(1.dp, EolssuTheme.colors.lineSoft, shape) else Modifier)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(provider.mark, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = colors.text)
    }
}
