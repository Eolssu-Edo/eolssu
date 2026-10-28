package com.ssafy.eolssu.feature.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConsentViewModelTest {
    private val required = listOf(Agreement.Age, Agreement.Terms, Agreement.Privacy, Agreement.Recording)

    @Test
    fun agreementsFollowWireframeOrderAndRequiredFlags() {
        assertEquals(required + Agreement.News, Agreement.entries)
        assertEquals(required, Agreement.entries.filter { it.required })
        // 만 14세 이상(AGE_OVER_14)도 문서가 있는 필수 약관이라 모든 항목에 '보기'가 있다. 소식·이벤트 알림은 선택 약관이다.
        assertEquals(Agreement.entries, Agreement.entries.filter { it.hasTerms })
    }

    @Test
    fun startsWithNothingCheckedAndCannotStart() {
        val state = ConsentViewModel().uiState.value

        assertTrue(state.checked.isEmpty())
        assertFalse(state.canStart)
        assertFalse(state.allChecked)
    }

    @Test
    fun allRequiredWithoutOptionalCanStartButIsNotAllChecked() {
        val viewModel = ConsentViewModel()

        required.dropLast(1).forEach(viewModel::toggle)
        assertFalse(viewModel.uiState.value.canStart)

        viewModel.toggle(required.last())
        assertTrue(viewModel.uiState.value.canStart)
        assertFalse(viewModel.uiState.value.allChecked)
    }

    @Test
    fun togglingTwiceUnchecks() {
        val viewModel = ConsentViewModel()

        viewModel.toggle(Agreement.News)
        viewModel.toggle(Agreement.News)

        assertTrue(viewModel.uiState.value.checked.isEmpty())
    }

    @Test
    fun agreeAllChecksEverythingAndSecondTapClearsEverything() {
        val viewModel = ConsentViewModel()

        viewModel.toggleAll()
        assertEquals(Agreement.entries.toSet(), viewModel.uiState.value.checked)
        assertTrue(viewModel.uiState.value.allChecked)

        viewModel.toggleAll()
        assertTrue(viewModel.uiState.value.checked.isEmpty())
    }

    @Test
    fun agreeAllWhenPartlyCheckedChecksEverything() {
        val viewModel = ConsentViewModel()
        viewModel.toggle(Agreement.Age)

        viewModel.toggleAll()

        assertTrue(viewModel.uiState.value.allChecked)
    }

    @Test
    fun uncheckingOneAfterAgreeAllTurnsOffAllAndStart() {
        val viewModel = ConsentViewModel()
        viewModel.toggleAll()

        viewModel.toggle(Agreement.Recording)

        assertFalse(viewModel.uiState.value.allChecked)
        assertFalse(viewModel.uiState.value.canStart)
    }
}
