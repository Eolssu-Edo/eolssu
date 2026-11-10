package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.model.fingering.Blow
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_done_go_songs
import eolssu.shared.generated.resources.learn_tour_done_body_beat
import eolssu.shared.generated.resources.learn_tour_done_body_notes
import eolssu.shared.generated.resources.learn_tour_done_body_patterns
import eolssu.shared.generated.resources.learn_tour_done_body_range
import eolssu.shared.generated.resources.learn_tour_done_next
import kotlin.test.Test
import kotlin.test.assertEquals

/** 4~7단계 따라 배우기(S15P31E102-626)의 칸 수와 완료 문구 짝. */
class LearnStageTourTest {
    private val danso = GuideInstrument.Danso
    private fun detail(
        pitchNames: List<String> = emptyList(),
        ranges: List<LearnRange> = emptyList(),
        tasks: Int = 0,
    ) = LearnStageDetail(
        goal = "", why = "", source = "",
        tasks = List(tasks) { LearnTask("할 일 $it") },
        pitchNames = pitchNames,
        ranges = ranges,
    )

    /**
     * 4단계 첫 음은 3단계 첫 소리와 같아 칸을 두지 않는다(S15P31E102-655). 두 번째 음의 운지가 있어야
     * 할 일 2칸(옮겨 가기 · 남은 음), 없으면 따라 배우기를 두지 않는다.
     */
    @Test fun stageFourSkipsTheFirstSoundAndHasTwoSteps() {
        assertEquals(2, stageTourCount(4, detail(listOf("汰", "潢", "無", "林", "仲")), danso))
        assertEquals(0, stageTourCount(4, detail(listOf("汰")), danso))
        assertEquals(0, stageTourCount(4, detail(listOf("汰", "없는음")), danso))
    }

    /** 5단계는 기본 5음이 있으면 올라가기·내려가기 2칸, 모자라면 없다(건너뛰기·세 음 동요는 같은 음의 반복이라 뺐다). */
    @Test fun stageFiveHasAscendingAndDescendingOnly() {
        assertEquals(listOf(LearnPattern.Ascending, LearnPattern.Descending), LearnPattern.entries)
        assertEquals(2, stageTourCount(5, detail(listOf("仲", "林", "無", "潢", "汰")), danso))
        assertEquals(0, stageTourCount(5, detail(listOf("仲", "林")), danso))
    }

    /** 6단계는 새 음역마다 첫 음 내기 1칸(음계 오르내리기는 4·5단계 음의 반복이라 뺐다). */
    @Test fun stageSixHasOneStepPerRange() {
        val high = LearnRange(Blow.High, "역취 · 한 옥타브 위", "", "㳞")
        val low = LearnRange(Blow.Low, "저취 · 한 옥타브 아래", "", "㣴")
        assertEquals(1, stageTourCount(6, detail(ranges = listOf(high)), danso))
        assertEquals(2, stageTourCount(6, detail(ranges = listOf(high, low)), danso))
        assertEquals(0, stageTourCount(6, detail(), danso))
    }

    /** 7단계(혀치기)는 서버 할 일 수 그대로다. */
    @Test fun stageSevenFollowsServerTasks() {
        assertEquals(1, stageTourCount(7, detail(tasks = 1), danso))
        assertEquals(0, stageTourCount(7, detail(), danso))
    }

    /** 마지막 단계(7단계)를 마치면 '완료했어요!' 버튼이 다음 단계 대신 곡 연습으로 간다(S15P31E102-658). */
    @Test fun lastStageDoneLeadsToSongPractice() {
        assertEquals(Res.string.learn_done_go_songs, learnDoneAction(last = true))
        assertEquals(Res.string.learn_tour_done_next, learnDoneAction(last = false))
    }

    @Test fun doneBodyPerStage() {
        assertEquals(
            listOf(
                Res.string.learn_tour_done_body_notes,
                Res.string.learn_tour_done_body_patterns,
                Res.string.learn_tour_done_body_range,
                Res.string.learn_tour_done_body_beat,
            ),
            (4..7).map(::learnDoneBody),
        )
    }
}
