package com.ssafy.eolssu.core.audio

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** 녹음 파일 규격(PRA-02, `docs/specs/recording-file.md`)에서 앱이 지키는 값. */
object RecordingSpec {
    /** 녹음은 이 길이에 닿으면 멈춘다. 서버는 601초 이상을 거부한다. */
    val MaxDuration: Duration = 10.minutes

    /** 업로드 전에 이 크기를 넘으면 먼저 알린다. 서버는 넘는 파일을 413으로 거부한다. */
    const val MAX_BYTES: Long = 20L * 1024 * 1024

    /** 세션 시작 뒤 이 시간이 지나면 서버가 업로드를 410으로 거부한다(최대 녹음 10분 + 업로드·재시도 여유 30분). */
    val UploadWindow: Duration = 40.minutes
}

/**
 * 평가에 넘길 마친 녹음. [key]는 이 녹음으로 만들 연습 세션의 `Idempotency-Key`이며, 녹음을 새로 시작할 때마다 바뀐다.
 * 세션 생성 응답을 놓쳐 다시 만들 때 같은 키를 써서 세션이 하나만 생기게 한다.
 */
data class RecordedTake(val path: String, val key: String)

/** 녹음을 마친 m4a 파일. */
data class RecordedAudio(val path: String, val bytes: Long) {
    val tooLarge: Boolean get() = bytes > RecordingSpec.MAX_BYTES
}

/**
 * 연주 녹음. 한 번에 하나만 녹음하고 m4a(AAC)로 저장한다(PRA-02).
 * 길이 제한은 부르는 쪽이 [RecordingSpec.MaxDuration]으로 잰다. 일시정지한 동안은 녹음되지 않는다.
 * 플랫폼 구현은 `platform/audio`에 두고, 구현이 없는 플랫폼(현재 Web)은 등록하지 않는다.
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

    /**
     * 전화·Siri처럼 시스템이 녹음을 끊었는지. true면 부르는 쪽은 화면을 떠날 때처럼 녹음을 버리고 중단 안내를 띄운다.
     * [start]로 새 녹음을 시작하면 false로 돌아간다. 끊김을 알 수 없는 플랫폼은 항상 false다.
     */
    val interrupted: Boolean get() = false
}
