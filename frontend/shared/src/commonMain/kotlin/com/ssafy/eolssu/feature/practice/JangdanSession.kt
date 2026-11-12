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
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext

enum class JangdanStatus { Idle, Loading, Playing, Unsupported, Failed, Interrupted }

/** One owner for preview, model accompaniment and unrecorded practice. Never queue missed attacks. */
internal class JangdanSession(private val player: JangdanSamplePlayer, private val scope: CoroutineScope) {
    private val mutableStatus = MutableStateFlow(JangdanStatus.Idle)
    val status = mutableStatus.asStateFlow()
    private var job: Job? = null
    private val stoppingJobs = mutableListOf<Job>()
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

    fun start(plan: JangdanPlan?, accompanyingModel: Boolean, tick: () -> Double?, resumeAfterTick: Double? = null, startAtTick: Double? = null, onStarted: () -> Unit = {}, clockReady: () -> Boolean = { true }) {
        stop()
        if (!player.available || plan == null) {
            mutableStatus.value = JangdanStatus.Unsupported
            return
        }
        val token = generation
        val previous = stoppingJobs.toList()
        mutableStatus.value = JangdanStatus.Loading
        job = scope.launch {
            try {
                previous.joinAll()
                if (!prepare(plan) || !player.begin(accompanyingModel)) {
                    mutableStatus.value = JangdanStatus.Failed
                    return@launch
                }
                onStarted()
                mutableStatus.value = JangdanStatus.Playing
                val cursor = JangdanCursor(plan, resumeAfterTick = resumeAfterTick, startAtTick = startAtTick)
                // Preparation, focus and status stay with the owner. Only timing/output leave the UI.
                val result = withContext(player.timingContext) {
                    while (isActive) {
                        if (player.failed) return@withContext JangdanStatus.Failed
                        if (player.interrupted) return@withContext JangdanStatus.Interrupted
                        if (!clockReady()) { delay(5); continue }
                        val now = tick() ?: break
                        val strikes = cursor.due(now)
                        if (cursor.movedBackward) player.silence()
                        if (strikes.any { !player.play(it.clip, it.gain) }) return@withContext JangdanStatus.Failed
                        delay(5)
                    }
                    null
                }
                if (token == generation && result != null) mutableStatus.value = result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (token == generation) mutableStatus.value = JangdanStatus.Failed
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
        job?.let { it.cancel(); stoppingJobs += it }
        stoppingJobs.removeAll { it.isCompleted }
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
