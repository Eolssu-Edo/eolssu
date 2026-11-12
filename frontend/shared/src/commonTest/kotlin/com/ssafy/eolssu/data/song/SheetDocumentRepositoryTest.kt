package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class SheetDocumentRepositoryTest {
    @Test fun keepsServerPitchLocationsAndDoesNotLoseOtherInvalidRequestReasons() = runTest {
        val copy = SheetWorkingCopy("검사", null, 60, smallScoreData(), "1", 0)
        var error = """{"errorCode":"INVALID_REQUEST","details":{"reason":"UNSUPPORTED_PITCHES","instrumentCode":"SOGEUM","issues":[{"eventId":"c0-j0-r0-u0","cellId":"c0-j0","yul":"이","octave":1,"reasonCodes":["FINGERING_UNKNOWN"]}]}}"""
        val client = createHttpClient(MockEngine { respond("""{"isSuccess":false,"code":400,"result":$error}""",
            HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json")) }, ServerAddress.Production)
        try {
            val repository = ApiSheetDocumentRepository(client)
            val rejected = assertIs<SheetWrite.UnsupportedPitches>(repository.save("31", copy, false))
            assertEquals("c0-j0", rejected.details.issues.single().cellId)
            assertEquals(listOf("FINGERING_UNKNOWN"), rejected.details.issues.single().reasonCodes)
            error = """{"errorCode":"UNFILLED_CELLS","details":{"cellIds":["c1-j0"]}}"""
            assertEquals(SheetWrite.Unfilled, repository.save("31", copy, false))
        } finally { client.close() }
    }
    @Test fun largeSourceSnapshotIsNotEchoedInAutosaveBody() = runTest {
        var bytes = 0
        val repository = ApiSheetDocumentRepository(createHttpClient(MockEngine { request ->
            val text = (request.body as io.ktor.http.content.TextContent).text
            bytes = text.encodeToByteArray().size
            val source = Json.parseToJsonElement(text).jsonObject.getValue("authoring").jsonObject.getValue("source").jsonObject
            assertEquals(setOf("scoreSha256"), source.keys)
            respond("""{"isSuccess":true,"code":200,"result":{"sheetId":"31","draftRevision":1}}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production))
        val source = buildJsonObject { put("schema", "sheet-authoring/v1"); put("source", buildJsonObject {
            put("scoreSha256", "0".repeat(64)); put("score", "x".repeat(600_000))
        }) }
        assertEquals(SheetWrite.Saved("1", 1), repository.save("31", SheetWorkingCopy("x", null, 60,
            smallScoreData(), "1", 0, authoring = source), true))
        assertTrue(bytes < 10_000)
    }

    @Test fun carriesNumericExpressionsThroughReadDraftAndSave() = runTest {
        val score = Json.encodeToJsonElement(smallScoreData())
        val expressions = Json.parseToJsonElement("""{"schema":"common-score-expressions/v1","sourceSha256":"bound","placements":[],"performancePlan":{"tempos":[{"gak":1,"cell":2,"bpm":80}]}}""").jsonObject
        var sent: JsonObject? = null
        val repository = ApiSheetDocumentRepository(createHttpClient(MockEngine { request ->
            val result = if (request.method == HttpMethod.Get) {
                """{"sheetId":"31","title":"곡","bpm":100,"scoreVersion":"1","reviewStatus":"EVALUABLE","scoreData":$score,"expressions":$expressions}"""
            } else {
                sent = Json.parseToJsonElement((request.body as io.ktor.http.content.TextContent).text).jsonObject
                """{"sheetId":"31","scoreVersion":"2","expressions":$expressions}"""
            }
            respond("""{"isSuccess":true,"code":200,"result":$result}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType,"application/json"))
        }, ServerAddress.Production))
        val document = assertNotNull(repository.get("31"))
        assertEquals(expressions, document.expressions)
        val copy = SheetWorkingCopy(document.title, null, 120, assertNotNull(document.score), "1", 0, expressions = document.expressions)
        val saved = assertIs<SheetWrite.Saved>(repository.save("31", copy, false))
        assertEquals(expressions, sent?.get("expressions"))
        assertEquals(expressions, saved.expressions)
    }
    @Test fun keepsWholeScoreNoticesSeparateFromCellIssues() = runTest {
        val score = Json.encodeToJsonElement(smallScoreData())
        val repository = ApiSheetDocumentRepository(createHttpClient(MockEngine {
            respond(
                """{"isSuccess":true,"code":200,"result":{"sheetId":"31","title":"악보","bpm":60,"scoreVersion":"1","reviewStatus":"NEEDS_REVIEW","scoreData":$score,"issues":[{"issueId":"i1","cellId":"c0-j0","type":"OUT_OF_RANGE"}],"notices":[{"type":"UNREADABLE","pageIdx":0,"detail":{"reason":"NO_TEXT"}}]}}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }, ServerAddress.Production))
        val document = assertNotNull(repository.get("31"))
        assertEquals(JsonPrimitive("i1"), document.issues.single()["issueId"])
        assertEquals(JsonPrimitive(0), document.notices.single()["pageIdx"])
    }

    @Test fun restoresDraftAndSendsVersionedWritesWithoutReportingConflictAsSuccess() = runTest {
        val score = Json.encodeToJsonElement(smallScoreData())
        val sent = mutableListOf<JsonObject>()
        var conflict = false
        val repository = ApiSheetDocumentRepository(createHttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val result = if (request.method == HttpMethod.Get) {
                """{"sheetId":"31","title":"원본","bpm":60,"scoreVersion":"7","reviewStatus":"EVALUABLE","scoreData":$score,"draft":{"title":"편집 중","bpm":55,"score":$score,"baseScoreVersion":"7","draftRevision":3}}"""
            } else {
                sent += Json.parseToJsonElement((request.body as io.ktor.http.content.TextContent).text).jsonObject
                if (conflict) return@MockEngine respond("""{"isSuccess":false,"code":409,"result":{"errorCode":"STATE_CONFLICT"}}""", HttpStatusCode.Conflict, headersOf(HttpHeaders.ContentType,"application/json"))
                if (path.endsWith("/draft")) """{"sheetId":"31","draftRevision":4}""" else """{"sheetId":"31","scoreVersion":"8","reviewStatus":"NEEDS_REVIEW","pendingIssueCount":1}"""
            }
            respond("""{"isSuccess":true,"code":200,"result":$result}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType,"application/json"))
        }, ServerAddress.Production))
        val document = assertNotNull(repository.get("31"))
        assertEquals("편집 중", document.draft?.title)
        assertEquals(emptyList(), document.notices)
        val copy = assertNotNull(document.draft).copy(resolvedIssueIds = listOf("i1"))
        assertEquals(SheetWrite.Saved("7", 4), repository.save("31", copy, draft = true))
        assertEquals(JsonPrimitive(3), sent.last()["draftRevision"])
        assertEquals(JsonPrimitive("7"), sent.last()["baseScoreVersion"])
        assertTrue(sent.last()["resolvedIssueIds"] == null || sent.last()["resolvedIssueIds"] == JsonNull)
        assertEquals(SheetWrite.Saved("8", reviewStatus = "NEEDS_REVIEW"), repository.save("31", copy, draft = false))
        assertTrue(sent.last()["draftRevision"] == null || sent.last()["draftRevision"] == JsonNull)
        assertEquals(JsonArray(listOf(JsonPrimitive("i1"))), sent.last()["resolvedIssueIds"])
        conflict = true
        assertEquals(SheetWrite.Conflict, repository.save("31", copy, draft = false))
    }
}
