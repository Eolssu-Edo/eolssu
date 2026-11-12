package com.ssafy.eolssu.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * 화면 키보드를 내리는 동작(S15P31E102-800). 키보드의 확인·검색·엔터를 눌렀을 때 쓴다.
 * 입력칸 초점은 그대로 둔다. 초점을 풀면 실물 키보드의 엔터가 화면 첫 버튼(간편 로그인 등)을 누를 수 있다.
 * 칸을 다시 누르면 키보드가 다시 올라온다.
 */
@Composable
fun rememberHideKeyboard(): () -> Unit {
    val keyboard = LocalSoftwareKeyboardController.current
    return { keyboard?.hide() }
}
