package com.ssafy.eolssu.core.model

import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InstrumentTest {
    @Test
    fun serverCodeFindsPracticeInstrumentForDropdownPicture() {
        Instrument.entries.forEach { assertEquals(it, instrumentOfServerCode(it.serverCode)) }
    }

    @Test
    fun unknownOrMissingServerCodeHasNoInstrument() {
        assertNull(instrumentOfServerCode("PIRI"))
        assertNull(instrumentOfServerCode(null))
    }

    /** 연습 악기 드롭다운은 같은 이름의 운지 악기 그림을 그린다. 이름이 어긋나면 그림을 그릴 때 실패한다. */
    @Test
    fun everyPracticeInstrumentHasGuideInstrumentPicture() {
        Instrument.entries.forEach { assertEquals(it.name, GuideInstrument.valueOf(it.name).name) }
    }
}
