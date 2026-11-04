package com.ssafy.eolssu.feature.song.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.data.song.MySheetRepository
import com.ssafy.eolssu.data.song.SampleSongs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 불러올 악보 고르기. 내 악보는 [MySheetRepository]의 악보(모두 정간보)를, 제공 곡은 API를 붙이기 전까지 예시 곡([SampleSongs])을 보인다.
 */
class EditPickViewModel(mySheetRepository: MySheetRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(
        EditPickUiState(songs = SampleSongs.map { EditPickSong(it.id, it.title, it.level, it.jangdanName) }),
    )
    val uiState: StateFlow<EditPickUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            mySheetRepository.sheets.collect { sheets -> _uiState.update { it.copy(sheets = sheets) } }
        }
    }

    fun selectTab(tab: EditPickTab) = _uiState.update { it.copy(tab = tab) }

    fun search(query: String) = _uiState.update { it.copy(query = query) }

    fun toggle(target: EditPickTarget) = _uiState.update { it.toggle(target) }
}
