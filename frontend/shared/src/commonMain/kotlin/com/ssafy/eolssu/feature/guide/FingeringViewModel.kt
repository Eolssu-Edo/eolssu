package com.ssafy.eolssu.feature.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.data.song.InstrumentCatalog
import com.ssafy.eolssu.data.song.CatalogFingerings
import com.ssafy.eolssu.data.song.ReferenceCatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.Blow

/** GUIDE-01. 기초부터 배우기의 '더 보기'로 들어오면 그 악기·취법으로 시작한다. */
class FingeringViewModel(
    instrument: GuideInstrument = GuideInstrument.Default,
    blow: Blow = Blow.Mid,
    private val instruments: InstrumentCatalog? = null,
    private val references: ReferenceCatalogRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FingeringUiState(instrument = instrument, blow = blow))
    val uiState: StateFlow<FingeringUiState> = _uiState.asStateFlow()
    private var catalogJob: Job? = null

    init { retryCatalog() }

    fun toggleDropdown() = _uiState.update { it.toggleDropdown() }

    fun dismissDropdown() = _uiState.update { it.dismissDropdown() }

    fun selectInstrument(instrument: GuideInstrument) {
        if (instrument == _uiState.value.instrument) { dismissDropdown(); return }
        catalogJob?.cancel()
        _uiState.update { it.selectInstrument(instrument).copy(catalogPitches = emptyList(), catalogFingerings = CatalogFingerings(emptyList()), playing = null) }
        retryCatalog()
    }

    fun retryCatalog() {
        val instruments = instruments ?: return
        val references = references ?: return
        if (catalogJob?.isActive == true) return
        val selected = _uiState.value.instrument
        _uiState.update { it.copy(catalogState = ReferenceLoadState.Loading) }
        catalogJob = viewModelScope.launch {
            try {
                // Variant identity follows the server code, never a shared DAEGEUM fallback or enum position.
                val code = when (selected) {
                    GuideInstrument.Danso -> "DANSO"
                    GuideInstrument.Sogeum -> "SOGEUM"
                    GuideInstrument.DaegeumJeongak -> "JEONGAK_DAEGEUM"
                    GuideInstrument.DaegeumSanjo -> "SANJO_DAEGEUM"
                }
                val instrument = instruments.load().firstOrNull { it.code == code }
                if (instrument == null) {
                    _uiState.update { it.copy(catalogState = ReferenceLoadState.NotRegistered) }
                    return@launch
                }
                val (pitches, fingerings) = coroutineScope {
                    val pitches = async { references.pitches(instrument.instrumentId) }
                    val fingerings = async { references.fingerings(instrument.instrumentId) }
                    pitches.await() to fingerings.await()
                }
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(catalogState = ReferenceLoadState.Ready, catalogPitches = pitches, catalogFingerings = fingerings) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(catalogState = ReferenceLoadState.Failed) }
            }
        }
    }

    fun selectBlow(blow: Blow) = _uiState.update { it.selectBlow(blow) }

    fun listen(hanja: String) = _uiState.update { it.listen(hanja) }

    fun finishListening(hanja: String) = _uiState.update { it.finishListening(hanja) }
}
