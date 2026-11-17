package com.ssafy.eolssu.feature.song.edit

/**
 * 가져온 악보를 열었을 때 보여 주는 확인 안내(S15P31E102-979).
 *
 * 슬라이더의 최초 선택은 원음(S15P31E102-893)로 두고, 더 나은 추천 이조가 있으면 한 번 눌러 적용하도록 권한다.
 *
 * @property rangeNotes 지금 적용된 이조에서 악기 음역·운지 밖이라 고쳐야 할 음 수.
 * @property recommendedTranspose 수정 필요 음을 줄이는 추천 이조(원본 기준 반음). 권할 추천이 없으면 null.
 * @property recommendedRemaining 추천을 적용해도 남는 수정 필요 음 수.
 * @property reviewCells 음역과 별개로 인식 결과를 확인해야 하는 정간 수.
 * @property recommendedShift 지금 높이에서 추천까지 옮길 반음 수(양수는 올림). 안내 문장에 쓴다.
 * @property instrumentName 음역을 계산한 악기 이름. 모르면 null.
 * @property reviewPosition 지금 고른 칸이 다시 볼 곳 중 몇 번째인지(1부터). 다시 볼 칸이 아니면 null.
 * @property tuningMismatch 악보 기준음(황)이 악기 조율과 달라 모든 음이 입력 가능 음으로 인정되지 않는 상태.
 *   높이 맞추기를 한 번 적용하면 악기 기준음으로 바뀌므로 같은 줄에서 바로 적용한다(S15P31E102-1080).
 * @property blankCells 다시 볼 곳 중 정간 나누기 등으로 아직 비어 있어 채워야 하는 칸 수.
 * @property selectedBlank 지금 고른 칸이 채워야 할 빈칸인지. 카드 줄이 무엇을 넣을지 짧게 말한다(S15P31E102-1136).
 */
data class SheetImportGuide(
    val rangeNotes: Int,
    val recommendedTranspose: Int?,
    val recommendedRemaining: Int?,
    val reviewCells: Int,
    val recommendedShift: Int? = null,
    val instrumentName: String? = null,
    val reviewPosition: Int? = null,
    val tuningMismatch: Boolean = false,
    val blankCells: Int = 0,
    val selectedBlank: Boolean = false,
)

/** 음역과 별개로 인식 결과를 다시 볼 칸(연주 순서). */
internal val SheetEditUiState.recognitionReviewCells: List<Int>
    get() = completionValidation.let { (it.correctionCellIndices - it.rangeCellIndices).sorted() }

internal fun SheetEditUiState.importGuide(): SheetImportGuide? {
    if (draft.original == null || documentLoading || documentFailed) return null
    val validation = completionValidation
    val reviewList = recognitionReviewCells
    val reviewCells = reviewList.size
    val unfilled = draft.unfilledCells.toSet()
    val panel = adaptation?.takeIf { !it.loading && !it.failed && it.baseline == draft }
    val current = panel?.plans?.firstOrNull { it.transpose == draft.appliedTranspose }
    // 낼 수 없는 음 수는 정간에 칠한 음(rangeUnitKeys)으로 센다. 높이 계산은 원본 음 기준이라 직접 고친 음과 다를 수 있다(S15P31E102-1136).
    // 이음(ㅡ) 자리는 앞 음과 같은 음이므로 세지 않는다.
    val actualRange = draft.cells.indices.sumOf { index ->
        draft.cells[index].indices.count { sub ->
            draft.cells[index][sub].pitchKey() != null && draft.unitKeyOf(index, sub) in validation.rangeUnitKeys
        }
    }
    val better = panel?.let { recommendedAdaptations(it.plans).firstOrNull() }
        ?.takeIf { rec -> current != null && rec.transpose != current.transpose && rec.unresolved < actualRange }
    // 기준음이 다르면 이조 계획의 음 수는 0이어도 칸은 모두 표시된다. 지금 높이(또는 더 나은 추천)를 적용해 기준음을 맞춘다.
    val tuningMismatch = validation.hasTuningMismatch && current != null
    val recommended = better ?: current?.takeIf { tuningMismatch }
    val rangeNotes = if (tuningMismatch) maxOf(requireNotNull(current).unresolved, actualRange) else actualRange
    if (rangeNotes == 0 && reviewCells == 0 && recommended == null) return null
    val instrument = panel?.instruments?.firstOrNull { it.instrumentId == panel.selectedId }?.name
    return SheetImportGuide(rangeNotes, recommended?.transpose, recommended?.unresolved, reviewCells,
        recommendedShift = recommended?.let { it.transpose - draft.appliedTranspose }, instrumentName = instrument,
        reviewPosition = (reviewList.indexOf(selected) + 1).takeIf { it > 0 }, tuningMismatch = tuningMismatch,
        blankCells = reviewList.count { it in unfilled }, selectedBlank = selected in unfilled && selected in reviewList)
}
