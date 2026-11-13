package com.ssafy.eolssu.feature.practice.component

import com.ssafy.eolssu.feature.practice.PracticePopup
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_popup_end_body
import eolssu.shared.generated.resources.practice_popup_end_limit_body
import kotlin.test.Test
import kotlin.test.assertEquals

/** ‘연주를 마쳤어요’ 창 설명: 곡 전체 연주 평가와 구간 재평가가 같은 한 줄짜리 평가 기준 안내를 쓴다. */
class PracticeEndDialogTextTest {
    @Test
    fun endUsesOneLineCriteria() {
        assertEquals(Res.string.practice_popup_end_body, endBodyRes(PracticePopup.End))
    }

    @Test
    fun recordingLimitKeepsLimitBody() {
        assertEquals(Res.string.practice_popup_end_limit_body, endBodyRes(PracticePopup.EndAtLimit))
    }
}
