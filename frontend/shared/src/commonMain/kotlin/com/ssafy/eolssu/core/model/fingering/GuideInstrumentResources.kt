package com.ssafy.eolssu.core.model.fingering

import eolssu.shared.generated.resources.Res
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

/** 악기 이름(정악대금 · 산조대금 · 단소 · 소금). 운지법 보기·기초부터 배우기·연습 화면이 함께 쓴다. */
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
