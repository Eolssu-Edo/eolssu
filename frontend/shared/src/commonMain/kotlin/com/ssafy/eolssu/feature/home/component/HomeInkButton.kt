package com.ssafy.eolssu.feature.home.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_ink_brush
import eolssu.shared.generated.resources.home_start_evaluation
import eolssu.shared.generated.resources.home_ink_griun
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** 원본 SVG는 고정하고 붓털 모양의 클립만 전진시킨다. 글자 반전도 같은 경계를 쓴다. */
@Composable
internal fun HomeInkButton(
    onClick: () -> Unit,
    darkBackground: Boolean,
    scrollProgress: Float,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val fontFamily = FontFamily(Font(Res.font.home_ink_griun, FontWeight.Normal))
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val targetReveal = if (reduceMotion) {
        if (scrollProgress >= .5f) 1f else 0f
    } else scrollProgress.coerceIn(0f, 1f)
    // 휠·빠른 스와이프로 위치가 뛰어도 중간 붓질은 건너뛰지 않는다.
    val reveal by animateFloatAsState(targetReveal, tween(if (reduceMotion) 0 else 500))
    val ink by animateColorAsState(if (darkBackground) Color.White else Color(0xFF0B1020), tween(if (reduceMotion) 0 else 250))
    val rest by animateColorAsState(if (darkBackground) Color.White else Color(0xFF171714), tween(if (reduceMotion) 0 else 250))
    val onInk by animateColorAsState(if (darkBackground) Color(0xFF171714) else Color(0xFFFFFEFA), tween(if (reduceMotion) 0 else 250))
    val label = stringResource(Res.string.home_start_evaluation)
    Box(
        modifier.fillMaxWidth().heightIn(min = 44.dp)
            .border(2.dp, if (focused) Color(0xFF686250) else Color.Transparent, RoundedCornerShape(3.dp))
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(Res.drawable.home_ink_brush), contentDescription = null,
            modifier = Modifier.matchParentSize().padding(vertical = 2.dp).inkReveal(reveal),
            contentScale = ContentScale.FillBounds, colorFilter = ColorFilter.tint(ink),
        )
        InkLabel(label, rest, fontFamily)
        Box(Modifier.matchParentSize().inkReveal(reveal).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            InkLabel(label, onInk, fontFamily)
        }
    }
}

@Composable
private fun InkLabel(label: String, color: Color, fontFamily: FontFamily?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, top = 10.dp, bottom = 10.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelLarge.copy(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None, fontSize = 20.sp, lineHeight = 28.sp))
        Text("→", color = color, fontSize = 24.sp, modifier = Modifier.clearAndSetSemantics {})
    }
}

private fun Modifier.inkReveal(progress: Float) = drawWithCache {
    val edge = inkRevealPath(size, progress)
    onDrawWithContent { clipPath(edge) { this@onDrawWithContent.drawContent() } }
}
