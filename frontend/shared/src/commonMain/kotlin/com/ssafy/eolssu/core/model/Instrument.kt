package com.ssafy.eolssu.core.model

import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_instrument_daegeum_jeongak
import eolssu.shared.generated.resources.guide_instrument_daegeum_sanjo
import eolssu.shared.generated.resources.instrument_danso
import eolssu.shared.generated.resources.instrument_sogeum
import org.jetbrains.compose.resources.StringResource

/**
 * 연습 악기. 가입(AUTH-07)에서 고르고 마이페이지(MY-01)에서 바꾸며, 홈은 현재 악기를 보여 준다. 피리는 당분간 범위 밖이다.
 * 선언 순서(단소 · 소금 · 산조대금 · 정악대금)가 모든 화면의 악기 표시 순서다.
 */
enum class Instrument(val label: StringResource, val serverCode: String) {
    Danso(Res.string.instrument_danso, "DANSO"),
    Sogeum(Res.string.instrument_sogeum, "SOGEUM"),
    DaegeumSanjo(Res.string.guide_instrument_daegeum_sanjo, "SANJO_DAEGEUM"),
    DaegeumJeongak(Res.string.guide_instrument_daegeum_jeongak, "JEONGAK_DAEGEUM"),
}

/** 서버 악기 코드([Instrument.serverCode])에 맞는 연습 악기. 모르는 코드(피리 등)는 null이다. */
fun instrumentOfServerCode(serverCode: String?): Instrument? = Instrument.entries.firstOrNull { it.serverCode == serverCode }

/** 서버 악기 코드의 화면 표시 순서([Instrument] 선언 순서). 모르는 코드는 서버가 준 순서대로 맨 뒤에 둔다. */
fun instrumentDisplayRank(serverCode: String?): Int =
    Instrument.entries.indexOfFirst { it.serverCode == serverCode }.let { if (it < 0) Instrument.entries.size else it }
