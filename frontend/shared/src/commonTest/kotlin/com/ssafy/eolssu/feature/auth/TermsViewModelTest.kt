package com.ssafy.eolssu.feature.auth

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.data.user.TermsRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TermsViewModelTest {
    @Test
    fun documentLookupCanRecoverAfterOutageAndClearPreviousOpenError() = runTest {
        var available = false
        val client = createHttpClient(MockEngine {
            if (!available) respond("{}", HttpStatusCode.ServiceUnavailable)
            else respond("""{"isSuccess":true,"result":{"items":[
                {"termId":"17","code":"SERVICE_TERMS","title":"이용약관","required":true,"version":"v2","contentUrl":"https://example.test/terms"}]}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        val vm = TermsViewModel("17", TermsRepository(client))
        vm.load()
        assertFalse(vm.uiState.value.loading)
        assertNull(vm.uiState.value.term)
        available = true
        vm.load()
        assertEquals("v2", vm.uiState.value.term?.version)
        vm.opened(false)
        assertTrue(vm.uiState.value.openFailed)
        vm.load()
        assertFalse(vm.uiState.value.openFailed)
        client.close()
    }
}
