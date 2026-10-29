package com.ssafy.eolssu.core.audio

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** 녹음 파일 규격(PRA-02, `docs/specs/recording-file.md`)에서 앱이 지키는 값. */
object RecordingSpec {
    /** 녹음은 이 길이에 닿으면 멈춘다. 서버는 601초 이상을 거부한다. */
    val MaxDuration: Duration = 10.minutes

    /** 업로드 전에 이 크기를 넘으면 먼저 알린다. 서버는 넘는 파일을 413으로 거부한다. */
    const val MAX_BYTES: Long = 20L * 1024 * 1024
}

/** 녹음을 마친 m4a 파일. */
data class RecordedAudio(val path: String, val bytes: Long) {
    val tooLarge: Boolean get() = bytes > RecordingSpec.MAX_BYTES
}

/**
 * 연주 녹음. 한 번에 하나만 녹음하고 m4a(AAC)로 저장한다(PRA-02).
 * 길이 제한은 부르는 쪽이 [RecordingSpec.MaxDuration]으로 잰다. 일시정지한 동안은 녹음되지 않는다.
 * 플랫폼 구현은 `platform/audio`에 두고, 구현이 없는 플랫폼(현재 iOS·Web)은 등록하지 않는다.
 */
interface AudioRecorder {
    /** 새 녹음을 시작한다. 전에 [stop]으로 남긴 파일은 지운다. 시작하지 못하면 예외를 던진다. */
    fun start()

    fun pause()

    fun resume()

    /** 녹음을 마치고 파일을 돌려준다. 녹음 중이 아니었거나 저장하지 못했으면 null. */
    fun stop(): RecordedAudio?

    /** 진행 중인 녹음을 버리고 파일을 지운다. 녹음 중이 아니면 아무것도 하지 않는다. */
    fun cancel()

    /** 마지막으로 부른 뒤 들어온 소리의 크기(0~1). 아주 작은 소리는 0이다. */
    fun level(): Float

    /**
     * [level]이 실제 마이크 입력 크기인지. false면 부르는 쪽은 '입력 없음'을 판정하지 않고 녹음을 그대로 보내
     * 서버의 `NO_SOUND` 판정에 맡긴다(S15P31E102-233).
     */
    val measuresLevel: Boolean
}
