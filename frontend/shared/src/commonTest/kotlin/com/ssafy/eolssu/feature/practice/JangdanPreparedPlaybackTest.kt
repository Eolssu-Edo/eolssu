package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.audio.JangdanClip
import com.ssafy.eolssu.core.audio.JangdanPlan
import com.ssafy.eolssu.core.audio.JangdanSamplePlayer
import com.ssafy.eolssu.core.audio.planJangdan
import com.ssafy.eolssu.core.model.JangdanPlayback
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class JangdanPreparedPlaybackTest {
    private val plan = planJangdan(JangdanPlayback("12", "4박", 0, 4,
        listOf("덩", "덕", "궁", "덕"), listOf(4)), 77, 1)!!

    @Test fun adoptedPlaybackSilencesAtItsEndWhileTheUiIsBlocked() = runTest {
        val audioScheduler = TestCoroutineScheduler()
        val player = PreparedPlayer(plan, StandardTestDispatcher(audioScheduler) + audioScheduler)
        val session = JangdanSession(player, backgroundScope)
        var tick: Double? = .02
        session.start(plan, true, { tick }, allowPreparedPlayback = true)
        runCurrent()
        audioScheduler.runCurrent()
        assertEquals(1, player.adoptions)
        assertEquals(0, player.stops)
        assertEquals(0, player.begins)
        tick = null
        audioScheduler.advanceTimeBy(6)
        assertEquals(1, player.silences, "The audio observer must silence without waiting for the UI")
        assertEquals(0, player.hits, "Prepared output must not receive duplicate attacks")
        assertEquals(JangdanStatus.Playing, session.status.value, "Owner cleanup is still queued on the blocked UI")
        runCurrent()
        assertEquals(JangdanStatus.Idle, session.status.value)
        assertEquals(1, player.stops)
    }

    @Test fun recordingAdoptsWithoutStoppingOrRepeatingNativeAttacks() = runTest {
        val player = PreparedPlayer(plan)
        val session = JangdanSession(player, backgroundScope)
        var tick = .02
        var starts = 0
        session.start(plan, accompanyingModel = true, tick = { tick }, startAtTick = tick,
            allowPreparedPlayback = true, onStarted = { starts++ })
        runCurrent()
        assertEquals(1, player.adoptions)
        assertEquals(0, player.stops)
        assertEquals(0, player.begins)
        assertEquals(1, starts)
        assertEquals(JangdanStatus.Playing, session.status.value)
        tick = 4.01
        advanceTimeBy(5); runCurrent()
        assertEquals(0, player.hits)
        session.stop(); runCurrent()
        assertEquals(1, player.stops)
        assertEquals(JangdanStatus.Idle, session.status.value)
    }

    @Test fun normalPlaybackKeepsTheExistingClockAndCannotConsumePreparation() = runTest {
        val player = PreparedPlayer(plan)
        val session = JangdanSession(player, backgroundScope)
        session.start(plan, accompanyingModel = true, tick = { 0.0 })
        runCurrent()
        assertEquals(0, player.adoptions)
        assertEquals(1, player.stops)
        assertEquals(1, player.begins)
        assertEquals(1, player.hits)
        session.stop()
    }

    @Test fun stoppedPreparationFallsBackAndOutputInterruptionReleasesTheSchedule() = runTest {
        val player = PreparedPlayer(plan)
        val session = JangdanSession(player, backgroundScope)
        session.start(plan, true, { 0.0 }, allowPreparedPlayback = true)
        runCurrent()
        player.interrupted = true
        advanceTimeBy(5); runCurrent()
        assertEquals(JangdanStatus.Interrupted, session.status.value)
        assertEquals(1, player.stops)
        player.interrupted = false
        session.start(plan, true, { 1.0 }, resumeAfterTick = 1.0, allowPreparedPlayback = true)
        runCurrent()
        assertEquals(1, player.adoptions)
        assertEquals(1, player.begins)
        assertEquals(0, player.hits)
        session.stop()
    }
}

private class PreparedPlayer(
    private var prepared: JangdanPlan?,
    override val timingContext: kotlin.coroutines.CoroutineContext = kotlin.coroutines.EmptyCoroutineContext,
) : JangdanSamplePlayer {
    override val available = true
    override var interrupted = false
    var adoptions = 0
    var stops = 0
    var begins = 0
    var hits = 0
    var silences = 0
    override suspend fun prepare(clips: Set<JangdanClip>) = true
    override fun begin(accompanyingModel: Boolean): Boolean { begins++; return true }
    override fun play(clip: JangdanClip, gain: Float): Boolean { hits++; return true }
    override fun adoptPreparedPlayback(plan: JangdanPlan): Boolean {
        if (prepared != plan) return false
        prepared = null
        adoptions++
        return true
    }
    override fun silence() { silences++; prepared = null }
    override fun stop() { stops++; silence() }
    override fun release() = stop()
}
