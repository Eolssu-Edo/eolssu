package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class SheetDocumentRepositoryTest {
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
                if (path.endsWith("/draft")) """{"sheetId":"31","draftRevision":4}""" else """{"sheetId":"31","scoreVersion":"8"}"""
            }
            respond("""{"isSuccess":true,"code":200,"result":$result}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType,"application/json"))
        }, ServerAddress.Production))
        val document = assertNotNull(repository.get("31"))
        assertEquals("편집 중", document.draft?.title)
        val copy = assertNotNull(document.draft)
        assertEquals(SheetWrite.Saved("7", 4), repository.save("31", copy, draft = true))
        assertEquals(JsonPrimitive(3), sent.last()["draftRevision"])
        assertEquals(JsonPrimitive("7"), sent.last()["baseScoreVersion"])
        assertEquals(SheetWrite.Saved("8"), repository.save("31", copy, draft = false))
        assertTrue(sent.last()["draftRevision"] == null || sent.last()["draftRevision"] == JsonNull)
        conflict = true
        assertEquals(SheetWrite.Conflict, repository.save("31", copy, draft = false))
    }
}
