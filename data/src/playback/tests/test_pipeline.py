"""Cross-module and __main__.run integration; synthetic audio, no CAT bank."""
from fractions import Fraction
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import numpy as np

from data.src.playback.__main__ import run, publish
from data.src.playback.audio import resolve_encoder
from data.src.playback.sampler import FS
from data.src.playback.score import PROFILES
from data.src.playback.tests._helpers import (
    EXAMPLE, HAVE_ENCODER, converted, document, inputs, midi, sampler,
    score, synthetic_sampler, wire,
)


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


class JgbRenderIntegrationTests(unittest.TestCase):
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
            plan, profile = root / "plan.json", root / "profile.json"
            plan.write_text(json.dumps({"schema": "danso-performance/v1",
                "input_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
                "tempos": [{"gak": 1, "cell": 2, "bpm": 120}]}), encoding="utf-8")
            profile.write_text(json.dumps({"schema": "danso-expression-profile/v1", "policies": {}}), encoding="utf-8")
            with patch("data.src.playback.__main__.CatSampler", return_value=sampler()):
                result = run(source, assets, root / "out", ["danso"], audio_format="wav",
                    performance_plan=plan, expression_profile=profile, max_duration_seconds=1200)
            self.assertEqual(result["resource_limits"]["max_duration_seconds"], 1200)
            for filename, original in (("performance-plan.json", plan), ("expression-profile.json", profile)):
                self.assertEqual((root / "out" / filename).read_bytes(), original.read_bytes())
                self.assertEqual(result["configuration_sha256"][filename], hashlib.sha256(original.read_bytes()).hexdigest())
            rendered = json.loads((root / "out/score.json").read_text(encoding="utf-8"))
            self.assertAlmostEqual(midi(rendered)["notes"][-1]["end"], 1.5, places=4)

    def test_failed_cli_preserves_previous_result(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); out=root/"out";out.mkdir()
            (out/"manifest.json").write_text("previous",encoding="utf-8")
            (root/"score.json").write_text(json.dumps(score()),encoding="utf-8")
            (root/"assets.json").write_text("{}",encoding="utf-8")
            with self.assertRaisesRegex(ValueError,"asset mapping"):
                run(root/"score.json",root/"assets.json",out,["danso"],audio_format="wav")
            self.assertEqual((out/"manifest.json").read_text(),"previous")
            self.assertEqual(len(list(out.iterdir())),1)

    def test_cli_pipeline_keeps_original_bytes_and_resolvable_source_map(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root/"input.jgb.json"
            payload = b"\xef\xbb\xbf" + json.dumps(document("청황 | -"), ensure_ascii=False).encode("utf-8")
            source.write_bytes(payload)
            assets = root/"assets.json"
            assets.write_text('{"danso":"unused.zip"}', encoding="utf-8")
            fake = synthetic_sampler()
            fake.manifest, fake.close = {"fixture": "synthetic"}, lambda: None
            with patch("data.src.playback.__main__.CatSampler", return_value=fake):
                result = run(source, assets, root/"out", ["danso"], audio_format="wav")
            self.assertEqual(source.read_bytes(), payload)
            self.assertEqual((root/"out/source.jgb.json").read_bytes(), payload)
            self.assertEqual(result["input_sha256"], hashlib.sha256(payload).hexdigest())
            mapping = json.loads((root/"out/source-map.json").read_text(encoding="utf-8"))
            report = json.loads((root/"out/danso.json").read_text(encoding="utf-8"))
            self.assertEqual(len(mapping["notes"][report["events"][0]["parent_ids"][0]]), 2)
            self.assertEqual(result["jgb"]["part_id"], "part-17")

    def test_bad_jgb_preserves_previous_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root/"bad.jgb.json"
            source.write_text(json.dumps(document("청황{알수없음} | 청태")), encoding="utf-8")
            out = root/"out"
            out.mkdir()
            (out/"danso.wav").write_bytes(b"previous")
            with self.assertRaisesRegex(ValueError, "미지원"):
                run(source, root/"missing-assets.json", out, ["danso"])
            self.assertEqual((out/"danso.wav").read_bytes(), b"previous")
            self.assertEqual(len(list(out.iterdir())), 1)

    def test_hash_describes_captured_input_even_if_original_changes_during_render(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source, assets = root/"input.jgb.json", root/"assets.json"
            payload = json.dumps(document()).encode("utf-8")
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
                result = run(source, assets, root/"out", ["danso"], audio_format="wav")
            self.assertEqual((root/"out/source.jgb.json").read_bytes(), payload)
            self.assertEqual(result["input_sha256"], hashlib.sha256(payload).hexdigest())
            self.assertEqual(result["jgb"]["input_sha256"], result["input_sha256"])

    def test_output_cannot_overwrite_original_input(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source, assets = root/"score.json", root/"assets.json"
            payload = json.dumps(document()).encode("utf-8")
            source.write_bytes(payload)
            assets.write_text('{"danso":"unused.zip"}', encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "겹칩니다"):
                run(source, assets, root, ["danso"])
            self.assertEqual(source.read_bytes(), payload)

    def test_ir_cannot_leave_an_obsolete_jgb_map_beside_new_audio(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source, assets, out = root/"score.json", root/"assets.json", root/"out"
            source.write_text(json.dumps(converted(document())[0]), encoding="utf-8")
            assets.write_text('{"danso":"unused.zip"}', encoding="utf-8")
            out.mkdir()
            (out/"source-map.json").write_bytes(b"previous map")
            with self.assertRaisesRegex(ValueError, "이전 JGB"):
                run(source, assets, out, ["danso"])
            self.assertEqual((out/"source-map.json").read_bytes(), b"previous map")

    def test_unconfirmed_tempo_fails_before_assets_or_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            source, out = Path(tmp) / "a.jgb.json", Path(tmp) / "out"
            doc = document()
            doc.pop("tempoBpmUserSet")
            payload = json.dumps(doc).encode()
            source.write_bytes(payload)
            with self.assertRaisesRegex(ValueError, "빠르기 선택"):
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
                    run(source, assets, out, ["danso"], audio_format=kind)
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
                    run(source, assets, out, ["danso"], encoder={"ffmpeg": "unused"})
            self.assertEqual({p.name: p.read_bytes() for p in out.iterdir()}, before)

    def test_missing_encoder_fails_before_writing_any_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source, assets = inputs(root)
            with self.assertRaisesRegex(ValueError, "ffmpeg"):
                run(source, assets, root / "out", ["danso"], ffmpeg=str(root / "missing-ffmpeg"))
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
                result = run(source, assets, root / "out", ["danso"], encoder=self.encoder)
            audio = result["results"][0]["audio"]
            self.assertEqual((audio["file"], audio["codec"], audio["profile"]), ("danso.m4a", "aac", "LC"))
            self.assertEqual(audio["sha256"], hashlib.sha256((root / "out/danso.m4a").read_bytes()).hexdigest())
            self.assertFalse(list((root / "out").rglob("*.wav")))
            self.assertFalse(list((root / "out").rglob("*.html")))
            self.assertIn("ffmpeg_version", result["audio_output"]["encoder"])
            self.assertIn("audio.py", result["source_sha256"])


if __name__ == "__main__":
    unittest.main()
