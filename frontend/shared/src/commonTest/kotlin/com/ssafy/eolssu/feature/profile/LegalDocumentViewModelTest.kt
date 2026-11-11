package com.ssafy.eolssu.feature.profile

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.data.user.PublishedLegalDocument
import com.ssafy.eolssu.data.user.TermsBlock
import com.ssafy.eolssu.data.user.TermsRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** 마이페이지 법적 문서를 브라우저 대신 앱 안에서 보여 준다(S15P31E102-731). */
class LegalDocumentViewModelTest {
    private val document = """<!doctype html><html><head><title>개인정보 처리방침</title></head>
        <body><main><h1>개인정보 처리방침</h1><p>수집 항목을 안내합니다.</p><ul><li>이메일</li></ul></main></body></html>"""

    @Test
    fun opensPublishedDocumentInsideAppAsTextBlocks() = runTest {
        val requested = mutableListOf<String>()
        val client = createHttpClient(MockEngine { request ->
            requested += request.url.toString()
            if (request.headers.getAll(HttpHeaders.Accept).orEmpty().none { "text/html" in it }) {
                respond("", HttpStatusCode.NotAcceptable)
            } else {
                respond(document, headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"))
            }
        }, ServerAddress.Production)
        val vm = LegalDocumentViewModel(PublishedLegalDocument.Privacy, TermsRepository(client))
        assertTrue(vm.uiState.value.loading)
        vm.load()
        assertFalse(vm.uiState.value.loading)
        assertEquals(
            listOf(TermsBlock.Heading(1, "개인정보 처리방침"), TermsBlock.Paragraph("수집 항목을 안내합니다."), TermsBlock.Bullet("이메일")),
            vm.uiState.value.body,
        )
        assertEquals(listOf(PublishedLegalDocument.Privacy.url), requested)
        client.close()
    }

    @Test
    fun unavailableDocumentShowsFailureThenRecoversOnRetry() = runTest {
        var up = false
        val client = createHttpClient(MockEngine {
            if (up) respond(document, headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"))
            else respond("공개 준비 중", HttpStatusCode.ServiceUnavailable)
        }, ServerAddress.Production)
        val vm = LegalDocumentViewModel(PublishedLegalDocument.Service, TermsRepository(client))
        vm.load()
        assertFalse(vm.uiState.value.loading)
        assertNull(vm.uiState.value.body)
        up = true
        vm.load()
        assertEquals(TermsBlock.Heading(1, "개인정보 처리방침"), vm.uiState.value.body?.first())
        client.close()
    }
}
