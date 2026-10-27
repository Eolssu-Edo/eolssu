package com.ssafy.eolssu.app.di

import com.ssafy.eolssu.data.practice.EvaluationRepository
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.practice.SampleEvaluationRepository
import com.ssafy.eolssu.data.practice.SamplePracticeRecordRepository
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.feature.home.HomeViewModel
import com.ssafy.eolssu.feature.practice.PracticeViewModel
import com.ssafy.eolssu.feature.result.AnalysisViewModel
import com.ssafy.eolssu.feature.result.ResultViewModel
import com.ssafy.eolssu.feature.song.SongDetailViewModel
import com.ssafy.eolssu.feature.song.SongListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import kotlin.time.Clock

/** 앱 전체에서 쓰는 객체 등록. 화면은 `koinViewModel()`로 ViewModel을 받는다. */
val appModule = module {
    // API가 생기면 예시 구현을 API 구현으로 바꾼다.
    single<SongRepository> { SampleSongRepository() }
    single { PracticeSettingsStore() }
    single<EvaluationRepository> { SampleEvaluationRepository(now = { Clock.System.now().toEpochMilliseconds() }) }
    single<PracticeRecordRepository> { SamplePracticeRecordRepository() }

    viewModelOf(::HomeViewModel)
    viewModelOf(::SongListViewModel)
    viewModel { (songId: String) -> SongDetailViewModel(songId, get()) }
    viewModel { (songId: String, solo: Boolean) -> PracticeViewModel(songId, solo, get(), get()) }
    viewModel { (songId: String, bpm: Int) -> AnalysisViewModel(songId, bpm, get(), get()) }
    viewModel { (evaluationId: String) -> ResultViewModel(evaluationId, get(), get(), get()) }
}
