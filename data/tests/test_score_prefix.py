"""앞 N각 자르기 (S15P31E102-1227). 합성 2각 악보·시간표로 검사한다."""
from copy import deepcopy
from pathlib import Path
import shutil
import tempfile
import unittest

import numpy as np

from data.src.catalog.score_prefix import cut_audio, cut_performance, cut_score, decode, gak_end_tick
from data.src.scores.rebind_performance import validate_performance

SRC_SHA, NEW_SHA, AUDIO_SHA, NEW_AUDIO = '1' * 64, '2' * 64, '3' * 64, '4' * 64


def examples():
    """2각 × 2정간, ticksPerJeonggan 2. 0각: 임(4틱, 두 정간 이음), 1각: 중(2틱)·쉼(2틱)."""
    def event(eid, kind, start, duration, yul='임'):
        return dict(id=eid, kind=kind, yul=yul, octave=1, startTick=start, durationTick=duration,
                    cellId=eid[:5], breathAfter=False, sigimsae=[])
    a, b, c = 'c0-j0-r0-u0', 'c1-j0-r0-u0', 'c1-j1-r0-u0'
    score = dict(schemaVersion=1,
                 score=dict(title='도라지타령', instrument='SOGEUM', part=0, sourceId='orig', scoreVersion=1),
                 music=dict(hwangHz=311.13, jangdan='세마치', bpmUnit='QUARTER', ticksPerBeat=2, ticksPerJeonggan=2),
                 provenance=dict(sourceId='orig', jgbSha256='0' * 64, review='reviewed', converter='jgb-playback-import/v1'),
                 events=[event(a, 'NOTE', 0, 4), event(b, 'NOTE', 4, 2, '중'), event(c, 'REST', 6, 2)],
                 columns=[dict(index=0, cells=[dict(id='c0-j0', index=0, lyric='도', units=[dict(row=0, col=0, symbol='청임', eventId=a)]),
                                               dict(id='c0-j1', index=1, units=[dict(row=0, col=0, symbol='', eventId=a)])]),
                          dict(index=1, cells=[dict(id='c1-j0', index=0, units=[dict(row=0, col=0, symbol='청중', eventId=b)]),
                                               dict(id='c1-j1', index=1, units=[dict(row=0, col=0, symbol='쉼', eventId=c)])])])
    performance = dict(schema='common-score-performance/v1', policy='educational-preview/v1', sourceSha256=SRC_SHA,
                       scoreVersion=1, sourceId='orig', instrument='SOGEUM', bpm=120., bpmUnit='QUARTER',
                       audioSha256=AUDIO_SHA, durationMs=2000.004, segments=[])
    for i, (eid, cid, st, ms) in enumerate([(a, 'c0-j0', 0., 0.), (a, 'c0-j1', 2., 500.001),
                                            (b, 'c1-j0', 4., 1000.002), (c, 'c1-j1', 6., 1500.003)]):
        performance['segments'].append(dict(occurrenceId=f'unit-{i}', eventId=eid, cellId=cid, row=0, col=0,
                                            startTick=st, endTick=st + 2, startMs=ms, endMs=ms + 500.001, sigimsae=[]))
    return score, performance


class ScorePrefixTests(unittest.TestCase):
    def test_first_gak_keeps_its_events_cells_and_lyrics_and_takes_the_new_identity(self):
        score, _ = examples()
        before = deepcopy(score)
        cut = cut_score(score, 1, source_id='new-src', title='도라지타령 1각')
        self.assertEqual(score, before)
        self.assertEqual([e['id'] for e in cut['events']], ['c0-j0-r0-u0'])
        self.assertEqual([c['index'] for c in cut['columns']], [0])
        self.assertEqual(cut['columns'][0]['cells'][0]['lyric'], '도')
        self.assertEqual((cut['score']['sourceId'], cut['score']['title'], cut['provenance']['sourceId']),
                         ('new-src', '도라지타령 1각', 'new-src'))
        self.assertEqual(cut['provenance']['review'], 'reviewed')

    def test_gak_end_tick_counts_cells_times_ticks_per_jeonggan(self):
        self.assertEqual(gak_end_tick(examples()[0], 1), 4)

    def test_a_note_held_across_the_cut_is_rejected(self):
        score, _ = examples()
        score['events'][0]['durationTick'] = 6
        with self.assertRaises(ValueError):
            cut_score(score, 1, source_id='new-src', title='x')

    def test_cut_performance_keeps_measured_times_and_validates_against_the_cut_score(self):
        score, performance = examples()
        cut = cut_score(score, 1, source_id='new-src', title='도라지타령 1각')
        timed = cut_performance(performance, 4, source_sha256=NEW_SHA, audio_sha256=NEW_AUDIO, source_id='new-src')
        self.assertEqual([(s['startMs'], s['endMs']) for s in timed['segments']], [(0., 500.001), (500.001, 1000.002)])
        self.assertEqual(timed['durationMs'], 1000.002)
        validate_performance(cut, timed, source_sha256=NEW_SHA, audio_sha256=NEW_AUDIO)


def onset(pcm, threshold=0.05):
    return int(np.argmax(np.abs(pcm) > threshold))


@unittest.skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'), 'ffmpeg·ffprobe 필요')
class CutAudioTests(unittest.TestCase):
    def test_cut_keeps_the_first_onset_and_ends_at_the_cut(self):
        from data.src.playback.audio import FS, resolve_encoder, write_audio
        encoder = resolve_encoder()
        t = np.arange(FS) / FS
        pcm = np.where(t >= .1, .5 * np.sin(2 * np.pi * 440 * t), 0.).astype('<f4')
        with tempfile.TemporaryDirectory() as work:
            source, target = Path(work, 'full.m4a'), Path(work, 'cut.m4a')
            write_audio(pcm, source, encoder)
            report = cut_audio(source, target, 500, encoder)
            full, cut = decode(source, encoder), decode(target, encoder)
        self.assertEqual(report['frames'], FS // 2)
        self.assertLess(len(cut) - FS // 2, 1024)
        self.assertLessEqual(abs(onset(cut) - onset(full)), FS * 5 // 1000)
        self.assertLessEqual(abs(onset(cut) - int(.1 * FS)), FS * 5 // 1000)


if __name__ == '__main__':
    unittest.main()
