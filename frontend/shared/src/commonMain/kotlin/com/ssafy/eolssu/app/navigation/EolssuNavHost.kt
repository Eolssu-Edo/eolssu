package com.ssafy.eolssu.app.navigation

import com.ssafy.eolssu.core.diagnostics.PerfTarget
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import com.ssafy.eolssu.core.ui.component.EolssuSnackbarHost
import com.ssafy.eolssu.core.ui.component.SheetCompletionBannerHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.inSession
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.practice.SessionSection
import com.ssafy.eolssu.core.audio.RecordedTake
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.feature.history.GrowthRoute
import com.ssafy.eolssu.feature.history.RecordDetailRoute
import com.ssafy.eolssu.feature.history.RecordListRoute
import com.ssafy.eolssu.core.model.fingering.Blow
import com.ssafy.eolssu.feature.guide.FingeringBasicAudio
import com.ssafy.eolssu.feature.guide.rememberBasicNotePlayer
import com.ssafy.eolssu.feature.guide.FingeringRoute
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.feature.guide.ReadingPage
import com.ssafy.eolssu.feature.guide.ReadingRoute
import com.ssafy.eolssu.feature.guide.learn.LearnListRoute
import com.ssafy.eolssu.feature.guide.learn.LearnNavigation
import com.ssafy.eolssu.feature.guide.learn.LearnStageRoute
import com.ssafy.eolssu.feature.guide.learn.LearnViewModel
import com.ssafy.eolssu.feature.home.HomeFeature
import com.ssafy.eolssu.data.user.FirstRunRepository
import com.ssafy.eolssu.feature.home.HomeRoute
import com.ssafy.eolssu.feature.practice.PracticeRoute
import com.ssafy.eolssu.feature.practice.component.StepNoteAudio
import com.ssafy.eolssu.feature.practice.component.StepNotePlayMs
import com.ssafy.eolssu.feature.result.AnalysisExit
import com.ssafy.eolssu.feature.result.AnalysisRoute
import com.ssafy.eolssu.feature.result.ResultRoute
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.feature.song.MySongsRoute
import com.ssafy.eolssu.feature.song.SongDetailRoute
import com.ssafy.eolssu.feature.song.SongListRoute
import eolssu.shared.generated.resources.Res
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import com.ssafy.eolssu.data.user.UserRepository
import org.koin.compose.koinInject

/** 화면 경로. Web에서는 주소의 `#` 뒤에 그대로 보인다. 화면을 추가할 때 여기에 경로를 더한다. */
object EolssuDestination {
    const val HOME = "home"
    const val SONG_LIST = "songs"
    const val SONG_DETAIL = "songs/{songId}"

    /** [title]은 내 악보 연습에서 곡 제목 대신 보일 악보 제목(비우면 곡 제목). */
    const val PRACTICE = "practice/{songId}?solo={solo}&switched={switched}&mine={mine}&learn={learn}&direct={direct}&title={title}" +
        "&weak={weak}&sc={sc}&sr={sr}&ec={ec}&er={er}"

    /**
     * [recording]·[key]는 연습 화면에서 마친 녹음, [session]은 앱을 다시 켜고 보내는 기기에 남은 녹음의 세션이다.
     * [start]·[end]·[parent]는 구간 반복의 재평가 녹음(음표 번호 범위와 기준 세션)이고, 곡 전체 연주면 비어 있다.
     */
    const val ANALYSIS = "analysis/{songId}/{bpm}?recording={recording}&key={key}&session={session}&start={start}&end={end}&parent={parent}&scoreVersion={scoreVersion}"
    const val RESULT = "result/{evaluationId}"

    const val RECORDS = "records?saved={saved}"
    const val RECORD_RESULT = "records/{recordId}/evaluation"
    const val GROWTH = "growth"

    fun songDetail(songId: String) = "songs/$songId"

    fun records(savedId: String? = null) = if (savedId == null) "records" else "records?saved=$savedId"

    fun recordResult(recordId: String) = "records/$recordId/evaluation"

    fun analysis(songId: String, bpm: Int, take: RecordedTake? = null, pendingSessionId: String? = null, section: SessionSection? = null) =
        "analysis/$songId/$bpm?recording=${take?.path.orEmpty().encodeURLParameter()}&key=${take?.key.orEmpty()}" +
            "&session=${pendingSessionId.orEmpty()}&scoreVersion=${take?.scoreVersion ?: -1}" +
            (section?.let { "&start=${it.startNoteIdx}&end=${it.endNoteIdx}&parent=${it.parentSessionId}" } ?: "")

    fun result(evaluationId: String) = "result/$evaluationId"

    /**
     * [solo]: 평가 없이 혼자 연습. [switched]: 연습 모드에서 평가로 바꿔 들어옴. [mine]: 내 악보 연습(평가 버튼 없음, solo와 함께).
     * [learn]: 기초부터 배우기 8단계의 곡 연습(평가 버튼 없음, 운지법과 함께 보기로 시작, solo와 함께).
     * [direct]: 대기 상태 없이 바로 평가를 시작하고, 준비로 돌아갈 일이 생기면 들어온 화면으로 돌아간다.
     * 곡 정보의 '연주 평가 시작'(S15P31E102-705)과 혼자 연습에서 바꾼 평가([switched]와 함께, S15P31E102-739)가 쓴다.
     */
    fun practice(
        songId: String, solo: Boolean = false, switched: Boolean = false, mine: Boolean = false, title: String? = null, learn: Boolean = false, direct: Boolean = false,
        weak: String? = null, section: ScoreSection? = null,
    ) =
        "practice/$songId?solo=${solo || mine || learn}&switched=$switched&mine=$mine&learn=$learn&direct=$direct&title=${title.orEmpty().encodeURLParameter()}" +
            "&weak=${weak.orEmpty()}" + (section?.let { "&sc=${it.start.column}&sr=${it.start.row}&ec=${it.end.column}&er=${it.end.row}" } ?: "")

    /**
     * 구간 고르기의 ‘구간 연습 시작’ → 연습 화면을 고른 구간의 구간 연습으로 연다(S15P31E102-794).
     * [weak]는 바탕 연주(연습 세션 ID)로, 구간 연습의 ‘구간 재평가 시작’이 쓴다.
     */
    fun sectionPractice(songId: String, weak: String, section: ScoreSection) =
        practice(songId, solo = true, weak = weak, section = section)
    // 운지법 보기·정간보 쉽게 읽기·기초부터 배우기. 운지법 보기는 기초부터 배우기에서 악기·취법을, 정간보 쉽게 읽기는 펼칠 쪽을 넘겨받는다.
    const val GUIDE_FINGERING = "guide/fingering?instrument={instrument}&blow={blow}"
    const val GUIDE_READING = "guide/reading?page={page}"
    const val LEARN = "learn"
    const val LEARN_LIST = "learn/stages"
    const val LEARN_STAGE = "learn/stages/{stage}?instrument={instrument}"

    fun guideFingering(instrument: GuideInstrument? = null, blow: Blow? = null) =
        "guide/fingering?instrument=${instrument?.id.orEmpty()}&blow=${blow?.name.orEmpty()}"

    fun guideReading(page: ReadingPage? = null) = "guide/reading?page=${page?.id.orEmpty()}"

    fun learnStage(stage: Int, instrument: GuideInstrument? = null) =
        "learn/stages/$stage" + (instrument?.let { "?instrument=${it.id}" } ?: "")


    // 내 악보 (SONG-03)
    const val MY_SONGS = "my-songs?sheets={sheets}"

    /** 연습곡 고르기. [sheets]가 true면 내 악보 탭부터 보인다(홈 '악보 추가·편집하기'는 악보 추가로 바로 간다). */
    fun mySongs(sheets: Boolean = false) = if (sheets) "my-songs?sheets=true" else "my-songs"
}

/**
 * 앱의 화면 이동 그래프. 기능 사이의 이동은 이곳에서 콜백으로 연결한다.
 * 로그인한 회원이 없으면(처음 켬·로그아웃 뒤·세션 만료) 로그인에서 시작한다. 약관 동의는 회원가입 흐름에서, 마이크 권한은 녹음을 시작할 때 받는다.
 * [onNavHostReady]는 Web 진입점이 브라우저 뒤로 가기·주소를 연결할 때 쓴다.
 */
@Composable
fun EolssuNavHost(
    onNavHostReady: suspend (NavController) -> Unit = {},
    navController: NavHostController = rememberNavController(),
    browserBackGuard: BrowserBackGuard? = null,
    sessionExpired: Boolean = false,
) {
    // 저장소 읽기는 즉시 끝나므로 첫 화면을 그리기 전에 시작 화면을 정한다(홈이 잠깐 보이지 않게).
    val firstRunRepository = koinInject<FirstRunRepository>()
    val member = koinInject<SignedInMember>()
    val users = koinInject<UserRepository>()
    // 앱을 켤 때의 로그인 여부. 로그아웃 뒤 다시 켜면 회원이 없어 만료 안내 없이 로그인에서 시작한다.
    val firstRunCompleted = remember { firstRunRepository.isCompleted() }
    val signedInAtStart = remember { member.id != null }
    val start = remember(sessionExpired) {
        startDestination(firstRunCompleted, sessionExpired, signedInAtStart)
    }
    val performance = remember(navController) { PerformanceNavigation(navController) }
    val scope = rememberCoroutineScope()
    val recentPrefetch = remember(scope) { RecentPracticePrefetch(scope) }
    DisposableEffect(navController, recentPrefetch) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            com.ssafy.eolssu.core.diagnostics.UserActionLog.screen(destination.route)
            recentPrefetch.onDestinationChanged(destination.route)
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
            recentPrefetch.cancel()
        }
    }
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            if (entry.destination.route !in measuredDestinations) performance.leave()
        }
    }
    // 녹음 키로 분석에 넘기고 세션 ID로 결과에 넘기는 같은 화면 흐름의 곡 스냅샷.
    var evaluationSong by remember { mutableStateOf<Pair<String, Song>?>(null) }
    LaunchedEffect(member, navController, recentPrefetch) {
        var previous = member.session.value
        member.session.collect { current ->
            if (current != previous) {
                recentPrefetch.cancel()
                evaluationSong = null
                performance.reset()
            }
            if (previous.memberId != null && current != previous) {
                navController.navigate(if (current.memberId == null) AuthDestination.LOGIN else EolssuDestination.HOME) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
            previous = current
        }
    }
    // 화면을 옮긴 뒤에도 남아야 하는 안내(예: 마이크를 허용하지 않아 연습 화면을 나옴). 각 화면의 안내 위에 겹쳐 띄운다.
    val appSnackbar = remember { SnackbarHostState() }
    val completionBanner = remember { SnackbarHostState() }
    val sheetAudio = koinInject<com.ssafy.eolssu.data.song.NotifyingSheetAudioRepository>()
    val sheetPractice = koinInject<com.ssafy.eolssu.data.song.SheetPracticeRepository>()
    SheetAudioCompletionEffect(sheetAudio, member, completionBanner) { sheetId ->
        navController.openCompletedSheet(sheetId, sheetAudioCompletionTarget(sheetPractice.personalAudioPlayable))
    }
    val sheetConversions = koinInject<com.ssafy.eolssu.data.song.NotifyingSheetConversionRepository>()
    SheetConversionCompletionEffect(sheetConversions, member, completionBanner) { sheetId ->
        navController.openCompletedSheet(sheetId, SheetCompletionTarget.Editor)
    }
    Box(Modifier.fillMaxSize()) {
        // 화면 전환 모션을 쓰지 않는다. 기본 전환(0.7초 페이드)이 도는 동안 나가는 화면이 남아 그 위의 확인 창도 늦게 사라졌고,
        // Android 제스처 뒤로 가기(predictive)는 따로 정한 줌아웃(scaleOut 0.7)을 써서 그것도 함께 끈다.
        NavHost(
            navController = navController,
            startDestination = start,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
            predictivePopEnterTransition = { EnterTransition.None },
            predictivePopExitTransition = { ExitTransition.None },
        ) {
            composable(EolssuDestination.HOME) { entry ->
                // 홈 아래에 쌓인 화면이 없을 때만 Android 뒤로 가기 두 번으로 앱을 닫는다. 홈으로 가는 이동은 모두 쌓인 화면을 비운다.
                val homeIsRoot = remember(entry) { navController.previousBackStackEntry == null }
                val profile by users.profile.collectAsStateWithLifecycle()
                val records = koinInject<PracticeRecordRepository>()
                val now = koinInject<() -> Long>()
                val session = member.session.collectAsStateWithLifecycle().value
                LaunchedEffect(entry, session, records) {
                    if (session.memberId == null || !records.available || navController.currentDestination?.route != EolssuDestination.HOME) {
                        return@LaunchedEffect
                    }
                    recentPrefetch.start {
                        member.inSession {
                            if (member.session.value == session) {
                                records.prefetchRecentPractice(KoreanDateTime.fromEpochMillis(now()))
                            }
                        }
                    }
                }
                HomeRoute(
                    onStartEvaluation = { navController.navigate(EolssuDestination.SONG_LIST) },
                    onOpenFeature = { feature ->
                        val route = when (feature) {
                            HomeFeature.Fingering -> EolssuDestination.guideFingering()
                            HomeFeature.Reading -> EolssuDestination.guideReading()
                            HomeFeature.Learn -> EolssuDestination.LEARN
                            HomeFeature.SongSelect -> EolssuDestination.mySongs()
                            HomeFeature.EditSheets -> SheetEditDestination.new()
                            HomeFeature.Records -> EolssuDestination.records()
                        }
                        route?.let {
                            if (feature == HomeFeature.Records) performance.move(null, PerfTarget.RECORDS) { navController.navigate(it) }
                            else navController.navigate(it)
                        }
                        route != null
                    },
                    onOpenMyPage = { navController.navigate(ProfileDestination.MY_PAGE) },
                    profileImage = profile.profileImage,
                    startMessage = entry.startNotice(),
                    onStartMessageShown = entry::clearStartNotice,
                    exitOnDoubleBack = homeIsRoot,
                )
            }
            composable(EolssuDestination.SONG_LIST) { entry ->
                val pickSongId by entry.savedStateHandle.getStateFlow<String?>(PICK_SONG_KEY, null).collectAsStateWithLifecycle()
                SongListRoute(
                    onBack = { navController.popBackStackFrom(entry) },
                    onOpenSong = { id -> performance.move(null, PerfTarget.SONG_DETAIL) { navController.navigate(EolssuDestination.songDetail(id)) } },
                    onStartPractice = { id -> performance.move(null, PerfTarget.PRACTICE) { navController.navigate(EolssuDestination.practice(id, direct = true)) } },
                    pickSongId = pickSongId,
                    onPickTaken = { entry.savedStateHandle[PICK_SONG_KEY] = null },
                    onSessionExpired = {
                        navController.navigateWithNotice(AuthDestination.LOGIN, StartNotice.SessionExpired) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    },
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
                val perf = performance.visit(entry, PerfTarget.SONG_DETAIL)
                SongDetailRoute(
                    perf = perf,
                    songId = songId,
                    onBack = { navController.popBackStackFrom(entry) },
                    onStartPractice = { id -> performance.move(perf, PerfTarget.PRACTICE) { navController.navigate(EolssuDestination.practice(id, direct = true)) } },
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
                    navArgument("mine") { type = NavType.BoolType; defaultValue = false },
                    navArgument("learn") { type = NavType.BoolType; defaultValue = false },
                    navArgument("direct") { type = NavType.BoolType; defaultValue = false },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    navArgument("weak") { type = NavType.StringType; defaultValue = "" },
                ) + listOf("sc", "sr", "ec", "er").map { name -> navArgument(name) { type = NavType.IntType; defaultValue = -1 } },
            ) { entry ->
                val songId = entry.savedStateHandle.get<String>("songId").orEmpty()
                val weakSource = entry.savedStateHandle.get<String>("weak").orEmpty().ifEmpty { null }
                val initialSection = run {
                    fun arg(name: String) = entry.savedStateHandle.get<Int>(name) ?: -1
                    if (arg("sc") < 0 || arg("ec") < 0) null
                    else ScoreSection.between(ScorePosition(arg("sc"), arg("sr").coerceAtLeast(0)), ScorePosition(arg("ec"), arg("er").coerceAtLeast(0)))
                }
                val mine = entry.savedStateHandle.get<Boolean>("mine") ?: false
                val perf = performance.visit(entry, PerfTarget.PRACTICE)
                PracticeRoute(
                    perf = perf,
                    songId = songId,
                    fingeringAudio = { instrument, note, onPlaying -> FingeringBasicAudio(instrument, note, onPlaying) },
                    stepNoteAudio = { instrument -> rememberBasicNotePlayer(instrument, StepNotePlayMs).let { StepNoteAudio(it.play, it.playback) } },
                    solo = entry.savedStateHandle.get<Boolean>("solo") ?: false,
                    onBack = { navController.popBackStackFrom(entry) },
                    onGoHome = { navController.popBackStack(EolssuDestination.HOME, inclusive = false) },
                    // 혼자 연습 위에 평가 화면을 쌓아, 평가 화면에서 뒤로 가면 쓰던 혼자 연습 화면(곡·악기·상태 그대로)으로 돌아온다.
                    // 이 화면이 맨 위일 때만 열어 빠른 연속 누름에 평가 화면이 겹쳐 쌓이지 않게 한다.
                    // 곡 정보에서 시작한 평가처럼 대기 상태를 두지 않아(direct), 뒤로 가면 '연주 평가' 대기 화면을 거치지 않고 바로 연습으로 간다(S15P31E102-739).
                    onSwitchToEval = {
                        if (navController.currentBackStackEntry?.id == entry.id) {
                            navController.navigate(EolssuDestination.practice(songId, switched = true, direct = true))
                        }
                    },
                    onEvaluate = { id, bpm, take, song ->
                        evaluationSong = if (take != null && song != null) take.key to song else null
                        performance.move(perf, PerfTarget.ANALYSIS) { navController.navigate(EolssuDestination.analysis(id, bpm, take)) }
                    },
                    // 연주 중에는 Web 브라우저 뒤로 가기도 화면의 뒤로 가기처럼 확인 창을 띄운다.
                    onConfirmBackChange = { browserBackGuard?.onBack = it },
                    // 마이크 거부는 연습 화면 안에서 안내한다(보드 Dialog-PermDenied·PermBlocked, S15P31E102-357).
                    mine = mine,
                    learn = entry.savedStateHandle.get<Boolean>("learn") ?: false,
                    sheetTitle = entry.savedStateHandle.get<String>("title"),
                    direct = entry.savedStateHandle.get<Boolean>("direct") ?: false,
                    initialSection = initialSection,
                    onSectionEvaluate = { section ->
                        weakSource?.let { navController.navigate(WeakSectionDestination.evaluate(it, section)) }
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
                    navArgument("start") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("end") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("parent") { type = NavType.StringType; defaultValue = "" },
                    navArgument("scoreVersion") { type = NavType.IntType; defaultValue = -1 },
                ),
            ) { entry ->
                val recording = entry.savedStateHandle.get<String>("recording").orEmpty()
                val key = entry.savedStateHandle.get<String>("key").orEmpty()
                val section = entry.sessionSection()
                val perf = performance.visit(entry, PerfTarget.ANALYSIS)
                AnalysisRoute(
                    perf = perf,
                    songId = entry.savedStateHandle.get<String>("songId").orEmpty(),
                    bpm = entry.savedStateHandle.get<Int>("bpm") ?: 0,
                    take = if (recording.isNotEmpty() && key.isNotEmpty()) RecordedTake(recording, key,
                        entry.savedStateHandle.get<Int>("scoreVersion")?.takeIf { it > 0 }) else null,
                    pendingSessionId = entry.savedStateHandle.get<String>("session")?.takeIf { it.isNotEmpty() },
                    section = section,
                    initialSong = evaluationSong?.takeIf { it.first == key }?.second,
                    // 구간 재평가는 구간 재평가 결과로, 곡 전체 연주는 평가 결과로 간다.
                    onResult = { id, song ->
                        evaluationSong = if (section == null && song != null) id to song else null
                        val route = section?.let { WeakSectionDestination.sectionResult(it.parentSessionId, id) } ?: EolssuDestination.result(id)
                        performance.move(perf, if (section == null) PerfTarget.RESULT else PerfTarget.SECTION_RESULT) {
                            navController.navigate(route) {
                                popUpTo(EolssuDestination.ANALYSIS) { inclusive = true }
                            }
                        }
                    },
                    // 대기 상태 없이 시작한 평가면 준비 상태의 연주 평가 화면을 건너 들어온 화면(곡 정보 또는 혼자 연습)으로 돌아간다.
                    onBackToPractice = { navController.popBackStackFrom(entry, pastDirectPractice = true) },
                    onExit = { exit ->
                        when (exit) {
                            AnalysisExit.SessionGone -> if (!navController.popBackStack(EolssuDestination.SONG_LIST, inclusive = false)) {
                                navController.navigate(EolssuDestination.SONG_LIST) { popUpTo(EolssuDestination.HOME) }
                            }
                            AnalysisExit.NeedsLogin -> navController.navigate(AuthDestination.LOGIN)
                        }
                    },
                )
            }
            composable(
                EolssuDestination.RESULT,
                arguments = listOf(navArgument("evaluationId") { type = NavType.StringType }),
            ) { entry ->
                val evaluationId = entry.savedStateHandle.get<String>("evaluationId").orEmpty()
                val perf = performance.visit(entry, PerfTarget.RESULT)
                ResultRoute(
                    perf = perf,
                    evaluationId = evaluationId,
                    initialSong = evaluationSong?.takeIf { it.first == evaluationId }?.second,
                    onBack = { navController.popBackStackFrom(entry, pastDirectPractice = true) },
                    // 저장하지 않은 결과면 Web 브라우저 뒤로 가기도 ‘연주 결과를 버릴까요?’를 띄운다(S15P31E102-1214).
                    onConfirmBackChange = { browserBackGuard?.onBack = it },
                    onPracticeAgain = { songId ->
                        if (navController.currentBackStackEntry?.id == entry.id) {
                            val previous = navController.previousBackStackEntry
                            // '다시 하기'는 평가를 시작한 화면으로 돌아가 다시 '연주 평가 시작'을 누르게 한다.
                            // 곡 정보에서 시작했으면 곡 정보(S15P31E102-705), 혼자 연습에서 바꿨으면 그 연습 화면(S15P31E102-739)이다.
                            if (previous?.destination?.route == EolssuDestination.PRACTICE &&
                                previous.savedStateHandle.get<String>("songId") == songId
                            ) {
                                navController.popBackStackFrom(entry, pastDirectPractice = true)
                            } else {
                                navController.navigate(EolssuDestination.songDetail(songId)) {
                                    popUpTo(EolssuDestination.RESULT) { inclusive = true }
                                }
                            }
                        }
                    },
                    onOtherSong = { navController.popBackStack(EolssuDestination.SONG_LIST, inclusive = false) },
                    onHome = { navController.popBackStack(EolssuDestination.HOME, inclusive = false) },
                    // 저장 완료 시트의 ‘기록으로 이동’: 방금 저장한 기록 상세. 뒤로 가면 이 결과 화면으로 돌아온다.
                    onOpenRecord = { id ->
                        if (navController.currentBackStackEntry?.id == entry.id) {
                            entry.savedStateHandle[OPENED_RECORD_KEY] = id
                            performance.move(perf, PerfTarget.RECORD_DETAIL) { navController.navigate(EolssuDestination.recordResult(id)) }
                        }
                    },
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
                val perf = performance.visit(entry, PerfTarget.RECORDS)
                RecordListRoute(
                    perf = perf,
                    justSavedId = entry.savedStateHandle.get<String>("saved"),
                    onBack = { navController.popBackStackFrom(entry) },
                    onOpenGrowth = { navController.navigate(EolssuDestination.GROWTH) },
                    onOpenRecord = { id -> performance.move(perf, PerfTarget.RECORD_DETAIL) { navController.navigate(EolssuDestination.recordResult(id)) } },
                    onCompare = { id, previous -> navController.navigate(WeakSectionDestination.compare(id, previous)) },
                    onSelectSection = { id -> performance.move(perf, PerfTarget.WEAK_SELECT) { navController.navigate(WeakSectionDestination.select(id)) } },
                    onPracticeSection = { id, _ -> performance.move(perf, PerfTarget.WEAK_SELECT) { navController.navigate(WeakSectionDestination.select(id)) } },
                    onStartPractice = { navController.navigate(EolssuDestination.SONG_LIST) },
                )
            }
            composable(
                EolssuDestination.RECORD_RESULT,
                arguments = listOf(navArgument("recordId") { type = NavType.StringType }),
            ) { entry ->
                val perf = performance.visit(entry, PerfTarget.RECORD_DETAIL)
                val recordId = entry.savedStateHandle.get<String>("recordId").orEmpty()
                // 태블릿 가로·넓은 창은 연습 기록 + 기록 상세 두 칸으로 보이고 이 기록을 고른다.
                if (currentWindowLayout().widthClass == WindowWidthClass.Expanded) {
                    RecordListRoute(
                        perf = perf,
                        justSavedId = null,
                        onBack = { navController.popBackStackFrom(entry) },
                        onOpenGrowth = { navController.navigate(EolssuDestination.GROWTH) },
                        onOpenRecord = { navController.navigate(EolssuDestination.recordResult(it)) },
                        initialRecordId = recordId,
                        onRecordDeleted = { id -> navController.leaveDeletedRecordResult(entry, id) },
                        onCompare = { id, previous -> navController.navigate(WeakSectionDestination.compare(id, previous)) },
                        onSelectSection = { navController.navigate(WeakSectionDestination.select(it)) },
                        onPracticeSection = { id, _ -> navController.navigate(WeakSectionDestination.select(id)) },
                        onStartPractice = { navController.navigate(EolssuDestination.SONG_LIST) },
                    )
                } else {
                    RecordDetailRoute(
                        recordId = recordId,
                        onDeleted = {
                            if (!navController.leaveDeletedRecordResult(entry, recordId)) navController.popBackStackFrom(entry)
                        },
                        onBack = { navController.popBackStackFrom(entry) },
                        onCompare = { id, previous -> navController.navigate(WeakSectionDestination.compare(id, previous)) },
                        onSelectSection = { navController.navigate(WeakSectionDestination.select(it)) },
                        onPracticeSection = { id, _ -> navController.navigate(WeakSectionDestination.select(id)) },
                    )
                }
            }
            composable(EolssuDestination.GROWTH) { entry ->
                GrowthRoute(onBack = { navController.popBackStackFrom(entry) })
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
                    onBack = { navController.popBackStackFrom(entry) },
                    instrument = GuideInstrument.fromId(entry.savedStateHandle.get<String>("instrument")) ?: GuideInstrument.Default,
                    blow = Blow.entries.firstOrNull { it.name == blowName } ?: Blow.Mid,
                )
            }
            composable(
                EolssuDestination.GUIDE_READING,
                arguments = listOf(navArgument("page") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                ReadingRoute(
                    onBack = { navController.popBackStackFrom(entry) },
                    initialPage = ReadingPage.fromId(entry.savedStateHandle.get<String>("page")),
                )
            }
            navigation(startDestination = EolssuDestination.LEARN_LIST, route = EolssuDestination.LEARN) {
                val learnNavigation = LearnNavigation(
                    onOpenFingering = { instrument, blow -> navController.navigate(EolssuDestination.guideFingering(instrument, blow)) },
                    onOpenReading = { page -> navController.navigate(EolssuDestination.guideReading(page)) },
                    // 마지막 단계(7단계)를 마치면 단계 상세를 닫고 단계 목록으로 돌아가 결승 축하를 본다(S15P31E102-688).
                    // 마이페이지·주소로 바로 들어와 목록이 없으면 상세를 목록으로 바꿔 끼운다.
                    onReturnToList = {
                        if (!navController.popBackStack(EolssuDestination.LEARN_LIST, inclusive = false)) {
                            navController.navigate(EolssuDestination.LEARN_LIST) {
                                popUpTo(EolssuDestination.LEARN_STAGE) { inclusive = true }
                            }
                        }
                    },
                )
                composable(EolssuDestination.LEARN_LIST) { entry ->
                    LearnListRoute(
                        viewModel = learnViewModel(navController, entry),
                        practiceOwner = learnGraphEntry(navController, entry),
                        onBack = { navController.popBackStackFrom(entry) },
                        onOpenStage = { navController.navigate(EolssuDestination.learnStage(it)) },
                        navigation = learnNavigation,
                    )
                }
                composable(
                    EolssuDestination.LEARN_STAGE,
                    arguments = listOf(
                        navArgument("stage") { type = NavType.IntType },
                        navArgument("instrument") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    val viewModel = learnViewModel(navController, entry)
                    val initialInstrument = GuideInstrument.fromId(entry.savedStateHandle.get<String>("instrument"))
                    var initialized by remember(entry) { mutableStateOf(initialInstrument == null) }
                    LaunchedEffect(entry) {
                        initialInstrument?.let(viewModel::selectInstrument)
                        initialized = true
                    }
                    if (initialized) {
                        LearnStageRoute(
                            stageNumber = entry.savedStateHandle.get<Int>("stage") ?: 1,
                            viewModel = viewModel,
                            practiceOwner = learnGraphEntry(navController, entry),
                            onBack = { navController.popBackStackFrom(entry) },
                            // 이전·다음 상세는 바꿔 끼워 뒤로 가면 들어온 목록 또는 마이페이지로 돌아간다.
                            onOpenStage = { stage ->
                                navController.navigate(EolssuDestination.learnStage(stage)) {
                                    popUpTo(EolssuDestination.LEARN_STAGE) { inclusive = true }
                                }
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
            }
            profileGraph(navController, onLogOutStarted = recentPrefetch::cancel)
            weakSectionGraph(navController, performance)
            sheetEditGraph(navController)
            authGraph(
                navController,
                onFinished = { notice, provider -> navController.finishFirstRun(firstRunRepository, notice, provider) },
            )

            // 내 악보: 연습곡 고르기 → (평가 없는 연습) / '악보 추가' → 악보 추가·편집(2차).
            composable(
                EolssuDestination.MY_SONGS,
                arguments = listOf(navArgument("sheets") { type = NavType.BoolType; defaultValue = false }),
            ) { entry ->
                MySongsRoute(
                    startOnSheets = entry.savedStateHandle.get<Boolean>("sheets") == true,
                    onBack = { navController.popBackStackFrom(entry) },
                    onPractice = { navController.navigate(EolssuDestination.practice(it, solo = true)) },
                    onOpenSheet = { sheet ->
                        if (navController.currentBackStackEntry?.id == entry.id) {
                            navController.navigate(
                                if (sheet.practiceReady) EolssuDestination.practice(sheet.id, mine = true)
                                else SheetEditDestination.editMine(sheet.id),
                            )
                        }
                    },
                    onAddSheet = { navController.navigate(SheetEditDestination.new()) },
                    onOpenConversion = { sheetId ->
                        if (navController.currentBackStackEntry?.id == entry.id) navController.navigate(SheetEditDestination.conversion(sheetId))
                    },
                    onEditSheet = { navController.navigate(SheetEditDestination.editMine(it.id)) },
                    onSessionExpired = {
                        navController.navigateWithNotice(AuthDestination.LOGIN, StartNotice.SessionExpired) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    },
                )
            }
        }
        EolssuSnackbarHost(appSnackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars))
        SheetCompletionBannerHost(completionBanner, Modifier.align(Alignment.TopCenter))
        PendingRecordingNotice(
            routeId = navController.currentBackStackEntryAsState().value?.id,
            onSend = {
                performance.move(null, PerfTarget.ANALYSIS) {
                    navController.navigate(EolssuDestination.analysis(it.songId, it.bpm, pendingSessionId = it.sessionId))
                }
            },
        )
        // 보내지 못한 녹음 안내와 함께 뜨면 업데이트 안내가 위에 온다. 닫으면 아래 안내가 그대로 남는다.
        StoreUpdateNotice()
    }
    OAuthReturnEffect(navController)
    LaunchedEffect(navController) {
        // Web 연결은 주소를 먼저 적용한 뒤 계속 수집하므로, 끝나기를 기다리지 않는다.
        launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { onNavHostReady(navController) }
        // Web 주소로 다른 화면에 바로 들어와도 로그인이 필요하면 로그인으로 돌려보낸다. 만료 안내는 실제로 세션이 만료됐을 때만 띄운다.
        val route = navController.currentBackStackEntry?.destination?.route
        if (startNeedsLogin(firstRunCompleted, sessionExpired, signedInAtStart) && shouldRedirectExpiredSession(route) &&
            (sessionExpired || route != AuthDestination.LOGIN)
        ) {
            navController.navigateWithNotice(AuthDestination.LOGIN, StartNotice.SessionExpired.takeIf { sessionExpired }) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }
}

/**
 * [entry] 화면이 맨 위일 때만 한 화면 뒤로 간다. 화면의 뒤로 가기·닫기는 이것으로 연결한다.
 * 화면이 바뀌는 동안 떠나는 화면의 버튼이 한 번 더 눌리면 그 아래 화면까지 꺼내, 첫 화면에서는 쌓인 화면이 비어 흰 화면이 된다.
 */
private val measuredDestinations = setOf(
    EolssuDestination.SONG_DETAIL, EolssuDestination.PRACTICE, EolssuDestination.ANALYSIS,
    EolssuDestination.RESULT, EolssuDestination.RECORDS, EolssuDestination.RECORD_RESULT,
    WeakSectionDestination.SELECT, WeakSectionDestination.EVALUATE, WeakSectionDestination.RESULT,
)

internal fun NavController.popBackStackFrom(entry: NavBackStackEntry, pastDirectPractice: Boolean = false) {
    if (currentBackStackEntry?.id != entry.id) return
    popBackStack()
    // 대기 상태 없이 시작한 연주 평가(direct)는 준비 상태를 보이지 않으므로 그 화면까지 닫아 들어온 화면으로 간다.
    // 곡 정보에서 시작했으면 곡 정보(태블릿 가로는 곡 목록 오른쪽 칸), 혼자 연습에서 바꿨으면 그 연습 화면이다.
    val practice = currentBackStackEntry
    if (pastDirectPractice && practice?.destination?.route == EolssuDestination.PRACTICE &&
        practice.savedStateHandle.get<Boolean>("direct") == true
    ) popBackStack()
}

/** 결과 화면에서 ‘기록으로 이동’으로 연 기록 ID를 그 결과 화면 경로에 남기는 키. */
internal const val OPENED_RECORD_KEY = "openedRecord"

/**
 * 기록 상세([entry])에서 [recordId]를 지운 뒤, 바로 아래가 그 기록을 저장하고 ‘기록으로 이동’한 결과 화면이면
 * 기록 상세와 결과 화면을 함께 닫아 결과 화면의 뒤로 가기와 같은 곳(평가를 시작한 곡 정보·연습 화면)으로 간다.
 * 지운 기록의 점수가 다시 보이지 않게 하기 위해서다. 그런 결과 화면이 아니면 아무것도 하지 않고 false를 돌린다.
 */
internal fun NavController.leaveDeletedRecordResult(entry: NavBackStackEntry, recordId: String): Boolean {
    if (currentBackStackEntry?.id != entry.id) return false
    val result = previousBackStackEntry ?: return false
    if (result.savedStateHandle.get<String>(OPENED_RECORD_KEY) != recordId) return false
    popBackStack()
    popBackStackFrom(result, pastDirectPractice = true)
    return true
}

/** 분석 경로의 구간 재평가 인자. 셋 중 하나라도 없으면 곡 전체 연주다. */
private fun NavBackStackEntry.sessionSection(): SessionSection? {
    val start = savedStateHandle.get<Int>("start") ?: -1
    val end = savedStateHandle.get<Int>("end") ?: -1
    val parent = savedStateHandle.get<String>("parent").orEmpty()
    return if (start < 0 || end < start || parent.isEmpty()) null else SessionSection(start, end, parent)
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
private fun learnViewModel(navController: NavController, entry: NavBackStackEntry): LearnViewModel =
    koinViewModel(viewModelStoreOwner = learnGraphEntry(navController, entry))

/**
 * 그래프 `learn`의 항목. 단계 목록·상세가 함께 쓰는 ViewModel(단계 연습 포함)을 여기에 묶는다.
 * 태블릿을 돌리면 단계 상세 항목이 빠지고 목록(2단)이 이어 그리므로, 상세 항목에 묶으면 연습 상태가 사라진다(S15P31E102-1030).
 */
@Composable
private fun learnGraphEntry(navController: NavController, entry: NavBackStackEntry): NavBackStackEntry =
    remember(entry) { navController.getBackStackEntry(EolssuDestination.LEARN) }
