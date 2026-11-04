package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.PdfFile
import com.ssafy.eolssu.data.song.MySheetRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UPLOAD-01. [filePicker]가 고른 파일을 [MySheetRepository]에 올린다. 고르기를 취소하면(null) 상태를 그대로 둔다.
 * 지금은 기기 파일 선택기 대신 예시 파일을 고르는 함수를 받는다(`app/di`).
 */
class PdfUploadViewModel(
    private val repository: MySheetRepository,
    private val filePicker: suspend () -> PdfFile?,
    member: SignedInMember? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PdfUploadUiState())
    val uiState: StateFlow<PdfUploadUiState> = _uiState.asStateFlow()
    private var uploadJob: Job? = null

    private val accountScope = member.screenScope(viewModelScope) {
        _uiState.value = PdfUploadUiState()
    }

    fun pickFile() {
        if (!accountScope.isActive) return
        accountScope.launch {
            val file = filePicker() ?: return@launch
            uploadJob?.cancel()
            _uiState.update { it.startUpload(file) }
            uploadJob = accountScope.launch {
                val uploaded = repository.uploadPdf(file) { progress -> _uiState.update { it.withProgress(progress) } }
                _uiState.update { it.finishUpload(uploaded) }
            }
        }
    }

    fun removeFile() {
        if (!accountScope.isActive) return
        uploadJob?.cancel()
        uploadJob = null
        _uiState.update { it.removeFile() }
    }
}
