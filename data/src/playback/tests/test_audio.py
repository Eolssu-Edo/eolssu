"""audio.py PCM output, process errors and real AAC codec integration."""
import hashlib
from pathlib import Path
import subprocess
import tempfile
import tracemalloc
import unittest
from unittest.mock import patch

import numpy as np
import soundfile as sf

from data.src.playback.audio import command, resolve_encoder, write_audio
from data.src.playback.sampler import FS
from data.src.playback.tests._helpers import HAVE_ENCODER


class AudioOutputTests(unittest.TestCase):
    def test_wav_validation_needs_one_decoded_copy_and_keeps_exact_rms(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "long.wav"
            audio = .2 * np.sin(2 * np.pi * 440 * np.arange(2_000_000) / FS)
            tracemalloc.start()
            try:
                report = write_audio(audio, path)
                peak = tracemalloc.get_traced_memory()[1]
            finally:
                tracemalloc.stop()
            self.assertLess(peak, 1.5 * audio.nbytes)
            decoded, _ = sf.read(path, dtype="float64")
            self.assertEqual(report["rms"], float(np.sqrt(np.mean(decoded ** 2))))
            self.assertEqual(report["peak"], float(np.max(np.abs(decoded))))

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

    def test_process_failure_and_timeout_become_value_errors(self):
        failures = [subprocess.CompletedProcess(["ffmpeg"], 1, b"", b"bad codec"),
                    subprocess.TimeoutExpired(["ffmpeg"], 1)]
        for failure in failures:
            with self.subTest(failure=type(failure).__name__):
                kwargs = {"side_effect": failure} if isinstance(failure, Exception) else {"return_value": failure}
                with patch("data.src.playback.audio.subprocess.run", **kwargs):
                    with self.assertRaises(ValueError):
                        command(["ffmpeg"])


@unittest.skipUnless(HAVE_ENCODER, "M4A integration requires ffmpeg and ffprobe on PATH or *_BINARY")
class AacCodecIntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.encoder = resolve_encoder()

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
