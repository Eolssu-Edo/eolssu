package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.audio.JangdanClip
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
    private var preparedClips: Set<JangdanClip> = emptySet()

    suspend fun prepareAccompaniment(plan: JangdanPlan): Boolean {
        val ready = try {
            prepare(plan)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        if (!ready) {
            release()
            mutableStatus.value = JangdanStatus.Failed
        }
        return ready
    }

    private suspend fun prepare(plan: JangdanPlan): Boolean {
        val clips = plan.strikes.map { it.clip }.toSet()
        if (preparedClips == clips) return true
        preparedClips = emptySet()
        return player.prepare(clips).also { if (it) preparedClips = clips }
    }

    fun start(plan: JangdanPlan?, accompanyingModel: Boolean, tick: () -> Double?, resumeAfterTick: Double? = null, startAtTick: Double? = null, allowPreparedPlayback: Boolean = false, onStarted: () -> Unit = {}) {
        val adopted = allowPreparedPlayback && plan != null && player.available && player.adoptPreparedPlayback(plan)
        generation++
        job?.cancel()
        job = null
        if (!adopted) player.stop()
        mutableStatus.value = JangdanStatus.Idle
        if (!player.available || plan == null) {
            mutableStatus.value = JangdanStatus.Unsupported
            return
        }
        val token = generation
        mutableStatus.value = JangdanStatus.Loading
        job = scope.launch {
            try {
                if (!adopted && (!prepare(plan) || !player.begin(accompanyingModel))) {
                    mutableStatus.value = JangdanStatus.Failed
                    return@launch
                }
                onStarted()
                mutableStatus.value = JangdanStatus.Playing
                val cursor = JangdanCursor(plan, resumeAfterTick = resumeAfterTick, startAtTick = startAtTick)
                while (isActive) {
                    if (player.interrupted) {
                        mutableStatus.value = JangdanStatus.Interrupted
                        break
                    }
                    val now = tick() ?: break
                    if (!adopted) {
                        val strikes = cursor.due(now)
                        if (cursor.movedBackward) player.silence()
                        if (strikes.any { !player.play(it.clip, it.gain) }) {
                            mutableStatus.value = JangdanStatus.Failed
                            break
                        }
                    }
                    delay(5)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableStatus.value = JangdanStatus.Failed
            } finally {
                if (token == generation) {
                    if (mutableStatus.value == JangdanStatus.Failed) {
                        preparedClips = emptySet()
                        player.release()
                    } else player.stop()
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

    fun release() {
        stop()
        preparedClips = emptySet()
        player.release()
    }
}
