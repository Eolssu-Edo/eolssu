package com.ssafy.eolssu.feature.auth

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** AUTH-01. 약관 보기(AUTH-02)에 다녀와도 이 화면이 뒤에 남아 있어 동의 상태가 유지된다. */
class ConsentViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ConsentUiState())
    val uiState: StateFlow<ConsentUiState> = _uiState.asStateFlow()

    fun toggle(agreement: Agreement) = _uiState.update {
        it.copy(checked = if (agreement in it.checked) it.checked - agreement else it.checked + agreement)
    }

    /** 모두 동의돼 있으면 모두 풀고, 하나라도 빠져 있으면 모두 동의한다. */
    fun toggleAll() = _uiState.update {
        it.copy(checked = if (it.allChecked) emptySet() else Agreement.entries.toSet())
    }
}
