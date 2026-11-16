package com.ssafy.eolssu.feature.practice.component

import androidx.compose.ui.window.DialogProperties

/** Android 대화 창은 기본으로 시스템 바를 피해 놓이므로 화면 끝까지 그리게 한다. */
internal actual fun practiceSheetDialogProperties(): DialogProperties =
    DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
