"""Small synthetic signals; no CAT download, network, app or expert claims."""
from copy import deepcopy
import json
from pathlib import Path
import tempfile
import unittest
import mido
import numpy as np
from playback.score import compile_score, PPQN, PROFILES
from playback.midi import write_midi, read_midi
from playback.sampler import CatSampler, FS
from playback.__main__ import run

EXAMPLE = Path(__file__).parents[2]/"playback/examples/seryeongsan-first-jangdan.json"


def score(notes=None):
    value = json.loads(EXAMPLE.read_text(encoding="utf-8"))
    value["quarter_bpm"] = 60
    value["notes"] = notes or [{"id":"a", "pitch":69, "duration":"1"}]
    return value


def wire(value, instrument="danso"):
    perf = compile_score(value, instrument)
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp)/"test.mid"
        write_midi(perf, path)
        return perf, read_midi(path)


def synthetic_sampler(length=5):
    sampler = CatSampler.__new__(CatSampler)
    sampler.instrument, sampler.attack, sampler.release, sampler.gain = "danso", 0, .05, 1
    sampler.zones = [{"low":66,"high":92,"root":69,"detune":0,"file":"sine.wav","volume":1}]
    data = .2*np.sin(2*np.pi*440*np.arange(round(length*FS))/FS)
    sampler.samples = lambda z: (data, FS, "synthetic")
    return sampler


class ScoreTests(unittest.TestCase):
    def test_fixture_expands_37_to_44_and_preserves_parent_ids(self):
        source = json.loads(EXAMPLE.read_text(encoding="utf-8"))
        for instrument in PROFILES:
            perf, midi = wire(source, instrument)
            self.assertEqual(len(midi["notes"]), 44)
            self.assertEqual({p for e in perf["events"] for p in e["parent_ids"]}, {str(i) for i in range(1,38)})
            self.assertEqual([e["pitch"] for e in perf["events"] if e["parent_ids"] == ["27"]], [70,73,70])
            self.assertEqual([e["pitch"] for e in perf["events"] if e["parent_ids"] == ["26"]], [77,75])

    def test_written_octave_applied_once(self):
        value = score([{"id":"a","pitch":60,"duration":1}])
        value["context"].update(pitch_mode="written", notation_octave_shift=1)
        self.assertEqual(compile_score(value,"danso")["events"][0]["pitch"],72)
        value["context"]["pitch_mode"] = "sounding"
        with self.assertRaisesRegex(ValueError,"again"):
            compile_score(value,"danso")

    def test_yul_requires_reference_and_respects_register(self):
        value = score([{"id":"a","pitch":{"yul":"黃","register":1},"duration":1}])
        value["context"].update(pitch_mode="yul", huangjong_midi=63)
        self.assertEqual(compile_score(value,"danso")["events"][0]["pitch"],75)
        del value["context"]["huangjong_midi"]
        with self.assertRaisesRegex(ValueError,"huangjong"):
            compile_score(value,"danso")

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

    def test_unresolved_or_oversized_ornament_rejected(self):
        for ornament in ({}, {"kind":"unknown"}, {"kind":"upper-return","pitch":72,"duration":"1"}):
            value = score()
            value["notes"][0]["ornament"] = ornament
            with self.assertRaises(ValueError):
                compile_score(value,"danso")

    def test_range_checked_after_ornament_and_no_automatic_fold(self):
        value = score()
        value["notes"][0]["ornament"]={"kind":"upper-return","pitch":100}
        with self.assertRaisesRegex(ValueError,"outside"):
            compile_score(value,"danso")

    def test_explicit_danso_yoseong_uses_amplitude_default(self):
        value = score()
        value["notes"][0]["yoseong"]={}
        self.assertEqual(compile_score(value,"danso")["events"][0]["yoseong"]["mode"],"amplitude")

    def test_bad_input_cannot_be_silently_ignored(self):
        for field, value in (("unknown",1),("duration",0),("velocity",0),("rest","yes"),("breath_seconds",2)):
            source=score();source["notes"][0][field]=value
            with self.assertRaises(ValueError):
                compile_score(source,"danso")
        source=score();source["notes"]*=2
        with self.assertRaisesRegex(ValueError,"Unique"):
            compile_score(source,"danso")

    def test_unclosed_tie_rejected(self):
        value=score();value["notes"][0]["tie"]="start"
        with self.assertRaisesRegex(ValueError,"Unclosed"):
            compile_score(value,"danso")

    def test_tie_cannot_silently_drop_new_slur_or_velocity(self):
        for extra in ({"slur":"new"},{"velocity":1}):
            value=score([{"id":"a","pitch":69,"duration":1,"tie":"start"},
                         {"id":"b","pitch":69,"duration":1,"tie":"stop",**extra}])
            with self.assertRaisesRegex(ValueError,"inside a tie"):
                compile_score(value,"danso")


class RenderTests(unittest.TestCase):
    def test_cc11_changes_actual_pcm(self):
        value=score();_, baseline=wire(value)
        value["notes"][0]["expression"]=[64,64,64]
        _, quiet=wire(value)
        loud,_=synthetic_sampler().render(baseline)
        soft,_=synthetic_sampler().render(quiet)
        a,b=round(.2*FS),round(.8*FS)
        self.assertAlmostEqual(float(np.linalg.norm(soft[a:b])/np.linalg.norm(loud[a:b])),64/127,places=10)

    def test_pitch_bend_changes_actual_frequency_and_resets(self):
        value=score();value["context"]["tuning_a_hz"]=452
        _, midi=wire(value)
        audio,_=synthetic_sampler().render(midi)
        signal=audio[round(.2*FS):round(.8*FS)]
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
        _, whole=wire(score([{"id":"a","pitch":69,"duration":2}]))
        _, split=wire(score([{"id":"a","pitch":69,"duration":1,"slur":"s"},
                            {"id":"b","pitch":69,"duration":1,"slur":"s"}]))
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

    def test_unsupported_midi_cc_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/"test.mid"
            write_midi(compile_score(score(),"danso"),path)
            wire_file=mido.MidiFile(path)
            wire_file.tracks[0].insert(0,mido.Message("control_change",control=1,value=20))
            wire_file.save(path)
            with self.assertRaisesRegex(ValueError,"Unsupported controller"):
                read_midi(path)

    def test_failed_cli_preserves_previous_result(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); out=root/"out";out.mkdir()
            (out/"comparison.html").write_text("previous",encoding="utf-8")
            (root/"score.json").write_text(json.dumps(score()),encoding="utf-8")
            (root/"assets.json").write_text("{}",encoding="utf-8")
            with self.assertRaisesRegex(ValueError,"asset mapping"):
                run(root/"score.json",root/"assets.json",out,["danso"])
            self.assertEqual((out/"comparison.html").read_text(),"previous")
            self.assertEqual(len(list(out.iterdir())),1)


if __name__ == "__main__":
    unittest.main()
