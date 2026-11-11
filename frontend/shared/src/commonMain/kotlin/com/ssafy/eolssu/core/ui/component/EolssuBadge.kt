package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.theme.EolssuTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 배지 색. 공통 컴포넌트 보드의 `.badge`(상태)와 `.lv`(난이도) 기준이다.
 * - [Soft] `.badge` 기본(연습 준비) · [Play] `.badge.play`(모범 연주·AI 평가) · [Recording] `.badge.rec`(연주 중, 빨간 점)
 * - [Outline] `.badge.lite`(예시 결과·예시 악보처럼 덜 중요한 표시)
 * - [Level1]·[Level2]·[Level3] 입문 · 초급 · 중급(매화 · 대나무 · 소나무). 난이도는 [EolssuLevelBadge]로 쓰고, 같은 단계 모양을 다른 문구에 빌릴 때만 직접 고른다.
 */
enum class EolssuBadgeStyle { Soft, Play, Recording, Outline, Level1, Level2, Level3 }

/**
 * 알약 배지. 상태 배지는 최소 26dp · 12sp, 난이도 배지는 최소 24dp · 12sp이고 [small]이면 각각 22dp · 20dp, 11sp다(SONG-03 곡 카드).
 * 난이도 모양([EolssuBadgeStyle.Level1]~[EolssuBadgeStyle.Level3])은 같은 높이에 그림을 붙인 배지로 그린다. 글꼴 확대에 따라 높이가 늘어난다.
 */
@Composable
fun EolssuBadge(
    text: String,
    modifier: Modifier = Modifier,
    style: EolssuBadgeStyle = EolssuBadgeStyle.Outline,
    icon: ImageVector? = null,
    small: Boolean = false,
) {
    levelBadgePalette(style)?.let { palette ->
        LevelBadge(text, palette, modifier, small)
        return
    }
    val colors = EolssuTheme.colors
    val (bg, fg, line) = when (style) {
        EolssuBadgeStyle.Soft -> Triple(colors.accentTint, colors.text, Color.Transparent)
        EolssuBadgeStyle.Play -> Triple(colors.accentTint, colors.playText, Color.Transparent)
        EolssuBadgeStyle.Recording -> Triple(colors.recSoft, colors.recText, Color.Transparent)
        EolssuBadgeStyle.Outline -> Triple(colors.surface, colors.textMuted, colors.lineSoft)
        EolssuBadgeStyle.Level1, EolssuBadgeStyle.Level2, EolssuBadgeStyle.Level3 -> error("난이도 배지는 LevelBadge가 그린다")
    }
    val minHeight = if (small) 22.dp else 26.dp
    Row(
        modifier
            .heightIn(min = minHeight)
            .background(bg, CircleShape)
            .border(1.dp, line, CircleShape)
            .padding(horizontal = if (small) 9.dp else 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (style == EolssuBadgeStyle.Recording) {
            Box(Modifier.padding(end = 6.dp).size(if (small) 6.dp else 8.dp).background(colors.rec, CircleShape))
        }
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 4.dp).size(13.dp), tint = fg)
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = if (small) 11.sp else 12.sp,
                fontWeight = if (style == EolssuBadgeStyle.Outline) FontWeight.Medium else FontWeight.SemiBold,
            ),
            color = fg,
        )
    }
}

/**
 * 곡 난이도 배지: 입문은 매화(분홍), 초급은 대나무(연두), 중급은 소나무(하늘) 그림과 색의 알약이다.
 * 높이·글자 크기는 예전 `.lv`와 같다(최소 24dp · 12sp, [small]이면 20dp · 11sp).
 */
@Composable
fun EolssuLevelBadge(level: SongLevel, modifier: Modifier = Modifier, small: Boolean = false) {
    EolssuBadge(songLevelLabel(level), modifier, style = levelBadgeStyle(level), small = small)
}

/** 난이도마다 쓰는 배지 색. */
fun levelBadgeStyle(level: SongLevel): EolssuBadgeStyle = when (level) {
    SongLevel.Intro -> EolssuBadgeStyle.Level1
    SongLevel.Elementary -> EolssuBadgeStyle.Level2
    SongLevel.Intermediate -> EolssuBadgeStyle.Level3
}

/**
 * 고르기 칩(`.chip`): 흰 바탕 · 옅은 테두리, 고르면 연두 바탕, [enabled]가 false면 회색 바탕의 흐린 글자.
 * [bordered]가 false면 테두리 없이 그린다(회색 바탕 화면용). [containerColor]는 고르지 않은 칩의 바탕이다(흰 카드 안이면 회색).
 * 보이는 높이는 36dp(14sp)이고 [small]이면 28dp(12sp, 연습 속도 칩)다. 누르는 영역은 Material 최소 크기(48dp)를 유지하고, 확대 글꼴을 수용한다.
 */
@Composable
fun EolssuChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    small: Boolean = false,
    bordered: Boolean = true,
    containerColor: Color? = null,
) {
    val colors = EolssuTheme.colors
    val on = selected && enabled
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = if (small) 28.dp else 36.dp).semantics {
            this.selected = selected
            role = Role.Tab
        },
        shape = CircleShape,
        color = when {
            !enabled -> colors.surfaceStrong
            on -> colors.primary
            else -> containerColor ?: colors.surface
        },
        contentColor = when {
            !enabled -> colors.textDisabled
            on -> colors.onPrimary
            else -> colors.textSub
        },
        border = if (bordered) BorderStroke(1.dp, if (on) colors.primary else colors.lineSoft) else null,
    ) {
        Box(Modifier.padding(horizontal = if (small) 10.dp else 14.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = if (small) 12.sp else 14.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                ),
            )
        }
    }
}

/**
 * 난이도 고르기 칩: [EolssuChip]과 같은 36dp(14sp) 알약에 난이도 배지 그림을 왼쪽에 둔다.
 * 고르지 않으면 흰 바탕 · 옅은 테두리, 고르면 그 난이도 배지의 바탕 · 테두리 · 글자색이다.
 */
@Composable
fun EolssuLevelChip(level: SongLevel, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, bordered: Boolean = true) {
    val colors = EolssuTheme.colors
    val palette = requireNotNull(levelBadgePalette(levelBadgeStyle(level)))
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 36.dp).semantics {
            this.selected = selected
            role = Role.Tab
        },
        shape = CircleShape,
        color = if (selected) palette.background else colors.surface,
        contentColor = if (selected) palette.text else colors.textSub,
        // 고른 칩은 난이도 바탕색으로만 구분하고 테두리를 두지 않는다(S15P31E102-768).
        border = if (bordered && !selected) BorderStroke(1.dp, colors.lineSoft) else null,
    ) {
        Row(
            Modifier.padding(start = 11.dp, end = 14.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Canvas(Modifier.size(16.dp)) { drawLevelMotif(palette.motif) }
            Text(
                songLevelLabel(level),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                ),
            )
        }
    }
}

/** 칩을 가로로 늘어놓고 넘치면 다음 줄로 보낸다(`.chips`). */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/**
 * 난이도 배지 색. 옅은 바탕([background])·테두리([line])·글자([text])와 왼쪽 그림([motif])이다.
 * 배지에만 쓰며, 글자는 바탕 위에서 본문 기준(4.5:1)을 넘는다.
 */
internal data class LevelBadgePalette(val background: Color, val line: Color, val text: Color, val motif: LevelMotif)

/** 난이도 배지 왼쪽 그림: 입문 매화 · 초급 대나무 · 중급 소나무. */
internal enum class LevelMotif { Plum, Bamboo, Pine }

/** 난이도 단계별 배지 색. 난이도가 아닌 배지 모양이면 null이다. */
internal fun levelBadgePalette(style: EolssuBadgeStyle): LevelBadgePalette? = when (style) {
    EolssuBadgeStyle.Level1 -> LevelBadgePalette(Color(0xFFFDE4DD), Color(0xFFC8675A), Color(0xFF7A2618), LevelMotif.Plum)
    EolssuBadgeStyle.Level2 -> LevelBadgePalette(Color(0xFFEEF5CE), Color(0xFF6E8A2E), Color(0xFF384C10), LevelMotif.Bamboo)
    EolssuBadgeStyle.Level3 -> LevelBadgePalette(Color(0xFFE2EEF1), Color(0xFF4E7E91), Color(0xFF1D4251), LevelMotif.Pine)
    else -> null
}

/** 그림 색. 배경 위 장식이라 글자 대비 기준을 적용하지 않는다. */
private val PlumPetal = Color(0xFFE25A48)
private val PlumStamen = Color(0xFFF6C443)
private val PlumBranch = Color(0xFF8A4A30)
private val BambooStalk = Color(0xFF6F9A2C)
private val BambooNode = Color(0xFF4C6E17)
private val BambooLeaf = Color(0xFF557F1F)
private val PineNeedle = Color(0xFF2F6655)
private val PineNeedleLight = Color(0xFFB9D7C6)
private val PineTrunk = Color(0xFF6E4630)

/**
 * 테두리 없는 옅은 바탕의 알약에 왼쪽에 단계 그림, 오른쪽에 짙은 글자를 둔다(S15P31E102-768).
 * 높이·좌우 여백·글자 크기는 상태 배지와 맞춘 예전 난이도 배지와 같고, 그림만큼 폭이 늘어난다.
 */
@Composable
private fun LevelBadge(text: String, palette: LevelBadgePalette, modifier: Modifier, small: Boolean) {
    Row(
        modifier
            .heightIn(min = if (small) 20.dp else 24.dp)
            .background(palette.background, CircleShape)
            .padding(start = 7.dp, end = 9.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Canvas(Modifier.size(if (small) 14.dp else 16.dp)) { drawLevelMotif(palette.motif) }
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (small) 11.sp else 12.sp, fontWeight = FontWeight.Bold),
            color = palette.text,
        )
    }
}

/** 정사각형 칸에 단계 그림을 그린다. 좌표는 칸 한 변을 1로 본 비율이다. */
private fun DrawScope.drawLevelMotif(motif: LevelMotif) {
    val s = size.minDimension
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    when (motif) {
        LevelMotif.Plum -> {
            drawLine(PlumBranch, p(0.58f, 0.66f), p(0.86f, 0.98f), strokeWidth = s * 0.09f, cap = StrokeCap.Round)
            drawLine(PlumBranch, p(0.74f, 0.82f), p(0.9f, 0.72f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawCircle(PlumPetal, radius = s * 0.1f, center = p(0.92f, 0.66f))
            val center = p(0.4f, 0.42f)
            repeat(5) { i ->
                val angle = (-90f + 72f * i) * PI.toFloat() / 180f
                drawCircle(PlumPetal, radius = s * 0.19f, center = center + Offset(cos(angle), sin(angle)) * (s * 0.19f))
            }
            repeat(5) { i ->
                val angle = (-54f + 72f * i) * PI.toFloat() / 180f
                drawLine(PlumStamen, center, center + Offset(cos(angle), sin(angle)) * (s * 0.13f), strokeWidth = s * 0.04f, cap = StrokeCap.Round)
            }
            drawCircle(PlumStamen, radius = s * 0.06f, center = center)
        }
        LevelMotif.Bamboo -> {
            drawLeaf(p(0.44f, 0.42f), p(0.04f, 0.16f), s * 0.12f, BambooLeaf)
            drawLeaf(p(0.44f, 0.42f), p(0.14f, 0.5f), s * 0.07f, BambooLeaf)
            drawLeaf(p(0.58f, 0.66f), p(0.98f, 0.5f), s * 0.11f, BambooLeaf)
            drawRoundRect(BambooStalk, p(0.42f, 0.04f), Size(s * 0.17f, s * 0.94f), CornerRadius(s * 0.05f))
            listOf(0.36f, 0.68f).forEach { y ->
                drawLine(BambooNode, p(0.41f, y), p(0.6f, y), strokeWidth = s * 0.06f)
            }
        }
        LevelMotif.Pine -> {
            val trunk = Path().apply {
                moveTo(0.04f * s, 0.98f * s)
                quadraticTo(0.3f * s, 0.62f * s, 0.5f * s, 0.46f * s)
                moveTo(0.3f * s, 0.74f * s)
                quadraticTo(0.55f * s, 0.7f * s, 0.74f * s, 0.7f * s)
            }
            drawPath(trunk, PineTrunk, style = Stroke(width = s * 0.09f, cap = StrokeCap.Round))
            drawPineTuft(p(0.46f, 0.42f), s * 0.36f, s * 0.26f)
            drawPineTuft(p(0.76f, 0.7f), s * 0.24f, s * 0.18f)
        }
    }
}

/** 아래가 평평한 반타원 솔잎 뭉치와 위로 퍼지는 옅은 솔잎 결. [center]는 밑변 가운데다. */
private fun DrawScope.drawPineTuft(center: Offset, halfWidth: Float, height: Float) {
    drawArc(
        PineNeedle,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(center.x - halfWidth, center.y - height),
        size = Size(halfWidth * 2, height * 2),
    )
    listOf(-0.5f, 0f, 0.5f).forEach { t ->
        val from = Offset(center.x + halfWidth * t * 0.5f, center.y - height * 0.2f)
        val to = Offset(center.x + halfWidth * t * 0.9f, center.y - height * 0.75f)
        drawLine(PineNeedleLight, from, to, strokeWidth = halfWidth * 0.1f, cap = StrokeCap.Round)
    }
}

/** [from]에서 [to]로 뻗는 잎. [width]는 가운데 폭이다. */
private fun DrawScope.drawLeaf(from: Offset, to: Offset, width: Float, color: Color) {
    val mid = (from + to) / 2f
    val d = to - from
    val len = d.getDistance().coerceAtLeast(1f)
    val normal = Offset(-d.y / len, d.x / len) * width
    val leaf = Path().apply {
        moveTo(from.x, from.y)
        quadraticTo(mid.x + normal.x, mid.y + normal.y, to.x, to.y)
        quadraticTo(mid.x - normal.x, mid.y - normal.y, from.x, from.y)
        close()
    }
    drawPath(leaf, color)
}
