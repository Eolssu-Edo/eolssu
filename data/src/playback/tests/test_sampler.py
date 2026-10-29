"""sampler.py rendering with synthetic samples, without a CAT bank."""
import unittest
from copy import deepcopy
import hashlib
import io
import zipfile
from unittest.mock import patch

import numpy as np
import soundfile as sf

from data.src.playback.sampler import CatSampler, FS, find_sustain_loop
from data.src.playback.score import PROFILES
from data.src.playback.tests._helpers import score, synthetic_sampler, wire


class SamplerTests(unittest.TestCase):
    def _sanjo_extension_fixture(self):
        fake = synthetic_sampler()
        fake.instrument = "sanjo-daegeum"
        fake.zones[0].update(low=60, high=91)
        profile = deepcopy(PROFILES[fake.instrument])
        fake.manifest = {"sha256": profile["cat_provenance"]["archive_sha256"],
                         "preset_xml_sha256": profile["cat_provenance"]["preset_xml_sha256"],
                         "zones": fake.zones}
        fake.zip, fake.cache = zipfile.ZipFile(io.BytesIO(), "w"), {}
        self.addCleanup(fake.close)
        fake.samples = CatSampler.samples.__get__(fake, CatSampler)
        for item in profile["sample_zone_extensions"]["zones"]:
            zone = item["zone"]
            frequency = 440 * 2 ** ((zone["root"] - 69) / 12)
            payload = io.BytesIO()
            sf.write(payload, .2 * np.sin(2 * np.pi * frequency * np.arange(FS) / FS),
                     FS, format="WAV", subtype="FLOAT")
            data = payload.getvalue()
            fake.zip.writestr("synthetic/" + zone["file"], data)
            zone.update(start=0, end=FS - 1)
            item.update(sample_sha256=hashlib.sha256(data).hexdigest(), sample_rate=FS, frames=FS)
        return fake, profile

    def test_sanjo_shared_samples_extend_only_missing_keys_and_record_provenance(self):
        fake, profile = self._sanjo_extension_fixture()
        original = deepcopy(fake.zones)
        with patch.dict(PROFILES, {fake.instrument: profile}):
            with self.assertRaisesRegex(ValueError, "zones"):
                fake.zone(57)
            for pitch in range(60, 92):
                self.assertIs(fake.zone(pitch), fake.zones[0])
            self.assertNotIn("sample_zone_extensions", fake.manifest)
            for pitch in (58, 59):
                _, playback = wire(score([{"id": "a", "pitch": pitch, "duration": .5}]), fake.instrument)
                audio, trace = fake.render(playback)
                self.assertEqual(trace[0]["pitch"], pitch)
                self.assertEqual(trace[0]["root"], pitch)
                self.assertEqual(trace[0]["sample_zone_origin"], "explicit-shared-bank-extension")
                self.assertEqual(trace[0]["detune_cents"], 20 if pitch == 59 else 0)
                signal = audio[round(.1 * FS):round(.4 * FS)]
                crossings = np.where((signal[:-1] <= 0) & (signal[1:] > 0))[0]
                times = crossings - signal[crossings] / (signal[crossings + 1] - signal[crossings])
                expected = 440 * 2 ** ((pitch - 69 + (0.2 if pitch == 59 else 0)) / 12)
                self.assertAlmostEqual(FS / np.mean(np.diff(times)), expected, delta=.02)
        self.assertEqual(fake.zones, original)
        record = fake.manifest["sample_zone_extensions"]
        self.assertEqual(record["original_key_range"], [60, 91])
        self.assertEqual(record["effective_key_range"], [58, 91])
        self.assertEqual(record["used_pitches"], [58, 59])
        self.assertEqual(record["source_preset"], profile["sample_zone_extensions"]["source_preset"])

    def test_sample_extension_rejects_mismatched_bank_source_hash_rate_trim_and_root(self):
        for mismatch in ("archive", "preset", "source", "sample", "rate", "trim", "root"):
            with self.subTest(mismatch=mismatch):
                fake, profile = self._sanjo_extension_fixture()
                extension = profile["sample_zone_extensions"]
                if mismatch == "archive": fake.manifest["sha256"] = "wrong"
                elif mismatch == "preset": fake.manifest["preset_xml_sha256"] = "wrong"
                elif mismatch == "source": extension["source_preset"]["xml_sha256"] = "wrong"
                elif mismatch == "sample": extension["zones"][0]["sample_sha256"] = "wrong"
                elif mismatch == "rate": extension["zones"][0]["sample_rate"] = FS // 2
                elif mismatch == "trim": extension["zones"][0]["zone"]["end"] -= 1
                elif mismatch == "root": extension["zones"][0]["zone"]["root"] += 12
                with patch.dict(PROFILES, {fake.instrument: profile}):
                    with self.assertRaisesRegex(ValueError, "extension"):
                        fake.zone(58)
                self.assertNotIn("sample_zone_extensions", fake.manifest)

    def test_early_stable_passage_extends_without_changing_short_notes_or_slur_phase(self):
        clock = np.arange(4 * FS) / FS
        samples = .2 * np.sin(2 * np.pi * 440 * clock)
        # The original late candidates land in noise; the early sustain is valid.
        samples[round(1.4 * FS):] = np.random.default_rng(17).normal(0, .1, len(samples) - round(1.4 * FS))
        loop = find_sustain_loop(samples, FS, 69)
        self.assertEqual(loop["search"], "fallback-grid")
        self.assertLess(loop["end_frame"], 1.4 * FS)
        self.assertGreaterEqual(loop["correlation"], .98)
        self.assertLessEqual(loop["sustain_rms_ratio"], 1.5)
        self.assertLessEqual(loop["pitch_spread_cents"], 35)
        for instrument in ("jeongak-daegeum", "sanjo-daegeum"):
            with self.subTest(instrument=instrument):
                fake = synthetic_sampler(4)
                fake.instrument, fake.manifest = instrument, {}
                fake.samples = lambda zone: (samples, FS, "early-stable-fixture")
                _, whole = wire(score([{"id": "a", "pitch": 69, "duration": 15}]), instrument)
                _, split = wire(score([{"id": "a", "pitch": 69, "duration": 7.5, "slur": "s"},
                                       {"id": "b", "pitch": 69, "duration": 7.5, "slur": "s"}]), instrument)
                audio, trace = fake.render(whole)
                connected, _ = fake.render(split)
                np.testing.assert_allclose(audio, connected, rtol=0, atol=1e-10)
                steady = audio[2 * FS:14 * FS]
                self.assertGreater(float(np.sqrt(np.mean(steady * steady))), .05)
                self.assertLess(float(np.max(np.abs(np.diff(steady)))), .02)
                self.assertLess(trace[0]["sustain_loop"]["pitch_spread_cents"], .1)
                self.assertEqual(fake.manifest["sustain_extension"]["loops"], [trace[0]["sustain_loop"]])
                _, short = wire(score([{"id": "a", "pitch": 69, "duration": .3}]), instrument)
                reference = synthetic_sampler(4)
                reference.instrument = instrument
                reference.samples = fake.samples
                expected, _ = reference.render(short)
                actual, short_trace = fake.render(short)
                np.testing.assert_array_equal(actual, expected)
                self.assertIsNone(short_trace[0]["sustain_loop"])

    def test_jeongak_sustain_keeps_same_pitch_phase_and_fails_without_stable_loop(self):
        fake = synthetic_sampler(1.5)
        fake.instrument = "jeongak-daegeum"
        _, whole = wire(score([{"id": "a", "pitch": 75, "duration": 4}]), fake.instrument)
        _, split = wire(score([{"id": "a", "pitch": 75, "duration": 2, "slur": "s"},
                               {"id": "b", "pitch": 75, "duration": 2, "slur": "s"}]), fake.instrument)
        a, trace = fake.render(whole)
        b, split_trace = fake.render(split)
        self.assertIsNotNone(trace[0]["sustain_loop"])
        self.assertTrue(split_trace[1]["same_pitch_continuation"])
        np.testing.assert_allclose(a, b, rtol=0, atol=1e-10)
        _, short = wire(score([{"id": "a", "pitch": 75, "duration": .4}]), fake.instrument)
        with patch("data.src.playback.sampler.find_sustain_loop", side_effect=AssertionError("not needed")):
            _, trace = fake.render(short)
        self.assertIsNone(trace[0]["sustain_loop"])
        failing = synthetic_sampler(1.5)
        failing.instrument = "jeongak-daegeum"
        with patch("data.src.playback.sampler.find_sustain_loop", return_value=None):
            with self.assertRaisesRegex(ValueError, "no validated stable sustain loop"):
                failing.render(whole)


    def test_sanjo_sustain_keeps_phase_breath_and_rejects_unstable_or_missing_samples(self):
        def fake():
            value = synthetic_sampler(1.5)
            value.instrument = "sanjo-daegeum"
            return value
        _, whole = wire(score([{"id": "a", "pitch": 75, "duration": 4}]), "sanjo-daegeum")
        _, split = wire(score([{"id": "a", "pitch": 75, "duration": 2, "slur": "s"},
                               {"id": "b", "pitch": 75, "duration": 2, "slur": "s"}]), "sanjo-daegeum")
        a, trace = fake().render(whole)
        b, split_trace = fake().render(split)
        self.assertIsNotNone(trace[0]["sustain_loop"])
        self.assertTrue(split_trace[1]["same_pitch_continuation"])
        np.testing.assert_allclose(a, b, rtol=0, atol=1e-10)
        _, breath = wire(score([{"id": "a", "pitch": 75, "duration": 4, "breath_seconds": .12},
                                {"id": "b", "pitch": 75, "duration": 1}]), "sanjo-daegeum")
        audio, _ = fake().render(breath)
        self.assertEqual(np.count_nonzero(audio[round(3.881 * FS):4 * FS]), 0)
        with patch("data.src.playback.sampler.find_sustain_loop", return_value=None):
            with self.assertRaisesRegex(ValueError, "no validated stable sustain loop"):
                fake().render(whole)
        _, missing = wire(score([{"id": "a", "pitch": 95, "duration": 1}]), "sanjo-daegeum")
        with self.assertRaisesRegex(ValueError, "zones"):
            fake().render(missing)

    def test_sogeum_loop_preserves_slur_phase_breath_and_short_note_path(self):
        def fake():
            value = synthetic_sampler(1.5)
            value.instrument = "sogeum"
            return value
        _, whole = wire(score([{"id": "a", "pitch": 75, "duration": 4}]), "sogeum")
        _, split = wire(score([{"id": "a", "pitch": 75, "duration": 2, "slur": "s"},
                               {"id": "b", "pitch": 75, "duration": 2, "slur": "s"}]), "sogeum")
        a, trace = fake().render(whole)
        b, split_trace = fake().render(split)
        self.assertIsNotNone(trace[0]["sustain_loop"])
        self.assertEqual(split_trace[0]["sustain_loop"], split_trace[1]["sustain_loop"])
        np.testing.assert_allclose(a, b, rtol=0, atol=1e-10)
        _, short = wire(score([{"id": "a", "pitch": 75, "duration": .4}]), "sogeum")
        with patch("data.src.playback.sampler.find_sustain_loop", side_effect=AssertionError("unneeded loop")):
            _, trace = fake().render(short)
        self.assertIsNone(trace[0]["sustain_loop"])
        _, breath = wire(score([{"id": "a", "pitch": 75, "duration": 4, "breath_seconds": .12},
                                {"id": "b", "pitch": 75, "duration": 1}]), "sogeum")
        audio, _ = fake().render(breath)
        self.assertEqual(np.count_nonzero(audio[round(3.881 * FS):4 * FS]), 0)
        with patch("data.src.playback.sampler.find_sustain_loop", return_value=None):
            with self.assertRaisesRegex(ValueError, "no validated stable sustain loop"):
                fake().render(whole)

    def test_cc11_changes_actual_pcm(self):
        value=score();_, baseline=wire(value)
        value["notes"][0]["expression"]=[64,64,64]
        _, quiet=wire(value)
        loud,_=synthetic_sampler().render(baseline)
        soft,_=synthetic_sampler().render(quiet)
        a,b=round(.2*FS),round(.8*FS)
        self.assertAlmostEqual(float(np.linalg.norm(soft[a:b])/np.linalg.norm(loud[a:b])),64/127,places=10)

    def test_pitch_bend_changes_actual_frequency_and_resets(self):
        value=score([{"id":"a","pitch":69,"duration":"1/2"}])
        value["context"]["tuning_a_hz"]=452
        _, midi=wire(value)
        audio,_=synthetic_sampler().render(midi)
        signal=audio[round(.1*FS):round(.4*FS)]
        crossings=np.where((signal[:-1] <= 0)&(signal[1:]>0))[0]
        crossing_times=crossings-signal[crossings]/(signal[crossings+1]-signal[crossings])
        frequency=FS/np.mean(np.diff(crossing_times))
        self.assertLess(abs(frequency-452),.02)
        self.assertEqual(midi["bends"][-1][1],0)

    def test_next_note_expression_does_not_leak_backwards(self):
        source=score([{"id":"a","pitch":69,"duration":"1/2","expression":[127,64,0]},
                      {"id":"b","pitch":69,"duration":"1/2","expression":[127,127,127]}])
        _, fading=wire(source)
        source["notes"][0]["expression"]=[127,127,127]
        _, baseline=wire(source)
        quiet,_=synthetic_sampler().render(fading)
        loud,_=synthetic_sampler().render(baseline)
        index=round(.499*FS)
        self.assertLess(abs(quiet[index]/loud[index]),.03)

    def test_same_pitch_slur_preserves_phase(self):
        _, whole=wire(score([{"id":"a","pitch":69,"duration":"1/2"}]))
        _, split=wire(score([{"id":"a","pitch":69,"duration":"1/4","slur":"s"},
                            {"id":"b","pitch":69,"duration":"1/4","slur":"s"}]))
        a,_=synthetic_sampler().render(whole)
        b,trace=synthetic_sampler().render(split)
        self.assertTrue(trace[1]["same_pitch_continuation"])
        self.assertLess(float(np.max(np.abs(a-b))),1e-10)

    def test_breath_has_real_silence_after_release(self):
        _, midi=wire(score([{"id":"a","pitch":69,"duration":1,"breath_seconds":.12},
                           {"id":"b","pitch":69,"duration":1}]))
        audio,_=synthetic_sampler().render(midi)
        self.assertEqual(np.count_nonzero(audio[round(.881*FS):round(1*FS)]),0)

    def test_outside_zone_and_exhaustion_fail(self):
        _, midi=wire(score())
        sampler=synthetic_sampler();sampler.zones=[]
        with self.assertRaisesRegex(ValueError,"zones"):
            sampler.render(midi)
        with self.assertRaisesRegex(ValueError,"exhausted"):
            synthetic_sampler(.5).render(midi)

    def test_rendered_pcm_has_25_cent_width_and_delayed_start(self):
        _, short=wire(score([{"id":"a","pitch":69,"duration":"1/2"}]))
        _, long=wire(score([{"id":"a","pitch":69,"duration":2}]))
        plain,_=synthetic_sampler().render(short)
        audio,_=synthetic_sampler().render(long)
        np.testing.assert_array_equal(audio[:round(.22*FS)],plain[:round(.22*FS)])
        signal=audio[round(.45*FS):round(1.7*FS)]
        crossings=np.where((signal[:-1]<=0)&(signal[1:]>0))[0]
        times=crossings-signal[crossings]/(signal[crossings+1]-signal[crossings])
        cents=1200*np.log2(FS/np.diff(times)/440)
        self.assertAlmostEqual(float(max(cents)),25,delta=.2)
        self.assertAlmostEqual(float(min(cents)),-25,delta=.2)

    def test_long_tone_uses_validated_loop_without_pitch_or_boundary_jumps(self):
        _, playback = wire(score([{"id": "a", "pitch": 69, "duration": 4}]))
        playback["bends"] = [(0, 0)]
        sampler = synthetic_sampler(1.5)
        sampler.manifest = {}
        audio, trace = sampler.render(playback)
        loop = trace[0]["sustain_loop"]
        self.assertGreaterEqual(loop["correlation"], .98)
        self.assertGreaterEqual(loop["period_frames"], .5 * FS)
        self.assertEqual(loop["sample_sha256"], "synthetic")
        self.assertEqual(loop["policy"]["status"], "synthetic-extension-not-performance-standard")
        self.assertEqual(sampler.manifest["sustain_extension"]["loops"], [loop])
        steady = audio[2 * FS:round(3.9 * FS)]
        self.assertLess(float(np.max(np.abs(np.diff(steady)))), .02)
        crossings = np.where((steady[:-1] <= 0) & (steady[1:] > 0))[0]
        times = crossings - steady[crossings] / (steady[crossings + 1] - steady[crossings])
        self.assertAlmostEqual(FS / np.mean(np.diff(times)), 440, delta=.02)

    def test_same_pitch_long_slur_uses_one_continuous_loop(self):
        _, whole = wire(score([{"id": "a", "pitch": 69, "duration": 4}]))
        _, split = wire(score([{"id": "a", "pitch": 69, "duration": 2, "slur": "s"},
                               {"id": "b", "pitch": 69, "duration": 2, "slur": "s"}]))
        whole["bends"] = split["bends"] = [(0, 0)]
        a, _ = synthetic_sampler(1.5).render(whole)
        b, trace = synthetic_sampler(1.5).render(split)
        self.assertEqual(trace[0]["sustain_loop"], trace[1]["sustain_loop"])
        self.assertTrue(trace[1]["same_pitch_continuation"])
        self.assertLess(float(np.max(np.abs(a - b))), 1e-10)

    def test_extended_tone_still_leaves_breath_silence(self):
        _, playback = wire(score([{"id": "a", "pitch": 69, "duration": 4, "breath_seconds": .12},
                                  {"id": "b", "pitch": 69, "duration": 1}]))
        audio, trace = synthetic_sampler(1.5).render(playback)
        self.assertIsNotNone(trace[0]["sustain_loop"])
        self.assertIsNone(trace[1]["sustain_loop"])
        self.assertEqual(np.count_nonzero(audio[round(3.881 * FS):4 * FS]), 0)

    def test_short_tone_does_not_search_or_apply_sustain_loop(self):
        _, playback = wire(score())
        with patch("data.src.playback.sampler.find_sustain_loop", side_effect=AssertionError("unneeded loop")):
            audio, trace = synthetic_sampler(1.5).render(playback)
        self.assertIsNone(trace[0]["sustain_loop"])
        self.assertGreater(np.max(np.abs(audio)), 0)

    def test_unstable_noise_glide_and_silence_have_no_sustain_loop(self):
        clock = np.arange(2 * FS) / FS
        fixtures = [np.random.default_rng(7).normal(0, .1, len(clock)),
                    .2 * np.sin(2 * np.pi * (300 * clock + 75 * clock ** 2)),
                    np.zeros(len(clock))]
        for index, samples in enumerate(fixtures):
            with self.subTest(index=index):
                self.assertIsNone(find_sustain_loop(samples, FS, 69))
                sampler = synthetic_sampler(2)
                sampler.samples = lambda zone: (samples, FS, f"fixture-{index}")
                _, playback = wire(score([{"id": "a", "pitch": 69, "duration": 4}]))
                with self.assertRaisesRegex(ValueError, "no validated stable sustain loop"):
                    sampler.render(playback)


if __name__ == "__main__":
    unittest.main()
