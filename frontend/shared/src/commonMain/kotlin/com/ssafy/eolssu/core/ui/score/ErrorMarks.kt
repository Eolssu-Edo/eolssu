package com.ssafy.eolssu.core.ui.score

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.ScoreErrorKind
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.result_legend_missed_short
import eolssu.shared.generated.resources.result_metric_length
import eolssu.shared.generated.resources.result_metric_pitch
import eolssu.shared.generated.resources.result_metric_rhythm
import eolssu.shared.generated.resources.result_tag_length
import eolssu.shared.generated.resources.result_tag_missed
import eolssu.shared.generated.resources.result_tag_pitch
import eolssu.shared.generated.resources.result_tag_rhythm
import org.jetbrains.compose.resources.stringResource

/*
 * 틀린 종류 표시(2차 결합본 `screens.py`의 CELL_FILL · ICON_P/R/L · legend_chip). 1차 결과 RESULT-02도 같은 방식으로 바꿨다.
 * 음정 = 바탕 #F6E7D2 · 테두리 #BB7B43 · 음표, 박자 = #E3E9F9 · #6475C5 · 시계, 음 길이 = #ECECEA · #2C2C2C 점선 · 양쪽 화살표.
 * 곁줄(가사 칸)은 어떤 표시도 칠하지 않는다.
 */

/** 오류 종류의 테두리(아이콘 상자 바탕)·정간 바탕 색과 점선 여부. */
internal data class ErrorStyle(val ring: Color, val fill: Color, val dashed: Boolean)

@Composable
internal fun errorStyle(kind: ScoreErrorKind): ErrorStyle {
    val c = EolssuTheme.colors
    return when (kind) {
        ScoreErrorKind.Pitch -> ErrorStyle(c.pitchError, c.pitchErrorSoft, dashed = false)
        ScoreErrorKind.Rhythm -> ErrorStyle(c.rhythmError, c.rhythmErrorSoft, dashed = false)
        ScoreErrorKind.Length -> ErrorStyle(c.lengthError, c.lengthErrorSoft, dashed = true)
        // 음 미검출은 2차 시안에 없다. 다른 종류와 섞이지 않게 회색으로 둔다.
        ScoreErrorKind.Missed -> ErrorStyle(c.textMuted, c.surfaceStrong, dashed = false)
    }
}

/**
 * 정간 하나의 꾸밈. [JeongganboView]의 `decorations`로 넘긴다. 곁줄에는 적용하지 않는다.
 *
 * @property fill 정간 바탕.
 * @property ring 안쪽 테두리. [dashed]이면 점선이다.
 * @property tag 오른쪽 위 14dp 아이콘 상자로 보일 틀린 종류.
 * @property alpha 정간 불투명도(구간 밖 흐리게 등). 고른 정간은 흐리게 하지 않는다.
 * @property edgeLabel 왼쪽 아래 연두 알약 글자(구간 ‘처음’·‘끝’).
 */
@Immutable
data class ScoreCellDecoration(
    val fill: Color? = null,
    val ring: Color? = null,
    val ringWidth: Dp = 2.dp,
    val dashed: Boolean = false,
    val tag: ScoreErrorKind? = null,
    val alpha: Float = 1f,
    val edgeLabel: String? = null,
)

/** 틀린 정간을 그리는 방식. [Kind]는 종류별 색·아이콘, [Plain]은 종류 없이 옅은 붉은 바탕 + 붉은 테두리(기록 상세 2차). */
enum class ErrorMarkStyle { Kind, Plain }

/** 틀린 정간 꾸밈. [Kind]는 2dp 안쪽 테두리(음 길이 점선)와 아이콘, [Plain]은 1.5dp 붉은 테두리만. */
@Composable
fun errorDecoration(kind: ScoreErrorKind, style: ErrorMarkStyle = ErrorMarkStyle.Kind): ScoreCellDecoration = when (style) {
    ErrorMarkStyle.Kind -> errorStyle(kind).let { ScoreCellDecoration(fill = it.fill, ring = it.ring, dashed = it.dashed, tag = kind) }
    ErrorMarkStyle.Plain -> ScoreCellDecoration(fill = EolssuTheme.colors.recFaint, ring = EolssuTheme.colors.rec, ringWidth = 1.5.dp)
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

/** 틀린 종류 이름(음정·박자·음 길이·미검출). */
@Composable
fun errorKindLabel(kind: ScoreErrorKind): String = stringResource(
    when (kind) {
        ScoreErrorKind.Pitch -> Res.string.result_metric_pitch
        ScoreErrorKind.Rhythm -> Res.string.result_metric_rhythm
        ScoreErrorKind.Length -> Res.string.result_metric_length
        ScoreErrorKind.Missed -> Res.string.result_legend_missed_short
    },
)

/** 틀린 종류 아이콘(12×12): 음정 = 음표, 박자 = 시계, 음 길이 = 양쪽 화살표. 미검출은 아이콘이 없다. */
fun errorKindIcon(kind: ScoreErrorKind): ImageVector? = when (kind) {
    ScoreErrorKind.Pitch -> ErrorIcons.Pitch
    ScoreErrorKind.Rhythm -> ErrorIcons.Rhythm
    ScoreErrorKind.Length -> ErrorIcons.Length
    ScoreErrorKind.Missed -> null
}

/**
 * 틀린 종류 아이콘 상자(`.tag`): 종류 색 바탕에 흰 아이콘. 정간 안은 14dp·반지름 3, 범례는 16dp·반지름 4.
 * [describe]이면 화면 읽기 프로그램이 종류 이름을 읽는다.
 */
@Composable
fun ErrorKindTag(kind: ScoreErrorKind, modifier: Modifier = Modifier, size: Dp = 14.dp, corner: Dp = 3.dp, describe: Boolean = false) {
    val style = errorStyle(kind)
    val label = errorKindLabel(kind)
    val icon = errorKindIcon(kind)
    Box(
        modifier
            .size(size)
            .background(style.ring, RoundedCornerShape(corner))
            .then(if (describe) Modifier.semantics { contentDescription = label } else Modifier.clearAndSetSemantics {}),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color.White)
        } else {
            Text(
                errorTag(kind),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}

/**
 * 오류 종류 색으로 칠한 상자(필터 견본·고른 정간 표시·범례 칸 색 견본). [kind]가 null이면 [fallback] 바탕만 칠한다.
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

/**
 * 틀린 종류 범례(`err_legend`): [title](예: ‘틀린 곳’ · ‘지난번 틀린 곳’) 뒤에 종류마다 칸 색 견본(16dp) · 아이콘 상자(16dp) · 이름(12sp).
 * [boxed]이면 바탕색 상자(반지름 10, 안쪽 8·10)에 담는다. [showMissed]이면 미검출을 더한다.
 * [dense]이면 간격을 줄여(항목 8 · 견본 4 · 안쪽 8) 375dp 폭에서도 한 줄에 들어가게 한다(구간 반복 · 시안 390px에서 한 줄).
 */
@Composable
fun ErrorKindLegend(modifier: Modifier = Modifier, title: String? = null, boxed: Boolean = false, showMissed: Boolean = false, dense: Boolean = false) {
    val colors = EolssuTheme.colors
    val kinds = buildList {
        add(ScoreErrorKind.Pitch)
        add(ScoreErrorKind.Rhythm)
        add(ScoreErrorKind.Length)
        if (showMissed) add(ScoreErrorKind.Missed)
    }
    val text = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp)
    FlowRow(
        modifier.then(if (boxed) Modifier.background(colors.background, RoundedCornerShape(10.dp)).padding(horizontal = if (dense) 8.dp else 10.dp, vertical = 8.dp) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(if (dense) 8.dp else if (boxed) 14.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (title != null) Text(title, style = text, color = colors.textMuted)
        kinds.forEach { kind ->
            Row(horizontalArrangement = Arrangement.spacedBy(if (dense) 4.dp else 5.dp), verticalAlignment = Alignment.CenterVertically) {
                ErrorMarkBox(kind, Modifier.size(16.dp), cornerRadius = 2.dp)
                ErrorKindTag(kind, size = 16.dp, corner = 4.dp)
                Text(errorKindLabel(kind), style = text, color = if (boxed) colors.text else colors.textSub)
            }
        }
    }
}

/** 시안의 12×12 아이콘을 옮겼다(`screens.py` ICON_P · ICON_R · ICON_L). */
private object ErrorIcons {
    val Pitch = icon("ErrorPitch", 1.5f, listOf("M7.6 8.6V1.6c.9 1.2 2.6 1.6 2.6 3.6"), filled = listOf("M3.1 8.9a2.3 1.8 0 1 0 4.6 0a2.3 1.8 0 1 0 -4.6 0z"))
    val Rhythm = icon("ErrorRhythm", 1.6f, listOf("M1.4 6a4.6 4.6 0 1 0 9.2 0a4.6 4.6 0 1 0 -9.2 0z", "M6 3.4V6l1.9 1.3"))
    val Length = icon("ErrorLength", 1.7f, listOf("M1.6 6h8.8", "M3.8 3.6L1.4 6l2.4 2.4", "M8.2 3.6L10.6 6L8.2 8.4"))

    private fun icon(name: String, width: Float, strokes: List<String>, filled: List<String> = emptyList()): ImageVector {
        val builder = ImageVector.Builder(name, 12.dp, 12.dp, 12f, 12f)
        strokes.forEach {
            builder.addPath(
                pathData = PathParser().parsePathString(it).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        filled.forEach { builder.addPath(pathData = PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.Black)) }
        return builder.build()
    }
}
