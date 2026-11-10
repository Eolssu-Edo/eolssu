package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.encodedPath
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PublishedSheetCopyRepositoryTest {
    @Test
    fun sendsCopyWithTheSelectionKeyAndReadsTheNewId() = runTest {
        var status = HttpStatusCode.Created
        var body = """{"isSuccess":true,"code":201,"result":{"sheetId":"17"}}"""
        val client = createHttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/users/me/sheets/copies", request.url.encodedPath)
            assertEquals("copy-key", request.headers["Idempotency-Key"])
            val payload = request.body as OutgoingContent.ByteArrayContent
            assertEquals("""{"songId":"7"}""", payload.bytes().decodeToString())
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val repository = ApiPublishedSheetCopyRepository(client)
            assertEquals(PublishedSheetCopyResult.Created("17"), repository.copy("7", "copy-key"))
            status = HttpStatusCode.Conflict
            body = """{"isSuccess":false,"code":409,"result":{"errorCode":"SHEET_LIMIT_EXCEEDED"}}"""
            assertEquals(PublishedSheetCopyResult.Rejected(PublishedSheetCopyProblem.Limit), repository.copy("7", "copy-key"))
        } finally {
            client.close()
        }
    }
}
