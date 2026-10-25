"""Run actual local photo inference, then compare with separately stored references.

Reference JSON is read only AFTER inference. This command never supplies labels to
the recognizer. Evaluation is bounded to the supplied cases, not a benchmark claim.
"""
import argparse
from datetime import datetime, timezone
from fractions import Fraction
import hashlib
import json
from pathlib import Path
import time

from .recognition import recognize
from .score import resolve_pitch


def events(score):
    """Main written notes/rests; ornaments are counted separately, not hidden."""
    return [(None if n.get("rest") else resolve_pitch(n["pitch"],score["context"]),
             str(Fraction(str(n["duration"])))) for n in score["notes"]]


def expanded_events(score):
    """Symbolic sounding events before breath gaps, envelopes and tempo.

    Compare equivalent ornament objects and explicit note sequences without
    confusing their representation with recognition quality. Rests advance the
    clock, and explicit ties merge; repeated untied notes remain separate.
    """
    cursor = Fraction()
    result = []
    tied = False
    for note in score['notes']:
        duration = Fraction(str(note['duration']))
        if duration <= 0:
            raise ValueError('Evaluation duration must be positive')
        start = cursor
        cursor += duration
        if note.get('rest'):
            if tied:
                raise ValueError('Rest interrupts evaluation tie')
            continue
        pitch = resolve_pitch(note['pitch'], score['context'])
        tie = note.get('tie')
        if tied:
            if (tie not in ('continue', 'stop') or not result or result[-1][0] != pitch
                    or result[-1][1]+result[-1][2] != start or note.get('ornament')):
                raise ValueError('Invalid evaluation tie continuation')
            prior = result[-1]
            result[-1] = (pitch, prior[1], prior[2]+duration)
            tied = tie == 'continue'
            continue
        if tie not in (None, 'start'):
            raise ValueError('Unmatched evaluation tie')
        tied = tie == 'start'
        ornament = note.get('ornament')
        if not ornament:
            result.append((pitch, start, duration))
            continue
        if tied or ornament['kind'] not in ('upper-return', 'grace-before'):
            raise ValueError('Unsupported evaluation ornament')
        other = resolve_pitch(ornament['pitch'], score['context'])
        step = Fraction(str(ornament.get('duration', '1/16')))
        count = 2 if ornament['kind'] == 'upper-return' else 1
        if step <= 0 or count*step >= duration:
            raise ValueError('Evaluation ornament consumes main note')
        if count == 2:
            result.extend([(pitch,start,step),(other,start+step,step),
                           (pitch,start+2*step,duration-2*step)])
        else:
            result.extend([(other,start,step),(pitch,start+step,duration-step)])
    if tied:
        raise ValueError('Unclosed evaluation tie')
    return [(pitch,str(start),str(duration)) for pitch,start,duration in result]


def alignment(reference, candidate):
    """Unit-cost sequence edit alignment, deterministic tie order."""
    rows, cols = len(reference), len(candidate)
    d = [[0]*(cols+1) for _ in range(rows+1)]
    for i in range(rows+1):
        d[i][0] = i
    for j in range(cols+1):
        d[0][j] = j
    for i in range(1,rows+1):
        for j in range(1,cols+1):
            d[i][j] = min(d[i-1][j-1]+(reference[i-1]!=candidate[j-1]),
                          d[i-1][j]+1,d[i][j-1]+1)
    i,j = rows,cols
    pairs = []
    while i or j:
        if i and j and d[i][j] == d[i-1][j-1]+(reference[i-1]!=candidate[j-1]):
            pairs.append((i-1,j-1));i-=1;j-=1
        elif i and d[i][j] == d[i-1][j]+1:
            pairs.append((i-1,None));i-=1
        else:
            pairs.append((None,j-1));j-=1
    return d[rows][cols],list(reversed(pairs))


def compare(reference, candidate):
    ref, pred = events(reference), events(candidate)
    pitch_edits,pairs = alignment([x[0] for x in ref],[x[0] for x in pred])
    joint_edits,joint_pairs = alignment(ref,pred)
    matched = [(i,j) for i,j in pairs if i is not None and j is not None and ref[i][0]==pred[j][0]]
    def ornament(note, context):
        value = note.get('ornament')
        if not value:
            return None
        return (value['kind'], resolve_pitch(value['pitch'], context),
                str(Fraction(str(value.get('duration', '1/16')))))
    ref_ornaments = [ornament(n, reference['context']) for n in reference['notes']]
    pred_ornaments = [ornament(n, candidate['context']) for n in candidate['notes']]
    ornament_parents = [(i,j) for i,j in joint_pairs if i is not None and j is not None and ref[i][0]==pred[j][0]]
    ornament_matches = sum(ref_ornaments[i] is not None and ref_ornaments[i] == pred_ornaments[j]
                           for i,j in ornament_parents)
    ref_count, pred_count = sum(x is not None for x in ref_ornaments), sum(x is not None for x in pred_ornaments)
    expanded_ref, expanded_pred = expanded_events(reference), expanded_events(candidate)
    _, expanded_pitch_pairs = alignment([e[0] for e in expanded_ref], [e[0] for e in expanded_pred])
    duration_ref, duration_pred = [[(e[0],e[2]) for e in seq] for seq in (expanded_ref,expanded_pred)]
    _, expanded_duration_pairs = alignment(duration_ref, duration_pred)
    expanded_edits, expanded_pairs = alignment(expanded_ref, expanded_pred)
    return {"reference_main_events":len(ref),"recognized_main_events":len(pred),
            "reference_expanded_events":len(expanded_ref),"recognized_expanded_events":len(expanded_pred),
            "expanded_pitch_matches":sum(i is not None and j is not None and expanded_ref[i][0]==expanded_pred[j][0] for i,j in expanded_pitch_pairs),
            "expanded_pitch_and_duration_matches":sum(i is not None and j is not None and duration_ref[i]==duration_pred[j] for i,j in expanded_duration_pairs),
            "expanded_timed_matches":sum(i is not None and j is not None and expanded_ref[i]==expanded_pred[j] for i,j in expanded_pairs),
            "expanded_timed_edit_distance":expanded_edits,
            "expanded_matching_rule":"Expanded ornament/tie sounding events; exact MIDI pitch, onset and duration in quarters. Rests advance time; breath gaps, envelopes and tempo excluded.",
            "pitch_matches":len(matched),
            "reference_sounding_notes":sum(p is not None for p,d in ref),
            "recognized_sounding_notes":sum(p is not None for p,d in pred),
            "sounding_pitch_matches":sum(ref[i][0] is not None for i,j in matched),
            "sounding_pitch_and_duration_matches":sum(i is not None and j is not None and ref[i][0] is not None and ref[i]==pred[j] for i,j in joint_pairs),
            "rest_and_duration_matches":sum(i is not None and j is not None and ref[i][0] is None and ref[i]==pred[j] for i,j in joint_pairs),
            "pitch_and_duration_matches":sum(i is not None and j is not None and ref[i]==pred[j] for i,j in joint_pairs),
            "pitch_edit_distance":pitch_edits,"joint_edit_distance":joint_edits,
            "pitch_error_rate":pitch_edits/len(ref) if ref else None,
            "insertions":sum(i is None for i,j in pairs),"deletions":sum(j is None for i,j in pairs),
            "reference_ornament_count":ref_count,"recognized_ornament_count":pred_count,
            "ornament_matches":ornament_matches,
            "ornament_missed_or_wrong":ref_count-ornament_matches,
            "ornament_added_or_wrong":pred_count-ornament_matches,
            "ornament_matching_rule":"duration-aware main-event alignment, same parent pitch, kind, ornament pitch and quarter duration",
            "reference_quarter_bpm":reference['quarter_bpm'],"recognized_quarter_bpm":candidate['quarter_bpm'],
            "reference_total_quarters":str(sum((Fraction(x[1]) for x in ref),Fraction())),
            "recognized_total_quarters":str(sum((Fraction(x[1]) for x in pred),Fraction())),
            "alignment":[{"reference_index":i,"recognized_index":j,
                          "reference":ref[i] if i is not None else None,
                          "recognized":pred[j] if j is not None else None} for i,j in pairs]}


def dump(path,value):
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')


def summarize(reports):
    """Keep failed images in the denominator, with zero recovered events."""
    totals = {key: sum(r.get('metrics', {}).get(key, 0) for r in reports) for key in (
        'reference_main_events', 'recognized_main_events', 'pitch_matches', 'pitch_and_duration_matches',
        'reference_sounding_notes', 'recognized_sounding_notes', 'sounding_pitch_matches', 'sounding_pitch_and_duration_matches',
        'rest_and_duration_matches',
        'reference_expanded_events', 'recognized_expanded_events', 'expanded_pitch_matches',
        'expanded_pitch_and_duration_matches', 'expanded_timed_matches',
        'reference_ornament_count', 'recognized_ornament_count', 'ornament_matches')}
    reference, recognized = totals['reference_main_events'], totals['recognized_main_events']
    sounding = totals['reference_sounding_notes']
    return {'images':len(reports), 'recognized_images':sum(r['status']=='recognized' for r in reports),
            'failed_images':sum(r['status']=='failed' for r in reports), **totals,
            'pitch_recall':totals['pitch_matches']/reference if reference else None,
            'pitch_precision':totals['pitch_matches']/recognized if recognized else None,
            'pitch_and_duration_recall':totals['pitch_and_duration_matches']/reference if reference else None,
            'sounding_pitch_recall':totals['sounding_pitch_matches']/sounding if sounding else None,
            'sounding_pitch_and_duration_recall':totals['sounding_pitch_and_duration_matches']/sounding if sounding else None,
            'expanded_timed_recall':totals['expanded_timed_matches']/totals['reference_expanded_events'] if totals['reference_expanded_events'] else None,
            'note':'Micro average over supplied references; failures count as zero recovery. Not general accuracy.'}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cases',required=True,help='JSON list: id, image, notation, context, reference')
    parser.add_argument('--out',required=True)
    parser.add_argument('--model',default='qwen3-vl:4b-instruct-q4_K_M')
    parser.add_argument('--ollama-url',default='http://127.0.0.1:11434')
    parser.add_argument('--audiveris',help='Installed Audiveris executable; uses the same staff reader as the service')
    parser.add_argument('--direct',action='store_true',help='Experimental whole-image VLM baseline instead of segmented readers')
    args=parser.parse_args()
    out=Path(args.out);out.mkdir(parents=True,exist_ok=True)
    cases=json.loads(Path(args.cases).read_text(encoding='utf-8-sig'))
    dump(out/'run.json', {'started_at':datetime.now(timezone.utc).isoformat(),
        'model':args.model, 'audiveris':args.audiveris, 'direct':args.direct,
        'source_sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest()
                         for p in sorted(Path(__file__).parent.glob('*.py'))},
        'cases_sha256':hashlib.sha256(Path(args.cases).read_bytes()).hexdigest(),
        'protocol':'Reference content is read only after its inference attempt. Each split/conditions field declares prior tuning or independent evaluation; this command does not certify reference independence.'})
    reports=[]
    for number,case in enumerate(cases):
        name=f'{number:02d}'
        started=time.monotonic()
        report={'id':case['id'],'image_sha256':hashlib.sha256(Path(case['image']).read_bytes()).hexdigest(),
                'notation':case['notation'],'context':case['context'],'model':args.model,
                'split':case.get('split','unspecified'), 'conditions':case.get('conditions',''),
                'scope':'supplied excerpt only; manually transcribed references are not expert certification'}
        result = None
        try:
            if args.audiveris and case['notation']=='staff' and not args.direct:
                from .staff import recognize_staff
                result=recognize_staff(case['image'],case['context'],executable=args.audiveris,
                    model=args.model,base_url=args.ollama_url,trace_path=out/f'{name}-trace.json')
            else:
                result=recognize(case['image'],case['notation'],case['context'],model=args.model,
                    base_url=args.ollama_url,trace_path=out/f'{name}-trace.json',structured=not args.direct)
            dump(out/f'{name}-recognized.json',result)
            report.update(status='recognized',warnings=result['warnings'])
        except (ValueError,OSError) as exc:
            report.update(status='failed',error=str(exc))
        report['elapsed_seconds']=round(time.monotonic()-started,3)
        # Deliberately after the request, including failures: labels never enter inference.
        raw_reference = Path(case['reference']).read_bytes()
        reference = json.loads(raw_reference.decode('utf-8-sig'))
        candidate = result['score'] if result is not None else {**reference, 'notes':[], 'quarter_bpm':None}
        report.update(reference_sha256=hashlib.sha256(raw_reference).hexdigest(),metrics=compare(reference,candidate))
        reports.append(report)
        dump(out/'report.json',reports)
        groups = {(r['notation'],r['split']) for r in reports}
        dump(out/'summary.json',{'overall':summarize(reports),'groups':{
            notation+'/'+split:summarize([r for r in reports if (r['notation'],r['split'])==(notation,split)])
            for notation,split in sorted(groups)}})
        print(json.dumps({k:v for k,v in report.items() if k!='metrics'},ensure_ascii=False),flush=True)


if __name__=='__main__':
    main()
