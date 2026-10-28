package com.ssafy.eolssu.app.navigation

/**
 * 화면을 떠나기 전에 확인해야 할 때 Web 브라우저 뒤로 가기를 가로채는 자리.
 * Android 기기 뒤로 가기와 Web Escape 키는 화면의 `BackHandler`가 받지만, 브라우저 뒤로 가기는
 * 화면 이동 그래프의 브라우저 연결(`bindToBrowserNavigation`)로 바로 가서 `BackHandler`를 거치지 않는다.
 *
 * 확인이 필요한 화면은 [onBack]에 화면의 뒤로 가기 동작을 넣고, 필요 없어지면 비운다.
 * Web 진입점은 [onBack]이 있으면 브라우저 뒤로 가기로 화면을 옮기지 않고 이것을 부른다. 다른 플랫폼은 쓰지 않는다.
 */
class BrowserBackGuard {
    var onBack: (() -> Unit)? = null
}
