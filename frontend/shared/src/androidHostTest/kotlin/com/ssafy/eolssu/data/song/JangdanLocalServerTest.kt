package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.feature.guide.ReadingCatalogViewModel
import com.ssafy.eolssu.feature.guide.ReferenceLoadState
import io.ktor.client.engine.okhttp.OkHttp
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assume.assumeTrue
import kotlin.test.*

/** V19를 적용한 폐기용 DB의 실제 HTTP→Repository→읽기 화면 상태. 실기기 UI 검증과 구분한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class JangdanLocalServerTest {
    @Test fun sourcedRhythmsReachReadingStateWithTheirOriginalSyllablesGroupsAndUnits() = runTest {
        val baseUrl = System.getenv("EOLSSU_JANGDAN_TEST_URL")
        assumeTrue("Local V19 jangdan server not configured", baseUrl != null)
        val uri = URI(requireNotNull(baseUrl))
        require(uri.host in setOf("127.0.0.1", "localhost") && uri.scheme == "http" && uri.userInfo == null)
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = createHttpClient(OkHttp.create(), ServerAddress(baseUrl))
        try {
            val repository = ApiReferenceCatalogRepository(client)
            val vm = ReadingCatalogViewModel(repository)
            val loaded = vm.uiState.first { it.jangdansState != ReferenceLoadState.Loading }
            assertEquals(ReferenceLoadState.Ready, loaded.jangdansState)
            assertEquals(listOf("1", "2", "3", "4", "5"), loaded.jangdans.map { it.jangdanId })
            val expected = listOf(
                Triple("4", 96, listOf("떵", "", "덕", "쿵", "덕", "", "떵", "", "덕", "쿵", "덕", "")),
                Triple("5", 80, listOf("덩", "", "따", "궁", "따", "따", "궁", "궁", "따", "궁", "", "궁")),
            )
            for ((id, bpm, beats) in expected) {
                vm.selectJangdan(id)
                val selected = vm.uiState.first { it.selectedJangdanId == id && it.detailState != ReferenceLoadState.Loading }
                assertEquals(ReferenceLoadState.Ready, selected.detailState)
                val detail = assertNotNull(selected.detail)
                assertEquals(if (id == "4") "자진모리" else "중중모리", detail.name)
                assertEquals(bpm, detail.defaultBpm)
                assertEquals("DOTTED_QUARTER", detail.bpmUnit)
                assertEquals(beats, detail.pattern.beats)
                assertEquals(listOf(3, 3, 3, 3), detail.pattern.group)
                val summary = loaded.jangdans.single { it.jangdanId == id }
                assertEquals(12, summary.beatCount)
                assertEquals(4, summary.groupCount)
                assertEquals(summary.defaultBpm, detail.defaultBpm)
                assertEquals(summary.bpmUnit, detail.bpmUnit)
                assertTrue(assertNotNull(detail.source).contains("https://"))
                assertNull(detail.soundUrl)
                assertEquals(detail, repository.jangdan(id))
            }
            vm.uiState.first { it.symbolsState != ReferenceLoadState.Loading }
        } finally {
            client.close()
            Dispatchers.resetMain()
        }
    }
}
