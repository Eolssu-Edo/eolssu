"""Cross-module and __main__.run integration; synthetic audio, no CAT bank."""
from fractions import Fraction
from contextlib import redirect_stderr, redirect_stdout
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import numpy as np

from data.src.playback.__main__ import main, run, publish, replace_directory
from data.src.playback.audio import resolve_encoder
from data.src.playback.sampler import FS
from data.src.playback.score import PROFILES
from data.src.playback.tests._helpers import (
    EXAMPLE, HAVE_ENCODER, converted, document, inputs, midi, sampler,
    score, synthetic_sampler, wire, common_notes,
)


class GenerationBoundaryTests(unittest.TestCase):
    def test_jgb_and_legacy_ir_require_common_import_before_assets_or_output(self):
        for value in (document(), score()):
            with self.subTest(value=value.get("schema", "JGB")), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                source = root / "input.json"
                source.write_text(json.dumps(value), encoding="utf-8")
                with patch("data.src.playback.__main__.CatSampler") as bank:
                    with self.assertRaisesRegex(ValueError, "data.src.scores.playback_import"):
                        run(source, root / "missing-assets.json", root / "out", ["danso"],
                            common_bpm=60, audio_format="wav")
                    bank.assert_not_called()
                self.assertFalse((root / "out").exists())

    def test_generation_help_exposes_only_common_score_options(self):
        output = io.StringIO()
        with patch("sys.argv", ["playback", "--help"]), redirect_stdout(output):
            with self.assertRaises(SystemExit) as stopped:
                main()
        self.assertEqual(stopped.exception.code, 0)
        for option in ("--part", "--bpm ", "--speed", "--hwang-midi", "--jo ",
                       "--performance-plan", "--expression-profile", "--assume-danso"):
            self.assertNotIn(option, output.getvalue())
        self.assertIn("--common-bpm", output.getvalue())
        self.assertIn("--common-expressions", output.getvalue())

    def test_generation_cli_requires_explicit_common_bpm(self):
        with patch("sys.argv", ["playback", "--score", "score.json", "--assets", "assets.json",
                                "--out", "out", "--instrument", "danso"]), redirect_stderr(io.StringIO()), \
             redirect_stdout(io.StringIO()), patch("data.src.playback.__main__.run", return_value={}) as generate:
            with self.assertRaises(SystemExit) as stopped:
                main()
        self.assertEqual(stopped.exception.code, 2)
        generate.assert_not_called()


class ScoreMidiIntegrationTests(unittest.TestCase):
    def test_fixture_expands_37_to_44_and_preserves_parent_ids(self):
        source = json.loads(EXAMPLE.read_text(encoding="utf-8"))
        for instrument in PROFILES:
            perf, midi = wire(source, instrument)
            self.assertEqual(len(midi["notes"]), 44)
            self.assertEqual({p for e in perf["events"] for p in e["parent_ids"]}, {str(i) for i in range(1,38)})
            self.assertEqual([e["pitch"] for e in perf["events"] if e["parent_ids"] == ["27"]], [70,73,70])
            self.assertEqual([e["pitch"] for e in perf["events"] if e["parent_ids"] == ["26"]], [77,75])

    def test_rest_tie_and_rearticulation_are_different(self):
        value = score([{"id":"a","pitch":69,"duration":1,"tie":"start"},
                       {"id":"b","pitch":69,"duration":1,"tie":"stop"},
                       {"id":"r","rest":True,"duration":"1/2"},
                       {"id":"c","pitch":69,"duration":"1/2"}])
        perf, midi = wire(value)
        self.assertEqual(len(midi["notes"]),2)
        self.assertEqual(perf["events"][0]["parent_ids"],["a","b"])
        self.assertAlmostEqual(midi["notes"][0]["end"],2)
        self.assertAlmostEqual(midi["notes"][1]["start"],2.5)
        self.assertFalse(midi["notes"][1]["legato"])


class RenderIntegrationTests(unittest.TestCase):
    def test_jeongak_single_input_preserves_original_and_has_only_pipeline_outputs(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source, assets = root / "input.common.json", root / "assets.json"
            source.write_text(json.dumps(common_notes((("황", 0), ("태", 0), ("중", 0)),
                                                      "jeongak-daegeum")), encoding="utf-8")
            assets.write_text('{"jeongak-daegeum":"unused.zip"}', encoding="utf-8")
            fake = sampler()
            fake.instrument = "jeongak-daegeum"
            fake.zones[0].update(low=58, high=89)
            with patch("data.src.playback.__main__.CatSampler", return_value=fake):
                manifest = run(source, assets, root / "out", [fake.instrument], common_bpm=60, audio_format="wav")
            self.assertEqual((root / "out/source.common.json").read_bytes(), source.read_bytes())
            self.assertEqual({p.name for p in (root / "out").iterdir()}, {
                "jeongak-daegeum.wav", "jeongak-daegeum.mid", "jeongak-daegeum.json",
                "manifest.json", "source-map.json", "source.common.json", "score.json"})
            self.assertIsNone(manifest["playback_vibrato"])
            report = json.loads((root / "out/jeongak-daegeum.json").read_text(encoding="utf-8"))
            self.assertEqual([n["pitch"] for n in report["events"]], [63, 65, 68])
            self.assertTrue(all(n["parent_ids"] for n in report["events"]))


    def test_sanjo_single_input_preserves_source_and_records_explicit_policies(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "input.common.json"
            value = common_notes((("황", 0), ("태", 0), ("중", 0), ("임", 0)), "sanjo-daegeum")
            for event, marks in zip(value["events"], (["농음표"], ["흘림표"], ["니레"], [])):
                event["sigimsae"] = marks
            value["events"][-1]["breathAfter"] = True
            source.write_text(json.dumps(value), encoding="utf-8")
            config = root / "expressions.json"
            config.write_text(json.dumps({"schema": "common-score-expressions/v1",
                "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
                "placements": [{"eventId": e["id"], "cellId": e["cellId"], "row": 0, "col": 0,
                                "symbols": e["sigimsae"]} for e in value["events"] if e["sigimsae"]],
                "relativeScale": {"pitch_classes": [0, 2, 5, 7, 9], "basis": "Authored test scale"},
                "expressionPolicy": {"name": "sanjo-authored-fixture-v1", "basis": "Authored test choices",
                    "status": "audition-policy-not-performance-standard",
                    "vibrato": {"mode": "pitch", "hz": 4, "cents": 25, "delay_seconds": .1},
                    "fall": {"end_cents": -50, "tail_fraction": .3, "maximum_seconds": .2}}}), encoding="utf-8")
            assets = root / "assets.json"
            assets.write_text('{"sanjo-daegeum":"unused.zip"}', encoding="utf-8")
            fake = sampler()
            fake.instrument = "sanjo-daegeum"
            fake.zones[0].update(low=60, high=91)
            with patch("data.src.playback.__main__.CatSampler", return_value=fake) as constructor:
                manifest = run(source, assets, root / "out", ["sanjo-daegeum"], common_bpm=60,
                               notation_policy="educational-preview/v1", common_expressions=config, audio_format="wav")
            constructor.assert_called_once_with(root / "unused.zip", "sanjo-daegeum")
            self.assertEqual((root / "out/source.common.json").read_bytes(), source.read_bytes())
            self.assertIsNone(manifest["playback_vibrato"])
            report = json.loads((root / "out/sanjo-daegeum.json").read_text(encoding="utf-8"))
            mapping = json.loads((root / "out/source-map.json").read_text(encoding="utf-8"))
            self.assertEqual([e["pitch"] for e in report["events"]], [63, 65, 70, 68, 70])
            self.assertEqual(mapping["expression_source_map"]["expression_policy"]["name"], "sanjo-authored-fixture-v1")
            self.assertEqual(mapping["expression_source_map"]["symbol_policy"]["name"], "sanjo-daegeum-general-figures-v2")
            self.assertEqual(len(list((root / "out").iterdir())), 9)
            self.assertTrue(any("WAV 67개" in message for message in report["warnings"]))

    def test_windows_directory_lock_is_retried_but_permanent_error_stops(self):
        locked = PermissionError("temporary lock")
        locked.winerror = 5
        with patch.object(Path, "replace", side_effect=[locked, None]) as replace, \
             patch("data.src.playback.__main__.time.sleep") as sleep:
            replace_directory(Path("stage"), Path("out"))
            self.assertEqual(replace.call_count, 2)
            sleep.assert_called_once_with(.1)
        with patch.object(Path, "replace", side_effect=locked) as replace, \
             patch("data.src.playback.__main__.time.sleep") as sleep:
            with self.assertRaises(PermissionError):
                replace_directory(Path("stage"), Path("out"))
            self.assertEqual(replace.call_count, 5)
            self.assertEqual(sleep.call_count, 4)
        with patch.object(Path, "replace", side_effect=OSError("not a lock")) as replace, \
             patch("data.src.playback.__main__.time.sleep") as sleep:
            with self.assertRaises(OSError):
                replace_directory(Path("stage"), Path("out"))
            replace.assert_called_once()
            sleep.assert_not_called()

    def test_sogeum_single_input_keeps_source_ids_and_instrument_manifest(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "input.common.json"
            value = common_notes((("황", 1), ("태", 1), ("중", 1)), "sogeum")
            value["score"]["sourceId"] = value["provenance"]["sourceId"] = "sogeum-example"
            source.write_text(json.dumps(value), encoding="utf-8")
            assets = root / "assets.json"
            assets.write_text('{"sogeum":"unused.zip"}', encoding="utf-8")
            fake = sampler()
            fake.instrument = "sogeum"
            with patch("data.src.playback.__main__.CatSampler", return_value=fake) as constructor:
                manifest = run(source, assets, root / "out", ["sogeum"], common_bpm=60, audio_format="wav")
            constructor.assert_called_once_with(root / "unused.zip", "sogeum")
            self.assertEqual((root / "out/source.common.json").read_bytes(), source.read_bytes())
            self.assertIsNone(manifest["playback_vibrato"])
            self.assertIsNone(manifest["playback_vibrato_by_instrument"]["sogeum"])
            report = json.loads((root / "out/sogeum.json").read_text(encoding="utf-8"))
            mapping = json.loads((root / "out/source-map.json").read_text(encoding="utf-8"))
            self.assertEqual(report["notes"], 3)
            self.assertEqual([n["pitch"] for n in report["events"]], [75, 77, 80])
            self.assertEqual(mapping["score"]["sourceId"], "sogeum-example")
            source_ids = {event["id"] for event in mapping["events"]}
            self.assertTrue(all(parent in source_ids for event in report["events"] for parent in event["parent_ids"]))
            self.assertEqual(len(list((root / "out").iterdir())), 7)

    def test_short_breath_preserves_half_note_next_onset_and_audible_gap(self):
        for bpm in (60, 198, 400):
            with self.subTest(bpm=bpm):
                score, mapping = converted(document('청임 청중< | 청태', staffUnit='eighth'), bpm=bpm)
                playback = midi(score)
                last, following = playback['notes'][1:]
                duration = 30 / bpm
                self.assertGreaterEqual(last['end']-last['start'], duration/2 - .0002)
                self.assertAlmostEqual(following['start'], 60/bpm, places=5)
                self.assertGreater(following['start']-last['end']-last['release_seconds'], 0)
                audio, _ = synthetic_sampler().render(playback)
                a=round((last['end']+last['release_seconds']+.001)*FS)
                b=round(following['start']*FS)
                self.assertGreater(b-a, 0)
                self.assertEqual(np.count_nonzero(audio[a:b]), 0)
                self.assertIn('breath_policy', mapping)

    def test_staccato_breath_share_release_budget_and_leave_actual_silence(self):
        for bpm in (60, 400):
            for mark in ("{끊는표}", "{끊는표}<"):
                with self.subTest(bpm=bpm, mark=mark):
                    score, _ = converted(document(f"청황{mark} | 청태", staffUnit="eighth"), bpm=bpm)
                    wire = midi(score)
                    n, following = wire["notes"]
                    self.assertAlmostEqual(n["end"] + n["release_seconds"], 30 / bpm, delta=.0001)
                    self.assertAlmostEqual(following["start"], 60 / bpm, places=5)
                    pcm, _ = synthetic_sampler().render(wire)
                    self.assertEqual(np.count_nonzero(pcm[round((30 / bpm + .001) * FS):round(60 / bpm * FS)]), 0)

    def test_fermata_records_written_and_performed_times_including_rest(self):
        score, mapping = converted(document("청황{늘임표} | 쉼{늘임표} | 청태", beats=3))
        self.assertEqual(mapping["written_total_quarters"], "9/2")
        self.assertEqual(mapping["total_quarters"], "15/2")
        self.assertEqual([Fraction(n["duration"]) for n in score["notes"]], [3, 3, Fraction(3, 2)])
        self.assertAlmostEqual(midi(score)["notes"][-1]["start"], 4, places=5)
        last = mapping["notes"][score["notes"][-1]["id"]][0]
        self.assertEqual((last["written_start_quarters"], last["start_quarters"]), ("3", "6"))
        with self.assertRaisesRegex(ValueError, "샘플|exhaust|sample",):
            synthetic_sampler(length=.2).render(midi(score))

    def test_short_staccato_silence_cannot_leak_previous_note_tails(self):
        for text in ("청임 청중{끊는표} | 청태", "청임 청중{끊는표}< | 청태",
                     "청황청태청임 청황청태청임 청황청태청중{끊는표} | 청태"):
            with self.subTest(text=text):
                score, _ = converted(document(text, staffUnit="eighth"), bpm=400)
                wire = midi(score)
                last, following = wire["notes"][-2:]
                pcm, trace = synthetic_sampler().render(wire)
                stop = last["end"] + last["release_seconds"]
                self.assertEqual(np.count_nonzero(pcm[round((stop + .001) * FS):round(following["start"] * FS)]), 0)
                self.assertTrue(all(n["end"] + n["rendered_release_seconds"] <= stop + 1 / FS for n in trace[:-1]))


class CliPipelineTests(unittest.TestCase):
    def test_publish_rename_failure_restores_complete_previous_bundle(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            out, stage = root / "out", root / "stage"
            out.mkdir(); stage.mkdir()
            names = ["manifest.json", "score.json", "danso.wav"]
            for name in names:
                (out / name).write_bytes(b"previous")
                (stage / name).write_bytes(b"new")
            original = Path.replace
            def fail_stage(path, target):
                if path == stage:
                    raise OSError("injected publish failure")
                return original(path, target)
            with patch.object(Path, "replace", fail_stage):
                with self.assertRaisesRegex(OSError, "publish failure"):
                    publish(stage, out, names)
            self.assertEqual({p.name: p.read_bytes() for p in out.iterdir()},
                             {name: b"previous" for name in names})
            self.assertFalse(list(root.glob(".*-previous-*")))

    def test_publish_refuses_unrelated_files_and_replaces_valid_bundle(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            out, stage = root / "out", root / "stage"
            out.mkdir(); stage.mkdir()
            (out / "personal.txt").write_bytes(b"keep")
            (stage / "manifest.json").write_bytes(b"new")
            with self.assertRaisesRegex(ValueError, "다른 파일"):
                publish(stage, out, ["manifest.json"])
            self.assertEqual((out / "personal.txt").read_bytes(), b"keep")
            (out / "personal.txt").rename(out / "manifest.json")
            publish(stage, out, ["manifest.json"])
            self.assertEqual((out / "manifest.json").read_bytes(), b"new")

    def test_cli_preserves_explicit_configs_and_resource_limit(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source, assets = inputs(root)
            config = root / "expressions.json"
            source_hash = hashlib.sha256(source.read_bytes()).hexdigest()
            config.write_text(json.dumps({"schema": "common-score-expressions/v1", "sourceSha256": source_hash,
                "placements": [], "performancePlan": {"schema": "danso-performance/v1",
                    "input_sha256": source_hash, "tempos": [{"gak": 1, "cell": 2, "bpm": 120}]},
                "expressionProfile": {"schema": "danso-expression-profile/v1", "policies": {}}}), encoding="utf-8")
            with patch("data.src.playback.__main__.CatSampler", return_value=sampler()):
                result = run(source, assets, root / "out", ["danso"], common_bpm=60, audio_format="wav",
                    notation_policy="educational-preview/v1", common_expressions=config, max_duration_seconds=1200)
            self.assertEqual(result["resource_limits"]["max_duration_seconds"], 1200)
            self.assertEqual((root / "out/expressions.common.json").read_bytes(), config.read_bytes())
            self.assertEqual(result["configuration_sha256"]["expressions.common.json"], hashlib.sha256(config.read_bytes()).hexdigest())
            rendered = json.loads((root / "out/score.json").read_text(encoding="utf-8"))
            last = midi(rendered)["notes"][-1]
            self.assertAlmostEqual(last["end"] + last["release_seconds"], 1.5, places=4)

    def test_failed_cli_preserves_previous_result(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); out=root/"out";out.mkdir()
            (out/"manifest.json").write_text("previous",encoding="utf-8")
            (root/"score.json").write_text(json.dumps(common_notes()),encoding="utf-8")
            (root/"assets.json").write_text("{}",encoding="utf-8")
            with self.assertRaisesRegex(ValueError,"asset mapping"):
                run(root/"score.json",root/"assets.json",out,["danso"],common_bpm=60,audio_format="wav")
            self.assertEqual((out/"manifest.json").read_text(),"previous")
            self.assertEqual(len(list(out.iterdir())),1)

    def test_cli_pipeline_keeps_original_bytes_and_resolvable_source_map(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root/"input.common.json"
            value = common_notes()
            value["events"] = [dict(value["events"][0], durationTick=2)]
            value["columns"][0]["cells"][1]["units"][0].update(symbol="-", eventId=value["events"][0]["id"])
            payload = b"\xef\xbb\xbf" + json.dumps(value, ensure_ascii=False).encode("utf-8")
            source.write_bytes(payload)
            assets = root/"assets.json"
            assets.write_text('{"danso":"unused.zip"}', encoding="utf-8")
            fake = synthetic_sampler()
            fake.manifest, fake.close = {"fixture": "synthetic"}, lambda: None
            with patch("data.src.playback.__main__.CatSampler", return_value=fake):
                result = run(source, assets, root/"out", ["danso"], common_bpm=60, audio_format="wav")
            self.assertEqual(source.read_bytes(), payload)
            self.assertEqual((root/"out/source.common.json").read_bytes(), payload)
            self.assertEqual(result["input_sha256"], hashlib.sha256(payload).hexdigest())
            mapping = json.loads((root/"out/source-map.json").read_text(encoding="utf-8"))
            report = json.loads((root/"out/danso.json").read_text(encoding="utf-8"))
            self.assertEqual(report["events"][0]["parent_ids"], [value["events"][0]["id"]])
            self.assertEqual(len(mapping["events"][0]["displayUnits"]), 2)
            self.assertEqual(result["common_score"]["score"]["sourceId"], value["score"]["sourceId"])

    def test_bad_common_score_preserves_previous_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root/"bad.common.json"
            value = common_notes()
            value["events"][0]["sigimsae"] = ["알수없음"]
            source.write_text(json.dumps(value), encoding="utf-8")
            out = root/"out"
            out.mkdir()
            (out/"danso.wav").write_bytes(b"previous")
            with self.assertRaisesRegex(ValueError, "sigimsae"):
                run(source, root/"missing-assets.json", out, ["danso"], common_bpm=60)
            self.assertEqual((out/"danso.wav").read_bytes(), b"previous")
            self.assertEqual(len(list(out.iterdir())), 1)

    def test_hash_describes_captured_input_even_if_original_changes_during_render(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source, assets = root/"input.common.json", root/"assets.json"
            payload = json.dumps(common_notes()).encode("utf-8")
            source.write_bytes(payload)
            assets.write_text('{"danso":"unused.zip"}', encoding="utf-8")
            fake = synthetic_sampler()
            render = fake.render
            def render_with_external_edit(playback):
                source.write_bytes(b"external edit")
                return render(playback)
            fake.render = render_with_external_edit
            fake.manifest, fake.close = {"fixture": "synthetic"}, lambda: None
            with patch("data.src.playback.__main__.CatSampler", return_value=fake):
                result = run(source, assets, root/"out", ["danso"], common_bpm=60, audio_format="wav")
            self.assertEqual((root/"out/source.common.json").read_bytes(), payload)
            self.assertEqual(result["input_sha256"], hashlib.sha256(payload).hexdigest())
            self.assertEqual(result["common_score"]["input_sha256"], result["input_sha256"])

    def test_output_cannot_overwrite_original_input(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source, assets = root/"score.json", root/"assets.json"
            payload = json.dumps(common_notes()).encode("utf-8")
            source.write_bytes(payload)
            assets.write_text('{"danso":"unused.zip"}', encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "겹칩니다"):
                run(source, assets, root, ["danso"], common_bpm=60)
            self.assertEqual(source.read_bytes(), payload)

    def test_common_generation_refuses_previous_jgb_bundle_without_deleting_it(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source, assets, out = root/"score.json", root/"assets.json", root/"out"
            source.write_text(json.dumps(common_notes()), encoding="utf-8")
            assets.write_text('{"danso":"unused.zip"}', encoding="utf-8")
            out.mkdir()
            (out/"source-map.json").write_bytes(b"previous map")
            (out/"source.jgb.json").write_bytes(b"previous JGB")
            with self.assertRaisesRegex(ValueError, "이전 JGB"):
                run(source, assets, out, ["danso"], common_bpm=60)
            self.assertEqual((out/"source-map.json").read_bytes(), b"previous map")
            self.assertEqual((out/"source.jgb.json").read_bytes(), b"previous JGB")

    def test_unconfirmed_tempo_fails_before_assets_or_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            source, out = Path(tmp) / "a.common.json", Path(tmp) / "out"
            payload = json.dumps(common_notes()).encode()
            source.write_bytes(payload)
            with self.assertRaisesRegex(ValueError, "common-bpm"):
                run(source, Path(tmp) / "missing-assets.json", out, ["danso"], audio_format="wav")
            self.assertEqual(source.read_bytes(), payload)
            self.assertFalse(out.exists())

    def test_switching_format_rejects_stale_audio_without_deleting_it(self):
        for kind, old in (("m4a", "wav"), ("wav", "m4a")):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                source, assets = inputs(root)
                out = root / "out"
                out.mkdir()
                previous = out / f"danso.{old}"
                previous.write_bytes(b"previous output")
                with self.assertRaisesRegex(ValueError, "이전 형식"):
                    run(source, assets, out, ["danso"], common_bpm=60, audio_format=kind)
                self.assertEqual(previous.read_bytes(), b"previous output")
                self.assertEqual(len(list(out.iterdir())), 1)

    def test_encoder_failure_preserves_all_previous_outputs(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source, assets = inputs(root)
            out = root / "out"
            out.mkdir()
            for name in ("danso.m4a", "danso.mid", "manifest.json", "source-map.json"):
                (out / name).write_bytes(b"previous output")
            before = {p.name: p.read_bytes() for p in out.iterdir()}
            with patch("data.src.playback.__main__.CatSampler", return_value=sampler()), \
                 patch("data.src.playback.audio.command", side_effect=ValueError("encoder failed")):
                with self.assertRaisesRegex(ValueError, "encoder failed"):
                    run(source, assets, out, ["danso"], common_bpm=60, encoder={"ffmpeg": "unused"})
            self.assertEqual({p.name: p.read_bytes() for p in out.iterdir()}, before)

    def test_missing_encoder_fails_before_writing_any_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source, assets = inputs(root)
            with self.assertRaisesRegex(ValueError, "ffmpeg"):
                run(source, assets, root / "out", ["danso"], common_bpm=60, ffmpeg=str(root / "missing-ffmpeg"))
            self.assertFalse((root / "out").exists())


@unittest.skipUnless(HAVE_ENCODER, "M4A integration requires ffmpeg and ffprobe on PATH or *_BINARY")
class M4aPipelineIntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.encoder = resolve_encoder()

    def test_default_pipeline_emits_m4a_without_intermediate_wav(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source, assets = inputs(root)
            with patch("data.src.playback.__main__.CatSampler", return_value=sampler()), \
                 patch("data.src.playback.audio.sf.write", side_effect=AssertionError("WAV must not be written")):
                result = run(source, assets, root / "out", ["danso"], common_bpm=60, encoder=self.encoder)
            audio = result["results"][0]["audio"]
            self.assertEqual((audio["file"], audio["codec"], audio["profile"]), ("danso.m4a", "aac", "LC"))
            self.assertEqual(audio["sha256"], hashlib.sha256((root / "out/danso.m4a").read_bytes()).hexdigest())
            self.assertFalse(list((root / "out").rglob("*.wav")))
            self.assertFalse(list((root / "out").rglob("*.html")))
            self.assertIn("ffmpeg_version", result["audio_output"]["encoder"])
            self.assertIn("audio.py", result["source_sha256"])


if __name__ == "__main__":
    unittest.main()
