package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferenceCatalogRepositoryTest {
    @Test fun authoringPreservesUnknownFingeringAndUsesServerIntersection() = runTest {
        val result = """{"instrumentId":"42","instrumentCode":"SOGEUM","policyVersion":"registered-guidance-cat/v1","nominalHwangMidi":63,"physicalRange":{"minMidi":70,"maxMidi":96,"verificationStatus":"REFERENCE_ONLY"},"items":[{"pitchId":"83","yul":"이","octave":1,"hanja":"洟","hangul":"청이","semitone":20,"midi":83,"physical":"WITHIN_REFERENCE_RANGE","physicalVerification":"UNKNOWN","fingering":"UNKNOWN","notation":"SUPPORTED","synthesis":"SUPPORTED","selectable":false,"reasonCodes":["FINGERING_UNKNOWN"]}]}"""
        val client = client(result)
        try {
            val capabilities = ApiReferenceCatalogRepository(client).authoringCapabilities("42")
            assertTrue(capabilities.selectablePitches.isEmpty())
            assertEquals("UNKNOWN", capabilities.items.single().physicalVerification)
            assertEquals(listOf("FINGERING_UNKNOWN"), capabilities.items.single().reasonCodes)
        } finally { client.close() }
        val inconsistent = client(result.replace("\"selectable\":false", "\"selectable\":true"))
        try { assertFailsWith<CatalogUnavailable> { ApiReferenceCatalogRepository(inconsistent).authoringCapabilities("42") } }
        finally { inconsistent.close() }
    }
    @Test fun builtinPatternSurvivesTheWireWithoutReplacingTheInstrumentDrawing() = runTest {
        val pattern = """{"blow":"HIGH","hanja":"㶃","name":"중청태주","reading":"청태","holes":["CLOSED","OPEN","CLOSED","OPEN","OPEN"],"basic":true,"source":"출처를 보존","instrumentNote":"높은 태는 낮은 태와 달라요.","nominalHwangPitchClass":3,"verificationStatus":"BUILTIN_REFERENCE"}"""
        val client = client("""{"items":[{"fingeringId":"2","instrumentId":"42","pitchId":"83","imageUrl":null,"description":null,"pattern":$pattern}]}""")
        try {
            val result = ApiReferenceCatalogRepository(client).fingerings("42", "83")
            assertEquals(Json.parseToJsonElement(pattern), Json.encodeToJsonElement(result.items.single()).jsonObject["pattern"])
            assertNull(result.items.single().imageUrl)
        } finally { client.close() }
    }

    @Test fun patternKeepsUnknownAndHalfHolesAndRejectsInvalidShapes() = runTest {
        val pattern = """{"blow":"MID","hanja":"南","name":"남려","reading":"남","holes":["UNKNOWN","HALF","CLOSED","OPEN","OPEN"],"basic":false,"source":"확인 전 자료","instrumentNote":"원본 설명","nominalHwangPitchClass":5,"verificationStatus":"BUILTIN_REFERENCE"}"""
        fun response(value: String) = """{"items":[{"fingeringId":"2","instrumentId":"42","pitchId":"83","pattern":$value}]}"""
        val valid = client(response(pattern))
        try {
            val result = ApiReferenceCatalogRepository(valid).fingerings("42").items.single().pattern!!
            assertEquals(CatalogFingeringHole.UNKNOWN, result.holes[0])
            assertEquals(CatalogFingeringHole.HALF, result.holes[1])
            assertEquals(5, result.nominalHwangPitchClass)
        } finally { valid.close() }
        for (bad in listOf(
            pattern.replace("UNKNOWN","MAYBE"),
            pattern.replace("\"nominalHwangPitchClass\":5","\"nominalHwangPitchClass\":12"),
            pattern.replace("\"reading\":\"남\"","\"reading\":\"\""),
            pattern.replace("\"basic\":false,",""),
            pattern.replace(",\"OPEN\",\"OPEN\"",""),
            pattern.replace("BUILTIN_REFERENCE","REVIEWED"),
        )) {
            val invalid = client(response(bad))
            try { assertFailsWith<CatalogUnavailable> { ApiReferenceCatalogRepository(invalid).fingerings("42") } }
            finally { invalid.close() }
        }
    }

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

    @Test fun eachJangdanResolvesOnlyItsOwnPlaybackPath() = runTest {
        val server = ServerAddress("http://127.0.0.1:18081")
        val client = createHttpClient(MockEngine { request ->
            val id = request.url.encodedPath.substringAfterLast('/')
            val path = if (id == "11") "/api/jangdans/1/audio" else "/api/jangdans/$id/audio"
            val body = JANGDAN.replace("\"jangdanId\":\"7\"", "\"jangdanId\":\"$id\"")
                .replace("\"soundUrl\":null", "\"soundUrl\":\"$path\"")
            respond(envelope(body), headers = jsonHeaders)
        }, server)
        try {
            val repository = ApiReferenceCatalogRepository(client)
            for (id in 1..10) {
                assertEquals("http://127.0.0.1:18081/api/jangdans/$id/audio", repository.jangdan("$id")?.soundUrl)
            }
            assertFailsWith<CatalogUnavailable> { repository.jangdan("11") }
        } finally { client.close() }
    }

    @Test fun symbolsKeepIdDescriptionAndHttpsLink() = runTest {
        val client = client("""{"items":[{"symbolId":"17","symbol":"△","name":"쉼표","description":"등록된 설명","imageUrl":"https://example.test/symbol.png"}]}""")
        try {
            val symbol = ApiReferenceCatalogRepository(client).symbols().single()
            assertEquals("17", symbol.symbolId)
            assertEquals("등록된 설명", symbol.description)
        } finally { client.close() }
    }

    @Test fun symbolImagePathIsResolvedOnlyForItsOwnIdAndPreservesProvenance() = runTest {
        val server = ServerAddress("http://127.0.0.1:18081/ignored?discard=1#fragment")
        val client = createHttpClient(MockEngine {
            respond(envelope("""{"items":[{"symbolId":"17","symbol":"노라느니","name":"노라느니","description":"기존 설명","imageUrl":"/api/notation-symbols/17/image","source":"우물사이 추정","descriptionStatus":"INFERRED","imageLicense":"MIT"}]}"""), headers = jsonHeaders)
        }, server)
        try {
            val symbol = ApiReferenceCatalogRepository(client).symbols().single()
            assertEquals("http://127.0.0.1:18081/api/notation-symbols/17/image", symbol.imageUrl)
            // 새 필드는 wire 단계에서 보존되고 기존 설명은 바뀌지 않는다.
            assertEquals("기존 설명", symbol.description)
            assertEquals("우물사이 추정", symbol.source)
            assertEquals("INFERRED", symbol.descriptionStatus)
            assertEquals("MIT", symbol.imageLicense)
        } finally { client.close() }
    }

    @Test fun unregisteredOrForeignRelativeImagePathsFail() = runTest {
        for (path in listOf("/api/notation-symbols/18/image", "//evil.test/x", "/api/notation-symbols/17/image?x=1", "/api/users/me", "/api/notation-symbols/../image")) {
            val client = client("""{"items":[{"symbolId":"17","symbol":"△","name":"쉼표","description":"설명","imageUrl":"$path"}]}""")
            try { assertFailsWith<CatalogUnavailable> { ApiReferenceCatalogRepository(client).symbols() } }
            finally { client.close() }
        }
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
