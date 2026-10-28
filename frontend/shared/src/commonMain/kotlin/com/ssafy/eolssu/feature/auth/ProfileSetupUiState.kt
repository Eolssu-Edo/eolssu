package com.ssafy.eolssu.feature.auth

import com.ssafy.eolssu.core.model.Instrument

/** AUTH-07 프로필 설정 입력 상태. 기본 악기는 홈·서버 기본값과 같은 단소이고, 가입 요청에 `instrumentId`로 보낸다. */
data class ProfileSetupUiState(
    val nickname: String = "",
    val instrument: Instrument = Instrument.Danso,
    val nicknameError: FieldError? = null,
    val instruments: List<Instrument> = Instrument.entries,
)
