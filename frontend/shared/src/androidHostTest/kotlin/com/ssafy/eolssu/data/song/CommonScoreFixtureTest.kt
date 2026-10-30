package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.ui.score.scoreStrips
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommonScoreFixtureTest {
    private fun load(name: String, hash: String): CommonScoreData {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/scores/$name.score.json")).use { it.readBytes() }
        assertEquals(hash, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        return CommonScoreData.decode(bytes.toString(Charsets.UTF_8))
    }

    @Test fun arirangKeepsFourTwelveCellColumnsAndSixtyOneNotes() {
        val data = load("ngc-standard-2022-p173", "51cd874652d705511cfaa0f2c03ba6d3632ae10b4a483036d6a438a8eac73f1b")
        val score = data.toScore()
        val jangdan = Jangdan("세마치", listOf(3, 3, 3), 3, 3)
        val song = Song("service-id", data.score.title, SongLevel.Intro, jangdan, 60, "", true, null, score)
        assertEquals(listOf(12, 12, 12, 12), score.columns.map { it.size })
        assertEquals(listOf(12, 12, 12, 12), scoreStrips(score, jangdan).map { it.rows.count() })
        assertEquals(65, song.timeline.size)
        assertEquals((0..60).toList(), song.timeline.mapNotNull { it.noteIndex })
        assertEquals(4, song.timeline.count { it.note == ScoreNote.Rest })
        assertEquals(144L, song.timeline.sumOf { requireNotNull(it.durationTick) })
        assertEquals(48.0, song.durationMs() / 1000)
        assertEquals("c0-j0-r0-u0", song.timeline.first().id)
        assertEquals(5L, song.timeline.first().durationTick)
        assertEquals(3, song.displayUnits.count { it.event.index == 0 })
        assertEquals(data.events.map { it.id }, song.timeline.map { it.id })
        assertEquals(data.columns.flatMap { c -> c.cells.flatMap { it.units } }.map { it.eventId }, song.displayUnits.map { it.event.id })
    }

    @Test fun nilliriyaUsesSixTicksPerBeatAndTwoTicksPerJeonggan() {
        val data = load("ngc-standard-2022-p174", "e43ad1bd7c32e397b7bd231aeadfdc03c854639a156b81432d8328c44138d3fe")
        val score = data.toScore()
        val jangdan = Jangdan("굿거리", listOf(3, 3, 3, 3), 4, 3)
        val song = Song("42", data.score.title, SongLevel.Elementary, jangdan, 60, "", true, null, score)
        assertEquals(6, score.timing?.ticksPerBeat)
        assertEquals(2, score.timing?.ticksPerJeonggan)
        assertEquals(List(6) { 12 }, score.columns.map { it.size })
        assertEquals(72, scoreStrips(score, jangdan).sumOf { it.rows.count() })
        assertEquals(72, song.timeline.size)
        assertEquals(68, song.timeline.count { it.noteIndex != null })
        assertEquals(144L, song.timeline.sumOf { requireNotNull(it.durationTick) })
        assertEquals(24.0, song.durationMs() / 1000)
        assertEquals(60_000.0 / 66 / 3, song.cellDurationMs(66), 0.000001)
        assertTrue(song.timeline.any { it.note == ScoreNote.SourcePitch("중", 1) })
        assertEquals(data.events.map { it.durationTick }, song.timeline.map { it.durationTick })
    }
}
