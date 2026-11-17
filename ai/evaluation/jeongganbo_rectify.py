"""1059 실험: 평평한 정간보 쪽을 회전·원근·그늘로 변형한 사진에서 보정 전후의 격자 검출과 율명을 실제 리더로 비교한다.

보정 전은 기존 경로(원래 그림의 크기 재시도), 보정 후는 jeongganbo_scan과 같은 순서(편 그림 → 실패하면 원래 그림)다.
기준은 `arirang`(docs/knowledge/data/jeongganbo-omr.md의 단소 아리랑 48정간 수기 정답), `cells`(`--answer`로 준
cells.json의 정간별 `expected`, 예: data/tests/fixtures/scores/double-water/cells.json 24칸) 또는 `flat`(평평한 원본을
같은 리더로 읽은 결과)이다. `flat` 일치율은 원본 결과를 얼마나 되찾았는지일 뿐 정확도가 아니다.
결과는 조건마다 JSON 한 줄이다. 칸 일치는 정간별 율명 목록(이음·쉼표·위치·시김새 제외)의 순서 대응 개수다.

  uv run python -m evaluation.jeongganbo_rectify --page <쪽.png> --out <폴더> --instrument DANSO --reference arirang \
      --python <MALerLab 가상 환경 python> --omr-dir <jeongganbo-omr> -- 3:0:0 0:0.12:0 -5:0.12:1
조건은 `회전°:원근:그늘`이다. 원근은 위 변을 그 비율만큼 줄이고, 그늘 1은 왼쪽 위가 어두운 밝기 기울기를 준다.
"""
import argparse
import difflib
import json
import os
import re
import tempfile
import time
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

from omr_service import api, jeongganbo_scan as scan, page_rectify

NAMES = re.compile(r'중청황|중청태|청황|청태|청협|청고|청중|청임|청남|청무|배임|배남|배무|황|태|협|고|중|임|남|무|이')
# 오른쪽 열부터, 각은 '/', 정간은 '|'. '(빈칸)'·'쉼표'는 율명이 없는 정간이다.
ARIRANG = """중 | -임 | 중임 / 무 | -청황 | 무청황 / 청태 | 청황청태 | 무임 / 중 | -임 | 중임
무 | -청황 | 무청황 / 청태청황 | 무임 | 중임 / 무 | -청황 | 무 / 무 | (빈칸) | 쉼표
청중 | (빈칸) | 청중 / 청중 | 청태 | 청황 / 청태 | 청황청태 | 무임 / 중 | -임 | 중임
무 | -청황 | 무청황 / 청태청황 | 무임 | 중임 / 무 | -청황 | 무 / 무 | (빈칸) | 쉼표"""


def answer_cells():
    return [tuple(NAMES.findall(c)) for line in ARIRANG.splitlines() for gak in line.split('/') for c in gak.split('|')]


def pitches(cell):
    names = (token.rsplit(':', 1)[0].split('_')[0] for token in cell.get('text', '').split())
    return tuple(name for name in names if name not in ('-', '쉼표', '쉼'))


def matched(got, want):
    return sum(b.size for b in difflib.SequenceMatcher(None, got, want, autojunk=False).get_matching_blocks())


def photo(base, rot, persp, shade):
    """여백을 붙인 쪽을 회전·원근 변형하고 그늘·잡음·흐림을 준다. 바깥은 책상 같은 어두운 바탕이다."""
    pad = int(max(base.size) * .08)
    flat = Image.new('RGB', (base.width + 2 * pad, base.height + 2 * pad), 'white')
    flat.paste(base, (pad, pad))
    w, h = flat.size
    corners = np.array([[0, 0], [w, 0], [w, h], [0, h]], float)
    moved = corners + np.array([[persp * w / 2, 0], [-persp * w / 2, 0], [0, 0], [0, 0]])
    a = np.radians(rot)
    moved = (moved - [w / 2, h / 2]) @ np.array([[np.cos(a), -np.sin(a)], [np.sin(a), np.cos(a)]]).T + [w / 2, h / 2]
    m = page_rectify._homography(moved, corners)
    image = flat.transform((w, h), Image.Transform.PERSPECTIVE, tuple((m / m[2, 2]).ravel()[:8]),
                           Image.Resampling.BICUBIC, fillcolor=(120, 110, 100))
    pixels = np.asarray(image).astype(np.float32)
    if shade:
        pixels *= .62 + .25 * np.linspace(0, 1, w)[None, :, None] + .1 * np.linspace(0, 1, h)[:, None, None]
    pixels += np.random.default_rng(1).normal(0, 4, pixels.shape)
    return Image.fromarray(np.clip(pixels, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(.6))


def read(sources, work, omr_dir, yul):
    """sources를 차례로 읽어 처음 격자를 찾은 결과. (cells.json, 걸린 초, 쓴 순번, 오류 종류)"""
    total, error = 0., None
    for index, source in enumerate(sources):
        began = time.perf_counter()
        try:
            report = scan._read_page(api, 0, source, work, omr_dir, yul, time.monotonic() + 900)[0]
        except Exception as e:  # noqa: BLE001 - 실험 기록에 종류만 남긴다
            report, error = None, type(e).__name__
        total += time.perf_counter() - began
        if report and report['cells']:
            return report, total, index, error
    return None, total, None, error


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--page', type=Path, required=True, help='평평한 쪽 그림(PNG)')
    parser.add_argument('--out', type=Path, required=True, help='합성 사진·편 그림을 남길 폴더')
    parser.add_argument('--instrument', default='DANSO', help='리더 율명 제한에 쓸 악기 코드(1058)')
    parser.add_argument('--reference', choices=('arirang', 'cells', 'flat'), required=True)
    parser.add_argument('--answer', type=Path, help='--reference cells의 정답 cells.json(읽는 순서, 항목마다 expected)')
    parser.add_argument('--python', required=True, help='MALerLab 리더를 실행할 가상 환경의 python')
    parser.add_argument('--omr-dir', required=True, help='MALerLab/jeongganbo-omr 폴더(체크포인트 포함)')
    parser.add_argument('conditions', nargs='+')
    args = parser.parse_args()

    scan.READER = [args.python, '-X', 'utf8', str(scan.SCORES / 'jeongganbo_omr.py')]
    yul = ['--yul', scan.YUL_PRESETS[args.instrument]] if args.instrument in scan.YUL_PRESETS else []
    args.out.mkdir(parents=True, exist_ok=True)
    base = Image.open(args.page).convert('RGB')
    with tempfile.TemporaryDirectory() as folder:
        work = Path(folder)
        if args.reference == 'arirang':
            want = answer_cells()
        elif args.reference == 'cells':
            want = [pitches({'text': c['expected']}) for c in json.loads(args.answer.read_text(encoding='utf-8'))]
        else:
            report, seconds, _, error = read([page_rectify.Rectified(args.page, *base.size, base.size)], work,
                                             args.omr_dir, yul)
            want = [pitches(c) for c in report['cells']] if report else []
            print(json.dumps(dict(condition='flat', cells=len(want), seconds=round(seconds, 1), error=error)), flush=True)
        for condition in args.conditions:
            rot, persp, shade = condition.split(':')
            path = args.out / f'{args.page.stem}-{condition.replace(":", "_")}.jpg'
            photo(base, float(rot), float(persp), shade == '1').save(path, quality=85)
            size = Image.open(path).size
            plain = page_rectify.Rectified(path, *size, size)
            row = dict(condition=condition, reference=len(want))
            report, seconds, _, error = read([plain], work, args.omr_dir, yul)
            got = [pitches(c) for c in report['cells']] if report else []
            row['before'] = dict(cells=len(got), match=matched(got, want), seconds=round(seconds, 1), error=error)
            began = time.perf_counter()
            straight = page_rectify.rectify(path, args.out / f'{path.stem}-rectified.png')
            rectify_s = time.perf_counter() - began
            report, seconds, used, error = read([straight, plain] if straight.changed else [plain], work,
                                                args.omr_dir, yul)
            got = [pitches(c) for c in report['cells']] if report else []
            row['after'] = dict(cells=len(got), match=matched(got, want), seconds=round(seconds + rectify_s, 1),
                                rectify_s=round(rectify_s, 2), changed=straight.changed, angle=round(straight.angle, 2),
                                flattened=straight.flattened, rectified_read=straight.changed and used == 0, error=error)
            print(json.dumps(row, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    os.environ.setdefault('PYTHONIOENCODING', 'utf-8')
    main()
