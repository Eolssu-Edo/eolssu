package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.song.InstrumentCatalog
import com.ssafy.eolssu.data.user.ApiUserRepository
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.MemberResult
import com.ssafy.eolssu.data.user.SampleUserRepository
import com.ssafy.eolssu.data.user.UserRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** 곡 고르기 화면(SONG-01 · SONG-03) 상단 바의 연습 악기 드롭다운은 마이페이지와 같은 절차로 서버에 반영하고, 고른 악기를 함께 나눈다. */
class SongInstrumentSelectionTest {
    private fun selection(instruments: InstrumentRepository, users: UserRepository = SampleUserRepository()) =
        SongInstrumentSelection(instruments, users, CoroutineScope(SupervisorJob()))

    @Test
    fun startsWithDanso() {
        assertEquals(Instrument.Danso, selection(InstrumentRepository(FakeKeyValueStore())).instrument.value)
    }

    @Test
    fun showsInstrumentChosenElsewhere() {
        val repository = InstrumentRepository(FakeKeyValueStore())
        val model = selection(repository)

        repository.select(Instrument.Sogeum)

        assertEquals(Instrument.Sogeum, model.instrument.value)
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
            val model = selection(instruments, users)
            assertEquals(Instrument.Danso, model.instrument.value)

            assertEquals(MemberResult.Done, model.select(Instrument.DaegeumSanjo))

            assertEquals(Instrument.DaegeumSanjo, model.instrument.value)
            assertEquals("92", users.profile.value.instrumentId)
            assertFalse(model.saving.value)
            assertNull(model.select(Instrument.DaegeumSanjo))
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
            val model = selection(instruments, users)

            assertEquals(MemberResult.RateLimited, model.select(Instrument.DaegeumSanjo))

            assertEquals(Instrument.Danso, model.instrument.value)
            assertFalse(model.saving.value)
        } finally { client.close() }
    }

    private companion object {
        val JSON = headersOf(HttpHeaders.ContentType, "application/json")
        const val CATALOG = """{"isSuccess":true,"result":{"items":[{"instrumentId":"73","code":"DANSO","name":"단소"},{"instrumentId":"92","code":"SANJO_DAEGEUM","name":"산조대금"}]}}"""
        const val PROFILE = """{"isSuccess":true,"result":{"nickname":"격리테스트","instrumentId":"73","notationMode":"HANJA","linkedProviders":[]}}"""
    }
}
