package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.model.displayUnitAt
import com.ssafy.eolssu.core.model.fingeringYul
import com.ssafy.eolssu.core.model.timeline
import com.ssafy.eolssu.feature.practice.PracticeUiState
import com.ssafy.eolssu.feature.practice.selectEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommonScoreDataTest {
    @Test fun preservesEventIdentityPitchAndNoteOnlyIndicesAcrossDisplayTies() {
        val source = smallScoreData()
        val score = source.toScore()
        val events = score.timeline("database-id-is-not-a-prefix")
        assertEquals(source.events.map { it.id }, events.map { it.id })
        assertEquals(listOf(0, 1, null), events.map { it.noteIndex })
        assertEquals(listOf(0L, 1L, 8L), events.map { it.startTick })
        assertEquals(listOf(1L, 7L, 4L), events.map { it.durationTick })
        assertEquals(ScoreNote.SourcePitch("중", 1), events[1].note)
        assertTrue(events[1].breathAfter)
        assertEquals(listOf("니레"), events[1].sigimsae)
        assertEquals(source.score.sourceId, score.source?.sourceId)
        assertEquals(source.provenance.jgbSha256, score.source?.provenance?.jgbSha256)
        assertEquals(source.music.hwangHz, score.timing?.hwangHz)
    }

    @Test fun rowsAndColumnsArePreservedWithoutTurningTiesIntoPlaybackEvents() {
        val song = smallScoreSong()
        val units = song.displayUnits
        assertEquals(3, song.timeline.size)
        assertEquals(5, units.size)
        assertEquals(listOf(0 to 0, 0 to 1, 1 to 0), units.take(3).map { it.unitRow to it.unitCol })
        assertEquals(listOf(0.0, 0.25, 0.5, 1.0, 2.0), units.map { it.unitStart })
        assertEquals(listOf(0.25, 0.25, 0.5, 1.0, 1.0), units.map { it.unitDuration })
        assertEquals(listOf("황", "청중", "-", "", "쉼"), units.map { it.symbol })
        assertEquals(listOf(0, 1, 1, 1, 2), units.map { it.event.index })
        assertEquals(ScoreNote.Blank, units[3].note)
        assertEquals(1, song.displayUnitAt(1, 0.75f)?.row)
        assertEquals(0, song.displayUnitAt(1, 0.75f)?.column)
    }

    @Test fun timingUsesScoreBpmUnitRatherThanLegacyJangdanSubdivision() {
        val song = smallScoreSong()
        assertEquals(1000.0, song.cellDurationMs())
        assertEquals(250.0, song.eventDurationMs(song.timeline[0]))
        assertEquals(1750.0, song.eventDurationMs(song.timeline[1]))
        assertEquals(3000.0, song.durationMs())
        assertEquals(6000.0, song.durationMs(30))
        val state = PracticeUiState(song = song)
        assertEquals(1750.0, state.eventDurationMs(1))
        // REST의 시작은 2박째: 세마치 구음 6번 칸 = 세 번째 묶음. 원본 정간 row(0)로 추정하면 틀린다.
        assertEquals(2, state.copy(position = 2).beatIndex)
        assertNull(state.selectEvent(1).panel) // 청중 운지 데이터를 중려 운지로 바꾸지 않는다.
        assertNull(state.selectEvent(0).selectEvent(1).panel) // 이전 음의 운지 패널도 남겨 두지 않는다.
    }

    @Test fun unsupportedOctavesRemainExactAndNeverFallBackToBaseFingering() {
        val cheongJung = ScoreNote.SourcePitch("중", 1)
        assertEquals("㳞", cheongJung.symbol)
        assertEquals("청중려", cheongJung.fullName)
        assertNull(cheongJung.fingeringYul)
        assertEquals("㳲", ScoreNote.SourcePitch("태", 2).symbol)
        assertEquals("㴢", ScoreNote.SourcePitch("중", 2).symbol)
    }

    @Test fun fractionalDisplayBoundariesDoNotRoundCanonicalTicks() {
        val base = smallScoreData()
        val id = "c0-j0-r0-u0"
        val source = base.copy(
            music = base.music.copy(ticksPerBeat = 1, ticksPerJeonggan = 1),
            columns = listOf(CommonScoreData.Column(0, listOf(CommonScoreData.Cell("c0-j0", 0,
                listOf(CommonScoreData.Unit(0, 0, "황", id), CommonScoreData.Unit(1, 0, "-", id), CommonScoreData.Unit(2, 0, "-", id)))))),
            events = listOf(base.events.first().copy(durationTick = 1)),
        )
        val song = smallScoreSong(source)
        assertEquals(1L, song.timeline.single().durationTick)
        assertEquals(listOf(0.0, 1.0 / 3, 2.0 / 3), song.displayUnits.map { it.unitStart })
        assertEquals(1, song.timeline.size)
    }

    @Test fun malformedOrUnsupportedScoresFailInsteadOfBecomingSamples() {
        val source = smallScoreData()
        val first = source.events.first()
        val column = source.columns.first()
        val cell = column.cells.first()
        val bad = listOf(
            source.copy(schemaVersion = 2),
            source.copy(music = source.music.copy(ticksPerBeat = 0)),
            source.copy(music = source.music.copy(bpmUnit = "UNKNOWN")),
            source.copy(events = source.events + first),
            source.copy(events = listOf(first.copy(startTick = 1)) + source.events.drop(1)),
            source.copy(events = source.events.dropLast(1)),
            source.copy(events = listOf(first.copy(cellId = "c9-j9")) + source.events.drop(1)),
            source.copy(events = source.events.dropLast(1) + source.events.last().copy(yul = "황")),
            source.copy(columns = listOf(column.copy(cells = listOf(cell.copy(units = cell.units.map { it.copy(eventId = "missing") })) + column.cells.drop(1))) + source.columns.drop(1)),
            source.copy(columns = listOf(column.copy(cells = listOf(cell.copy(units = cell.units.map { it.copy(row = it.row + 1) })) + column.cells.drop(1))) + source.columns.drop(1)),
            source.copy(columns = listOf(column.copy(cells = listOf(cell.copy(units = cell.units.map { it.copy(col = 0) })) + column.cells.drop(1))) + source.columns.drop(1)),
        )
        bad.forEach { input -> assertFailsWith<ScoreDataException> { input.toScore() } }
    }

    @Test fun strictJsonKeepsTheVersionedSchemaBoundary() {
        val json = Json.encodeToString(smallScoreData())
        assertEquals(smallScoreData(), CommonScoreData.decode(json))
        assertFailsWith<ScoreDataException> { CommonScoreData.decode(json.dropLast(1) + ",\"futureField\":true}") }
        assertFailsWith<ScoreDataException> { CommonScoreData.decode("{}") }
    }
}

internal fun smallScoreSong(data: CommonScoreData = smallScoreData()): Song = Song(
    "db-42", "작은 검사 악보", SongLevel.Intro,
    Jangdan("세마치", listOf(3, 3, 3), 3, 3), 60, "", false, null, data.toScore(),
)

internal fun smallScoreData(): CommonScoreData {
    val a = "c0-j0-r0-u0"
    val b = "c0-j0-r0-u1"
    val rest = "c1-j0-r0-u0"
    return CommonScoreData(1,
        CommonScoreData.Header("작은 검사 악보", "DANSO", 0, "unit-test-score", 1),
        CommonScoreData.Music(311.13, "세마치", "DOTTED_QUARTER", 4, 4),
        listOf(
            CommonScoreData.Column(0, listOf(
                CommonScoreData.Cell("c0-j0", 0, listOf(CommonScoreData.Unit(0, 0, "황", a), CommonScoreData.Unit(0, 1, "청중", b), CommonScoreData.Unit(1, 0, "-", b))),
                CommonScoreData.Cell("c0-j1", 1, listOf(CommonScoreData.Unit(0, 0, "", b))))),
            CommonScoreData.Column(1, listOf(CommonScoreData.Cell("c1-j0", 0, listOf(CommonScoreData.Unit(0, 0, "쉼", rest))))),
        ),
        listOf(
            CommonScoreData.Event(a, "NOTE", "황", 0, 0, 1, "c0-j0", false, emptyList()),
            CommonScoreData.Event(b, "NOTE", "중", 1, 1, 7, "c0-j0", true, listOf("니레")),
            CommonScoreData.Event(rest, "REST", null, null, 8, 4, "c1-j0", false, emptyList()),
        ),
        CommonScoreData.Provenance("unit-test-score", "0".repeat(64), "inferred", "test-fixture"),
    )
}
