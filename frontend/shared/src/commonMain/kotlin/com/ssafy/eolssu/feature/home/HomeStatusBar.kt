package com.ssafy.eolssu.feature.home

import androidx.compose.runtime.Composable

/**
 * 홈 그림이 상태 표시줄 뒤에 있는 동안 상태 표시줄 아이콘(시계·배터리)을 밝게 바꾼다([light]).
 * 홈을 떠나면 앱 기본(어두운 아이콘)으로 되돌린다. 상태 표시줄이 없는 Web은 아무것도 하지 않는다(S15P31E102-973).
 */
@Composable
internal expect fun HomeStatusBarIcons(light: Boolean)
