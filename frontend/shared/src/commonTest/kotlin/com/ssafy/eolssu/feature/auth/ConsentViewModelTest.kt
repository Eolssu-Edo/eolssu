package com.ssafy.eolssu.feature.auth

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.data.user.*
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ConsentViewModelTest {
    @Test
    fun optionalOnlyCatalogCannotPermitSignup() = runTest {
        val client = createHttpClient(MockEngine {
            respond("""{"isSuccess":true,"result":{"items":[
                {"termId":"19","code":"MICROPHONE_NOTICE","title":"접근권한 안내","required":false,"version":"v1","contentUrl":"https://example.test/permissions"}]}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        val draft = AccountDraft()
        val vm = ConsentViewModel(TermsRepository(client), draft)
        vm.load()
        vm.toggleAll()
        assertFalse(vm.accept())
        assertTrue(vm.uiState.value.failed)
        assertTrue(draft.agreements.isEmpty())
        client.close()
    }

    @Test
    fun retryAfterCatalogRecoveryRequiresFreshConsentAndUsesNewVersion() = runTest {
        var attempt = 0
        val client = createHttpClient(MockEngine {
            val items = if (attempt++ == 0) "[]" else """[{"termId":"17","code":"SERVICE_TERMS","title":"약관","required":true,"version":"v$attempt","contentUrl":"https://example.test/terms"}]"""
            respond("""{"isSuccess":true,"result":{"items":$items}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        val draft = AccountDraft()
        val vm = ConsentViewModel(TermsRepository(client), draft)
        vm.load()
        assertTrue(vm.uiState.value.failed)
        vm.load(force = true)
        assertFalse(vm.uiState.value.failed)
        assertFalse(vm.accept())
        vm.toggle("17")
        assertTrue(vm.accept())
        assertEquals("v2", draft.agreements.single().version)
        vm.load(force = true)
        assertTrue(draft.agreements.isEmpty())
        assertTrue(vm.uiState.value.checked.isEmpty())
        assertFalse(vm.accept())
        vm.toggle("17")
        assertTrue(vm.accept())
        assertEquals("v3", draft.agreements.single().version)
        client.close()
    }

    @Test
    fun consentStartsUncheckedAndKeepsSelectedServerVersions() = runTest {
        val client = createHttpClient(MockEngine {
            respond("""{"isSuccess":true,"result":{"items":[
                {"termId":"17","code":"SERVICE_TERMS","title":"약관","required":true,"version":"v3","contentUrl":"https://example.test/terms"},
                {"termId":"19","code":"NEWS","title":"소식","required":false,"version":"v2","contentUrl":"https://example.test/news"}]}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        val draft = AccountDraft()
        val vm = ConsentViewModel(TermsRepository(client), draft)
        assertFalse(vm.accept())
        vm.load()
        assertTrue(vm.uiState.value.checked.isEmpty())
        assertFalse(vm.accept())
        vm.toggle("17")
        assertTrue(vm.accept())
        assertEquals(listOf(TermAgreement("17", "v3", true), TermAgreement("19", "v2", false)), draft.agreements)
        vm.load() // Returning from the document preserves explicit selections.
        assertEquals(setOf("17"), vm.uiState.value.checked)
        vm.toggleAll()
        assertTrue(vm.uiState.value.allChecked)
        vm.toggleAll()
        assertFalse(vm.accept())
        vm.load(force = true)
        assertTrue(draft.agreements.isEmpty())
        client.close()
    }

    @Test
    fun emptyOrUnavailableTermsNeverPermitSignup() = runTest {
        for (empty in listOf(true, false)) {
            val client = createHttpClient(MockEngine {
                if (!empty) error("offline test")
                respond("""{"isSuccess":true,"result":{"items":[]}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }, ServerAddress.Production)
            val vm = ConsentViewModel(TermsRepository(client), AccountDraft())
            vm.load()
            vm.toggleAll()
            assertFalse(vm.accept())
            assertTrue(vm.uiState.value.failed)
            client.close()
        }
    }
}
