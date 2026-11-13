package com.ssafy.eolssu.feature.practice.component

import com.ssafy.eolssu.feature.practice.PracticePopup
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_popup_end_body
import eolssu.shared.generated.resources.practice_popup_end_limit_body
import eolssu.shared.generated.resources.weak_loop_end_body
import kotlin.test.Test
import kotlin.test.assertEquals

/** ‘연주를 마쳤어요’ 창 설명: 구간 재평가만 한 줄짜리 평가 기준 안내를 쓴다. */
class PracticeEndDialogTextTest {
    @Test
    fun sectionEvaluationEndUsesOneLineCriteria() {
        assertEquals(Res.string.weak_loop_end_body, endBodyRes(PracticePopup.End, section = true))
    }

    @Test
    fun wholeSongEndKeepsExistingBody() {
        assertEquals(Res.string.practice_popup_end_body, endBodyRes(PracticePopup.End, section = false))
    }

    @Test
    fun recordingLimitKeepsLimitBodyEvenInSection() {
        assertEquals(Res.string.practice_popup_end_limit_body, endBodyRes(PracticePopup.EndAtLimit, section = true))
        assertEquals(Res.string.practice_popup_end_limit_body, endBodyRes(PracticePopup.EndAtLimit, section = false))
    }
}
