package com.ssafy.eolssu.core.audio

/**
 * 앱 리소스에 넣어 둔 짧은 소리(`composeResources/files`)를 한 번 튼다. 한 번에 하나만 나고, 새로 틀면 앞 소리는 멈춘다.
 * 플랫폼 구현은 `platform/audio`에 둔다. 소리를 틀지 못해도 예외를 던지지 않는다(화면은 소리 없이 그대로 움직인다).
 */
interface SoundPlayer {
    /**
     * [path](`Res.readBytes`에 넘기는 경로, 예: `files/sigimsae/chuseong.mp3`)를 처음부터 튼다.
     * 소리가 나기 시작하면 돌아와 부르는 쪽이 그림을 소리에 맞춰 시작할 수 있다. 틀지 못했으면 false.
     */
    suspend fun play(path: String): Boolean

    /** 나고 있는 소리를 멈춘다. 나는 소리가 없으면 아무것도 하지 않는다. */
    fun stop()
}
