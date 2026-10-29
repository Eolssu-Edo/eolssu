package com.ssafy.eolssu.backend.feature.analysis.infrastructure.ai

import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisFailReason
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisRequest
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisResponseException
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisResult
import com.ssafy.eolssu.backend.feature.analysis.domain.NoteJudgement
import com.ssafy.eolssu.backend.feature.analysis.domain.NoteVerdict
import com.ssafy.eolssu.backend.feature.analysis.domain.ScoreNote
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
    fun `reads a DONE result`() {
        respond(doneBody)

        assertEquals(
            AnalysisResult.Done(
                "pyin-1", 87, 90, 85, 86,
                listOf(NoteJudgement(4, NoteVerdict.CORRECT, 3.1, 20, -15), NoteJudgement(5, NoteVerdict.MISSED, null, null, null)),
            ),
            client.analyze(request),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["NO_SOUND", "TOO_SHORT", "DECODE_ERROR"])
    fun `reads an AI failure reason`(reason: String) {
        respond("""{"status":"FAILED","failReason":"$reason"}""")

        assertEquals(AnalysisResult.Failed(AnalysisFailReason.valueOf(reason)), client.analyze(request))
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
