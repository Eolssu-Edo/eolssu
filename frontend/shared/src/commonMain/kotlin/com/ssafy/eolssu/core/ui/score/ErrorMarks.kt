package com.ssafy.eolssu.core.ui.score

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.ScoreErrorKind
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.result_tag_length
import eolssu.shared.generated.resources.result_tag_missed
import eolssu.shared.generated.resources.result_tag_pitch
import eolssu.shared.generated.resources.result_tag_rhythm
import org.jetbrains.compose.resources.stringResource

/** 오류 종류의 테두리·바탕·글자 색. 음 길이는 점선 테두리. */
internal data class ErrorStyle(val ring: androidx.compose.ui.graphics.Color, val fill: androidx.compose.ui.graphics.Color, val tag: androidx.compose.ui.graphics.Color, val dashed: Boolean)

@Composable
internal fun errorStyle(kind: ScoreErrorKind): ErrorStyle {
    val c = EolssuTheme.colors
    return when (kind) {
        ScoreErrorKind.Pitch -> ErrorStyle(c.pitchError, c.pitchErrorSoft, c.pitchErrorText, dashed = false)
        ScoreErrorKind.Rhythm -> ErrorStyle(c.rhythmError, c.rhythmErrorSoft, c.rhythmErrorText, dashed = false)
        ScoreErrorKind.Length -> ErrorStyle(c.text, c.surfaceStrong, c.text, dashed = true)
        ScoreErrorKind.Missed -> ErrorStyle(c.text, c.surfaceStrong, c.text, dashed = false)
    }
}

/** 보드 결과 `.ms .ms-cell.err`: 종류와 상관없이 흰 바탕에 붉은 2dp 안쪽 테두리, 붉은 글자 상자(`.tag`)에 흰 글자. */
@Composable
internal fun boardErrorStyle(): ErrorStyle {
    val c = EolssuTheme.colors
    return ErrorStyle(c.rec, Color.Transparent, c.onRec, dashed = false)
}

@Composable
fun errorTag(kind: ScoreErrorKind): String = stringResource(
    when (kind) {
        ScoreErrorKind.Pitch -> Res.string.result_tag_pitch
        ScoreErrorKind.Rhythm -> Res.string.result_tag_rhythm
        ScoreErrorKind.Length -> Res.string.result_tag_length
        ScoreErrorKind.Missed -> Res.string.result_tag_missed
    },
)

/**
 * 오류 종류 색으로 칠한 상자(필터 견본·고른 칸 표시). [kind]가 null이면 [fallback] 바탕만 칠한다.
 */
@Composable
fun ErrorMarkBox(
    kind: ScoreErrorKind?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 3.dp,
    fallback: Color = EolssuTheme.colors.surface,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val style = kind?.let { errorStyle(it) }
    Box(
        modifier
            .background(style?.fill ?: fallback, RoundedCornerShape(cornerRadius))
            .drawBehind {
                if (style == null) return@drawBehind
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    style.ring,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                    style = Stroke(stroke, pathEffect = if (style.dashed) PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 1.5.dp.toPx())) else null),
                )
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}
