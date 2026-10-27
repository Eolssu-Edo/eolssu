from fractions import Fraction
import unittest
import numpy as np
from data.src.playback.tests.test_jgb import document, converted, midi
from data.src.playback.tests.test_pipeline import synthetic_sampler
from data.src.playback.sampler import FS


class ExpressionPolicyTests(unittest.TestCase):
    def test_nested_triplets_preserve_exact_cell_and_midi_timing(self):
        score, _ = converted(document('청황청태임 청황청태임 청황청태임 | 청태', staffUnit='eighth'))
        self.assertEqual([Fraction(n['duration']) for n in score['notes'][:9]], [Fraction(1,18)]*9)
        played = midi(score)
        self.assertEqual(len(played['notes']), 10)
        self.assertAlmostEqual(played['notes'][-1]['start'], 1, places=6)

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

    def test_long_breath_keeps_existing_120ms_gap_and_50ms_release(self):
        score, _ = converted(document('청황< | 청태'))
        n=midi(score)['notes'][0]
        self.assertAlmostEqual(n['end'], .83, places=5)
        self.assertAlmostEqual(n['release_seconds'], .05)

    def test_blank_sustain_keeps_subdivision_and_source_location(self):
        score, mapping = converted(document('청황 {빈지속} | 청태'))
        self.assertEqual([n['pitch'] for n in score['notes']], [75,77])
        self.assertEqual([Fraction(n['duration']) for n in score['notes']], [Fraction(3,2)]*2)
        self.assertEqual(len(mapping['notes'][score['notes'][0]['id']]),2)
        score, _ = converted(document('{빈지속} | 청태'))
        self.assertTrue(score['notes'][0]['rest'])

    def test_vibrato_on_hold_starts_at_mark_without_rearticulating(self):
        score, mapping = converted(document('청임 -{농음표} 청임 | 청태'))
        self.assertEqual([n['pitch'] for n in score['notes']], [82,82,77])
        self.assertEqual(Fraction(score['notes'][0]['duration']), 1)
        self.assertAlmostEqual(score['notes'][0]['yoseong']['delay_seconds'], 1/3)
        played = midi(score)
        self.assertTrue(all(value==1 for t,value in played['cc11'] if t<1/3))
        self.assertTrue(any(value<1 for t,value in played['cc11'] if 1/3<t<2/3))
        self.assertIn('농음표',mapping['notes'][score['notes'][0]['id']][-1]['symbols'])
        with self.assertRaisesRegex(ValueError, '본음'):
            converted(document('-{농음표} | 청태'))

    def test_marked_vibrato_and_fall_change_controls_without_changing_pitches(self):
        plain, _ = converted(document('청황 | 청태'))
        decorated, mapping = converted(document('청황{농음표} | 청태{흘림표}'))
        a,b=midi(plain),midi(decorated)
        self.assertEqual([(n['pitch'],n['start'],n['end']) for n in a['notes']],
                         [(n['pitch'],n['start'],n['end']) for n in b['notes']])
        self.assertNotEqual(a['cc11'], b['cc11'])
        # The falling articulation is directional, not a lower replacement note.
        ending=[cents for t,cents in b['bends'] if 1.95<t<2]
        self.assertTrue(ending and min(ending)<-35)
        self.assertIn('expression_policy',mapping)

    def test_paired_length_marks_redistribute_one_cell_and_keep_annotation(self):
        decorated, mapping=converted(document('청황{덧길이표} 청태{반길이표} | 임 -{원문점}{노}'))
        self.assertEqual([Fraction(n['duration']) for n in decorated['notes'][:2]],
                         [Fraction(9,8),Fraction(3,8)])
        self.assertEqual(sum(Fraction(n['duration']) for n in decorated['notes']),3)
        locations=[p for locations in mapping['notes'].values() for p in locations]
        self.assertTrue(any('원문점' in p['symbols'] for p in locations))
        with self.assertRaisesRegex(ValueError,'길이'):
            converted(document('청황{덧길이표} 청태 | 임'))

    def test_surrounding_graces_and_independent_three_note_figure_keep_time(self):
        score, _=converted(document('청황{느니르} | {니로나}'))
        self.assertEqual([n['pitch'] for n in score['notes']], [73,75,73,77,75,73])
        self.assertEqual([Fraction(n['duration']) for n in score['notes'][:3]],
                         [Fraction(1,16),Fraction(11,8),Fraction(1,16)])
        self.assertEqual(sum(Fraction(n['duration']) for n in score['notes']),3)


if __name__=='__main__':
    unittest.main()
