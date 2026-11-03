package com.ssafy.eolssu.feature.home

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.song.InstrumentCatalog
import com.ssafy.eolssu.data.user.ApiUserRepository
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.MemberResult
import com.ssafy.eolssu.data.user.SampleUserRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** HOME-01은 현재 악기를 보여 주고, 상단 드롭다운의 선택을 마이페이지와 같은 절차로 서버에 반영한다. */
class HomeViewModelTest {
    @Test
    fun startsWithDanso() {
        assertEquals(Instrument.Danso, HomeViewModel(InstrumentRepository(FakeKeyValueStore()), SampleUserRepository()).instrument.value)
    }

    @Test
    fun showsInstrumentChosenElsewhere() {
        val repository = InstrumentRepository(FakeKeyValueStore())
        val viewModel = HomeViewModel(repository, SampleUserRepository())

        repository.select(Instrument.Sogeum)

        assertEquals(Instrument.Sogeum, viewModel.instrument.value)
    }

    @Test
    fun dropdownChoiceSavesServerIdThenUpdatesSharedInstrument() = runTest {
        var patches = 0
        val client = createHttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/instruments" -> respond(CATALOG, headers = JSON)
                "/api/users/me" -> respond(PROFILE, headers = JSON)
                "/api/users/me/preferences" -> {
                    patches++
                    assertEquals(HttpMethod.Patch, request.method)
                    assertEquals("""{"instrumentId":"92"}""", (request.body as TextContent).text)
                    respond("""{"isSuccess":true,"result":{"instrumentId":"92","notationMode":"HANJA"}}""", headers = JSON)
                }
                else -> error("Unexpected test request")
            }
        }, ServerAddress.Production)
        try {
            val instruments = InstrumentRepository(FakeKeyValueStore(), InstrumentCatalog(client))
            val users = ApiUserRepository(client, null, instruments)
            assertEquals(MemberResult.Done, users.refresh())
            val model = HomeViewModel(instruments, users)
            assertEquals(Instrument.Danso, model.instrument.value)

            assertEquals(MemberResult.Done, model.selectInstrument(Instrument.DaegeumSanjo))

            assertEquals(Instrument.DaegeumSanjo, model.instrument.value)
            assertEquals("92", users.profile.value.instrumentId)
            assertFalse(model.savingInstrument.value)
            assertNull(model.selectInstrument(Instrument.DaegeumSanjo))
            assertEquals(1, patches)
        } finally { client.close() }
    }

    @Test
    fun failedSaveKeepsCurrentInstrumentAndReportsKind() = runTest {
        val client = createHttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/instruments" -> respond(CATALOG, headers = JSON)
                "/api/users/me" -> respond(PROFILE, headers = JSON)
                else -> respond("""{"isSuccess":false}""", HttpStatusCode.TooManyRequests, JSON)
            }
        }, ServerAddress.Production)
        try {
            val instruments = InstrumentRepository(FakeKeyValueStore(), InstrumentCatalog(client))
            val users = ApiUserRepository(client, null, instruments)
            assertEquals(MemberResult.Done, users.refresh())
            val model = HomeViewModel(instruments, users)

            assertEquals(MemberResult.RateLimited, model.selectInstrument(Instrument.DaegeumSanjo))

            assertEquals(Instrument.Danso, model.instrument.value)
            assertFalse(model.savingInstrument.value)
        } finally { client.close() }
    }

    private companion object {
        val JSON = headersOf(HttpHeaders.ContentType, "application/json")
        const val CATALOG = """{"isSuccess":true,"result":{"items":[{"instrumentId":"73","code":"DANSO","name":"단소"},{"instrumentId":"92","code":"SANJO_DAEGEUM","name":"산조대금"}]}}"""
        const val PROFILE = """{"isSuccess":true,"result":{"nickname":"격리테스트","instrumentId":"73","notationMode":"HANJA","linkedProviders":[]}}"""
    }
}
