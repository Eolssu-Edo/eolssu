package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 별도 폐기용 MySQL+실제 Spring 서버만 사용한다. 미지정 시 명시적으로 skipped이며 성공 증거가 아니다. */
class CatalogLocalServerTest {
    @Test
    fun actualServerCatalogUsesOriginalScoreAndActiveInstrumentIds() = runTest {
        val baseUrl = System.getenv("EOLSSU_CATALOG_TEST_URL")
        assumeTrue("Local catalog server not configured", baseUrl != null)
        val uri = URI(requireNotNull(baseUrl))
        require(uri.host in setOf("127.0.0.1", "localhost") && uri.scheme == "http" && uri.userInfo == null)
        val client = createHttpClient(OkHttp.create(), ServerAddress(baseUrl))
        try {
            val repository = ApiSongRepository(client)
            val summaries = repository.getSongs()
            assertEquals(4, summaries.size, "This check requires the isolated V6 fixture database")
            val instruments = InstrumentCatalog(client).load()
            val danso = instruments.single { it.code == "DANSO" }
            val references = ApiReferenceCatalogRepository(client)
            val pitches = references.pitches(danso.instrumentId)
            assertEquals(11, pitches.size)
            assertTrue(references.fingerings(danso.instrumentId, pitches.first().pitchId).items.isEmpty())
            val jangdans = references.jangdans()
            assertEquals(3, jangdans.size)
            for (jangdan in jangdans) {
                val detail = assertNotNull(references.jangdan(jangdan.jangdanId))
                assertEquals(jangdan.beatCount, detail.pattern.beats.size)
            }
            assertTrue(references.symbols().isEmpty())
            val audio = ApiModelAudioRepository(client)
            for (summary in summaries) {
                val song = assertNotNull(repository.getSong(summary.id))
                assertEquals(danso.instrumentId, song.instrumentId)
                assertEquals(summary.scoreVersion, song.scoreVersion)
                assertEquals(song.noteCount, song.timeline.count { it.noteIndex != null })
                assertTrue(song.timeline.all { it.startTick != null && it.durationTick != null })
                assertTrue(song.durationMs() > 0)
                assertNull(audio.get(song), "The isolated database has no published model audio")
            }
        } finally { client.close() }
    }
}
