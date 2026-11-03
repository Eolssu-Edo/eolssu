package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ApiSongRepositoryTest {
    @Test
    fun missingJangdanKeepsCanonicalNameWithoutBreakingOtherSongs() = runTest {
        val unlinked = """{"songId":"7","title":"장단 미연결 곡","instrumentId":"3","difficulty":2,"defaultBpm":60,"jangdan":null,"isScorable":false,"noteCount":1,"scoreVersion":1,"score":{"score":{"instrument":"SANJO_DAEGEUM"},"music":{"jangdan":"타령","bpmUnit":"QUARTER"}}}"""
        val items = List(8) { ITEM.replace("9007199254740993", (it + 1).toString()) } +
            List(103) { unlinked.replace("\"songId\":\"7\"", "\"songId\":\"${it + 9}\"") }
        val client = catalogClient("""{"isSuccess":true,"result":{"items":[${items.joinToString(",")}]}}""")
        try {
            val songs = ApiSongRepository(client).getSongs()
            assertEquals(111, songs.size)
            assertEquals("타령", songs.last().jangdanName)
            assertEquals("SANJO_DAEGEUM", songs.last().instrumentCode)
            assertFalse(songs.last().evaluable)
        } finally { client.close() }
    }

    @Test
    fun listRetainsLargeIdsUnknownDifficultyAndPracticeOnlySongs() = runTest {
        val client = catalogClient("""{"isSuccess":true,"result":{"items":[$ITEM]}}""")
        try {
            val song = ApiSongRepository(client).getSongs().single()
            assertEquals("9007199254740993", song.id)
            assertEquals("42", song.instrumentId)
            assertEquals(5, song.difficulty)
            assertNull(song.level)
            assertFalse(song.evaluable)
            assertNull(song.instrumentCode, "목록에 악보가 없으면 악기 코드를 추정하지 않는다")
        } finally { client.close() }
    }

    @Test
    fun listKeepsInstrumentCodeFromAttachedScore() = runTest {
        val withScore = ITEM.dropLast(1) + ""","score":{"score":{"instrument":"DANSO","scoreVersion":3}}}"""
        val client = catalogClient("""{"isSuccess":true,"result":{"items":[$withScore]}}""")
        try {
            assertEquals("DANSO", ApiSongRepository(client).getSongs().single().instrumentCode)
        } finally { client.close() }
    }

    @Test
    fun emptyListIsValidButDuplicateIdsAndMalformedEnvelopeFail() = runTest {
        val empty = catalogClient("""{"isSuccess":true,"result":{"items":[]}}""")
        try { assertEquals(emptyList(), ApiSongRepository(empty).getSongs()) } finally { empty.close() }
        for (body in listOf(
            """{"isSuccess":true,"result":{"items":[$ITEM,$ITEM]}}""",
            """{"isSuccess":false,"result":{"items":[]}}""",
            """{"isSuccess":true,"result":{}}""",
            "<html>proxy error</html>",
        )) {
            val client = catalogClient(body)
            try { assertFailsWith<CatalogUnavailable> { ApiSongRepository(client).getSongs() } } finally { client.close() }
        }
    }

    @Test
    fun missingDetailIsDifferentFromUnavailableServer() = runTest {
        val missing = catalogClient("{}", HttpStatusCode.NotFound)
        try { assertNull(ApiSongRepository(missing).getSong("1")) } finally { missing.close() }
        val failed = catalogClient("{}", HttpStatusCode.ServiceUnavailable)
        try { assertFailsWith<CatalogUnavailable> { ApiSongRepository(failed).getSong("1") } } finally { failed.close() }
    }

    @Test
    fun invalidOrSampleIdNeverBecomesServerPath() = runTest {
        var requests = 0
        val client = createHttpClient(MockEngine {
            requests++
            respond("{}")
        }, ServerAddress.Production)
        try {
            for (id in listOf("arirang", "../users/me", "0", "9223372036854775808")) {
                assertNull(ApiSongRepository(client).getSong(id))
            }
            assertEquals(0, requests)
        } finally { client.close() }
    }

    @Test
    fun instrumentCodeIsPreservedWithoutFallbackAndDuplicateIdsFail() = runTest {
        val item = """{"instrumentId":"17","code":"FUTURE_INSTRUMENT","name":"새 악기"}"""
        val client = catalogClient("""{"isSuccess":true,"result":{"items":[$item]}}""")
        try {
            assertEquals(CatalogInstrument("17", "FUTURE_INSTRUMENT", "새 악기"), InstrumentCatalog(client).load().single())
        } finally { client.close() }
        val duplicate = catalogClient("""{"isSuccess":true,"result":{"items":[$item,$item]}}""")
        try { assertFailsWith<CatalogUnavailable> { InstrumentCatalog(duplicate).load() } } finally { duplicate.close() }
    }

    private companion object {
        const val ITEM = """{"songId":"9007199254740993","title":"테스트 곡","instrumentId":"42","difficulty":5,"defaultBpm":96,"jangdan":{"jangdanId":"3","name":"세마치","bpmUnit":"DOTTED_QUARTER","pattern":{"beats":["떵","","","떵","","덕","쿵","덕",""],"group":[3,3,3]}},"isScorable":false,"noteCount":1,"scoreVersion":3}"""
    }
}

private fun catalogClient(body: String, status: HttpStatusCode = HttpStatusCode.OK) = createHttpClient(
    MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) },
    ServerAddress.Production,
)
