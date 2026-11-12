"""Manual golden fixture generation with real CAT/FFmpeg; never runs in the unit suite.

Run from the repository root after setting PLAYBACK_ASSETS_JSON,
PLAYBACK_JANGGU_SAMPLES, FFMPEG_BINARY and FFPROBE_BINARY.
"""
import hashlib
import json
import sys
from pathlib import Path

import mido

from data.src.playback.render_request import render
from data.src.playback.tests._helpers import common_notes
from data.tests.test_playback_import import bundle, source as jgb_source


def generate():
    catalog = json.loads(Path('data/reference/jangdans.json').read_text(encoding='utf-8'))['rows']
    target = Path('frontend/shared/src/androidHostTest/resources/scores/personal-jangdan')
    target.mkdir(parents=True, exist_ok=True)
    for name, jangdan_id, pattern_name, ramp, repeat in (
            ('step', '12', '4박 기본 반주', False, False),
            ('repeat', '12', '4박 기본 반주', False, True),
            ('ramp', '7', '단모리', True, False),
            ('dotted', '11', '자진타령', False, False),
            ('weighted', '7', '단모리', False, False),
            ('weighted-ramp', '7', '단모리', True, False)):
        if sys.argv[1:] and name not in sys.argv[1:]:
            continue
        row = next(row for row in catalog if row['name'] == pattern_name)
        source = common_notes((None, ('황', 1), ('태', 1), ('황', 1)))
        imported = bundle(jgb_source('청황{덧길이표} 청태{반길이표}|청황{덧길이표} 청태{반길이표}|청태|청황', beats=4)) if name.startswith('weighted') else None
        if imported:
            source = imported['common']
        source['score'].update(title=f'로컬 장단 시간표 {name}', sourceId=f'tempo773-{name}')
        source['provenance']['sourceId'] = source['score']['sourceId']
        source['music'].update(jangdan=pattern_name, bpmUnit=row['bpm_unit'])
        source_json = json.dumps(source, ensure_ascii=False, sort_keys=True, separators=(',', ':'))
        digest = hashlib.sha256(source_json.encode()).hexdigest()
        tempos = ([{'gak': 1, 'cell': 1, 'bpm': 120, 'end_gak': 2, 'end_cell': 1}] if ramp
                  else [] if imported else [{'gak': 1, 'cell': 3, 'bpm': 30}])
        expressions = {'schema': 'common-score-expressions/v1', 'sourceSha256': digest, 'placements': [],
                       'performancePlan': {'schema': 'danso-performance/v1', 'input_sha256': digest, 'tempos': tempos,
                                           'repeats': [{'start_gak': 1, 'end_gak': 1, 'times': 2}] if repeat else []}}
        if imported:
            expressions.update({key: value for key, value in imported['expressions'].items()
                                if key not in ('sourceSha256', 'performancePlan')})
        jangdan = {'name': row['name'], 'bpmUnit': row['bpm_unit'], 'pattern': row['pattern'],
                   'patternCellEighths': row['pattern_cell_eighths']}
        request = {'score': source, 'instrument': 'danso', 'bpm': 60, 'expressions': expressions,
                   'performanceSourceSha256': digest, 'withJangdan': False, 'jangdan': None}
        fixture = {'sourceJson': source_json, 'sourceSha256': digest, 'jangdanId': jangdan_id,
                   'jangdan': jangdan, 'expressions': expressions}
        for accompanied in (False, True):
            root = Path('.scratch/local-jangdan') / name / ('accompanied' if accompanied else 'melody')
            root.mkdir(parents=True, exist_ok=True)
            request.update(withJangdan=accompanied, jangdan=jangdan if accompanied else None)
            (root / 'request.json').write_text(json.dumps(request, ensure_ascii=False), encoding='utf-8')
            render(str(root))
            if not accompanied:
                fixture.update(performance=json.loads((root / 'performance.common.json').read_text(encoding='utf-8')),
                               audioDurationMs=json.loads((root / 'result.json').read_text())['durationMs'],
                               audioBytes=(root / 'audio.m4a').stat().st_size)
            else:
                elapsed, hits = 0., []
                for message in mido.MidiFile(root / 'score.mid'):
                    elapsed += message.time
                    if message.type == 'note_on' and message.channel == 9 and message.velocity:
                        hits.append({'ms': round(elapsed * 1000, 6), 'note': message.note})
                fixture['expectedStrikes'] = hits
                fixture['accompaniedAudioSha256'] = hashlib.sha256((root / 'audio.m4a').read_bytes()).hexdigest()
        (target / f'{name}.json').write_text(json.dumps(fixture, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print(name, fixture['audioDurationMs'], fixture['expectedStrikes'])


if __name__ == '__main__':
    generate()
