package com.ssafy.eolssu.core.ui.component

import androidx.compose.ui.window.DialogProperties

/**
 * [EolssuDialog] 창의 설정. 키보드 여백은 창 크기를 바꾸지 않고 [EolssuDialog] 안에서만 넣는다.
 * 시스템이 창을 줄이거나 옮기면서 화면 안 여백까지 겹치면 입력할 때 창이 흔들린다(S15P31E102-1000).
 */
internal expect fun eolssuDialogProperties(): DialogProperties
