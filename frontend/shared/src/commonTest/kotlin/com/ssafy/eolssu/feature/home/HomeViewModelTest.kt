package com.ssafy.eolssu.feature.home

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.user.InstrumentRepository
import kotlin.test.Test
import kotlin.test.assertEquals

/** HOME-01은 현재 악기를 보여 주기만 하고, 바꾸기는 마이페이지에서 한다. */
class HomeViewModelTest {
    @Test
    fun startsWithDanso() {
        assertEquals(Instrument.Danso, HomeViewModel(InstrumentRepository(FakeKeyValueStore())).instrument.value)
    }

    @Test
    fun showsInstrumentChosenElsewhere() {
        val repository = InstrumentRepository(FakeKeyValueStore())
        val viewModel = HomeViewModel(repository)

        repository.select(Instrument.Sogeum)

        assertEquals(Instrument.Sogeum, viewModel.instrument.value)
    }
}
