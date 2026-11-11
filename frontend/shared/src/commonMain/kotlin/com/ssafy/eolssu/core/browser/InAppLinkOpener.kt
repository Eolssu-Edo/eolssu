package com.ssafy.eolssu.core.browser

import androidx.compose.runtime.Composable

/**
 * 출처 같은 외부 링크를 앱 위에 겹쳐 연다(S15P31E102-768). Android는 앱과 같은 작업에 브라우저 탭(Custom Tabs)을 띄워
 * 브라우저에서 뒤로 가기·닫기를 누르면 앱으로 돌아온다. 다른 플랫폼은 기본 링크 열기와 같다. 열지 못하면 예외를 던진다.
 */
@Composable
expect fun rememberInAppLinkOpener(): (String) -> Unit
