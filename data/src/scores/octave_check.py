"""스캔 OMR의 청·중청을 실제 글자 그림으로 구별한다.

recover_water_register는 모델의 청 토큰에 두 물수변이 분명히 적힌 경우만 중청으로 복원한다.
별도 폰트·정답 템플릿 없이 서비스의 두 격자 경로에서 사용한다. 아래 CLI는 기존 교재용 평균 모양 도구다.

중청 글자(㳲·㶂)는 청성 글자(汰·潢) 왼쪽에 물수변 점 줄이 하나 더 있다. 점은 이 판형에서 몸통과 붙어
점 줄 수를 직접 세기 어렵다. 그래서 글자 먹을 24×24로 맞춰 줄이고, 정답에서 모은 청성·중청 글자의
평균 모양 중 더 가까운 쪽으로 정한다. 중청태는 모델 어휘에 없어 모델의 원래 추론만으로 나오지 않는다.

평균 모양 만들기:
  python data/src/scores/octave_check.py <정답 폴더> <결과 폴더> [...] --out <평균 모양.json>
  정답 폴더   검토 화면의 <제목>.review.json과 <제목>.jgb.json이 있는 폴더
  결과 폴더   jeongganbo_omr.py 출력 폴더(cells.json·page.png·page-read.png). page-read.png는 300dpi로 그린 쪽이다.
"""
import argparse
import json
import re
import sys
from pathlib import Path

import cv2
import numpy as np

FAMILY = {"청태": "청태", "중청태": "청태", "청황": "청황", "중청황": "청황"}
SIZE = 24


def slot_rect(pos):
    """정간 안 위치 번호를 정간 상자 비율(x0, y0, x1, y1)로 바꾼다. review_stats.jg_to_um의 slot과 같은 배치다."""
    if pos == 0:
        return 0, 0, 1, 1
    if pos <= 9:
        r, c = (pos - 1) // 3, (pos - 1) % 3
        x0, x1 = [(0, .5), (0, 1), (.5, 1)][c]
        return x0, r / 3, x1, (r + 1) / 3
    r, x0, x1 = {10: (0, 0, 1), 11: (1, 0, 1), 12: (0, 0, .5), 13: (0, .5, 1), 14: (1, 0, .5), 15: (1, .5, 1)}[pos]
    return x0, r / 2, x1, (r + 1) / 2


def pitch_slots(jg):
    """JG-like 정간에서 청성·중청 율명 토큰의 (토큰 번호, 율명, 위치 비율)."""
    toks, pos = jg.split(), None
    for i, t in enumerate(toks):
        if t.startswith(":"):
            pos = int(t[1:])
        elif t in FAMILY:
            yield i, t, slot_rect(pos)


def crop(img, box, rect, k):
    x, y, w, h = box
    x0, y0, x1, y1 = rect
    return img[round((y + h * y0) * k):round((y + h * y1) * k), round((x + w * x0) * k):round((x + w * x1) * k)]


def glyph(gray):
    """칸 선(가장자리에 닿은 먹)을 빼고 가장 큰 먹 덩어리와 그 왼쪽 점을 24×24로 맞춘다. 먹이 없으면 None."""
    _, b = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
    H, W = b.shape
    n, lab, st, _ = cv2.connectedComponentsWithStats(b, 8)
    keep = [i for i in range(1, n) if st[i][4] >= 6 and st[i][0] > 0 and st[i][1] > 0
            and st[i][0] + st[i][2] < W and st[i][1] + st[i][3] < H]
    if not keep:
        return None
    x, y, w, h = st[max(keep, key=lambda i: st[i][4])][:4]
    sub = np.isin(lab[y:y + h, max(0, x - h // 3):x + w], keep).astype(np.float32)
    cols = np.where(sub.any(0))[0]
    return cv2.resize(sub[:, cols[0]:cols[-1] + 1], (SIZE, SIZE), interpolation=cv2.INTER_AREA)


def classify(vec, templates, family):
    """'청' 또는 '중청'."""
    d = {o: np.abs(vec - np.array(templates[family][o])).sum() for o in ("청", "중청")}
    return min(d, key=d.get)


def _double_water(gray, *, dark_core=False):
    """Require two aligned dot pairs and the outer radical's lower stroke.

    The scanner cannot emit most double-register tokens. Two written water
    radicals can prove the missing register; note range and neighbouring notes
    cannot. Broken/merged strokes remain unchanged rather than being guessed.
    """
    if gray.size == 0 or min(gray.shape[:2]) < 12:
        return False
    if gray.ndim == 3:
        gray = cv2.cvtColor(gray, cv2.COLOR_BGR2GRAY)
    threshold, ink = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
    height, width = ink.shape
    _, _, stats, _ = cv2.connectedComponentsWithStats(ink, 8)
    # Border-connected ink is the grid, not a radical. Many isolated specks do
    # not provide a legible character and must not make pair matching expensive.
    parts = [s for s in stats[1:] if s[4] >= 2 and s[0] > 1 and s[1] > 1
             and s[0] + s[2] < width - 1 and s[1] + s[3] < height - 1]
    if not parts or len(parts) > 64:
        return False
    left, top = min(s[0] for s in parts), min(s[1] for s in parts)
    gw = max(s[0] + s[2] for s in parts) - left
    gh = max(s[1] + s[3] for s in parts) - top
    if gh < 12 or not .65 <= gw / gh <= 1.4:
        return False
    dots = [(x + w / 2, y + h / 2) for x, y, w, h, _ in parts
            if w <= .23 * gh and h <= .23 * gh and .4 <= w / h <= 2.1
            and x + w / 2 < left + .52 * gw
            and top + .05 * gh < y + h / 2 < top + .60 * gh]
    pairs = [(a, b) for a in dots for b in dots
             if .14 * gh <= b[1] - a[1] <= .40 * gh
             and -.05 * gh <= a[0] - b[0] <= .20 * gh]
    for outer_top, outer_mid in pairs:
        lower_stroke = any(.22 * gh <= h <= .60 * gh and w <= .35 * gh
                           and outer_mid[0] - .12 * gh <= x + w / 2 <= outer_mid[0] + .25 * gh
                           and outer_mid[1] < y + h / 2 and top + .45 * gh <= y <= top + .75 * gh
                           for x, y, w, h, _ in parts)
        if not lower_stroke:
            continue
        for inner_top, inner_mid in pairs:
            if (.12 * gh <= inner_top[0] - outer_top[0] <= .32 * gh
                    and .12 * gh <= inner_mid[0] - outer_mid[0] <= .32 * gh
                    and abs(inner_top[1] - outer_top[1]) <= .12 * gh
                    and abs(inner_mid[1] - outer_mid[1]) <= .12 * gh):
                return True
    # At small sizes JPEG grey bridges can join the two top dots. Require the
    # same complete radical geometry in the darker stroke cores, once only.
    if gh < 20 and not dark_core:
        core = cv2.threshold(gray, threshold * .8, 255, cv2.THRESH_BINARY)[1]
        return _double_water(core, dark_core=True)
    return False


def recover_water_register(text, gray):
    """Restore only an explicit double-water glyph; preserve token positions and ornaments."""
    tokens = text.split()
    changed = False
    for i, token in enumerate(tokens):
        match = re.fullmatch(r'(청[황대태협고중유임이남무응])(_[^:\s]+)?:(\d+)', token)
        if not match or int(match[3]) > 15:
            continue
        position = int(match[3])
        # A lone centre token is the whole cell, just as jg_like normalizes :5.
        rect = slot_rect(0 if len(tokens) == 1 and position == 5 else position)
        h, w = gray.shape[:2]
        x0, y0, x1, y1 = rect
        if _double_water(gray[round(h * y0):round(h * y1), round(w * x0):round(w * x1)]):
            tokens[i] = '중' + token
            changed = True
    return ' '.join(tokens) if changed else text


def fix_cells(cells, img, k, templates):
    """cells의 jg에서 청성·중청 율명을 글자 모양으로 다시 정한다. 바꾼 수를 돌려준다."""
    changed = 0
    for c in cells:
        toks = c["jg"].split()
        for i, name, rect in pitch_slots(c["jg"]):
            v = glyph(crop(img, (c["x"], c["y"], c["w"], c["h"]), rect, k))
            if v is None:
                continue
            fam = FAMILY[name]
            new = ("중" if classify(v, templates, fam) == "중청" else "") + fam
            changed += new != name
            toks[i] = new
        c["jg"] = " ".join(toks)
    return changed


if __name__ == "__main__":
    sys.path.insert(0, str(Path(__file__).parent))  # python -I로 실행해도 같은 폴더의 review_stats를 찾게
    from review_stats import parse, with_jgb

    ap = argparse.ArgumentParser()
    ap.add_argument("truth", type=Path)
    ap.add_argument("runs", type=Path, nargs="+")
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()
    truths = {}
    for f in args.truth.glob("*.review.json"):
        r = json.loads(f.read_text(encoding="utf-8"))
        truths[r["title"]] = with_jgb(r, f.with_name(f"{r['title']}.jgb.json"))[0]
    vecs = {(f, o): [] for f in ("청태", "청황") for o in ("청", "중청")}
    for run in args.runs:
        for cf in run.glob("*/cells.json"):
            omr = json.loads(cf.read_text(encoding="utf-8"))
            page, hi = (cv2.imread(str(cf.with_name(n)), cv2.IMREAD_GRAYSCALE) for n in ("page.png", "page-read.png"))
            k = hi.shape[1] / page.shape[1]
            boxes = {(c["x"], c["y"]): c for c in omr["cells"]}
            for t in truths[omr["title"]]["cells"]:
                c = boxes.get(tuple(t["box"][:2])) if t.get("box") else None
                if not c:
                    continue
                final = parse(t["cell"])
                for _, name, (x0, y0, x1, y1) in pitch_slots(c["jg"]):
                    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2  # 위치 가운데가 든 정답 음 단위를 찾는다
                    row = final[min(int(cy * len(final)), len(final) - 1)]
                    core = row[min(int(cx * len(row)), len(row) - 1)][0]
                    if FAMILY.get(core) != FAMILY[name]:
                        continue
                    v = glyph(crop(hi, (c["x"], c["y"], c["w"], c["h"]), (x0, y0, x1, y1), k))
                    if v is not None:
                        vecs[FAMILY[name], "중청" if core.startswith("중청") else "청"].append(v)
    templates = {f: {o: np.round(np.mean(vecs[f, o], 0), 3).tolist() for o in ("청", "중청")} for f in ("청태", "청황")}
    args.out.write_text(json.dumps(dict(size=SIZE, count={f"{f}/{o}": len(v) for (f, o), v in vecs.items()}, **templates)),
                        encoding="utf-8")
    print({f"{f}/{o}": len(v) for (f, o), v in vecs.items()}, "→", args.out)
