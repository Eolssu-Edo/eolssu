package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
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

/**
 * 입력칸 밖 여백을 누르면 키보드를 내리고 칸 초점을 푼다(S15P31E102-1079).
 * 버튼·칩·입력칸처럼 누름을 먼저 받는 요소는 그대로 동작하고, 아무도 받지 않은 누름만 여기로 온다.
 * 초점만 풀면 키보드가 남는 기기가 있어 키보드도 함께 내린다.
 */
@Composable
fun Modifier.hideKeyboardOnTapOutside(): Modifier {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    return pointerInput(keyboard, focusManager) {
        detectTapGestures {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
}
