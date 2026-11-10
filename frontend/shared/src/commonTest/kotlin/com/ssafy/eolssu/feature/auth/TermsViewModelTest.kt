package com.ssafy.eolssu.feature.auth

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.data.user.TermsBlock
import com.ssafy.eolssu.data.user.TermsRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TermsViewModelTest {
    private val catalog = """{"isSuccess":true,"result":{"items":[
        {"termId":"17","code":"SERVICE_TERMS","title":"이용약관","required":true,"version":"v2","contentUrl":"https://example.test/terms"}]}}"""
    private val document = """<!doctype html><html><head><title>얼쑤 이용약관</title><style>body{}</style></head>
        <body><main><h1>얼쑤 이용약관</h1><p>이용 조건을 정합니다.</p><h2>1. 회원가입</h2><ul><li>필수 약관 동의</li></ul></main></body></html>"""

    @Test
    fun openingTermsLoadsBodyImmediatelyWithoutVersionOrExtraStep() = runTest {
        val requested = mutableListOf<String>()
        val client = createHttpClient(MockEngine { request ->
            requested += request.url.toString()
            if (request.url.encodedPath == "/api/terms") {
                respond(catalog, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            } else if (request.headers.getAll(HttpHeaders.Accept).orEmpty().none { "text/html" in it }) {
                respond("", HttpStatusCode.NotAcceptable) // The server's document endpoint produces text/html only.
            } else {
                respond(document, headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"))
            }
        }, ServerAddress.Production)
        val vm = TermsViewModel("17", TermsRepository(client))
        vm.load()
        val state = vm.uiState.value
        assertFalse(state.loading)
        assertEquals("이용약관", state.term?.title)
        assertEquals(
            listOf(
                TermsBlock.Heading(1, "얼쑤 이용약관"),
                TermsBlock.Paragraph("이용 조건을 정합니다."),
                TermsBlock.Heading(2, "1. 회원가입"),
                TermsBlock.Bullet("필수 약관 동의"),
            ),
            state.body,
        )
        assertTrue(state.body.orEmpty().none { "v2" in it.text })
        assertEquals("https://example.test/terms", requested.last())
        client.close()
    }

    @Test
    fun unavailableCatalogOrDocumentCanRecoverOnRetry() = runTest {
        var catalogUp = false
        var documentUp = false
        val client = createHttpClient(MockEngine { request ->
            when {
                request.url.encodedPath == "/api/terms" && !catalogUp -> respond("{}", HttpStatusCode.ServiceUnavailable)
                request.url.encodedPath == "/api/terms" -> respond(catalog, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                !documentUp -> respond("약관 공개 준비 중입니다.", HttpStatusCode.ServiceUnavailable)
                else -> respond(document, headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"))
            }
        }, ServerAddress.Production)
        val vm = TermsViewModel("17", TermsRepository(client))
        vm.load()
        assertFalse(vm.uiState.value.loading)
        assertNull(vm.uiState.value.term)
        assertNull(vm.uiState.value.body)
        catalogUp = true
        vm.load()
        assertNotNull(vm.uiState.value.term)
        assertNull(vm.uiState.value.body)
        assertFalse(vm.uiState.value.loading)
        documentUp = true
        vm.load()
        assertEquals(TermsBlock.Heading(1, "얼쑤 이용약관"), vm.uiState.value.body?.first())
        client.close()
    }
}
