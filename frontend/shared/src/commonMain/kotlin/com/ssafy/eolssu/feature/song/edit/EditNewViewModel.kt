package com.ssafy.eolssu.feature.song.edit

import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.feature.song.edit.component.SheetFileProblem
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

    private var reimportHandled = false

    /** '다시 불러오기'로 들어온 첫 한 번만 [problem] 안내를 띄운다. 화면을 다시 그려도 닫은 안내를 되살리지 않는다. */
    fun showReimportProblem(problem: SheetFileProblem?) {
        if (reimportHandled) return
        reimportHandled = true
        if (problem != null) _uiState.update { it.copy(problem = problem) }
    }

    fun dismissProblem() = _uiState.update { it.copy(problem = null) }
}
