"""Prepare the nine 699 score replacements; never writes to the service.

Independent ornaments are collapsed only when the original importer identifies
their exact generated events. The source's review status is not promoted.
Run from the repository root with ``python -m data.src.scores.repair_scoring``.
"""
import argparse
import copy
from fractions import Fraction
import hashlib
import json
from pathlib import Path

from data.src.playback.common_score import validate_common
from data.src.scores.playback_import import import_jgb
from data.src.scores.score_data import check, convert_answer
from infra.catalog.enable_scoring import canonical_sha, target_score


ORNAMENT_IDS = (19, 32, 37, 45, 50, 63, 65)
ANSWERS = {104: 'ngc-standard-2022-p202-part-1', 109: 'ngc-standard-2022-p203'}
SONG_IDS = (*ORNAMENT_IDS, *ANSWERS)
CONVERTER = 'scoring-ornament-collapse/v1'


def merge_independent_ornaments(production, slots):
    """Return a new version plus old-event -> surviving-event identities.

    The display grid and its lyrics stay in place. Only units belonging to an
    expanded ornament become continuations of the preceding written NOTE.
    """
    validate_common(production, allow_sigimsae=True)
    events = production['events']
    indexes = {event['id']: index for index, event in enumerate(events)}
    marks_by_id = {}
    if not slots:
        raise ValueError('no independent ornament slots')
    for slot in slots:
        if slot.get('expandedIndependent') is not True:
            raise ValueError('not an independent ornament slot')
        marks = slot.get('source', {}).get('symbols')
        if not isinstance(marks, list) or not marks or any(not isinstance(s, str) or not s for s in marks):
            raise ValueError('original ornament symbols are missing')
        ids = list(dict.fromkeys(item['eventId'] for item in slot['generated']))
        if not ids or any(eid not in indexes or eid in marks_by_id for eid in ids):
            raise ValueError('missing or overlapping generated events')
        positions = [indexes[eid] for eid in ids]
        if positions != list(range(positions[0], positions[0] + len(positions))):
            raise ValueError('generated ornament events are not contiguous')
        marks_by_id.update((eid, marks) for eid in ids)

    result = copy.deepcopy(production)
    kept, mapping = [], {}
    for event in result['events']:
        eid = event['id']
        if eid not in marks_by_id:
            kept.append(event)
            mapping[eid] = eid
            continue
        if not kept or kept[-1]['kind'] != 'NOTE' or event['kind'] != 'NOTE':
            raise ValueError(f'{eid}: an immediately preceding NOTE is required')
        anchor = kept[-1]
        if anchor['cellId'].split('-')[0] != event['cellId'].split('-')[0]:
            raise ValueError(f'{eid}: ornament crosses a column boundary')
        if anchor['startTick'] + anchor['durationTick'] != event['startTick']:
            raise ValueError(f'{eid}: ornament is not adjacent to its anchor')
        if anchor['breathAfter'] or event['sigimsae']:
            raise ValueError(f'{eid}: unexpected breath or nested ornament')
        anchor['durationTick'] += event['durationTick']
        anchor['sigimsae'] = list(dict.fromkeys([*anchor['sigimsae'], *marks_by_id[eid]]))
        anchor['breathAfter'] = event['breathAfter']
        mapping[eid] = anchor['id']
    result['events'] = kept
    for column in result['columns']:
        for cell in column['cells']:
            for unit in cell['units']:
                if unit['eventId'] in marks_by_id:
                    unit['eventId'] = mapping[unit['eventId']]
                    unit['symbol'] = '-'
    result['score']['scoreVersion'] += 1
    result['provenance']['converter'] = CONVERTER
    validate_common(result, allow_sigimsae=True)
    return result, mapping


def replace_with_answer(production, answer):
    """Use a previously reviewed answer while retaining each existing cell lyric."""
    validate_common(production, allow_sigimsae=True)
    validate_common(answer, allow_sigimsae=True)
    if answer['provenance']['review'] != 'reviewed':
        raise ValueError('answer has not been reviewed')
    result = target_score(production, answer)
    lyrics = {cell['id']: cell['lyric'] for col in production['columns']
              for cell in col['cells'] if 'lyric' in cell}
    cells = {cell['id']: cell for col in result['columns'] for cell in col['cells']}
    if not lyrics.keys() <= cells.keys():
        raise ValueError('answer removes existing lyric cells')
    for cid, lyric in lyrics.items():
        cells[cid]['lyric'] = lyric
    validate_common(result, allow_sigimsae=True)
    return result


def overlapping_events(before, after):
    """Map old intervals to new intervals in beats, including changed tick scales."""
    old_tpb, new_tpb = before['music']['ticksPerBeat'], after['music']['ticksPerBeat']
    mapping = {}
    for old in before['events']:
        start = Fraction(old['startTick'], old_tpb)
        end = Fraction(old['startTick'] + old['durationTick'], old_tpb)
        mapping[old['id']] = [new['id'] for new in after['events']
            if start < Fraction(new['startTick'] + new['durationTick'], new_tpb)
            and Fraction(new['startTick'], new_tpb) < end]
    return mapping


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def build_replacements(snapshots, audit_report):
    """Reproduce source maps, then return targets, the CAS plan and audit evidence."""
    audited = {row['songId']: row for row in read(audit_report)['songs']}
    targets, plan, evidence = {}, [], []
    for sid in SONG_IDS:
        raw = (snapshots / f'{sid}.json').read_bytes()
        payload = json.loads(raw)['result']
        production = payload['score']
        if str(payload['songId']) != str(sid) or payload['isScorable'] or payload['scoreVersion'] != 1 \
                or production['score']['scoreVersion'] != 1:
            raise ValueError(f'{sid}: expected the captured non-scorable first version')
        if sid in ORNAMENT_IDS:
            row = audited[sid]
            source_path = Path(row['sourcePath'])
            source_raw = source_path.read_bytes()
            if sha(raw) != row['serverSnapshotSha256'] or sha(source_raw) != production['provenance']['jgbSha256']:
                raise ValueError(f'{sid}: captured source changed')
            imported = import_jgb(json.loads(source_raw), source_name=source_path.name,
                instrument=production['score']['instrument'].lower().replace('_', '-'),
                part=production['score']['part'] + 1, input_sha256=sha(source_raw),
                source_id=production['score']['sourceId'], notation_policy='educational-preview/v1')
            if imported['common']['events'] != production['events'] or imported['common']['music'] != production['music']:
                raise ValueError(f'{sid}: source import no longer matches production')
            slots = [slot for slot in imported['source_map']['slots'] if slot['expandedIndependent']]
            target, mapping = merge_independent_ornaments(production, slots)
            answer_name = f'scoring-repair-699-{sid}'
            basis = 'source-mapped AI technical correction; existing inferred review preserved'
        else:
            answer_name = ANSWERS[sid]
            target = replace_with_answer(production, convert_answer(answer_name))
            mapping = {old: ids[0] for old, ids in overlapping_events(production, target).items() if len(ids) == 1}
            basis = 'existing reviewed answer, with original catalog lyrics retained'
        # The service's reviewed label fallback only applies to version 1.
        if not target['music']['jangdan'] and payload.get('jangdanName'):
            target['music']['jangdan'] = payload['jangdanName']
        validate_common(target, allow_sigimsae=True)
        if problems := check(target):
            raise ValueError(f'{sid}: {problems}')
        targets[sid] = target
        item = dict(songId=sid, source=production['score']['sourceId'], scoreVersion=1,
                    answer=answer_name, breathEventIds=[e['id'] for e in target['events'] if e['breathAfter']],
                    currentSha256=canonical_sha(production), targetSha256=canonical_sha(target),
                    targetScore=f'repair-699-scores/{sid}.json', targetScoreVersion=2)
        plan.append(item)
        evidence.append(dict(songId=sid, title=payload['title'], basis=basis,
            beforeScoreSha256=item['currentSha256'], targetScoreSha256=item['targetSha256'],
            beforeNoteCount=payload['noteCount'], targetNoteCount=sum(e['kind'] == 'NOTE' for e in target['events']),
            eventMap=mapping, overlappingEvents=overlapping_events(production, target),
            review=target['provenance']['review'], jangdanName=target['music']['jangdan']))
    return targets, plan, evidence


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--snapshots', type=Path, required=True)
    parser.add_argument('--audit-report', type=Path, required=True)
    parser.add_argument('--plan', type=Path, required=True)
    parser.add_argument('--evidence', type=Path, required=True)
    parser.add_argument('--check', action='store_true', help='reconstruct and compare without writing')
    args = parser.parse_args()
    targets, plan, evidence = build_replacements(args.snapshots, args.audit_report)
    outputs = {args.plan: encoded(plan), args.evidence: encoded({'task': 'S15P31E102-699', 'songs': evidence})}
    outputs.update((args.plan.parent / 'repair-699-scores' / f'{sid}.json', encoded(value))
                   for sid, value in targets.items())
    for path, raw in outputs.items():
        if path.exists() and path.read_bytes() != raw:
            raise ValueError(f'existing output differs: {path}')
        if args.check and not path.exists():
            raise ValueError(f'missing output: {path}')
    if not args.check:
        for path, raw in outputs.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            if not path.exists():
                with path.open('xb') as stream:
                    stream.write(raw)
    print(json.dumps({'songs': len(targets), 'ids': list(targets), 'checkedOnly': args.check}))


if __name__ == '__main__':
    main()
