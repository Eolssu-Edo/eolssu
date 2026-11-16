package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.audio.JangdanClip
import com.ssafy.eolssu.core.audio.JangdanCursor
import com.ssafy.eolssu.core.audio.JangdanPlan
import com.ssafy.eolssu.core.audio.JangdanSamplePlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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

    /** [start] 뒤 [onStarted]를 부르기 전까지. 이미 준비한 소리로 다시 시작할 때는 상태를 Loading으로 바꾸지 않으므로 따로 둔다. */
    val starting: Boolean get() = job?.isActive == true && !started
    private var started = false
    /** 재생 중 탐색 요청. 타이밍 문맥의 루프가 읽으므로 스레드 안전한 흐름으로 둔다. */
    private val repositions = MutableStateFlow(0)

    /**
     * @param untilTick 이 tick과 그 뒤의 타격은 미리 예약하지 않는다. 재생 끝·구간 끝에서 [tick]이 null이 되기 전에
     *   다음 주기의 첫 박이 예약됐다가 잘리는 것을 막는다.
     * @param lookaheadMs 미리 예약할 앞. [tick]이 재생 중 스스로 뒤로 돌아가는 시계(반복이 있는 수행표)는 0으로 둔다.
     */
    fun start(plan: JangdanPlan?, accompanyingModel: Boolean, tick: () -> Double?, resumeAfterTick: Double? = null, startAtTick: Double? = null,
              onStarted: () -> Unit = {}, clockReady: () -> Boolean = { true }, untilTick: Double? = null,
              lookaheadMs: Double = JANGDAN_LOOKAHEAD_MS) {
        stop()
        if (!player.available || plan == null) {
            mutableStatus.value = JangdanStatus.Unsupported
            return
        }
        val token = generation
        val previous = stoppingJobs.toList()
        // 이미 받아 둔 소리로 다시 시작(탐색·모드 전환)할 때는 불러올 것이 없으므로 로딩 안내를 띄우지 않는다.
        if (preparedClips != plan.strikes.map { it.clip }.toSet()) mutableStatus.value = JangdanStatus.Loading
        started = false
        job = scope.launch {
            try {
                previous.joinAll()
                if (!prepare(plan) || !player.begin(accompanyingModel)) {
                    mutableStatus.value = JangdanStatus.Failed
                    return@launch
                }
                started = true
                onStarted()
                mutableStatus.value = JangdanStatus.Playing
                fun cursor(resumeAfter: Double?, startAt: Double?) = JangdanCursor(plan, resumeAfterTick = resumeAfter,
                    startAtTick = startAt, lookaheadMs = lookaheadMs, untilTick = untilTick ?: Double.POSITIVE_INFINITY)
                var cursor = cursor(resumeAfterTick, startAtTick)
                var reposition = repositions.value
                var waiting = false
                // 5 ms마다 깨어나되 탐색 요청이 오면 바로 깨어나 새 위치의 첫 타격을 기다리지 않고 예약한다.
                suspend fun nap() { withTimeoutOrNull(5) { repositions.first { it != reposition } } }
                // Preparation, focus and status stay with the owner. Only timing/output leave the UI.
                val result = withContext(player.timingContext) {
                    while (isActive) {
                        if (player.failed) return@withContext JangdanStatus.Failed
                        if (player.interrupted) return@withContext JangdanStatus.Interrupted
                        if (repositions.value != reposition) {
                            // 탐색: 예약해 둔 앞 타격을 비우고 새 위치에서 처음 시작한 것처럼 다시 꺼낸다.
                            reposition = repositions.value
                            player.silence()
                            cursor = cursor(null, null)
                        }
                        if (!clockReady()) {
                            // 음원이 다시 버퍼링하는 동안 미리 예약한 타격이 혼자 울리지 않게 비운다.
                            if (!waiting) { player.silence(); cursor.forgetScheduled(); waiting = true }
                            nap(); continue
                        }
                        waiting = false
                        val now = tick()
                        if (now == null) {
                            // The owner/UI may still be stalled when a playback boundary is reached.
                            player.silence()
                            break
                        }
                        val strikes = cursor.due(now)
                        if (cursor.movedBackward) player.silence()
                        if (strikes.any { !player.play(it.clip, it.gain, ((it.tick - now) * plan.msPerTick).coerceAtLeast(0.0)) }) {
                            return@withContext JangdanStatus.Failed
                        }
                        nap()
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

    /**
     * 재생을 멈추지 않고 위치만 옮긴다. 호출한 쪽은 [start]에 넘긴 tick 시계를 새 위치로 바꾼다.
     * 소리·출력·오디오 포커스를 그대로 두므로 로딩과 출력 재시작 없이 이어진다. 재생 중이 아니면 false.
     */
    fun reposition(): Boolean {
        if (mutableStatus.value != JangdanStatus.Playing || job?.isActive != true) return false
        repositions.value++
        return true
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

/** 타이밍 루프가 늦게 깨어나도 다음 타격을 먼저 예약해 둘 만큼의 앞. 정지·탐색 때는 [JangdanSamplePlayer.silence]로 비운다. */
internal const val JANGDAN_LOOKAHEAD_MS = 120.0
