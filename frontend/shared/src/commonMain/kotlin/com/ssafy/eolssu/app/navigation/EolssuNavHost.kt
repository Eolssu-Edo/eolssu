package com.ssafy.eolssu.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ssafy.eolssu.core.audio.RecordedTake
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.feature.history.GrowthRoute
import com.ssafy.eolssu.feature.history.RecordDetailRoute
import com.ssafy.eolssu.feature.history.RecordListRoute
import com.ssafy.eolssu.feature.guide.Blow
import com.ssafy.eolssu.feature.guide.FingeringRoute
import com.ssafy.eolssu.feature.guide.GuideInstrument
import com.ssafy.eolssu.feature.guide.ReadingPage
import com.ssafy.eolssu.feature.guide.ReadingScreen
import com.ssafy.eolssu.feature.guide.learn.LearnListRoute
import com.ssafy.eolssu.feature.guide.learn.LearnNavigation
import com.ssafy.eolssu.feature.guide.learn.LearnStageRoute
import com.ssafy.eolssu.feature.guide.learn.LearnViewModel
import com.ssafy.eolssu.feature.home.HomeFeature
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.user.FirstRunRepository
import com.ssafy.eolssu.feature.home.HomeRoute
import com.ssafy.eolssu.feature.practice.PracticeRoute
import com.ssafy.eolssu.feature.result.AnalysisRoute
import com.ssafy.eolssu.feature.result.ResultRoute
import com.ssafy.eolssu.feature.song.MySongsRoute
import com.ssafy.eolssu.feature.song.PdfPreviewRoute
import com.ssafy.eolssu.feature.song.PdfUploadRoute
import com.ssafy.eolssu.feature.song.SongDetailRoute
import com.ssafy.eolssu.feature.song.SongListRoute
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_mic_required
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject

/** 화면 경로. Web에서는 주소의 `#` 뒤에 그대로 보인다. 화면을 추가할 때 여기에 경로를 더한다. */
object EolssuDestination {
    const val HOME = "home"
    const val SONG_LIST = "songs"
    const val SONG_DETAIL = "songs/{songId}"

    const val PRACTICE = "practice/{songId}?solo={solo}&switched={switched}"

    /** [recording]·[key]는 연습 화면에서 마친 녹음, [session]은 앱을 다시 켜고 보내는 기기에 남은 녹음의 세션이다. */
    const val ANALYSIS = "analysis/{songId}/{bpm}?recording={recording}&key={key}&session={session}"
    const val RESULT = "result/{evaluationId}"

    const val RECORDS = "records?saved={saved}"
    const val RECORD_DETAIL = "records/{recordId}"
    const val GROWTH = "growth"

    fun songDetail(songId: String) = "songs/$songId"

    fun records(savedId: String? = null) = if (savedId == null) "records" else "records?saved=$savedId"

    fun recordDetail(recordId: String) = "records/$recordId"

    fun analysis(songId: String, bpm: Int, take: RecordedTake? = null, pendingSessionId: String? = null) =
        "analysis/$songId/$bpm?recording=${take?.path.orEmpty().encodeURLParameter()}&key=${take?.key.orEmpty()}" +
            "&session=${pendingSessionId.orEmpty()}"

    fun result(evaluationId: String) = "result/$evaluationId"

    /** [solo]: 평가 없이 혼자 연습. [switched]: 연습 모드에서 평가로 바꿔 들어옴. */
    fun practice(songId: String, solo: Boolean = false, switched: Boolean = false) = "practice/$songId?solo=$solo&switched=$switched"
    // 운지법 보기·정간보 쉽게 읽기·기초부터 배우기. 운지법 보기는 기초부터 배우기에서 악기·취법을, 정간보 쉽게 읽기는 펼칠 쪽을 넘겨받는다.
    const val GUIDE_FINGERING = "guide/fingering?instrument={instrument}&blow={blow}"
    const val GUIDE_READING = "guide/reading?page={page}"
    const val LEARN = "learn"
    const val LEARN_LIST = "learn/stages"
    const val LEARN_STAGE = "learn/stages/{stage}"

    fun guideFingering(instrument: GuideInstrument? = null, blow: Blow? = null) =
        "guide/fingering?instrument=${instrument?.id.orEmpty()}&blow=${blow?.name.orEmpty()}"

    fun guideReading(page: ReadingPage? = null) = "guide/reading?page=${page?.id.orEmpty()}"

    fun learnStage(stage: Int) = "learn/stages/$stage"

    // 내 악보 · PDF 올리기 (SONG-03 · UPLOAD-01 · UPLOAD-02)
    const val MY_SONGS = "my-songs"
    const val PDF_UPLOAD = "my-songs/pdf"
    const val PDF_PREVIEW = "my-songs/pdf/{uploadId}"

    fun pdfPreview(uploadId: String) = "my-songs/pdf/$uploadId"
}

/**
 * 앱의 화면 이동 그래프. 기능 사이의 이동은 이곳에서 콜백으로 연결한다.
 * 첫 실행을 마치지 않았으면 약관 동의([AuthDestination.CONSENT])에서 시작한다.
 * [onNavHostReady]는 Web 진입점이 브라우저 뒤로 가기·주소를 연결할 때 쓴다.
 */
@Composable
fun EolssuNavHost(
    onNavHostReady: suspend (NavController) -> Unit = {},
    navController: NavHostController = rememberNavController(),
    browserBackGuard: BrowserBackGuard? = null,
) {
    // 저장소 읽기는 즉시 끝나므로 첫 화면을 그리기 전에 시작 화면을 정한다(홈이 잠깐 보이지 않게).
    val firstRunRepository = koinInject<FirstRunRepository>()
    val start = remember { startDestination(firstRunRepository.isCompleted()) }
    val practiceSettings = koinInject<PracticeSettingsStore>()
    // 화면을 옮긴 뒤에도 남아야 하는 안내(예: 마이크를 허용하지 않아 연습 화면을 나옴). 각 화면의 안내 위에 겹쳐 띄운다.
    val appSnackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = start) {
            composable(EolssuDestination.HOME) { entry ->
                HomeRoute(
                    onStartEvaluation = { navController.navigate(EolssuDestination.SONG_LIST) },
                    onOpenFeature = { feature ->
                        val route = when (feature) {
                            HomeFeature.Fingering -> EolssuDestination.guideFingering()
                            HomeFeature.Reading -> EolssuDestination.guideReading()
                            HomeFeature.Learn -> EolssuDestination.LEARN
                            HomeFeature.SongSelect -> EolssuDestination.MY_SONGS
                        }
                        route?.let(navController::navigate)
                        route != null
                    },
                    onOpenMyPage = { navController.navigate(ProfileDestination.MY_PAGE) },
                    startMessage = entry.startNotice(),
                    onStartMessageShown = entry::clearStartNotice,
                )
            }
            composable(EolssuDestination.SONG_LIST) { entry ->
                val pickSongId by entry.savedStateHandle.getStateFlow<String?>(PICK_SONG_KEY, null).collectAsStateWithLifecycle()
                SongListRoute(
                    onBack = { navController.popBackStack() },
                    onOpenSong = { navController.navigate(EolssuDestination.songDetail(it)) },
                    onStartPractice = { navController.navigate(EolssuDestination.practice(it)) },
                    pickSongId = pickSongId,
                    onPickTaken = { entry.savedStateHandle[PICK_SONG_KEY] = null },
                )
            }
            composable(
                EolssuDestination.SONG_DETAIL,
                arguments = listOf(navArgument("songId") { type = NavType.StringType }),
            ) { entry ->
                val songId = entry.savedStateHandle.get<String>("songId").orEmpty()
                // 태블릿 가로·넓은 창은 곡 목록 + 곡 상세 두 칸(곡 목록 경로)으로 이 곡을 골라 보인다(세로·휴대폰은 곡 상세 한 화면).
                if (currentWindowLayout().widthClass == WindowWidthClass.Expanded) {
                    LaunchedEffect(Unit) { navController.showSongInList(songId) }
                }
                SongDetailRoute(
                    songId = songId,
                    onBack = { navController.popBackStack() },
                    onStartPractice = { navController.navigate(EolssuDestination.practice(it)) },
                )
            }
            composable(
                EolssuDestination.PRACTICE,
                arguments = listOf(
                    navArgument("songId") { type = NavType.StringType },
                    navArgument("solo") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                    navArgument("switched") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                val songId = entry.savedStateHandle.get<String>("songId").orEmpty()
                PracticeRoute(
                    songId = songId,
                    solo = entry.savedStateHandle.get<Boolean>("solo") ?: false,
                    showSwitchedNotice = entry.savedStateHandle.get<Boolean>("switched") ?: false,
                    onBack = { navController.popBackStack() },
                    onGoHome = { navController.popBackStack(EolssuDestination.HOME, inclusive = false) },
                    onSwitchToEval = {
                        navController.navigate(EolssuDestination.practice(songId, switched = true)) {
                            popUpTo(EolssuDestination.PRACTICE) { inclusive = true }
                        }
                    },
                    onEvaluate = { id, bpm, take -> navController.navigate(EolssuDestination.analysis(id, bpm, take)) },
                    // 연주 중에는 Web 브라우저 뒤로 가기도 화면의 뒤로 가기처럼 확인 창을 띄운다.
                    onConfirmBackChange = { browserBackGuard?.onBack = it },
                    onMicDenied = {
                        navController.popBackStack()
                        scope.launch {
                            appSnackbar.currentSnackbarData?.dismiss()
                            appSnackbar.showSnackbar(getString(Res.string.practice_mic_required))
                        }
                    },
                )
            }
            composable(
                EolssuDestination.ANALYSIS,
                arguments = listOf(
                    navArgument("songId") { type = NavType.StringType },
                    navArgument("bpm") { type = NavType.IntType },
                    navArgument("recording") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("key") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("session") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val recording = entry.savedStateHandle.get<String>("recording").orEmpty()
                val key = entry.savedStateHandle.get<String>("key").orEmpty()
                AnalysisRoute(
                    songId = entry.savedStateHandle.get<String>("songId").orEmpty(),
                    bpm = entry.savedStateHandle.get<Int>("bpm") ?: 0,
                    take = if (recording.isNotEmpty() && key.isNotEmpty()) RecordedTake(recording, key) else null,
                    pendingSessionId = entry.savedStateHandle.get<String>("session")?.takeIf { it.isNotEmpty() },
                    onResult = { id ->
                        navController.navigate(EolssuDestination.result(id)) {
                            popUpTo(EolssuDestination.ANALYSIS) { inclusive = true }
                        }
                    },
                    onBackToPractice = { navController.popBackStack() },
                )
            }
            composable(
                EolssuDestination.RESULT,
                arguments = listOf(navArgument("evaluationId") { type = NavType.StringType }),
            ) { entry ->
                ResultRoute(
                    evaluationId = entry.savedStateHandle.get<String>("evaluationId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onPracticeAgain = { navController.popBackStack() },
                    onOtherSong = { navController.popBackStack(EolssuDestination.SONG_LIST, inclusive = false) },
                    onHome = { navController.popBackStack(EolssuDestination.HOME, inclusive = false) },
                    onGoRecords = { navController.navigate(EolssuDestination.records(it)) },
                )
            }
            composable(
                EolssuDestination.RECORDS,
                arguments = listOf(
                    navArgument("saved") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                RecordListRoute(
                    justSavedId = entry.savedStateHandle.get<String>("saved"),
                    onBack = { navController.popBackStack() },
                    onOpenGrowth = { navController.navigate(EolssuDestination.GROWTH) },
                    onOpenRecord = { navController.navigate(EolssuDestination.recordDetail(it)) },
                )
            }
            composable(
                EolssuDestination.RECORD_DETAIL,
                arguments = listOf(navArgument("recordId") { type = NavType.StringType }),
            ) { entry ->
                val recordId = entry.savedStateHandle.get<String>("recordId").orEmpty()
                // 태블릿 가로·넓은 창은 연습 기록 + 기록 상세 두 칸으로 보이고 이 기록을 고른다.
                if (currentWindowLayout().widthClass == WindowWidthClass.Expanded) {
                    RecordListRoute(
                        justSavedId = null,
                        onBack = { navController.popBackStack() },
                        onOpenGrowth = { navController.navigate(EolssuDestination.GROWTH) },
                        onOpenRecord = { navController.navigate(EolssuDestination.recordDetail(it)) },
                        initialRecordId = recordId,
                    )
                } else {
                    RecordDetailRoute(
                        recordId = recordId,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(EolssuDestination.GROWTH) {
                GrowthRoute(
                    onBack = { navController.popBackStack() },
                    onOpenRecords = { navController.popBackStack() },
                    onOpenMissions = { navController.navigate(EolssuDestination.LEARN) },
                )
            }

            // 운지법 보기·정간보 쉽게 읽기·기초부터 배우기 (S15P31E102-150)
            composable(
                EolssuDestination.GUIDE_FINGERING,
                arguments = listOf(
                    navArgument("instrument") { type = NavType.StringType; defaultValue = "" },
                    navArgument("blow") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val blowName = entry.savedStateHandle.get<String>("blow")
                FingeringRoute(
                    onBack = { navController.popBackStack() },
                    instrument = GuideInstrument.fromId(entry.savedStateHandle.get<String>("instrument")) ?: GuideInstrument.Default,
                    blow = Blow.entries.firstOrNull { it.name == blowName } ?: Blow.Mid,
                )
            }
            composable(
                EolssuDestination.GUIDE_READING,
                arguments = listOf(navArgument("page") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                ReadingScreen(
                    onBack = { navController.popBackStack() },
                    initialPage = ReadingPage.fromId(entry.savedStateHandle.get<String>("page")),
                )
            }
            navigation(startDestination = EolssuDestination.LEARN_LIST, route = EolssuDestination.LEARN) {
                val learnNavigation = LearnNavigation(
                    onOpenFingering = { instrument, blow -> navController.navigate(EolssuDestination.guideFingering(instrument, blow)) },
                    onOpenReading = { page -> navController.navigate(EolssuDestination.guideReading(page)) },
                    onOpenSong = { navController.navigate(EolssuDestination.songDetail(it)) },
                    onOpenSongList = { navController.navigate(EolssuDestination.SONG_LIST) },
                )
                composable(EolssuDestination.LEARN_LIST) { entry ->
                    LearnListRoute(
                        viewModel = learnViewModel(navController, entry),
                        onBack = { navController.popBackStack() },
                        onOpenStage = { navController.navigate(EolssuDestination.learnStage(it)) },
                        navigation = learnNavigation,
                    )
                }
                composable(
                    EolssuDestination.LEARN_STAGE,
                    arguments = listOf(navArgument("stage") { type = NavType.IntType }),
                ) { entry ->
                    LearnStageRoute(
                        stageNumber = entry.savedStateHandle.get<Int>("stage") ?: 1,
                        viewModel = learnViewModel(navController, entry),
                        onBack = { navController.popBackStack() },
                        // 이전·다음 단계는 단계 상세를 바꿔 끼워, 뒤로 가기가 항상 단계 목록으로 간다.
                        onOpenStage = { stage ->
                            navController.navigate(EolssuDestination.learnStage(stage)) { popUpTo(EolssuDestination.LEARN_LIST) }
                        },
                        // 태블릿 가로가 되면 단계 목록(목록 + 상세 한 화면)으로 돌아간다. 주소로 바로 들어와 목록이 없으면 바꿔 끼운다.
                        onShowInList = {
                            if (!navController.popBackStack(EolssuDestination.LEARN_LIST, inclusive = false)) {
                                navController.navigate(EolssuDestination.LEARN_LIST) {
                                    popUpTo(EolssuDestination.LEARN_STAGE) { inclusive = true }
                                }
                            }
                        },
                        navigation = learnNavigation,
                    )
                }
            }
            profileGraph(navController)
            authGraph(
                navController,
                onMicAllowed = practiceSettings::grantMic,
                onFinished = { notice, provider -> navController.finishFirstRun(firstRunRepository, notice, provider) },
            )

            // 내 악보 · PDF 올리기: 연습곡 고르기 → (평가 없는 연습) / PDF 올리기 → 미리보기·저장 → 연습곡 고르기
            composable(EolssuDestination.MY_SONGS) {
                MySongsRoute(
                    onBack = { navController.popBackStack() },
                    onPractice = { navController.navigate(EolssuDestination.practice(it, solo = true)) },
                    onUploadPdf = { navController.navigate(EolssuDestination.PDF_UPLOAD) },
                )
            }
            composable(EolssuDestination.PDF_UPLOAD) {
                PdfUploadRoute(
                    onBack = { navController.popBackStack() },
                    onNext = { navController.navigate(EolssuDestination.pdfPreview(it)) },
                )
            }
            composable(
                EolssuDestination.PDF_PREVIEW,
                arguments = listOf(navArgument("uploadId") { type = NavType.StringType }),
            ) { entry ->
                PdfPreviewRoute(
                    uploadId = entry.savedStateHandle.get<String>("uploadId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack(EolssuDestination.MY_SONGS, inclusive = false) },
                )
            }
        }
        SnackbarHost(appSnackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars))
        PendingRecordingNotice(
            onSend = { navController.navigate(EolssuDestination.analysis(it.songId, it.bpm, pendingSessionId = it.sessionId)) },
        )
    }
    LaunchedEffect(navController) { onNavHostReady(navController) }
}

/** 곡 목록 경로에 두 칸 화면에서 직접 고른 것으로 둘 곡을 넘기는 키. */
private const val PICK_SONG_KEY = "pickSong"

/**
 * 곡 상세를 곡 목록 두 칸 화면으로 바꿔 [songId]를 고른다. 아래가 곡 목록이면 그리로 돌아가고,
 * 아니면(배우기 등에서 곡 상세를 바로 열었으면) 곡 상세를 곡 목록으로 바꿔 끼운다. 어느 쪽이든 뒤로 가기 한 번에 곡 목록을 연 화면으로 간다.
 */
private fun NavController.showSongInList(songId: String) {
    val previous = previousBackStackEntry
    if (previous?.destination?.route == EolssuDestination.SONG_LIST) {
        previous.savedStateHandle[PICK_SONG_KEY] = songId
        popBackStack()
    } else {
        navigate(EolssuDestination.SONG_LIST) { popUpTo(EolssuDestination.SONG_DETAIL) { inclusive = true } }
        currentBackStackEntry?.savedStateHandle?.set(PICK_SONG_KEY, songId)
    }
}

/** 기초부터 배우기의 단계 목록·상세가 함께 쓰는 ViewModel. 그래프 `learn`을 벗어나면 사라진다. */
@Composable
private fun learnViewModel(navController: NavController, entry: NavBackStackEntry): LearnViewModel {
    val graphEntry = remember(entry) { navController.getBackStackEntry(EolssuDestination.LEARN) }
    return koinViewModel(viewModelStoreOwner = graphEntry)
}
