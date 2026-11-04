package com.ssafy.eolssu.feature.song.edit

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** EDIT-06 인식 결과 확인. 인식 API를 붙이기 전이라 예시 인식 결과([SheetEditSamples.checks])로 시작한다. */
class ImportResultViewModel(kind: ImportKind, hwang: HwangPitch) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportResultUiState(kind, hwang, SheetEditSamples.checks(kind)))
    val uiState: StateFlow<ImportResultUiState> = _uiState.asStateFlow()

    fun select(index: Int) = _uiState.update { it.select(index) }

    fun choose(candidate: String) = _uiState.update { it.choose(candidate) }

    fun togglePlay() = _uiState.update { it.copy(playing = !it.playing) }
}
