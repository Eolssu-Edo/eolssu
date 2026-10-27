package com.ssafy.eolssu.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ssafy.eolssu.feature.home.HomeRoute
import com.ssafy.eolssu.feature.practice.PracticeRoute
import com.ssafy.eolssu.feature.result.AnalysisRoute
import com.ssafy.eolssu.feature.result.ResultRoute
import com.ssafy.eolssu.feature.song.SongDetailRoute
import com.ssafy.eolssu.feature.song.SongListRoute

/** 화면 경로. Web에서는 주소의 `#` 뒤에 그대로 보인다. 화면을 추가할 때 여기에 경로를 더한다. */
object EolssuDestination {
    const val HOME = "home"
    const val SONG_LIST = "songs"
    const val SONG_DETAIL = "songs/{songId}"

    const val PRACTICE = "practice/{songId}?solo={solo}&switched={switched}"

    const val ANALYSIS = "analysis/{songId}/{bpm}"
    const val RESULT = "result/{evaluationId}"

    fun songDetail(songId: String) = "songs/$songId"

    fun analysis(songId: String, bpm: Int) = "analysis/$songId/$bpm"

    fun result(evaluationId: String) = "result/$evaluationId"

    /** [solo]: 평가 없이 혼자 연습. [switched]: 연습 모드에서 평가로 바꿔 들어옴. */
    fun practice(songId: String, solo: Boolean = false, switched: Boolean = false) = "practice/$songId?solo=$solo&switched=$switched"
}

/**
 * 앱의 화면 이동 그래프. 기능 사이의 이동은 이곳에서 콜백으로 연결한다.
 * [onNavHostReady]는 Web 진입점이 브라우저 뒤로 가기·주소를 연결할 때 쓴다.
 */
@Composable
fun EolssuNavHost(
    onNavHostReady: suspend (NavController) -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = EolssuDestination.HOME) {
        composable(EolssuDestination.HOME) {
            HomeRoute(onStartEvaluation = { navController.navigate(EolssuDestination.SONG_LIST) })
        }
        composable(EolssuDestination.SONG_LIST) {
            SongListRoute(
                onBack = { navController.popBackStack() },
                onOpenSong = { navController.navigate(EolssuDestination.songDetail(it)) },
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
                onGoRecords = null,
            )
        }
    }
    LaunchedEffect(navController) { onNavHostReady(navController) }
}
