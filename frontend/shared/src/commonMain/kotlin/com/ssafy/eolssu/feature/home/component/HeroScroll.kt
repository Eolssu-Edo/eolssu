package com.ssafy.eolssu.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_brand
import eolssu.shared.generated.resources.home_hero_body
import eolssu.shared.generated.resources.home_hero_title
import eolssu.shared.generated.resources.home_start_evaluation
import org.jetbrains.compose.resources.stringResource

// 족자(두루마리) 그림에만 쓰는 색이라 테마 토큰으로 올리지 않는다.
private val Silk = Color(0xFFB9C3BD)
private val SilkDeep = Color(0xFF9EABA6)
private val Paper = Color(0xFFF7F1E4)
private val Rod = Color(0xFF3A2D22)
private val RodLight = Color(0xFF6B5644)
private val Knob = Color(0xFFC9B48C)
private val Seal = Color(0xFFB8322A)

/**
 * HOME-01의 족자 모양 안내 영역. 와이어프레임의 한지 질감·붓질 버튼은 시각 디자인 단계에서 다시 정하고,
 * 지금은 위 막대·비단 테두리·한지 바탕·아래 축과 낙관만 그린다.
 *
 * @param large 태블릿 배치용 큰 족자. 와이어프레임 `Tab-Main-Port`·`Tab-Main-Land`의 여백·글자 크기를 따른다.
 */
@Composable
internal fun HeroScroll(
    title: String,
    body: String,
    actionLabel: String,
    brand: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val size = if (large) HeroSize.Large else HeroSize.Compact
    Column(modifier = modifier.padding(horizontal = size.sideMargin), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .background(Brush.verticalGradient(listOf(RodLight, Rod)), RoundedCornerShape(2.dp)),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .background(Brush.verticalGradient(0f to SilkDeep, 0.1f to Silk, 0.92f to Silk, 1f to SilkDeep))
                .padding(start = 9.dp, end = 9.dp, top = 24.dp, bottom = 20.dp),
        ) {
            Box(Modifier.fillMaxWidth().background(Paper).border(1.dp, Rod.copy(alpha = 0.18f))) {
                SealMark(brand, Modifier.align(Alignment.TopEnd).padding(top = size.sealInset, end = size.sealInset - 2.dp))
                Column(
                    Modifier.padding(
                        start = size.paperPadding,
                        end = size.paperPadding,
                        top = size.paperTop,
                        bottom = size.paperBottom,
                    ),
                    verticalArrangement = Arrangement.spacedBy(size.textGap),
                ) {
                    val headline = EolssuTheme.serif.headline
                    Text(
                        title,
                        style = if (large) headline.copy(fontSize = 38.sp, lineHeight = 48.sp) else headline,
                        color = EolssuTheme.colors.text,
                    )
                    Text(
                        body,
                        style = if (large) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                        color = EolssuTheme.colors.textMuted,
                    )
                    Spacer(Modifier.height(size.actionGap))
                    Button(
                        onClick = onAction,
                        modifier = Modifier.widthIn(max = size.actionMaxWidth).fillMaxWidth().height(size.actionHeight),
                        shape = CircleShape,
                    ) {
                        Icon(HomeIcons.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(actionLabel, style = MaterialTheme.typography.labelLarge.copy(fontSize = size.actionFontSize))
                    }
                }
            }
        }
        ScrollAxis()
    }
}

/** 홈 문구를 넣은 [HeroScroll]. */
@Composable
internal fun HomeHero(onStartEvaluation: () -> Unit, modifier: Modifier = Modifier, large: Boolean = false) {
    HeroScroll(
        title = stringResource(Res.string.home_hero_title),
        body = stringResource(Res.string.home_hero_body),
        actionLabel = stringResource(Res.string.home_start_evaluation),
        brand = stringResource(Res.string.home_brand),
        onAction = onStartEvaluation,
        modifier = modifier,
        large = large,
    )
}

private class HeroSize(
    val sideMargin: Dp,
    val paperPadding: Dp,
    val paperTop: Dp,
    val paperBottom: Dp,
    val sealInset: Dp,
    val textGap: Dp,
    val actionGap: Dp,
    val actionHeight: Dp,
    val actionMaxWidth: Dp,
    val actionFontSize: TextUnit,
) {
    companion object {
        val Compact = HeroSize(
            sideMargin = 10.dp, paperPadding = 20.dp, paperTop = 28.dp, paperBottom = 24.dp, sealInset = 20.dp,
            textGap = 10.dp, actionGap = 10.dp, actionHeight = 56.dp, actionMaxWidth = Dp.Infinity, actionFontSize = 15.sp,
        )
        val Large = HeroSize(
            sideMargin = 14.dp, paperPadding = 40.dp, paperTop = 44.dp, paperBottom = 40.dp, sealInset = 28.dp,
            textGap = 14.dp, actionGap = 0.dp, actionHeight = 60.dp, actionMaxWidth = 360.dp, actionFontSize = 16.sp,
        )
    }
}

@Composable
private fun ScrollAxis() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp).background(Knob, CircleShape))
        Box(
            Modifier
                .weight(1f)
                .height(12.dp)
                .background(Brush.verticalGradient(listOf(RodLight, Rod)), RoundedCornerShape(3.dp)),
        )
        Box(Modifier.size(18.dp).background(Knob, CircleShape))
    }
}

/** 붉은 낙관. 세로쓰기가 없어 글자를 한 자씩 세로로 쌓는다. 장식이라 화면 읽기에서 제외한다. */
@Composable
private fun SealMark(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clearAndSetSemantics {}
            .rotate(4f)
            .border(1.5.dp, Seal, RoundedCornerShape(3.dp))
            .padding(horizontal = 3.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        text.forEach { ch ->
            Text(
                ch.toString(),
                style = TextStyle(
                    fontFamily = EolssuTheme.serif.brand.fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Seal,
                ),
            )
        }
    }
}
