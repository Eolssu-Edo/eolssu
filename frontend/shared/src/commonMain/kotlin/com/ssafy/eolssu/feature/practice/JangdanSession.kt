package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.audio.JangdanCursor
import com.ssafy.eolssu.core.audio.JangdanPlan
import com.ssafy.eolssu.core.audio.JangdanSamplePlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class JangdanStatus { Idle, Loading, Playing, Unsupported, Failed, Interrupted }

/** One owner for preview, model accompaniment and unrecorded practice. Never queue missed attacks. */
internal class JangdanSession(private val player: JangdanSamplePlayer, private val scope: CoroutineScope) {
    private val mutableStatus = MutableStateFlow(JangdanStatus.Idle)
    val status = mutableStatus.asStateFlow()
    private var job: Job? = null
    private var generation = 0

    fun start(plan: JangdanPlan?, accompanyingModel: Boolean, tick: () -> Double?, onStarted: () -> Unit = {}) {
        stop()
        if (!player.available || plan == null) {
            mutableStatus.value = JangdanStatus.Unsupported
            return
        }
        val token = generation
        mutableStatus.value = JangdanStatus.Loading
        job = scope.launch {
            try {
                if (!player.prepare(plan.strikes.map { it.sample }.toSet()) || !player.begin(accompanyingModel)) {
                    mutableStatus.value = JangdanStatus.Failed
                    return@launch
                }
                onStarted()
                mutableStatus.value = JangdanStatus.Playing
                val cursor = JangdanCursor(plan)
                while (isActive) {
                    if (player.interrupted) {
                        mutableStatus.value = JangdanStatus.Interrupted
                        break
                    }
                    val now = tick() ?: break
                    val strikes = cursor.due(now)
                    if (cursor.movedBackward) player.silence()
                    if (strikes.any { !player.play(it.sample, it.gain) }) {
                        mutableStatus.value = JangdanStatus.Failed
                        break
                    }
                    delay(5)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableStatus.value = JangdanStatus.Failed
            } finally {
                if (token == generation) {
                    if (mutableStatus.value == JangdanStatus.Failed) player.release() else player.stop()
                    if (mutableStatus.value == JangdanStatus.Playing) mutableStatus.value = JangdanStatus.Idle
                }
            }
        }
    }

    fun stop() {
        generation++
        job?.cancel()
        job = null
        player.stop()
        mutableStatus.value = JangdanStatus.Idle
    }

    fun release() { stop(); player.release() }
}
