package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.data.song.MySheetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PdfPreviewViewModel(
    private val uploadId: String,
    private val repository: MySheetRepository,
    member: SignedInMember? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PdfPreviewUiState())
    val uiState: StateFlow<PdfPreviewUiState> = _uiState.asStateFlow()

    private val accountScope = member.screenScope(viewModelScope) {
        _uiState.value = PdfPreviewUiState()
    }

    init {
        accountScope.launch {
            repository.getUploadedPdf(uploadId)?.let { upload -> _uiState.update { it.receiveUpload(upload) } }
        }
    }

    fun selectPage(page: Int) = _uiState.update { it.selectPage(page) }

    fun changeTitle(title: String) = _uiState.update { it.copy(title = title) }

    fun selectJangdan(name: String?) = _uiState.update { it.copy(jangdanName = name) }

    /** 내 악보에 저장한다. 저장 중에는 버튼이 돈다. */
    fun save() {
        if (!accountScope.isActive) return
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(saving = true) }
        accountScope.launch {
            val sheet = repository.savePdf(uploadId, state.title.trim(), state.jangdanName)
            _uiState.update { it.copy(saving = false, savedSheetId = sheet.id) }
        }
    }
}
