package com.ssafy.eolssu.feature.guide

import com.ssafy.eolssu.data.song.CatalogPitch
import com.ssafy.eolssu.data.song.CatalogFingerings
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.FingeringNote
import com.ssafy.eolssu.core.model.fingering.Blow
import com.ssafy.eolssu.core.model.fingering.BlowTab
import com.ssafy.eolssu.core.model.fingering.baseYulOf
import com.ssafy.eolssu.core.model.fingering.octaveOf
import com.ssafy.eolssu.core.model.Yul

/** 서버의 선택지와 앱이 지원하는 운지 그림을 연결한다. */
data class FingeringInstrumentOption(val instrumentId: String, val name: String, val instrument: GuideInstrument)

/**
 * GUIDE-01 상태.
 *
 * @property blow 고른 취법. 고른 악기에 없는 취법이면 화면은 평취를 보여주고([shownBlow]) 값은 남겨 둔다.
 *   다시 그 취법이 있는 악기로 바꾸면 처음 고른 취법이 보인다(와이어프레임과 같다).
 * @property playing '음 듣기'를 누른 율명. 음원이 없어 버튼만 잠시 강조한다.
 */
data class FingeringUiState(
    val instrument: GuideInstrument = GuideInstrument.Default,
    val blow: Blow = Blow.Mid,
    val isDropdownOpen: Boolean = false,
    val playing: String? = null,
    val instrumentListState: ReferenceLoadState = ReferenceLoadState.Loading,
    val instrumentOptions: List<FingeringInstrumentOption> = emptyList(),
    val catalogState: ReferenceLoadState = ReferenceLoadState.NotRegistered,
    val catalogPitches: List<CatalogPitch> = emptyList(),
    val catalogFingerings: CatalogFingerings = CatalogFingerings(emptyList()),
) {
    val hasSelectedInstrument: Boolean get() = instrumentListState == ReferenceLoadState.Ready &&
        instrumentOptions.any { it.instrument == instrument }

    val shownBlow: Blow get() = instrument.resolveBlow(blow)

    val notes: List<FingeringNote> get() = instrument.notesByPitch(blow)

    val blowTabs: List<BlowTab> get() = instrument.blowTabs(shownBlow)

    fun pitchFor(hanja: String): CatalogPitch? {
        val yul = baseYulOf(hanja)?.let(Yul::fromSymbol)?.hangul ?: return null
        return catalogPitches.singleOrNull { it.yul == yul && it.octave == octaveOf(hanja) }
    }
}

internal fun FingeringUiState.toggleDropdown() = copy(isDropdownOpen = !isDropdownOpen)

internal fun FingeringUiState.dismissDropdown() = copy(isDropdownOpen = false)

internal fun FingeringUiState.selectInstrument(instrument: GuideInstrument) = copy(instrument = instrument, isDropdownOpen = false)

/** 이 악기에 없는 취법은 고를 수 없다. */
internal fun FingeringUiState.selectBlow(blow: Blow) = if (blow in instrument.blows) copy(blow = blow) else this

internal fun FingeringUiState.listen(hanja: String) = copy(playing = hanja)

/** [hanja]의 강조가 끝났다. 그 사이 다른 음을 눌렀으면 그 음의 강조는 남긴다. */
internal fun FingeringUiState.finishListening(hanja: String) = if (playing == hanja) copy(playing = null) else this
