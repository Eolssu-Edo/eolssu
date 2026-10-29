"""midi.py serialization, supported controls and automatic pitch vibrato."""
from copy import deepcopy
from pathlib import Path
import tempfile
import unittest

import mido

from data.src.playback.midi import read_midi, write_midi
from data.src.playback.score import compile_score
from data.src.playback.tests._helpers import score, wire


class MidiTests(unittest.TestCase):
    def test_jeongak_has_no_automatic_motion_and_authored_unwind_reaches_midi(self):
        value = score([{"id": "a", "pitch": 75, "duration": 2}])
        _, plain = wire(value, "jeongak-daegeum")
        self.assertTrue(all(cents == 0 for _, cents in plain["bends"]))
        value["notes"][0].update(
            yoseong={"mode": "pitch", "hz": 3, "cents": 80, "delay_seconds": .5},
            pitch_curve={"points": [[0, 0], [1, -200]], "delay_seconds": .5})
        _, marked = wire(value, "jeongak-daegeum")
        self.assertTrue(all(cents == 0 for t, cents in marked["bends"] if t < .5))
        self.assertLess(min(cents for _, cents in marked["bends"]), -200)
        self.assertEqual(marked["bends"][-1][1], 0)


    def test_sanjo_has_no_unmarked_bend_and_keeps_authored_onset_curve(self):
        value = score([{"id": "a", "pitch": 75, "duration": 2}])
        _, plain = wire(value, "sanjo-daegeum")
        self.assertTrue(all(cents == 0 for _, cents in plain["bends"]))
        value["notes"][0]["pitch_curve"] = {"points": [[0, 0], [.1, -100], [.2, 0], [1, 0]]}
        _, marked = wire(value, "sanjo-daegeum")
        low_time, low_cents = min(marked["bends"], key=lambda pair: pair[1])
        self.assertAlmostEqual(low_time, .2, delta=.02)
        self.assertAlmostEqual(low_cents, -100, delta=.1)
        self.assertEqual(marked["bends"][-1][1], 0)

    def test_sogeum_has_no_unnotated_motion_but_explicit_pitch_motion_roundtrips(self):
        value = score([{"id": "a", "pitch": 75, "duration": 2}])
        _, plain = wire(value, "sogeum")
        self.assertTrue(all(cents == 0 for _, cents in plain["bends"]))
        _, danso = wire(value, "danso")
        self.assertGreater(max(abs(v) for _, v in danso["bends"]), 20)
        value["notes"][0]["pitch_curve"] = {"points": [[0, 0], [1, -200]]}
        _, curve = wire(value, "sogeum")
        self.assertEqual(curve["pitch_bend_range_cents"], 200)
        self.assertAlmostEqual(min(v for _, v in curve["bends"]), -200, delta=.05)
        value["notes"][0]["yoseong"] = {"mode": "pitch", "hz": 3, "cents": 300, "delay_seconds": 0}
        _, marked = wire(value, "sogeum")
        self.assertEqual(marked["pitch_bend_range_cents"], 500)
        self.assertLess(min(v for _, v in marked["bends"]), -400)

    def test_wide_and_piecewise_curves_roundtrip_with_automatic_rpn_range(self):
        source = score([{"id": "a", "pitch": 75, "duration": 2,
                         "yoseong": {"mode": "pitch", "cents": 0},
                         "pitch_curve": {"points": [[0, 0], [.5, -300], [1, 0]]}}])
        _, result = wire(source)
        self.assertEqual(result["pitch_bend_range_cents"], 300)
        def at(second):
            time, cents = min(result["bends"], key=lambda pair: abs(pair[0]-second))
            self.assertAlmostEqual(time, second)
            return cents
        self.assertAlmostEqual(at(1), -300, delta=.05)
        self.assertAlmostEqual(at(.5), -150, delta=.05)
        self.assertAlmostEqual(at(1.5), -150, delta=.05)
        self.assertAlmostEqual(at(2), 0, delta=.05)
        source["notes"][0]["pitch_curve"] = {"points": [[0, 0], [1, 300]]}
        _, result = wire(source)
        self.assertAlmostEqual(max(v for _, v in result["bends"]), 300, delta=.05)

    def test_delayed_piecewise_curve_keeps_held_prefix_at_original_pitch(self):
        source = score([{"id": "a", "pitch": 75, "duration": 2,
                         "yoseong": {"mode": "pitch", "cents": 0},
                         "pitch_curve": {"points": [[0, 0], [.5, -300], [1, 0]], "delay_seconds": 1}}])
        _, result = wire(source)
        self.assertTrue(all(v == 0 for t, v in result["bends"] if t <= 1))
        time, cents = min(result["bends"], key=lambda pair: abs(pair[0]-1.5))
        self.assertAlmostEqual(time, 1.5)
        self.assertAlmostEqual(cents, -300, delta=.05)

    def test_explicit_deep_yoseong_and_combined_controls_do_not_clip(self):
        source = score([{"id": "a", "pitch": 75, "duration": 2,
                         "yoseong": {"mode": "pitch", "hz": 3, "cents": 300, "delay_seconds": 0}}])
        _, result = wire(source)
        self.assertEqual(result["pitch_bend_range_cents"], 300)
        values = [v for _, v in result["bends"]]
        self.assertAlmostEqual(max(values), 300, delta=.2)
        self.assertAlmostEqual(min(values), -300, delta=.2)
        source["context"]["tuning_a_hz"] = 450
        source["notes"][0]["pitch_curve"] = {"points": [[0, 0], [1, -300]]}
        _, result = wire(source)
        self.assertEqual(result["pitch_bend_range_cents"], 600)
        source["notes"][0]["yoseong"]["cents"] = 1200
        with self.assertRaisesRegex(ValueError, "Combined pitch controls"):
            wire(source)

    def test_rpn_fractional_cents_are_decoded_and_malformed_order_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp)/"test.mid"
            write_midi(compile_score(score(), "danso"), path)
            original = mido.MidiFile(path)
            ordered = [(m.control, m.value) for m in original.tracks[0]
                       if m.type == "control_change" and m.control in (101, 100, 6, 38)]
            self.assertEqual(ordered, [(101, 0), (100, 0), (6, 2), (38, 0), (101, 127), (100, 127)])
            baseline = read_midi(path)
            changed = deepcopy(original)
            for msg in changed.tracks[0]:
                if msg.type == "control_change" and msg.control == 6:
                    msg.value = 3
                if msg.type == "control_change" and msg.control == 38:
                    msg.value = 50
            changed.save(path)
            decoded = read_midi(path)
            self.assertEqual(decoded["pitch_bend_range_cents"], 350)
            for (t, cents), (original_t, original_cents) in zip(decoded["bends"], baseline["bends"]):
                self.assertEqual(t, original_t)
                self.assertAlmostEqual(cents, original_cents*350/200)
            for mode in ("order", "wide", "cents", "early-note"):
                changed = deepcopy(original)
                indices = [i for i, m in enumerate(changed.tracks[0])
                           if m.type == "control_change" and m.control in (101, 100, 6, 38)]
                if mode == "order":
                    a, b = indices[:2]
                    changed.tracks[0][a], changed.tracks[0][b] = changed.tracks[0][b], changed.tracks[0][a]
                elif mode == "wide":
                    changed.tracks[0][indices[2]].value = 13
                elif mode == "cents":
                    changed.tracks[0][indices[3]].value = 100
                else:
                    changed.tracks[0].insert(0, mido.Message("note_on", note=75, velocity=90))
                changed.save(path)
                with self.subTest(mode=mode), self.assertRaisesRegex(ValueError, "RPN"):
                    read_midi(path)

    def test_unsupported_midi_cc_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/"test.mid"
            write_midi(compile_score(score(),"danso"),path)
            wire_file=mido.MidiFile(path)
            wire_file.tracks[0].insert(0,mido.Message("control_change",control=1,value=20))
            wire_file.save(path)
            with self.assertRaisesRegex(ValueError,"Unsupported controller"):
                read_midi(path)

    def test_fixed_25_cent_vibrato_keeps_short_notes_and_timing(self):
        source=score([{"id":"short","pitch":69,"duration":"1/2"},
                      {"id":"long","pitch":69,"duration":2},
                      {"id":"last","pitch":69,"duration":"1/2"}])
        original=deepcopy(source)
        _, midi=wire(source)
        self.assertEqual(source,original)
        self.assertEqual(len(midi["notes"]),3)
        for note,(start,end) in zip(midi["notes"],[(0,.5),(.5,2.5),(2.5,3)]):
            self.assertAlmostEqual(note["start"],start)
            self.assertAlmostEqual(note["end"],end)
        self.assertTrue(all(c==0 for t,c in midi["bends"] if t<=.5))
        self.assertTrue(all(c==0 for t,c in midi["bends"] if t>=2.5))
        bends=[c for t,c in midi["bends"] if .5<t<2.5]
        self.assertAlmostEqual(max(bends),25,delta=.08)
        self.assertAlmostEqual(min(bends),-25,delta=.08)
        self.assertEqual(midi["bends"][-1][1],0)
        for duration,enabled in (("0.64",False),("0.65",True)):
            with self.subTest(duration=duration):
                _, boundary=wire(score([{"id":"a","pitch":69,"duration":duration}]))
                self.assertEqual(any(c!=0 for _,c in boundary["bends"]),enabled)

    def test_authored_pitch_and_mixed_yoseong_are_not_added_twice(self):
        for mode in ("pitch","mixed"):
            with self.subTest(mode=mode):
                _, midi=wire(score([{"id":"a","pitch":69,"duration":2,
                                     "yoseong":{"mode":mode,"cents":8,"hz":3}}]))
                bends=[c for _,c in midi["bends"]]
                self.assertAlmostEqual(max(bends),8,delta=.08)
                self.assertAlmostEqual(min(bends),-8,delta=.08)

    def test_amplitude_yoseong_remains_with_fixed_pitch_vibrato(self):
        source=score([{"id":"a","pitch":69,"duration":2}])
        _, baseline=wire(source)
        source["notes"][0]["yoseong"]={"mode":"amplitude","depth":.2}
        _, midi=wire(source)
        self.assertEqual(midi["bends"],baseline["bends"])
        self.assertTrue(all(c==1 for _,c in baseline["cc11"]))
        self.assertLess(min(c for _,c in midi["cc11"]),.82)
        self.assertGreater(max(c for _,c in midi["bends"]),24.9)


if __name__ == "__main__":
    unittest.main()
