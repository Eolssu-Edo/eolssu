package com.ssafy.eolssu.feature.auth

import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.data.user.AccountTerm
import com.ssafy.eolssu.data.user.TermsBlock
import com.ssafy.eolssu.data.user.TermsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 약관 상세(AUTH-02). [body]는 약관 본문이며, 약관이나 본문을 받지 못하면 [loading]이 끝난 뒤에도 null이다. */
data class TermsUiState(val term: AccountTerm? = null, val body: List<TermsBlock>? = null, val loading: Boolean = true)

class TermsViewModel(private val termId: String, private val repository: TermsRepository) : ViewModel() {
    private val state = MutableStateFlow(TermsUiState())
    val uiState = state.asStateFlow()

    /** 약관 목록에서 [termId]를 찾고, 그 본문을 바로 받아 온다. 다시 부르면 처음부터 다시 받는다(다시 시도). */
    suspend fun load() {
        state.value = TermsUiState()
        val term = repository.load()?.firstOrNull { it.termId == termId }
        state.value = TermsUiState(term = term, loading = term != null)
        if (term == null) return
        state.value = TermsUiState(term = term, body = repository.loadDocument(term.contentUrl), loading = false)
    }
}
