package com.ssafy.eolssu.feature.home.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_hero_body
import eolssu.shared.generated.resources.home_hero_title
import eolssu.shared.generated.resources.home_illustration
import eolssu.shared.generated.resources.home_start_evaluation
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** 최신 Main 보드의 원본 일러스트. 그림은 장식이며 문구·동작은 실제 UI다. */
@Composable
internal fun HomeHero(
    onStartEvaluation: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    edgeToEdge: Boolean = false,
    header: (@Composable () -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(if (edgeToEdge) 0.dp else 16.dp)).background(Color(0xFFEFF2D6)),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Image(
                painterResource(Res.drawable.home_illustration),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, 0.24f),
            )
            val shade = if (edgeToEdge) {
                // 보드 `.shade`: 52%까지 투명, 88%에서 먹색 62%.
                Brush.verticalGradient(0.52f to Color.Transparent, 0.88f to HeroShade)
            } else {
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC141824)))
            }
            Box(Modifier.matchParentSize().background(shade))
            Column(
                Modifier.fillMaxWidth().heightIn(min = if (edgeToEdge) 376.dp else if (large) 340.dp else 280.dp).padding(start = 20.dp, end = 20.dp, top = if (header != null) 100.dp else 20.dp, bottom = if (edgeToEdge) 20.dp else 24.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (edgeToEdge) {
                    // 보드 `.headline`: 붓글씨 26sp(줄 높이 1.25) 미색, 소개 13sp 연한 금빛, 둘 다 옅은 그림자.
                    Text(
                        stringResource(Res.string.home_hero_title),
                        style = EolssuTheme.serif.headline.copy(fontSize = 26.sp, lineHeight = 32.5.sp, shadow = Shadow(TextShadow, Offset(0f, 2f), 20f)),
                        color = EolssuTheme.colors.background,
                        textAlign = TextAlign.End,
                    )
                    Text(
                        stringResource(Res.string.home_hero_body),
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, shadow = Shadow(TextShadow, Offset(0f, 2f), 12f)),
                        color = HeroBodyColor,
                        textAlign = TextAlign.End,
                    )
                } else {
                    Text(stringResource(Res.string.home_hero_title), style = EolssuTheme.serif.headline, color = Color.White, textAlign = TextAlign.End)
                    Text(stringResource(Res.string.home_hero_body), modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = Color.White, textAlign = TextAlign.End)
                }
            }
            if (header != null) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()) { header() }
        }
        HomeEvaluationButton(onStartEvaluation, if (edgeToEdge) Modifier.padding(horizontal = 20.dp) else Modifier)
    }
}

private val HeroShade = Color(0x9E141824)
private val HeroBodyColor = Color(0xFFF2E6CE)
private val TextShadow = Color(0x66000000)

/** Main 기본 연두 CTA와 hover/누름의 먹색 붓 효과. 문구·조작은 실제 UI다. */
@Composable
private fun HomeEvaluationButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val painted = pressed || hovered
    val ink = EolssuTheme.colors.text
    val foreground = if (painted) EolssuTheme.colors.onInk else EolssuTheme.colors.onPrimary
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).background(EolssuTheme.colors.primary, RoundedCornerShape(14.dp))
            .border(2.dp, if (focused) ink else Color.Transparent, RoundedCornerShape(14.dp)).drawWithCache {
            val brush = Path().apply {
                moveTo(0f, 7f); cubicTo(40f, 1f, 90f, 13f, 140f, 7f); cubicTo(190f, 1f, 250f, -1f, 350f, 9f)
                lineTo(350f, 45f); cubicTo(300f, 51f, 250f, 39f, 200f, 47f); cubicTo(150f, 55f, 80f, 53f, 0f, 45f); close()
            }
            val edges = Path().apply {
                moveTo(0f, 3f); cubicTo(60f, 9f, 120f, 2f, 180f, 6f); cubicTo(240f, 10f, 300f, 3f, 350f, 4f)
                lineTo(350f, 7f); cubicTo(300f, 6f, 240f, 11f, 180f, 9f); cubicTo(120f, 7f, 60f, 12f, 0f, 6f); close()
                moveTo(0f, 49f); cubicTo(70f, 45f, 150f, 51f, 220f, 47f); cubicTo(290f, 43f, 320f, 50f, 350f, 48f)
                lineTo(350f, 51f); cubicTo(320f, 52f, 240f, 50f, 170f, 51f); cubicTo(100f, 52f, 60f, 49f, 0f, 52f); close()
            }
            onDrawBehind {
                if (painted) {
                    scale(size.width / 350f, size.height / 52f, pivot = Offset.Zero) {
                        drawPath(brush, ink)
                        drawPath(edges, ink, alpha = 0.45f)
                    }
                }
            }
        }.hoverable(interactions).clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick).padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(EolssuIcons.Mic, contentDescription = null, modifier = Modifier.size(20.dp), tint = foreground)
        Text(stringResource(Res.string.home_start_evaluation), modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge.copy(fontSize = 17.sp), color = foreground, textAlign = TextAlign.Center)
        Icon(EolssuIcons.ArrowRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = foreground)
    }
}
