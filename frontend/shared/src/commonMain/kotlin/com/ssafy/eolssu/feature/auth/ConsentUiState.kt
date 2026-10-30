package com.ssafy.eolssu.feature.auth

import com.ssafy.eolssu.data.user.AccountTerm

data class ConsentUiState(
    val terms: List<AccountTerm> = emptyList(),
    val checked: Set<String> = emptySet(),
    val loading: Boolean = true,
    val failed: Boolean = false,
) {
    val allChecked: Boolean get() = terms.isNotEmpty() && terms.all { it.termId in checked }
    val canStart: Boolean get() = !loading && !failed && terms.any { it.required } && terms.filter { it.required }.all { it.termId in checked }
}
