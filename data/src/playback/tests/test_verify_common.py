"""Observer controls: authored waveforms, never the production renderer."""
import unittest

import numpy as np

from data.src.playback.verify_common import fundamental, inspect_waveform


RATE = 44100


def source():
    return {"schemaVersion": 1, "provenance": {"review": "reviewed"},
            "music": {"bpmUnit": "QUARTER", "ticksPerBeat": 1, "hwangHz": 440},
            "events": [{"id": str(i), "kind": kind, "startTick": i, "durationTick": 1,
                        "yul": None if kind == "REST" else "황", "octave": None if kind == "REST" else 0,
                        "breathAfter": False, "sigimsae": []}
                       for i, kind in enumerate(("NOTE", "REST", "NOTE"))]}


def authored_wave(frequency=440):
    wave = np.zeros(round(3.55 * RATE))
    time = np.arange(RATE) / RATE
    envelope = np.minimum(1, time / .01) * np.minimum(1, (1 - time) / .05)
    for start in (0, 2 * RATE):
        wave[start:start + RATE] = .2 * np.sin(2 * np.pi * frequency * time) * envelope
    return wave


class CommonAudioObserverTests(unittest.TestCase):
    def test_frequency_observer_uses_waveform_not_target(self):
        time = np.arange(round(.08 * RATE)) / RATE
        for hz in (233.08188, 440, 880, 1567.9817):
            measured, periodicity = fundamental(np.sin(2 * np.pi * hz * time), RATE)
            self.assertAlmostEqual(measured, hz, delta=.3)
            self.assertGreater(periodicity, .95)

    def test_correct_authored_wave_passes(self):
        report = inspect_waveform(source(), 60, authored_wave(), RATE)
        self.assertTrue(report["passed"], report["failures"])
        self.assertEqual(report["source_nominal_seconds"], 3)
        self.assertEqual(len(report["silences"]), 1)

    def test_pitch_shift_is_detected(self):
        report = inspect_waveform(source(), 60, authored_wave(440 * 2 ** (1 / 12)), RATE)
        self.assertFalse(report["passed"])
        self.assertIn("0:pitch", report["failures"])

    def test_filled_rest_and_truncated_file_are_detected(self):
        wave = authored_wave()
        wave[RATE:2 * RATE] = .05
        report = inspect_waveform(source(), 60, wave, RATE)
        self.assertIn("1:rest", report["failures"])
        report = inspect_waveform(source(), 60, authored_wave()[:-RATE], RATE)
        self.assertIn("file-duration", report["failures"])

    def test_missing_attack_is_detected(self):
        wave = authored_wave()
        wave[:round(.12 * RATE)] = 0
        report = inspect_waveform(source(), 60, wave, RATE)
        self.assertIn("0:active-interval", report["failures"])

    def test_adjacent_same_pitch_requires_a_new_articulation(self):
        value = source()
        value["events"][1].update(kind="NOTE", yul="황", octave=0)
        value["events"][2].update(kind="REST", yul=None, octave=None)
        wave = np.zeros(round(3.55 * RATE))
        time = np.arange(2 * RATE) / RATE
        wave[:2 * RATE] = .2 * np.sin(2 * np.pi * 440 * time) * np.minimum(1, time / .01) * np.minimum(1, (2 - time) / .05)
        report = inspect_waveform(value, 60, wave, RATE)
        self.assertIn("1:onset-boundary", report["failures"])

    def test_unsupported_source_is_unavailable_not_passed(self):
        value = source()
        value["events"][0]["sigimsae"] = ["unresolved"]
        with self.assertRaisesRegex(ValueError, "ornaments"):
            inspect_waveform(value, 60, authored_wave(), RATE)


if __name__ == "__main__":
    unittest.main()
