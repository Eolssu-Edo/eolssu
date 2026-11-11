package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.theme.EolssuColors
import com.ssafy.eolssu.core.theme.EolssuTheme

/**
 * 악기 고르기에 그릴 대나무 악기 모양(S15P31E102-749). 한자 낙관 대신 사진처럼 대나무 몸통·감은 띠·검은 지공으로 그리고, 몸통 색·띠 색·길이·굵기로 악기를 구별한다.
 * [length]는 칸 대각선 대비 길이, [thickness]는 칸 한 변 대비 굵기, [holes]는 앞 지공 수, [bands]는 몸통 길이 대비 감은 띠 위치(아래 끝 0 → 위 끝 1).
 * 단소는 짧고 굵으며, 소금은 짧고 가늘다. 대금은 가장 길고 취구 옆에 청공이 있으며, 산조대금은 정악대금보다 조금 짧다.
 */
private class FluteShape(
    val length: Float,
    val thickness: Float,
    val holes: Int,
    val mouth: Boolean,
    val cheong: Boolean,
    val bands: List<Float>,
    val body: List<Color>,
)

/** 몸통 색은 악기마다 다르게(밝은 쪽 → 어두운 쪽) 두어 한눈에 구별되게 한다. 감은 띠는 그 악기 색([sealColor])이다. */
private val GuideInstrument.flute: FluteShape
    get() = when (this) {
        GuideInstrument.Danso -> FluteShape(
            0.80f, 0.15f, holes = 4, mouth = false, cheong = false, bands = listOf(0.05f, 0.50f, 0.95f),
            body = listOf(Color(0xFFF3DDA6), Color(0xFFDDB86E), Color(0xFFB98F45)),
        )
        GuideInstrument.Sogeum -> FluteShape(
            0.76f, 0.10f, holes = 6, mouth = true, cheong = false, bands = listOf(0.05f, 0.95f),
            body = listOf(Color(0xFFF7F3E6), Color(0xFFE2D9BC), Color(0xFFAFA482)),
        )
        GuideInstrument.DaegeumJeongak -> FluteShape(
            1.00f, 0.12f, holes = 6, mouth = true, cheong = true, bands = listOf(0.04f, 0.30f, 0.58f, 0.96f),
            body = listOf(Color(0xFFD9A06E), Color(0xFFB0703F), Color(0xFF7C4A26)),
        )
        GuideInstrument.DaegeumSanjo -> FluteShape(
            0.90f, 0.12f, holes = 6, mouth = true, cheong = true, bands = listOf(0.04f, 0.58f, 0.96f),
            body = listOf(Color(0xFFE2E8B2), Color(0xFFBCC67E), Color(0xFF858F4A)),
        )
    }

private val Hole = Color(0xFF2E2016)

/** 악기마다 다른 색. 악기 그림의 감은 띠가 이 색이다. */
private fun GuideInstrument.sealColor(colors: EolssuColors): Color = when (this) {
    GuideInstrument.Danso -> colors.rec
    GuideInstrument.Sogeum -> colors.rhythmError
    GuideInstrument.DaegeumJeongak -> colors.play
    GuideInstrument.DaegeumSanjo -> colors.pitchError
}

/**
 * 악기 그림: 색 칸 없이 대나무 악기를 왼쪽 아래에서 오른쪽 위로 비스듬히 그린다. 위 끝이 부는 쪽이다.
 * 그림은 꾸밈이라 읽지 않고, 옆의 악기 이름이 읽힌다. 기초부터 배우기·운지법 보기·마이페이지 진도 뱃지가 함께 쓴다(S15P31E102-768).
 */
@Composable
fun InstrumentPicture(instrument: GuideInstrument, size: Dp = 28.dp) {
    val flute = instrument.flute
    val band = instrument.sealColor(EolssuTheme.colors)
    Canvas(Modifier.size(size)) {
        val side = this.size.minDimension
        val length = side * 1.4142f * flute.length * 0.86f
        val thick = side * flute.thickness
        val left = center.x - length / 2
        val top = center.y - thick / 2
        val radius = CornerRadius(thick / 2)
        val body = Path().apply { addRoundRect(RoundRect(left, top, left + length, top + thick, radius)) }
        rotate(-45f) {
            // 둥근 몸통: 위는 밝고 아래는 어둡게 칠해 대나무 통처럼 보이게 한다.
            drawPath(body, Brush.verticalGradient(flute.body, startY = top, endY = top + thick))
            clipPath(body) {
                flute.bands.forEach { at ->
                    val w = thick * 0.45f
                    drawRect(band, topLeft = Offset(left + length * at - w / 2, top), size = Size(w, thick))
                }
            }
            drawPath(body, flute.body.last(), style = Stroke(width = side * 0.025f))
            val hole = thick * 0.2f
            val head = left + length - thick * 1.4f
            if (flute.mouth) drawOval(Hole, topLeft = Offset(head - hole * 1.5f, center.y - hole), size = Size(hole * 3f, hole * 2f))
            if (flute.cheong) drawOval(Hole.copy(alpha = 0.55f), topLeft = Offset(left + length * 0.72f - hole * 1.5f, center.y - hole), size = Size(hole * 3f, hole * 2f))
            // 앞 지공은 아래 끝 쪽 절반에 고르게 놓는다.
            val start = left + length * 0.12f
            val span = length * 0.42f
            repeat(flute.holes) { i ->
                drawCircle(Hole, radius = hole, center = Offset(start + span * (i + 0.5f) / flute.holes, center.y))
            }
        }
    }
}

/** 연습 악기([Instrument])의 악기 그림. 두 악기 목록은 이름이 같다. */
@Composable
fun InstrumentPicture(instrument: Instrument, size: Dp = 28.dp) = InstrumentPicture(GuideInstrument.valueOf(instrument.name), size)
