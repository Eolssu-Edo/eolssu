"""정간보 스캔의 확인 필요 칸 표시를 사람이 대조한 정답으로 잰다 (S15P31E102-1061).

두 단계로 나눈다. 모델 추론은 collect에서 한 번만 하고, 표시 규칙 비교는 score에서 반복한다.

  collect  정답(data/annotations/scores/<원본ID>/*.review.json·*.jgb.json)의 원본 PDF를 sha256으로 찾아,
           API와 같은 300dpi 쪽 그림으로 omr_service.jeongganbo_scan.recognize를 실제 모델로 실행한다.
           칸마다 리더 결과(율명·신뢰도·보충 여부)와 그 칸에 실제로 붙은 issue를 곡별 JSON으로 남긴다.
  score    곡별 JSON을 정답 정간과 순서 정렬로 짝짓고, 칸이 틀렸는지와 표시됐는지를 센다.
           신뢰도 구간별 정답률과 표시 규칙 후보별 놓친 오류 수·확인 필요 칸 수를 낸다.

실행 (스캔 런타임: torch·OpenCV·pdfplumber·pypdfium2가 있는 Python, 저장소 루트에서):
  python -X utf8 ai/evaluation/jeongganbo_scan_review.py collect --sources <원본 PDF 폴더> \
      --model <MALerLab jeongganbo-omr 폴더> --out data/processed/scores/scan-review-1061/<이름>
  python -X utf8 ai/evaluation/jeongganbo_scan_review.py score data/processed/scores/scan-review-1061/<이름>

정답 판정은 칸 모양(줄 수·줄마다 음 단위 수)과 율명·이음·쉼표가 모두 같은지다. 시김새·숨표는 보지 않는다
(시김새 인식은 S15P31E102-1062 범위). 정답 정간과 짝이 없는 인식 칸은 틀린 칸, 인식 칸과 짝이 없는 정답 정간은
'빠진 정간'으로 따로 센다. 빠진 정간은 칸이 없어 표시할 수 없다(격자 문제, S15P31E102-1060).
"""
import argparse
import hashlib
import json
import sys
import types
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'ai' / 'src'))
sys.path.insert(0, str(ROOT / 'data' / 'src' / 'scores'))
from review_stats import jg_to_um, parse  # noqa: E402  정답 문법은 검토 화면 집계와 같다

DPI = 300  # omr_service.api.DPI와 같다. api는 fastapi가 필요해 여기서 import하지 않는다.
INSTRUMENT_BY_SUFFIX = {'단소': 'DANSO', '소금': 'SOGEUM'}
OCTAVE_PAIR = {'청황': '중청황', '중청황': '청황', '청태': '중청태', '중청태': '청태'}


# ---------- 정답 ----------

def truth_cells(jgb):
    """우물사이 문서의 첫 파트 선율을 읽는 순서의 정간 목록으로 바꾼다."""
    melody = jgb['parts'][0]['melody'] if 'parts' in jgb else jgb['melody']
    return [' '.join(cell.split()) for line in melody.split('\n') if line.strip() for cell in line.split('|')]


def pitch_rows(um):
    """칸 모양과 율명·이음·쉼표만 남긴다. 빈 정간과 '-' 하나는 같은 '이어짐'이다."""
    rows = [[core for core, _ in row] for row in parse(um)]
    return [] if rows in ([], [['-']]) else rows


def answers(folder):
    """원본 하나에 정답 하나가 대응하는 곡만 고른다. 2중주처럼 원본 하나에 정답이 둘이면 뺀다."""
    found = {}
    for review_path in sorted(Path(folder).glob('*/*.review.json')):
        review = json.loads(review_path.read_text(encoding='utf-8'))
        jgb_path = next(review_path.parent.glob('*.jgb.json'), None)
        hashes = review.get('sourceHashes') or {}
        if jgb_path is None or len(hashes) != 1:
            continue
        (source_id, sha), = hashes.items()
        # 표준악보집 검토 기록에는 song이 없다. 파일 이름 끝(_단소·_소금)과 sourceValues로 정한다.
        song = review.get('song') or {}
        instrument = song.get('instrument') or INSTRUMENT_BY_SUFFIX[
            jgb_path.name.removesuffix('.jgb.json').rsplit('_', 1)[-1].rstrip('0123456789')]
        jangdan = song.get('jangdan') or (review.get('sourceValues') or {}).get('jangdan') or ''
        found.setdefault(sha, []).append(dict(answer=review_path.parent.name, sourceId=source_id,
                                              instrument=instrument, jangdan=jangdan,
                                              truth=truth_cells(json.loads(jgb_path.read_text(encoding='utf-8')))))
    return {sha: items[0] for sha, items in found.items() if len(items) == 1}


# ---------- collect ----------

def _stub_api(page_time_s):
    """scan.recognize가 쓰는 api 이름만 둔다. 실제 api 모듈은 fastapi가 있어야 import된다."""
    from PIL import Image
    api = types.ModuleType('omr_service.api')

    class RecognitionFailed(Exception):
        pass
    api.PAGE_TIME_S, api.MAX_PIXELS, api.RecognitionFailed = page_time_s, Image.MAX_IMAGE_PIXELS, RecognitionFailed
    sys.modules['omr_service.api'] = api
    return api


@dataclass(frozen=True)
class _Page:
    path: Path
    width: int
    height: int


class _Captured(Exception):
    def __init__(self, lines, issues):
        self.lines, self.issues = lines, issues


def render(pdf, work):
    import pypdfium2 as pdfium
    pages = []
    with pdfium.PdfDocument(pdf) as doc:
        for i, page in enumerate(doc):
            image = page.render(scale=DPI / 72).to_pil()
            out = work / f'page-{i}.png'
            image.save(out)
            pages.append(_Page(out, *image.size))
    return pages


def recognize_cells(pdf, instrument, jangdan, work, page_time_s):
    """실제 recognize를 돌리고, 공통 악보 변환 직전에 칸과 issue를 가로챈다."""
    api = _stub_api(page_time_s)
    from omr_service import jeongganbo_scan as scan
    seen = []
    original_cell_text = scan.cell_text

    def recording_cell_text(cell):
        seen.append({k: cell.get(k) for k in ('text', 'conf', 'filled', 'ink', 'x', 'y', 'w', 'h')})
        return original_cell_text(cell)

    def capture(lines, request, jangdan_name, bpj_of, *rest, **kw):
        closure = dict(zip(bpj_of.__code__.co_freevars, (c.cell_contents for c in bpj_of.__closure__)))
        raise _Captured(lines, closure['issues'])

    pages = render(pdf, work)
    request = types.SimpleNamespace(instrument=instrument, jangdan=jangdan)
    job = types.SimpleNamespace(request=request, pages=pages, time_budget_s=page_time_s * len(pages))
    scan.cell_text, scan.lines_to_score = recording_cell_text, capture
    try:
        scan.recognize(job)
        raise RuntimeError('lines_to_score was not reached')
    except _Captured as got:
        lines, issues = got.lines, got.issues
    except api.RecognitionFailed:
        lines, issues = [], []
    finally:
        scan.cell_text = original_cell_text
    # cellId는 recognize와 같은 순서로 매긴다(c{각}-j{정간}). 읽힌 칸은 seen 순서와 같다.
    cells, k = [], 0
    for g, line in enumerate(lines):
        for j, text in enumerate(line.split(' | ')):
            cell_id = f'c{g}-j{j}'
            cells.append(dict(seen[k], cellId=cell_id, jg=text,
                              issues=[dict(kind=i['kind'], reason=(i.get('detail') or {}).get('reason'))
                                      for i in issues if i.get('cellId') == cell_id]))
            k += 1
    notices = [dict(kind=i['kind'], reason=(i.get('detail') or {}).get('reason'), page=i.get('page'))
               for i in issues if i.get('cellId') is None]
    return dict(engine=scan.ENGINE_VERSION, pages=len(pages), cells=cells, notices=notices)


def collect(args):
    import os
    import tempfile
    os.environ['JEONGGANBO_OMR_DIR'] = str(Path(args.model).resolve())
    by_sha = answers(args.answers)
    sources = {}
    for pdf in (p for folder in args.sources for p in Path(folder).rglob('*.pdf')):
        sha = hashlib.sha256(pdf.read_bytes()).hexdigest()
        if sha in by_sha:
            sources[sha] = pdf
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    shard, shards = map(int, args.shard.split('/'))
    for n, (sha, item) in enumerate(sorted(by_sha.items(), key=lambda kv: kv[1]['answer'])):
        if n % shards != shard or args.only and not any(o in item['answer'] for o in args.only):
            continue
        target = out / f"{item['answer']}.json"
        if target.exists() and not args.force:
            continue
        if sha not in sources:
            print(f"{item['answer']}: 원본 없음", flush=True)
            continue
        from time import monotonic
        began = monotonic()
        with tempfile.TemporaryDirectory(prefix='scan-review-') as work:
            try:
                result = recognize_cells(sources[sha], item['instrument'], item['jangdan'], Path(work), args.page_time)
            except TimeoutError:
                # 결과 파일을 쓰지 않으므로 다음 실행에서 다시 시도한다.
                print(f"{item['answer']}: 시간 초과({args.page_time}초/쪽), 건너뜀", flush=True)
                continue
        result.update(answer=item['answer'], sourceId=item['sourceId'], sourceSha256=sha,
                      sourceFile=sources[sha].name, instrument=item['instrument'], truth=item['truth'],
                      seconds=round(monotonic() - began, 1))
        target.write_text(json.dumps(result, ensure_ascii=False, indent=1), encoding='utf-8')
        print(f"{item['answer']}: 칸 {len(result['cells'])} / 정답 {len(item['truth'])}, {result['seconds']}초", flush=True)


# ---------- score ----------

def omr_rows(jg):
    try:
        return pitch_rows(jg_to_um(jg))
    except (ValueError, IndexError):
        return None  # 정답 문법으로 바꿀 수 없는 인식 결과는 틀린 칸으로 센다


def align(omr, truth):
    """정간 순서를 보존하는 최소 편집 정렬. 같은 칸은 비용 0, 다른 칸·빈자리는 1이다.
    (인식 칸 번호 또는 None, 정답 번호 또는 None) 목록을 돌려준다."""
    n, m = len(omr), len(truth)
    cost = [[0] * (m + 1) for _ in range(n + 1)]
    for i in range(1, n + 1):
        cost[i][0] = i
    for j in range(1, m + 1):
        cost[0][j] = j
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            cost[i][j] = min(cost[i - 1][j - 1] + (omr[i - 1] != truth[j - 1]), cost[i - 1][j] + 1, cost[i][j - 1] + 1)
    pairs, i, j = [], n, m
    while i or j:
        if i and j and cost[i][j] == cost[i - 1][j - 1] + (omr[i - 1] != truth[j - 1]):
            pairs.append((i - 1, j - 1))
            i, j = i - 1, j - 1
        elif i and cost[i][j] == cost[i - 1][j] + 1:
            pairs.append((i - 1, None))
            i -= 1
        else:
            pairs.append((None, j - 1))
            j -= 1
    return pairs[::-1]


def token_count(text):
    """모델이 신뢰도에 곱한 토큰 수(율명·위치·시김새 토큰). 끝 토큰은 곱하지 않는다."""
    return sum(1 + word.count('_') + (':' in word) for word in (text or '').split()) or 1


def labelled(song):
    """곡 결과의 칸마다 정답과 비교한 판정(wrong)을 붙이고, 빠진 정간 수를 돌려준다."""
    omr = [omr_rows(c['jg']) for c in song['cells']]
    truth = [pitch_rows(t) for t in song['truth']]
    pairs = align([o if o is not None else ['?'] for o in omr], truth)
    rows, missing = [], 0
    for i, j in pairs:
        if i is None:
            missing += 1
            continue
        cell = song['cells'][i]
        rows.append(dict(cell, answer=song['answer'], instrument=song['instrument'],
                         truth=None if j is None else song['truth'][j],
                         extra=j is None, wrong=j is None or omr[i] is None or omr[i] != truth[j],
                         ntok=token_count(cell['text'])))
    return rows, missing


def rule_current(c):
    """collect 때의 코드가 실제로 낸 issue."""
    return bool(c['issues'])


def rule_applied(c):
    """지금 코드의 판정(jeongganbo_scan.review_reason)을 모은 칸에 다시 적용한다. 모델을 다시 돌리지 않는다."""
    from omr_service.jeongganbo_scan import review_reason
    return any(i['kind'] == 'UNREADABLE' for i in c['issues']) or review_reason(c, c['jg'] or '') is not None


def by_threshold(t, geometric=False, pairs=()):
    def rule(c):
        if any(i['kind'] == 'UNREADABLE' for i in c['issues']) or c.get('filled') or c['conf'] is None:
            return True
        conf = c['conf'] ** (1 / c['ntok']) if geometric else c['conf']
        if conf < t:
            return True
        return bool(pairs) and octave_ambiguous(c, pairs)
    return rule


def octave_ambiguous(c, names=('황', '태')):
    """모델이 옥타브(청↔중청)를 자주 못 가리는 율명(names: 황·태)이 있고, 악기 율명 후보에 그 짝도 있는 칸."""
    import re
    from omr_service.jeongganbo_scan import YUL_PRESETS
    allowed = set(PRESET_YUL.get(YUL_PRESETS.get(c['instrument']), '').split())
    yuls = re.findall(rf"(?:중청|청)[{''.join(names)}]", c['jg'] or '')
    return any(y in allowed and OCTAVE_PAIR[y] in allowed for y in yuls)


# 리더 YUL_PRESETS와 같은 목록(리더는 import하면 모델을 읽으므로 글자로 둔다).
PRESET_YUL = {
    'danso': '중 임 무 청황 청태 청중 청임 청무 중청황 중청태',
    'sogeum': '임 남 청황 청태 청고 청중 청임 청남 청무 중청황 중청태 중청고',
    'jeongak-daegeum': '배임 배남 배무 황 태 고 중 임 남 무 이 청황 청태 청협 청고 청중 청임 청남 청무 중청황',
}


def tally(cells, rule):
    s = Counter(cells=len(cells))
    for c in cells:
        flagged = rule(c)
        s['wrong'] += c['wrong']
        s['extra'] += c['extra']
        s['flagged'] += flagged
        s['caught'] += c['wrong'] and flagged
        s['missed'] += c['wrong'] and not flagged
        s['missed_extra'] += c['extra'] and not flagged
        s['false_alarm'] += flagged and not c['wrong']
    return s


def score(args):
    songs = [json.loads(p.read_text(encoding='utf-8')) for p in sorted(Path(args.folder).glob('*.json'))]
    groups = {}
    for song in songs:
        rows, missing = labelled(song)
        group = song['answer'].split('-2016-')[0].split('-2022-')[0]
        groups.setdefault(group, dict(cells=[], missing=0, songs=0))
        groups[group]['cells'] += rows
        groups[group]['missing'] += missing
        groups[group]['songs'] += 1
    report = {}
    for name, g in groups.items():
        cells = g['cells']
        bins = Counter(), Counter()
        for c in cells:
            b = 'None' if c['conf'] is None else f"{min(int(c['conf'] * 10), 9) / 10:.1f}"
            bins[0][b] += 1
            bins[1][b] += not c['wrong']
        rules = {'collect 때 코드의 issue': rule_current, '지금 코드(review_reason)': rule_applied}
        for t in args.thresholds:
            rules[f'곱 < {t}'] = by_threshold(t)
            rules[f'곱 < {t} + 청황·중청황'] = by_threshold(t, pairs=('황',))
            rules[f'곱 < {t} + 청황·청태 짝'] = by_threshold(t, pairs=('황', '태'))
        for t in args.geometric:
            rules[f'기하평균 < {t}'] = by_threshold(t, geometric=True)
        report[name] = dict(songs=g['songs'], missing=g['missing'],
                            bins={b: dict(cells=bins[0][b], correct=bins[1][b]) for b in sorted(bins[0])},
                            rules={k: dict(tally(cells, r)) for k, r in rules.items()},
                            confusion=Counter(f"{c['jg']} → {c['truth']}" for c in cells if c['wrong']).most_common(15))
    text = json.dumps(report, ensure_ascii=False, indent=1)
    if args.json:
        Path(args.json).write_text(text, encoding='utf-8')
    for name, r in report.items():
        print(f"\n## {name}: 곡 {r['songs']}, 빠진 정간 {r['missing']}")
        print('| 신뢰도 | 칸 | 정답률 |\n| --- | ---: | ---: |')
        for b, v in r['bins'].items():
            print(f"| {b} | {v['cells']} | {100 * v['correct'] / v['cells']:.0f}% |")
        print('| 규칙 | 칸 | 틀린 칸(정답 없는 칸) | 확인 필요 칸 | 놓친 오류(정답 없는 칸) | 헛표시 |\n'
              '| --- | ---: | ---: | ---: | ---: | ---: |')
        for k, v in r['rules'].items():
            print(f"| {k} | {v['cells']} | {v.get('wrong', 0)} ({v.get('extra', 0)}) | {v.get('flagged', 0)} | "
                  f"{v.get('missed', 0)} ({v.get('missed_extra', 0)}) | {v.get('false_alarm', 0)} |")


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    sub = ap.add_subparsers(dest='cmd', required=True)
    c = sub.add_parser('collect')
    c.add_argument('--answers', default=str(ROOT / 'data' / 'annotations' / 'scores'))
    c.add_argument('--sources', nargs='+', required=True, help='원본 PDF를 찾을 폴더들(정답의 sha256으로 고른다)')
    c.add_argument('--model', required=True)
    c.add_argument('--out', required=True)
    c.add_argument('--only', nargs='*')
    c.add_argument('--shard', default='0/1', help='k/n: 정답 목록의 n개 중 k번째 몫만 읽는다(프로세스를 나눠 돌릴 때)')
    c.add_argument('--force', action='store_true')
    c.add_argument('--page-time', type=float, default=180, help='쪽당 시간 예산(초). 서비스는 60')
    s = sub.add_parser('score')
    s.add_argument('folder')
    s.add_argument('--json')
    s.add_argument('--thresholds', type=float, nargs='*', default=[.2, .3, .4, .5, .6, .7, .8])
    s.add_argument('--geometric', type=float, nargs='*', default=[.7, .8, .85, .9, .95])
    args = ap.parse_args(argv)
    collect(args) if args.cmd == 'collect' else score(args)


if __name__ == '__main__':
    main()
