package com.ssafy.eolssu.data.practice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 연습 화면을 다시 열어도 이어지는 설정. 와이어프레임처럼 표기·장단·크게 보기는 모든 곡에, 속도는 곡마다 둔다.
 *
 * @property bpmBySong 곡별 연습 속도(BPM). 없으면 곡의 기준 속도.
 */
data class PracticeSettings(
    val showHangul: Boolean = false,
    val showSolfege: Boolean = false,
    val jangdanSound: Boolean = false,
    val beatVisual: Boolean = true,
    val bigScore: Boolean = false,
    val bpmBySong: Map<String, Int> = emptyMap(),
)

/**
 * 앱이 켜져 있는 동안의 연습 설정과 마이크 권한 응답.
 * 마이크 권한은 실제 녹음을 붙일 때 플랫폼 권한(`platform/permission`)으로 바꾼다. 지금은 '계속'을 누르면 허용한 것으로 둔다.
 */
class PracticeSettingsStore {
    private val _settings = MutableStateFlow(PracticeSettings())
    val settings: StateFlow<PracticeSettings> = _settings.asStateFlow()

    private val _micGranted = MutableStateFlow(false)
    val micGranted: StateFlow<Boolean> = _micGranted.asStateFlow()

    fun update(transform: (PracticeSettings) -> PracticeSettings) = _settings.update(transform)

    fun grantMic() {
        _micGranted.value = true
    }
}
