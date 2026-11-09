package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.model.fingering.Blow
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_tour_done_body_beat
import eolssu.shared.generated.resources.learn_tour_done_body_notes
import eolssu.shared.generated.resources.learn_tour_done_body_patterns
import eolssu.shared.generated.resources.learn_tour_done_body_range
import eolssu.shared.generated.resources.learn_tour_done_body_song
import kotlin.test.Test
import kotlin.test.assertEquals

/** 4~8단계 따라 배우기(S15P31E102-626)의 칸 수와 그림·완료 문구 짝. */
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

    /** 4단계는 앞 두 음의 운지가 있어야 할 일 3칸(첫 음 · 옮겨 가기 · 남은 음), 없으면 따라 배우기를 두지 않는다. */
    @Test fun stageFourHasThreeStepsOnlyWithTwoKnownNotes() {
        assertEquals(3, stageTourCount(4, detail(listOf("汰", "潢", "無", "林", "仲")), danso))
        assertEquals(0, stageTourCount(4, detail(listOf("汰")), danso))
        assertEquals(0, stageTourCount(4, detail(listOf("汰", "없는음")), danso))
    }

    /** 5단계는 기본 5음이 있으면 패턴 5칸, 모자라면 없다. */
    @Test fun stageFiveHasOneStepPerPattern() {
        assertEquals(5, stageTourCount(5, detail(listOf("仲", "林", "無", "潢", "汰")), danso))
        assertEquals(0, stageTourCount(5, detail(listOf("仲", "林")), danso))
    }

    /** 6단계는 새 음역마다 '내기 · 음계 오르내리기' 2칸. */
    @Test fun stageSixHasTwoStepsPerRange() {
        val high = LearnRange(Blow.High, "역취 · 한 옥타브 위", "", "㳞")
        val low = LearnRange(Blow.Low, "저취 · 한 옥타브 아래", "", "㣴")
        assertEquals(2, stageTourCount(6, detail(ranges = listOf(high)), danso))
        assertEquals(4, stageTourCount(6, detail(ranges = listOf(high, low)), danso))
        assertEquals(0, stageTourCount(6, detail(), danso))
    }

    /** 7·8단계는 서버 할 일 수 그대로다. */
    @Test fun stagesSevenAndEightFollowServerTasks() {
        assertEquals(3, stageTourCount(7, detail(tasks = 3), danso))
        assertEquals(3, stageTourCount(8, detail(tasks = 3), danso))
        assertEquals(0, stageTourCount(8, detail(), danso))
    }

    /** 7단계 서버 할 일 순서(혀치기 · 1정간을 2로 · 3으로)와 박 나누기 그림 짝. */
    @Test fun stageSevenSplitsFollowServerTaskOrder() {
        assertEquals(listOf(null, 2, 3, null), (0..3).map(::beatSplitOf))
    }

    @Test fun doneBodyPerStage() {
        assertEquals(
            listOf(
                Res.string.learn_tour_done_body_notes,
                Res.string.learn_tour_done_body_patterns,
                Res.string.learn_tour_done_body_range,
                Res.string.learn_tour_done_body_beat,
                Res.string.learn_tour_done_body_song,
            ),
            (4..8).map(::learnDoneBody),
        )
    }
}
