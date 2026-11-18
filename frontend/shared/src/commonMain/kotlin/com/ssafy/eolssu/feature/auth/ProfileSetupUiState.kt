package com.ssafy.eolssu.feature.auth

import com.ssafy.eolssu.core.model.Instrument

/** AUTH-07 프로필 설정 입력 상태. 기본 악기는 홈·서버 기본값과 같은 단소이고, 가입 요청에 `instrumentId`로 보낸다. */
data class ProfileSetupUiState(
    val nickname: String = "",
    val instrument: Instrument = Instrument.Danso,
    val nicknameError: FieldError? = null,
    /** 닉네임과 상관없는 가입 실패(재인증·약관·악기·티켓 만료·서버 오류). 닉네임을 고쳐도 지우지 않고 다시 보낼 때 새로 정한다(S15P31E102-996). */
    val formError: FieldError? = null,
    /** 가입 요청 중. 완료 버튼에 진행 표시를 보인다. */
    val submitting: Boolean = false,
    val instruments: List<Instrument> = Instrument.entries,
)
