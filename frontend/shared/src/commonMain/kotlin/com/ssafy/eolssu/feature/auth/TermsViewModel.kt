package com.ssafy.eolssu.feature.auth

import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.data.user.AccountTerm
import com.ssafy.eolssu.data.user.TermsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TermsUiState(val term: AccountTerm? = null, val loading: Boolean = true, val openFailed: Boolean = false)

class TermsViewModel(private val termId: String, private val repository: TermsRepository) : ViewModel() {
    private val state = MutableStateFlow(TermsUiState())
    val uiState = state.asStateFlow()

    suspend fun load() {
        state.value = TermsUiState()
        state.value = TermsUiState(term = repository.load()?.firstOrNull { it.termId == termId }, loading = false)
    }

    fun opened(success: Boolean) = state.update { it.copy(openFailed = !success) }
}
