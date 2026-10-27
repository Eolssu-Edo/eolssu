package com.ssafy.eolssu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import com.ssafy.eolssu.platform.webPlatformModule

/** Web 진입점(`webApp`의 `main.kt`)이 부르는 `App`. 브라우저 뒤로 가기·주소는 [onNavHostReady]로 잇는다. */
@Composable
fun App(onNavHostReady: suspend (NavController) -> Unit = {}) {
    val platformModule = remember { webPlatformModule() }
    App(platformModule = platformModule, onNavHostReady = onNavHostReady)
}
