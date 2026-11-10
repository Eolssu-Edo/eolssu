from copy import deepcopy
import unittest

from data.src.scores.rebind_performance import rebind_performance


OLD_SHA = '1' * 64
NEW_SHA = '2' * 64
AUDIO_SHA = '3' * 64


def examples():
    def event(eid, cell, yul, start, duration):
        return dict(id=eid, kind='NOTE', yul=yul, octave=1, startTick=start,
                    durationTick=duration, cellId=cell, breathAfter=False, sigimsae=[])
    a, b, c = 'c0-j0-r0-u0', 'c0-j1-r0-u0', 'c0-j1-r0-u1'
    old = dict(schemaVersion=1,
               score=dict(title='검사', instrument='DANSO', part=0, sourceId='synthetic', scoreVersion=1),
               music=dict(hwangHz=311.13, jangdan='', bpmUnit='QUARTER', ticksPerBeat=4, ticksPerJeonggan=4),
               provenance=dict(sourceId='synthetic', jgbSha256='0' * 64, review='inferred', converter='fixture'),
               events=[event(a, 'c0-j0', '임', 0, 4), event(b, 'c0-j1', '중', 4, 2), event(c, 'c0-j1', '임', 6, 2)],
               columns=[dict(index=0, cells=[
                   dict(id='c0-j0', index=0, units=[dict(row=0, col=0, symbol='청임', eventId=a)]),
                   dict(id='c0-j1', index=1, units=[dict(row=0, col=0, symbol='청중', eventId=b),
                                                    dict(row=0, col=1, symbol='청임', eventId=c)])])])
    new = deepcopy(old)
    new['score']['scoreVersion'] = 2
    new['music'].update(ticksPerBeat=2, ticksPerJeonggan=2)
    new['events'] = [dict(old['events'][0], durationTick=4, sigimsae=['니나'])]
    new['columns'][0]['cells'][1]['units'] = [dict(row=0, col=0, symbol='-', eventId=a)]
    performance = dict(schema='common-score-performance/v1', policy='educational-preview/v1',
                       sourceSha256=OLD_SHA, scoreVersion=1, sourceId='synthetic', instrument='DANSO',
                       bpm=120., bpmUnit='QUARTER', audioSha256=AUDIO_SHA, durationMs=1000.003, segments=[])
    for i, (eid, cid, col, st, en, ms, end) in enumerate([
        (a, 'c0-j0', 0, 0., 4., 0., 400.001),
        (b, 'c0-j1', 0, 4., 6., 400.001, 700.002),
        (c, 'c0-j1', 1, 6., 8., 700.002, 1000.003),
    ]):
        performance['segments'].append(dict(occurrenceId=f'unit-{i}', eventId=eid, cellId=cid, row=0, col=col,
                                           startTick=st, endTick=en, startMs=ms, endMs=end, sigimsae=[]))
    return old, performance, new, {a: [a, b, c]}


class RebindPerformanceTests(unittest.TestCase):
    def bind(self, old, performance, new, groups):
        return rebind_performance(old, performance, new, previous_sha256=OLD_SHA,
                                  corrected_sha256=NEW_SHA, audio_sha256=AUDIO_SHA, changed_events=groups)

    def test_merged_ornament_uses_new_slots_and_preserves_measured_times(self):
        old, performance, new, groups = examples()
        before = deepcopy((old, performance, new))
        result = rebind_performance(old, performance, new, previous_sha256=OLD_SHA,
                                    corrected_sha256=NEW_SHA, audio_sha256=AUDIO_SHA, changed_events=groups)
        self.assertEqual(result['sourceSha256'], NEW_SHA)
        self.assertEqual(result['scoreVersion'], 2)
        self.assertEqual(result['audioSha256'], AUDIO_SHA)
        self.assertEqual(result['durationMs'], 1000.003)
        self.assertEqual(len(result['segments']), 2)
        self.assertEqual([(s['startTick'], s['endTick']) for s in result['segments']], [(0., 2.), (2., 4.)])
        self.assertEqual([(s['startMs'], s['endMs']) for s in result['segments']], [(0., 400.001), (400.001, 1000.003)])
        self.assertEqual([s['eventId'] for s in result['segments']], ['c0-j0-r0-u0'] * 2)
        self.assertEqual([s['sigimsae'] for s in result['segments']], [['니나'], []])
        self.assertEqual((old, performance, new), before)

    def test_boundary_inside_original_slot_interpolates_measured_time(self):
        old, performance, new, groups = examples()
        new['columns'][0]['cells'][0]['units'].append(
            dict(row=0, col=1, symbol='-', eventId=new['events'][0]['id']))
        result = self.bind(old, performance, new, groups)
        self.assertEqual(result['segments'][0]['endMs'], 200.0005)
        self.assertEqual(result['segments'][1]['startMs'], 200.0005)
        self.assertEqual(result['segments'][1]['endMs'], 400.001)

    def test_stale_source_or_audio_hash_is_rejected(self):
        for field in ('sourceSha256', 'audioSha256'):
            with self.subTest(field=field):
                old, performance, new, groups = examples()
                performance[field] = 'f' * 64
                with self.assertRaisesRegex(ValueError, 'mismatch'):
                    self.bind(old, performance, new, groups)

    def test_changed_ordinary_pitch_cannot_reuse_audio(self):
        old, performance, _, _ = examples()
        new = deepcopy(old)
        new['score']['scoreVersion'] = 2
        new['events'][0]['yul'] = '남'
        new['columns'][0]['cells'][0]['units'][0]['symbol'] = '청남'
        with self.assertRaisesRegex(ValueError, 'pitch'):
            self.bind(old, performance, new, {})

    def test_missing_and_reordered_ornament_members_are_rejected(self):
        for ids in ([0, 1], [0, 2, 1]):
            with self.subTest(ids=ids):
                old, performance, new, groups = examples()
                groups[new['events'][0]['id']] = [old['events'][i]['id'] for i in ids]
                with self.assertRaisesRegex(ValueError, 'span'):
                    self.bind(old, performance, new, groups)

    def test_added_breath_requires_new_audio(self):
        old, performance, new, groups = examples()
        new['events'][0]['breathAfter'] = True
        with self.assertRaisesRegex(ValueError, 'breath'):
            self.bind(old, performance, new, groups)

    def test_repeated_missing_slots_and_broken_time_are_rejected(self):
        for mutation in ('repeat', 'missing', 'time', 'annotation'):
            with self.subTest(mutation=mutation):
                old, performance, new, groups = examples()
                if mutation == 'repeat':
                    performance['segments'].append(deepcopy(performance['segments'][0]))
                elif mutation == 'missing':
                    performance['segments'].pop()
                elif mutation == 'time':
                    performance['segments'][1]['startMs'] += 1
                else:
                    performance['segments'][0]['sigimsae'] = ['니나']
                with self.assertRaises(ValueError):
                    self.bind(old, performance, new, groups)


if __name__ == '__main__':
    unittest.main()
