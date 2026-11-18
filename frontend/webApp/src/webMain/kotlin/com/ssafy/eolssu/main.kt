package com.ssafy.eolssu

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.bindToBrowserNavigation

@OptIn(ExperimentalComposeUiApi::class, ExperimentalBrowserHistoryApi::class)
fun main() {
    ComposeViewport {
        // 브라우저 뒤로·앞으로 가기와 주소(#경로)를 앱의 화면 이동에 연결한다.
        App(onNavHostReady = { it.bindToBrowserNavigation() })
    }
}
