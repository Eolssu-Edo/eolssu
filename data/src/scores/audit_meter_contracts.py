"""Compare fixed MusicXML contracts and separately captured actual OMR outputs.

The fixed XML path is conversion evidence only. --actual-dir reads separately
captured real engine results. Candidate math is a projection of FE/BE policy;
the Kotlin runtime checks are recorded separately in the report.
"""
import argparse
from collections import Counter
from fractions import Fraction as F
import hashlib
import json
from pathlib import Path
import sys
from time import monotonic
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'ai/src'))
sys.path.insert(0, str(ROOT))
from omr_service import staff_musicxml, staff_engine, jeongganbo_text
from data.src.playback.common_score import convert_common_score
from data.src.playback.authoring_tempo import preflight


def note_quarters(score):
    music = score['music']
    unit = F(3, 2) if music['bpmUnit'] == 'DOTTED_QUARTER' else F(1)
    return [[63 + staff_musicxml.YUL.index(e['yul']) + 12 * e['octave'],
             str(F(e['startTick'], music['ticksPerBeat']) * unit),
             str(F(e['durationTick'], music['ticksPerBeat']) * unit)]
            for e in score['events'] if e['kind'] == 'NOTE']


def inspect(score, rows, recognized_bpm=None, expressions=None):
    music = score['music']
    sizes = [len(x['cells']) for x in score['columns']]
    first = score['events'][0]
    choices = []
    for i, r in enumerate(rows, 1):
        beat_eighths = 3 if r['bpm_unit'] == 'DOTTED_QUARTER' else 2
        cycle = F(len(r['pattern']['beats']) * r['pattern_cell_eighths'] * music['ticksPerBeat'], beat_eighths)
        reasons = []
        if r['bpm_unit'] != music['bpmUnit']:
            reasons.append('BPM_UNIT')
        if cycle.denominator != 1:
            reasons.append('FRACTIONAL_CYCLE_TICK')
        if any((n * music['ticksPerJeonggan'] / cycle).denominator != 1 for n in sizes):
            reasons.append('COLUMN_NOT_COMPLETE_CYCLE')
        if first['cellId'] != 'c0-j0' or first['startTick'] != 0:
            reasons.append('START_ORIGIN')
        choices.append(dict(id=str(i), name=r['name'], cycleQuarters=str(F(len(r['pattern']['beats']) * r['pattern_cell_eighths'],2)),
                            cycleTicks=str(cycle), eligible=not reasons, excludedBy=reasons))
    selected = next((r for r in rows if r['name'] == music['jangdan']), None)
    bpm = recognized_bpm or (selected['default_bpm'] if selected else 60)
    digest = hashlib.sha256(json.dumps(score, ensure_ascii=False).encode()).hexdigest()
    playback_status, playback_detail = 'PASS', None
    try:
        _, mapping = convert_common_score(score, bpm=bpm, instrument=score['score']['instrument'].lower(), input_sha256=digest,
                                         notation_policy='educational-preview/v1')
        assert len(mapping['events']) == len(score['events'])
        for event, actual in zip(score['events'], mapping['events']):
            assert actual['id'] == event['id']
            assert abs(actual['start_seconds'] - event['startTick'] / music['ticksPerBeat'] * 60 / bpm) < 1e-9
            assert abs(actual['duration_seconds'] - event['durationTick'] / music['ticksPerBeat'] * 60 / bpm) < 1e-9
    except Exception as error:
        playback_status, playback_detail = 'FAIL', str(error)
    preflight_status, preflight_detail = 'PASS', None
    try:
        performance, _ = preflight(dict(score=score, bpm=bpm,
            instrument=score['score']['instrument'].lower().replace('_','-'),
            withJangdan=False, expressions=expressions))
        assert performance['events']
    except Exception as error:
        preflight_status, preflight_detail = 'FAIL', str(error)
    total = sum(x['durationTick'] for x in score['events'])
    return dict(music=music, columnSizes=sizes, eventCount=len(score['events']),
                sourceGridQuarters=str(F(music['ticksPerJeonggan'],music['ticksPerBeat']) * (F(3,2) if music['bpmUnit']=='DOTTED_QUARTER' else 1)),
                defaultBpm=bpm, bpmOrigin=('recognized numeric tempo' if recognized_bpm else
                                          'catalog default' if selected else 'no-accompaniment fallback 60'),
                accompaniment='catalog' if selected else 'none',
                durationAtDefaultSeconds=total / music['ticksPerBeat'] * 60 / bpm,
                candidates=[x['id'] for x in choices if x['eligible']], candidateChecks=choices,
                playbackProjection=playback_status, playbackDetail=playback_detail,
                melodyPreflight=preflight_status, melodyPreflightDetail=preflight_detail,
                localJsonRoundTrip=json.loads(json.dumps(score, ensure_ascii=False)) == score)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--actual-dir', type=Path)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    rows = list(jeongganbo_text.JANGDANS.values())
    fixtures = ROOT / 'data/tests/fixtures/meter-validation'
    result = {'fixedMusicXml': [], 'actualOmr': []}
    for case in json.loads((fixtures/'contracts.json').read_text(encoding='utf-8'))['cases']:
        conv = staff_musicxml.convert((fixtures/case['path']).read_bytes(), instrument='sogeum', hwang_pitch=63)
        jangdan = conv.jangdan or ''
        request = SimpleNamespace(conversion_id=case['id'],instrument='sogeum',hwang_pitch=63)
        unit = conv.bpm_unit
        bpj = conv.beat / staff_engine.UNIT_QUARTERS[unit]
        _, score = jeongganbo_text.lines_to_score(conv.lines, request, jangdan, lambda _:bpj, monotonic()+30,
                                                'staff-omr/audiveris-5.11.0', continue_explicit_ties=True,
                                                gak_beats=conv.gak_beats, bpm_unit=unit)
        staff_engine.restore_source_pitches(score, conv.source_pitches, 63)
        (args.out/(case['id']+'.score.json')).write_text(json.dumps(score,ensure_ascii=False,indent=2),encoding='utf-8')
        actual = note_quarters(score)
        expected = [[m,str(F(s)),str(F(d))] for m,s,d in case['expected']['notes']]
        actual_bars = [str(F(len(c['cells']) * score['music']['ticksPerJeonggan'],score['music']['ticksPerBeat']) *
                           staff_engine.UNIT_QUARTERS[unit]) for c in score['columns']]
        expected_bars = case['expected'].get('barQuarterLengths')
        row = dict(id=case['id'], evidence='fixed MusicXML; no image OMR', **inspect(score,rows),
                   expectedNotes=expected, actualNotes=actual, noteFidelity='PASS' if actual==expected else 'FAIL',
                   expectedBarQuarterLengths=expected_bars, actualBarQuarterLengths=actual_bars,
                   barFidelity='NOT_SPECIFIED' if expected_bars is None else
                       'PASS' if list(map(lambda n:str(F(n)),expected_bars))==actual_bars else 'FAIL',
                   issues=conv.issues, lines=conv.lines)
        result['fixedMusicXml'].append(row)
        print(case['id'],row['noteFidelity'],row['candidates'],row['playbackProjection'])
    if args.actual_dir:
        for path in sorted(args.actual_dir.glob('*/engine.json')):
            item=json.loads(path.read_text(encoding='utf-8'))
            result['actualOmr'].append(dict(id=path.parent.name,evidence='actual PDF/image engine',
                                           **inspect(item['score'],rows,item.get('bpm'),item.get('expressions')),
                                           issues=dict(Counter(x['kind'] for x in item['issues']))))
    (args.out/'audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    failed=[x['id'] for x in result['fixedMusicXml'] if x['noteFidelity']!='PASS' or x['playbackProjection']!='PASS' or
            x['melodyPreflight']!='PASS' or x['barFidelity']=='FAIL']
    failed += [x['id'] for x in result['actualOmr'] if x['playbackProjection']!='PASS' or x['melodyPreflight']!='PASS']
    print('Fixed fidelity/playback failures:',failed)
    return 1 if failed else 0


if __name__ == '__main__':
    raise SystemExit(main())
