package com.ssafy.eolssu.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ssafy.eolssu.feature.history.GrowthRoute
import com.ssafy.eolssu.feature.history.RecordDetailRoute
import com.ssafy.eolssu.feature.history.RecordListRoute
import com.ssafy.eolssu.feature.guide.Blow
import com.ssafy.eolssu.feature.guide.FingeringRoute
import com.ssafy.eolssu.feature.guide.GuideInstrument
import com.ssafy.eolssu.feature.guide.ReadingScreen
import com.ssafy.eolssu.feature.guide.learn.LearnListRoute
import com.ssafy.eolssu.feature.guide.learn.LearnStageRoute
import com.ssafy.eolssu.feature.guide.learn.LearnViewModel
import com.ssafy.eolssu.feature.home.HomeFeature
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
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject

/** 화면 경로. Web에서는 주소의 `#` 뒤에 그대로 보인다. 화면을 추가할 때 여기에 경로를 더한다. */
object EolssuDestination {
    const val HOME = "home"
    const val SONG_LIST = "songs"
    const val SONG_DETAIL = "songs/{songId}"

    const val PRACTICE = "practice/{songId}?solo={solo}&switched={switched}"

    const val ANALYSIS = "analysis/{songId}/{bpm}"
    const val RESULT = "result/{evaluationId}"

    const val RECORDS = "records?saved={saved}"
    const val RECORD_DETAIL = "records/{recordId}"
    const val GROWTH = "growth"

    fun songDetail(songId: String) = "songs/$songId"

    fun records(savedId: String? = null) = if (savedId == null) "records" else "records?saved=$savedId"

    fun recordDetail(recordId: String) = "records/$recordId"

    fun analysis(songId: String, bpm: Int) = "analysis/$songId/$bpm"

    fun result(evaluationId: String) = "result/$evaluationId"

    /** [solo]: 평가 없이 혼자 연습. [switched]: 연습 모드에서 평가로 바꿔 들어옴. */
    fun practice(songId: String, solo: Boolean = false, switched: Boolean = false) = "practice/$songId?solo=$solo&switched=$switched"
    // 운지법 보기·정간보 쉽게 읽기·기초부터 배우기. 운지법 보기는 기초부터 배우기에서 악기·취법을 넘겨받는다.
    const val GUIDE_FINGERING = "guide/fingering?instrument={instrument}&blow={blow}"
    const val GUIDE_READING = "guide/reading"
    const val LEARN = "learn"
    const val LEARN_LIST = "learn/stages"
    const val LEARN_STAGE = "learn/stages/{stage}"

    fun guideFingering(instrument: GuideInstrument? = null, blow: Blow? = null) =
        "guide/fingering?instrument=${instrument?.id.orEmpty()}&blow=${blow?.name.orEmpty()}"

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
) {
    // 저장소 읽기는 즉시 끝나므로 첫 화면을 그리기 전에 시작 화면을 정한다(홈이 잠깐 보이지 않게).
    val firstRunRepository = koinInject<FirstRunRepository>()
    val start = remember { startDestination(firstRunRepository.isCompleted()) }
    NavHost(navController = navController, startDestination = start) {
        composable(EolssuDestination.HOME) { entry ->
            HomeRoute(
                onStartEvaluation = { navController.navigate(EolssuDestination.SONG_LIST) },
                onOpenFeature = { feature ->
                    val route = when (feature) {
                        HomeFeature.Fingering -> EolssuDestination.guideFingering()
                        HomeFeature.Reading -> EolssuDestination.GUIDE_READING
                        HomeFeature.Learn -> EolssuDestination.LEARN
                        HomeFeature.SongSelect -> EolssuDestination.MY_SONGS
                    }
                    route?.let(navController::navigate)
                    route != null
                },
                startMessage = entry.startNotice()?.message,
                onStartMessageShown = entry::clearStartNotice,
            )
        }
        composable(EolssuDestination.SONG_LIST) {
            SongListRoute(
                onBack = { navController.popBackStack() },
                onOpenSong = { navController.navigate(EolssuDestination.songDetail(it)) },
                onStartPractice = { navController.navigate(EolssuDestination.practice(it)) },
            )
        }
        composable(
            EolssuDestination.SONG_DETAIL,
            arguments = listOf(navArgument("songId") { type = NavType.StringType }),
        ) { entry ->
            SongDetailRoute(
                songId = entry.savedStateHandle.get<String>("songId").orEmpty(),
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
                onEvaluate = { id, bpm -> navController.navigate(EolssuDestination.analysis(id, bpm)) },
            )
        }
        composable(
            EolssuDestination.ANALYSIS,
            arguments = listOf(navArgument("songId") { type = NavType.StringType }, navArgument("bpm") { type = NavType.IntType }),
        ) { entry ->
            AnalysisRoute(
                songId = entry.savedStateHandle.get<String>("songId").orEmpty(),
                bpm = entry.savedStateHandle.get<Int>("bpm") ?: 0,
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
            RecordDetailRoute(
                recordId = entry.savedStateHandle.get<String>("recordId").orEmpty(),
                onBack = { navController.popBackStack() },
            )
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
        composable(EolssuDestination.GUIDE_READING) {
            ReadingScreen(onBack = { navController.popBackStack() })
        }
        navigation(startDestination = EolssuDestination.LEARN_LIST, route = EolssuDestination.LEARN) {
            composable(EolssuDestination.LEARN_LIST) { entry ->
                LearnListRoute(
                    viewModel = learnViewModel(navController, entry),
                    onBack = { navController.popBackStack() },
                    onOpenStage = { navController.navigate(EolssuDestination.learnStage(it)) },
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
                    onOpenFingering = { instrument, blow -> navController.navigate(EolssuDestination.guideFingering(instrument, blow)) },
                    onOpenReading = { navController.navigate(EolssuDestination.GUIDE_READING) },
                    onOpenSong = { navController.navigate(EolssuDestination.songDetail(it)) },
                    onOpenSongList = { navController.navigate(EolssuDestination.SONG_LIST) },
                )
            }
        }
        authGraph(navController, onFinished = { welcome -> navController.finishFirstRun(firstRunRepository, welcome) })

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
    LaunchedEffect(navController) { onNavHostReady(navController) }
}

/** 기초부터 배우기의 단계 목록·상세가 함께 쓰는 ViewModel. 그래프 `learn`을 벗어나면 사라진다. */
@Composable
private fun learnViewModel(navController: NavController, entry: NavBackStackEntry): LearnViewModel {
    val graphEntry = remember(entry) { navController.getBackStackEntry(EolssuDestination.LEARN) }
    return koinViewModel(viewModelStoreOwner = graphEntry)
}
