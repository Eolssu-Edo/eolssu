package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferenceCatalogRepositoryTest {
    @Test fun pitchesUseActualInstrumentIdAndPreserveNullsAndSemitone() = runTest {
        val client = createHttpClient(MockEngine { request ->
            assertEquals("/api/pitches", request.url.encodedPath)
            assertEquals("9007199254740993", request.url.parameters["instrumentId"])
            respond(envelope("""{"items":[$PITCH]}"""), headers = jsonHeaders)
        }, ServerAddress.Production)
        try {
            val pitch = ApiReferenceCatalogRepository(client).pitches("9007199254740993").single()
            assertEquals("83", pitch.pitchId)
            assertEquals(75, pitch.semitone)
            assertNull(pitch.soundUrl)
            assertNull(pitch.description)
        } finally { client.close() }
    }

    @Test fun fingeringFilterAndNullableMetadataPreserveWireMeaning() = runTest {
        val client = createHttpClient(MockEngine { request ->
            assertEquals("/api/instruments/42/fingerings", request.url.encodedPath)
            assertEquals("83", request.url.parameters["pitchId"])
            respond(envelope("""{"items":[{"fingeringId":"2","instrumentId":"42","pitchId":"83","imageUrl":null,"description":null}],"structureImageUrl":null}"""), headers = jsonHeaders)
        }, ServerAddress.Production)
        try {
            val result = ApiReferenceCatalogRepository(client).fingerings("42", "83")
            assertNull(result.items.single().description)
            assertNull(result.structureImageUrl)
        } finally { client.close() }
    }

    @Test fun filteredFingeringRejectsWrongInstrumentOrPitch() = runTest {
        for ((instrument, pitch) in listOf("43" to "83", "42" to "84")) {
            val client = client("""{"items":[{"fingeringId":"2","instrumentId":"$instrument","pitchId":"$pitch"}]}""")
            try { assertFailsWith<CatalogUnavailable> { ApiReferenceCatalogRepository(client).fingerings("42", "83") } }
            finally { client.close() }
        }
    }

    @Test fun emptyReferencesAreSuccessfulAndMissingJangdanIsNull() = runTest {
        val client = client("""{"items":[]}""")
        try {
            val repository = ApiReferenceCatalogRepository(client)
            assertTrue(repository.pitches("42").isEmpty())
            assertTrue(repository.fingerings("42").items.isEmpty())
            assertTrue(repository.jangdans().isEmpty())
            assertTrue(repository.symbols().isEmpty())
        } finally { client.close() }
        val missing = client("{}", HttpStatusCode.NotFound)
        try { assertNull(ApiReferenceCatalogRepository(missing).jangdan("7")) } finally { missing.close() }
    }

    @Test fun jangdanKeepsDottedQuarterPatternAndCounts() = runTest {
        val client = createHttpClient(MockEngine { request ->
            val result = if (request.url.encodedPath == "/api/jangdans")
                """{"items":[{"jangdanId":"7","name":"굿거리","defaultBpm":66,"bpmUnit":"DOTTED_QUARTER","beatCount":12,"groupCount":4}]}"""
            else JANGDAN
            respond(envelope(result), headers = jsonHeaders)
        }, ServerAddress.Production)
        try {
            val repository = ApiReferenceCatalogRepository(client)
            assertEquals(12, repository.jangdans().single().beatCount)
            val detail = repository.jangdan("7")!!
            assertEquals("DOTTED_QUARTER", detail.bpmUnit)
            assertEquals(listOf(3, 3, 3, 3), detail.pattern.group)
            assertEquals(12, detail.pattern.beats.size)
            assertNull(detail.soundUrl)
        } finally { client.close() }
    }

    @Test fun wrongDetailIdAndMalformedGroupingFail() = runTest {
        for (body in listOf(JANGDAN.replace("\"7\"", "\"8\""), JANGDAN.replace("[3,3,3,3]", "[3,3,3,2]"))) {
            val client = client(body)
            try { assertFailsWith<CatalogUnavailable> { ApiReferenceCatalogRepository(client).jangdan("7") } } finally { client.close() }
        }
    }

    @Test fun symbolsKeepIdDescriptionAndHttpsLink() = runTest {
        val client = client("""{"items":[{"symbolId":"17","symbol":"△","name":"쉼표","description":"등록된 설명","imageUrl":"https://example.test/symbol.png"}]}""")
        try {
            val symbol = ApiReferenceCatalogRepository(client).symbols().single()
            assertEquals("17", symbol.symbolId)
            assertEquals("등록된 설명", symbol.description)
        } finally { client.close() }
    }

    @Test fun invalidIdsNeverBecomeRequests() = runTest {
        var count = 0
        val client = createHttpClient(MockEngine { count++; respond("{}") }, ServerAddress.Production)
        try {
            val repository = ApiReferenceCatalogRepository(client)
            for (id in listOf("0", "01", "../me", "+1", "9223372036854775808")) {
                assertFailsWith<CatalogUnavailable> { repository.pitches(id) }
                assertFailsWith<CatalogUnavailable> { repository.fingerings("42", id) }
                assertFailsWith<CatalogUnavailable> { repository.jangdan(id) }
            }
            assertEquals(0, count)
        } finally { client.close() }
    }

    @Test fun duplicateIdsUnsafeLinksAndFailedEnvelopeAreNotValidContent() = runTest {
        for (body in listOf("""{"items":[$PITCH,$PITCH]}""", """{"items":[${PITCH.replace("\"soundUrl\":null", "\"soundUrl\":\"javascript:alert(1)\"")}]}""")) {
            val client = client(body)
            try { assertFailsWith<CatalogUnavailable> { ApiReferenceCatalogRepository(client).pitches("42") } } finally { client.close() }
        }
        val client = createHttpClient(MockEngine { respond("""{"isSuccess":false,"result":{"items":[]}}""", headers = jsonHeaders) }, ServerAddress.Production)
        try { assertFailsWith<CatalogUnavailable> { ApiReferenceCatalogRepository(client).symbols() } } finally { client.close() }
    }

    @Test fun networkFailureDoesNotBecomeEmptyContent() = runTest {
        val client = client("{}", HttpStatusCode.ServiceUnavailable)
        try { assertFailsWith<CatalogUnavailable> { ApiReferenceCatalogRepository(client).symbols() } } finally { client.close() }
    }

    private fun client(result: String, status: HttpStatusCode = HttpStatusCode.OK) = createHttpClient(MockEngine {
        respond(envelope(result), status, jsonHeaders)
    }, ServerAddress.Production)
    private fun envelope(result: String) = """{"isSuccess":true,"result":$result}"""
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private companion object {
        const val PITCH = """{"pitchId":"83","yul":"황","octave":1,"hanja":"潢","hangul":"청황","semitone":75,"description":null,"soundUrl":null}"""
        const val JANGDAN = """{"jangdanId":"7","name":"굿거리","defaultBpm":66,"bpmUnit":"DOTTED_QUARTER","pattern":{"beats":["덩","","기","덕","","쿵","더","러","러","쿵","","기"],"group":[3,3,3,3]},"source":"검증 출처","soundUrl":null}"""
    }
}
