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

/** V21을 적용한 폐기용 DB의 실제 HTTP→Repository→읽기 화면 상태. 실기기 UI 검증과 구분한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class JangdanLocalServerTest {
    @Test fun sourcedRhythmsReachReadingStateWithTheirOriginalSyllablesGroupsAndUnits() = runTest {
        val baseUrl = System.getenv("EOLSSU_JANGDAN_TEST_URL")
        assumeTrue("Local V21 jangdan server not configured", baseUrl != null)
        val uri = URI(requireNotNull(baseUrl))
        require(uri.host in setOf("127.0.0.1", "localhost") && uri.scheme == "http" && uri.userInfo == null)
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = createHttpClient(OkHttp.create(), ServerAddress(baseUrl))
        try {
            val repository = ApiReferenceCatalogRepository(client)
            val vm = ReadingCatalogViewModel(repository)
            val loaded = vm.uiState.first { it.jangdansState != ReferenceLoadState.Loading }
            assertEquals(ReferenceLoadState.Ready, loaded.jangdansState)
            assertEquals((1..10).map(Int::toString), loaded.jangdans.map { it.jangdanId })
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
                assertEquals("${baseUrl.trimEnd('/')}/api/jangdans/$id/audio", detail.soundUrl)
                assertEquals(detail, repository.jangdan(id))
            }
            data class Remaining(val id: String, val name: String, val bpm: Int, val beats: List<String>, val groups: List<Int>)
            val remaining = listOf(
                Remaining("6", "엇모리", 90, listOf("덩", "", "덕", "쿵", "", "쿵", "", "덕", "쿵", ""), listOf(3,2,3,2)),
                Remaining("7", "단모리", 120, listOf("덩", "", "따", "따", "궁", "따", "궁", ""), listOf(2,2,2,2)),
                Remaining("8", "타령", 48, listOf("덩", "", "", "기닥", "", "다", "덩", "", "기닥", "", "", "다"), listOf(3,3,3,3)),
                Remaining("9", "계면가락도드리", 48, listOf("덩", "", "", "기닥", "", "다", "덩", "", "기닥", "", "", "다"), listOf(3,3,3,3)),
                Remaining("10", "세령산", 63, listOf("덩", "", "덩", "더러러러", "", "궁", "", "기닥", "", "다"), listOf(3,2,2,3)),
            )
            for (e in remaining) {
                vm.selectJangdan(e.id)
                val selected = vm.uiState.first { it.selectedJangdanId == e.id && it.detailState != ReferenceLoadState.Loading }
                assertEquals(ReferenceLoadState.Ready, selected.detailState)
                val detail = assertNotNull(selected.detail)
                assertEquals(e.name, detail.name)
                assertEquals(e.bpm, detail.defaultBpm)
                assertEquals("QUARTER", detail.bpmUnit)
                assertEquals(e.beats, detail.pattern.beats)
                assertEquals(e.groups, detail.pattern.group)
                val summary = loaded.jangdans.single { it.jangdanId == e.id }
                assertEquals(e.beats.size, summary.beatCount)
                assertEquals(e.groups.size, summary.groupCount)
                assertEquals(detail, repository.jangdan(e.id))
                if (e.id.toInt() >= 8) assertTrue(assertNotNull(detail.source).contains("사용자승인값"))
                assertEquals("${baseUrl.trimEnd('/')}/api/jangdans/${e.id}/audio", detail.soundUrl)
            }
            vm.uiState.first { it.symbolsState != ReferenceLoadState.Loading }
        } finally {
            client.close()
            Dispatchers.resetMain()
        }
    }
}
