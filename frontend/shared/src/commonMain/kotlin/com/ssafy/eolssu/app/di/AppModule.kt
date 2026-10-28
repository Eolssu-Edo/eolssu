package com.ssafy.eolssu.app.di

import com.ssafy.eolssu.data.practice.EvaluationRepository
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.practice.SampleEvaluationRepository
import com.ssafy.eolssu.data.practice.SamplePracticeRecordRepository
import com.ssafy.eolssu.data.song.MySheetRepository
import com.ssafy.eolssu.data.song.SampleMySheetRepository
import com.ssafy.eolssu.data.song.SamplePdfFile
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.feature.history.GrowthViewModel
import com.ssafy.eolssu.feature.history.RecordDetailViewModel
import com.ssafy.eolssu.feature.history.RecordListViewModel
import com.ssafy.eolssu.feature.guide.Blow
import com.ssafy.eolssu.feature.guide.FingeringViewModel
import com.ssafy.eolssu.feature.guide.GuideInstrument
import com.ssafy.eolssu.feature.guide.learn.LearnViewModel
import com.ssafy.eolssu.data.user.AuthRepository
import com.ssafy.eolssu.data.user.FirstRunRepository
import com.ssafy.eolssu.data.user.LearnProgressRepository
import com.ssafy.eolssu.data.user.SampleAuthRepository
import com.ssafy.eolssu.data.user.VerificationPurpose
import com.ssafy.eolssu.feature.auth.AccountLinkViewModel
import com.ssafy.eolssu.feature.auth.ConsentViewModel
import com.ssafy.eolssu.feature.auth.EmailVerificationViewModel
import com.ssafy.eolssu.feature.auth.LoginViewModel
import com.ssafy.eolssu.feature.auth.PasswordSetupViewModel
import com.ssafy.eolssu.feature.auth.ProfileSetupViewModel
import com.ssafy.eolssu.feature.home.HomeViewModel
import com.ssafy.eolssu.feature.practice.PracticeViewModel
import com.ssafy.eolssu.feature.result.AnalysisViewModel
import com.ssafy.eolssu.feature.result.ResultViewModel
import com.ssafy.eolssu.feature.song.MySongsViewModel
import com.ssafy.eolssu.feature.song.PdfPreviewViewModel
import com.ssafy.eolssu.feature.song.PdfUploadViewModel
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
    single { PracticeSettingsStore(get()) }
    // 현재 시각(ms). 테스트에서는 고정 값을 넣는다.
    single<() -> Long> { { Clock.System.now().toEpochMilliseconds() } }
    single<EvaluationRepository> { SampleEvaluationRepository(now = get()) }
    single<PracticeRecordRepository> { SamplePracticeRecordRepository() }

    viewModelOf(::HomeViewModel)
    viewModelOf(::SongListViewModel)
    viewModel { (songId: String) -> SongDetailViewModel(songId, get()) }
    // 녹음·마이크 권한은 플랫폼 모듈에 구현이 있을 때만 쓴다(현재 Android).
    viewModel { (songId: String, solo: Boolean) -> PracticeViewModel(songId, solo, get(), get(), getOrNull(), getOrNull()) }
    viewModel { (songId: String, bpm: Int) -> AnalysisViewModel(songId, bpm, get(), get()) }
    viewModel { (evaluationId: String) -> ResultViewModel(evaluationId, get(), get(), get()) }
    viewModel { (justSavedId: String?) -> RecordListViewModel(justSavedId, get(), get()) }
    viewModel { (recordId: String) -> RecordDetailViewModel(recordId, get(), get()) }
    viewModel { GrowthViewModel(get(), get()) }

    // 운지법 보기·기초부터 배우기
    viewModel { (instrument: GuideInstrument, blow: Blow) -> FingeringViewModel(instrument, blow) }
    viewModelOf(::LearnViewModel)

    // 첫 실행·로그인·회원가입·비밀번호 찾기. 인증 API를 붙이기 전까지 예시 저장소가 로그인·인증번호 결과를 돌려준다.
    single { FirstRunRepository(get()) }
    single { LearnProgressRepository(get()) }
    single<AuthRepository> { SampleAuthRepository(now = get()) }
    viewModelOf(::ConsentViewModel)
    viewModelOf(::LoginViewModel)
    viewModel { (purpose: VerificationPurpose) -> EmailVerificationViewModel(purpose, get(), get()) }
    viewModelOf(::PasswordSetupViewModel)
    viewModelOf(::ProfileSetupViewModel)
    viewModelOf(::AccountLinkViewModel)

    // 내 악보 · PDF 올리기. 기기 파일 선택기를 붙이기 전까지 '파일에서 선택'은 예시 파일을 고른다.
    single<MySheetRepository> { SampleMySheetRepository(now = get()) }
    viewModelOf(::MySongsViewModel)
    viewModel { PdfUploadViewModel(get(), filePicker = { SamplePdfFile }) }
    viewModel { (uploadId: String) -> PdfPreviewViewModel(uploadId, get()) }
}
