package com.ssafy.eolssu.feature.guide.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.score.ComposedGlyph
import com.ssafy.eolssu.feature.guide.glyphPartsOf
import com.ssafy.eolssu.feature.guide.westernNameLabel

/*
 * 학습 화면의 글자 그리기. 본문 글꼴(Pretendard)에 한자·♭가 없고 세리프 글꼴에는 율명 15자만 있어서,
 * 문장 속 한자는 세리프로 바꾸고 ♭는 그림으로 넣으며, 글꼴에 없는 율명은 변 + 율명으로 나눠 그린다.
 */

private const val FLAT_ID = "flat"

/** 내림표(♭). 두 글꼴 모두 이 글자가 없어서 그림으로 넣는다. */
private val FlatSign: ImageVector = ImageVector.Builder("Flat", 10.dp, 24.dp, 10f, 24f).apply {
    addPath(
        pathData = PathParser().parsePathString("M3 3v18c5 -2.5 6.5 -6.5 4 -8.2c-1.4 -0.9 -3 -0.2 -4 1.2").toNodes(),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}.build()

private fun Char.isHanja(): Boolean = this in '㐀'..'䶿' || this in '一'..'鿿' || this in '豈'..'﫿'

/**
 * `**굵게**` 표시를 굵은 글자로, 한자를 세리프로, ♭를 그림 자리로 바꾼다.
 * [boldColor]가 있으면 굵은 부분 색을 바꾼다(회색 문장 속 검은 강조).
 */
internal fun guideAnnotated(raw: String, serif: FontFamily, boldColor: Color? = null): AnnotatedString = buildAnnotatedString {
    var bold = false
    var i = 0
    while (i < raw.length) {
        if (raw.startsWith("**", i)) {
            bold = !bold
            i += 2
            continue
        }
        val c = raw[i]
        val start = length
        when {
            c == '♭' -> appendInlineContent(FLAT_ID, "♭")
            c.isHanja() -> {
                pushStyle(SpanStyle(fontFamily = serif))
                append(c)
                pop()
            }
            else -> append(c)
        }
        if (bold) addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor ?: Color.Unspecified), start, length)
        i++
    }
}

/** 학습 화면 문장. [raw]의 `**굵게**`·한자·♭를 [guideAnnotated]대로 그린다. */
@Composable
internal fun GuideText(
    raw: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = EolssuTheme.colors.text,
    boldColor: Color? = null,
    textAlign: TextAlign? = null,
) {
    val serif = EolssuTheme.serif.yul.fontFamily ?: FontFamily.Serif
    val text = remember(raw, serif, boldColor) { guideAnnotated(raw, serif, boldColor) }
    val inline = if ('♭' in raw) {
        mapOf(
            FLAT_ID to InlineTextContent(Placeholder(0.42.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
                Icon(FlatSign, contentDescription = null, modifier = Modifier.fillMaxSize(), tint = color)
            },
        )
    } else {
        emptyMap()
    }
    Text(text, modifier, color = color, style = style, textAlign = textAlign, inlineContent = inline)
}

/**
 * 율명 글자 하나. 세리프 글꼴에 없는 청성·탁성·중청 글자는 작은 변(氵·亻)을 기본 율명 왼쪽에 겹쳐 그린다.
 * 화면 읽기 이름은 호출하는 쪽에서 율명 이름으로 붙인다.
 */
@Composable
internal fun HanjaGlyph(
    hanja: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = EolssuTheme.colors.text,
    fontWeight: FontWeight = FontWeight.SemiBold,
) {
    val parts = remember(hanja) { glyphPartsOf(hanja) }
    if (parts.radicals.isEmpty()) {
        Text(hanja, modifier, color = color, style = EolssuTheme.serif.yul.copy(fontSize = fontSize, lineHeight = fontSize, fontWeight = fontWeight))
    } else {
        ComposedGlyph(
            radicals = parts.radicals,
            base = parts.base,
            radicalSize = fontSize * 0.62f,
            baseSize = fontSize * 0.8f,
            overlap = 0.45f,
            modifier = modifier,
            color = color,
            fontWeight = fontWeight,
        )
    }
}

/** 서양 음이름(작은 회색 글자, [westernNameLabel]대로 괄호). ♭는 그림으로 넣는다. */
@Composable
internal fun SolfegeText(solfege: String, modifier: Modifier = Modifier) {
    GuideText(
        westernNameLabel(solfege),
        modifier,
        style = LocalTextStyle.current.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        color = EolssuTheme.colors.textMuted,
    )
}
