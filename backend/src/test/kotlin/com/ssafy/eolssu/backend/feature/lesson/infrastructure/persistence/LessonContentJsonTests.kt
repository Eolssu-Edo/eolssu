package com.ssafy.eolssu.backend.feature.lesson.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.lesson.domain.LessonTask
import com.ssafy.eolssu.backend.feature.lesson.domain.PitchNote
import com.ssafy.eolssu.backend.feature.lesson.domain.PitchRange
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class LessonContentJsonTests {
    private val reader = LessonContentJson(JsonMapper.builder().build())

    private val full = """{"goal":"목표","why":"이유","tasks":[{"title":"할 일","description":null},{"title":"잡기","description":"세로로"}],
        "passCriteria":null,"pitchNames":["汰"],"pitchNote":{"text":"순서","caution":null},"parts":["취구"],
        "ranges":[{"blow":"HIGH","label":"역취","description":"세게","firstPitch":"㳞"}],"songs":[],"source":"근거"}"""

    @Test
    fun `reads every field of the stage content`() {
        val content = reader.parse(1, full)

        assertEquals(listOf(LessonTask("할 일", null), LessonTask("잡기", "세로로")), content.tasks)
        assertNull(content.passCriteria)
        assertEquals(PitchNote("순서", null), content.pitchNote)
        assertEquals(listOf(PitchRange("HIGH", "역취", "세게", "㳞")), content.ranges)
        assertEquals("근거", content.source)
    }

    @Test
    fun `fails instead of dropping a malformed field`() {
        for (broken in listOf(
            full.replace("\"goal\":\"목표\",", ""),
            full.replace("\"songs\":[]", "\"songs\":null"),
            full.replace("\"pitchNames\":[\"汰\"]", "\"pitchNames\":[1]"),
            "not json",
        )) {
            assertFailsWith<LessonContentException> { reader.parse(1, broken) }
        }
    }
}
