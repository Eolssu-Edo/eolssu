"""정간보 쪽 그림에서 선만 보고 정간 상자를 찾는다. 율명은 읽지 않는다.

review_page.py가 상자 없는 .jgb.json에 원본 칸 위치를 붙일 때 쓴다. 판형이 달라도 공통인 규칙만 쓴다.
  * 세로선(그림 기준 60px 이상)으로 열을 나눈다. 세로선 길이가 같은 묶음이 한 단(악보를 가로로 나눈 덩어리)이다.
  * 정간 열은 안에 가로선(실선 또는 열 양끝까지 닿는 점선)이 있고, 오른쪽에 가로선 없는 가사 열이 붙은 열이다.
    가사 열 바로 왼쪽에 정간 열이 여럿 이어지면 성부별 열이다(가사 쪽부터 1성부).
    가사 열에도 가로선이 있는 판형(학교 교과서)은 율명·가사 열이 번갈아 있어 먹이 많은 쪽을 율명 열로 본다.
  * 칸 크기가 중간값에서 크게 벗어나는 열(장단 열, 머리 그림 무늬)은 뺀다.
  * 읽는 순서는 단 위→아래, 단 안에서 열 오른쪽→왼쪽, 열 안에서 위→아래다.
    한 단에 악보가 나란하면 덩어리를 나눠 돌려주고, 어느 것을 쓸지는 부르는 쪽이 고른다.
"""
import numpy as np

DARK = 215  # 이보다 어두우면 선·글자로 본다. 표준악보집의 옅은 회색 선까지 잡는 값


def vlines(dark, minlen=60):
    """세로선 (x, y0, y1). 이웃한 x에서 y가 겹치는 조각은 한 선으로 합친다."""
    pad = np.zeros((1, dark.shape[1]), bool)
    d = np.diff(np.vstack([pad, dark, pad]).astype(np.int8), axis=0)
    segs = []
    for x in np.flatnonzero((d == 1).any(axis=0)):
        starts, ends = np.flatnonzero(d[:, x] == 1), np.flatnonzero(d[:, x] == -1)
        segs += [(x, a, b) for a, b in zip(starts, ends) if b - a >= minlen]
    lines = []
    for x, a, b in sorted(segs):
        for line in lines:
            if x - line[1] <= 2 and a < line[3] and b > line[2]:
                line[1:] = [x, min(line[2], a), max(line[3], b)]
                break
        else:
            lines.append([x, x, a, b])
    return [((x0 + x1) / 2, a, b) for x0, x1, a, b in lines]


def separators(dark, x0, x1, y0, y1):
    """열 안의 가로선 y. 실선은 폭 85% 이상, 점선은 15% 이상이면서 끊긴 조각이 4개 이상이고 양끝에 닿는다."""
    sub = dark[int(y0):int(y1), int(x0) + 3:int(x1) - 2]
    if sub.shape[1] < 8:
        return []
    edge = max(2, sub.shape[1] // 7)
    cov = sub.mean(axis=1)
    pieces = (np.diff(sub.astype(np.int8), axis=1) == 1).sum(axis=1)
    hit = (cov >= 0.85) | ((cov >= 0.15) & (pieces >= 4) & sub[:, :edge].any(axis=1) & sub[:, -edge:].any(axis=1))
    groups = []
    for y in np.flatnonzero(hit):
        if groups and y - groups[-1][-1] <= 3:
            groups[-1].append(y)
        else:
            groups.append([y])
    ys = [(g[0] + g[-1]) / 2 for g in groups]
    return [int(y0) + y for y in ys if 4 < y < sub.shape[0] - 4]  # 단의 위·아래 테두리는 빼고 안쪽 선만


def rows(seps, top, bottom):
    """가로선으로 나눈 칸 (위, 아래). 중간값의 절반보다 얇은 틈(이중선 사이 등)은 버린다."""
    edges = sorted({top, bottom, *seps})
    spans = list(zip(edges, edges[1:]))
    typical = sorted(b - a for a, b in spans)[len(spans) // 2]
    return [(a, b) for a, b in spans if b - a > typical * 0.5]


def find_blocks(gray, parts=1, *, allow_alternating_lyrics=True):
    """쪽 그림(흑백 numpy 배열) → 악보 덩어리 목록. 덩어리는 성부별 상자 목록이고 상자는 (x, y, w, h)다.
    한 단에 악보가 나란히 여럿(예: 소금보·단소보)이면 왼쪽부터 덩어리가 나뉜다. 여러 단은 한 덩어리로 잇는다."""
    dark = gray < DARK
    lines = vlines(dark)
    bands = []
    for _, a, b in sorted(lines, key=lambda l: (l[1], l[2])):
        if not any(abs(a - p) <= 12 and abs(b - q) <= 12 for p, q in bands):
            bands.append((a, b))
    # 단마다 그 단 전체를 덮는 세로선 사이가 열이다. 같은 열이 여러 단에서 잡히면 가장 긴 단을 쓴다.
    cols = {}
    for a, b in bands:
        xs = sorted(x for x, y0, y1 in lines if y0 <= a + 12 and y1 >= b - 12)
        for x0, x1 in zip(xs, xs[1:]):
            if x1 - x0 <= 8:
                continue
            same = [k for k in cols if abs(k[0] - x0) < 3 and abs(k[1] - x1) < 3 and k[2] < b and k[3] > a]
            if all(k[3] - k[2] < b - a for k in same):
                for k in same:
                    del cols[k]
                cols[(x0, x1, a, b)] = separators(dark, x0, x1, a, b)
    # 단별로 열을 오른쪽부터 보며 가로선 있는 열이 이어진 묶음(run)을 모은다.
    #   run 길이 = parts: 가사 열에 가로선이 없는 판형. run이 한 각 묶음이고 오른쪽 열부터 1성부다.
    #   run 길이가 짝수(성부 하나): 가사 열에도 가로선이 있어 율명·가사 열이 번갈아 있다. 먹이 많은 쪽(한자)이 율명 열이다.
    ink = lambda run: np.mean([(gray[int(a):int(b), int(c[0]) + 3:int(c[1]) - 2] < 160).mean() for c in run])
    by_band = {}
    for (x0, x1, a, b), seps in cols.items():
        by_band.setdefault((a, b), []).append((x0, x1, seps))
    per_band = []  # (단 위, [악보 덩어리]), 덩어리 = [(묶음 x, [성부별 칸 상자 목록])] 오른쪽부터
    for (a, b), band_cols in by_band.items():
        band_cols.sort(key=lambda c: -c[0])
        runs, prev = [], None  # (run 바로 오른쪽 열, run)
        for c in band_cols:
            if c[2] and prev and prev[2] and abs(prev[0] - c[1]) < 3:
                runs[-1][1].append(c)
            elif c[2]:
                runs.append((prev, [c]))
            prev = c
        groups = []
        for right, run in runs:
            # 가사 열은 율명 열보다 넓지 않다. 오른쪽이 넓은 제목 칸인 열(장단 열)은 빼낸다.
            lyric = right and abs(right[0] - run[0][1]) < 3 and right[1] - right[0] <= (run[0][1] - run[0][0]) * 1.05
            if len(run) == parts and lyric:
                chains = [run]
            elif allow_alternating_lyrics and parts == 1 and len(run) % 2 == 0:
                chains = [[c] for c in max(run[0::2], run[1::2], key=ink)]
            else:
                continue
            groups += [(ch[0][0], [[(c[0], t, c[1] - c[0], u - t) for t, u in rows(c[2], a, b)] for c in ch]) for ch in chains]
        # 한 단에서 묶음 간격이 평소의 1.3배를 넘으면 다른 악보로 나눈다.
        if groups:
            steps = [p[0] - q[0] for p, q in zip(groups, groups[1:])]
            step = sorted(steps)[len(steps) // 2] if steps else 0
            per_band.append((a, [[groups[0]]]))
            for p, q in zip(groups, groups[1:]):
                if p[0] - q[0] > step * 1.3:
                    per_band[-1][1].append([])
                per_band[-1][1][-1].append(q)
    per_band.sort()
    # 정간 크기는 악보 안에서 거의 같다. 중간값과 높이가 2배 넘게, 너비가 1.5배 넘게 다른 칸이 있는 묶음
    # (장단 열, 단 사이 빈 줄까지 덮는 바깥 테두리, 머리 그림의 작은 무늬 등)은 정간 열이 아니다.
    boxes = [box for _, bl in per_band for blk in bl for g in blk for p in g[1] for box in p]
    if boxes:
        med = lambda v: sorted(v)[len(v) // 2]
        h, w = med([b[3] for b in boxes]), med([b[2] for b in boxes])
        fits = lambda g: all(h / 2 <= b[3] <= h * 2 and w / 1.5 <= b[2] <= w * 1.5 for p in g[1] for b in p)
        per_band = [(a, [blk for blk in ([g for g in blk if fits(g)] for blk in bl) if blk]) for a, bl in per_band]
        per_band = [(a, bl) for a, bl in per_band if bl]
    # 단마다 악보가 하나이고 단끼리 위아래로 겹치지 않으면 여러 단을 한 악보로 잇는다.
    # 아니면(나란한 악보) 덩어리를 왼쪽부터 늘어놓는다.
    spans = [(a, max(box[1] + box[3] for g in blk for p in g[1] for box in p)) for a, (blk, *_) in per_band]
    if all(len(blocks) == 1 for _, blocks in per_band) and all(p[1] <= q[0] + 4 for p, q in zip(spans, spans[1:])):
        blocks = [[g for _, (blk,) in per_band for g in blk]]
    else:
        blocks = sorted((blk for _, bl in per_band for blk in bl), key=lambda blk: blk[-1][0])
    return [[[box for g in blk for box in g[1][k]] for k in range(parts)] for blk in blocks]
