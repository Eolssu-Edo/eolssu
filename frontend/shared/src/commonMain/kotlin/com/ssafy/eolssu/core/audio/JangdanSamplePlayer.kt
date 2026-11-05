package com.ssafy.eolssu.core.audio

/** 미리 읽은 짧은 타격/굴림을 원래 음높이로 겹쳐 재생한다. 화면마다 하나를 소유한다. */
interface JangdanSamplePlayer {
    val available: Boolean
    val interrupted: Boolean
    suspend fun prepare(samples: Set<JangdanSample>): Boolean
    /** 모범 음원과 함께 틀 때는 그 플레이어가 오디오 포커스를 소유한다. */
    fun begin(accompanyingModel: Boolean): Boolean
    fun play(sample: JangdanSample, gain: Float): Boolean
    fun silence()
    fun stop()
    fun release()
}

enum class JangdanSample(val file: String) {
    Deong("deong"), Gi("gi"), Kung("kung"), Deok("deok"), Da("da"), Deo("deo"),
    Gutgeori59("gutgeori_roll_59"), Gutgeori79("gutgeori_roll_79"), Gutgeori99("gutgeori_roll_99"),
    Gutgeori109("gutgeori_roll_109"), Gutgeori119("gutgeori_roll_119")
}

class UnavailableJangdanSamplePlayer : JangdanSamplePlayer {
    override val available = false
    override val interrupted = false
    override suspend fun prepare(samples: Set<JangdanSample>) = false
    override fun begin(accompanyingModel: Boolean) = false
    override fun play(sample: JangdanSample, gain: Float) = false
    override fun silence() = Unit
    override fun stop() = Unit
    override fun release() = Unit
}
