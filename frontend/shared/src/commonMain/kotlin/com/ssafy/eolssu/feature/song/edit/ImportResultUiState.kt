package com.ssafy.eolssu.feature.song.edit

/**
 * EDIT-06 인식 결과 확인 상태. 확인이 필요한 정간([checks])을 하나씩 골라 후보 중 하나로 정한다.
 *
 * @property fixed 확인한 정간 번호 → 고른 후보.
 * @property selected 지금 보고 있는 확인 정간. 모두 확인했으면 null.
 * @property playing '들어보며 확인' 재생 중. 재생 API를 붙이기 전이라 버튼 모양만 바뀐다.
 */
data class ImportResultUiState(
    val kind: ImportKind,
    val hwang: HwangPitch,
    val checks: List<ImportCheck>,
    val fixed: Map<Int, String> = emptyMap(),
    val selected: Int? = checks.firstOrNull()?.index,
    val playing: Boolean = false,
) {
    /** 아직 확인하지 않은 정간. */
    val openChecks: List<ImportCheck> get() = checks.filter { it.index !in fixed }

    val selectedCheck: ImportCheck? get() = checks.firstOrNull { it.index == selected }

    val allDone: Boolean get() = selectedCheck == null

    /** 이 정간에 지금 적힌 후보. 고르지 않았으면 인식한 첫 후보다. */
    fun currentCandidate(check: ImportCheck): String = fixed[check.index] ?: check.candidates.first()

    /** 확인 정간을 누르면 그 정간을 본다. 다른 정간은 무시한다. */
    fun select(index: Int): ImportResultUiState = if (checks.any { it.index == index }) copy(selected = index) else this

    /** 보고 있는 정간을 [candidate]로 정하고, 남은 확인 정간 중 첫 정간으로 넘어간다. 남은 게 없으면 선택을 비운다. */
    fun choose(candidate: String): ImportResultUiState {
        val check = selectedCheck ?: return this
        if (candidate !in check.candidates) return this
        val updated = fixed + (check.index to candidate)
        return copy(fixed = updated, selected = checks.firstOrNull { it.index !in updated }?.index)
    }

    /** 고친 내용을 반영한 인식 악보. */
    fun draft(): SheetDraft = SheetEditSamples.importResultDraft(kind, fixed)
}
