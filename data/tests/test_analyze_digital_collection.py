"""Synthetic checks for measurements, not real-instrument correctness claims."""

import sys
import unittest
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]/"src"))
from analyze_digital_collection import measure, modulation, reference_matches


class CollectionTests(unittest.TestCase):
    def test_native_rates_preserve_pitch(self):
        for rate in (44100, 48000):
            with self.subTest(rate=rate):
                t = np.arange(rate)/rate
                signal = .2*np.sin(2*np.pi*440*t)
                frames, _, _, _, result = measure(signal[:, None], rate)
                self.assertEqual(result["sample_rate_hz"], rate)
                self.assertLess(abs(1200*np.log2(result["pitch"]["median_hz"]/440)), 3)
                self.assertTrue(frames)

    def test_modulation_distinguishes_periodic_pitch_from_glide(self):
        t = np.arange(250)*.02
        hz = 440*2**((90*np.sin(2*np.pi*5*t)+20*t)/1200)
        result = modulation(hz, .02)
        self.assertAlmostEqual(result["rate_hz"], 5, delta=.2)
        self.assertGreater(result["detrended_span_cents"], 150)
        self.assertIsNone(modulation(440*2**((20*t)/1200), .02)["rate_hz"])
        self.assertIsNone(modulation([440]*30, .02)["rate_hz"])

    def test_short_articulations_survive_separate_envelope(self):
        rate = 48000
        t = np.arange(rate)/rate
        signal = np.zeros(rate)
        for begin in (.1, .35, .6):
            mask = (t >= begin) & (t < begin+.08)
            signal[mask] = .2*np.sin(2*np.pi*440*t[mask])
        _, _, attacks, _, _ = measure(signal[:, None], rate)
        self.assertEqual(len(attacks), 3)
        self.assertAlmostEqual(attacks[0]["start_seconds"], .1, delta=.02)

    def test_antiphase_is_reported_and_silence_not_labeled(self):
        t = np.arange(48000)/48000
        wave = .2*np.sin(2*np.pi*440*t)
        _, regions, attacks, _, result = measure(np.column_stack([wave, -wave]), 48000)
        self.assertEqual(result["mono_power_to_channel_power"], 0)
        self.assertEqual(result["accepted_pitch_ratio_of_active"], 0)
        self.assertEqual(regions, [])
        self.assertEqual(attacks, [])

    def test_wide_melody_is_not_mislabeled_as_a_single_note(self):
        references = [{"segment_id": "tone-01", "median_hz": 440}]
        regions = [{"median_hz": 442, "span_cents": 1300, "valid_pitch_ratio": 1}]
        labels = {"tone-01": {"yulmyeong": "임", "status": "inferred"}}
        result = reference_matches(regions, references, labels)
        self.assertIsNone(result[0]["reference_tone_id"])
        regions[0]["span_cents"] = 20
        result = reference_matches(regions, references, labels)
        self.assertEqual(result[0]["reference_tone_id"], "tone-01")
        self.assertFalse(result[0]["eligible_for_scoring"])


if __name__ == "__main__":
    unittest.main()
