package com.ssafy.eolssu.feature.practice.component

import androidx.compose.ui.window.DialogProperties

/**
 * 연습 설정 시트를 띄우는 창의 설정. 시트가 화면 바닥까지 닿도록 창 내용을 시스템 여백(상태 표시줄·홈 막대·내비게이션 바)으로
 * 줄이지 않고, 여백은 [PracticePanelSheet]가 직접 넣는다(S15P31E102-973).
 */
internal expect fun practiceSheetDialogProperties(): DialogProperties
