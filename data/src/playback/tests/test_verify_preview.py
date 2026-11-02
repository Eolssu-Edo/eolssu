"""Independent observer controls, authored without the production renderer."""
import unittest

import numpy as np

from data.src.playback.verify_preview import inspect_preview, select_expectation


RATE = 44100


def expectation(**checks):
    return {"schema": "preview-waveform-check/v1", "duration_seconds": 4.55, **checks}


def tone(hz, seconds, cents=None, gain=.2):
    time = np.arange(round(seconds * RATE)) / RATE
    frequency = np.full(len(time), hz) if cents is None else hz * 2 ** (cents(time) / 1200)
    return gain * np.sin(2 * np.pi * np.cumsum(frequency) / RATE)


class PreviewObserverTests(unittest.TestCase):
    def test_independently_authored_pitch_sequence_and_silence(self):
        wave = np.zeros(round(4.55 * RATE))
        for i, midi in enumerate((82, 80, 82, 80)):
            wave[i * RATE:(i + 1) * RATE] = tone(440 * 2 ** ((midi - 69) / 12), 1)
        expected = expectation(
            pitches=[{"id": str(i), "span": [i + .03, i + .97], "midi": midi}
                     for i, midi in enumerate((82, 80, 82, 80))],
            silences=[{"id": "tail", "span": [4.01, 4.55]}],
            boundaries=[{"id": "split", "at": 1, "from_midi": 82, "to_midi": 80}])
        self.assertTrue(inspect_preview(expected, wave, RATE)["passed"])
        wave[RATE:2 * RATE] = tone(440 * 2 ** ((81 - 69) / 12), 1)
        self.assertIn("1:pitch", inspect_preview(expected, wave, RATE)["failures"])

    def test_shifted_split_and_filled_rest_fail(self):
        wave = np.zeros(round(4.55 * RATE))
        split = round(1.12 * RATE)
        wave[:split] = tone(880, 1.12)
        wave[split:2 * RATE] = tone(440, .88)
        wave[3 * RATE:4 * RATE] = .1
        expected = expectation(boundaries=[{"id": "split", "at": 1, "from_midi": 81, "to_midi": 69}],
                               silences=[{"id": "rest", "span": [3.01, 3.99]}])
        report = inspect_preview(expected, wave, RATE)
        self.assertIn("split:boundary", report["failures"])
        self.assertIn("rest:silence", report["failures"])

    def test_pitch_sweep_requires_observed_change(self):
        wave = np.zeros(round(4.55 * RATE))
        wave[:2 * RATE] = tone(880, 2, lambda t: -200 * np.minimum(t / .5, 1))
        expected = expectation(sweeps=[{"id": "fall", "early": [.03, .12], "late": [1.0, 1.5],
                                       "delta_cents": [-230, -140]}])
        self.assertTrue(inspect_preview(expected, wave, RATE)["passed"])
        wave[:2 * RATE] = tone(880, 2)
        self.assertIn("fall:sweep", inspect_preview(expected, wave, RATE)["failures"])

    def test_vibrato_requires_frequency_and_depth_in_waveform(self):
        wave = np.zeros(round(4.55 * RATE))
        wave[:2 * RATE] = tone(880, 2, lambda t: 100 * np.sin(2 * np.pi * 4 * t))
        wave[2 * RATE:4 * RATE] = tone(880, 2)
        expected = expectation(vibratos=[{"id": "deep", "span": [.2, 1.8], "reference_span": [2.2, 3.8],
                                         "hz": 4, "amplitude_cents": [70, 130], "minimum_added_cents": 50}])
        self.assertTrue(inspect_preview(expected, wave, RATE)["passed"])
        wave[:2 * RATE] = tone(880, 2, lambda t: 100 * np.sin(2 * np.pi * 6 * t))
        self.assertIn("deep:vibrato", inspect_preview(expected, wave, RATE)["failures"])

    def test_accent_compares_same_file_plain_note(self):
        wave = np.zeros(round(4.55 * RATE))
        wave[:2 * RATE] = tone(880, 2, gain=.2)
        wave[2 * RATE:4 * RATE] = tone(880, 2, gain=.35)
        expected = expectation(levels=[{"id": "accent", "reference_span": [.1, .5], "span": [2.1, 2.5],
                                       "ratio": [1.5, 2.0]}])
        self.assertTrue(inspect_preview(expected, wave, RATE)["passed"])
        wave[2 * RATE:4 * RATE] = tone(880, 2, gain=.2)
        self.assertIn("accent:level", inspect_preview(expected, wave, RATE)["failures"])

    def test_invalid_or_empty_expectation_cannot_pass(self):
        wave = np.zeros(round(4.55 * RATE))
        with self.assertRaisesRegex(ValueError, "one check"):
            inspect_preview(expectation(), wave, RATE)
        with self.assertRaisesRegex(ValueError, "span"):
            inspect_preview(expectation(pitches=[{"id": "x", "span": [3, 5], "midi": 80}]), wave, RATE)

    def test_same_pitch_reattack_is_not_a_continuous_tone(self):
        wave = np.zeros(round(4.55 * RATE))
        wave[:4 * RATE] = tone(880, 4)
        expected = expectation(attacks=[{"id": "new-note", "at": 2, "reference_span": [2.1, 2.3]}])
        self.assertIn("new-note:attack", inspect_preview(expected, wave, RATE)["failures"])
        wave[round(1.99 * RATE):round(2.01 * RATE)] = 0
        self.assertTrue(inspect_preview(expected, wave, RATE)["passed"])

    def test_named_expectations_keep_shared_checks_and_reject_unknown_case(self):
        shared = expectation(silences=[{"id": "rest", "span": [1, 2]}])
        payload = {"schema": "preview-waveform-cases/v1", "shared": shared,
                   "cases": {"danso": {"pitches": [{"id": "main", "span": [0, 1], "midi": 80}]}}}
        chosen = select_expectation(payload, "danso")
        self.assertEqual(chosen["silences"], shared["silences"])
        self.assertEqual(chosen["pitches"][0]["midi"], 80)
        with self.assertRaisesRegex(ValueError, "named"):
            select_expectation(payload, "unknown")

    def test_spectral_diagnostic_does_not_pass_an_octave_error(self):
        wave = np.zeros(round(4.55 * RATE))
        wave[:RATE] = tone(880, 1)
        report = inspect_preview(expectation(pitches=[{"id": "wrong-octave", "span": [.1, .15], "midi": 69}]), wave, RATE)
        self.assertIn("wrong-octave:pitch", report["failures"])
        diagnostic = report["checks"][0]["diagnostic_windows"][0]
        self.assertAlmostEqual(diagnostic["acf_hz"], 880, delta=1)
        self.assertAlmostEqual(diagnostic["dominant_spectral_hz"], 880, delta=1)

    def test_truncated_waveform_fails_without_diagnostic_crash(self):
        expected = expectation(pitches=[{"id": "missing", "span": [2.1, 2.2], "midi": 80}])
        report = inspect_preview(expected, tone(880, 1), RATE)
        self.assertIn("file-duration", report["failures"])
        self.assertIn("missing:pitch", report["failures"])


if __name__ == "__main__":
    unittest.main()
