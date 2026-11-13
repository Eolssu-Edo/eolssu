package com.ssafy.eolssu.app.navigation

import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.model.ScoreSection
import kotlin.test.Test
import kotlin.test.assertEquals

/** 구간 재평가 결과의 ‘구간 재평가 다시하기’ 이동(S15P31E102-826). */
class SectionRetryMoveTest {
    private val section = ScoreSection(ScorePosition(1, 3), ScorePosition(2, 5))

    @Test
    fun returnsToTheEvaluateScreenLeftBelowTheResult() {
        // 구간 재평가 → 분석 → 결과로 왔으면 분석은 닫혀 있고 바로 아래가 방금 평가한 구간 재평가 화면이다.
        val move = sectionRetryMove("base", WeakSectionDestination.EVALUATE, previousSource = "base", section = section)
        assertEquals(SectionRetryMove.BackToEvaluate, move)
    }

    @Test
    fun reopensTheSameSectionWhenTheEvaluateScreenIsNotBelow() {
        // 아래가 구간 재평가 화면이 아니면(웹에서 결과 주소로 연 경우 등) 결과를 닫고 같은 구간·바탕 연주로 새로 연다.
        val expected = SectionRetryMove.Replace("weak/base/evaluate?sc=1&sr=3&ec=2&er=5")
        assertEquals(expected, sectionRetryMove("base", WeakSectionDestination.SELECT, previousSource = "base", section = section))
        assertEquals(expected, sectionRetryMove("base", previousRoute = null, previousSource = null, section = section))
    }

    @Test
    fun doesNotReturnToAnotherSourcesEvaluateScreen() {
        // 아래의 구간 재평가 화면이 다른 바탕 연주의 것이면 그리로 돌아가지 않는다.
        val move = sectionRetryMove("base", WeakSectionDestination.EVALUATE, previousSource = "other", section = section)
        assertEquals(SectionRetryMove.Replace(WeakSectionDestination.evaluate("base", section)), move)
    }

    @Test
    fun opensSectionSelectWhenTheSectionIsUnknown() {
        val move = sectionRetryMove("base", previousRoute = null, previousSource = null, section = null)
        assertEquals(SectionRetryMove.Replace("weak/base/sections"), move)
    }
}
