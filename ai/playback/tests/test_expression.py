"""Audition pitch presets checked through saved MIDI and rendered waveforms."""
from copy import deepcopy
import json
from pathlib import Path
import tempfile
import unittest

import numpy as np

from playback.expression import apply_expression
from playback.midi import read_midi, write_midi
from playback.sampler import CatSampler, FS
from playback.score import compile_score


def performance(notes):
    example = Path(__file__).parents[1] / "examples/seryeongsan-first-jangdan.json"
    score = json.loads(example.read_text(encoding="utf-8"))
    score.update(quarter_bpm=60, notes=notes)
    return compile_score(score, "danso")


def saved_midi(perf):
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "expression.mid"
        write_midi(perf, path)
        return read_midi(path)


def sine_render(midi):
    sampler = CatSampler.__new__(CatSampler)
    sampler.instrument, sampler.attack, sampler.release, sampler.gain = "danso", 0, .05, 1
    sampler.zones = [{"low": 69, "high": 69, "root": 69, "detune": 0,
                      "file": "sine.wav", "volume": 1}]
    signal = .2 * np.sin(2 * np.pi * 440 * np.arange(5 * FS) / FS)
    sampler.samples = lambda zone: (signal, FS, "synthetic")
    return sampler.render(midi)[0]


class ExpressionTests(unittest.TestCase):
    def test_quarter_and_third_change_long_notes_but_keep_short_notes_and_timing(self):
        perf = performance([{"id": "short", "pitch": 69, "duration": "1/2"},
                            {"id": "long", "pitch": 69, "duration": 2}])
        original = deepcopy(perf)
        baseline = saved_midi(perf)
        for preset, depth in (("quarter", 25), ("third", 100 / 3)):
            with self.subTest(preset=preset):
                chosen = apply_expression(perf, preset)
                midi = saved_midi(chosen)
                self.assertEqual(perf, original)
                self.assertEqual(chosen["events"], original["events"])
                self.assertEqual(midi["notes"], baseline["notes"])
                self.assertEqual(midi["cc11"], baseline["cc11"])
                self.assertTrue(all(c == 0 for t, c in midi["bends"] if t <= .5))
                bends = [c for t, c in midi["bends"] if .5 < t < 2.5]
                self.assertAlmostEqual(max(bends), depth, delta=.08)
                self.assertAlmostEqual(min(bends), -depth, delta=.08)
                self.assertEqual(midi["bends"][-1][1], 0)

    def test_none_restores_legacy_output_and_presets_are_validated(self):
        perf = performance([{"id": "a", "pitch": 69, "duration": 2}])
        chosen = apply_expression(perf, "quarter")
        self.assertEqual(saved_midi(apply_expression(chosen, "none")), saved_midi(perf))
        chosen["events"][0]["expression"][0] = 0
        self.assertEqual(perf["events"][0]["expression"][0], 127)
        for preset in ("eighth", None, []):
            with self.assertRaisesRegex(ValueError, "preset"):
                apply_expression(perf, preset)

    def test_authored_pitch_and_mixed_yoseong_are_not_added_twice(self):
        for mode in ("pitch", "mixed"):
            with self.subTest(mode=mode):
                perf = performance([{"id": "a", "pitch": 69, "duration": 2,
                                     "yoseong": {"mode": mode, "cents": 8, "hz": 3}}])
                self.assertEqual(saved_midi(apply_expression(perf, "third")), saved_midi(perf))

    def test_authored_amplitude_remains_while_optional_pitch_is_added(self):
        perf = performance([{"id": "a", "pitch": 69, "duration": 2,
                             "yoseong": {"mode": "amplitude", "depth": .2}}])
        baseline = saved_midi(perf)
        midi = saved_midi(apply_expression(perf, "quarter"))
        self.assertEqual(midi["cc11"], baseline["cc11"])
        self.assertGreater(max(c for _, c in midi["bends"]), 24.9)
        self.assertEqual(max(c for _, c in baseline["bends"]), 0)

    def test_rendered_pcm_contains_the_requested_frequency_width_and_delayed_attack(self):
        perf = performance([{"id": "a", "pitch": 69, "duration": 2}])
        plain = sine_render(saved_midi(perf))
        for preset, depth in (("quarter", 25), ("third", 100 / 3)):
            with self.subTest(preset=preset):
                audio = sine_render(saved_midi(apply_expression(perf, preset)))
                np.testing.assert_array_equal(audio[:round(.22 * FS)], plain[:round(.22 * FS)])
                # Consecutive positive zero crossings measure actual PCM periods.
                signal = audio[round(.45 * FS):round(1.7 * FS)]
                crossings = np.where((signal[:-1] <= 0) & (signal[1:] > 0))[0]
                times = crossings - signal[crossings] / (signal[crossings + 1] - signal[crossings])
                cents = 1200 * np.log2(FS / np.diff(times) / 440)
                self.assertAlmostEqual(float(max(cents)), depth, delta=.2)
                self.assertAlmostEqual(float(min(cents)), -depth, delta=.2)


if __name__ == "__main__":
    unittest.main()
