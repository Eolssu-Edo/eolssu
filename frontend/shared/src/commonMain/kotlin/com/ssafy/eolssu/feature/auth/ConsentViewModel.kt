package com.ssafy.eolssu.feature.auth

import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.data.user.AccountDraft
import com.ssafy.eolssu.data.user.TermAgreement
import com.ssafy.eolssu.data.user.TermsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ConsentViewModel(private val repository: TermsRepository, private val draft: AccountDraft) : ViewModel() {
    private val state = MutableStateFlow(ConsentUiState())
    val uiState = state.asStateFlow()

    private var initialized = false

    suspend fun load(force: Boolean = false) {
        if (initialized && !force) return
        state.value = ConsentUiState()
        draft.agreements = emptyList()
        val terms = repository.load()
        initialized = true
        state.value = ConsentUiState(terms = terms.orEmpty(), loading = false, failed = terms.isNullOrEmpty())
    }
    fun toggle(termId: String) = state.update {
        if (it.terms.none { term -> term.termId == termId }) it
        else it.copy(checked = if (termId in it.checked) it.checked - termId else it.checked + termId)
    }
    fun toggleAll() = state.update { it.copy(checked = if (it.allChecked) emptySet() else it.terms.map { term -> term.termId }.toSet()) }
    fun accept(): Boolean {
        val current = state.value
        if (!current.canStart) return false
        draft.agreements = current.terms.map { TermAgreement(it.termId, it.version, it.termId in current.checked) }
        return true
    }
}
