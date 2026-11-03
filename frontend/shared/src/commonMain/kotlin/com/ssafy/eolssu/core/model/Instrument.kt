package com.ssafy.eolssu.core.model

import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_instrument_daegeum_jeongak
import eolssu.shared.generated.resources.guide_instrument_daegeum_sanjo
import eolssu.shared.generated.resources.instrument_danso
import eolssu.shared.generated.resources.instrument_sogeum
import org.jetbrains.compose.resources.StringResource

/** 연습 악기. 가입(AUTH-07)에서 고르고 마이페이지(MY-01)에서 바꾸며, 홈은 현재 악기를 보여 준다. 피리는 당분간 범위 밖이다. */
enum class Instrument(val label: StringResource, val serverCode: String) {
    DaegeumJeongak(Res.string.guide_instrument_daegeum_jeongak, "JEONGAK_DAEGEUM"),
    DaegeumSanjo(Res.string.guide_instrument_daegeum_sanjo, "SANJO_DAEGEUM"),
    Sogeum(Res.string.instrument_sogeum, "SOGEUM"),
    Danso(Res.string.instrument_danso, "DANSO"),
}
