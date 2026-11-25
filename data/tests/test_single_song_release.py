"""곡 하나 release·적재 모델 만들기 (S15P31E102-1227)."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from data.src.catalog.score_prefix import cut_performance, cut_score
from data.src.catalog.single_song_release import build_release
from data.tests.test_score_prefix import examples


def write_case(folder):
    score, performance = examples()
    cut = cut_score(score, 1, source_id='new-src', title='도라지타령 1각')
    source_bytes = json.dumps(cut, ensure_ascii=False).encode('utf-8')
    audio = b'fake m4a bytes'
    timed = cut_performance(performance, 4, source_sha256=hashlib.sha256(source_bytes).hexdigest(),
                            audio_sha256=hashlib.sha256(audio).hexdigest(), source_id='new-src')
    (folder / 'source.common.json').write_bytes(source_bytes)
    (folder / 'performance.common.json').write_text(json.dumps(timed), encoding='utf-8')
    (folder / 'sogeum.m4a').write_bytes(audio)
    return cut, audio


class SingleSongReleaseTests(unittest.TestCase):
    def build(self, work, **kwargs):
        options = dict(case_id='sogeum-227-p1', duration_ms=1001, task='S15P31E102-1227', derived_from={'songId': 93},
                       rights={'status': 'UNCONFIRMED'}, review={'source': 'inferred'})
        options.update(kwargs)
        return build_release(work / 'in', work / 'out', **options)

    def test_model_row_binds_keys_hashes_and_score_to_the_release(self):
        with tempfile.TemporaryDirectory() as tmp:
            work = Path(tmp)
            (work / 'in').mkdir()
            cut, audio = write_case(work / 'in')
            model = self.build(work)
            release = (work / 'out' / 'release' / 'release.json').read_bytes()
            checksum = hashlib.sha256(release).hexdigest()
            (row,) = model['rows']
            prefix = f'model-audio/drafts/{checksum}/cases/sogeum-227-p1/'
            self.assertEqual(model['manifestSha256'], checksum)
            self.assertEqual((row['object_key'], row['performance_object_key']),
                             (prefix + 'sogeum.m4a', prefix + 'performance.common.json'))
            self.assertEqual(row['sha256'], hashlib.sha256(audio).hexdigest())
            self.assertEqual((row['score_json'], row['source'], row['title'], row['note_count']),
                             (cut, 'new-src', '도라지타령 1각', 1))
            self.assertEqual((row['instrument_code'], row['default_bpm'], row['jangdan_name']), ('SOGEUM', 120, None))
            self.assertIn(row['difficulty'], (1, 2, 3))
            self.assertEqual(row['generation_metadata']['derivedFrom'], {'songId': 93})
            self.assertEqual(json.loads((work / 'out' / 'model.json').read_text(encoding='utf-8')), model)

    def test_case_id_must_follow_the_three_digit_object_key_rule(self):
        with tempfile.TemporaryDirectory() as tmp:
            work = Path(tmp)
            (work / 'in').mkdir()
            write_case(work / 'in')
            with self.assertRaises(ValueError):
                self.build(work, case_id='sogeum-1227-p1')

    def test_audio_shorter_than_the_timetable_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            work = Path(tmp)
            (work / 'in').mkdir()
            write_case(work / 'in')
            with self.assertRaises(ValueError):
                self.build(work, duration_ms=999)


if __name__ == '__main__':
    unittest.main()
