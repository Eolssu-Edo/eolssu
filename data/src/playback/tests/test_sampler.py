"""sampler.py rendering with synthetic samples, without a CAT bank."""
import unittest

import numpy as np

from data.src.playback.sampler import FS
from data.src.playback.tests._helpers import score, synthetic_sampler, wire


class SamplerTests(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
