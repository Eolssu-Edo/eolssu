package com.ssafy.eolssu.feature.auth

import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.instrument_daegeum
import eolssu.shared.generated.resources.instrument_danso
import eolssu.shared.generated.resources.instrument_sogeum
import org.jetbrains.compose.resources.StringResource

/**
 * 가입 때 고르는 악기. 피리는 지금 범위에 넣지 않는다. 가입 요청에 `instrumentId`로 보내고, 보내지 않으면 서버도 단소로 정한다.
 * 홈의 `Instrument`가 `core/model`로 옮겨지면 그 모델로 바꾸고 고른 악기를 홈에 넘긴다.
 */
enum class ProfileInstrument(val label: StringResource) {
    Daegeum(Res.string.instrument_daegeum),
    Sogeum(Res.string.instrument_sogeum),
    Danso(Res.string.instrument_danso),
}

/** AUTH-07 프로필 설정 입력 상태. 기본 악기는 홈·서버 기본값과 같은 단소다. */
data class ProfileSetupUiState(
    val nickname: String = "",
    val instrument: ProfileInstrument = ProfileInstrument.Danso,
    val nicknameError: FieldError? = null,
    val instruments: List<ProfileInstrument> = ProfileInstrument.entries,
)
