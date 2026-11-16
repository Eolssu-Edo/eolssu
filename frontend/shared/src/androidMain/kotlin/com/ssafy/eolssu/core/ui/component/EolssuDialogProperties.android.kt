package com.ssafy.eolssu.core.ui.component

import androidx.compose.ui.window.DialogProperties

/** 기본값(decorFitsSystemWindows = true)은 키보드가 올라올 때 시스템이 창 크기·위치도 바꾸므로 끄고, 여백은 화면 안에서만 넣는다. */
internal actual fun eolssuDialogProperties(): DialogProperties =
    DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
