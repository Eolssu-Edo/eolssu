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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.feature.guide.BasicSigimsae
import com.ssafy.eolssu.feature.guide.BasicSigimsaeCount
import com.ssafy.eolssu.feature.guide.MoreSigimsae
import com.ssafy.eolssu.feature.guide.MoreSigimsaeCount
import com.ssafy.eolssu.feature.guide.SigimsaeItem
import com.ssafy.eolssu.feature.guide.SigimsaeStep
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_reading_length_full
import eolssu.shared.generated.resources.guide_reading_length_hold
import eolssu.shared.generated.resources.guide_reading_length_split
import eolssu.shared.generated.resources.guide_reading_order_caption
import eolssu.shared.generated.resources.guide_reading_order_column
import eolssu.shared.generated.resources.guide_reading_order_rtl
import eolssu.shared.generated.resources.guide_reading_order_ttb
import eolssu.shared.generated.resources.guide_sigimsae_basic
import eolssu.shared.generated.resources.guide_sigimsae_collapse
import eolssu.shared.generated.resources.guide_sigimsae_expand
import eolssu.shared.generated.resources.guide_sigimsae_more
import eolssu.shared.generated.resources.guide_sigimsae_sequence
import eolssu.shared.generated.resources.score_column_start
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/*
 * 정간보 쉽게 읽기의 설명 조각. 기초부터 배우기 7·8·9단계가 같은 조각을 가져다 쓴다(설명은 한 곳에만 둔다).
 */

/**
 * 설명 카드(`.gcard`). [number]가 있으면 번호 원과 [title]을 머리에 단다.
 * [intro]이면 회색 바탕에 테두리 없이 그린다.
 */
@Composable
internal fun GuideCard(
    modifier: Modifier = Modifier,
    number: Int? = null,
    title: String? = null,
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
                if (number != null) {
                    Box(Modifier.size(24.dp).background(colors.primary, CircleShape), contentAlignment = Alignment.Center) {
                        Text("$number", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = colors.onPrimary)
                    }
                }
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp))
            }
        }
        content()
    }
}

/** 카드 안 첫 문단(`.lead`, 13sp). */
@Composable
internal fun GuideLead(raw: String, modifier: Modifier = Modifier) {
    GuideText(raw, modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp))
}

/** 회색 작은 태그(`.tag.mono`). */
@Composable
private fun MonoTag(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.height(24.dp).background(EolssuTheme.colors.surface, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold))
    }
}

/** 세로로 쓴 태그(`.tag.mono.v`). 글자를 한 줄에 하나씩 위에서 아래로 놓는다. */
@Composable
private fun VerticalMonoTag(text: String) {
    Column(
        Modifier
            .width(24.dp)
            .background(EolssuTheme.colors.surface, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = text },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
        text.forEach { c ->
            if (c == ' ') Spacer(Modifier.height(5.dp)) else Text(c.toString(), Modifier.clearAndSetSemantics {}, style = style)
        }
    }
}

/** 왼쪽을 가리키는 가는 화살표(`.arrow-left`, 64dp) 또는 아래를 가리키는 화살표(`.arrow-down`, 70dp). */
@Composable
private fun ThinArrow(down: Boolean) {
    val color = EolssuTheme.colors.text
    Canvas(if (down) Modifier.size(width = 10.dp, height = 72.dp) else Modifier.size(width = 66.dp, height = 10.dp)) {
        val stroke = 1.5.dp.toPx()
        val head = 7.dp.toPx()
        val half = 5.dp.toPx()
        if (down) {
            val x = size.width / 2
            drawLine(color, Offset(x, 0f), Offset(x, size.height - head), stroke)
            drawPath(Path().apply { moveTo(x - half, size.height - head); lineTo(x + half, size.height - head); lineTo(x, size.height); close() }, color)
        } else {
            val y = size.height / 2
            drawLine(color, Offset(head, y), Offset(size.width, y), stroke)
            drawPath(Path().apply { moveTo(head, y - half); lineTo(head, y + half); lineTo(0f, y); close() }, color)
        }
    }
}

/** 읽는 순서 예시의 음 하나. [symbol]이면 회색 기호(ㅡ·△)다. */
private data class OrderNote(val order: Int, val text: String, val symbol: Boolean = false)

/** 예시 세 각(왼쪽 각 3 → 오른쪽 각 1). 숫자가 연주 순서다. */
private val OrderColumns: List<Pair<Int, List<List<OrderNote>>>> = listOf(
    3 to listOf(listOf(OrderNote(8, "潢")), listOf(OrderNote(9, "林")), listOf(OrderNote(10, "ㅡ", true))),
    2 to listOf(listOf(OrderNote(4, "仲")), listOf(OrderNote(5, "林"), OrderNote(6, "南")), listOf(OrderNote(7, "△", true))),
    1 to listOf(listOf(OrderNote(1, "黃")), listOf(OrderNote(2, "太")), listOf(OrderNote(3, "ㅡ", true))),
)

/** 1 읽는 순서(`.rd-board`): 예시 세 각과 '오른쪽에서 왼쪽으로'·'위에서 아래로' 화살표. */
@Composable
internal fun ReadOrderBoard(modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MonoTag(stringResource(Res.string.guide_reading_order_rtl))
            ThinArrow(down = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrderColumns.forEach { (column, cells) -> OrderColumn(column, cells) }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VerticalMonoTag(stringResource(Res.string.guide_reading_order_ttb))
                ThinArrow(down = true)
            }
        }
        Text(
            stringResource(Res.string.guide_reading_order_caption),
            Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colors.textMuted,
        )
    }
}

@Composable
private fun OrderColumn(column: Int, cells: List<List<OrderNote>>) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.width(62.dp).clip(shape).background(colors.background).border(1.dp, colors.lineStrong, shape)) {
        Row(
            Modifier.fillMaxWidth().height(24.dp).background(colors.surface),
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
            if (column == 1) Text(stringResource(Res.string.score_column_start), style = style)
            Text(stringResource(Res.string.guide_reading_order_column, column), style = style, color = colors.textMuted)
        }
        cells.forEach { notes ->
            Column(Modifier.fillMaxWidth().height(62.dp).topLine(colors.line)) {
                notes.forEachIndexed { k, note ->
                    Box(
                        Modifier.fillMaxWidth().weight(1f).then(if (k > 0) Modifier.dashedTopLine(colors.line) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        val size = if (notes.size > 1) 17.sp else 24.sp
                        Text(
                            note.text,
                            style = EolssuTheme.serif.yul.copy(fontSize = size, lineHeight = size),
                            color = if (note.symbol) colors.textMuted else colors.text,
                            fontWeight = if (note.symbol) FontWeight.Normal else FontWeight.SemiBold,
                        )
                        Box(
                            Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 3.dp).size(16.dp)
                                .background(colors.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${note.order}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold),
                                color = colors.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.topLine(color: Color): Modifier = drawBehind { drawRect(color, size = size.copy(height = 1.dp.toPx())) }

private fun Modifier.dashedTopLine(color: Color): Modifier = drawBehind {
    drawLine(
        color,
        Offset(0f, 0f),
        Offset(size.width, 0f),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
    )
}

/** 2 음의 길이(`.len-row`): 한 칸 · 반씩 나누기 · 이어 가기. */
@Composable
internal fun LengthRow(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LengthExample(listOf(listOf("林" to false)), stringResource(Res.string.guide_reading_length_full), Modifier.weight(1f))
        LengthExample(listOf(listOf("林" to false, "南" to false)), stringResource(Res.string.guide_reading_length_split), Modifier.weight(1f))
        LengthExample(listOf(listOf("林" to false), listOf("ㅡ" to true)), stringResource(Res.string.guide_reading_length_hold), Modifier.weight(1f))
    }
}

/** [cells]는 칸마다 (글자, 기호인지) 목록. */
@Composable
private fun LengthExample(cells: List<List<Pair<String, Boolean>>>, caption: String, modifier: Modifier) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier.background(colors.surface, RoundedCornerShape(12.dp)).padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.width(50.dp).clip(shape).background(colors.background).border(1.dp, colors.lineStrong, shape)) {
            cells.forEachIndexed { i, notes ->
                Column(Modifier.fillMaxWidth().height(46.dp).then(if (i > 0) Modifier.topLine(colors.line) else Modifier)) {
                    notes.forEachIndexed { k, (text, symbol) ->
                        Box(
                            Modifier.fillMaxWidth().weight(1f).then(if (k > 0) Modifier.dashedTopLine(colors.line) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            val size = if (notes.size > 1) 15.sp else 19.sp
                            Text(
                                text,
                                style = EolssuTheme.serif.yul.copy(fontSize = size, lineHeight = size),
                                color = if (symbol) colors.textMuted else colors.text,
                                fontWeight = if (symbol) FontWeight.Normal else FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
        Text(caption, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 15.5.sp), textAlign = TextAlign.Center)
    }
}

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

/** 5 시김새의 '처음 배우는 시김새'(`.sig-block`). */
@Composable
internal fun SigimsaeBasicBlock(modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Column(modifier.fillMaxWidth()) {
        CountTitle(stringResource(Res.string.guide_sigimsae_basic), BasicSigimsaeCount, 15.sp, Modifier.padding(top = 4.dp))
        BasicSigimsae.forEach { group ->
            Column(Modifier.padding(top = 18.dp)) {
                Text(group.title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
                Text(
                    group.caption,
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                    color = colors.textMuted,
                )
                Column(Modifier.padding(top = 6.dp)) {
                    group.items.forEachIndexed { i, item ->
                        SigimsaeRow(item, last = i == group.items.lastIndex)
                    }
                }
            }
        }
    }
}

@Composable
private fun CountTitle(title: String, count: Int, fontSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
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

@Composable
private fun SigimsaeRow(item: SigimsaeItem, last: Boolean) {
    val colors = EolssuTheme.colors
    Column {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = if (last) 0.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.size(52.dp).background(colors.surface, RoundedCornerShape(12.dp)),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item.images.forEach { SigimsaeImage(it, 30.dp, if (item.images.size > 1) 18.dp else 38.dp) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
                    if (item.chip != null) {
                        Box(
                            Modifier.height(20.dp).border(1.dp, colors.line, RoundedCornerShape(10.dp)).padding(horizontal = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(item.chip, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = colors.textMuted)
                        }
                    }
                }
                Text(item.description, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp))
                if (item.sequence.isNotEmpty()) SigimsaeSequence(item.sequence)
            }
        }
        if (!last) HorizontalDivider(color = colors.line)
    }
}

@Composable
private fun SigimsaeSequence(steps: List<SigimsaeStep>) {
    val colors = EolssuTheme.colors
    val label = stringResource(Res.string.guide_sigimsae_sequence)
    FlowRow(
        Modifier.padding(top = 2.dp).semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { i, step ->
            if (i > 0) Text("→", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = colors.textMuted)
            val shape = RoundedCornerShape(11.dp)
            Box(
                Modifier
                    .height(22.dp)
                    .background(if (step.main) colors.primary else Color.Transparent, shape)
                    .border(1.dp, if (step.main) colors.primary else colors.lineStrong, shape)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                val fg = if (step.main) colors.onPrimary else colors.text
                Text(
                    buildAnnotatedString {
                        append(step.label)
                        if (step.length.isNotEmpty()) {
                            append(' ')
                            withStyle(SpanStyle(fontSize = 10.5.sp, color = if (step.main) fg.copy(alpha = 0.75f) else colors.textMuted)) { append(step.length) }
                        }
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                    color = fg,
                )
            }
        }
    }
}

/** '더 많은 시김새'(`.sig-more`). 처음에는 접어 둔다. */
@Composable
internal fun SigimsaeMoreBlock(expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val toggleLabel = stringResource(if (expanded) Res.string.guide_sigimsae_collapse else Res.string.guide_sigimsae_expand)
    Column(modifier.fillMaxWidth().padding(top = 20.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .clickable(role = Role.Button, onClick = onToggle)
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
                    group.symbols.chunked(4).forEach { row ->
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
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** 원 둘레를 점선으로 그린다(낮은 음 표시 `.ring.low`). */
internal fun Modifier.dashedCircle(color: Color, width: Dp): Modifier = drawBehind {
    val stroke = width.toPx()
    drawCircle(
        color,
        radius = (size.minDimension - stroke) / 2,
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 2, stroke * 1.5f))),
    )
}
