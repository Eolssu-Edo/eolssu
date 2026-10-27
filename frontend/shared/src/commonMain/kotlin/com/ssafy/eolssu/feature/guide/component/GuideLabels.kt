package com.ssafy.eolssu.feature.guide.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.feature.guide.Blow
import com.ssafy.eolssu.feature.guide.GuideInstrument
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_blow_high
import eolssu.shared.generated.resources.guide_blow_low
import eolssu.shared.generated.resources.guide_blow_mid
import eolssu.shared.generated.resources.guide_instrument_daegeum_jeongak
import eolssu.shared.generated.resources.guide_instrument_daegeum_sanjo
import eolssu.shared.generated.resources.guide_instrument_danso
import eolssu.shared.generated.resources.guide_instrument_sogeum
import eolssu.shared.generated.resources.instrument_daegeum_jeongak
import eolssu.shared.generated.resources.instrument_daegeum_sanjo
import eolssu.shared.generated.resources.instrument_danso
import eolssu.shared.generated.resources.instrument_sogeum
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/** 악기 이름. */
val GuideInstrument.label: StringResource
    get() = when (this) {
        GuideInstrument.Danso -> Res.string.guide_instrument_danso
        GuideInstrument.Sogeum -> Res.string.guide_instrument_sogeum
        GuideInstrument.DaegeumJeongak -> Res.string.guide_instrument_daegeum_jeongak
        GuideInstrument.DaegeumSanjo -> Res.string.guide_instrument_daegeum_sanjo
    }

/** 악기 사진(2000px 너비). 구멍 좌표는 [GuideInstrument.holes]. */
val GuideInstrument.image: DrawableResource
    get() = when (this) {
        GuideInstrument.Danso -> Res.drawable.instrument_danso
        GuideInstrument.Sogeum -> Res.drawable.instrument_sogeum
        GuideInstrument.DaegeumJeongak -> Res.drawable.instrument_daegeum_jeongak
        GuideInstrument.DaegeumSanjo -> Res.drawable.instrument_daegeum_sanjo
    }

/** 취법 긴 이름(저취 · 낮은 단). 앞부분(저취)만 쓸 때는 [blowShortLabel]. */
val Blow.label: StringResource
    get() = when (this) {
        Blow.Low -> Res.string.guide_blow_low
        Blow.Mid -> Res.string.guide_blow_mid
        Blow.High -> Res.string.guide_blow_high
    }

/** '저취 · 낮은 단' → '저취'. */
internal fun blowShortLabel(longLabel: String): String = longLabel.substringBefore(" · ")

/** 학습 화면 전용 선 아이콘(24×24, 선 굵기 1.8). */
internal object GuideIcons {
    val Speaker: ImageVector = ImageVector.Builder("Speaker", 24.dp, 24.dp, 24f, 24f).apply {
        listOf("M4 9h4l5 -4v14l-5 -4H4z", "M16.5 9a4.5 4.5 0 0 1 0 6M19 6.5a8 8 0 0 1 0 11").forEach { data ->
            addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
}
