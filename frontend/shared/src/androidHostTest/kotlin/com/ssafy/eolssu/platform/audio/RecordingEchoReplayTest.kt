package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.EchoCalibrationResult
import com.ssafy.eolssu.core.audio.EchoCalibrationStrike
import com.ssafy.eolssu.core.audio.EchoPlaybackEvent
import com.ssafy.eolssu.core.audio.EchoQuietWindow
import com.ssafy.eolssu.core.audio.JangdanClip
import com.ssafy.eolssu.core.audio.JangdanSample
import com.ssafy.eolssu.core.audio.RecordingEchoCalibrator
import com.ssafy.eolssu.core.audio.RecordingEchoFilter
import com.ssafy.eolssu.core.audio.RecordingEchoStatus
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Optional local evidence replay. User microphone files are deliberately not repository fixtures. */
class RecordingEchoReplayTest {
    @Test fun rejectsContaminatedGalaxyCalibrationAndUsesCleanCalibration() {
        val path = System.getenv("EOLSSU_ECHO_FIXTURES")
        assumeTrue("Set EOLSSU_ECHO_FIXTURES to the local eolssu-stream-manual-695 folder", path != null)
        val root = File(requireNotNull(path))
        val timeline = Json.parseToJsonElement(File(root, "timeline.json").readText().removePrefix("\uFEFF")).jsonObject
        assertEquals(44100, timeline.getValue("rate").jsonPrimitive.int)
        val events = timeline.getValue("events").jsonArray.map { it.jsonObject }
        fun clip(kind: String) = JangdanClip(JangdanSample.entries.first { it.file == kind })
        val lengths = events.associate { clip(it.getValue("kind").jsonPrimitive.content) to it.getValue("asset_frames").jsonPrimitive.int }
        val takes = listOf(
            Triple("170050", 24247, "5a1d059aadfa2794afe00d591fb61f4c2e8271c6a5bfeea21c9de06603b7a410"),
            Triple("170621", 25772, "d1dbbb09f08b7fb0872998add5d87cc740c2a13356a5554533fcf37a0fedae16"),
        )
        for ((name, shift, expectedDigest) in takes) {
            val bytes = File(root, "take-20261110-$name/mic.s16le").readBytes()
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(expectedDigest, digest)
            val pcmBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val raw = ShortArray(pcmBuffer.remaining()).also { pcmBuffer.get(it) }
            val input = FloatArray(raw.size) { raw[it] / 32768f }
            val strikes = events.filter { it.getValue("partition").jsonPrimitive.content == "calibration" }.map {
                EchoCalibrationStrike(clip(it.getValue("kind").jsonPrimitive.content),
                    shift + it.getValue("reference_frame").jsonPrimitive.int.toLong(), it.getValue("gain").jsonPrimitive.float)
            }
            val quiet = listOf(EchoQuietWindow(shift + 13230L, shift + 79380L),
                EchoQuietWindow(shift + 414540L, shift + 454230L))
            val result = RecordingEchoCalibrator.calibrate(input, strikes, lengths, quiet)
            if (name == "170050") {
                val rejected = assertIs<EchoCalibrationResult.Rejected>(result)
                assertEquals(RecordingEchoStatus.NotQuiet, rejected.reason)
                println("$name rejected=NotQuiet diagnostic=${rejected.diagnostic}")
                continue
            }
            val model = assertIs<EchoCalibrationResult.Accepted>(result).model
            val playback = events.filter { it.getValue("partition").jsonPrimitive.content == "holdout" }.map {
                EchoPlaybackEvent(clip(it.getValue("kind").jsonPrimitive.content),
                    shift + it.getValue("reference_frame").jsonPrimitive.int.toLong(), it.getValue("gain").jsonPrimitive.float)
            }
            val output = raw.copyOf()
            val filter = RecordingEchoFilter(model)
            var applied = 0
            val block = ShortArray(882)
            var elapsedNanos = 0L
            for (first in raw.indices step block.size) {
                val count = min(block.size, raw.size - first)
                raw.copyInto(block, 0, first, first + count)
                val start = System.nanoTime()
                val changed = filter.process(block, count, first.toLong(), playback)
                elapsedNanos += System.nanoTime() - start
                if (changed) applied++
                block.copyInto(output, first, 0, count)
                val before = (0 until count).sumOf { raw[first + it].toDouble() * raw[first + it] }
                val after = (0 until count).sumOf { block[it].toDouble() * block[it] }
                assertTrue(after <= before + count, "energy increased at $first")
            }
            assertTrue(applied > 10, "A correction that always bypasses does not meet acceptance")
            val holdoutStart = shift + (10.5 * 44100).roundToInt()
            val before = raw.drop(holdoutStart).sumOf { it.toDouble() * it }
            val after = output.drop(holdoutStart).sumOf { it.toDouble() * it }
            val reduction = 10 * log10(before / after)
            println("$name accepted similarity=${model.minimumSimilarity} responseResidual=${model.maximumResponseResidual} appliedBlocks=$applied rmsReductionDb=$reduction processingMs=${elapsedNanos / 1e6}")
            // Diagnostics must not change the already verified PCM output for this take.
            val buffer = ByteBuffer.allocate(output.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            output.forEach { buffer.putShort(it) }
            val outputDigest = MessageDigest.getInstance("SHA-256").digest(buffer.array()).joinToString("") { "%02x".format(it) }
            assertEquals("5b32a16c0cdeff40a5c90527a1a3e04125ff86def9817a8042a9d1e00d22a28a", outputDigest)
            System.getenv("EOLSSU_ECHO_REPLAY_OUTPUT")?.let { outputPath ->
                val destination = File(outputPath).also { it.mkdirs() }
                File(destination, "guarded-$name.s16le").writeBytes(buffer.array())
            }
        }
    }
}
