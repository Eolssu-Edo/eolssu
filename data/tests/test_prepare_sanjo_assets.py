import sys
import json
import tempfile
import unittest
import wave
from pathlib import Path
from unittest.mock import patch

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))
from prepare_sanjo_assets import (crop_frames, write_audio, split_units, remove_previous_generated_files,
                                 assign_pitch, unit_analysis, short_pitch_frames, daruchigi_analysis,
                                 validate_mapping_source, validate_inputs, digest)


# Small fixed observations from the same-source 18-group reference. These are
# hypothesis labels, not an independently annotated instrument tuning standard.
REFERENCE_HZ = [267.276, 319.491, 345.631, 372.138, 417.916, 461.070, 505.216,
                533.311, 625.570, 686.686, 742.623, 833.737, 936.797, 1021.643,
                1074.916, 1206.344, 1345.428, 1527.104]
REFERENCE = [{"hz": hz, "groupIndex": i+1, "register": ["저취", "평취", "역취"][i//7],
              "yulmyeong": ["임", "남", "무", "황", "태", "고", "중"][i % 7]}
             for i, hz in enumerate(REFERENCE_HZ)]


class PreparationTests(unittest.TestCase):
    def test_stale_mapping_cannot_label_another_source(self):
        sample = {"id": "ngc-sanjo-2550", "sha256": "ab" * 32}
        mapping = {"sampleId": "ngc-sanjo-basic-2550", "sourceSha256": "AB" * 32}
        validate_mapping_source(mapping, sample)
        for change in [{"sourceSha256": "0" * 64}, {"sampleId": "another-source"}]:
            with self.assertRaisesRegex(ValueError, "mapping belongs"):
                validate_mapping_source({**mapping, **change}, sample)

    def test_missing_score_is_rejected_during_input_preflight(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            raw = root / "source.wav"
            raw.write_bytes(b"source input")
            sample = {"id": "ngc-sanjo-2550", "rawPath": "source.wav",
                      "category": "기본음계", "sha256": digest(raw)}
            mapping = {"sampleId": "ngc-sanjo-basic-2550", "sourceSha256": digest(raw)}
            directory = root / "analysis" / sample["id"]
            directory.mkdir(parents=True)
            (directory / "analysis.json").write_text(json.dumps({"summary": sample}), encoding="utf-8")
            (directory / "frames.csv").write_text("seconds,f0_hz\n", encoding="utf-8")
            with patch("prepare_sanjo_assets.ROOT", root), self.assertRaises(FileNotFoundError):
                validate_inputs({"samples": [sample]}, mapping, root / "analysis",
                                {"rawPath": "missing.pdf", "sha256": "0" * 64})

    def test_cleanup_validates_all_candidates_before_deleting_any(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            intact, modified = root / "intact.wav", root / "modified.wav"
            intact.write_bytes(b"old generated asset")
            modified.write_bytes(b"user changed asset")
            previous = {"audio": [{"file": intact.name, "sha256": digest(intact)},
                                   {"file": modified.name, "sha256": "0" * 64}]}
            with self.assertRaisesRegex(ValueError, "Preserving a modified"):
                remove_previous_generated_files(root, previous, set())
            self.assertTrue(intact.exists())
            self.assertEqual(modified.read_bytes(), b"user changed asset")

    def test_reviewed_continuous_tone_restores_gap_release_and_retains_real_repeat(self):
        rows = [{"region_id": "region-001", "start_seconds": .6, "end_seconds": 3.4653},
                {"region_id": "region-002", "start_seconds": 3.74, "end_seconds": 6.2253},
                {"region_id": "region-003", "start_seconds": 8.92, "end_seconds": 16.3053}]
        review = [{"unitId": "group-001", "sourceRegionIds": ["region-001", "region-002"],
                   "startSeconds": .6, "endSeconds": 7.1, "reason": "continuous periodic waveform across RMS gap"}]
        measurement = {"summary": {"id": "ngc-sanjo-2552"}, "regions": rows}
        result = split_units(measurement, review)
        self.assertEqual(len(result), 2)
        self.assertEqual(result[0]["source_region_ids"], ["region-001", "region-002"])
        self.assertEqual((result[0]["start_seconds"], result[0]["end_seconds"]), (.6, 7.1))
        self.assertEqual(result[1]["unit_id"], "region-003")
        # Same pitch alone must not merge the separately articulated noniro pair.
        noniro = {"summary": {"id": "ngc-sanjo-2553"}, "regions": rows[:2]}
        self.assertEqual(len(split_units(noniro)), 2)

    def test_pitches_missing_from_basic13_have_reference_labels(self):
        for hz, register, yul in [(356.494, "저취", "무"), (938.413, "평취", "고"),
                                  (1533.619, "역취", "황"), (1033.116, "평취", "중")]:
            result = assign_pitch(hz, REFERENCE)
            self.assertEqual((result["reference_register"], result["inferred_yulmyeong"]), (register, yul))

    def test_wide_vibrato_is_not_rejected_as_unmatched(self):
        unit = {"start_seconds": 0, "end_seconds": 2}
        frames = [{"seconds": i / 100, "accepted": True, "f0_hz": 357 + 20*np.sin(i*np.pi/10)}
                  for i in range(201)]
        result = unit_analysis(unit, frames, REFERENCE)
        self.assertGreater(result["span_cents"], 100)
        self.assertEqual(result["inferred_yulmyeong"], "무")

    def test_context_can_select_jung_while_preserving_nearest_im_alternative(self):
        for hz, group in [(529.801, 7), (1050.862, 14)]:
            self.assertEqual(assign_pitch(hz, REFERENCE)["inferred_yulmyeong"], "임")
            result = assign_pitch(hz, REFERENCE, {"groupIndex": group, "reason": "reviewed sequence"})
            self.assertEqual(result["inferred_yulmyeong"], "중")
            self.assertEqual(result["alternative_matches"][0]["yulmyeong"], "임")
            self.assertTrue(result["ambiguous_between_candidates"])

    def test_unmeasurable_or_distant_pitch_does_not_receive_arbitrary_label(self):
        for hz in [None, 0, float("nan"), 3000]:
            with self.assertRaises(ValueError):
                assign_pitch(hz, REFERENCE)

    def test_short_pitch_analysis_labels_both_anchors_not_gesture_average(self):
        rate = 48000
        t = np.arange(rate) / rate
        hz = np.where((t < .3) | ((t >= .5) & (t < .7)), 267., 319.)
        mono = .2*np.sin(2*np.pi*np.cumsum(hz)/rate)
        frames = short_pitch_frames(np.column_stack([mono, mono]), rate)
        unit = {"start_seconds": 0, "end_seconds": 1}
        review = {"startWindows": [[.1, .2], [.55, .65]],
                  "destinationWindows": [[.35, .45], [.8, .9]]}
        result = daruchigi_analysis(unit, frames, REFERENCE, review)
        self.assertEqual(result["lower_anchor"]["inferred_yulmyeong"], "임")
        self.assertEqual(result["destination"]["inferred_yulmyeong"], "남")
        self.assertAlmostEqual(result["destination"]["weighted_median_hz"], 319, delta=1)

    def test_staccato_uses_short_attacks_instead_of_bridged_long_region(self):
        measurement = {"summary": {"id": "ngc-sanjo-2555"},
                       "regions": [{"start_seconds": 0., "end_seconds": 1.}],
                       "articulations": [{"attack_id": "attack-0001", "start_seconds": .1, "end_seconds": .3},
                                         {"attack_id": "attack-0002", "start_seconds": .5, "end_seconds": .7}]}
        units = split_units(measurement)
        self.assertEqual(len(units), 2)
        self.assertEqual([(u["start_seconds"], u["end_seconds"]) for u in units], [(.1, .3), (.5, .7)])
        self.assertTrue(all(u["kind"] == "short_rms_articulation" for u in units))

    def test_cleanup_cannot_remove_modified_or_outside_assets(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "output"
            output.mkdir()
            path = output / "old.wav"
            path.write_bytes(b"changed")
            with self.assertRaises(ValueError):
                remove_previous_generated_files(output, {"audio": [{"file": "old.wav", "sha256": "old"}]}, set())
            with self.assertRaises(ValueError):
                remove_previous_generated_files(output, {"audio": [{"file": "../outside.wav", "sha256": "old"}]}, set())
            self.assertEqual(path.read_bytes(), b"changed")

    def test_crop_preserves_exact_source_frames_and_rejects_bad_bounds(self):
        source = np.arange(20).reshape(10, 2)
        clip, first, last = crop_frames(source, 10, .2, .7)
        np.testing.assert_array_equal(clip, source[2:7])
        self.assertEqual((first, last), (2, 7))
        for start, end in [(-.1, .5), (.5, .5), (.5, 1.1)]:
            with self.assertRaises(ValueError):
                crop_frames(source, 10, start, end)

    def test_float_headroom_becomes_pcm16_without_wrap_or_source_mutation(self):
        source = np.array([[1.02, 1.02], [-1.02, -1.02], [0., 0.]])
        original = source.copy()
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "clip.wav"
            info = write_audio(path, source, 44100)
            with wave.open(str(path)) as stream:
                self.assertEqual((stream.getnchannels(), stream.getsampwidth(), stream.getframerate()), (1, 2, 44100))
                pcm = np.frombuffer(stream.readframes(3), dtype="<i2")
            self.assertGreater(pcm[0], 32000)
            self.assertLess(pcm[1], -32000)
            self.assertAlmostEqual(info["gain"], .999 / 1.02)
            np.testing.assert_array_equal(source, original)


if __name__ == "__main__":
    unittest.main()
