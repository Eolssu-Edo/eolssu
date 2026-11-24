@file:OptIn(ExperimentalWasmJsInterop::class)

package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.JANGDAN_SAMPLE_RATE
import com.ssafy.eolssu.core.audio.JangdanClip
import com.ssafy.eolssu.core.audio.JangdanSample
import com.ssafy.eolssu.core.audio.JangdanSamplePlayer
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.document
import kotlinx.coroutines.CancellationException
import org.w3c.dom.events.Event

/**
 * Web Audio로 장단 타격을 겹쳐 재생한다(S15P31E102-1260). 화면마다 하나를 소유한다.
 *
 * - 타격은 `AudioBufferSourceNode.start(when)`으로 오디오 시계에 예약하므로 타이밍 루프의 깨어나는 지연이 박에 섞이지 않는다.
 * - 출력 문맥은 앱 전체가 [WebAudioOutput] 하나를 함께 쓴다. 사용자 제스처로 소리를 여는 일도 그쪽이 맡는다.
 *   아직 막혀 있는 동안의 타격은 쌓아 두지 않고 버린다.
 * - [release] 뒤에도 같은 플레이어를 다시 쓸 수 있다. 연습 화면은 탭이 가려지거나 재생이 실패할 때마다 놓았다가 다시 쓴다.
 * - 굿거리 굴림의 길이 조절([JangdanClip.targetFrames])은 Web Audio에 음높이 보존 속도 변경이 없어서,
 *   계획이 고른 가장 가까운 BPM 원본을 원래 속도로 틀고 박자 길이보다 길면 끝을 짧게 줄여 자른다.
 * - 탭이 가려지면 브라우저가 타이머를 늦추므로 중단(interrupted)으로 보고 멈춘다.
 */
internal class WebJangdanSamplePlayer(
    private val cache: WebJangdanSampleCache,
    private val output: WebAudioOutput,
) : JangdanSamplePlayer {
    override val available = true
    override var interrupted = false
        private set

    private class Voice(val source: AudioBufferSourceNode, val amp: GainNode, val endTime: Double)

    private val samples = mutableMapOf<JangdanSample, DecodedJangdanSample>()
    private val voices = ArrayDeque<Voice>()
    private var context: AudioContext? = null
    private var leadSeconds = MIN_LEAD_SECONDS
    private var acceptingHits = false
    private var attached = false
    private var epoch = 0
    private val visibility: (Event) -> Unit = {
        if (acceptingHits && pageHidden()) {
            interrupted = true
            silence()
        }
    }

    init {
        // 화면이 열리면 바로 붙어 첫 누름에서 출력을 연다. Safari는 '장단 듣기' 누름 자체가 허락 시점이 된다.
        attach()
    }

    override suspend fun prepare(clips: Set<JangdanClip>): Boolean {
        attach()
        val request = epoch
        for (sample in clips.mapTo(mutableSetOf()) { it.sample }) {
            if (sample in samples) continue
            val decoded = try {
                cache.sampleFor(sample)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                return false
            }
            if (request != epoch) return false
            samples[sample] = decoded
        }
        if (request != epoch) return false
        // 멈춰 둔 문맥을 다시 깨우는 동안의 첫 타격을 버리지 않도록 돌기 시작할 때까지 잠깐 기다린다.
        // 세션은 놓은(release) 뒤 다시 틀 때 늘 prepare부터 부른다.
        output.awaitResumed()
        return request == epoch
    }

    override fun begin(accompanyingModel: Boolean): Boolean {
        attach()
        val current = output.start() ?: return false
        context = current
        interrupted = false
        leadSeconds = (latencySeconds(current) + SAFETY_SECONDS).coerceIn(MIN_LEAD_SECONDS, MAX_LEAD_SECONDS)
        acceptingHits = true
        return true
    }

    override fun play(clip: JangdanClip, gain: Float, delayMs: Double): Boolean {
        if (interrupted || !acceptingHits) return false
        val current = context ?: return false
        when (current.state) {
            "running" -> Unit
            // 제스처를 기다리는 동안의 타격은 버린다. 나중에 한꺼번에 울리지 않게 예약하지 않는다.
            "suspended" -> return true
            else -> {
                // Safari의 통화 같은 중단, 닫힌 문맥. 세션 루프가 다음 확인에서 Interrupted로 끝낸다.
                interrupted = true
                silence()
                return true
            }
        }
        val sample = samples[clip.sample] ?: return false
        val window = jangdanHitWindow(sample.frames, clip.targetFrames)
        val at = maxOf(audibleNow(current) + leadSeconds + delayMs.coerceAtLeast(0.0) / 1000.0, current.currentTime)
        val level = gain.coerceIn(0f, 1f).toDouble() * OUTPUT_GAIN
        return try {
            val source = current.createBufferSource()
            source.buffer = sample.buffer
            val amp = current.createGain()
            amp.gain.value = level
            window.fadeOutSeconds?.let { fade ->
                amp.gain.setValueAtTime(level, at + window.durationSeconds - fade)
                amp.gain.linearRampToValueAtTime(0.0, at + window.durationSeconds)
            }
            source.connect(amp)
            amp.connect(current.destination)
            source.start(at, 0.0, window.durationSeconds)
            pruneVoices(current.currentTime)
            voices.addLast(Voice(source, amp, at + window.durationSeconds))
            true
        } catch (_: Throwable) {
            false
        }
    }

    override fun silence() {
        while (voices.isNotEmpty()) {
            val voice = voices.removeFirst()
            runCatching { voice.source.stop() }
            runCatching { voice.source.disconnect() }
            runCatching { voice.amp.disconnect() }
        }
    }

    override fun stop() {
        acceptingHits = false
        silence()
    }

    /** 소리를 끊고 출력에서 떨어진다. 받은 소리는 앱 캐시에 남으므로 다시 쓸 때 내려받지 않는다. */
    override fun release() {
        stop()
        epoch++
        samples.clear()
        context = null
        if (!attached) return
        attached = false
        document.removeEventListener("visibilitychange", visibility)
        output.detach()
    }

    private fun attach() {
        if (attached) return
        attached = true
        document.addEventListener("visibilitychange", visibility)
        output.attach()
    }

    private fun pruneVoices(now: Double) {
        while (voices.isNotEmpty() && voices.first().endTime < now) {
            val voice = voices.removeFirst()
            runCatching { voice.source.disconnect() }
            runCatching { voice.amp.disconnect() }
        }
    }

    /**
     * 지금 스피커로 나가는 소리의 문맥 시각. `currentTime`은 출력 묶음 단위로 뛰므로 그대로 쓰면 타격마다 수 ms씩 흔들린다.
     * `getOutputTimestamp`를 성능 시계로 보간해 매끄럽게 하고, 쓸 수 없거나 멈췄다 깨어난 직후의 낡은 값이면
     * `currentTime`에서 출력 지연을 뺀다. 들리는 시각은 렌더 시각(`currentTime`)보다 앞설 수 없다.
     */
    private fun audibleNow(current: AudioContext): Double {
        val rendered = current.currentTime
        val stamp = runCatching { current.getOutputTimestamp() }.getOrNull()
        if (stamp != null && stamp.contextTime > 0.0 && stamp.performanceTime > 0.0) {
            val now = stamp.contextTime + (performanceNow() - stamp.performanceTime) / 1000.0
            if (now.isFinite() && now in rendered - 1.0..rendered + STAMP_TOLERANCE_SECONDS) return now
        }
        return rendered - latencySeconds(current)
    }

    private fun latencySeconds(current: AudioContext): Double =
        listOfNotNull(current.baseLatency, current.outputLatency).sumOf { it.toDouble().takeIf(Double::isFinite) ?: 0.0 }
            .coerceIn(0.0, MAX_LEAD_SECONDS)

    private companion object {
        /** Android·iOS와 같은 타격 크기. */
        const val OUTPUT_GAIN = 0.65
        /** 렌더 주기보다 늦게 예약하도록 출력 지연에 더하는 여유. */
        const val SAFETY_SECONDS = 0.02
        const val MIN_LEAD_SECONDS = 0.02
        const val MAX_LEAD_SECONDS = 0.25
        /** 출력 시각 보간이 렌더 묶음 하나만큼 앞서는 것은 허용한다. */
        const val STAMP_TOLERANCE_SECONDS = 0.05
    }
}

/** 한 타격을 얼마나 틀지. [fadeOutSeconds]가 있으면 원본보다 짧게 잘라 끝을 줄인다. */
internal data class JangdanHitWindow(val durationSeconds: Double, val fadeOutSeconds: Double?)

/**
 * 원본 [sourceFrames]를 [targetFrames](굴림이 채워야 할 박자 길이)에 맞춘다. 음높이를 바꾸지 않으므로 늘이지는 않는다.
 * 원본이 더 길면 박자 길이에서 자르고 끝 [JANGDAN_TRIM_FADE_FRAMES]를 줄여 딸깍 소리를 막는다.
 */
internal fun jangdanHitWindow(sourceFrames: Int, targetFrames: Int?): JangdanHitWindow {
    val frames = targetFrames?.coerceIn(1, sourceFrames) ?: sourceFrames
    val trimmed = frames < sourceFrames
    val fade = if (trimmed) minOf(JANGDAN_TRIM_FADE_FRAMES, frames / 2) / JANGDAN_SAMPLE_RATE.toDouble() else null
    return JangdanHitWindow(frames / JANGDAN_SAMPLE_RATE.toDouble(), fade)
}

/** 5ms. */
internal const val JANGDAN_TRIM_FADE_FRAMES = 220
