package com.ssafy.eolssu.backend.feature.analysis.infrastructure.ai

import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisFailReason
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisRequest
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisResponseException
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisResult
import com.ssafy.eolssu.backend.feature.analysis.domain.NoteJudgement
import com.ssafy.eolssu.backend.feature.analysis.domain.NoteScore
import com.ssafy.eolssu.backend.feature.analysis.domain.NoteVerdict
import com.ssafy.eolssu.backend.feature.analysis.domain.ScoreNote
import com.ssafy.eolssu.backend.feature.analysis.domain.ScoringRule
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonMapperBuilder

class HttpAnalysisClientTests {
    private val builder = RestClient.builder().baseUrl("http://ai.test")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = HttpAnalysisClient(builder.build(), jacksonMapperBuilder().build())
    private val request = AnalysisRequest(
        7, 2, "https://storage.example/a.m4a?sig=x", 60, "JEONGGAN",
        listOf(ScoreNote(4, 0, 4.0, 1.0, false, 11), ScoreNote(5, 2, 6.0, 2.5, true, 12)),
    )

    private fun respond(body: String) {
        server.expect(requestTo("http://ai.test/v1/analyses")).andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
    }

    private val doneBody = """
        {"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":90,"timing":85,"duration":86,
         "tuningOffsetCent":-12.5,"tuningOutOfRange":false,
         "notes":[{"idx":4,"verdict":"CORRECT","pitchErrorCent":3.1,"onsetErrorMs":20,"durationErrorMs":-15},
                  {"idx":5,"verdict":"MISSED","pitchErrorCent":null,"onsetErrorMs":null,"durationErrorMs":null}]}
    """

    @Test
    fun `posts the agreed request body without the pitch ids`() {
        server.expect(requestTo("http://ai.test/v1/analyses")).andExpect(method(HttpMethod.POST))
            .andExpect(
                content().json(
                    """
                    {"sessionId":7,"attempt":2,"audioUrl":"https://storage.example/a.m4a?sig=x","bpm":60,"bpmUnit":"JEONGGAN",
                     "notes":[{"idx":4,"semitone":0,"startBeat":4.0,"beats":1.0,"sigimsae":false},{"idx":5,"semitone":2,"startBeat":6.0,"beats":2.5,"sigimsae":true}]}
                    """,
                    true,
                ),
            )
            .andRespond(withSuccess(doneBody, MediaType.APPLICATION_JSON))

        client.analyze(request)
        server.verify()
    }

    @Test
    fun `sends the instrument code so the AI can correct low daegeum playing`() {
        server.expect(requestTo("http://ai.test/v1/analyses")).andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.instrument").value("JEONGAK_DAEGEUM"))
            .andRespond(withSuccess(doneBody, MediaType.APPLICATION_JSON))

        client.analyze(request.copy(instrument = "JEONGAK_DAEGEUM"))
        server.verify()
    }

    @Test
    fun `omits the instrument when the song instrument is unknown`() {
        server.expect(requestTo("http://ai.test/v1/analyses")).andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.instrument").doesNotExist())
            .andRespond(withSuccess(doneBody, MediaType.APPLICATION_JSON))

        client.analyze(request)
        server.verify()
    }

    @Test
    fun `reads a DONE result`() {
        respond(doneBody)

        assertEquals(
            AnalysisResult.Done(
                "pyin-1", 87, 90, 85, 86,
                listOf(NoteJudgement(4, NoteVerdict.CORRECT, 3.1, 20, -15), NoteJudgement(5, NoteVerdict.MISSED, null, null, null)),
                -12.5, false,
            ),
            client.analyze(request),
        )
    }

    @Test
    fun `reads the note scores and the scoring rule`() {
        respond("""{"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":90,"timing":85,"duration":86,"scoringVersion":"note-mean-1","scoreWeights":{"pitch":0.5,"timing":0.25,"duration":0.25},"notes":[{"idx":4,"verdict":"CORRECT","pitchScore":90.5,"timingScore":80,"durationScore":70,"weight":1},{"idx":5,"verdict":"MISSED","pitchScore":null,"timingScore":0,"durationScore":0,"weight":2.5}]}""")

        val done = client.analyze(request) as AnalysisResult.Done
        assertEquals(ScoringRule("note-mean-1", 0.5, 0.25, 0.25), done.scoring)
        assertEquals(NoteScore(90.5, 80.0, 70.0, 1.0), done.notes[0].score)
        assertEquals(NoteScore(null, 0.0, 0.0, 2.5), done.notes[1].score) // 요청의 5번 음은 시김새다
    }

    @Test
    fun `an AI response without note scores keeps them empty`() {
        respond(doneBody)

        val done = client.analyze(request) as AnalysisResult.Done
        assertEquals(null, done.scoring)
        assertEquals(listOf(null, null), done.notes.map { it.score })
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":90,"timing":85,"duration":86,"scoringVersion":"note-mean-1","notes":[{"idx":4,"verdict":"CORRECT","pitchScore":90.5,"timingScore":80,"durationScore":70,"weight":1},{"idx":5,"verdict":"MISSED","pitchScore":null,"timingScore":0,"durationScore":0,"weight":2.5}]}""",
            """{"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":90,"timing":85,"duration":86,"scoringVersion":"note-mean-1","scoreWeights":{"pitch":0.5,"timing":0.25,"duration":0.5},"notes":[{"idx":4,"verdict":"CORRECT","pitchScore":90.5,"timingScore":80,"durationScore":70,"weight":1},{"idx":5,"verdict":"MISSED","pitchScore":null,"timingScore":0,"durationScore":0,"weight":2.5}]}""",
            """{"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":90,"timing":85,"duration":86,"scoringVersion":"note-mean-1","scoreWeights":{"pitch":1.5,"timing":-0.25,"duration":-0.25},"notes":[{"idx":4,"verdict":"CORRECT","pitchScore":90.5,"timingScore":80,"durationScore":70,"weight":1},{"idx":5,"verdict":"MISSED","pitchScore":null,"timingScore":0,"durationScore":0,"weight":2.5}]}""",
            """{"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":90,"timing":85,"duration":86,"scoringVersion":"","scoreWeights":{"pitch":0.5,"timing":0.25,"duration":0.25},"notes":[{"idx":4,"verdict":"CORRECT","pitchScore":90.5,"timingScore":80,"durationScore":70,"weight":1},{"idx":5,"verdict":"MISSED","pitchScore":null,"timingScore":0,"durationScore":0,"weight":2.5}]}""",
            """{"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":90,"timing":85,"duration":86,"scoringVersion":"nnnnnnnnnnnnnnnnnnnnnnnnnnnnnnn","scoreWeights":{"pitch":0.5,"timing":0.25,"duration":0.25},"notes":[{"idx":4,"verdict":"CORRECT","pitchScore":90.5,"timingScore":80,"durationScore":70,"weight":1},{"idx":5,"verdict":"MISSED","pitchScore":null,"timingScore":0,"durationScore":0,"weight":2.5}]}""",
            """{"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":90,"timing":85,"duration":86,"notes":[{"idx":4,"verdict":"CORRECT","pitchScore":90.5,"timingScore":80,"durationScore":70,"weight":1},{"idx":5,"verdict":"MISSED","pitchScore":null,"timingScore":0,"durationScore":0,"weight":2.5}]}""",
            """{"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":90,"timing":85,"duration":86,"scoringVersion":"note-mean-1","scoreWeights":{"pitch":0.5,"timing":0.25,"duration":0.25},"notes":[{"idx":4,"verdict":"CORRECT"},{"idx":5,"verdict":"MISSED","pitchScore":null,"timingScore":0,"durationScore":0,"weight":2.5}]}""",
            """{"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":90,"timing":85,"duration":86,"scoringVersion":"note-mean-1","scoreWeights":{"pitch":0.5,"timing":0.25,"duration":0.25},"notes":[{"idx":4,"verdict":"CORRECT","pitchScore":90,"durationScore":70,"weight":1},{"idx":5,"verdict":"MISSED","pitchScore":null,"timingScore":0,"durationScore":0,"weight":2.5}]}""",
            """{"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":90,"timing":85,"duration":86,"scoringVersion":"note-mean-1","scoreWeights":{"pitch":0.5,"timing":0.25,"duration":0.25},"notes":[{"idx":4,"verdict":"CORRECT","pitchScore":100.5,"timingScore":80,"durationScore":70,"weight":1},{"idx":5,"verdict":"MISSED","pitchScore":null,"timingScore":0,"durationScore":0,"weight":2.5}]}""",
            """{"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":90,"timing":85,"duration":86,"scoringVersion":"note-mean-1","scoreWeights":{"pitch":0.5,"timing":0.25,"duration":0.25},"notes":[{"idx":4,"verdict":"CORRECT","pitchScore":90,"timingScore":-1,"durationScore":70,"weight":1},{"idx":5,"verdict":"MISSED","pitchScore":null,"timingScore":0,"durationScore":0,"weight":2.5}]}""",
            """{"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":90,"timing":85,"duration":86,"scoringVersion":"note-mean-1","scoreWeights":{"pitch":0.5,"timing":0.25,"duration":0.25},"notes":[{"idx":4,"verdict":"CORRECT","pitchScore":90,"timingScore":80,"durationScore":101,"weight":1},{"idx":5,"verdict":"MISSED","pitchScore":null,"timingScore":0,"durationScore":0,"weight":2.5}]}""",
            """{"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":90,"timing":85,"duration":86,"scoringVersion":"note-mean-1","scoreWeights":{"pitch":0.5,"timing":0.25,"duration":0.25},"notes":[{"idx":4,"verdict":"CORRECT","pitchScore":90,"timingScore":80,"durationScore":70,"weight":0},{"idx":5,"verdict":"MISSED","pitchScore":null,"timingScore":0,"durationScore":0,"weight":2.5}]}""",
            """{"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":90,"timing":85,"duration":86,"scoringVersion":"note-mean-1","scoreWeights":{"pitch":0.5,"timing":0.25,"duration":0.25},"notes":[{"idx":4,"verdict":"CORRECT","pitchScore":null,"timingScore":80,"durationScore":70,"weight":1},{"idx":5,"verdict":"MISSED","pitchScore":null,"timingScore":0,"durationScore":0,"weight":2.5}]}""",
            """{"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":90,"timing":85,"duration":86,"scoringVersion":"note-mean-1","scoreWeights":{"pitch":0.5,"timing":0.25,"duration":0.25},"notes":[{"idx":4,"verdict":"CORRECT","pitchScore":90.5,"timingScore":80,"durationScore":70,"weight":1},{"idx":5,"verdict":"MISSED","pitchScore":0,"timingScore":0,"durationScore":0,"weight":1}]}""",
        ],
    )
    fun `rejects note scores that break the contract`(body: String) {
        respond(body)

        assertFailsWith<AnalysisResponseException> { client.analyze(request) }
    }

    @Test
    fun `reads the detected semitone and recording offset of each heard note`() {
        respond(
            doneBody.replace("\"durationErrorMs\":-15}", "\"durationErrorMs\":-15,\"detectedSemitone\":12,\"recordingOffsetMs\":1520}")
                .replace("\"durationErrorMs\":null}", "\"durationErrorMs\":null,\"detectedSemitone\":null,\"recordingOffsetMs\":null}"),
        )

        val notes = (client.analyze(request) as AnalysisResult.Done).notes
        assertEquals(NoteJudgement(4, NoteVerdict.CORRECT, 3.1, 20, -15, 12, 1520), notes[0])
        assertEquals(NoteJudgement(5, NoteVerdict.MISSED, null, null, null, null, null), notes[1])
    }

    @Test
    fun `does not keep a detected note or offset for a missed note`() {
        respond(doneBody.replace("\"durationErrorMs\":null}", "\"durationErrorMs\":null,\"detectedSemitone\":12,\"recordingOffsetMs\":3000}"))

        val missed = (client.analyze(request) as AnalysisResult.Done).notes[1]
        assertEquals(null, missed.detectedSemitone)
        assertEquals(null, missed.recordingOffsetMs)
    }

    @Test
    fun `rejects a negative recording offset`() {
        respond(doneBody.replace("\"durationErrorMs\":-15}", "\"durationErrorMs\":-15,\"recordingOffsetMs\":-1}"))

        assertFailsWith<AnalysisResponseException> { client.analyze(request) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["NO_SOUND", "TOO_SHORT", "DECODE_ERROR"])
    fun `reads an AI failure reason`(reason: String) {
        respond("""{"status":"FAILED","failReason":"$reason"}""")

        assertEquals(AnalysisResult.Failed(AnalysisFailReason.valueOf(reason)), client.analyze(request))
    }

    @Test
    fun `preserves the raw tuning estimate and out of range flag`() {
        respond(doneBody.replace("-12.5", "137.25").replace("\"tuningOutOfRange\":false", "\"tuningOutOfRange\":true"))

        val mapper = jacksonMapperBuilder().build()
        val result = mapper.readTree(mapper.writeValueAsString(client.analyze(request)))
        assertEquals(137.25, result.path("tuningOffsetCent").asDouble())
        assertEquals(true, result.path("tuningOutOfRange").asBoolean())
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "\"tuningOffsetCent\":null,\"tuningOutOfRange\":null,"])
    fun `keeps unavailable tuning metadata null`(metadata: String) {
        respond(doneBody.replace("\"tuningOffsetCent\":-12.5,\"tuningOutOfRange\":false,", metadata))
        val result = client.analyze(request) as AnalysisResult.Done
        assertEquals(null, result.tuningOffsetCent)
        assertEquals(null, result.tuningOutOfRange)
        assertEquals(87, result.total)
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "\"tuningOffsetCent\":-12.5,",
        "\"tuningOutOfRange\":false,",
        "\"tuningOffsetCent\":1e999,\"tuningOutOfRange\":true,",
    ])
    fun `rejects partial or non finite tuning metadata`(metadata: String) {
        respond(doneBody.replace("\"tuningOffsetCent\":-12.5,\"tuningOutOfRange\":false,", metadata))
        assertFailsWith<AnalysisResponseException> { client.analyze(request) }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"status":"FAILED","failReason":"TIMEOUT"}""",
            """{"status":"FAILED"}""",
            """{"status":"RUNNING"}""",
            """{"status":"DONE","modelVersion":"pyin-1","total":101,"pitch":90,"timing":85,"duration":86,"notes":[{"idx":4,"verdict":"CORRECT"},{"idx":5,"verdict":"MISSED"}]}""",
            """{"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":-1,"timing":85,"duration":86,"notes":[{"idx":4,"verdict":"CORRECT"},{"idx":5,"verdict":"MISSED"}]}""",
            """{"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":90,"timing":101,"duration":86,"notes":[{"idx":4,"verdict":"CORRECT"},{"idx":5,"verdict":"MISSED"}]}""",
            """{"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":90,"timing":85,"notes":[{"idx":4,"verdict":"CORRECT"},{"idx":5,"verdict":"MISSED"}]}""",
            """{"status":"DONE","modelVersion":"","total":87,"pitch":90,"timing":85,"duration":86,"notes":[{"idx":4,"verdict":"CORRECT"},{"idx":5,"verdict":"MISSED"}]}""",
            """{"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":90,"timing":85,"duration":86,"notes":[{"idx":4,"verdict":"CORRECT"}]}""",
            """{"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":90,"timing":85,"duration":86,"notes":[{"idx":4,"verdict":"CORRECT"},{"idx":6,"verdict":"MISSED"}]}""",
            """{"status":"DONE","modelVersion":"pyin-1","total":87,"pitch":90,"timing":85,"duration":86,"notes":[{"idx":4,"verdict":"GOOD"},{"idx":5,"verdict":"MISSED"}]}""",
            """not json""",
        ],
    )
    fun `rejects a response that breaks the contract`(body: String) {
        respond(body)

        assertFailsWith<AnalysisResponseException> { client.analyze(request) }
    }

    @ParameterizedTest
    @ValueSource(ints = [422, 500])
    fun `reports an AI error status without its body which may echo the read url`(status: Int) {
        server.expect(requestTo("http://ai.test/v1/analyses"))
            .andRespond(withStatus(HttpStatus.valueOf(status)).body("""{"detail":"bad","audioUrl":"https://storage.example/a.m4a?sig=x"}"""))

        val failure = assertFailsWith<AnalysisResponseException> { client.analyze(request) }
        assertEquals("AI responded $status", failure.message)
    }

    @Test
    fun `rejects a model version longer than the stored column`() {
        respond(doneBody.replace("pyin-1", "m".repeat(31)))

        assertFailsWith<AnalysisResponseException> { client.analyze(request) }
    }
}
