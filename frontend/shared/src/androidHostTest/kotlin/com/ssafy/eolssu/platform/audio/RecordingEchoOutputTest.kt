package com.ssafy.eolssu.platform.audio

import android.media.AudioDeviceInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordingEchoOutputTest {
    private val prepared = JangdanOutputSnapshot(
        frameZeroNanos = 1_000_000_000L, epoch = 1, volume = 5, deviceId = 2,
        deviceType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, underruns = 0, clipped = false, events = emptyList(),
    )

    @Test
    fun stableOutputRetainsCalibrationDespiteSmallHardwareClockRefinement() {
        assertTrue(sameOutput(prepared, prepared.copy(frameZeroNanos = 1_000_020_000L)))
    }

    @Test
    fun volumeRouteFlushAndBufferFaultsInvalidateCalibration() {
        val changed = listOf(
            prepared.copy(volume = 6), prepared.copy(deviceId = 3),
            prepared.copy(deviceType = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP),
            prepared.copy(epoch = 2), prepared.copy(underruns = 1), prepared.copy(clipped = true),
        )
        for (output in changed) assertFalse(sameOutput(prepared, output), output.toString())
    }

    @Test
    fun missingHardwareClockCannotKeepCalibrationValid() {
        assertFalse(sameOutput(prepared, prepared.copy(frameZeroNanos = null)))
    }
}
