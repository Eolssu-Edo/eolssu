"""Small synthetic checks; these do not validate real instrument labels."""

import importlib.util
import struct
import tempfile
import unittest
from pathlib import Path

import numpy as np

SPEC = importlib.util.spec_from_file_location(
    "tones", Path(__file__).resolve().parents[1] / "src/analyze_basic_tones.py"
)
tones = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(tones)


class BasicToneTests(unittest.TestCase):
    def test_known_fundamentals_with_harmonics(self):
        t = np.arange(4096) / 48000
        for hz in (220, 440, 880, 1295, 1760):
            with self.subTest(hz=hz):
                signal = np.sin(2 * np.pi * hz * t) + 0.6 * np.sin(4 * np.pi * hz * t)
                estimated, periodicity = tones.yin(signal, 48000)
                self.assertLess(abs(1200 * np.log2(estimated / hz)), 3)
                self.assertGreater(periodicity, 0.95)

    def test_stronger_second_harmonic_preserves_fundamental(self):
        t = np.arange(4096) / 48000
        signal = 0.3 * np.sin(2 * np.pi * 440 * t) + np.sin(4 * np.pi * 440 * t)
        estimated, _ = tones.yin(signal, 48000)
        self.assertLess(abs(1200 * np.log2(estimated / 440)), 3)

    def test_silence_has_no_pitch_or_regions(self):
        self.assertIsNone(tones.yin(np.zeros(4096), 48000)[0])
        frames, regions, _ = tones.analyze(np.zeros((48000, 2)), 48000)
        self.assertEqual(regions, [])
        self.assertTrue(all(f["f0_hz"] is None for f in frames))
        with self.assertRaisesRegex(ValueError, "shorter"):
            tones.analyze(np.zeros((100, 2)), 48000)

    def test_regions_bridge_short_gap_and_reject_short_noise(self):
        active = np.zeros(150, dtype=bool)
        active[5:25] = True
        active[29:50] = True
        active[70:90] = True
        active[110:112] = True
        self.assertEqual(tones.sounding_regions(active, .02), [(5, 50), (70, 90)])

    def test_float_wave_preserves_above_one_and_odd_chunk(self):
        fmt = struct.pack("<HHIIHH", 3, 2, 48000, 384000, 8, 32)
        samples = np.array([[1.4, -1.2], [.25, .5]], dtype="<f4")
        body = (b"WAVEJUNK" + struct.pack("<I", 1) + b"x\0" + b"fmt "
                + struct.pack("<I", len(fmt)) + fmt + b"data"
                + struct.pack("<I", samples.nbytes) + samples.tobytes())
        raw = b"RIFF" + struct.pack("<I", len(body)) + body
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "input.wav"
            path.write_bytes(raw)
            decoded, rate, _ = tones.read_wave(path)
            self.assertEqual(rate, 48000)
            np.testing.assert_allclose(decoded, samples)
            path.write_bytes(raw[:-1])
            with self.assertRaises(ValueError):
                tones.read_wave(path)


if __name__ == "__main__":
    unittest.main()
