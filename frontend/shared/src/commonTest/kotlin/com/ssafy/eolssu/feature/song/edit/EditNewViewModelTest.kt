package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.feature.song.edit.component.SheetFileProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class EditNewViewModelTest {
    @Test fun reimportProblemShowsOnceAndStaysClosedAfterDismiss() {
        val model = EditNewViewModel()
        model.showReimportProblem(SheetFileProblem.Unrecognized)
        assertEquals(SheetFileProblem.Unrecognized, model.uiState.value.problem)
        model.dismissProblem()
        model.showReimportProblem(SheetFileProblem.Unrecognized)
        assertNull(model.uiState.value.problem)
    }

    @Test fun selectedKindIsKeptWhileProblemIsShown() {
        val model = EditNewViewModel()
        model.selectKind(ImportKind.Staff)
        model.showReimportProblem(SheetFileProblem.Unreadable)
        assertEquals(ImportKind.Staff, model.uiState.value.kind)
        model.showReimportProblem(null)
        assertEquals(SheetFileProblem.Unreadable, model.uiState.value.problem)
    }
}
