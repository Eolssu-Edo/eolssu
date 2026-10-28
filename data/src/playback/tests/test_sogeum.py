"""Instrument interpretation regression cases; raw CAT stays outside Git."""
import unittest
from copy import deepcopy
import json
from pathlib import Path
import tempfile
from data.src.playback.tests.test_pipeline import score
from data.src.playback.jgb import convert_jgb
from data.src.playback.score import compile_score
from data.src.playback.midi import read_midi, write_midi


class InstrumentTests(unittest.TestCase):
    def test_jgb_sounding_pitches_timing_and_source_ids_are_preserved(self):
        path = Path(__file__).parents[1] / "examples/sogeum-jgb.json"
        document = json.loads(path.read_text(encoding="utf-8"))
        before = deepcopy(document)
        value, mapping = convert_jgb(document, instrument="sogeum", source_name=path.name)
        result = compile_score(value, "sogeum")
        self.assertEqual(document, before)
        self.assertEqual([n["pitch"] for n in result["events"]], [75, 77, 80])
        self.assertEqual(mapping["part_id"], "sogeum-example")
        self.assertEqual(mapping["cells"], 4)
        self.assertEqual(result["tempo_us"], 1_000_000)
        self.assertEqual([n["start_tick"] // result["ppqn"] for n in result["events"]], [0, 1, 3])
        for event in result["events"]:
            self.assertTrue(all(note_id in mapping["notes"] for note_id in event["parent_ids"]))
        with tempfile.TemporaryDirectory() as temp:
            midi_path = Path(temp) / "sogeum.mid"
            write_midi(result, midi_path)
            playback = read_midi(midi_path)
        self.assertTrue(all(cents == 0 for _, cents in playback["bends"]))

    def test_written_octave_and_sounding_input_stay_distinct(self):
        value = score([{"id":"a","pitch":60,"duration":1}])
        value["context"].update(pitch_mode="written", notation_octave_shift=1)
        self.assertEqual(compile_score(value,"sogeum")["events"][0]["pitch"],72)
        value["notes"][0]["pitch"] = 72
        value["context"].update(pitch_mode="sounding", notation_octave_shift=0)
        self.assertEqual(compile_score(value,"sogeum")["events"][0]["pitch"],72)

    def test_yoseong_only_added_when_explicit(self):
        value = score([{"id":"a","pitch":75,"duration":1}])
        self.assertIsNone(compile_score(value,"sogeum")["events"][0]["yoseong"])
        value["notes"][0]["yoseong"] = {}
        self.assertEqual(compile_score(value,"sogeum")["events"][0]["yoseong"]["mode"],"mixed")

    def test_style_limit_warns_and_technical_limit_fails_without_folding(self):
        value = score([{"id":"a","pitch":93,"duration":1}])
        result = compile_score(value,"sogeum")
        self.assertEqual(result["events"][0]["pitch"],93)
        self.assertTrue(any("전통 관행 음역 밖" in w for w in result["warnings"]))
        value["notes"][0]["pitch"] = 97
        with self.assertRaisesRegex(ValueError,"outside"):
            compile_score(value,"sogeum")
