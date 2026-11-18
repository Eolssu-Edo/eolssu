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
    private var accepted = false

    /**
     * 처음 한 번만 약관 목록을 받는다. 약관 상세를 보고 돌아오거나 다음 가입 단계에서 뒤로 돌아와도 체크 상태를 유지한다.
     * 다만 동의를 넘긴 뒤 가입 요청이 최신 약관 동의를 요구해 동의가 비워졌으면 새 목록을 받아 처음부터 다시 동의받는다.
     */
    suspend fun load(force: Boolean = false) {
        val consumedByServer = accepted && draft.agreements.isEmpty()
        if (initialized && !force && !consumedByServer) return
        state.value = ConsentUiState()
        draft.agreements = emptyList()
        accepted = false
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
        accepted = true
        return true
    }
}
