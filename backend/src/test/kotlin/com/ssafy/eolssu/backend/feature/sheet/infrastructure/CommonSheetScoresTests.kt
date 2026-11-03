package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import com.ssafy.eolssu.backend.feature.sheet.application.PreparedScore
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.CommonScoreTimelineDecoder
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

/** 저장 전 확인(미입력 칸·최상위 모양). 타임라인 검증은 CAT-04 디코더 테스트와 DB 테스트가 다룬다. */
class CommonSheetScoresTests {
    private val json = JsonMapper.builder().build()
    private val scores = CommonSheetScores(json, CommonScoreTimelineDecoder(json)) { emptyList() }

    private fun document(cells: String) =
        """{"schemaVersion":1,"score":{"scoreVersion":1},"music":{"jangdan":"세마치","bpmUnit":"JANGDAN_BEAT"},""" +
            """"columns":[{"index":0,"cells":[$cells]}],"events":[{"kind":"NOTE"},{"kind":"REST"}],"provenance":{}}"""

    private val filled = """{"id":"c0-j0","index":0,"units":[{"row":0,"col":0,"symbol":"황","eventId":"c0-j0-r0-u0"}]}"""

    @Test
    fun `a filled score gets the new version and reports its counts and music`() {
        val ready = assertIs<PreparedScore.Ready>(scores.prepare(document(filled), 2))

        assertEquals(2, json.readTree(ready.json).path("score").path("scoreVersion").intValue())
        assertEquals(listOf(1, 1), listOf(ready.columnCount, ready.noteCount))
        assertEquals("세마치" to "JANGDAN_BEAT", ready.jangdanName to ready.bpmUnit)
    }

    @Test
    fun `empty cells are listed by id, and an empty cell without an id is malformed`() {
        assertEquals(PreparedScore.Unfilled(listOf("c0-j1")), scores.prepare(document("""$filled,{"id":"c0-j1","index":1,"units":[]}"""), 2))
        assertEquals(PreparedScore.Malformed, scores.prepare(document("""$filled,{"index":1,"units":[]}"""), 2))
    }

    @Test
    fun `missing or mistyped top-level fields are malformed rather than a server error`() {
        // Jackson 3의 MissingNode.intValue()·stringValue()는 0·null을 주지 않고 예외를 던진다.
        for (score in listOf("{}", """{"schemaVersion":"1"}""", document(filled).replace(""""kind":"NOTE"""", """"kind":1"""))) {
            assertEquals(PreparedScore.Malformed, scores.prepare(score, 2), score)
        }
        assertNull(scores.unfilledCellCount("{}"))
        assertNull(scores.unfilledCellCount("""{"schemaVersion":"1"}"""))
        assertEquals(PreparedScore.Malformed, scores.prepare(document("""$filled,{"id":"","index":1,"units":[]}"""), 2))
    }

    @Test
    fun `drafts only need the common score top level and count empty cells`() {
        assertEquals(1, scores.unfilledCellCount(document("""$filled,{"id":"c0-j1","index":1,"units":[]}""")))
        assertNull(scores.unfilledCellCount("""{"schemaVersion":1,"score":{}}"""))
        assertNull(scores.unfilledCellCount("not json"))
    }
}
