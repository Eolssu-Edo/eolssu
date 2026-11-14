package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.sheet.domain.SheetJangdan
import com.ssafy.eolssu.backend.feature.song.domain.ScoreTimelineEvent
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import kotlin.test.*

class CopyPublishedSheetTests {
    private val json = JsonMapper.builder().build()
    private val four = SheetJangdan(12, "QUARTER", "4박 기본 반주", """{"beats":["덩","덕","궁","덕"],"group":[1,1,1,1]}""")
    private val danmori = SheetJangdan(7, "QUARTER", "단모리", """{"beats":["덩","","따","따","궁","따","궁",""],"group":[2,2,2,2]}""")
    private fun score(name: String = four.name, unit: String = "QUARTER", sizes: List<Int> = listOf(4, 4)) =
        """{"music":{"jangdan":"$name","bpmUnit":"$unit","ticksPerBeat":12,"ticksPerJeonggan":12},
        "columns":[${sizes.joinToString { count -> "{\"cells\":[${List(count) { "{}" }.joinToString()}]}" }}],
        "events":[{"kind":"NOTE","cellId":"c0-j0","startTick":0}]}"""

    private class Repository(val source: PublishedSheetSource) : PublishedSheetCopyRepository {
        var saved: PublishedSheetCopy? = null
        override fun replay(request: SheetCopyRequest): SheetCopyResult? = null
        override fun findSource(songId: Long) = source
        override fun save(copy: PublishedSheetCopy): SheetCopyResult { saved = copy; return SheetCopyResult.Created(1) }
    }

    private class Scores : SheetScores {
        override fun timeline(scoreJson: String, scoreVersion: Int, noteCount: Int, bpm: Int, bpmUnit: String) =
            emptyList<ScoreTimelineEvent>()
        override fun prepare(scoreJson: String, scoreVersion: Int): PreparedScore {
            val music = JsonMapper.builder().build().readTree(scoreJson).path("music")
            return PreparedScore.Ready(scoreJson, 2, 1, music.path("jangdan").asString(), music.path("bpmUnit").asString())
        }
        override fun unfilledCellCount(scoreJson: String): Int? = 0
    }

    private fun copy(scoreJson: String, songJangdan: Long? = null, accompaniment: SheetJangdan?): Long? {
        val repository = Repository(PublishedSheetSource("곡", songJangdan, 77, "QUARTER", 1, scoreJson, 1))
        val result = CopyPublishedSheet(repository, Scores(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            accompaniment = CopyJangdanSource { accompaniment }, jangdanValidation = SheetJangdanValidation(json))
            .copy(memberId = 1, songId = 5, key = "k")
        assertEquals(SheetCopyResult.Created(1), result)
        return assertNotNull(repository.saved).jangdanId
    }

    @Test fun `a song without its own jangdan link copies the accompaniment it plays`() {
        assertEquals(12L, copy(score(), accompaniment = four))
    }

    @Test fun `an accompaniment the copy could not save with is left out`() {
        assertNull(copy(score(), accompaniment = danmori), "이름이 다르면 넣지 않는다")
        assertNull(copy(score(unit = "DOTTED_QUARTER"), accompaniment = four), "박 단위가 다르면 넣지 않는다")
        assertNull(copy(score(sizes = listOf(4, 2)), accompaniment = four), "각 길이가 주기와 맞지 않으면 넣지 않는다")
        assertNull(copy(score(), accompaniment = null), "원곡 반주가 없으면 장단 없이 복사한다")
    }

    @Test fun `a song's own jangdan link wins over the accompaniment`() {
        assertEquals(3L, copy(score(), songJangdan = 3, accompaniment = four))
    }
}
