package com.ssafy.eolssu.feature.song.edit

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** EDIT-01 악보 추가. 고른 가져오기 종류와 황 음을 기기 선택 창·인식 화면으로 넘길 때까지 들고 있다. */
class EditNewViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EditNewUiState())
    val uiState: StateFlow<EditNewUiState> = _uiState.asStateFlow()

    fun selectKind(kind: ImportKind) = _uiState.update { it.copy(kind = kind) }

    fun selectHwang(hwang: HwangPitch) = _uiState.update { it.copy(hwang = hwang) }

    fun openDevicePick() = _uiState.update { it.copy(devicePickOpen = true) }

    fun closeDevicePick() = _uiState.update { it.copy(devicePickOpen = false) }
}
