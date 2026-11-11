package com.ssafy.eolssu.app.di

import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.SessionSection
import com.ssafy.eolssu.core.audio.RecordedTake
import com.ssafy.eolssu.core.audio.RecordingFiles
import com.ssafy.eolssu.data.practice.ApiEvaluationRepository
import com.ssafy.eolssu.data.practice.EvaluationRepository
import com.ssafy.eolssu.data.practice.GrowthRepository
import com.ssafy.eolssu.data.practice.HttpGrowthRepository
import com.ssafy.eolssu.data.practice.HttpNotePracticeCriteriaRepository
import com.ssafy.eolssu.data.practice.NotePracticeCriteriaRepository
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
import com.ssafy.eolssu.data.song.SheetDocumentRepository
import com.ssafy.eolssu.data.song.SheetPracticeRepository
import com.ssafy.eolssu.data.song.ApiSheetDocumentRepository
import com.ssafy.eolssu.data.song.SheetImportRepository
import com.ssafy.eolssu.data.song.ApiSheetImportRepository
import com.ssafy.eolssu.data.song.ApiPublishedSheetCopyRepository
import com.ssafy.eolssu.data.song.PublishedSheetCopyRepository
import com.ssafy.eolssu.data.song.ApiSheetConversionRepository
import com.ssafy.eolssu.data.song.SheetConversionRepository
import com.ssafy.eolssu.feature.song.edit.SheetImportViewModel
import com.ssafy.eolssu.data.song.ApiMySheetRepository
import com.ssafy.eolssu.data.song.ApiSheetAudioRepository
import com.ssafy.eolssu.data.song.SheetAudioRepository
import com.ssafy.eolssu.data.song.ApiSongRepository
import com.ssafy.eolssu.data.song.InstrumentCatalog
import com.ssafy.eolssu.data.song.ApiReferenceCatalogRepository
import com.ssafy.eolssu.data.song.ReferenceCatalogRepository
import com.ssafy.eolssu.data.song.ReferenceAudioRepository
import com.ssafy.eolssu.data.song.ApiReferenceAudioRepository
import com.ssafy.eolssu.feature.guide.ReferenceAudioViewModel
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
import com.ssafy.eolssu.feature.guide.learn.loadLearnCriteria
import com.ssafy.eolssu.feature.guide.learn.LearnViewModel
import com.ssafy.eolssu.core.network.SecureCookiesStorage
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.data.user.ApiAuthRepository
import com.ssafy.eolssu.data.user.AuthRepository
import com.ssafy.eolssu.data.user.FirstRunRepository
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.HttpLessonApi
import com.ssafy.eolssu.data.user.LearnProgressRepository
import com.ssafy.eolssu.data.user.LessonApi
import com.ssafy.eolssu.data.user.OAuthAttempts
import com.ssafy.eolssu.data.user.LoginPreferences
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
import com.ssafy.eolssu.feature.song.SongDetailViewModel
import com.ssafy.eolssu.feature.song.SongListViewModel
import com.ssafy.eolssu.feature.song.edit.EditNewViewModel
import com.ssafy.eolssu.feature.song.edit.EditPickViewModel
import com.ssafy.eolssu.feature.song.edit.EditSource
import com.ssafy.eolssu.feature.song.edit.EditView
import com.ssafy.eolssu.feature.song.edit.HwangPitch
import com.ssafy.eolssu.feature.song.edit.ImportKind
import com.ssafy.eolssu.feature.song.edit.ImportResultViewModel
import com.ssafy.eolssu.feature.song.edit.SheetEditViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.data.practice.ApiWeakSectionRepository
import com.ssafy.eolssu.data.practice.WeakSectionRepository
import com.ssafy.eolssu.feature.history.CompareViewModel
import com.ssafy.eolssu.feature.practice.SectionLoopViewModel
import com.ssafy.eolssu.feature.practice.SectionSelectViewModel
import com.ssafy.eolssu.feature.result.SectionResultViewModel
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
    single { SheetPracticeRepository(get(), get(), get(), get(), get(), get(), get()) }
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
    single<NotePracticeCriteriaRepository> { HttpNotePracticeCriteriaRepository(get()) }

    viewModelOf(::SongListViewModel)
    viewModel { (songId: String, perf: PerformanceVisit?) -> SongDetailViewModel(songId, get(), perf) }
    // 녹음·마이크 권한은 플랫폼 모듈에 구현이 있을 때만 쓴다(현재 Android·iOS).
    // 여섯째 인자 direct: 곡 정보에서 바로 시작한 연주 평가(S15P31E102-705).
    viewModel { parameters ->
        val (songId: String, solo: Boolean, mine: Boolean, learn: Boolean, perf: PerformanceVisit?) = parameters
        val direct: Boolean = parameters[5]
        val personalPractice = if (mine) get<SheetPracticeRepository>() else null
        PracticeViewModel(songId, solo, get(), get(), PracticeTimeMeter(now = get()), get(), getOrNull(), getOrNull(),
            modelAudioRepository = personalPractice ?: get<ModelAudioRepository>(), audioPlayer = get(), mine = mine, learn = learn, member = get(), jangdanPlayer = getOrNull() ?: com.ssafy.eolssu.core.audio.UnavailableJangdanSamplePlayer(),
            notePractice = get(), loadPersonalSheet = personalPractice?.let { it::load }, perf = perf, personalAudio = personalPractice, direct = direct)
    }
    viewModel { parameters ->
        val (songId: String, bpm: Int, take: RecordedTake?, pendingSessionId: String?, section: SessionSection?) = parameters
        val initialSong: Song? = parameters[5]
        val perf: PerformanceVisit? = parameters[6]
        val uploads = getOrNull<RecordingFiles>()?.let { get<RecordingUploads>() }
        AnalysisViewModel(songId, bpm, take, pendingSessionId, get(), get(), uploads, get(), get(), section, initialSong, perf)
    }
    viewModel { (evaluationId: String, initialSong: Song?, perf: PerformanceVisit?) ->
        ResultViewModel(evaluationId, get(), get(), get(), get(), initialSong = initialSong, perf = perf, weakSections = get())
    }
    viewModel { (justSavedId: String?, perf: PerformanceVisit?) -> RecordListViewModel(justSavedId, get(), get(), get(), songs = get(), perf = perf) }
    viewModel { (recordId: String) -> RecordDetailViewModel(recordId, get(), get(), player = get(), now = get(), member = get(), loadInstruments = get<InstrumentCatalog>()::load) }
    viewModel { GrowthViewModel(get(), get(), get(), get()) }

    // 운지법 보기·기초부터 배우기
    viewModel { (instrument: GuideInstrument, blow: Blow) -> FingeringViewModel(instrument, blow, get(), get()) }
    viewModel { LearnViewModel(get(), get()) }
    // 단계 연습은 녹음기로 소리 크기만 잰다. 녹음기·마이크 권한이 없는 플랫폼은 스스로 확인만 한다.
    viewModel { (stage: Int) ->
        val catalog = get<InstrumentCatalog>()
        val criteria = get<NotePracticeCriteriaRepository>()
        LearnPracticeViewModel(stage, getOrNull(), getOrNull(), loadCriteria = { loadLearnCriteria(it, catalog::load, criteria) })
    }

    // 첫 실행·로그인·회원가입·비밀번호 찾기는 실제 회원 API를 사용한다.
    single { FirstRunRepository(get()) }
    single<LessonApi> { HttpLessonApi(get(), get()) }
    single { LearnProgressRepository(get(), get(), get()) }
    single<AuthRepository> { ApiAuthRepository(get(), getOrNull(), get(), get(), getOrNull<OAuthRoute>() ?: OAuthRedirect) }
    single { AccountDraft() }
    single { TermsRepository(get()) }
    viewModelOf(::ConsentViewModel)
    viewModel { (termId: String) -> TermsViewModel(termId, get()) }
    // 아이디 기억하기·자동 로그인 선택. 회원이 바뀌면 자동 로그인 표시를 지우므로 앱 시작 때 만든다(App).
    single { LoginPreferences(get(), getOrNull(), get()) }
    viewModel { LoginViewModel(get(), get(), get(), get()) }
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

    // 내 악보 목록과 편집 문서는 같은 인증 HTTP 클라이언트를 쓴다.
    single<MySheetRepository> { ApiMySheetRepository(get(), member = get(), deviceAudioStore = get()) }
    single<SheetDocumentRepository> { ApiSheetDocumentRepository(get(), member = get()) }
    single<SheetImportRepository> { ApiSheetImportRepository(get(), get()) }
    single<PublishedSheetCopyRepository> { ApiPublishedSheetCopyRepository(get(), get()) }
    single<SheetConversionRepository> { ApiSheetConversionRepository(get(), get()) }
    viewModel { (kind: ImportKind, hwang: HwangPitch) ->
        SheetImportViewModel(kind, hwang, get(),
            get<InstrumentCatalog>()::load, get<ReferenceCatalogRepository>()::jangdans, get())
    }
    viewModel { MySongsViewModel(get(), get(), get(), get(), get(), get()) }

    // 2차 취약 구간 연습 · 이전 연주 비교(S15P31E102-455). 서버 API가 없어 예시 데이터만 쓴다.
    single<WeakSectionRepository> { ApiWeakSectionRepository(get(), get(), get(), get(), get()) }
    viewModel { (sourceId: String, perf: PerformanceVisit?) -> SectionSelectViewModel(sourceId, get(), perf) }
    viewModel { (sourceId: String, section: ScoreSection, bpm: Int, repeats: Int, perf: PerformanceVisit?) ->
        SectionLoopViewModel(sourceId, section, bpm, repeats.takeIf { it > 0 }, get(), getOrNull(), getOrNull(), settingsStore = get(), perf = perf)
    }
    viewModel { (sessionId: String, perf: PerformanceVisit?) -> SectionResultViewModel(sessionId, get(), perf) }
    viewModel { (recordId: String, previousId: String?) -> CompareViewModel(recordId, get(), previousId) }
    // 악보 추가·편집(2차). 인식·저장 API를 붙이기 전이라 예시 악보로 화면 흐름만 다룬다.
    viewModelOf(::EditNewViewModel)
    viewModel { EditPickViewModel(get(), get(), documents = get(), copies = get(), member = get()) }
    viewModel { (kind: ImportKind, hwang: HwangPitch) -> ImportResultViewModel(kind, hwang) }
    // 내 악보 음원(SHT-12·13)은 서버 계약대로 부른다. 서버 ID가 없는 예시 악보는 저장소가 서버에 묻지 않는다.
    single<SheetAudioRepository> { ApiSheetAudioRepository(get(), member = get(), server = get()) }
    viewModel { (source: EditSource, startView: EditView) -> SheetEditViewModel(source, startView, get(), get(), now = get(), documents = get(), member = get(),
        songs = get(), pitchCatalog = get(), deviceAudioStore = get(), loadAudioInstruments = get<InstrumentCatalog>()::load) }
}
