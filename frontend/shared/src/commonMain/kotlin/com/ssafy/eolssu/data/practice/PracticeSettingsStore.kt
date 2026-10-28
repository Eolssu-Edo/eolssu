package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.storage.KeyValueStore
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
 * 앱이 켜져 있는 동안의 연습 설정과, 기기에 남기는 마이크 허용 응답.
 * 마이크는 첫 실행 안내(AUTH-03)의 '허용'이나 연습 화면 안내의 '계속'으로 허용한 것으로 두고, 앱을 다시 켜도 다시 묻지 않는다.
 * 실제 녹음을 붙일 때 플랫폼 권한(`platform/permission`)으로 바꾼다.
 */
class PracticeSettingsStore(private val store: KeyValueStore) {
    private val _settings = MutableStateFlow(PracticeSettings())
    val settings: StateFlow<PracticeSettings> = _settings.asStateFlow()

    private val _micGranted = MutableStateFlow(store.getString(MIC_KEY) == GRANTED)
    val micGranted: StateFlow<Boolean> = _micGranted.asStateFlow()

    fun update(transform: (PracticeSettings) -> PracticeSettings) = _settings.update(transform)

    fun grantMic() {
        store.putString(MIC_KEY, GRANTED)
        _micGranted.value = true
    }

    private companion object {
        const val MIC_KEY = "mic_granted"
        const val GRANTED = "true"
    }
}
