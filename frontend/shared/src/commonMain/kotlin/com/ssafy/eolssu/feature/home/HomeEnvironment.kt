package com.ssafy.eolssu.feature.home

import androidx.compose.runtime.Composable

internal data class HomeEnvironment(val reduceMotion: Boolean = false)

@Composable
internal expect fun rememberHomeEnvironment(): HomeEnvironment

/**
 * 홈에서 시스템 뒤로 가기를 두 번 눌렀을 때 앱을 끝내는 동작. Android만 현재 Activity를 닫고,
 * 시스템 뒤로 가기가 없는 iOS와 브라우저 뒤로 가기를 쓰는 Web은 null이라 홈의 뒤로 가기를 가로채지 않는다.
 */
@Composable
internal expect fun rememberHomeAppExit(): (() -> Unit)?

/** 홈이 완전히 펼쳐진 뒤 웹 글꼴 해석을 유휴 시간에 준비한다. */
@Composable
internal expect fun PrepareHomeFonts(expanded: Boolean)
