package com.ssafy.eolssu

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import com.ssafy.eolssu.app.di.appModule
import com.ssafy.eolssu.app.navigation.BrowserBackGuard
import com.ssafy.eolssu.app.navigation.EolssuNavHost
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.core.module.Module
import org.koin.dsl.koinConfiguration

/**
 * 모든 플랫폼이 띄우는 공통 진입점. Koin을 시작하고 테마 안에서 화면 이동 그래프를 그린다.
 * [platformModule]은 플랫폼 구현 등록이다. 진입점은 플랫폼별 `App`(androidMain·webMain)이나 `MainViewController`를 부른다.
 * Web은 [onNavHostReady]로 브라우저 뒤로 가기·주소를 화면 이동에 연결하고, [browserBackGuard]로 연주 중 브라우저 뒤로 가기를 막는다.
 * 앱이 앞으로 올 때마다 보내지 못한 연습 시간 기록을 보낸다.
 */
@Composable
internal fun App(
    platformModule: Module,
    onNavHostReady: suspend (NavController) -> Unit = {},
    browserBackGuard: BrowserBackGuard? = null,
) {
    KoinApplication(configuration = koinConfiguration { modules(platformModule, appModule) }) {
        val practiceTimeQueue = koinInject<PracticeTimeQueue>()
        LifecycleEventEffect(Lifecycle.Event.ON_START) { practiceTimeQueue.onAppStart() }
        EolssuTheme {
            EolssuNavHost(onNavHostReady = onNavHostReady, browserBackGuard = browserBackGuard)
        }
    }
}
