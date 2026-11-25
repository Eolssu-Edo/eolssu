package com.ssafy.eolssu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import com.ssafy.eolssu.app.navigation.BrowserBackGuard
import com.ssafy.eolssu.core.ui.component.LocalScreenBackRequests
import com.ssafy.eolssu.platform.navigation.installBrowserBackInterceptor
import com.ssafy.eolssu.platform.webPlatformModule

/**
 * Web 진입점(`webApp`의 `main.kt`)이 부르는 `App`. 브라우저 뒤로 가기·주소는 [onNavHostReady]로 잇는다.
 * 연주 중처럼 떠나기 전에 확인할 화면에서는 브라우저 뒤로 가기를 막고 그 화면의 확인 창을 띄운다([BrowserBackGuard]).
 * 화면 안의 패널·대화상자는 브라우저 뒤로 가기로 그것만 닫고, 저장하지 않은 편집은 새로고침·탭 닫기 전에 묻는다([LocalScreenBackRequests]).
 */
@Composable
fun App(onNavHostReady: suspend (NavController) -> Unit = {}) {
    val platformModule = remember { webPlatformModule() }
    val backGuard = remember { BrowserBackGuard().also(::installBrowserBackInterceptor) }
    CompositionLocalProvider(LocalScreenBackRequests provides backGuard.screens) {
        App(platformModule = platformModule, onNavHostReady = onNavHostReady, browserBackGuard = backGuard)
    }
}
