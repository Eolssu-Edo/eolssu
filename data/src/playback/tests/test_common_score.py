"""Service v1 -> the public playback path, with independent musical expectations."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from data.src.playback.__main__ import run
from data.src.playback.common_score import convert_common_score
from data.src.playback.midi import read_midi, write_midi
from data.src.playback.score import compile_score
from data.src.playback.tests._helpers import sampler

FIXTURE = Path(__file__).resolve().parents[3] / "tests/fixtures/scores/ngc-standard-2022-p173.score.json"


class CommonScorePlaybackTests(unittest.TestCase):
    def test_small_score_preserves_rearticulation_rest_and_exact_tuning(self):
        source = small_score()
        normalized, mapping = convert_common_score(source, bpm=60, instrument="danso", input_sha256="a"*64)
        perf = compile_score(normalized, "danso")
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "out.mid"
            write_midi(perf, path)
            midi = read_midi(path)
        self.assertEqual([n["pitch"] for n in midi["notes"]], [68, 68])
        self.assertEqual([e["parent_ids"] for e in perf["events"]], [["c0-j1-r0-u0"], ["c0-j2-r0-u0"]])
        self.assertAlmostEqual(midi["notes"][0]["start"], 1)
        self.assertAlmostEqual(midi["notes"][1]["start"], 2)
        self.assertAlmostEqual(midi["notes"][0]["end"] + midi["notes"][0]["release_seconds"], 2, delta=.0001)
        self.assertAlmostEqual(midi["notes"][1]["end"] + midi["notes"][1]["release_seconds"], 3, delta=.0001)
        self.assertEqual(mapping["score_seconds"], 4)
        self.assertAlmostEqual(normalized["context"]["tuning_a_hz"], 440.0042656609946)
        self.assertAlmostEqual(mapping["events"][1]["target_hz"], 311.13 * 2**(5/12))

    def test_breath_ends_inside_slot_without_moving_following_note(self):
        source = small_score()
        source["events"][1]["breathAfter"] = True
        normalized, _ = convert_common_score(source, bpm=60, instrument="danso", input_sha256="a"*64)
        perf = compile_score(normalized, "danso")
        a, b = perf["events"]
        sec_tick = perf["tempo_us"] / 1e6 / perf["ppqn"]
        self.assertAlmostEqual(a["end_tick"] * sec_tick + a["release_seconds"], 1.88, delta=.0001)
        self.assertAlmostEqual(b["start_tick"] * sec_tick, 2)

    def test_each_bpm_unit_preserves_beat_seconds(self):
        for unit in ("QUARTER", "DOTTED_QUARTER", "JEONGGAN", "JANGDAN_BEAT"):
            with self.subTest(unit=unit):
                source = small_score()
                source["music"]["bpmUnit"] = unit
                normalized, mapping = convert_common_score(source, bpm=60, instrument="danso", input_sha256="a"*64)
                perf = compile_score(normalized, "danso")
                self.assertAlmostEqual(perf["end_tick"] * perf["tempo_us"] / 1e6 / perf["ppqn"], 4, delta=.00001)
                self.assertEqual(mapping["score_seconds"], 4)

    def test_rejects_inconsistent_or_unresolved_input_before_asset_loading(self):
        changes = [
            (lambda s: s["events"][1].update(sigimsae=["농음"]), "sigimsae"),
            (lambda s: s["events"][1].update(startTick=2), "시간축"),
            (lambda s: s["events"][1].update(id=s["events"][0]["id"]), "중복"),
            (lambda s: s["columns"][0]["cells"][1]["units"][0].update(symbol="청중"), "표시"),
            (lambda s: s["columns"][0]["cells"][1]["units"][0].update(symbol="-"), "표시"),
            (lambda s: s["provenance"].update(review="rejected"), "rejected"),
            (lambda s: s["score"].update(instrument="SOGEUM"), "instrument"),
            (lambda s: s["music"].update(ticksPerBeat=7), "Unrepresentable"),
            (lambda s: s["music"].update(ticksPerBeat=1.0), "정수"),
        ]
        for change, message in changes:
            with self.subTest(message=message), tempfile.TemporaryDirectory() as tmp:
                source = small_score()
                change(source)
                path = Path(tmp) / "input.json"
                path.write_text(json.dumps(source), encoding="utf-8")
                with patch("data.src.playback.__main__.CatSampler") as bank:
                    with self.assertRaisesRegex(ValueError, message):
                        run(path, Path(tmp)/"missing-assets.json", Path(tmp)/"out", ["danso"], common_bpm=60, audio_format="wav")
                    bank.assert_not_called()
                self.assertFalse((Path(tmp)/"out").exists())

    def test_explicit_tempo_instrument_and_source_provenance_are_required(self):
        source = small_score()
        with self.assertRaisesRegex(ValueError, "common-bpm"):
            convert_common_score(source, bpm=None, instrument="danso", input_sha256="a"*64)
        source["provenance"]["review"] = "inferred"
        normalized, mapping = convert_common_score(source, bpm=60, instrument="danso", input_sha256="a"*64)
        self.assertEqual(mapping["provenance"]["review"], "inferred")
        self.assertEqual(normalized["source"]["review_status"], "source-review-unverified")

    def test_reviewed_arirang_reaches_midi_and_retains_common_identity(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            assets = root / "assets.json"
            assets.write_text('{"danso":"unused.zip"}', encoding="utf-8")
            fake = sampler()
            with patch("data.src.playback.__main__.CatSampler", return_value=fake):
                manifest = run(FIXTURE, assets, root / "out", ["danso"],
                               common_bpm=96, audio_format="wav")
            self.assertEqual((root / "out/source.common.json").read_bytes(), FIXTURE.read_bytes())
            report = json.loads((root / "out/danso.json").read_text(encoding="utf-8"))
            mapping = json.loads((root / "out/source-map.json").read_text(encoding="utf-8"))
            self.assertEqual(len(report["events"]), 61)
            self.assertEqual(report["events"][0]["parent_ids"], ["c0-j0-r0-u0"])
            self.assertEqual(report["events"][0]["pitch"], 68)  # 중 = E-flat4 + 5
            self.assertEqual(mapping["score"]["scoreVersion"], 1)
            self.assertEqual(mapping["provenance"]["review"], "reviewed")
            self.assertEqual(len([e for e in mapping["events"] if e["kind"] == "REST"]), 4)
            self.assertEqual(mapping["total_ticks"], 144)
            self.assertEqual(mapping["score_seconds"], 30)
            self.assertAlmostEqual(manifest["results"][0]["seconds"], 30.55, delta=.0001)


def small_score():
    """Authored four-beat fixture: rest, 중, newly attacked 중, rest."""
    value = json.loads(FIXTURE.read_text(encoding="utf-8"))
    value["music"].update(bpmUnit="QUARTER", ticksPerBeat=1, ticksPerJeonggan=1)
    value["columns"] = [{"index": 0, "cells": []}]
    value["events"] = []
    for i, symbol in enumerate(["쉼", "중", "중", "쉼"]):
        cid, eid = f"c0-j{i}", f"c0-j{i}-r0-u0"
        value["columns"][0]["cells"].append({"id": cid, "index": i, "units": [
            {"row": 0, "col": 0, "symbol": symbol, "eventId": eid}]})
        rest = symbol == "쉼"
        value["events"].append({"id": eid, "kind": "REST" if rest else "NOTE", "yul": None if rest else symbol,
                                "octave": None if rest else 0, "startTick": i, "durationTick": 1,
                                "cellId": cid, "breathAfter": False, "sigimsae": []})
    return value


if __name__ == "__main__":
    unittest.main()
