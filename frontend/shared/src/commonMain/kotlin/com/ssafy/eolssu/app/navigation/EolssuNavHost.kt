package com.ssafy.eolssu.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ssafy.eolssu.feature.home.HomeRoute

/** 화면 경로. Web에서는 주소의 `#` 뒤에 그대로 보인다. 화면을 추가할 때 여기에 경로를 더한다. */
object EolssuDestination {
    const val HOME = "home"
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
        composable(EolssuDestination.HOME) { HomeRoute() }
    }
    LaunchedEffect(navController) { onNavHostReady(navController) }
}
