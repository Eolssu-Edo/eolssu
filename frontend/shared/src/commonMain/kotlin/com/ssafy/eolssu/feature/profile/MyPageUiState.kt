package com.ssafy.eolssu.feature.profile

import com.ssafy.eolssu.core.model.SocialProvider

/** MY-01 위에 띄우는 확인 창. */
sealed interface MyPageDialog {
    /** 간편 로그인 연결 해제 확인. 이메일 로그인은 남아 있어 언제든 해제할 수 있다. */
    data class Unlink(val provider: SocialProvider) : MyPageDialog

    data object LogOut : MyPageDialog
}
