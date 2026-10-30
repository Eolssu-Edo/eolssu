package com.ssafy.eolssu.feature.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.data.song.CatalogJangdanDetail
import com.ssafy.eolssu.data.song.CatalogJangdanSummary
import com.ssafy.eolssu.data.song.CatalogNotationSymbol
import com.ssafy.eolssu.data.song.ReferenceCatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReadingCatalogUiState(
    val jangdansState: ReferenceLoadState = ReferenceLoadState.Loading,
    val jangdans: List<CatalogJangdanSummary> = emptyList(),
    val symbolsState: ReferenceLoadState = ReferenceLoadState.Loading,
    val symbols: List<CatalogNotationSymbol> = emptyList(),
    val selectedJangdanId: String? = null,
    val detailState: ReferenceLoadState = ReferenceLoadState.Loading,
    val detail: CatalogJangdanDetail? = null,
)

/** 기존 읽기 수업을 보존하며 서버에 등록된 장단·기호 설명을 함께 조회한다. */
class ReadingCatalogViewModel(private val references: ReferenceCatalogRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ReadingCatalogUiState())
    val uiState = _uiState.asStateFlow()
    private var jangdansJob: Job? = null
    private var symbolsJob: Job? = null
    private var detailJob: Job? = null

    init { retryJangdans(); retrySymbols() }

    fun retryJangdans() {
        if (jangdansJob?.isActive == true) return
        _uiState.update { it.copy(jangdansState = ReferenceLoadState.Loading) }
        jangdansJob = viewModelScope.launch {
            try {
                val items = references.jangdans()
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(jangdansState = ReferenceLoadState.Ready, jangdans = items) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _uiState.update { it.copy(jangdansState = ReferenceLoadState.Failed) } }
        }
    }

    fun retrySymbols() {
        if (symbolsJob?.isActive == true) return
        _uiState.update { it.copy(symbolsState = ReferenceLoadState.Loading) }
        symbolsJob = viewModelScope.launch {
            try {
                val items = references.symbols()
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(symbolsState = ReferenceLoadState.Ready, symbols = items) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _uiState.update { it.copy(symbolsState = ReferenceLoadState.Failed) } }
        }
    }

    fun selectJangdan(id: String) {
        if (_uiState.value.jangdans.none { it.jangdanId == id }) return
        detailJob?.cancel()
        _uiState.update { it.copy(selectedJangdanId = id, detail = null, detailState = ReferenceLoadState.Loading) }
        detailJob = viewModelScope.launch {
            try {
                val detail = references.jangdan(id)
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(detail = detail, detailState = if (detail == null) ReferenceLoadState.NotRegistered else ReferenceLoadState.Ready) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(detailState = ReferenceLoadState.Failed) }
            }
        }
    }

    fun retryDetail() { _uiState.value.selectedJangdanId?.let(::selectJangdan) }
}
