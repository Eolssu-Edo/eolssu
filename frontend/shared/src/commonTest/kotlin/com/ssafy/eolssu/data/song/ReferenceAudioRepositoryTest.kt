package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReferenceAudioRepositoryTest {
    @Test fun exactLabelFiltersPreserveBothRecordingsAndResolvePlaybackAgainstApiOrigin() = runTest {
        val address = ServerAddress("https://api.example.test:8443")
        val client = createHttpClient(MockEngine { request ->
            assertEquals("/api/reference-audios", request.url.encodedPath)
            assertEquals("sogeum", request.url.parameters["datasetId"])
            assertEquals("기본음계", request.url.parameters["category"])
            assertEquals("역취", request.url.parameters["register"])
            assertEquals("중청임", request.url.parameters["yulmyeong"])
            respond("""{"isSuccess":true,"result":{"items":[${clip("a")},${clip("b")}]}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, address)
        try {
            val result = ApiReferenceAudioRepository(client, address).clips(ReferenceAudioQuery("sogeum", "기본음계", "역취", "중청임"))
            assertEquals(listOf("a".repeat(64), "b".repeat(64)), result.map { it.assetId })
            assertEquals(listOf("source-a", "source-b"), result.map { it.labelId })
            assertEquals("https://api.example.test:8443/api/reference-audios/${"a".repeat(64)}/play", result.first().soundUrl)
        } finally { client.close() }
    }

    @Test fun allClipsOmitsNoteFiltersAndAllowsLocalConfiguredOrigin() = runTest {
        val address = ServerAddress("http://127.0.0.1:18065")
        val client = createHttpClient(MockEngine { request ->
            assertEquals(setOf("datasetId"), request.url.parameters.names())
            respond(envelope(clip("a")), headers = jsonHeaders)
        }, address)
        try {
            assertEquals("http://127.0.0.1:18065/api/reference-audios/${"a".repeat(64)}/play",
                ApiReferenceAudioRepository(client, address).clips(ReferenceAudioQuery("sogeum")).single().soundUrl)
        } finally { client.close() }
    }

    @Test fun unsafeOrWrongAssetPlaybackLinksNeverReachTheUi() = runTest {
        val path = "/api/reference-audios/${"a".repeat(64)}/play"
        for (url in listOf("https://evil.example/file.m4a", "//evil.example/file.m4a", "javascript:alert(1)",
            "/api/reference-audios/${"b".repeat(64)}/play", "$path?token=x", "$path#fragment", "$path/../other")) {
            val client = mockClient(envelope(clip("a").replace(path, url)))
            try { assertFailsWith<CatalogUnavailable> { ApiReferenceAudioRepository(client, ServerAddress.Production).clips(ReferenceAudioQuery("sogeum")) } }
            finally { client.close() }
        }
    }

    @Test fun duplicatesWrongFiltersAndRejectedRowsAreNotExposed() = runTest {
        for (body in listOf(envelope("${clip("a")},${clip("a")}"), envelope(clip("a").replace("sogeum", "danso")),
            envelope(clip("a").replace("inferred", "rejected")), envelope(clip("a").replace("중청임", "임")))) {
            val client = mockClient(body)
            try { assertFailsWith<CatalogUnavailable> { ApiReferenceAudioRepository(client, ServerAddress.Production)
                .clips(ReferenceAudioQuery("sogeum", "기본음계", "역취", "중청임")) } }
            finally { client.close() }
        }
    }

    @Test fun emptyPublishedListIsDifferentFromNetworkAndEnvelopeFailure() = runTest {
        val empty = mockClient(envelope(""))
        try { assertTrue(ApiReferenceAudioRepository(empty, ServerAddress.Production).clips(ReferenceAudioQuery("danso")).isEmpty()) }
        finally { empty.close() }
        for ((body, status) in listOf("{}" to HttpStatusCode.ServiceUnavailable,
            """{"isSuccess":false,"result":{"items":[]}}""" to HttpStatusCode.OK, "not-json" to HttpStatusCode.OK)) {
            val client = mockClient(body, status)
            try { assertFailsWith<CatalogUnavailable> { ApiReferenceAudioRepository(client, ServerAddress.Production).clips(ReferenceAudioQuery("danso")) } }
            finally { client.close() }
        }
    }

    @Test fun invalidDatasetDoesNotRequestAndCancellationPropagates() = runTest {
        var requests = 0
        val client = createHttpClient(MockEngine { requests++; throw CancellationException("dismissed") }, ServerAddress.Production)
        try {
            val repository = ApiReferenceAudioRepository(client, ServerAddress.Production)
            assertFailsWith<CatalogUnavailable> { repository.clips(ReferenceAudioQuery("daegeum")) }
            assertEquals(0, requests)
            assertFailsWith<CancellationException> { repository.clips(ReferenceAudioQuery("danso")) }
        } finally { client.close() }
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private fun envelope(items: String) = """{"isSuccess":true,"result":{"items":[$items]}}"""
    private fun mockClient(body: String, status: HttpStatusCode = HttpStatusCode.OK) = createHttpClient(MockEngine {
        respond(body, status, jsonHeaders)
    }, ServerAddress.Production)

    private fun clip(id: String) = """{"assetId":"${id.repeat(64)}","datasetId":"sogeum","labelId":"source-$id","category":"기본음계","technique":"깊은요성","register":"역취","yulmyeong":"중청임","displayLabel":"중청임","durationMs":1200,"review":"inferred","soundUrl":"/api/reference-audios/${id.repeat(64)}/play"}"""
}
