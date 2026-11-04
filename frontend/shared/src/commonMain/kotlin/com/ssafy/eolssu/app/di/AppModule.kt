package com.ssafy.eolssu.app.di

import com.ssafy.eolssu.core.audio.RecordedTake
import com.ssafy.eolssu.core.audio.RecordingFiles
import com.ssafy.eolssu.data.practice.ApiEvaluationRepository
import com.ssafy.eolssu.data.practice.EvaluationRepository
import com.ssafy.eolssu.data.practice.GrowthRepository
import com.ssafy.eolssu.data.practice.HttpGrowthRepository
import com.ssafy.eolssu.data.practice.HttpPracticeSessionRepository
import com.ssafy.eolssu.data.practice.HttpRecordingUploader
import com.ssafy.eolssu.data.practice.PracticeSessionRepository
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.practice.HttpScoreViewApi
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.practice.RecordingUploader
import com.ssafy.eolssu.data.practice.RecordingUploads
import com.ssafy.eolssu.data.practice.ScoreViewApi
import com.ssafy.eolssu.data.practice.HttpPracticeRecordRepository
import com.ssafy.eolssu.data.song.MySheetRepository
import com.ssafy.eolssu.data.song.SampleMySheetRepository
import com.ssafy.eolssu.data.song.SamplePdfFile
import com.ssafy.eolssu.data.song.ApiSongRepository
import com.ssafy.eolssu.data.song.InstrumentCatalog
import com.ssafy.eolssu.data.song.ApiReferenceCatalogRepository
import com.ssafy.eolssu.data.song.ReferenceCatalogRepository
import com.ssafy.eolssu.data.song.ReferenceAudioRepository
import com.ssafy.eolssu.data.song.ApiReferenceAudioRepository
import com.ssafy.eolssu.feature.guide.ReferenceAudioViewModel
import com.ssafy.eolssu.feature.guide.ReadingCatalogViewModel
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.data.song.ModelAudioRepository
import com.ssafy.eolssu.data.song.ApiModelAudioRepository
import com.ssafy.eolssu.feature.history.GrowthViewModel
import com.ssafy.eolssu.feature.history.RecordDetailViewModel
import com.ssafy.eolssu.feature.history.RecordListViewModel
import com.ssafy.eolssu.core.model.fingering.Blow
import com.ssafy.eolssu.feature.guide.FingeringViewModel
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.feature.guide.learn.LearnPracticeViewModel
import com.ssafy.eolssu.feature.guide.learn.LearnViewModel
import com.ssafy.eolssu.core.network.SecureCookiesStorage
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.data.user.ApiAuthRepository
import com.ssafy.eolssu.data.user.AuthRepository
import com.ssafy.eolssu.data.user.FirstRunRepository
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.LearnProgressRepository
import com.ssafy.eolssu.data.user.OAuthAttempts
import com.ssafy.eolssu.data.user.OAuthRedirect
import com.ssafy.eolssu.data.user.OAuthRoute
import com.ssafy.eolssu.data.user.AccountDraft
import com.ssafy.eolssu.data.user.TermsRepository
import com.ssafy.eolssu.data.user.ApiUserRepository
import com.ssafy.eolssu.data.user.UserRepository
import com.ssafy.eolssu.data.user.VerificationPurpose
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.feature.auth.AccountLinkViewModel
import com.ssafy.eolssu.feature.auth.ConsentViewModel
import com.ssafy.eolssu.feature.auth.OAuthViewModel
import com.ssafy.eolssu.feature.auth.TermsViewModel
import com.ssafy.eolssu.feature.auth.EmailVerificationViewModel
import com.ssafy.eolssu.feature.auth.LoginViewModel
import com.ssafy.eolssu.feature.auth.PasswordSetupViewModel
import com.ssafy.eolssu.feature.auth.ProfileSetupViewModel
import com.ssafy.eolssu.feature.home.HomeViewModel
import com.ssafy.eolssu.feature.profile.MyPageViewModel
import com.ssafy.eolssu.feature.profile.NicknameEditViewModel
import com.ssafy.eolssu.feature.profile.PasswordChangeViewModel
import com.ssafy.eolssu.feature.profile.SocialLinkViewModel
import com.ssafy.eolssu.feature.profile.WithdrawViewModel
import com.ssafy.eolssu.feature.practice.PracticeTimeMeter
import com.ssafy.eolssu.feature.practice.PracticeViewModel
import com.ssafy.eolssu.feature.result.AnalysisViewModel
import com.ssafy.eolssu.feature.result.ResultViewModel
import com.ssafy.eolssu.feature.song.MySongsViewModel
import com.ssafy.eolssu.feature.song.PdfPreviewViewModel
import com.ssafy.eolssu.feature.song.PdfUploadViewModel
import com.ssafy.eolssu.feature.song.SongDetailViewModel
import com.ssafy.eolssu.feature.song.SongListViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * 앱 전체에서 쓰는 객체 등록. 화면은 `koinViewModel()`로 ViewModel을 받는다.
 * 플랫폼 구현(`KeyValueStore` 등)은 각 진입점이 넘기는 플랫폼 모듈에 있다.
 */
val appModule = module {
    // 화면이 사라져도 끝내야 하는 일(로그아웃 요청 등)을 하는 앱 전체의 범위.
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { createHttpClient(get(), get(), getOrNull<SecureCookiesStorage>()) }
    single { SignedInMember(get()) }
    single<SongRepository> { ApiSongRepository(get()) }
    single<ModelAudioRepository> { ApiModelAudioRepository(get()) }
    single { InstrumentCatalog(get()) }
    single<ReferenceCatalogRepository> { ApiReferenceCatalogRepository(get()) }
    single<ReferenceAudioRepository> { ApiReferenceAudioRepository(get(), get()) }
    viewModelOf(::ReferenceAudioViewModel)
    single { PracticeSettingsStore(get()) }
    // 현재 시각(ms). 테스트에서는 고정 값을 넣는다.
    single<() -> Long> { { Clock.System.now().toEpochMilliseconds() } }
    // 실제 세션과 결과만 사용한다. 테스트 대역은 테스트에서 직접 주입한다.
    single<EvaluationRepository> { ApiEvaluationRepository(get(), get(), member = get()) }
    single<PracticeSessionRepository> { HttpPracticeSessionRepository(get(), get()) }
    single<RecordingUploader> { HttpRecordingUploader(get()) }
    // 녹음 보관은 RecordingFiles가 있는 플랫폼(현재 Android·iOS)만 한다. 쓰는 쪽은 RecordingFiles가 있을 때만 받는다.
    single { RecordingUploads(get(), get(), get(), get(), get()) }
    single<PracticeRecordRepository> { HttpPracticeRecordRepository(get(), get(), get()) }
    single<ScoreViewApi> { HttpScoreViewApi(get()) }
    single { PracticeTimeQueue(get(), get<ScoreViewApi>(), get(), get()) }
    single<GrowthRepository> { HttpGrowthRepository(get(), get()) }

    viewModelOf(::HomeViewModel)
    viewModelOf(::SongListViewModel)
    viewModel { (songId: String) -> SongDetailViewModel(songId, get()) }
    // 녹음·마이크 권한은 플랫폼 모듈에 구현이 있을 때만 쓴다(현재 Android·iOS).
    viewModel { (songId: String, solo: Boolean) ->
        PracticeViewModel(songId, solo, get(), get(), PracticeTimeMeter(now = get()), get(), getOrNull(), getOrNull(),
            modelAudioRepository = get(), audioPlayer = get(), member = get())
    }
    viewModel { (songId: String, bpm: Int, take: RecordedTake?, pendingSessionId: String?) ->
        val uploads = getOrNull<RecordingFiles>()?.let { get<RecordingUploads>() }
        AnalysisViewModel(songId, bpm, take, pendingSessionId, get(), get(), uploads, get(), get())
    }
    viewModel { (evaluationId: String) -> ResultViewModel(evaluationId, get(), get(), get(), get()) }
    viewModel { (justSavedId: String?) -> RecordListViewModel(justSavedId, get(), get(), get()) }
    viewModel { (recordId: String) -> RecordDetailViewModel(recordId, get(), get(), player = get(), now = get(), member = get()) }
    viewModel { GrowthViewModel(get(), get(), get(), get(), get()) }

    // 운지법 보기·기초부터 배우기
    viewModel { (instrument: GuideInstrument, blow: Blow) -> FingeringViewModel(instrument, blow, get(), get()) }
    viewModelOf(::ReadingCatalogViewModel)
    viewModelOf(::LearnViewModel)
    // 단계 연습은 녹음기로 소리 크기만 잰다. 녹음기·마이크 권한이 없는 플랫폼은 스스로 확인만 한다.
    viewModel { (stage: Int) -> LearnPracticeViewModel(stage, getOrNull(), getOrNull()) }

    // 첫 실행·로그인·회원가입·비밀번호 찾기는 실제 회원 API를 사용한다.
    single { FirstRunRepository(get()) }
    single { LearnProgressRepository(get(), get()) }
    single<AuthRepository> { ApiAuthRepository(get(), getOrNull(), get(), get(), getOrNull<OAuthRoute>() ?: OAuthRedirect) }
    single { AccountDraft() }
    single { TermsRepository(get()) }
    viewModelOf(::ConsentViewModel)
    viewModel { (termId: String) -> TermsViewModel(termId, get()) }
    viewModelOf(::LoginViewModel)
    viewModel { (purpose: VerificationPurpose) -> EmailVerificationViewModel(purpose, get(), get(), get()) }
    viewModel { PasswordSetupViewModel(get(), get()) }
    viewModel { (social: Boolean) -> ProfileSetupViewModel(get(), get(), get(), get(), get(), social) }
    // 간편 로그인·연결. 외부 브라우저와 복귀 주소는 플랫폼 모듈에 구현이 있을 때만 쓴다(Android App Links, iOS 중계).
    // 복귀 경로(OAuthRoute)를 등록하지 않은 플랫폼은 Android 주소를 쓴다.
    single { OAuthAttempts(getOrNull(), get(), getOrNull<OAuthRoute>() ?: OAuthRedirect, get()) }
    viewModel { (provider: SocialProvider) -> OAuthViewModel(provider, get(), get(), getOrNull(), getOrNull(), get(), get()) }
    viewModel { AccountLinkViewModel(get(), get(), get(), get()) }

    // 활성 악기 목록의 실제 ID와 회원 설정을 연결한다.
    single { InstrumentRepository(get(), get()) }
    single<UserRepository> { ApiUserRepository(get(), getOrNull(), get(), get(), getOrNull<OAuthRoute>() ?: OAuthRedirect) }
    viewModelOf(::MyPageViewModel)
    viewModelOf(::NicknameEditViewModel)
    viewModelOf(::PasswordChangeViewModel)
    viewModel { WithdrawViewModel(get(), get(), get(), getOrNull(), getOrNull(), get()) }
    viewModel { (provider: SocialProvider) -> SocialLinkViewModel(provider, get(), get(), getOrNull(), getOrNull(), get()) }

    // 내 악보 · PDF 올리기. 기기 파일 선택기를 붙이기 전까지 '파일에서 선택'은 예시 파일을 고른다.
    single<MySheetRepository> { SampleMySheetRepository(now = get(), member = get()) }
    viewModelOf(::MySongsViewModel)
    viewModel { PdfUploadViewModel(get(), filePicker = { SamplePdfFile }, member = get()) }
    viewModel { (uploadId: String) -> PdfPreviewViewModel(uploadId, get(), get()) }
}
