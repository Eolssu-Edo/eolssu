package com.ssafy.eolssu.feature.profile

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.song.InstrumentCatalog
import com.ssafy.eolssu.data.user.*
import com.ssafy.eolssu.testing.CatalogViewModelTest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** viewModelScope는 iOS에서 실제 메인 큐를 쓰므로, runTest가 메인 스레드를 잡고 있으면 결과를 받지 못해 멈춘다. 테스트 디스패처로 바꿔 둔다. */
internal class MyPageInstrumentTest : CatalogViewModelTest() {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun savesServerIdThenUpdatesSharedInstrumentAndIgnoresRepeatedTap() = runTest {
        val gate = CompletableDeferred<Unit>()
        var patches = 0
        val client = createHttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/instruments" -> respond(CATALOG, headers = JSON)
                "/api/users/me" -> respond(PROFILE, headers = JSON)
                "/api/users/me/preferences" -> {
                    patches++
                    assertEquals(HttpMethod.Patch, request.method)
                    assertEquals("""{"instrumentId":"92"}""", (request.body as TextContent).text)
                    gate.await()
                    respond("""{"isSuccess":true,"result":{"instrumentId":"92","notationMode":"HANJA"}}""", headers = JSON)
                }
                else -> error("Unexpected test request")
            }
        }, ServerAddress.Production)
        try {
            val store = FakeKeyValueStore()
            val instruments = InstrumentRepository(store, InstrumentCatalog(client))
            val users = ApiUserRepository(client, null, instruments)
            val model = keep(MyPageViewModel(users, instruments,
                PracticeTimeQueue(store, null, SignedInMember(store), backgroundScope), SampleAuthRepository { 0L }))
            assertEquals(MemberResult.Done, model.refresh())
            assertEquals(Instrument.Danso, model.instrument.value)
            val save = async { model.selectInstrument(Instrument.DaegeumSanjo) }
            runCurrent()
            assertTrue(model.savingInstrument.value)
            assertEquals(Instrument.Danso, model.instrument.value)
            assertNull(model.selectInstrument(Instrument.Sogeum))
            gate.complete(Unit)
            assertEquals(MemberResult.Done, save.await())
            assertEquals(Instrument.DaegeumSanjo, model.instrument.value)
            assertEquals("92", users.profile.value.instrumentId)
            assertFalse(model.savingInstrument.value)
            assertNull(model.selectInstrument(Instrument.DaegeumSanjo))
            assertEquals(1, patches)
        } finally { client.close() }
    }

    @Test fun failedSavePreservesFailureKindAndNeverAppliesUnacceptedChoice() = runTest {
        for ((status, expected) in listOf(
            HttpStatusCode.Unauthorized to MemberResult.Unauthenticated,
            HttpStatusCode.TooManyRequests to MemberResult.RateLimited,
            HttpStatusCode.ServiceUnavailable to MemberResult.Unavailable,
        )) {
            val client = createHttpClient(MockEngine { request ->
                when (request.url.encodedPath) {
                    "/api/instruments" -> respond(CATALOG, headers = JSON)
                    "/api/users/me" -> respond(PROFILE, headers = JSON)
                    else -> respond("""{"isSuccess":false}""", status, JSON)
                }
            }, ServerAddress.Production)
            try {
                val store = FakeKeyValueStore()
                val instruments = InstrumentRepository(store, InstrumentCatalog(client))
                val users = ApiUserRepository(client, null, instruments)
                val model = keep(MyPageViewModel(users, instruments,
                    PracticeTimeQueue(store, null, SignedInMember(store), backgroundScope), SampleAuthRepository { 0L }))
                assertEquals(MemberResult.Done, model.refresh())
                assertEquals(expected, model.selectInstrument(Instrument.DaegeumSanjo))
                assertEquals(if (expected == MemberResult.Unauthenticated) null else Instrument.Danso, model.instrument.value)
                assertFalse(model.savingInstrument.value)
            } finally { client.close() }
        }
    }

    @Test fun expiredProfileClearsInstrumentInsteadOfLeavingEditableOldAccount() = runTest {
        val client = createHttpClient(MockEngine {
            respond("""{"isSuccess":false}""", HttpStatusCode.Unauthorized, JSON)
        }, ServerAddress.Production)
        try {
            val store = FakeKeyValueStore()
            val instruments = InstrumentRepository(store, InstrumentCatalog(client)).apply { select(Instrument.Danso) }
            val users = ApiUserRepository(client, null, instruments)
            val model = keep(MyPageViewModel(users, instruments,
                PracticeTimeQueue(store, null, SignedInMember(store), backgroundScope), SampleAuthRepository { 0L }))
            assertEquals(MemberResult.Unauthenticated, model.refresh())
            assertNull(instruments.instrument.value)
        } finally { client.close() }
    }

    private companion object {
        val JSON = headersOf(HttpHeaders.ContentType, "application/json")
        const val CATALOG = """{"isSuccess":true,"result":{"items":[{"instrumentId":"73","code":"DANSO","name":"단소"},{"instrumentId":"92","code":"SANJO_DAEGEUM","name":"산조대금"}]}}"""
        const val PROFILE = """{"isSuccess":true,"result":{"nickname":"격리테스트","instrumentId":"73","notationMode":"HANJA","linkedProviders":[]}}"""
    }
}
