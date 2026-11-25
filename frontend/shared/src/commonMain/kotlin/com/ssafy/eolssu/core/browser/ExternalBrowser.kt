package com.ssafy.eolssu.core.browser

/**
 * 주소를 앱 밖의 브라우저로 연다. 간편 로그인 제공자 화면은 앱 안이 아니라 외부 브라우저에서 띄운다(RFC 8252).
 * 구현은 지원하는 플랫폼(Android 외부 브라우저, iOS 인증 세션, 웹 같은 탭 이동)의 플랫폼 모듈에만 등록하고, 쓰는 쪽은 `getOrNull`로 받아 없으면 '사용할 수 없음'으로 본다.
 */
fun interface ExternalBrowser {
    /** 열었으면 true. 열 브라우저가 없으면 false. */
    fun open(url: String): Boolean
}
