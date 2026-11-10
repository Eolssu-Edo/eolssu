package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.model.displayUnitAt
import com.ssafy.eolssu.core.model.fingeringYul
import com.ssafy.eolssu.core.model.timeline
import com.ssafy.eolssu.feature.practice.PracticeUiState
import com.ssafy.eolssu.feature.practice.selectUnit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommonScoreDataTest {
    @Test fun readsOptionalLyricsWithoutChangingTheTimeline() {
        val source = smallScoreData()
        val json = Json.encodeToString(source)
            .replaceFirst("\"units\":", "\"lyric\":\"아\",\"units\":")
        val score = CommonScoreData.decode(json).toScore()
        assertEquals("아", score.columns[0][0].lyric)
        assertNull(score.columns[0][1].lyric)
        assertEquals(source.toScore().events, score.events)
    }

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

    @Test fun aSustainedNoteCrossesColumnsWithoutChangingItsIdentityOrTiming() {
        for (symbol in listOf("", "-")) {
            val song = smallScoreSong(crossColumnScoreData(symbol)).copy(baseBpm = 30)
            val sustained = song.timeline[1]
            val continuation = song.displayUnits[4]
            assertEquals(3, song.timeline.size)
            assertEquals(listOf(0L, 1L, 12L), song.timeline.map { it.startTick })
            assertEquals(listOf(1L, 11L, 4L), song.timeline.map { it.durationTick })
            assertEquals(listOf(0, 1, null), song.timeline.map { it.noteIndex })
            assertEquals(0, sustained.column)
            assertEquals(1, continuation.column)
            assertEquals(sustained, continuation.event)
            assertEquals(symbol, continuation.symbol)
            assertEquals(5500.0, song.eventDurationMs(sustained))
            assertEquals(8000.0, song.durationMs())
            val playing = PracticeUiState(song = song, position = 1, eventElapsedMs = 4000.0)
            assertEquals(continuation, playing.currentScoreUnit)
            assertEquals(ScorePosition(1, 0), playing.selectUnit(continuation).scoreSelection)
        }
    }

    @Test fun crossColumnContinuationsStillRequireValidOriginsPitchAndCoveredTime() {
        val source = crossColumnScoreData()
        val continuation = source.columns[1].cells[0]
        fun withContinuation(symbol: String = "", eventId: String = source.events[1].id) = source.copy(
            columns = listOf(source.columns[0], source.columns[1].copy(cells = listOf(
                continuation.copy(units = listOf(CommonScoreData.Unit(0, 0, symbol, eventId))),
                source.columns[1].cells[1],
            ))),
        )
        val invalid = listOf(
            withContinuation(eventId = "missing"),
            withContinuation(eventId = source.events.last().id), // The next event has not started.
            withContinuation(symbol = "청황"), // Different pitch.
            withContinuation(symbol = "청중"), // Same pitch, but not a new attack.
            source.copy(events = listOf(source.events[0], source.events[1].copy(durationTick = 7),
                source.events[2].copy(startTick = 8, durationTick = 8))),
            source.copy(events = listOf(source.events[0], source.events[1].copy(cellId = "c1-j0"), source.events[2])),
            source.copy(columns = listOf(source.columns[0].copy(cells = listOf(
                source.columns[0].cells[0].copy(units = source.columns[0].cells[0].units.map {
                    if (it.row == 0 && it.col == 1) it.copy(symbol = "") else it
                }), source.columns[0].cells[1],
            )), source.columns[1])),
        )
        invalid.forEach { input -> assertFailsWith<ScoreDataException> { input.toScore() } }
    }

    @Test fun selectingAnAttackDoesNotSelectTheFollowingBlankCellWithTheSameEvent() {
        val song = smallScoreSong()
        val ready = PracticeUiState(song = song)
        assertNull(ready.scoreSelection)

        val attack = song.displayUnits[1]
        val blank = song.displayUnits[3]
        assertEquals(attack.event, blank.event)
        val selected = ready.selectUnit(attack)
        assertEquals(attack.event.index, selected.selected)
        assertEquals(ScorePosition(attack.column, attack.row), selected.scoreSelection)
        assertTrue(selected.scoreSelection != ScorePosition(blank.column, blank.row))

        val continued = selected.selectUnit(blank)
        assertEquals(ScorePosition(blank.column, blank.row), continued.scoreSelection)
        assertNull(continued.panel)
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
        val note = ScoreNote.SourcePitch("중", 1)
        assertEquals(note, state.selectUnit(song.displayUnits[1]).draft.note)
        assertEquals(note, state.selectUnit(song.displayUnits[0]).selectUnit(song.displayUnits[1]).draft.note)
    }

    @Test fun unsupportedOctavesRemainExactAndNeverFallBackToBaseFingering() {
        val cheongJung = ScoreNote.SourcePitch("중", 1)
        assertEquals("㳞", cheongJung.symbol)
        assertEquals("청중려", cheongJung.fullName)
        assertEquals(com.ssafy.eolssu.core.model.Yul.CheongJungnyeo, cheongJung.fingeringYul)
        assertNull(ScoreNote.SourcePitch("중", 2).fingeringYul)
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

private fun crossColumnScoreData(symbol: String = ""): CommonScoreData {
    val source = smallScoreData()
    val sustained = source.events[1]
    val rest = source.events.last().copy(id = "c1-j1-r0-u0", cellId = "c1-j1", startTick = 12)
    return source.copy(
        columns = listOf(source.columns[0], CommonScoreData.Column(1, listOf(
            CommonScoreData.Cell("c1-j0", 0, listOf(CommonScoreData.Unit(0, 0, symbol, sustained.id))),
            CommonScoreData.Cell("c1-j1", 1, listOf(CommonScoreData.Unit(0, 0, "쉼", rest.id))),
        ))),
        events = listOf(source.events[0], sustained.copy(durationTick = 11), rest),
    )
}

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
