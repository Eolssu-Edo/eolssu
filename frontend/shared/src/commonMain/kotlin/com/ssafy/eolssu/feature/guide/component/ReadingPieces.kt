package com.ssafy.eolssu.feature.guide.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.score.ComposedGlyph
import com.ssafy.eolssu.feature.guide.BasicSigimsae
import com.ssafy.eolssu.feature.guide.MoreSigimsae
import com.ssafy.eolssu.feature.guide.SigimsaeItem
import com.ssafy.eolssu.feature.guide.SigimsaeSymbol
import com.ssafy.eolssu.feature.guide.ReadingAudioClip
import com.ssafy.eolssu.feature.guide.ReadingAudioState
import com.ssafy.eolssu.feature.guide.ReadingLengthCells
import com.ssafy.eolssu.feature.guide.ReadingLengthClips
import com.ssafy.eolssu.feature.guide.ReadingPitchClips
import com.ssafy.eolssu.feature.guide.readingActiveNote
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_reading_intro_cell
import eolssu.shared.generated.resources.guide_reading_intro_cell_desc
import eolssu.shared.generated.resources.guide_reading_intro_figure
import eolssu.shared.generated.resources.guide_reading_intro_lead
import eolssu.shared.generated.resources.guide_reading_intro_strokes
import eolssu.shared.generated.resources.guide_reading_intro_strokes_desc
import eolssu.shared.generated.resources.guide_reading_length_cap_each
import eolssu.shared.generated.resources.guide_reading_length_cap_hold
import eolssu.shared.generated.resources.guide_reading_length_cap_rest
import eolssu.shared.generated.resources.guide_reading_length_cap_split
import eolssu.shared.generated.resources.guide_reading_length_key
import eolssu.shared.generated.resources.guide_reading_length_lead
import eolssu.shared.generated.resources.reading_length_listen_hint
import eolssu.shared.generated.resources.guide_reading_pitch_axis_high
import eolssu.shared.generated.resources.guide_reading_pitch_axis_low
import eolssu.shared.generated.resources.guide_reading_pitch_base
import eolssu.shared.generated.resources.guide_reading_pitch_base_gloss
import eolssu.shared.generated.resources.guide_reading_pitch_base_term
import eolssu.shared.generated.resources.guide_reading_pitch_caption
import eolssu.shared.generated.resources.guide_reading_pitch_high
import eolssu.shared.generated.resources.guide_reading_pitch_high_gloss
import eolssu.shared.generated.resources.guide_reading_pitch_high_term
import eolssu.shared.generated.resources.guide_reading_pitch_lead
import eolssu.shared.generated.resources.guide_reading_pitch_low
import eolssu.shared.generated.resources.guide_reading_pitch_low_gloss
import eolssu.shared.generated.resources.guide_reading_pitch_low_term
import eolssu.shared.generated.resources.guide_sigimsae_basic
import eolssu.shared.generated.resources.guide_sigimsae_tip_closed
import eolssu.shared.generated.resources.guide_sigimsae_tip_open
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/*
 * 정간보 쉽게 읽기의 쪽 카드와 그림 조각. 기초부터 배우기 7·8·9단계가 같은 조각을 가져다 쓴다(설명은 한 곳에만 둔다).
 * 그림 하나가 한 가지를 보여 준다: 정간보란은 井 획, 1 읽는 순서는 차례로 채워지는 선([ReadOrderBoard]), 2 길이는 소리 막대, 3 높낮이는 계단.
 */

/** 학습 보드의 옅은 선 · 글자(`--faint` #B9B3A6). 테마에 없는 색이라 학습 화면 조각에만 둔다. */
internal val GuideFaint = Color(0xFFB9B3A6)

/**
 * 설명 카드(`.gcard`): 흰 바탕에 옅은 테두리. [mark]가 있으면 연두 원 표시와 [title]을,
 * [small]이 있으면 그 옆에 작은 회색 글을 단다. 카드가 내용보다 높으면 [verticalArrangement]대로 내용을 놓는다.
 */
@Composable
internal fun GuideCard(
    modifier: Modifier = Modifier,
    mark: String? = null,
    title: String? = null,
    small: String? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = EolssuTheme.colors
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .padding(horizontal = GuideCardInset, vertical = 16.dp),
        verticalArrangement = verticalArrangement,
    ) {
        if (title != null) {
            Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mark != null) PageMark(mark)
                Text(
                    buildAnnotatedString {
                        append(title)
                        if (small != null) {
                            append("  ")
                            withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textMuted)) { append(small) }
                        }
                    },
                    // `.gcard h3`: 본문 글꼴 굵게 16(제목 글꼴 아님)
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
        content()
    }
}

/** [GuideCard] 안쪽 좌우 여백. */
private val GuideCardInset = 14.dp

/** 카드 안쪽 좌우 여백([GuideCardInset])만큼 양옆으로 넓혀 카드 끝까지 닿게 한다. 넓힌 만큼 안쪽 여백은 부르는 쪽이 다시 준다. */
private fun Modifier.bleedToCardEdges(): Modifier = layout { measurable, constraints ->
    val inset = GuideCardInset.roundToPx()
    val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + inset * 2 else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(minWidth = (constraints.minWidth + inset * 2).coerceAtMost(maxWidth), maxWidth = maxWidth))
    layout(placeable.width - inset * 2, placeable.height) { placeable.place(-inset, 0) }
}

/** 쪽 표시(`.rb-mark`): 연두 원 안의 번호. 정간보란의 井은 세리프로 쓴다. */
@Composable
internal fun PageMark(mark: String, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val han = mark.firstOrNull()?.isDigit() == false
    Box(modifier.size(24.dp).background(colors.primary, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            mark,
            style = if (han) {
                EolssuTheme.serif.yul.copy(fontSize = 13.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
            } else {
                MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
            },
            color = colors.onPrimary,
        )
    }
}

/**
 * 쪽 맨 위 설명 한 줄(`.page-lead`, 글자색은 `.lead`의 #5C564F). 줄을 바꾸지 않고 카드 너비에 맞춰 최대 18sp까지 키운다.
 * 태블릿 격자가 카드 높이를 맞추려고 크기를 미리 재므로 BoxWithConstraints 대신 글자 자동 크기를 쓴다.
 */
@Composable
internal fun PageLead(raw: String, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val serif = EolssuTheme.serif.yul.fontFamily ?: FontFamily.Serif
    val text = remember(raw, serif) { guideAnnotated(raw, serif) }
    BasicText(
        text,
        modifier.fillMaxWidth().padding(bottom = 14.dp),
        style = MaterialTheme.typography.bodyLarge.copy(color = colors.textSub, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 18.sp, stepSize = 0.5.sp),
    )
}

/* ───────── 그림 공용: SVG 좌표(viewBox)를 그대로 쓰는 그리기 ───────── */

/** viewBox 한 단위를 화면 픽셀로 바꾸는 배율을 받아 그린다. */
internal class FigureScope(val draw: DrawScope, val scale: Float, private val measurer: TextMeasurer) {
    fun u(v: Float): Float = v * scale

    /** SVG `<text text-anchor="middle">`: [x]를 가운데로, [baseline]을 글자 기준선으로 둔다. [halo]이면 흰 테두리를 먼저 그린다. */
    fun text(
        value: String,
        x: Float,
        baseline: Float,
        style: TextStyle,
        sizeUnits: Float,
        color: Color,
        halo: Color? = null,
        haloUnits: Float = 0f,
        glow: Color? = null,
    ) {
        val layout = measurer.measure(value, style.copy(fontSize = with(draw) { u(sizeUnits).toSp() }, lineHeight = TextUnit.Unspecified))
        val topLeft = Offset(u(x) - layout.size.width / 2f, u(baseline) - layout.firstBaseline)
        // 글자 붓의 그리기 방식은 넘기지 않으면 앞의 값이 남는다. 테두리(Stroke) 뒤의 글자는 Fill을, 번짐 뒤에는 Shadow.None을 꼭 넘긴다(넘기지 않으면 글자색 굵은 덩어리가 된다)
        if (halo != null) {
            draw.drawText(layout, color = halo, topLeft = topLeft, shadow = Shadow.None, drawStyle = Stroke(u(haloUnits), join = StrokeJoin.Round))
        }
        if (glow != null) {
            val shadow = Shadow(glow, blurRadius = u(6f))
            repeat(2) { draw.drawText(layout, color = color, topLeft = topLeft, shadow = shadow, drawStyle = Fill) }
        }
        draw.drawText(layout, color = color, topLeft = topLeft, shadow = Shadow.None, drawStyle = Fill)
    }
}

/**
 * viewBox [width]×[height] 그림을 너비에 맞춰 가운데에 그린다(최대 [maxWidth]). [description]은 화면 읽기 이름이다.
 * 태블릿 격자가 카드 높이를 미리 잴 때도 최대 너비로 줄인 높이를 알려 준다(widthIn · aspectRatio는 미리 잴 때 최대 너비를 보지 않는다).
 */
@Composable
internal fun Figure(
    width: Float,
    height: Float,
    maxWidth: Dp,
    description: String,
    modifier: Modifier = Modifier,
    onDraw: FigureScope.() -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val ratio = width / height
    Layout(
        content = {
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = description }) {
                FigureScope(this, size.width / width, measurer).onDraw()
            }
        },
        modifier = modifier.fillMaxWidth(),
        measurePolicy = remember(maxWidth, ratio) { FigurePolicy(maxWidth, ratio) },
    )
}

private class FigurePolicy(private val maxWidth: Dp, private val ratio: Float) : MeasurePolicy {
    private fun IntrinsicMeasureScope.figureWidth(available: Int): Int =
        if (available == Constraints.Infinity) maxWidth.roundToPx() else minOf(available, maxWidth.roundToPx())

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val w = figureWidth(constraints.maxWidth)
        val h = (w / ratio).roundToInt()
        val canvas = measurables.single().measure(Constraints.fixed(w, h))
        val outer = if (constraints.hasBoundedWidth) constraints.maxWidth else w
        return layout(outer, h) { canvas.place((outer - w) / 2, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int = (figureWidth(width) / ratio).roundToInt()

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int = (figureWidth(width) / ratio).roundToInt()

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int = 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int = maxWidth.roundToPx()
}

/* ───────── 정간보란 ───────── */

/**
 * 정간보란: 큰 井의 네 획을 3×3 칸 선 위에 겹쳐, 井 가운데 연두 네모가 곧 한 칸(정간)임을 보여 준다. 아래에 범례.
 * [figureMaxWidth]는 井 그림의 최대 너비다. 휴대폰 책은 카드 높이를 맞춰 남는 자리에 그림을 키운다.
 */
@Composable
internal fun ReadIntro(figureMaxWidth: Dp = 170.dp) {
    val colors = EolssuTheme.colors
    val serif = EolssuTheme.serif.yul
    PageLead(stringResource(Res.string.guide_reading_intro_lead))
    Figure(
        width = 152f,
        height = 144f,
        maxWidth = figureMaxWidth,
        description = stringResource(Res.string.guide_reading_intro_figure),
        modifier = Modifier.padding(bottom = 14.dp),
    ) {
        val c = 40f
        val gx = { i: Int -> 16f + c * i }
        val gy = { i: Int -> 12f + c * i }
        val d = draw
        d.drawRoundRect(colors.surface, Offset(u(gx(0)), u(gy(0))), Size(u(c * 3), u(c * 3)), CornerRadius(u(6f)))
        d.drawRoundRect(
            GuideFaint,
            Offset(u(gx(0)), u(gy(0))),
            Size(u(c * 3), u(c * 3)),
            CornerRadius(u(6f)),
            style = Stroke(u(1f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(u(3f), u(3f)))),
        )
        val faint = listOf(listOf("林", "太", "黃"), listOf("南", null, "ㅡ"), listOf("潢", "△", "仲"))
        faint.forEachIndexed { r, row ->
            row.forEachIndexed { k, t ->
                if (t != null) text(t, gx(k) + c / 2, gy(r) + c / 2 + 6, serif, 17f, GuideFaint)
            }
        }
        d.drawRoundRect(colors.accentTintStrong, Offset(u(gx(1) + 3), u(gy(1) + 3)), Size(u(c - 6), u(c - 6)), CornerRadius(u(4f)))
        text("黃", gx(1) + c / 2, gy(1) + c / 2 + 8, serif.copy(fontWeight = FontWeight.Bold), 24f, colors.text)
        val jeong = Stroke(u(6f), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val strokes = Path().apply {
            listOf(gy(1), gy(2)).forEach { y -> moveTo(u(gx(0) - 8), u(y)); lineTo(u(gx(3) + 8), u(y)) }
            moveTo(u(gx(2)), u(gy(0) - 8)); lineTo(u(gx(2)), u(gy(3) + 8))
            // 왼쪽 세로 획은 붓글씨처럼 끝을 왼쪽 아래로 삐친다
            moveTo(u(gx(1)), u(gy(0) - 8)); lineTo(u(gx(1)), u(gy(3) - 6))
            quadraticTo(u(gx(1) - 1), u(gy(3) + 4), u(gx(1) - 12), u(gy(3) + 9))
        }
        d.drawPath(strokes, colors.text, style = jeong)
    }
    IntroLegend()
}

/** 정간보란 범례(`.ip-legend`): 앞의 작은 표시가 그림 속 굵은 획 · 가운데 칸과 같은 모양이다. 용어 · 쌍점 · 뜻을 세로로 맞춘다. */
@Composable
private fun IntroLegend() {
    val colors = EolssuTheme.colors
    val rows = listOf(
        Triple<@Composable () -> Unit, String, String>(
            { Box(Modifier.size(width = 14.dp, height = 4.dp).background(colors.text, RoundedCornerShape(2.dp))) },
            stringResource(Res.string.guide_reading_intro_strokes),
            stringResource(Res.string.guide_reading_intro_strokes_desc),
        ),
        Triple<@Composable () -> Unit, String, String>(
            {
                Box(
                    Modifier.size(14.dp).background(colors.accentTintStrong, RoundedCornerShape(3.dp)).border(1.dp, GuideFaint, RoundedCornerShape(3.dp)),
                )
            },
            stringResource(Res.string.guide_reading_intro_cell),
            stringResource(Res.string.guide_reading_intro_cell_desc),
        ),
    )
    val style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Layout(
            content = {
                rows.forEach { (icon, term, _) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        icon()
                        GuideText(term, style = style.copy(fontWeight = FontWeight.Bold))
                    }
                }
                rows.forEach { (_, _, desc) ->
                    Row {
                        Text(":", Modifier.width(14.dp), style = style, color = GuideFaint)
                        Text(desc, style = style, color = colors.textMuted)
                    }
                }
            },
            modifier = Modifier.background(colors.surface, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
            measurePolicy = LegendGridPolicy,
        )
    }
}

/**
 * 두 칸 표: 앞 절반은 용어, 뒤 절반은 같은 줄의 뜻이다. 용어 칸은 가장 긴 용어 너비, 뜻 칸은 남은 너비(줄이 바뀌면 쌍점 뒤로 맞춘다).
 * 태블릿 격자가 카드 높이를 미리 재므로 높이 계산(intrinsic)도 같은 규칙으로 한다. 기본 계산은 용어에 줄 너비를 다 줘 뜻이 한 글자씩 꺾인다.
 */
private object LegendGridPolicy : MeasurePolicy {
    private val Gap = 10.dp
    private val RowGap = 8.dp

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val n = measurables.size / 2
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val terms = measurables.take(n).map { it.measure(loose) }
        val termWidth = terms.maxOf { it.width }
        val descMax = (constraints.maxWidth - termWidth - Gap.roundToPx()).coerceAtLeast(0)
        val descs = measurables.drop(n).map { it.measure(loose.copy(maxWidth = descMax)) }
        val heights = terms.indices.map { maxOf(terms[it].height, descs[it].height) }
        val width = termWidth + Gap.roundToPx() + descs.maxOf { it.width }
        return layout(width, heights.sum() + RowGap.roundToPx() * (n - 1)) {
            var y = 0
            terms.indices.forEach { i ->
                terms[i].place(0, y)
                descs[i].place(termWidth + Gap.roundToPx(), y)
                y += heights[i] + RowGap.roundToPx()
            }
        }
    }

    private fun IntrinsicMeasureScope.height(measurables: List<IntrinsicMeasurable>, width: Int): Int {
        val n = measurables.size / 2
        val termWidth = measurables.take(n).maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
        val descWidth = if (width == Constraints.Infinity) width else (width - termWidth - Gap.roundToPx()).coerceAtLeast(0)
        return (0 until n).sumOf { i ->
            maxOf(measurables[i].minIntrinsicHeight(termWidth), measurables[n + i].minIntrinsicHeight(descWidth))
        } + RowGap.roundToPx() * (n - 1)
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int = height(measurables, width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int = height(measurables, width)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int = width(measurables)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int = width(measurables)

    private fun IntrinsicMeasureScope.width(measurables: List<IntrinsicMeasurable>): Int {
        val n = measurables.size / 2
        return measurables.take(n).maxOf { it.maxIntrinsicWidth(Constraints.Infinity) } + Gap.roundToPx() +
            measurables.drop(n).maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
    }
}

/* ───────── 2 음의 길이 ───────── */

/**
 * 두 칸에서 소리가 나는 구간([시작, 길이], 칸 단위 0~2). 칸 안의 음은 칸을 똑같이 나누고,
 * ㅡ 는 앞 구간을 늘이며 △ 는 비운다.
 */
internal fun soundSpans(cells: List<List<String>>): List<Pair<Float, Float>> {
    val spans = mutableListOf<Pair<Float, Float>>()
    var t = 0f
    cells.forEach { notes ->
        val d = 1f / notes.size
        notes.forEach { n ->
            when {
                n == "ㅡ" && spans.isNotEmpty() -> spans[spans.lastIndex] = spans.last().let { it.first to it.second + d }
                n != "△" && n != "ㅡ" -> spans += t to d
            }
            t += d
        }
    }
    return spans
}

private fun isSymbol(text: String) = text == "ㅡ" || text == "△"

/** 2 음의 길이(`.len-row`): 정간 두 칸 옆 막대가 소리 나는 시간. 한 칸씩 · 반씩 나누기 · ㅡ 이어 가기 · △ 쉬기를 2×2로. */
@Composable
internal fun LengthRow(
    modifier: Modifier = Modifier,
    audio: ReadingAudioState = ReadingAudioState(),
    onPlayAudio: ((ReadingAudioClip) -> Unit)? = null,
    enabled: Boolean = true,
) {
    val colors = EolssuTheme.colors
    val captions = listOf(
        stringResource(Res.string.guide_reading_length_cap_each),
        stringResource(Res.string.guide_reading_length_cap_split),
        stringResource(Res.string.guide_reading_length_cap_hold),
        stringResource(Res.string.guide_reading_length_cap_rest),
    )
    Column(modifier.fillMaxWidth()) {
        PageLead(stringResource(Res.string.guide_reading_length_lead))
        if (onPlayAudio != null) Text(
            stringResource(Res.string.reading_length_listen_hint),
            Modifier.padding(bottom = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        GuideGrid(ReadingLengthCells, 2, horizontalGap = 8.dp, verticalGap = 8.dp) { i, cells, cell ->
            val clip = ReadingLengthClips[i]
            LengthExample(cells, captions[i], cell, clip, audio, onPlayAudio, enabled)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(width = 6.dp, height = 14.dp).background(colors.primary, RoundedCornerShape(3.dp)))
            Text(stringResource(Res.string.guide_reading_length_key), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
        }
        ReadingAudioError(audio, ReadingLengthClips)
    }
}

@Composable
private fun LengthExample(
    cells: List<List<String>>, caption: String, modifier: Modifier,
    clip: ReadingAudioClip, audio: ReadingAudioState,
    onPlayAudio: ((ReadingAudioClip) -> Unit)?, enabled: Boolean,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(8.dp)
    val active = audio.active(clip)
    val position = audio.positionMs.takeIf { audio.clip == clip && audio.playing }
    val note = position?.let { readingActiveNote(cells, it) }
    Column(
        modifier
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, if (active) colors.primary else colors.lineSoft, RoundedCornerShape(12.dp))
            .padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.width(50.dp).clip(shape).background(colors.surface).border(1.dp, GuideFaint, shape)) {
                cells.forEachIndexed { i, notes ->
                    if (i > 0) HorizontalDivider(color = colors.lineSoft)
                    Column(Modifier.fillMaxWidth().height(50.dp)) {
                        notes.forEachIndexed { k, text ->
                            Box(
                                Modifier.fillMaxWidth().weight(1f)
                                    .then(if (k > 0) Modifier.dashedTopLine(colors.lineSoft) else Modifier)
                                    .readingNoteGlow(note == (i to k)),
                                contentAlignment = Alignment.Center,
                            ) {
                                val size = if (notes.size > 1) 16.sp else 22.sp
                                // 율명은 세리프, ㅡ · △ 기호는 본문 글꼴의 회색(`.len-cell span.sym`)
                                Text(
                                    text,
                                    style = if (isSymbol(text)) {
                                        MaterialTheme.typography.bodyLarge.copy(fontSize = size, lineHeight = size, fontWeight = FontWeight.Normal)
                                    } else {
                                        EolssuTheme.serif.yul.copy(fontSize = size, lineHeight = size, fontWeight = FontWeight.SemiBold)
                                    },
                                    color = if (isSymbol(text)) colors.textMuted else colors.text,
                                )
                            }
                        }
                    }
                }
            }
            SoundBar(soundSpans(cells), Modifier.width(8.dp).fillMaxHeight())
        }
        if (onPlayAudio != null) ReadingBeatMarkers(position)
        Text(
            caption,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
        if (onPlayAudio != null) ReadingListenButton(clip, caption, audio, { onPlayAudio(clip) }, enabled)
    }
}

/** 소리 막대(`.len-bar`): 가운데 점선 위에 소리 나는 구간을 연두 막대로 칠한다. 구간 위아래는 3dp씩 띄운다. */
@Composable
private fun SoundBar(spans: List<Pair<Float, Float>>, modifier: Modifier) {
    val colors = EolssuTheme.colors
    Canvas(modifier) {
        val dash = 3.dp.toPx()
        drawLine(
            colors.lineSoft,
            Offset(size.width / 2, 0f),
            Offset(size.width / 2, size.height),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
        )
        val gap = 3.dp.toPx()
        spans.forEach { (start, length) ->
            val top = start / 2f * size.height + gap
            val h = length / 2f * size.height - gap * 2
            drawRoundRect(colors.primary, Offset(0f, top), Size(size.width, h), CornerRadius(4.dp.toPx()))
        }
    }
}

private fun Modifier.dashedTopLine(color: Color): Modifier = drawBehind {
    drawLine(
        color,
        Offset(0f, 0f),
        Offset(size.width, 0f),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
    )
}

/* ───────── 3 음의 높낮이 ───────── */

/** 계단 한 칸: [level] 0~2(낮음→높음), 율명 [base]와 왼쪽에 붙는 [radical](없으면 기준 음). */
private data class PitchStep(val level: Int, val radical: String, val base: String, val name: String, val term: String, val gloss: String)

/** 한 단 높이(`.pit.lv*`의 margin-top 차이). */
private val PitchRise: Dp = 32.dp

/** 3 음의 높낮이(`.pit-row`): 디딤판과 챌판이 이어진 계단으로 높이를 보여 주고, 율명 왼쪽에 붙는 부수만 이끼색으로 칠한다. */
@Composable
internal fun PitchSteps(audio: ReadingAudioState, onPlayAudio: (ReadingAudioClip) -> Unit, enabled: Boolean = true) {
    val colors = EolssuTheme.colors
    PageLead(stringResource(Res.string.guide_reading_pitch_lead))
    val steps = listOf(
        PitchStep(0, "亻", "黃", stringResource(Res.string.guide_reading_pitch_low), stringResource(Res.string.guide_reading_pitch_low_term), stringResource(Res.string.guide_reading_pitch_low_gloss)),
        PitchStep(1, "", "黃", stringResource(Res.string.guide_reading_pitch_base), stringResource(Res.string.guide_reading_pitch_base_term), stringResource(Res.string.guide_reading_pitch_base_gloss)),
        PitchStep(2, "氵", "黃", stringResource(Res.string.guide_reading_pitch_high), stringResource(Res.string.guide_reading_pitch_high_term), stringResource(Res.string.guide_reading_pitch_high_gloss)),
    )
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.Center) {
        PitchAxis(Modifier.width(32.dp).fillMaxHeight().padding(end = 12.dp))
        steps.forEach { PitchStepCell(it) }
    }
    // 디딤판 높이와 관계없이 세 스피커의 중심을 같은 가로선에 둔다.
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
        Spacer(Modifier.width(32.dp))
        steps.forEachIndexed { i, step ->
            Box(Modifier.width(82.dp), contentAlignment = Alignment.Center) {
                ReadingSpeakerButton(ReadingPitchClips[i], step.name, audio, { onPlayAudio(ReadingPitchClips[i]) }, enabled)
            }
        }
    }
    val raw = stringResource(Res.string.guide_reading_pitch_caption)
    val serif = EolssuTheme.serif.yul.fontFamily ?: FontFamily.Serif
    val caption = remember(raw, serif, colors) {
        val base = guideAnnotated(raw, serif, colors.text)
        buildAnnotatedString {
            append(base)
            // 홀로 쓴 부수는 작게 그려져 크게 키운다. 24sp 이상이라 이끼색이 큰 글자 대비(3:1)를 넘는다
            raw.replace("**", "").forEachIndexed { i, ch ->
                if (ch == '氵' || ch == '亻') addStyle(SpanStyle(color = colors.radical, fontSize = 24.sp, fontFamily = serif, fontWeight = FontWeight.Bold), i, i + 1)
            }
        }
    }
    Text(
        caption,
        Modifier.fillMaxWidth().padding(top = 12.dp),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 26.sp),
        color = colors.textMuted,
        textAlign = TextAlign.Center,
    )
    ReadingAudioError(audio, ReadingPitchClips)
}

/** 왼쪽 축(`.pit-axis`): 위 '높게' · 아래로 옅어지는 선 · 아래 '낮게'. '낮게'가 가장 낮은 디딤판 높이까지 내려온다. */
@Composable
private fun PitchAxis(modifier: Modifier) {
    val colors = EolssuTheme.colors
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
    Column(modifier.padding(top = 4.dp).offset(y = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.guide_reading_pitch_axis_high), style = style, color = colors.textMuted)
        Box(Modifier.weight(1f).width(1.5.dp).background(Brush.verticalGradient(listOf(colors.textMuted, colors.lineSoft))))
        Text(stringResource(Res.string.guide_reading_pitch_axis_low), style = style, color = colors.textMuted)
    }
}

@Composable
private fun PitchStepCell(step: PitchStep) {
    val colors = EolssuTheme.colors
    val tread = 3.dp
    Column(
        Modifier
            .padding(top = PitchRise * (2 - step.level))
            .width(82.dp)
            .drawBehind {
                // 디딤판(아래 굵은 선)과, 높은 칸이면 왼쪽에 한 단 아래 디딤판까지 내려오는 챌판
                val t = tread.toPx()
                drawRect(colors.text, Offset(0f, size.height - t), Size(size.width, t))
                if (step.level > 0) drawRect(colors.text, Offset(-t / 2, size.height - t), Size(t, PitchRise.toPx() + t))
            }
            .padding(bottom = 8.dp + tread),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.height(52.dp), contentAlignment = Alignment.Center) {
            if (step.radical.isEmpty()) {
                Text(step.base, style = EolssuTheme.serif.yul.copy(fontSize = 30.sp, lineHeight = 30.sp))
            } else {
                ComposedGlyph(step.radical, step.base, 23.sp, 30.sp, overlap = 0.45f, radicalColor = colors.radical, radicalWeight = FontWeight.Bold)
            }
        }
        Text(step.name, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp))
        Text(step.term, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp))
        Text(step.gloss, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.sp), color = colors.textMuted)
    }
}

/* ───────── 4 시김새 ───────── */

/**
 * 시김새 기호 그림. 높이 [height] 안에서 비율을 지키되 너비가 [maxWidth]를 넘으면 그 너비로 줄인다
 * (`.sig-glyph img`의 `height` · `max-width` · `object-fit: contain`).
 */
@Composable
internal fun SigimsaeImage(image: DrawableResource, height: Dp, maxWidth: Dp) {
    val painter = painterResource(image)
    val ratio = painter.intrinsicSize.let { if (it.height > 0f) it.width / it.height else 1f }
    val width = minOf(height * ratio, maxWidth)
    Image(painter, contentDescription = null, modifier = Modifier.size(width, height), contentScale = ContentScale.Fit)
}

/** 창에서 꺼내 쓰도록 처음 배우는 시김새를 무리의 범례 문구와 함께 한 줄로 편다. 목록의 줄 번호가 이 순서를 가리킨다. */
private val BasicSigimsaeEntries: List<Pair<SigimsaeItem, String?>> = BasicSigimsae.flatMap { group -> group.items.map { it to group.hint } }

/**
 * 4 시김새의 '처음 배우는 시김새'(`.sig-block`): 무리마다 기호 · 이름 · 짧은 설명 한 줄. 줄 전체가 단추이고,
 * 누르면 부는 모양을 그리는 창([SigimsaeDialog])을 연다. 닫으면 초점을 누른 줄로 돌려놓는다.
 *
 * @param columns 기호 목록 칸 수. 태블릿은 세로 2칸 · 가로 3칸(`.tabapp .sig-block .sig-list`)이고, 이때는 모든 기호 아래에 선을 긋는다.
 * @param enabled 넘기는 책에서 보이지 않는 쪽이면 거짓이라 누를 수 없다.
 */
@Composable
internal fun SigimsaeBasicBlock(modifier: Modifier = Modifier, columns: Int = 1, enabled: Boolean = true, onOpen: () -> Unit = {}) {
    val colors = EolssuTheme.colors
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    var refocus by remember { mutableStateOf<Int?>(null) }
    val rows = remember { List(BasicSigimsaeEntries.size) { FocusRequester() } }
    Column(modifier.fillMaxWidth()) {
        SectionTitle(stringResource(Res.string.guide_sigimsae_basic), 15.sp)
        var start = 0
        BasicSigimsae.forEach { group ->
            val first = start
            start += group.items.size
            Column(Modifier.padding(top = 16.dp)) {
                Text(group.title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = colors.textMuted)
                if (columns <= 1) {
                    Column(Modifier.padding(top = 4.dp)) {
                        group.items.forEachIndexed { i, item ->
                            SigimsaeRow(item, last = i == group.items.lastIndex, rows[first + i], enabled) { onOpen(); open = first + i }
                        }
                    }
                } else {
                    GuideGrid(group.items, columns, Modifier.padding(top = 4.dp), horizontalGap = 28.dp, verticalGap = 0.dp) { i, item, cell ->
                        SigimsaeRow(item, last = false, rows[first + i], enabled, modifier = cell, stretch = true) { onOpen(); open = first + i }
                    }
                }
            }
        }
    }
    open?.let { index ->
        val (item, hint) = BasicSigimsaeEntries[index]
        SigimsaeDialog(item, hint) {
            open = null
            refocus = index
        }
    }
    LaunchedEffect(refocus) {
        refocus?.let { runCatching { rows[it].requestFocus() } }
        refocus = null
    }
}

/** 묶음 제목. 처음 배우는 시김새(`.sig-head`)는 검은 글자, 더 많은 시김새의 무리(`.sig-sub`)는 회색 글자다. 기호 개수는 달지 않는다(QA). */
@Composable
private fun SectionTitle(title: String, fontSize: TextUnit, modifier: Modifier = Modifier, color: Color = EolssuTheme.colors.text) {
    Text(
        title,
        modifier,
        color = color,
        style = MaterialTheme.typography.titleSmall.copy(fontSize = fontSize, fontWeight = FontWeight.Bold),
    )
}

/**
 * 기호 한 줄(`.sig-item`): 기호 44 · 이름 66 · 설명 · 화살표 18. 이름 칸 너비를 고정해 설명이 모두 같은 세로줄에서 시작한다.
 * 줄 전체가 창을 여는 단추다. [stretch]이면 격자 한 줄의 높이에 맞춰 아래 선을 칸끼리 맞춘다.
 * 한 칸 목록(휴대폰)에서는 목록형으로 줄을 카드 좌우 끝까지 넓혀, 눌림 효과가 줄 전체 사각으로 그려지고 카드 모서리에서만 잘린다(S15P31E102-968).
 */
@Composable
private fun SigimsaeRow(
    item: SigimsaeItem,
    last: Boolean,
    focusRequester: FocusRequester,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    stretch: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = EolssuTheme.colors
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (stretch) Modifier else Modifier.bleedToCardEdges())
                .focusRequester(focusRequester)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .then(if (stretch) Modifier else Modifier.padding(horizontal = GuideCardInset))
                .padding(top = 10.dp, bottom = if (last) 0.dp else if (stretch) 12.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SigimsaeGlyph(item, box = 44.dp, imageHeight = 26.dp, imageMaxWidth = if (item.images.size > 1) 18.dp else 34.dp)
            Text(item.name, Modifier.width(66.dp), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.description, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.6.sp), color = colors.textMuted)
                if (item.chip != null) SigimsaeChip(item.chip)
            }
            Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textMuted)
        }
        if (stretch) Spacer(Modifier.weight(1f))
        if (!last) HorizontalDivider(color = colors.lineSoft)
    }
}

/**
 * '더 많은 시김새' 쪽(`.sig-more`): 35개를 늘 펼쳐 둔다. 칸을 누르면 그 칸이 있는 줄 바로 아래에 이름 · 한 줄 설명 말풍선이 나오고,
 * 같은 칸을 다시 누르면 접힌다. 말풍선 꼬리는 누른 칸의 가운데를 가리킨다.
 *
 * @param selected 설명을 펼친 칸. 두 무리를 이어 센 번호다.
 * @param columns 기호 격자 칸 수. 휴대폰 4칸, 태블릿 세로 6칸 · 가로 9칸(`.tabapp .sig-grid`).
 * @param enabled 넘기는 책에서 보이지 않는 쪽이면 거짓이라 누를 수 없다.
 */
@Composable
internal fun SigimsaeMorePage(selected: Int?, onSelect: (Int?) -> Unit, columns: Int, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Column(modifier.fillMaxWidth()) {
        var start = 0
        MoreSigimsae.forEachIndexed { g, group ->
            val first = start
            start += group.symbols.size
            SectionTitle(group.title, 13.sp, Modifier.padding(top = if (g == 0) 0.dp else 16.dp), color = EolssuTheme.colors.textMuted)
            Column(Modifier.padding(top = 8.dp)) {
                group.symbols.chunked(columns).forEachIndexed { r, row ->
                    if (r > 0) Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEachIndexed { k, symbol ->
                            val index = first + r * columns + k
                            SigimsaeTile(symbol, on = index == selected, enabled, Modifier.weight(1f)) { onSelect(if (index == selected) null else index) }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    val at = selected?.minus(first + r * columns)
                    if (at != null && at in row.indices) SigimsaeTip(row[at], (at + 0.5f) / columns)
                }
            }
        }
    }
}

@Composable
private fun SigimsaeTile(symbol: SigimsaeSymbol, on: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    val state = stringResource(if (on) Res.string.guide_sigimsae_tip_open else Res.string.guide_sigimsae_tip_closed)
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            // 고른 칸은 녹색 테두리로 보인다. 누르는 동안·키보드로 고른 동안의 반응은 칸(그림+이름) 전체의
            // 12dp 둥근 모양 안에만 그려 네모 박스로 튀어나오지 않게 한다(S15P31E102-969).
            .clip(shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { stateDescription = state },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 녹색 테두리 = 고른 칸
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(colors.surface, shape)
                .border(1.5.dp, if (on) colors.green else Color.Transparent, shape),
            contentAlignment = Alignment.Center,
        ) {
            SigimsaeImage(symbol.image, 24.dp, 34.dp)
        }
        Text(
            symbol.name,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 한 줄 전체의 설명 말풍선(`.sig-tip`). 꼬리는 너비의 [at] 비율 자리를 가리킨다.
 * 새 보드에 펼친 모습이 없어, 흰 카드 위에서 보이도록 고른 칸의 녹색 테두리와 같은 계열의 옅은 연두 바탕을 쓴다.
 * 꼬리(12dp 네모를 45° 돌린 위 절반)는 칸 줄과 말풍선 사이 8dp에 그린다.
 */
@Composable
private fun SigimsaeTip(symbol: SigimsaeSymbol, at: Float) {
    val colors = EolssuTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val half = 6.dp.toPx() * 1.4142f
            val x = size.width * at
            drawPath(Path().apply { moveTo(x - half, size.height); lineTo(x, size.height - half); lineTo(x + half, size.height); close() }, colors.accentTint)
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(symbol.name) }
                append("  ")
                append(symbol.description)
            },
            Modifier
                .fillMaxWidth()
                .background(colors.accentTint, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 19.6.sp),
            color = colors.text,
        )
    }
}
