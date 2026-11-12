package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.data.user.LearnProgressRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearnUiStateTest {

    /** 서버 단계 목록(LRN-01)과 같은 9단계: 1·2 설명, 3~6 음 판정, 7·8 박 판정, 9 듣기(선택). */
    private val stages = (1..9).map { n ->
        val judge = when (n) {
            1, 2 -> LearnJudge.Read
            in 3..6 -> LearnJudge.Pitch
            7, 8 -> LearnJudge.Beat
            else -> LearnJudge.Listen
        }
        LearnStage(n, "단계 $n", judge, optional = n == 9, lessonId = "danso-$n")
    }

    /** 단소·소금 단계 목록을 받은 상태. */
    private val base = LearnUiState()
        .trackLoaded(GuideInstrument.Danso, stages, emptySet())
        .trackLoaded(GuideInstrument.Sogeum, stages, emptySet())

    @Test
    fun beforeTrackLoadsThereAreNoStagesAndCardsStartAtFirst() {
        val state = LearnUiState()
        assertEquals(LearnTrackLoad.Loading, state.track)
        assertTrue(state.stages.isEmpty())
        assertNull(state.nextStage)
        assertEquals(0f, state.progress)
        assertEquals(1, state.cardStage)
        // 받기 전에는 아무 단계도 표시·선택할 수 없다.
        assertEquals(state, state.viewCard(3))
        assertNull(state.selectStage(3).selectedStage)
    }

    @Test
    fun loadedTrackShowsServerDoneMarksAndFailureKeepsOtherInstruments() {
        val state = LearnUiState().trackLoaded(GuideInstrument.Danso, stages, setOf(1, 2))
        assertEquals(setOf(1, 2), state.doneStages)
        assertEquals(3, state.nextStage?.number)

        val failed = state.trackLoaded(GuideInstrument.Sogeum, null, emptySet()).selectInstrument(GuideInstrument.Sogeum)
        assertEquals(LearnTrackLoad.Failed, failed.track)
        assertEquals(setOf(1, 2), failed.selectInstrument(GuideInstrument.Danso).doneStages)
    }

    @Test
    fun revertRestoresMarkAndRaisesSaveError() {
        val marked = base.toggleDone(3)
        val reverted = marked.revertDone(GuideInstrument.Danso, 3, completed = false, LearnSaveError.Mark)
        assertFalse(reverted.isDone(3))
        assertEquals(LearnSaveError.Mark, reverted.saveError)

        val reset = base.confirm(1).resetDone().revertDone(GuideInstrument.Danso, 1, completed = true, LearnSaveError.Reset)
        assertTrue(reset.isDone(1))
        assertEquals(LearnSaveError.Reset, reset.saveError)
    }

    @Test
    fun detailsAreKeptPerInstrumentAndStage() {
        val detail = LearnStageDetail(goal = "목표", why = "이유", source = "출처")
        val state = base.withDetail(GuideInstrument.Danso, 4, LearnDetailLoad.Loaded(detail))
        assertEquals(LearnDetailLoad.Loaded(detail), state.detail(4))
        assertEquals(LearnDetailLoad.Loading, state.detail(5))
        assertEquals(LearnDetailLoad.Loading, state.selectInstrument(GuideInstrument.Sogeum).detail(4))
    }

    @Test
    fun startsWithDansoAndNothingDone() {
        val state = base
        assertEquals(GuideInstrument.Danso, state.instrument)
        assertEquals(0, state.requiredDoneCount)
        assertEquals(1, state.nextStage?.number)
    }

    @Test
    fun progressCountsRequiredStagesOnly() {
        val state = base.toggleDone(1).toggleDone(2).toggleDone(9)

        assertEquals(setOf(1, 2, 9), state.doneStages)
        assertEquals(2, state.requiredDoneCount)
        assertEquals(0.25f, state.progress)
        assertEquals(3, state.nextStage?.number)
    }

    @Test
    fun nextStageIncludesOptionalStageAndIsNullWhenAllDone() {
        val required = stages.filterNot { it.optional }.fold(base) { s, stage -> s.confirm(stage.number) }
        assertEquals(1f, required.progress)
        assertEquals(9, required.nextStage?.number)

        assertNull(required.confirm(9).nextStage)
    }

    @Test
    fun doneMarksAreKeptPerInstrument() {
        val danso = base.toggleDone(1)
        val sogeum = danso.selectInstrument(GuideInstrument.Sogeum)
        assertTrue(sogeum.doneStages.isEmpty())

        val back = sogeum.toggleDone(3).selectInstrument(GuideInstrument.Danso)
        assertEquals(setOf(1), back.doneStages)
    }

    @Test
    fun toggleRemovesAndResetClearsOnlyCurrentInstrument() {
        val state = base.toggleDone(4).toggleDone(4)
        assertFalse(state.isDone(4))

        val both = base.confirm(1).selectInstrument(GuideInstrument.Sogeum).confirm(2)
        val reset = both.resetDone()
        assertTrue(reset.doneStages.isEmpty())
        assertEquals(setOf(1), reset.selectInstrument(GuideInstrument.Danso).doneStages)
    }

    @Test
    fun confirmKeepsDoneAndMovesToNextStageOrList() {
        val state = base.confirm(1).confirm(1)
        assertEquals(setOf(1), state.doneStages)
        assertEquals(2, base.stageAfterConfirm(1))
        assertNull(base.stageAfterConfirm(9))
    }

    @Test
    fun selectingInstrumentClosesDropdown() {
        val state = base.toggleDropdown()
        assertTrue(state.isDropdownOpen)
        assertFalse(state.selectInstrument(GuideInstrument.DaegeumSanjo).isDropdownOpen)
        assertFalse(state.dismissDropdown().isDropdownOpen)
    }

    @Test
    fun shownStageFollowsSelectionThenNextStage() {
        val state = base.confirm(1).confirm(2)
        assertNull(state.selectedStage)
        assertEquals(3, state.shownStage)

        val selected = state.toggleDropdown().selectStage(6)
        assertEquals(6, selected.shownStage)
        assertFalse(selected.isDropdownOpen)

        assertEquals(3, selected.selectStage(null).shownStage)
        assertNull(state.selectStage(0).selectedStage)
        assertNull(state.selectStage(10).selectedStage)
    }

    @Test
    fun shownStageIsFirstStageWhenAllDone() {
        val all = stages.fold(base) { s, stage -> s.confirm(stage.number) }
        assertEquals(1, all.shownStage)
    }

    @Test
    fun togglingShownDoneKeepsTheSameStageInView() {
        val state = base.confirm(1)
        assertEquals(2, state.shownStage)

        val marked = state.toggleShownDone(2)
        assertTrue(marked.isDone(2))
        assertEquals(2, marked.shownStage)
    }

    @Test
    fun cardStartsAtNextStageAndFirstStageWhenAllDone() {
        assertEquals(1, base.cardStage)
        assertEquals(4, base.confirm(1).confirm(2).confirm(3).cardStage)

        val all = stages.fold(base) { s, stage -> s.confirm(stage.number) }
        assertEquals(1, all.cardStage)
    }

    @Test
    fun viewedCardStaysEvenWhenDoneMarksChange() {
        // 상세에서 보던 단계 · '확인했어요' 뒤 연 단계가 목록에 돌아와도 그대로 펼쳐진다
        val viewed = base.viewCard(6)
        assertEquals(6, viewed.cardStage)
        assertEquals(6, viewed.toggleDone(1).cardStage)
        assertEquals(7, viewed.confirm(6).viewCard(7).confirm(1).cardStage)
    }

    @Test
    fun unknownCardIsIgnoredAndInstrumentOrResetReturnsToNextStage() {
        val viewed = base.confirm(1).viewCard(5)
        assertEquals(5, viewed.viewCard(0).viewCard(10).cardStage)

        assertNull(viewed.selectInstrument(GuideInstrument.Sogeum).viewedCard)
        assertEquals(1, viewed.selectInstrument(GuideInstrument.Sogeum).cardStage)
        assertEquals(1, viewed.resetDone().cardStage)
    }

    @Test
    fun everyPublicStageHasArt() {
        assertEquals(LearnProgressRepository.STAGE_COUNT, LearnArtCount, "공개 단계마다 그림이 하나씩이다")
    }

    @Test
    fun bottomBarFollowsBoardForEachJudge() {
        // 보드 Learn-Stage: 1·2 확인했어요, 3~8 연습 시작, 9 모범 연주 듣기
        assertEquals(
            listOf(
                LearnStageAction.Confirm, LearnStageAction.Confirm,
                LearnStageAction.Practice, LearnStageAction.Practice, LearnStageAction.Practice,
                LearnStageAction.Practice, LearnStageAction.Practice, LearnStageAction.Practice,
                LearnStageAction.ListenModel,
            ),
            stages.map { it.primaryAction },
        )
    }
}
