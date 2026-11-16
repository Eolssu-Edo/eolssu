package com.ssafy.eolssu.core.audio

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** 미리 읽은 짧은 타격/굴림을 원래 음높이로 겹쳐 재생한다. 화면마다 하나를 소유한다. */
interface JangdanSamplePlayer {
    val available: Boolean
    val interrupted: Boolean
    /** 연속 출력 쓰기의 비동기 실패. 다음 타격까지 기다리지 않고 세션을 종료한다. */
    val failed: Boolean get() = false
    /** 타격 조회·출력의 실행 문맥. 지정한 구현은 play/silence/interrupted의 동시 접근을 보호한다. */
    val timingContext: CoroutineContext get() = EmptyCoroutineContext
    suspend fun prepare(clips: Set<JangdanClip>): Boolean
    /** 모범 음원과 함께 틀 때는 그 플레이어가 오디오 포커스를 소유한다. */
    fun begin(accompanyingModel: Boolean): Boolean
    /**
     * [delayMs] 뒤에 들리도록 예약한다. 모든 타격에 같은 출력 지연을 더하므로 0이면 지금 친 것과 같다.
     * 타격 시각은 호출 시각이 아니라 악보 tick에서 계산해 넘기므로 확인 주기의 지연이 박자에 섞이지 않는다.
     */
    fun play(clip: JangdanClip, gain: Float, delayMs: Double = 0.0): Boolean
    fun silence()
    fun stop()
    fun release()
}

internal const val JANGDAN_SAMPLE_RATE = 44_100

data class JangdanClip(val sample: JangdanSample, val targetFrames: Int? = null)

enum class JangdanSample(val file: String) {
    Deong("deong"), Gi("gi"), Kung("kung"), Deok("deok"), Da("da"), Deo("deo"),
    Gutgeori59("gutgeori_roll_59"), Gutgeori79("gutgeori_roll_79"), Gutgeori99("gutgeori_roll_99"),
    Gutgeori109("gutgeori_roll_109"), Gutgeori119("gutgeori_roll_119")
}

class UnavailableJangdanSamplePlayer : JangdanSamplePlayer {
    override val available = false
    override val interrupted = false
    override suspend fun prepare(clips: Set<JangdanClip>) = false
    override fun begin(accompanyingModel: Boolean) = false
    override fun play(clip: JangdanClip, gain: Float, delayMs: Double) = false
    override fun silence() = Unit
    override fun stop() = Unit
    override fun release() = Unit
}
