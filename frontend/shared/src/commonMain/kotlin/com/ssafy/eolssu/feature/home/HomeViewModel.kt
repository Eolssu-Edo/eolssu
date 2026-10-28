package com.ssafy.eolssu.feature.home

import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.data.user.InstrumentRepository
import kotlinx.coroutines.flow.StateFlow

/** HOME-01. 현재 악기는 보여 주기만 하고, 바꾸기는 마이페이지(MY-01)에서 한다. */
class HomeViewModel(instrumentRepository: InstrumentRepository) : ViewModel() {
    val instrument: StateFlow<Instrument> = instrumentRepository.instrument
}
