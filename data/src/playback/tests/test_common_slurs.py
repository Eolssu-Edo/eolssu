"""Ornament links survive the common adapter without leaking across rests."""
from copy import deepcopy
import unittest

from data.src.playback.common_score import convert_common_score
from data.src.playback.score import compile_score
from data.src.playback.tests._helpers import document, wire
from data.src.scores.score_data import convert


def source(melody, instrument='danso'):
    return convert(document(melody, beats=4), beats_per_jeonggan=1,
                   title='연결 발음 검사', instrument=instrument.upper().replace('-', '_'),
                   source_id='authored-slur-check', jangdan='', bpm_unit='QUARTER',
                   review='inferred', jgb_sha256='b'*64)


def performed(value, config=None):
    instrument = value['score']['instrument'].lower().replace('_', '-')
    return convert_common_score(value, bpm=60, instrument=instrument, input_sha256='a'*64,
                                notation_policy='educational-preview/v1', expressions=config)


def companion(ids):
    return {'schema': 'common-score-expressions/v1', 'sourceSha256': 'a'*64, 'placements': [],
            'slurGroups': [{'eventIds': ids, 'basis': '원문 독립 음열의 연결'}]}


class CommonSlurTests(unittest.TestCase):
    def test_figures_keep_internal_links_and_close_before_rest(self):
        for instrument in ('danso', 'sogeum', 'jeongak-daegeum', 'sanjo-daegeum'):
            with self.subTest(instrument=instrument):
                value = source('청중{나니나}|청중{니레}|청중{느니르}|쉼', instrument)
                before = deepcopy(value)
                score, _ = performed(value)
                compiled, midi = wire(score, instrument)
                events = compiled['events']
                self.assertEqual(len(events), 8)
                self.assertEqual([e['legato'] for e in events],
                                 [False, True, True, False, True, False, True, True])
                for index in (1, 2, 4, 6, 7):
                    self.assertEqual(events[index-1]['end_tick'], events[index]['start_tick'])
                    self.assertAlmostEqual(midi['notes'][index-1]['end'], midi['notes'][index]['start'])
                # Both MIDI ticks and CC72 release are quantized.
                self.assertAlmostEqual(midi['notes'][-1]['end'] + midi['notes'][-1]['release_seconds'],
                                       3, delta=1/compiled['ppqn'])
                self.assertEqual(value, before)

    def test_explicit_plain_note_group_preserves_source_identity_and_final_breath(self):
        value = source('청중 청임<|쉼')
        before = deepcopy(value)
        ids = [e['id'] for e in value['events'][:2]]
        score, mapping = performed(value, companion(ids))
        events = compile_score(score, 'danso')['events']
        self.assertEqual([e['legato'] for e in events], [False, True])
        self.assertEqual(events[0]['end_tick'], events[1]['start_tick'])
        self.assertLess(events[1]['end_tick'], 14400)
        self.assertEqual(value, before)
        self.assertEqual([e['id'] for e in mapping['events']], [e['id'] for e in value['events']])
        self.assertEqual(mapping['slur_groups'], companion(ids)['slurGroups'])

    def test_invalid_or_overlapping_groups_are_rejected(self):
        value = source('청중 청임 청황|쉼')
        ids = [e['id'] for e in value['events'][:3]]
        valid = {'eventIds': ids[:2], 'basis': '원문 연결'}
        for groups in (None, {}, [None], [dict(valid, basis=' ')], [dict(valid, unknown=1)],
                       [dict(valid, eventIds=ids[:1])], [dict(valid, eventIds=[ids[0], ids[0]])],
                       [dict(valid, eventIds=[ids[0], ids[2]])], [dict(valid, eventIds=['missing', ids[0]])],
                       [valid, {'eventIds': ids[1:], 'basis': '겹친 그룹'}]):
            config = companion(ids[:2])
            config['slurGroups'] = groups
            with self.subTest(groups=groups), self.assertRaises(ValueError):
                performed(value, config)
        for melody in ('청중|청임', '청중 쉼', '청중{니레} 청임'):
            other = source(melody)
            config = companion([e['id'] for e in other['events'][:2]])
            with self.subTest(melody=melody), self.assertRaises(ValueError):
                performed(other, config)
        value['events'][0]['breathAfter'] = True
        with self.assertRaisesRegex(ValueError, '중간 숨표'):
            performed(value, companion(ids[:2]))

    def test_final_group_note_keeps_delayed_sustain_effect(self):
        value = source('청중 청임|-{깊은요성}|쉼')
        ids = [e['id'] for e in value['events'][:2]]
        config = companion(ids)
        config['placements'] = [{'eventId': ids[1], 'cellId': 'c0-j1', 'row': 0, 'col': 0,
                                 'symbols': ['깊은요성']}]
        score, _ = performed(value, config)
        events = compile_score(score, 'danso')['events']
        self.assertEqual([e['legato'] for e in events], [False, True])
        self.assertEqual(events[1]['yoseong']['cents'], 100)
        self.assertAlmostEqual(events[1]['yoseong']['delay_seconds'], .5)

    def test_repeat_does_not_join_groups_across_passes(self):
        value = source('청중 청임|쉼')
        config = companion([e['id'] for e in value['events'][:2]])
        config['performancePlan'] = {'schema': 'danso-performance/v1', 'input_sha256': 'a'*64,
                                     'repeats': [{'start_gak': 1, 'end_gak': 1, 'times': 2}]}
        score, _ = performed(value, config)
        events = compile_score(score, 'danso')['events']
        self.assertEqual([e['legato'] for e in events], [False, True, False, True])
        self.assertNotEqual(events[0]['slur'], events[2]['slur'])

    def test_staccato_remains_short_and_adjacent_unmarked_notes_reattack(self):
        score, _ = performed(source('청중{끊는표}|청중|청중|쉼'))
        compiled = compile_score(score, 'danso')
        self.assertEqual([e['legato'] for e in compiled['events']], [False]*3)
        self.assertLess(compiled['events'][0]['end_tick'], compiled['ppqn']/2)

    def test_continued_rest_cannot_be_replaced_by_an_unanchored_figure(self):
        value = source('청중|-\n-|청임')
        value['events'][1]['sigimsae'] = ['니나']
        config = {'schema': 'common-score-expressions/v1', 'sourceSha256': 'a'*64,
                  'gakContinuations': [{'gak': 2, 'basis': '앞 음 지속'}],
                  'placements': [{'eventId': value['events'][1]['id'], 'cellId': 'c1-j0',
                                  'row': 0, 'col': 0, 'symbols': ['니나']}]}
        with self.assertRaisesRegex(ValueError, 'c1-j0.*음열'):
            performed(value, config)


if __name__ == '__main__':
    unittest.main()
