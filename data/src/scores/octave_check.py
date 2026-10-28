"""OMR이 청성(청태·청황)과 중청(중청태·중청황)을 헷갈린 자리를 글자 모양으로 다시 가린다.

중청 글자(㳲·㶂)는 청성 글자(汰·潢) 왼쪽에 물수변 점 줄이 하나 더 있다. 점은 이 판형에서 몸통과 붙어
점 줄 수를 직접 세기 어렵다. 그래서 글자 먹을 24×24로 맞춰 줄이고, 정답에서 모은 청성·중청 글자의
평균 모양 중 더 가까운 쪽으로 정한다. 중청태는 모델 어휘에 없어 모델이 낼 수 없으므로 이 방법으로만 나온다.

평균 모양 만들기:
  python data/src/scores/octave_check.py <정답 폴더> <결과 폴더> [...] --out <평균 모양.json>
  정답 폴더   검토 화면의 <제목>.review.json과 <제목>.jgb.json이 있는 폴더
  결과 폴더   jeongganbo_omr.py 출력 폴더(cells.json·page.png·page-read.png). page-read.png는 300dpi로 그린 쪽이다.
"""
import argparse
import json
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
