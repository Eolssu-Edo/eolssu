package com.ssafy.eolssu

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.ssafy.eolssu.app.di.appModule
import com.ssafy.eolssu.app.navigation.EolssuNavHost
import com.ssafy.eolssu.core.theme.EolssuTheme
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

/**
 * 모든 플랫폼이 띄우는 공통 진입점. Koin을 시작하고 테마 안에서 화면 이동 그래프를 그린다.
 * Web은 [onNavHostReady]로 브라우저 뒤로 가기·주소를 화면 이동에 연결한다.
 */
@Composable
fun App(onNavHostReady: suspend (NavController) -> Unit = {}) {
    KoinApplication(configuration = koinConfiguration { modules(appModule) }) {
        EolssuTheme {
            EolssuNavHost(onNavHostReady = onNavHostReady)
        }
    }
}
