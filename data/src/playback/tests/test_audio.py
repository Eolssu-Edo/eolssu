"""Small codec integration checks; CAT samples and network are not required."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import numpy as np
import soundfile as sf

from data.src.playback.__main__ import run
from data.src.playback.audio import command, resolve_encoder, write_audio
from data.src.playback.sampler import FS
from data.src.playback.tests.test_jgb import document
from data.src.playback.tests.test_pipeline import synthetic_sampler


def inputs(root):
    source, assets = root / "input.jgb.json", root / "assets.json"
    source.write_text(json.dumps(document("청황 | 청태")), encoding="utf-8")
    assets.write_text('{"danso":"unused.zip"}', encoding="utf-8")
    return source, assets


def sampler():
    fake = synthetic_sampler()
    fake.manifest, fake.close = {"fixture": "synthetic"}, lambda: None
    return fake


class AudioOutputTests(unittest.TestCase):
    def test_wav_option_needs_no_encoder_and_keeps_exact_length(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "tone.wav"
            audio = .2 * np.sin(2 * np.pi * 440 * np.arange(12345) / FS)
            report = write_audio(audio, path)
            decoded, rate = sf.read(path)
            self.assertEqual((rate, len(decoded)), (FS, len(audio)))
            self.assertEqual(report["codec"], "pcm_s16le")
            self.assertEqual(report["sha256"], hashlib.sha256(path.read_bytes()).hexdigest())

    def test_invalid_pcm_creates_no_audio_file(self):
        for audio in (np.array([]), np.array([np.nan]), np.zeros((5, 2)), np.ones(5)):
            with self.subTest(shape=audio.shape), tempfile.TemporaryDirectory() as tmp:
                path = Path(tmp) / "invalid.wav"
                with self.assertRaises(ValueError):
                    write_audio(audio, path)
                self.assertFalse(path.exists())

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

    def test_process_failure_and_timeout_become_value_errors(self):
        failures = [subprocess.CompletedProcess(["ffmpeg"], 1, b"", b"bad codec"),
                    subprocess.TimeoutExpired(["ffmpeg"], 1)]
        for failure in failures:
            with self.subTest(failure=type(failure).__name__):
                kwargs = {"side_effect": failure} if isinstance(failure, Exception) else {"return_value": failure}
                with patch("data.src.playback.audio.subprocess.run", **kwargs):
                    with self.assertRaises(ValueError):
                        command(["ffmpeg"])

    def test_missing_encoder_fails_before_writing_any_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source, assets = inputs(root)
            with self.assertRaisesRegex(ValueError, "ffmpeg"):
                run(source, assets, root / "out", ["danso"], ffmpeg=str(root / "missing-ffmpeg"))
            self.assertFalse((root / "out").exists())


HAVE_ENCODER = all(shutil.which(os.environ.get(f"{name.upper()}_BINARY", name))
                   for name in ("ffmpeg", "ffprobe"))


@unittest.skipUnless(HAVE_ENCODER, "M4A integration requires ffmpeg and ffprobe on PATH or *_BINARY")
class M4aIntegrationTests(unittest.TestCase):
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

    def test_aac_priming_is_removed_and_only_tail_padding_remains(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "timing.m4a"
            frames = FS + 123
            clock = np.arange(frames) / FS
            audio = .3 * np.sin(2 * np.pi * (300 * clock + 500 * clock ** 2))
            audio[:4000] = 0
            audio[-4000:] = 0
            report = write_audio(audio, path, self.encoder)
            decoded = np.frombuffer(command([
                self.encoder["ffmpeg"], "-v", "error", "-i", path, "-f", "f32le", "pipe:1",
            ]), dtype="<f4")[:frames]
            scores = {lag: float(np.dot(audio[5000:30000], decoded[5000 + lag:30000 + lag]))
                      for lag in range(-8, 9)}
            self.assertLessEqual(abs(max(scores, key=scores.get)), 1)
            self.assertEqual(report["frames"], frames)
            self.assertLess(report["end_padding_frames"], 1024)
            self.assertAlmostEqual(report["container_seconds"], frames / FS, delta=2 / FS)
            self.assertLess(np.max(np.abs(decoded[:2000])), .001)

if __name__ == "__main__":
    unittest.main()
