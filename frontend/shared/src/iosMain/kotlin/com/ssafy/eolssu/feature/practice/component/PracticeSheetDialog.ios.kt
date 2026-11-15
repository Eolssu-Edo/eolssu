package com.ssafy.eolssu.feature.practice.component

import androidx.compose.ui.window.DialogProperties

/** 기본값은 창 내용을 안전 영역 안으로 줄이므로 끈다. 키보드 여백 처리는 기본값을 유지한다. */
internal actual fun practiceSheetDialogProperties(): DialogProperties =
    DialogProperties(usePlatformDefaultWidth = false, usePlatformInsets = false)
