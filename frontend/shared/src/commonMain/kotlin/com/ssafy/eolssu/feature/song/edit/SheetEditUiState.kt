package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.ScoreNote

/** EDIT-02 아래 입력판의 탭. */
enum class EditTab { Yul, Sigimsae, Cell }

/** 편집기 보기: 정간보(EDIT-02) / 오선보로 보기(EDIT-03). 한쪽을 고치면 다른 쪽도 같은 악보를 보인다. */
enum class EditView { Jeongganbo, Staff }

/** 화면 아래에 잠깐 띄우는 안내. */
enum class SheetEditMessage {
    /** 임시저장(SHT-11 초안). 빈 정간이 있어도 된다. */
    Draft,

    /** 저장(SHT-04). 빈 정간이 없을 때만 된다. */
    Saved,

    /** 마지막으로 저장한 악보로 음원(M4A)을 받았다. */
    Audio,

    /** 저장한 적이 없어 음원을 받을 수 없다. */
    AudioNeedsSave,

    /** 아직 만들지 않은 기능(장단 바꾸기). */
    ComingSoon,
}

/**
 * 악보 편집(EDIT-02 · 03)과 미리보기·저장(EDIT-04)이 함께 쓰는 상태. 저장 API를 붙이기 전이라 저장·임시저장·음원 받기는 안내만 띄운다.
 *
 * @property selected 고른 정간 번호(연주 순서).
 * @property past 실행 취소로 돌아갈 악보(최근 [HISTORY_LIMIT]개). [future]는 다시 실행할 악보.
 * @property staffKey 오선보에서 고른 음표. null이면 첫 음표.
 * @property showSigimsae 시김새를 정간 오른쪽에 작게 보인다.
 * @property emptyWarning 빈 정간이 있어 저장할 수 없다는 경고 창이 열려 있다.
 * @property savedOnce 저장한 악보가 있다. 음원은 마지막으로 저장한 악보로 만든다.
 */
data class SheetEditUiState(
    val source: EditSource,
    val draft: SheetDraft,
    val hwang: HwangPitch = HwangPitch.Default,
    val selected: Int = 0,
    val tab: EditTab = EditTab.Yul,
    val view: EditView = EditView.Jeongganbo,
    val past: List<SheetDraft> = emptyList(),
    val future: List<SheetDraft> = emptyList(),
    val staffKey: StaffNoteKey? = null,
    val showSigimsae: Boolean = true,
    val previewPlaying: Boolean = false,
    val withJangdan: Boolean = true,
    val emptyWarning: Boolean = false,
    val savedOnce: Boolean = false,
    val message: SheetEditMessage? = null,
) {
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    /** 인식 결과에서 왔고 아직 확인하지 않은 정간이 남았으면 그 수. 아니면 0. */
    val pendingChecks: Int get() = (source as? EditSource.Import)?.pendingChecks ?: 0

    /** 악보를 바꾼다. 바뀌지 않았으면 기록을 남기지 않는다. */
    private fun commit(next: SheetDraft, selected: Int = this.selected): SheetEditUiState {
        if (next == draft) return copy(selected = selected)
        return copy(
            draft = next,
            past = (past + draft).takeLast(HISTORY_LIMIT),
            future = emptyList(),
            selected = selected.coerceIn(0, next.cells.lastIndex),
        )
    }

    fun select(index: Int): SheetEditUiState = copy(selected = index.coerceIn(0, draft.cells.lastIndex))

    fun moveSelection(delta: Int): SheetEditUiState = select(selected + delta)

    /** 자판의 율명·기호를 고른 정간에 적고, 정간이 다 차면 다음 정간으로 넘어간다. */
    fun put(note: ScoreNote): SheetEditUiState {
        val (next, advance) = draft.put(selected, note)
        return commit(next, if (advance) (selected + 1).coerceAtMost(next.cells.lastIndex) else selected)
    }

    fun erase(): SheetEditUiState = commit(draft.erase(selected))

    fun putSigimsae(value: Sigimsae): SheetEditUiState = commit(draft.withSigimsae(selected, value))

    fun clearSigimsae(): SheetEditUiState = commit(draft.withSigimsae(selected, null))

    fun split(): SheetEditUiState = commit(draft.split(selected))

    fun merge(): SheetEditUiState = commit(draft.merge(selected))

    /** 고른 정간의 각 왼쪽에 빈 각을 넣고 새 각의 첫 정간을 고른다. */
    fun addColumn(): SheetEditUiState {
        val column = draft.columnOf(selected)
        val next = draft.addColumnAfter(column)
        return commit(next, (column + 1) * next.gak)
    }

    /** 고른 정간의 각을 지운다. 선택은 같은 자리(마지막 각이었으면 새 마지막 각)로 둔다. */
    fun deleteColumn(): SheetEditUiState {
        val column = draft.columnOf(selected)
        val next = draft.deleteColumn(column)
        if (next == draft) return this
        return commit(next, minOf(column, next.columnCount - 1) * next.gak)
    }

    fun undo(): SheetEditUiState {
        val previous = past.lastOrNull() ?: return this
        return copy(draft = previous, past = past.dropLast(1), future = listOf(draft) + future, selected = selected.coerceIn(0, previous.cells.lastIndex))
    }

    fun redo(): SheetEditUiState {
        val next = future.firstOrNull() ?: return this
        return copy(draft = next, past = past + draft, future = future.drop(1), selected = selected.coerceIn(0, next.cells.lastIndex))
    }

    /** 오선보에서 고른 음표. */
    fun staffNote(notes: List<StaffNote> = staffNotes(draft)): StaffNote? = notes.pick(staffKey)

    /** 오선보에서 음표를 고르면 정간보의 선택도 그 음표가 시작하는 정간으로 옮긴다. */
    fun selectStaffNote(key: StaffNoteKey): SheetEditUiState = copy(staffKey = key, selected = key.start)

    /** 오선보에서 고른 음표의 높이를 [steps]만큼 바꾼다. 쉼표는 바꾸지 않는다. */
    fun shiftStaffNote(steps: Int): SheetEditUiState {
        val note = staffNote() ?: return this
        if (note.isRest) return this
        return commit(draft.shiftPitch(note.key.start, note.key.sub, steps), note.key.start).copy(staffKey = note.key)
    }

    /** '저장': 빈 정간이 있으면 경고 창을 열고, 없으면 저장했다고 알린다. */
    fun requestSave(): SheetEditUiState =
        if (draft.emptyCells.isNotEmpty()) copy(emptyWarning = true) else copy(emptyWarning = false, savedOnce = true, message = SheetEditMessage.Saved)

    /** '임시저장': 빈 정간이 있어도 된다. 경고 창에서 눌러도 같다. */
    fun saveDraft(): SheetEditUiState = copy(emptyWarning = false, message = SheetEditMessage.Draft)

    /** '음원 받기': 마지막으로 저장한 악보로 만든다. 저장한 적이 없으면 먼저 저장하라고 알린다. */
    fun requestAudio(): SheetEditUiState = copy(message = if (savedOnce) SheetEditMessage.Audio else SheetEditMessage.AudioNeedsSave)

    /** 경고 창의 '빈 정간 채우기': 첫 빈 정간을 고르고 정간보 율명 입력으로 돌아간다. */
    fun fillEmpty(): SheetEditUiState {
        val first = draft.emptyCells.firstOrNull() ?: selected
        return copy(emptyWarning = false, selected = first, view = EditView.Jeongganbo, tab = EditTab.Yul)
    }

    companion object {
        const val HISTORY_LIMIT = 30

        /** 들어온 경로에 맞는 처음 상태. 인식 결과에서 오면 와이어프레임처럼 5각 5정간을, 아니면 첫 정간을 고른다. */
        fun initial(source: EditSource, draft: SheetDraft, startView: EditView = EditView.Jeongganbo): SheetEditUiState {
            val fromImport = source is EditSource.Import
            val selected = if (fromImport) SheetEditSamples.IMPORT_FIRST_SELECTED.coerceAtMost(draft.cells.lastIndex) else 0
            return SheetEditUiState(
                source = source,
                draft = draft,
                hwang = (source as? EditSource.Import)?.hwang ?: HwangPitch.Default,
                selected = selected,
                view = startView,
                savedOnce = source is EditSource.Mine,
            )
        }
    }
}
