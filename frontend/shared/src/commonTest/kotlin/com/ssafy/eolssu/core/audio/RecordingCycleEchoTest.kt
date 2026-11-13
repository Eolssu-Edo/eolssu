package com.ssafy.eolssu.core.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals

class RecordingCycleEchoTest {
    private val rate = 44_100
    private val period = 82_687.5
    private val origin = 44_100.0
    private val deong = JangdanClip(JangdanSample.Deong)
    private val deok = JangdanClip(JangdanSample.Deok)
    private val kung = JangdanClip(JangdanSample.Kung)
    private val lengths = mapOf(deong to 17_640, deok to 11_466, kung to 17_640)
    private val strikes = listOf(0 to deong, 3 to deong, 5 to deok, 6 to kung, 7 to deok).map { (tick, clip) ->
        CycleEchoStrike(tick * period / 9, clip, .65f, lengths.getValue(clip) + 5_292)
    }
    private fun input(playbackOrigin: Double = origin) = CycleEchoCalibrationInput(
        List(3) { (origin + it * period).roundToLong() }, period,
        2_205, (period - 2_205).toInt(), listOf(EchoQuietWindow(4_410, 39_000)),
        CycleEchoPlaybackReference(playbackOrigin, period, strikes),
    )
    private fun events(cycles: Int, at: Double = origin) = (0 until cycles).flatMap { cycle ->
        strikes.map { EchoPlaybackEvent(it.clip, (at + cycle * period + it.offsetFrames).roundToLong(), it.gain) }
    }
    private fun response(clip: JangdanClip, frame: Int): Float {
        val t = frame.toDouble() / rate
        val frequency = 1_237 + clip.sample.ordinal * 173
        return (exp(-t / .09) * (.14 * sin(2 * PI * 183 * t) +
            .12 * sin(2 * PI * frequency * t + 170 * t * t) + .06 * sin(2 * PI * 3_927 * t))).toFloat()
    }
    private fun capture(cycles: Int): FloatArray {
        val output = FloatArray((origin + (cycles + 1) * period).roundToInt())
        events(cycles).forEach { event ->
            repeat(lengths.getValue(event.clip) + 5_292) { i ->
                output[event.startFrame.toInt() + i] += response(event.clip, i) * event.gain
            }
        }
        return output
    }
    private fun pcm(capture: FloatArray, first: Long, count: Int = 882) =
        ShortArray(count) { (capture[first.toInt() + it] * 32768).roundToInt().toShort() }
    private fun energy(pcm: ShortArray) = pcm.sumOf { it.toDouble() * it }
    private fun fit(recorded: FloatArray = capture(3), configuration: CycleEchoCalibrationInput = input()): RecordingEchoModel {
        val result = CycleEchoCalibrator.calibrate(recorded, configuration)
        return assertIs<EchoCalibrationResult.Accepted>(result, result.toString()).model
    }

    private fun localEvents(cycle: Long, at: Double = origin) = (cycle - 1..cycle + 1).flatMap { index ->
        strikes.map { EchoPlaybackEvent(it.clip, (at + index * period + it.offsetFrames).roundToLong(), it.gain) }
    }.filter { it.startFrame >= 0 }

    private fun render(first: Long, output: List<EchoPlaybackEvent>, count: Int = 882): ShortArray = ShortArray(count) { i ->
        val sample = output.sumOf { event ->
            val relative = first + i - event.startFrame
            if (relative in 0 until lengths.getValue(event.clip) + 5_292L) response(event.clip, relative.toInt()).toDouble() * event.gain else 0.0
        }
        (sample * 32768).roundToInt().toShort()
    }

    @Test fun actualArirangOverlappingResponsesCalibrateAndReduceEchoFromThreeCycles() {
        val recorded = capture(5)
        val model = assertIs<EchoCalibrationResult.Accepted>(CycleEchoCalibrator.calibrate(recorded, input())).model
        val first = (origin + 3 * period).roundToLong() + 48_000
        val block = pcm(recorded, first)
        val before = energy(block)
        assertTrue(RecordingEchoFilter(model).process(block, block.size, first, events(5)))
        assertTrue(energy(block) < before * .35, "A clean overlapping response must be substantially reduced")
    }

    @Test fun stationaryRoomNoiseDoesNotHaveToRepeatLikeTheSpeakerResponse() {
        val random = Random(695)
        val recorded = capture(5)
        for (i in recorded.indices) recorded[i] += .012f * (random.nextFloat() - .5f)
        val model = fit(recorded)
        val first = (origin + 3 * period).roundToLong() + 48_000
        val block = pcm(recorded, first)
        val before = energy(block)
        assertTrue(RecordingEchoFilter(model).process(block, block.size, first, events(5)))
        assertTrue(energy(block) < before * .35)
    }

    @Test fun twoRepeatsCannotProduceACycleModel() {
        val rejected = assertIs<EchoCalibrationResult.Rejected>(CycleEchoCalibrator.calibrate(capture(3),
            input().copy(cycleStartFrames = input().cycleStartFrames.take(2))))
        assertEquals(EchoCalibrationGate.InsufficientRepeats, rejected.diagnostic?.gate)
    }

    @Test fun playingInInitialSilenceRejectsTheWholeCalibration() {
        val recorded = capture(3)
        for (i in 8_000..30_000) recorded[i] = (.04 * sin(2 * PI * 467 * i / rate)).toFloat()
        val rejected = assertIs<EchoCalibrationResult.Rejected>(CycleEchoCalibrator.calibrate(recorded, input()))
        assertEquals(RecordingEchoStatus.NotQuiet, rejected.reason)
    }

    @Test fun shortWindContaminationCannotHideInTheMiddleOrEdgesOfALoudCycle() {
        val configuration = input()
        for (phase in listOf(configuration.phaseStartFrame + 20, 48_000, configuration.phaseEndFrame - 2_000)) {
            val recorded = capture(3)
            val first = configuration.cycleStartFrames[1].toInt() + phase
            repeat(1_800) { i -> recorded[first + i] += (.2 * sin(2 * PI * 467 * i / rate)).toFloat() }
            assertIs<EchoCalibrationResult.Rejected>(CycleEchoCalibrator.calibrate(recorded, configuration), "phase=$phase")
        }
    }

    @Test fun performanceSuffixAndLaterCallerMutationCannotChangeTheLearnedResponse() {
        val recorded = capture(5)
        val firstModel = fit(recorded)
        val secondCapture = recorded.copyOf()
        secondCapture.fill(.8f, (origin + 3 * period).roundToInt())
        val secondModel = fit(secondCapture)
        recorded.fill(-.75f)
        val first = (origin + 3 * period).roundToLong() + 48_000
        val block = render(first, events(5))
        val other = block.copyOf()
        assertTrue(RecordingEchoFilter(firstModel).process(block, block.size, first, events(5)))
        assertTrue(RecordingEchoFilter(secondModel).process(other, other.size, first, events(5)))
        assertContentEquals(block, other)
    }

    @Test fun entireBlockAndSearchMarginMustRemainInsideTrainedPhase() {
        val model = fit()
        val anchor = (origin + 3 * period).roundToLong()
        for (phase in listOf(0, input().phaseStartFrame, input().phaseEndFrame - 882, period.toInt() - 500)) {
            val first = anchor + phase
            val block = render(first, events(5))
            val original = block.copyOf()
            assertFalse(RecordingEchoFilter(model).process(block, block.size, first, events(5)), "phase=$phase")
            assertContentEquals(original, block)
        }
    }

    @Test fun fractionalPeriodsUseAbsoluteAnchorsWithoutAccumulatingRoundingDrift() {
        for (playbackOrigin in listOf(origin, origin - 1_000 * period)) {
            val model = fit(configuration = input(playbackOrigin))
            for (cycle in listOf(3L, 4L, 1_001L, 100_000L, 1_000_001L)) {
                val first = (playbackOrigin + cycle * period).roundToLong() + 48_000
                if (first < 0) continue
                val output = localEvents(cycle, playbackOrigin)
                val block = render(first, output)
                val before = energy(block)
                assertTrue(RecordingEchoFilter(model).process(block, block.size, first, output), "origin=$playbackOrigin cycle=$cycle")
                assertTrue(energy(block) < before * .35, "origin=$playbackOrigin cycle=$cycle")
            }
        }
    }

    @Test fun missingEarlierTailChangedGainAndExtraOutputKeepTheOriginalBlock() {
        val model = fit()
        val cycle = 3L
        val first = (origin + cycle * period).roundToLong() + 48_000
        val output = localEvents(cycle)
        val earlier = output.single { it.startFrame == (origin + cycle * period + strikes[1].offsetFrames).roundToLong() }
        val mismatches = listOf(output - earlier, output.map { if (it == earlier) it.copy(gain = .3f) else it },
            output + EchoPlaybackEvent(kung, first + 100, .65f))
        for (changed in mismatches) {
            val block = render(first, output)
            val original = block.copyOf()
            assertFalse(RecordingEchoFilter(model).process(block, block.size, first, changed))
            assertContentEquals(original, block)
        }
    }

    @Test fun shiftedSectionNeedsActualHistoryAndThenUsesItsOwnCyclePhase() {
        val recordingStart = (origin + 3 * period).roundToLong()
        val shiftedOrigin = origin + 3 * period - 55_125
        val model = fit(configuration = input(shiftedOrigin))
        val output = events(4, shiftedOrigin).filter { it.startFrame >= recordingStart }
        val first = recordingStart + 500
        val partial = render(first, output)
        val untouched = partial.copyOf()
        assertFalse(RecordingEchoFilter(model).process(partial, partial.size, first, output))
        assertContentEquals(untouched, partial)
        val later = (shiftedOrigin + period).roundToLong() + 48_000
        val block = render(later, output)
        val before = energy(block)
        assertTrue(RecordingEchoFilter(model).process(block, block.size, later, output))
        assertTrue(energy(block) < before * .35)
    }

    @Test fun pickupBeforeFirstScheduledCycleIsNotInventedFromTheTrainingAudio() {
        val recordingStart = (origin + 3 * period).roundToLong()
        val shiftedOrigin = origin + 3 * period + 20_000
        val model = fit(configuration = input(shiftedOrigin))
        val output = events(3, shiftedOrigin)
        val block = ShortArray(882) { (3_000 * sin(2 * PI * 460 * it / rate)).roundToInt().toShort() }
        val original = block.copyOf()
        assertFalse(RecordingEchoFilter(model).process(block, block.size, recordingStart + 500, output))
        assertContentEquals(original, block)
    }

    @Test fun unrelatedNearEndToneRemainsByteIdentical() {
        val model = fit()
        val first = (origin + 3 * period).roundToLong() + 48_000
        val block = ShortArray(882) { (5_000 * sin(2 * PI * 460 * it / rate)).roundToInt().toShort() }
        val original = block.copyOf()
        assertFalse(RecordingEchoFilter(model).process(block, block.size, first, events(5)))
        assertContentEquals(original, block)
    }

    @Test fun weakNearEndComponentIsPreservedWhenMixedWithAKnownEcho() {
        val model = fit()
        val first = (origin + 3 * period).roundToLong() + 48_000
        val output = events(5)
        val clean = render(first, output, 4_410)
        val mixed = ShortArray(clean.size) { (clean[it] + 400 * sin(2 * PI * 831 * it / rate)).roundToInt().toShort() }
        val wind = ShortArray(clean.size) { (mixed[it] - clean[it]).toShort() }
        assertTrue(RecordingEchoFilter(model).process(clean, clean.size, first, output))
        assertTrue(RecordingEchoFilter(model).process(mixed, mixed.size, first, output))
        val retained = ShortArray(clean.size) { (mixed[it] - clean[it]).toShort() }
        assertTrue(energy(retained) / energy(wind) in .85..1.15, "Known weak component energy must stay within the comparison tolerance")
    }
}
