package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Opt-in, read-only QA: exercise the app's actual DTO/score adapters against production. */
class QaLiveCatalogTest {
    @Test
    fun publishedCatalogCanBeConsumedByAndroidRepositories() = runTest(timeout = 90.seconds) {
        assumeTrue("Production QA not requested", System.getenv("EOLSSU_QA_LIVE_CATALOG") == "true")
        val client = createHttpClient(OkHttp.create(), ServerAddress.Production)
        try {
            val instruments = InstrumentCatalog(client).load()
            assertTrue(instruments.isNotEmpty())
            val repository = ApiSongRepository(client)
            val songs = repository.getSongs()
            assertTrue(songs.isNotEmpty())
            val references = ApiReferenceCatalogRepository(client)
            for (instrument in instruments) {
                assertTrue(references.pitches(instrument.instrumentId).isNotEmpty())
                references.fingerings(instrument.instrumentId)
            }
            for (jangdan in references.jangdans()) assertNotNull(references.jangdan(jangdan.jangdanId))
            references.symbols()
            for (summary in songs) {
                val song = assertNotNull(repository.getSong(summary.id))
                assertEquals(summary.scoreVersion, song.scoreVersion)
                assertTrue(song.durationMs() > 0)
                ApiModelAudioRepository(client).get(song)?.let { assertTrue(it.matches(song)) }
            }
        } finally { client.close() }
    }
}
