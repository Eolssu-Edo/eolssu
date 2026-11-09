package com.ssafy.eolssu.feature.home

/** 첫 뒤로 가기 뒤 두 번째 입력을 앱 종료로 받는 시간(2초). 안내 문구도 이 시간만큼 보인다. */
internal const val HomeBackExitWindowMillis = 2_000L

/**
 * 홈의 '뒤로 가기 두 번으로 종료' 판정. [press]는 첫 입력이면 false(안내를 띄운다), [HomeBackExitWindowMillis] 안의 두 번째 입력이면 true(앱을 닫는다)다.
 * 시간이 지나면 처음 상태로 돌아가 다음 입력은 다시 첫 입력이 된다. 시각은 단조 증가하는 밀리초로 받는다.
 */
internal class HomeBackExit(private val windowMillis: Long = HomeBackExitWindowMillis) {
    private var armedAt: Long? = null

    fun press(nowMillis: Long): Boolean {
        val armed = armedAt
        if (armed != null && nowMillis - armed < windowMillis) {
            armedAt = null
            return true
        }
        armedAt = nowMillis
        return false
    }
}
