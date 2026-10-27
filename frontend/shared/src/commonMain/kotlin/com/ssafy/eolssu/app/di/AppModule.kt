package com.ssafy.eolssu.app.di

import com.ssafy.eolssu.data.practice.EvaluationRepository
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.practice.SampleEvaluationRepository
import com.ssafy.eolssu.data.practice.SamplePracticeRecordRepository
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.feature.history.GrowthViewModel
import com.ssafy.eolssu.feature.history.RecordDetailViewModel
import com.ssafy.eolssu.feature.history.RecordListViewModel
import com.ssafy.eolssu.feature.guide.Blow
import com.ssafy.eolssu.feature.guide.FingeringViewModel
import com.ssafy.eolssu.feature.guide.GuideInstrument
import com.ssafy.eolssu.feature.guide.learn.LearnViewModel
import com.ssafy.eolssu.data.user.FirstRunRepository
import com.ssafy.eolssu.feature.auth.ConsentViewModel
import com.ssafy.eolssu.feature.auth.LoginViewModel
import com.ssafy.eolssu.feature.auth.ProfileSetupViewModel
import com.ssafy.eolssu.feature.auth.SignupViewModel
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

/**
 * 앱 전체에서 쓰는 객체 등록. 화면은 `koinViewModel()`로 ViewModel을 받는다.
 * 플랫폼 구현(`KeyValueStore` 등)은 각 진입점이 넘기는 플랫폼 모듈에 있다.
 */
val appModule = module {
    // API가 생기면 예시 구현을 API 구현으로 바꾼다.
    single<SongRepository> { SampleSongRepository() }
    single { PracticeSettingsStore() }
    // 현재 시각(ms). 테스트에서는 고정 값을 넣는다.
    single<() -> Long> { { Clock.System.now().toEpochMilliseconds() } }
    single<EvaluationRepository> { SampleEvaluationRepository(now = get()) }
    single<PracticeRecordRepository> { SamplePracticeRecordRepository() }

    viewModelOf(::HomeViewModel)
    viewModelOf(::SongListViewModel)
    viewModel { (songId: String) -> SongDetailViewModel(songId, get()) }
    viewModel { (songId: String, solo: Boolean) -> PracticeViewModel(songId, solo, get(), get()) }
    viewModel { (songId: String, bpm: Int) -> AnalysisViewModel(songId, bpm, get(), get()) }
    viewModel { (evaluationId: String) -> ResultViewModel(evaluationId, get(), get(), get()) }
    viewModel { (justSavedId: String?) -> RecordListViewModel(justSavedId, get(), get()) }
    viewModel { (recordId: String) -> RecordDetailViewModel(recordId, get(), get()) }
    viewModel { GrowthViewModel(get(), get()) }

    // 운지법 보기·기초부터 배우기
    viewModel { (instrument: GuideInstrument, blow: Blow) -> FingeringViewModel(instrument, blow) }
    viewModelOf(::LearnViewModel)

    // 첫 실행·로그인·회원가입
    single { FirstRunRepository(get()) }
    viewModelOf(::ConsentViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::SignupViewModel)
    viewModelOf(::ProfileSetupViewModel)
}
