package com.ssafy.eolssu.feature.song.edit

/**
 * 가져온 악보를 열었을 때 보여 주는 확인 안내(S15P31E102-979).
 *
 * 슬라이더의 최초 선택은 원조 유지(S15P31E102-893)로 두고, 더 나은 추천 이조가 있으면 한 번 눌러 적용하도록 권한다.
 *
 * @property rangeNotes 지금 적용된 이조에서 악기 음역·운지 밖이라 고쳐야 할 음 수.
 * @property recommendedTranspose 수정 필요 음을 줄이는 추천 이조(원본 기준 반음). 권할 추천이 없으면 null.
 * @property recommendedRemaining 추천을 적용해도 남는 수정 필요 음 수.
 * @property reviewCells 음역과 별개로 인식 결과를 확인해야 하는 정간 수.
 * @property recommendedShift 지금 높이에서 추천까지 옮길 반음 수(양수는 올림). 안내 문장에 쓴다.
 * @property instrumentName 음역을 계산한 악기 이름. 모르면 null.
 * @property reviewPosition 지금 고른 칸이 다시 볼 곳 중 몇 번째인지(1부터). 다시 볼 칸이 아니면 null.
 */
data class SheetImportGuide(
    val rangeNotes: Int,
    val recommendedTranspose: Int?,
    val recommendedRemaining: Int?,
    val reviewCells: Int,
    val recommendedShift: Int? = null,
    val instrumentName: String? = null,
    val reviewPosition: Int? = null,
)

/** 음역과 별개로 인식 결과를 다시 볼 칸(연주 순서). */
internal val SheetEditUiState.recognitionReviewCells: List<Int>
    get() = completionValidation.let { (it.correctionCellIndices - it.rangeCellIndices).sorted() }

internal fun SheetEditUiState.importGuide(): SheetImportGuide? {
    if (draft.original == null || documentLoading || documentFailed) return null
    val validation = completionValidation
    val reviewList = recognitionReviewCells
    val reviewCells = reviewList.size
    val panel = adaptation?.takeIf { !it.loading && !it.failed && it.baseline == draft }
    val current = panel?.plans?.firstOrNull { it.transpose == draft.appliedTranspose }
    val recommended = panel?.let { recommendedAdaptations(it.plans).firstOrNull() }
        ?.takeIf { rec -> current != null && rec.transpose != current.transpose && rec.unresolved < current.unresolved }
    val rangeNotes = current?.unresolved ?: validation.rangeCellIndices.size
    if (rangeNotes == 0 && reviewCells == 0 && recommended == null) return null
    val instrument = panel?.instruments?.firstOrNull { it.instrumentId == panel.selectedId }?.name
    return SheetImportGuide(rangeNotes, recommended?.transpose, recommended?.unresolved, reviewCells,
        recommendedShift = recommended?.let { it.transpose - requireNotNull(current).transpose }, instrumentName = instrument,
        reviewPosition = (reviewList.indexOf(selected) + 1).takeIf { it > 0 })
}
