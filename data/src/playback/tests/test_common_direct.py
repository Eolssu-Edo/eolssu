"""Canonical common data must render without the legacy document interpreter."""
from copy import deepcopy
import json
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch

from data.src.playback.common_score import convert_common_score
from data.src.playback.score import compile_score


FIXTURE = Path(__file__).resolve().parents[3] / 'tests/fixtures/scores/common-preview-study.score.json'


class DirectCommonTests(unittest.TestCase):
    def test_fresh_process_never_imports_legacy_interpreter_modules(self):
        script = '''
import json, sys
sys.modules['data.src.playback.jgb'] = None
sys.modules['data.src.scores.score_data'] = None
from data.src.playback.common_score import convert_common_score
from data.src.playback.score import compile_score
with open(sys.argv[1], encoding='utf-8') as stream:
    source = json.load(stream)
for instrument in ('danso', 'sogeum', 'jeongak-daegeum', 'sanjo-daegeum'):
    source['score']['instrument'] = instrument.upper().replace('-', '_')
    score, mapping = convert_common_score(source, bpm=60, instrument=instrument,
        input_sha256='a'*64, notation_policy='educational-preview/v1')
    assert compile_score(score, instrument)['events']
    assert mapping['performanceTimeline']['durationMs'] == 29000
    assert mapping['expression_source_map']['schema'] == 'common-expression-map/v1'
'''
        result = subprocess.run([sys.executable, '-B', '-X', 'utf8', '-c', script, str(FIXTURE)],
                                text=True, capture_output=True, encoding='utf-8')
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_common_fixture_works_when_all_legacy_interpreters_are_blocked(self):
        source = json.loads(FIXTURE.read_text(encoding='utf-8'))
        before = deepcopy(source)
        with patch('data.src.playback.jgb.convert_jgb', side_effect=AssertionError('JGB conversion called')), \
             patch('data.src.playback.jgb.row_groups', side_effect=AssertionError('JGB row parser called')), \
             patch('data.src.scores.score_data.read_units', side_effect=AssertionError('Melody parser called')):
            score, mapping = convert_common_score(source, bpm=60, instrument='danso', input_sha256='a'*64,
                                                  notation_policy='educational-preview/v1')
            compiled = compile_score(score, 'danso')
        self.assertEqual(source, before)
        self.assertEqual([n.get('pitch') for n in score['notes'][:9]], [82, 80, 82, 80, None, 82, 80, 78, 80])
        self.assertEqual(mapping['performanceTimeline']['durationMs'], 29000)
        self.assertTrue(compiled['events'])


if __name__ == '__main__':
    unittest.main()
