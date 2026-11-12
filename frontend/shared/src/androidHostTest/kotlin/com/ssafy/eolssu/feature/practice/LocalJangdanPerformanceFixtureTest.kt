package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.audio.DeviceSheetAudio
import com.ssafy.eolssu.core.audio.JangdanCursor
import com.ssafy.eolssu.core.audio.JangdanSample
import com.ssafy.eolssu.core.audio.LocalAudioFile
import com.ssafy.eolssu.core.audio.SheetAudioDeviceStore
import com.ssafy.eolssu.core.audio.SheetAudioDownload
import com.ssafy.eolssu.core.audio.SheetAudioPerformance
import com.ssafy.eolssu.core.audio.planJangdan
import com.ssafy.eolssu.core.model.JangdanPlayback
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.song.*
import io.ktor.client.engine.mock.MockEngine
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.test.*

/** Golden sidecars and channel-10 hits came from real CAT/FFmpeg, not from the FE clock. */
class LocalJangdanPerformanceFixtureTest {
    @Test fun numericStep() = runTest { checkFixture("step") }
    @Test fun numericRepeatRestartsTheWrittenCycle() = runTest { checkFixture("repeat") }
    @Test fun rampUsesReciprocalIntegralInsideTheCell() = runTest { checkFixture("ramp") }
    @Test fun dottedQuarterUsesTheSamePerformedClock() = runTest { checkFixture("dotted") }
    @Test fun importedBalancedLengthsKeepTheAccompanimentClock() = runTest { checkFixture("weighted") }
    @Test fun importedBalancedLengthsProjectRampTimeToTheWrittenClock() = runTest { checkFixture("weighted-ramp") }

    private suspend fun checkFixture(name: String) {
        val fixture = Json.parseToJsonElement(requireNotNull(javaClass.getResourceAsStream("/scores/personal-jangdan/$name.json"))
            .use { it.readBytes().toString(Charsets.UTF_8) }).jsonObject
        val raw = fixture.getValue("sourceJson").jsonPrimitive.content
        val sourceHash = fixture.getValue("sourceSha256").jsonPrimitive.content
        assertEquals(sourceHash, MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) })
        val source = CommonScoreData.decode(raw)
        val score = source.toScore()
        val ticksPerBeat = requireNotNull(score.timing).ticksPerBeat
        val totalTicks = 4.0 * ticksPerBeat
        val jangdan = fixture.getValue("jangdan").jsonObject
        val pattern = jangdan.getValue("pattern").jsonObject
        val mapping = JangdanPlayback(fixture.getValue("jangdanId").jsonPrimitive.content,
            jangdan.getValue("name").jsonPrimitive.content, 0, totalTicks.toLong(),
            pattern.getValue("beats").jsonArray.map { it.jsonPrimitive.content },
            pattern.getValue("group").jsonArray.map { it.jsonPrimitive.int })
        val song = Song("773-$name", source.score.title, SongLevel.Intro, mapping.displayPattern, 60, "", false,
            score, instrumentId = "1", scoreVersion = source.score.scoreVersion, jangdanPlayback = mapping)
        val timeline = fixture.getValue("performance").jsonObject
        val audioHash = timeline.getValue("audioSha256").jsonPrimitive.content
        val duration = fixture.getValue("audioDurationMs").jsonPrimitive.long
        val device = MemoryAudio()
        val requests = mutableListOf<SheetAudioSettings>()
        val generated = object : SheetAudioRepository {
            override suspend fun request(sheetId: String, settings: SheetAudioSettings): SheetAudioRequest {
                requests += settings
                return SheetAudioRequest.Accepted(SheetAudio(song.id, SheetAudioStatus.Done,
                    song.scoreVersion.toString(), song.scoreVersion.toString(), false, "1", false, 60,
                    audio = SheetAudioFile("https://fixture.invalid/$name.m4a", 60_000, "audio/mp4",
                        fixture.getValue("audioBytes").jsonPrimitive.long, "$name.m4a", audioHash, duration,
                        performance = SheetAudioPerformance(sourceHash, timeline))))
            }
            override suspend fun status(sheetId: String): SheetAudioLookup = error("Already generated")
        }
        val client = createHttpClient(MockEngine { error("Only local metadata and fixed audio transport") }, ServerAddress.Production)
        try {
            val member = SignedInMember(FakeKeyValueStore()).apply { set("7") }
            val repository = SheetPracticeRepository(ApiSheetDocumentRepository(client), device,
                ApiReferenceCatalogRepository(client), InstrumentCatalog(client), member, generated, { 0L })
            assertEquals(SheetPracticeDownload.Saved, repository.download(song))
            assertEquals(listOf(SheetAudioSettings("1", false, 60)), requests)
            val audio = assertNotNull(repository.get(song))
            val performance = assertNotNull(audio.performance)
            val cursor = JangdanCursor(assertNotNull(planJangdan(mapping, 60, ticksPerBeat)))
            val actual = buildList {
                for (ms in 0 until ceil(performance.durationMs).toInt()) {
                    if (ms >= performance.durationMs) break // The real player stops the callback at the performed end.
                    cursor.due(song.modelJangdanTickAt(audio, ms.toDouble())).forEach { add(ms.toDouble() to it.clip.sample) }
                }
            }
            val notes = mapOf(JangdanSample.Deong to 35, JangdanSample.Deok to 38, JangdanSample.Kung to 36)
            val expected = fixture.getValue("expectedStrikes").jsonArray
            assertEquals(expected.size, actual.size, name)
            expected.zip(actual).forEach { (hit, played) ->
                assertEquals(hit.jsonObject.getValue("note").jsonPrimitive.int, notes[played.second], name)
                assertTrue(abs(hit.jsonObject.getValue("ms").jsonPrimitive.double - played.first) <= 1.1,
                    "$name: actual $played differs from renderer $hit")
            }
            assertEquals(0.0, actual.first().first, "A first rest must not delay jangdan")
            assertEquals(totalTicks, song.modelJangdanTickAt(audio, duration.toDouble()), "Audio tail stays at score end")
            assertEquals(0L, song.modelMediaPosition(audio, -1.0))
            assertTrue(abs(performance.durationMs - song.modelMediaPosition(audio, totalTicks)) < 1.0)
            for (segment in performance.segments) {
                val tick = (segment.startTick + segment.endTick) / 2
                assertEquals(tick, segment.sourceTickAt(segment.mediaMsAt(tick)), 1e-9)
            }
            val state = PracticeUiState(song = song, mode = PracticeMode.Model)
                .withModelAudio(ModelAudioState(ModelAudioStatus.Playing, audio, 3000))
            val cell = mapping.cellAt(song.modelJangdanTickAt(audio, 3000.0))
            assertEquals(mapping.displayPattern.daegangIndexOfRow(cell), state.beatIndex)
            if (name == "ramp") {
                assertEquals(4 * ln(1.125) * 1000, performance.segments.first().mediaMsAt(0.5), 0.1)
                assertEquals(471L, song.modelMediaPosition(audio, 0.5))
                assertTrue(performance.segments.all { it.tempoRatio > 1 })
            }
            if (name.startsWith("weighted")) {
                val segment = performance.segments.first()
                assertEquals(1.0, segment.endTick)
                assertEquals(1.5, segment.accompanimentEndTick)
                assertEquals(if (name == "weighted") 750L else 687L, song.modelMediaPosition(audio, 1.0))
                assertEquals(if (name == "weighted") 1.0 else 1.1875, segment.tempoRatio)
            }
            if (name == "repeat") {
                assertEquals(0.0, song.modelJangdanTickAt(audio, 6000.0))
                assertEquals(1000L, song.modelMediaPosition(audio, 1.0), "Seek uses the first written occurrence")
                val resumed = JangdanCursor(assertNotNull(planJangdan(mapping, 60, 1)), resumeAfterTick = 2.0)
                assertTrue(resumed.due(song.modelJangdanTickAt(audio, 2000.0)).isEmpty())
                assertEquals(listOf(JangdanSample.Deok), resumed.due(song.modelJangdanTickAt(audio, 4000.0)).map { it.clip.sample })
            }
        } finally { client.close() }
    }

    private class MemoryAudio : SheetAudioDeviceStore {
        private var file: DeviceSheetAudio? = null
        override val available = true
        override suspend fun find(accountId: String, sheetId: String, scoreVersion: String) = file
        override suspend fun save(accountId: String, sheetId: String, scoreVersion: String, download: SheetAudioDownload) =
            DeviceSheetAudio(LocalAudioFile("/fixture/$sheetId.m4a"), download.instrumentId, download.bpm,
                download.withJangdan, download.sha256, download.durationMs, download.performance).also { file = it }
        override suspend fun delete(accountId: String, sheetId: String) = error("Unexpected deletion")
    }
}
