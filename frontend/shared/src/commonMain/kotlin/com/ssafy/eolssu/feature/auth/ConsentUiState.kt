package com.ssafy.eolssu.feature.auth

/** AUTH-01 약관 동의 상태. */
data class ConsentUiState(val checked: Set<Agreement> = emptySet()) {
    val allChecked: Boolean get() = Agreement.entries.all { it in checked }

    /** 필수 항목을 모두 동의해야 시작할 수 있다. 선택 항목은 상관없다. */
    val canStart: Boolean get() = Agreement.entries.filter { it.required }.all { it in checked }
}
