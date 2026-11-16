package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.app.di.appModule
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.testing.CatalogSongs
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 곡 상세와 태블릿 가로 곡 목록의 오른쪽 칸이 넘기는 인자로 앱 모듈의 [SongDetailViewModel]을 만들 수 있는지.
 * 인자 수가 정의보다 적으면 가로에서 'AI 연주 평가 시작'을 누르거나 곡 목록에서 가로로 돌릴 때 앱이 종료됐다(S15P31E102-1012).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SongDetailWiringTest : CatalogViewModelTest() {
    @Test
    fun songListPaneArgumentsCreateTheDetailViewModel() = runTest {
        val song = SampleSongs.first()
        val app = koinApplication { modules(appModule, module { single<SongRepository> { CatalogSongs() } }) }
        try {
            val viewModel = keep(app.koin.get<SongDetailViewModel> { songDetailParameters(song.id) })
            advanceUntilIdle()
            assertEquals(SongDetailUiState(song, SongLoadState.Ready), viewModel.uiState.value)
        } finally {
            app.close()
        }
    }
}
