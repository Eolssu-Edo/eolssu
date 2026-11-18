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
from data.src.playback.tests._helpers import sampler, common_small_score as small_score, COMMON_SCORE_FIXTURE as FIXTURE


class CommonScorePlaybackTests(unittest.TestCase):
    def test_sustained_note_crosses_columns_as_one_midi_note(self):
        for symbol in ("", "-"):
            with self.subTest(symbol=symbol):
                source = cross_column_score(symbol)
                normalized, mapping = convert_common_score(source, bpm=30, instrument="danso", input_sha256="a"*64)
                perf = compile_score(normalized, "danso")
                with tempfile.TemporaryDirectory() as tmp:
                    path = Path(tmp) / "out.mid"
                    write_midi(perf, path)
                    midi = read_midi(path)
                self.assertEqual(len(midi["notes"]), 1)
                self.assertEqual(perf["events"][0]["parent_ids"], ["c0-j0-r1-u0"])
                self.assertAlmostEqual(midi["notes"][0]["start"], .5)
                self.assertAlmostEqual(midi["notes"][0]["end"] + midi["notes"][0]["release_seconds"], 6, delta=.0001)
                self.assertEqual(mapping["total_ticks"], 14)
                self.assertEqual(mapping["score_seconds"], 7)
                sustained = mapping["events"][1]
                self.assertEqual(sustained["duration_seconds"], 5.5)
                self.assertEqual([u["cellId"] for u in sustained["displayUnits"]],
                                 ["c0-j0", "c0-j1", "c0-j2", "c1-j0", "c1-j1", "c1-j2"])

    def test_cross_column_continuations_reject_invalid_references_pitch_and_time(self):
        changes = [
            (lambda s: s["columns"][1]["cells"][0]["units"][0].update(eventId="missing"), "eventId"),
            (lambda s: s["columns"][1]["cells"][0]["units"][0].update(eventId=s["events"][-1]["id"]), "시간"),
            (lambda s: s["columns"][1]["cells"][0]["units"][0].update(symbol="황"), "율명"),
            (lambda s: s["columns"][1]["cells"][0]["units"][0].update(symbol="남"), "율명"),
            (lambda s: (s["events"][1].update(durationTick=5), s["events"][2].update(startTick=6, durationTick=8)), "시간"),
            (lambda s: s["events"][1].update(cellId="c1-j0"), "시작 정간"),
            (lambda s: s["columns"][0]["cells"][0]["units"][1].update(symbol=""), "시작 음"),
        ]
        for change, message in changes:
            with self.subTest(message=message):
                source = cross_column_score()
                change(source)
                with self.assertRaisesRegex(ValueError, message):
                    convert_common_score(source, bpm=30, instrument="danso", input_sha256="a"*64)

    def test_invalid_integer_and_subdivision_identify_input_location(self):
        for value, location in ((1.0, "music.ticksPerBeat"), (7, "c0-j0-r0-u0")):
            with self.subTest(value=value):
                source = small_score()
                source["music"]["ticksPerBeat"] = value
                with self.assertRaisesRegex(ValueError, location):
                    convert_common_score(source, bpm=60, instrument="danso", input_sha256="a"*64)

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


def cross_column_score(symbol=""):
    """Authored two-column fixture: rest, 5.5-second sustain, rest at BPM 30."""
    source = small_score()
    source["music"].update(ticksPerBeat=4, ticksPerJeonggan=2)
    rest, note, end = "c0-j0-r0-u0", "c0-j0-r1-u0", "c1-j3-r0-u0"
    source["events"] = [
        {**source["events"][0], "durationTick": 1},
        {**source["events"][1], "id": note, "cellId": "c0-j0", "yul": "남", "startTick": 1, "durationTick": 11},
        {**source["events"][3], "id": end, "cellId": "c1-j3", "startTick": 12, "durationTick": 2},
    ]
    source["columns"] = [{"index": c, "cells": [
        {"id": f"c{c}-j{j}", "index": j, "units": [{"row": 0, "col": 0, "symbol": symbol, "eventId": note}]}
        for j in range(count)]} for c, count in enumerate((3, 4))]
    source["columns"][0]["cells"][0]["units"] = [
        {"row": 0, "col": 0, "symbol": "쉼", "eventId": rest},
        {"row": 1, "col": 0, "symbol": "남", "eventId": note},
    ]
    source["columns"][1]["cells"][3]["units"][0].update(symbol="쉼", eventId=end)
    return source


if __name__ == "__main__":
    unittest.main()
