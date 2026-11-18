package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.song.InstrumentCatalog
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class InstrumentPreferenceTest {
    @Test fun memberPreferencesKeepBothDaegeumVariantsDistinctAndIgnoreLegacyLocalChoice() = runTest {
        val store = FakeKeyValueStore().apply { putString("practice_instrument", "Daegeum") }
        val client = createHttpClient(MockEngine {
            respond("""{"isSuccess":true,"result":{"items":[{"instrumentId":"91","code":"JEONGAK_DAEGEUM","name":"정악대금"},{"instrumentId":"92","code":"SANJO_DAEGEUM","name":"산조대금"}]}}""", headers = JSON)
        }, ServerAddress.Production)
        try {
            val repo = InstrumentRepository(store, InstrumentCatalog(client))
            assertNull(repo.instrument.value)
            for ((id,name) in listOf("91" to "DaegeumJeongak", "92" to "DaegeumSanjo")) {
                assertTrue(repo.applyServerId(id))
                val selected = assertNotNull(repo.instrument.value)
                assertEquals(name, selected.name)
                assertEquals(id, repo.serverIdFor(selected))
            }
            assertEquals(4, com.ssafy.eolssu.feature.auth.ProfileSetupUiState().instruments.size)
        } finally { client.close() }
    }

    @Test
    fun activeCatalogMapsCodeToRealIdAndUnknownIdClearsOldSelection() = runTest {
        val client = createHttpClient(MockEngine { respond(CATALOG, headers = JSON) }, ServerAddress.Production)
        try {
            val repo = InstrumentRepository(FakeKeyValueStore(), InstrumentCatalog(client))
            assertNull(repo.instrument.value)
            assertEquals("73", repo.serverIdFor(Instrument.Danso))
            assertNull(repo.serverIdFor(Instrument.DaegeumJeongak))
            assertTrue(repo.applyServerId("73"))
            assertEquals(Instrument.Danso, repo.instrument.value)
            assertFalse(repo.applyServerId("74"))
            assertNull(repo.instrument.value)
        } finally { client.close() }
    }

    @Test
    fun preferencesOnlySucceedForAcceptedMatchingId() = runTest {
        for ((status, id, expected) in listOf(
            Triple(HttpStatusCode.OK, "73", MemberResult.Done),
            Triple(HttpStatusCode.OK, "1", MemberResult.Unavailable),
            Triple(HttpStatusCode.UnprocessableEntity, "73", MemberResult.InvalidInput),
        )) {
            val client = createHttpClient(MockEngine { request ->
                assertEquals("/api/users/me/preferences", request.url.encodedPath)
                assertEquals("""{"instrumentId":"73"}""", (request.body as TextContent).text)
                respond("""{"isSuccess":true,"result":{"instrumentId":"$id","notationMode":"HANJA"}}""", status, JSON)
            }, ServerAddress.Production)
            try {
                val repo = ApiUserRepository(client, null)
                assertEquals(expected, repo.changeInstrument("73"))
                assertEquals(if (expected == MemberResult.Done) "73" else null, repo.profile.value.instrumentId)
            } finally { client.close() }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun logoutInvalidatesPendingInstrumentLookup() = runTest {
        val responseReady = CompletableDeferred<Unit>()
        val client = createHttpClient(MockEngine {
            responseReady.await()
            respond(CATALOG, headers = JSON)
        }, ServerAddress.Production)
        try {
            val repo = InstrumentRepository(FakeKeyValueStore(), InstrumentCatalog(client))
            val pending = async { repo.applyServerId("73") }
            runCurrent()
            repo.clear()
            responseReady.complete(Unit)
            assertFalse(pending.await())
            assertNull(repo.instrument.value)
        } finally { client.close() }
    }

    @Test
    fun signupSendsCatalogId() = runTest {
        val client = createHttpClient(MockEngine { request ->
            assertTrue((request.body as TextContent).text.contains("\"instrumentId\":\"73\""))
            respond("""{"isSuccess":true,"result":{"userId":"42"}}""", HttpStatusCode.Created, JSON)
        }, ServerAddress.Production)
        try {
            assertEquals(MemberResult.Done, ApiAuthRepository(client, null, com.ssafy.eolssu.core.network.SignedInMember(com.ssafy.eolssu.core.storage.FakeKeyValueStore()), this).signUp(
                Registration("member@example.test", "test-pass1!", "Member", "verified-test", emptyList(), "73"),
            ))
        } finally { client.close() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun delayedProfileCannotOverwriteAcceptedPreference() = runTest {
        val responseReady = CompletableDeferred<Unit>()
        val client = createHttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith("preferences")) {
                respond("""{"isSuccess":true,"result":{"instrumentId":"73","notationMode":"HANJA"}}""", headers = JSON)
            } else {
                responseReady.await()
                respond("""{"isSuccess":true,"result":{"nickname":"Member","instrumentId":"74","notationMode":"HANJA","linkedProviders":[]}}""", headers = JSON)
            }
        }, ServerAddress.Production)
        try {
            val repo = ApiUserRepository(client, null)
            val pending = async { repo.refresh() }
            runCurrent()
            assertEquals(MemberResult.Done, repo.changeInstrument("73"))
            responseReady.complete(Unit)
            assertEquals(MemberResult.Unavailable, pending.await())
            assertEquals("73", repo.profile.value.instrumentId)
        } finally { client.close() }
    }

    private companion object {
        val JSON = headersOf(HttpHeaders.ContentType, "application/json")
        const val CATALOG = """{"isSuccess":true,"result":{"items":[{"instrumentId":"73","code":"DANSO","name":"단소"},{"instrumentId":"74","code":"FUTURE_INSTRUMENT","name":"새 악기"}]}}"""
    }
}
