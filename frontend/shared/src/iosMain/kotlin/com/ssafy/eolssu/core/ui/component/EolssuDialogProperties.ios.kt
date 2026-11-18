package com.ssafy.eolssu.core.ui.component

import androidx.compose.ui.window.DialogProperties

/** 지금 동작을 유지한다. 이 플랫폼의 키보드 여백 처리는 S15P31E102-1000에서 확인하지 않았다. */
internal actual fun eolssuDialogProperties(): DialogProperties =
    DialogProperties(usePlatformDefaultWidth = false)
