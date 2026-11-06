package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ApiMySheetRepositoryTest {
    @Test fun paginatesAndOnlyRemovesAfterServerConfirms() = runTest {
        var deleteStatus = HttpStatusCode.ServiceUnavailable
        val pages = mutableListOf<String?>()
        val repository = ApiMySheetRepository(createHttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Delete) {
                assertEquals("/api/users/me/sheets/31", request.url.encodedPath)
                respond("", deleteStatus)
            } else {
                pages += request.url.parameters["page"]
                val id = if (pages.size == 1) "31" else "32"
                respond("""{"isSuccess":true,"code":200,"result":{"items":[{"sheetId":"$id","kind":"JEONGGANBO","title":"내 악보 $id","status":"EVALUABLE","source":"IMPORTED_JEONGGANBO","updatedAt":"2026-11-05T16:00:00Z"}],"page":${pages.size-1},"totalPages":2}}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType,"application/json"))
            }
        }, ServerAddress.Production))
        assertTrue(repository.refresh())
        assertEquals(listOf<String?>("0", "1"), pages)
        assertEquals(listOf("31", "32"), repository.sheets.value.map { it.id })
        assertEquals(6, repository.sheets.value.first().updatedAt.day)
        assertFails { repository.delete("31") }
        assertEquals(2, repository.sheets.value.size)
        deleteStatus = HttpStatusCode.NoContent
        repository.delete("31")
        assertEquals(listOf("32"), repository.sheets.value.map { it.id })
    }
}
