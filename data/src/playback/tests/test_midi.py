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
