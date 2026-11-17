"""정간보 스캔의 격자(각·정간) 인식을 판형별 표본으로 잰다 (S15P31E102-1077).

표본 목록은 jeongganbo_grid_samples.json이다. 표본마다 정답(저장소 정답 jgb 또는 손으로 적은 선율), 원본 파일의
sha256, 그림 해상도, 한 각의 정간 수, 조정용(tune)·확인용(holdout)을 적는다. 원본 PDF·그림은 Git 밖 폴더에서
sha256으로 찾는다. 조정용으로만 규칙을 맞추고 확인용은 결과 확인에만 쓴다.

  run    실제 모델로 omr_service.jeongganbo_scan.recognize를 돌려 표본별 칸(cellId·인식 결과·issue)을 JSON으로 남긴다.
  score  저장한 JSON을 정답과 비교해 각 수·각 경계·주소·내용 일치를 표로 낸다. 모델을 다시 돌리지 않는다.
  grid   모델 없이 격자 단계(data/src/scores/grid_boxes.find_grid)만 돌려 찾은 칸 수·열별 칸 수를 정답 정간 수와 비교한다.

실행 (스캔 런타임 Python, 저장소 루트에서):
  python -X utf8 ai/evaluation/jeongganbo_grid_eval.py run --sources <원본 폴더>... --model <jeongganbo-omr 폴더> --out <폴더>
  python -X utf8 ai/evaluation/jeongganbo_grid_eval.py score <폴더> [--json <결과.json>]
  python -X utf8 ai/evaluation/jeongganbo_grid_eval.py grid --sources <원본 폴더>...

지표
  content          정답과 칸 모양·율명이 같은 칸 수(순서 정렬로 짝지음, 시김새·숨표는 보지 않음)
  address          내용이 같은 칸 중 cellId(각·정간 번호)가 정답 주소와 같은 칸 수
  boundaryRecall   정답 각 시작 정간 중, 인식에서도 각의 첫 칸으로 읽힌 비율
  extraBoundaries  정답에는 없는데 인식에서 각이 시작된 자리 수
  flagged          확인 필요 issue가 붙은 칸 수
"""
import argparse
import hashlib
import json
import re
import sys
import tempfile
import types
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import jeongganbo_scan_review as review  # noqa: E402  정답 문법·정렬·실제 인식 가로채기를 그대로 쓴다

ROOT = review.ROOT
SAMPLES = Path(__file__).with_name('jeongganbo_grid_samples.json')
CELL_ID = re.compile(r'c(\d+)-j(\d+)$')


# ---------- 지표 ----------

def geometry(cells, truth, gak_size):
    """인식 칸 목록(cellId·jg·issues)과 정답 정간 목록, 정답 각의 정간 수로 격자 지표를 낸다."""
    omr = [review.omr_rows(c['jg']) for c in cells]
    want = [review.pitch_rows(t) for t in truth]
    pairs = [(r, t) for r, t in review.align(omr, want) if r is not None and t is not None]
    same = [(r, t) for r, t in pairs if omr[r] == want[t]]
    places = [tuple(map(int, CELL_ID.search(c['cellId']).groups())) for c in cells]
    address = sum(1 for r, t in same if places[r] == divmod(t, gak_size))
    starts = {r for r, place in enumerate(places) if r == 0 or place[0] != places[r - 1][0]}
    truth_starts = set(range(0, len(truth), gak_size))
    found = {t for r, t in pairs if r in starts}
    return dict(cells=len(cells), truth=len(truth), content=len(same), address=address,
                gaks=len({p[0] for p in places}), truthGaks=len(truth_starts),
                boundaryRecall=round(len(found & truth_starts) / len(truth_starts), 3) if cells else 0.0,
                extraBoundaries=len(found - truth_starts) + sum(1 for r in starts if r not in dict(pairs)),
                flagged=sum(1 for c in cells if c.get('issues')), failed=not cells)


# ---------- 표본 ----------

def samples(path=SAMPLES):
    data = json.loads(Path(path).read_text(encoding='utf-8'))
    for s in data['samples']:
        if 'answer' in s:
            folder = ROOT / 'data' / 'annotations' / 'scores' / s['answer']
            s['truth'] = review.truth_cells(json.loads(next(folder.glob('*.jgb.json')).read_text(encoding='utf-8')))
            hashes = json.loads(next(folder.glob('*.review.json')).read_text(encoding='utf-8'))['sourceHashes']
            (s['sha256'],) = hashes.values()  # 정답 검토 기록의 원본 sha256
        else:
            s['truth'] = review.truth_cells({'melody': s['melody']})
    return data['samples']


def find_sources(folders, wanted):
    found = {}
    for path in (p for folder in folders for p in Path(folder).rglob('*') if p.suffix.lower() in ('.pdf', '.png', '.jpg')):
        sha = hashlib.sha256(path.read_bytes()).hexdigest()
        if sha in wanted:
            found[sha] = path
    return found


def page_images(source, dpi, work):
    """표본 그림. PDF는 dpi로 첫 쪽을 그리고(서비스 기본 300dpi), 그림 파일은 그대로 쓴다."""
    from PIL import Image
    if source.suffix.lower() != '.pdf':
        with Image.open(source) as image:
            return [review._Page(source, *image.size)]
    import pypdfium2 as pdfium
    with pdfium.PdfDocument(source) as doc:
        image = doc[0].render(scale=dpi / 72).to_pil()
    out = work / 'page-0.png'
    image.save(out)
    return [review._Page(out, *image.size)]


class _Captured(Exception):
    def __init__(self, lines, issues):
        self.lines, self.issues = lines, issues


def recognize_lines(pages, instrument, jangdan, page_time_s):
    """실제 recognize를 돌리고 공통 악보 변환 직전의 각별 줄과 issue를 가로챈다.

    줄 하나가 각 하나이고 ' | '로 나뉜 칸이 정간이다. 칸 주소(cellId)는 recognize와 같은 c{각}-j{정간}이다.
    """
    from omr_service import jeongganbo_scan as scan

    def capture(lines, request, jangdan_name, bpj_of, *rest, **kw):
        closure = dict(zip(bpj_of.__code__.co_freevars, (c.cell_contents for c in bpj_of.__closure__)))
        raise _Captured(lines, closure['issues'])

    request = types.SimpleNamespace(instrument=instrument, jangdan=jangdan)
    job = types.SimpleNamespace(request=request, pages=pages, time_budget_s=page_time_s * len(pages))
    original = scan.lines_to_score
    scan.lines_to_score = capture
    try:
        with review._api(page_time_s) as api:
            try:
                scan.recognize(job)
                raise RuntimeError('lines_to_score was not reached')
            except _Captured as got:
                lines, issues = got.lines, got.issues
            except api.RecognitionFailed:
                lines, issues = [], []
    finally:
        scan.lines_to_score = original
    reasons = {}
    for i in issues:
        if i.get('cellId'):
            reasons.setdefault(i['cellId'], []).append(dict(kind=i['kind'], reason=(i.get('detail') or {}).get('reason')))
    cells = [dict(cellId=f'c{g}-j{j}', jg=text, issues=reasons.get(f'c{g}-j{j}', []))
             for g, line in enumerate(lines) for j, text in enumerate(line.split(' | '))]
    notices = [dict(kind=i['kind'], reason=(i.get('detail') or {}).get('reason'), page=i.get('page'))
               for i in issues if i.get('cellId') is None]
    return dict(engine=scan.ENGINE_VERSION, cells=cells, notices=notices)


def run(args):
    import os
    from time import monotonic
    os.environ['JEONGGANBO_OMR_DIR'] = str(Path(args.model).resolve())
    items = samples(args.samples)
    sources = find_sources(args.sources, {s['sha256'] for s in items})
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    for s in items:
        if args.only and s['id'] not in args.only:
            continue
        target = out / f"{s['id']}.json"
        if target.exists() and not args.force:
            continue
        if s['sha256'] not in sources:
            print(f"{s['id']}: 원본 없음", flush=True)
            continue
        began = monotonic()
        with tempfile.TemporaryDirectory(prefix='grid-eval-') as work:
            pages = page_images(sources[s['sha256']], s.get('dpi', 300), Path(work))
            try:
                result = recognize_lines(pages, s['instrument'], s['jangdan'], args.page_time)
            except TimeoutError:
                print(f"{s['id']}: 시간 초과({args.page_time}초/쪽)", flush=True)
                continue
        result.update(id=s['id'], split=s['split'], gakSize=s['gakSize'], truth=s['truth'],
                      seconds=round(monotonic() - began, 1))
        target.write_text(json.dumps(result, ensure_ascii=False, indent=1), encoding='utf-8')
        print(f"{s['id']}: 칸 {len(result['cells'])} / 정답 {len(s['truth'])}, {result['seconds']}초", flush=True)


def score(args):
    rows = []
    for path in sorted(Path(args.folder).glob('*.json')):
        song = json.loads(path.read_text(encoding='utf-8'))
        rows.append(dict(id=song['id'], split=song['split'], engine=song.get('engine'), seconds=song.get('seconds'),
                         notices=len(song.get('notices') or []),
                         **geometry(song['cells'], song['truth'], song['gakSize'])))
    print('| 표본 | 나눔 | 칸/정답 | 내용 | 주소 | 각/정답 | 각 경계 | 남는 경계 | 확인 칸 | 알림 | 초 |')
    print('| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |')
    for r in sorted(rows, key=lambda r: (r['split'], r['id'])):
        print(f"| {r['id']} | {r['split']} | {r['cells']}/{r['truth']} | {r['content']} | {r['address']} | "
              f"{r['gaks']}/{r['truthGaks']} | {r['boundaryRecall']:.0%} | {r['extraBoundaries']} | {r['flagged']} | "
              f"{r['notices']} | {r['seconds']} |")
    if args.json:
        Path(args.json).write_text(json.dumps(rows, ensure_ascii=False, indent=1), encoding='utf-8')


def grid(args):
    """격자 단계만 잰다. 번갈음은 가사 열에도 가로선이 있는 판형을 율명·가사 번갈음으로 읽는 경우, 엄격은 읽지 않는 경우다.
    각은 엄격 결과를 grid_boxes.gaks로 나누고(장단 대신 표본의 gakSize만 받는다), 칸 수가 정답과 같을 때
    정답 각 시작(gakSize마다) 중 인식에서도 각이 시작된 수를 센다.
    """
    import numpy as np
    from PIL import Image
    sys.path.insert(0, str(ROOT / 'data' / 'src' / 'scores'))
    from grid_boxes import find_grid, gaks
    items = samples(args.samples)
    sources = find_sources(args.sources, {s['sha256'] for s in items})
    print('| 표본 | 나눔 | 정답 | 칸(번갈음) | 칸(엄격) | 덩어리 | 열별 칸 | 각/정답 | 맞은 각 시작 | 어긋남 표시 | 각 근거 |')
    print('| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |')
    for s in sorted(items, key=lambda s: (s['split'], s['id'])):
        if args.only and s['id'] not in args.only or s['sha256'] not in sources:
            continue
        with tempfile.TemporaryDirectory(prefix='grid-eval-') as work:
            (page,) = page_images(sources[s['sha256']], s.get('dpi', 300), Path(work))
            with Image.open(page.path) as image:
                gray = np.asarray(image.convert('L'))
        loose, strict = find_grid(gray), find_grid(gray, allow_alternating_lyrics=False)
        count = lambda blocks: len(blocks[0][0]) if len(blocks) == 1 else 0  # 서비스는 덩어리 하나일 때만 읽는다
        columns = {}
        for c in (loose[0][0] if len(loose) == 1 else []):
            columns[c['band'], c['col']] = columns.get((c['band'], c['col']), 0) + 1
        found = gaks(strict[0][0], lambda n: n == s['gakSize']) if count(strict) else []
        n, size = len(s['truth']), s['gakSize']
        starts, at = set(), 0
        for g in found:
            starts.add(at)
            at += len(g['cells'])
        truth_starts = set(range(0, n, size))
        hit = len(starts & truth_starts) if count(strict) == n else 0
        print(f"| {s['id']} | {s['split']} | {n} | {count(loose)} | {count(strict)} | {len(loose)} | "
              f"{'/'.join(map(str, columns.values()))} | {len(found)}/{len(truth_starts)} | {hit}/{len(truth_starts)} | "
              f"{sum(g['mismatch'] for g in found)} | {','.join(sorted({g['source'] for g in found}))} |", flush=True)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    sub = ap.add_subparsers(dest='cmd', required=True)
    r = sub.add_parser('run')
    r.add_argument('--samples', default=str(SAMPLES))
    r.add_argument('--sources', nargs='+', required=True)
    r.add_argument('--model', required=True)
    r.add_argument('--out', required=True)
    r.add_argument('--only', nargs='*')
    r.add_argument('--force', action='store_true')
    r.add_argument('--page-time', type=float, default=180, help='쪽당 시간 예산(초). 서비스는 60')
    s = sub.add_parser('score')
    s.add_argument('folder')
    s.add_argument('--json')
    g = sub.add_parser('grid')
    g.add_argument('--samples', default=str(SAMPLES))
    g.add_argument('--sources', nargs='+', required=True)
    g.add_argument('--only', nargs='*')
    args = ap.parse_args(argv)
    dict(run=run, score=score, grid=grid)[args.cmd](args)


if __name__ == '__main__':
    main()
