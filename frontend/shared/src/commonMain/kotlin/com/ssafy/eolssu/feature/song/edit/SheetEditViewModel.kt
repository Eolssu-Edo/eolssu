package com.ssafy.eolssu.feature.song.edit

import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.data.song.MySheetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 악보 편집(EDIT-02 · 03)과 미리보기·저장(EDIT-04). 편집 화면 경로에 묶여 미리보기에서도 같은 악보를 본다.
 * 저장 API를 붙이기 전이라 첫 악보는 예시([SheetEditSamples])에서 만들고, 내 악보 이름은 [MySheetRepository]에서 찾는다.
 */
class SheetEditViewModel(
    source: EditSource,
    startView: EditView,
    mySheetRepository: MySheetRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SheetEditUiState.initial(source, SheetEditSamples.draftFor(source, mySheetRepository.sheets.value), startView),
    )
    val uiState: StateFlow<SheetEditUiState> = _uiState.asStateFlow()

    private fun change(transform: (SheetEditUiState) -> SheetEditUiState) = _uiState.update(transform)

    fun selectCell(index: Int) = change { it.select(index) }
    fun previousCell() = change { it.moveSelection(-1) }
    fun nextCell() = change { it.moveSelection(1) }
    fun put(note: ScoreNote) = change { it.put(note) }
    fun erase() = change { it.erase() }
    fun putSigimsae(value: Sigimsae) = change { it.putSigimsae(value) }
    fun clearSigimsae() = change { it.clearSigimsae() }
    fun toggleSigimsae() = change { it.copy(showSigimsae = !it.showSigimsae) }
    fun split() = change { it.split() }
    fun merge() = change { it.merge() }
    fun addColumn() = change { it.addColumn() }
    fun deleteColumn() = change { it.deleteColumn() }
    fun undo() = change { it.undo() }
    fun redo() = change { it.redo() }
    fun selectTab(tab: EditTab) = change { it.copy(tab = tab) }
    fun selectView(view: EditView) = change { it.copy(view = view) }
    fun selectStaffNote(key: StaffNoteKey) = change { it.selectStaffNote(key) }
    fun shiftStaffNote(steps: Int) = change { it.shiftStaffNote(steps) }
    fun changeJangdan() = change { it.copy(message = SheetEditMessage.ComingSoon) }

    fun togglePreview() = change { it.copy(previewPlaying = !it.previewPlaying) }
    fun toggleWithJangdan() = change { it.copy(withJangdan = !it.withJangdan) }
    fun stopPreview() = change { it.copy(previewPlaying = false) }
    fun requestSave() = change { it.requestSave() }
    fun saveDraft() = change { it.saveDraft() }
    fun requestAudio() = change { it.requestAudio() }
    fun closeWarning() = change { it.copy(emptyWarning = false) }
    fun fillEmpty() = change { it.fillEmpty() }
    fun consumeMessage() = change { it.copy(message = null) }
}
