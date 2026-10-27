package com.ssafy.eolssu

import androidx.compose.ui.window.ComposeUIViewController
import com.ssafy.eolssu.platform.iosPlatformModule

/** iOS 진입점(`ContentView`)이 부르는 화면. 플랫폼 구현(저장소 등)을 `App`에 넘긴다. */
fun MainViewController() = iosPlatformModule().let { platformModule ->
    ComposeUIViewController { App(platformModule = platformModule) }
}
