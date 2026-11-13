"""Run from the repository root: python -m unittest frontend.tools.test_analyze_echo_pair -v"""

from hashlib import sha256
import json
import math
from pathlib import Path
import struct
import subprocess
import sys
from tempfile import TemporaryDirectory
import unittest
import wave

from frontend.tools.analyze_echo_pair import analyze_pair


class AnalyzeEchoPairTest(unittest.TestCase):
    def setUp(self):
        self.temporary = TemporaryDirectory(prefix="echo-pair-analysis-test-")
        self.directory = Path(self.temporary.name)

    def tearDown(self):
        self.temporary.cleanup()

    def make_pair(self, original, corrected, preparation=()):
        samples = {"preparation": list(preparation), "original": list(original), "corrected": list(corrected)}
        manifest = {
            "schemaVersion": 1, "sessionId": 7, "sampleRate": 44100, "channels": 1,
            "encoding": "PCM_S16LE", "complete": True, "cancelled": False, "failure": None,
            "preparationFrames": len(preparation), "originalFrames": len(original),
            "correctedFrames": len(corrected), "offeredPreparationFrames": len(preparation),
            "offeredPerformanceFrames": len(original),
            "preparationFirstPhysicalFrame": 10 if preparation else None,
            "preparationEndPhysicalFrame": 10 + len(preparation) if preparation else None,
            "performanceFirstPhysicalFrame": 10 + len(preparation),
            "performanceEndPhysicalFrame": 10 + len(preparation) + len(original),
            "summary": {"state": "Active"},
        }
        for name, values in samples.items():
            payload = self.write_wave(name, values)
            manifest[name + "PcmSha256"] = sha256(payload).hexdigest()
        self.write_manifest(manifest)
        return manifest

    def write_wave(self, name, samples, rate=44100):
        payload = struct.pack(f"<{len(samples)}h", *samples)
        with wave.open(str(self.directory / f"{name}.wav"), "wb") as output:
            output.setnchannels(1)
            output.setsampwidth(2)
            output.setframerate(rate)
            output.writeframes(payload)
        return payload

    def write_manifest(self, manifest):
        (self.directory / "metadata.json").write_text(json.dumps(manifest), encoding="utf-8")

    def test_unchanged_input_has_identical_wave_bytes_and_zero_changed_samples(self):
        samples = [0, 1000, -1000, 32767, -32768]
        self.make_pair(samples, samples, preparation=[20, -20])
        report = analyze_pair(self.directory)
        self.assertEqual(5, report["frames"]["original"])
        self.assertEqual(2, report["frames"]["preparation"])
        self.assertEqual(5 / 44100, report["duration_seconds"])
        self.assertEqual(report["files"]["original"]["sha256"], report["files"]["corrected"]["sha256"])
        self.assertEqual(0, report["metrics"]["changed_sample_count"])
        self.assertAlmostEqual(0, report["metrics"]["mixed_signal_rms_change_db"])
        self.assertIsNone(report["metrics"]["difference_signal_rms_dbfs"])
        self.assertNotIn("echo_reduction_db", report["metrics"])
        self.assertNotIn("quality_pass", report)

    def test_known_subtraction_reports_exact_energy_change_and_fixed_windows(self):
        samples = [1000, -1000] * 5000
        self.make_pair(samples, [value // 2 for value in samples])
        report = analyze_pair(self.directory)
        self.assertEqual(10000, report["metrics"]["changed_sample_count"])
        self.assertAlmostEqual(20 * math.log10(.5), report["metrics"]["mixed_signal_rms_change_db"])
        self.assertAlmostEqual(20 * math.log10(1000 / 32768), report["metrics"]["original_rms_dbfs"])
        self.assertAlmostEqual(20 * math.log10(500 / 32768), report["metrics"]["difference_signal_rms_dbfs"])
        self.assertEqual([(0, 4410), (4410, 8820), (8820, 10000)],
                         [(entry["start_frame"], entry["end_frame"]) for entry in report["windows"]])
        self.assertTrue(report["limitations"])

    def test_silent_pair_uses_json_null_for_undefined_decibels(self):
        self.make_pair([0] * 100, [0] * 100)
        report = analyze_pair(self.directory)
        self.assertIsNone(report["metrics"]["original_rms_dbfs"])
        self.assertIsNone(report["metrics"]["corrected_rms_dbfs"])
        self.assertIsNone(report["metrics"]["mixed_signal_rms_change_db"])
        json.dumps(report, allow_nan=False)

    def test_incomplete_capture_is_rejected(self):
        manifest = self.make_pair([100], [80])
        manifest.update(complete=False, failure="queue_overflow")
        self.write_manifest(manifest)
        with self.assertRaisesRegex(ValueError, "incomplete"):
            analyze_pair(self.directory)

    def test_wrong_actual_wave_length_is_rejected(self):
        self.make_pair([100, 200], [80, 160])
        self.write_wave("corrected", [80])
        with self.assertRaisesRegex(ValueError, "frame"):
            analyze_pair(self.directory)

    def test_same_length_pcm_replacement_is_rejected(self):
        self.make_pair([100, 200], [80, 160])
        self.write_wave("corrected", [80, 159])
        with self.assertRaisesRegex(ValueError, "PCM hash"):
            analyze_pair(self.directory)

    def test_missing_pcm_binding_is_rejected(self):
        manifest = self.make_pair([100], [80])
        del manifest["originalPcmSha256"]
        self.write_manifest(manifest)
        with self.assertRaisesRegex(ValueError, "originalPcmSha256"):
            analyze_pair(self.directory)

    def test_wrong_physical_frame_span_is_rejected(self):
        manifest = self.make_pair([100, 200], [80, 160], preparation=[1, 2])
        manifest["performanceEndPhysicalFrame"] += 1
        self.write_manifest(manifest)
        with self.assertRaisesRegex(ValueError, "physical"):
            analyze_pair(self.directory)

    def test_wrong_wave_rate_is_rejected(self):
        self.make_pair([100], [80])
        self.write_wave("original", [100], rate=22050)
        with self.assertRaisesRegex(ValueError, "44100"):
            analyze_pair(self.directory)

    def test_inconsistent_riff_length_is_rejected_even_when_pcm_hash_matches(self):
        self.make_pair([100, 200], [80, 160])
        path = self.directory / "original.wav"
        contents = bytearray(path.read_bytes())
        contents[4:8] = struct.pack("<I", len(contents) + 100)
        path.write_bytes(contents)
        with self.assertRaisesRegex(ValueError, "RIFF"):
            analyze_pair(self.directory)

    def test_cli_writes_report_and_preserves_all_sources(self):
        self.make_pair([100, -100], [80, -80])
        before = {path.name: path.read_bytes() for path in self.directory.iterdir()}
        output = self.directory / "analysis.json"
        command = [sys.executable, str(Path(__file__).with_name("analyze_echo_pair.py")),
                   "--pair-dir", str(self.directory), "--output", str(output)]
        result = subprocess.run(command, capture_output=True, text=True, timeout=10)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(2, json.loads(output.read_text())["metrics"]["changed_sample_count"])
        for name, contents in before.items():
            self.assertEqual(contents, (self.directory / name).read_bytes())

    def test_cli_cannot_overwrite_a_source_wave(self):
        self.make_pair([100, -100], [80, -80])
        original = self.directory / "original.wav"
        contents = original.read_bytes()
        result = subprocess.run([sys.executable, str(Path(__file__).with_name("analyze_echo_pair.py")),
                                 "--pair-dir", str(self.directory), "--output", str(original)],
                                capture_output=True, text=True, timeout=10)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("source", result.stderr)
        self.assertEqual(contents, original.read_bytes())


if __name__ == "__main__":
    unittest.main()
