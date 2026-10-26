package com.ssafy.eolssu.feature.home

import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.instrument_daegeum
import eolssu.shared.generated.resources.instrument_danso
import eolssu.shared.generated.resources.instrument_piri
import eolssu.shared.generated.resources.instrument_sogeum
import org.jetbrains.compose.resources.StringResource

/** 홈에서 고르는 관악기. 지금은 홈에서만 쓰며, 다른 기능이 쓰기 시작하면 `core/model`로 옮긴다. */
enum class Instrument(val label: StringResource) {
    Daegeum(Res.string.instrument_daegeum),
    Sogeum(Res.string.instrument_sogeum),
    Danso(Res.string.instrument_danso),
    Piri(Res.string.instrument_piri),
}

data class HomeUiState(
    val selectedInstrument: Instrument = Instrument.Daegeum,
    val isInstrumentSheetOpen: Boolean = false,
    val instruments: List<Instrument> = Instrument.entries,
)
