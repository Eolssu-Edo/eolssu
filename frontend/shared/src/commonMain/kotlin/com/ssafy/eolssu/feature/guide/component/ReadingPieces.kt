package com.ssafy.eolssu.feature.guide.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
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
import com.ssafy.eolssu.feature.guide.BasicSigimsae
import com.ssafy.eolssu.feature.guide.BasicSigimsaeCount
import com.ssafy.eolssu.feature.guide.MoreSigimsae
import com.ssafy.eolssu.feature.guide.MoreSigimsaeCount
import com.ssafy.eolssu.feature.guide.SigimsaeItem
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
import eolssu.shared.generated.resources.guide_sigimsae_collapse
import eolssu.shared.generated.resources.guide_sigimsae_expand
import eolssu.shared.generated.resources.guide_sigimsae_more
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/*
 * 정간보 쉽게 읽기의 쪽 카드와 그림 조각. 기초부터 배우기 7·8·9단계가 같은 조각을 가져다 쓴다(설명은 한 곳에만 둔다).
 * 그림 하나가 한 가지를 보여 준다: 정간보란은 井 획, 1 읽는 순서는 차례로 채워지는 선([ReadOrderBoard]), 2 길이는 소리 막대, 3 높낮이는 계단.
 */

/**
 * 설명 카드(`.gcard`). [mark]가 있으면 검은 원 표시와 [title]을, [small]이 있으면 그 옆에 작은 회색 글을 단다.
 * [intro]이면 회색 바탕에 테두리 없이 그린다(정간보란).
 */
@Composable
internal fun GuideCard(
    modifier: Modifier = Modifier,
    mark: String? = null,
    title: String? = null,
    small: String? = null,
    intro: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = EolssuTheme.colors
    val shape = MaterialTheme.shapes.medium
    var m = modifier.fillMaxWidth().clip(shape).background(if (intro) colors.surface else colors.background)
    if (!intro) m = m.border(1.dp, colors.line, shape)
    Column(m.padding(horizontal = 14.dp, vertical = 16.dp)) {
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
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                )
            }
        }
        content()
    }
}

/** 쪽 표시(`.rb-mark`): 검은 원 안의 번호. 정간보란의 井은 세리프로 쓴다. */
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
 * 쪽 맨 위 설명 한 줄(`.page-lead`). 줄을 바꾸지 않고 카드 너비에 맞춰 최대 18sp까지 키운다.
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
        style = MaterialTheme.typography.bodyLarge.copy(color = colors.text, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
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
        if (glow != null) {
            val shadow = Shadow(glow, blurRadius = u(6f))
            repeat(2) { draw.drawText(layout, color = color, topLeft = topLeft, shadow = shadow) }
        }
        if (halo != null) {
            draw.drawText(layout, color = halo, topLeft = topLeft, drawStyle = Stroke(u(haloUnits), join = StrokeJoin.Round))
        }
        draw.drawText(layout, color = color, topLeft = topLeft)
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

/** 정간보란: 큰 井의 네 획을 3×3 칸 선 위에 겹쳐, 井 가운데 네모가 곧 한 칸(정간)임을 보여 준다. 아래에 범례. */
@Composable
internal fun ReadIntro() {
    val colors = EolssuTheme.colors
    val serif = EolssuTheme.serif.yul
    PageLead(stringResource(Res.string.guide_reading_intro_lead))
    Figure(
        width = 152f,
        height = 144f,
        maxWidth = 170.dp,
        description = stringResource(Res.string.guide_reading_intro_figure),
        modifier = Modifier.padding(bottom = 14.dp),
    ) {
        val c = 40f
        val gx = { i: Int -> 16f + c * i }
        val gy = { i: Int -> 12f + c * i }
        val d = draw
        d.drawRoundRect(colors.background, Offset(u(gx(0)), u(gy(0))), Size(u(c * 3), u(c * 3)), CornerRadius(u(6f)))
        d.drawRoundRect(
            colors.lineStrong,
            Offset(u(gx(0)), u(gy(0))),
            Size(u(c * 3), u(c * 3)),
            CornerRadius(u(6f)),
            style = Stroke(u(1f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(u(3f), u(3f)))),
        )
        val faint = listOf(listOf("林", "太", "黃"), listOf("南", null, "ㅡ"), listOf("潢", "△", "仲"))
        faint.forEachIndexed { r, row ->
            row.forEachIndexed { k, t ->
                if (t != null) text(t, gx(k) + c / 2, gy(r) + c / 2 + 6, serif, 17f, colors.lineStrong)
            }
        }
        d.drawRoundRect(colors.surface, Offset(u(gx(1) + 3), u(gy(1) + 3)), Size(u(c - 6), u(c - 6)), CornerRadius(u(4f)))
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
                    Modifier.size(14.dp).background(colors.surface, RoundedCornerShape(3.dp)).border(1.dp, colors.lineStrong, RoundedCornerShape(3.dp)),
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
                        Text(":", Modifier.width(14.dp), style = style, color = colors.lineStrong)
                        Text(desc, style = style, color = colors.textMuted)
                    }
                }
            },
            modifier = Modifier.background(colors.background, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
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

private val LengthExamples: List<List<List<String>>> = listOf(
    listOf(listOf("林"), listOf("南")),
    listOf(listOf("林", "南"), listOf("太")),
    listOf(listOf("林"), listOf("ㅡ")),
    listOf(listOf("林"), listOf("△")),
)

private fun isSymbol(text: String) = text == "ㅡ" || text == "△"

/** 2 음의 길이(`.len-row`): 정간 두 칸 옆 막대가 소리 나는 시간. 한 칸씩 · 반씩 나누기 · ㅡ 이어 가기 · △ 쉬기를 2×2로. */
@Composable
internal fun LengthRow(modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val captions = listOf(
        stringResource(Res.string.guide_reading_length_cap_each),
        stringResource(Res.string.guide_reading_length_cap_split),
        stringResource(Res.string.guide_reading_length_cap_hold),
        stringResource(Res.string.guide_reading_length_cap_rest),
    )
    Column(modifier.fillMaxWidth()) {
        PageLead(stringResource(Res.string.guide_reading_length_lead))
        GuideGrid(LengthExamples, 2, horizontalGap = 8.dp, verticalGap = 8.dp) { i, cells, cell ->
            LengthExample(cells, captions[i], cell)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(width = 6.dp, height = 14.dp).background(colors.text, RoundedCornerShape(3.dp)))
            Text(stringResource(Res.string.guide_reading_length_key), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
        }
    }
}

@Composable
private fun LengthExample(cells: List<List<String>>, caption: String, modifier: Modifier) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier.background(colors.surface, RoundedCornerShape(12.dp)).padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.width(50.dp).clip(shape).background(colors.background).border(1.dp, colors.lineStrong, shape)) {
                cells.forEachIndexed { i, notes ->
                    if (i > 0) HorizontalDivider(color = colors.line)
                    Column(Modifier.fillMaxWidth().height(50.dp)) {
                        notes.forEachIndexed { k, text ->
                            Box(
                                Modifier.fillMaxWidth().weight(1f).then(if (k > 0) Modifier.dashedTopLine(colors.line) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                val size = if (notes.size > 1) 16.sp else 22.sp
                                Text(
                                    text,
                                    style = EolssuTheme.serif.yul.copy(fontSize = size, lineHeight = size),
                                    color = if (isSymbol(text)) colors.textMuted else colors.text,
                                    fontWeight = if (isSymbol(text)) FontWeight.Normal else FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
            SoundBar(soundSpans(cells), Modifier.width(8.dp).fillMaxHeight())
        }
        Text(
            caption,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 소리 막대(`.len-bar`): 가운데 점선 위에 소리 나는 구간을 검은 막대로 칠한다. 구간 위아래는 3dp씩 띄운다. */
@Composable
private fun SoundBar(spans: List<Pair<Float, Float>>, modifier: Modifier) {
    val colors = EolssuTheme.colors
    Canvas(modifier) {
        val dash = 3.dp.toPx()
        drawLine(
            colors.line,
            Offset(size.width / 2, 0f),
            Offset(size.width / 2, size.height),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
        )
        val gap = 3.dp.toPx()
        spans.forEach { (start, length) ->
            val top = start / 2f * size.height + gap
            val h = length / 2f * size.height - gap * 2
            drawRoundRect(colors.text, Offset(0f, top), Size(size.width, h), CornerRadius(4.dp.toPx()))
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
internal fun PitchSteps() {
    val colors = EolssuTheme.colors
    PageLead(stringResource(Res.string.guide_reading_pitch_lead))
    val steps = listOf(
        PitchStep(0, "亻", "黃", stringResource(Res.string.guide_reading_pitch_low), stringResource(Res.string.guide_reading_pitch_low_term), stringResource(Res.string.guide_reading_pitch_low_gloss)),
        PitchStep(1, "", "黃", stringResource(Res.string.guide_reading_pitch_base), stringResource(Res.string.guide_reading_pitch_base_term), stringResource(Res.string.guide_reading_pitch_base_gloss)),
        PitchStep(2, "氵", "黃", stringResource(Res.string.guide_reading_pitch_high), stringResource(Res.string.guide_reading_pitch_high_term), stringResource(Res.string.guide_reading_pitch_high_gloss)),
    )
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.Center) {
        PitchAxis(Modifier.fillMaxHeight().padding(end = 12.dp))
        steps.forEach { PitchStepCell(it) }
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
}

/** 왼쪽 축(`.pit-axis`): 위 '높게' · 아래로 옅어지는 선 · 아래 '낮게'. '낮게'가 가장 낮은 디딤판 높이까지 내려온다. */
@Composable
private fun PitchAxis(modifier: Modifier) {
    val colors = EolssuTheme.colors
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
    Column(modifier.padding(top = 4.dp).offset(y = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.guide_reading_pitch_axis_high), style = style, color = colors.textMuted)
        Box(Modifier.weight(1f).width(1.5.dp).background(Brush.verticalGradient(listOf(colors.textMuted, colors.line))))
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
private fun SigimsaeImage(image: DrawableResource, height: Dp, maxWidth: Dp) {
    val painter = painterResource(image)
    val ratio = painter.intrinsicSize.let { if (it.height > 0f) it.width / it.height else 1f }
    val width = minOf(height * ratio, maxWidth)
    Image(painter, contentDescription = null, modifier = Modifier.size(width, height), contentScale = ContentScale.Fit)
}

/**
 * 4 시김새의 '처음 배우는 시김새'(`.sig-block`): 무리마다 기호 · 이름 · 짧은 설명 한 줄.
 *
 * @param columns 기호 목록 칸 수. 태블릿은 세로 2칸 · 가로 3칸(`.tabapp .sig-block .sig-list`)이고, 이때는 모든 기호 아래에 선을 긋는다.
 */
@Composable
internal fun SigimsaeBasicBlock(modifier: Modifier = Modifier, columns: Int = 1) {
    val colors = EolssuTheme.colors
    Column(modifier.fillMaxWidth()) {
        CountTitle(stringResource(Res.string.guide_sigimsae_basic), BasicSigimsaeCount, 15.sp)
        BasicSigimsae.forEach { group ->
            Column(Modifier.padding(top = 16.dp)) {
                Text(group.title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = colors.textMuted)
                if (columns <= 1) {
                    Column(Modifier.padding(top = 4.dp)) {
                        group.items.forEachIndexed { i, item -> SigimsaeRow(item, last = i == group.items.lastIndex) }
                    }
                } else {
                    GuideGrid(group.items, columns, Modifier.padding(top = 4.dp), horizontalGap = 28.dp, verticalGap = 0.dp) { _, item, cell ->
                        SigimsaeRow(item, last = false, modifier = cell, stretch = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun CountTitle(title: String, count: Int, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val muted = EolssuTheme.colors.textMuted
    Text(
        buildAnnotatedString {
            append(title)
            append(' ')
            withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = muted)) { append("$count") }
        },
        modifier,
        style = MaterialTheme.typography.titleSmall.copy(fontSize = fontSize, fontWeight = FontWeight.Bold),
    )
}

/** 기호 한 줄(`.sig-item`): 기호 44 · 이름 66 · 설명. 이름 칸 너비를 고정해 설명이 모두 같은 세로줄에서 시작한다. [stretch]이면 격자 한 줄의 높이에 맞춰 아래 선을 칸끼리 맞춘다. */
@Composable
private fun SigimsaeRow(item: SigimsaeItem, last: Boolean, modifier: Modifier = Modifier, stretch: Boolean = false) {
    val colors = EolssuTheme.colors
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = if (last) 0.dp else if (stretch) 12.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.size(44.dp).background(colors.surface, RoundedCornerShape(12.dp)),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item.images.forEach { SigimsaeImage(it, 26.dp, if (item.images.size > 1) 18.dp else 34.dp) }
            }
            Text(item.name, Modifier.width(66.dp), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.description, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.6.sp), color = colors.textMuted)
                if (item.chip != null) {
                    Box(
                        Modifier.height(20.dp).border(1.dp, colors.line, RoundedCornerShape(10.dp)).padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(item.chip, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 11.sp), color = colors.textMuted)
                    }
                }
            }
        }
        if (stretch) Spacer(Modifier.weight(1f))
        if (!last) HorizontalDivider(color = colors.line)
    }
}

/**
 * '더 많은 시김새'(`.sig-more`). 처음에는 접어 둔다.
 * 와이어프레임은 시김새 다음 쪽으로 펼쳐 두지만, 그 쪽은 S15P31E102-236에서 옮기므로 그때까지 시김새 쪽 아래에 접어 둔다.
 *
 * @param columns 기호 격자 칸 수. 휴대폰 4칸, 태블릿 세로 6칸 · 가로 9칸(`.tabapp .sig-grid`).
 * @param enabled 넘기는 책에서 보이지 않는 쪽이면 거짓이라 누를 수 없다.
 */
@Composable
internal fun SigimsaeMoreBlock(expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, columns: Int = 4, enabled: Boolean = true) {
    val colors = EolssuTheme.colors
    val toggleLabel = stringResource(if (expanded) Res.string.guide_sigimsae_collapse else Res.string.guide_sigimsae_expand)
    Column(modifier.fillMaxWidth().padding(top = 20.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .clickable(enabled = enabled, role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = toggleLabel }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CountTitle(stringResource(Res.string.guide_sigimsae_more), MoreSigimsaeCount, 14.sp, Modifier.weight(1f))
            Text(toggleLabel, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium), color = colors.textMuted)
        }
        if (expanded) {
            MoreSigimsae.forEach { group ->
                CountTitle(group.title, group.symbols.size, 13.sp, Modifier.padding(top = 16.dp))
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    group.symbols.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { symbol ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        Modifier.fillMaxWidth().height(48.dp).background(colors.surface, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        SigimsaeImage(symbol.image, 24.dp, 34.dp)
                                    }
                                    Text(
                                        symbol.name,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}
