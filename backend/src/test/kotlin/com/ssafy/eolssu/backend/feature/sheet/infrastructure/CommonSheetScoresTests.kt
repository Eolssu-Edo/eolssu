package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import com.ssafy.eolssu.backend.feature.sheet.application.PreparedScore
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.CommonScoreTimelineDecoder
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

/** 저장 전 확인(미입력 칸·최상위 모양). 타임라인 검증은 CAT-04 디코더 테스트와 DB 테스트가 다룬다. */
class CommonSheetScoresTests {
    private val json = JsonMapper.builder().build()
    private val scores = CommonSheetScores(json, CommonScoreTimelineDecoder(json)) { emptyList() }

    private fun document(cells: String, provenance: String = "{}") =
        """{"schemaVersion":1,"score":{"scoreVersion":1,"sourceId":"sheet-7"},"music":{"jangdan":"세마치","bpmUnit":"JANGDAN_BEAT"},""" +
            """"columns":[{"index":0,"cells":[$cells]}],"events":[{"kind":"NOTE"},{"kind":"REST"}],"provenance":$provenance}"""

    private val filled = """{"id":"c0-j0","index":0,"units":[{"row":0,"col":0,"symbol":"황","eventId":"c0-j0-r0-u0"}]}"""

    @Test
    fun `a filled score gets the new version and reports its counts and music`() {
        val ready = assertIs<PreparedScore.Ready>(scores.prepare(document(filled), 2))

        assertEquals(2, json.readTree(ready.json).path("score").path("scoreVersion").intValue())
        assertEquals(listOf(1, 1), listOf(ready.columnCount, ready.noteCount))
        assertEquals("세마치" to "JANGDAN_BEAT", ready.jangdanName to ready.bpmUnit)
    }

    @Test
    fun `the server fills provenance the DATA schema requires instead of keeping the client's`() {
        fun saved(cells: String, provenance: String) =
            json.readTree(assertIs<PreparedScore.Ready>(scores.prepare(document(cells, provenance), 2)).json).path("provenance")
        val client = """{"sourceId":"other","jgbSha256":"bad","review":"rejected","converter":"x","extra":1}"""

        val provenance = saved(filled, client)
        assertEquals(setOf("sourceId", "jgbSha256", "review", "converter"), provenance.propertyNames().toSet())
        assertEquals(listOf("sheet-7", "inferred", "app-editor-1"), listOf("sourceId", "review", "converter").map { provenance.path(it).asString() })
        assertTrue(Regex("[0-9a-f]{64}").matches(provenance.path("jgbSha256").asString()))
        // 해시는 악보 내용으로 정해진다. 클라이언트가 보낸 provenance는 영향을 주지 않는다.
        assertEquals(provenance, saved(filled, "{}"))
        val rest = filled.replace("\"황\"", "\"태\"")
        assertNotEquals(provenance.path("jgbSha256"), saved(rest, client).path("jgbSha256"))
    }

    @Test
    fun `a score without a source id cannot fill provenance and is malformed`() {
        for (score in listOf("""{"scoreVersion":1}""", """{"scoreVersion":1,"sourceId":""}""", """{"scoreVersion":1,"sourceId":7}""")) {
            assertEquals(PreparedScore.Malformed, scores.prepare(document(filled).replace("""{"scoreVersion":1,"sourceId":"sheet-7"}""", score), 2), score)
        }
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
