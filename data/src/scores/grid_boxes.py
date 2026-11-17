"""정간보 쪽 그림에서 선만 보고 정간 상자를 찾는다. 율명은 읽지 않는다.

review_page.py가 상자 없는 .jgb.json에 원본 칸 위치를 붙일 때 쓴다. 판형이 달라도 공통인 규칙만 쓴다.
  * 세로선(쪽에서 가장 긴 세로선의 1/8 이상)으로 열을 나눈다. 세로선 길이가 같은 묶음이 한 단(악보를 가로로 나눈 덩어리)이다.
  * 정간 열은 안에 가로선(실선 또는 열 양끝까지 닿는 점선)이 있고, 오른쪽에 가로선 없는 가사 열이 붙은 열이다.
    가사 열 바로 왼쪽에 정간 열이 여럿 이어지면 성부별 열이다(가사 쪽부터 1성부).
    가사 열에도 가로선이 있는 판형(학교 교과서)은 율명·가사 열이 번갈아 있어 먹이 많은 쪽을 율명 열로 본다.
  * 칸 크기가 중간값에서 크게 벗어나는 열(장단 열, 머리 그림 무늬)은 뺀다.
  * find_grid는 칸마다 (단, 열, 줄) 주소와 칸 위 가로선 굵기를 함께 돌려준다. 스캔 각 경계(S15P31E102-1077)가 쓴다.
  * 읽는 순서는 단 위→아래, 단 안에서 열 오른쪽→왼쪽, 열 안에서 위→아래다.
    한 단에 악보가 나란하면 덩어리를 나눠 돌려주고, 어느 것을 쓸지는 부르는 쪽이 고른다.
"""
import numpy as np

DARK = 215  # 이보다 어두우면 선·글자로 본다. 표준악보집의 옅은 회색 선까지 잡는 값


def vlines(dark):
    """세로선 (x, y0, y1, 굵기)와 쪽에서 가장 긴 세로 먹 길이.

    세로 먹 줄기 중 쪽에서 가장 긴 줄기의 1/8 이상인 것만 선의 일부다. 글자 획은 정간 하나보다 짧아 빠진다.
    바로 옆 x에 있고 y가 겹치거나 조금 끊긴 줄기끼리 한 선으로 묶는다. 옛 방식은 60px 이상 조각을 이어 붙여 선에 붙은
    글자 획을 타고 선이 옆으로 번졌다. 사진 밖 바탕 같은 넓은 먹 덩어리는 y가 겹치지 않는 선과 섞이지 않는다.
    """
    pad = np.zeros((1, dark.shape[1]), bool)
    d = np.diff(np.vstack([pad, dark, pad]).astype(np.int8), axis=0)
    runs = []
    for x in np.flatnonzero((d == 1).any(axis=0)):
        starts, ends = np.flatnonzero(d[:, x] == 1), np.flatnonzero(d[:, x] == -1)
        runs += [(x, a, b) for a, b in zip(starts, ends)]
    longest = max((b - a for _, a, b in runs), default=0)
    gap = longest / 100  # 인쇄·스캔으로 끊긴 선 조각을 잇는 틈
    open_ = []  # [첫 x, 끝 x, y0, y1]
    for x, a, b in sorted(r for r in runs if r[2] - r[1] >= longest / 8):
        for line in open_:
            if x - line[1] <= 1 and a < line[3] + gap and b > line[2] - gap:
                line[1:] = [x, min(line[2], a), max(line[3], b)]
                break
        else:
            open_.append([x, x, a, b])
    return [((x0 + x1) / 2, a, b, x1 - x0 + 1) for x0, x1, a, b in open_], longest


def separators(dark, x0, x1, y0, y1, margin=3, edge_gap=4):
    """열 안의 가로선 (y, 굵기). 실선은 폭 85% 이상을 덮는다. 점선은 15% 이상이면서 조각이 4개 이상이고
    양끝에 닿으며, 조각 사이 틈이 모두 폭의 10% 이하로 고르다. 글자 획은 틈이 들쭉날쭉해 점선이 되지 않는다.
    margin은 양쪽 세로선 굵기만큼 비우는 폭, edge_gap은 단의 위·아래 테두리로 보고 버리는 거리다."""
    sub = dark[int(y0):int(y1), int(x0) + margin:int(x1) - margin]
    if sub.shape[1] < 8:
        return []
    edge = max(2, sub.shape[1] // 7)
    cov = sub.mean(axis=1)
    d = np.diff(np.pad(sub.astype(np.int8), ((0, 0), (1, 1))), axis=1)
    hit = cov >= 0.85
    for y in np.flatnonzero((cov >= 0.15) & ~hit):
        on, off = np.flatnonzero(d[y] == 1), np.flatnonzero(d[y] == -1)
        hit[y] = (len(on) >= 4 and on[0] < edge and off[-1] > sub.shape[1] - edge
                  and (on[1:] - off[:-1]).max() <= sub.shape[1] * 0.1)
    groups = []
    for y in np.flatnonzero(hit):
        if groups and y - groups[-1][-1] <= 3:
            groups[-1].append(y)
        else:
            groups.append([y])
    return [(int(y0) + (g[0] + g[-1]) / 2, g[-1] - g[0] + 1) for g in groups
            if edge_gap < (g[0] + g[-1]) / 2 < sub.shape[0] - edge_gap]  # 단의 위·아래 테두리는 빼고 안쪽 선만


def rows(seps, top, bottom):
    """가로선으로 나눈 칸 (위, 아래). 중간값의 절반보다 얇은 틈(이중선 사이 등)은 버린다."""
    edges = sorted({top, bottom, *seps})
    spans = list(zip(edges, edges[1:]))
    typical = sorted(b - a for a, b in spans)[len(spans) // 2]
    return [(a, b) for a, b in spans if b - a > typical * 0.5]


def find_grid(gray, parts=1, *, allow_alternating_lyrics=True):
    """쪽 그림(흑백 numpy 배열) → 악보 덩어리 목록. 덩어리는 성부별 칸 목록이다 (S15P31E102-1077).

    칸은 dict(band, col, row, x, y, w, h, line)이다. band는 덩어리 안 단 순번(위부터), col은 단 안 율명 열 묶음
    순번(오른쪽부터), row는 열 안 줄 순번(위부터), line은 칸 위 가로선의 굵기(px, 열의 첫 칸은 None)다.
    거리 기준은 고정 px가 아니라 쪽에서 가장 긴 세로선 길이와 선 굵기에 비례한다.
    """
    dark = gray < DARK
    lines, longest = vlines(dark)
    if not lines:
        return []
    near = max(4, longest / 200)  # 같은 단으로 볼 선 끝 차이
    same_x = max(3, int(np.median([t for *_, t in lines])))
    bands = []
    for _, a, b, _ in sorted(lines, key=lambda l: (l[1], l[2])):
        if not any(abs(a - p) <= near and abs(b - q) <= near for p, q in bands):
            bands.append((a, b))
    # 단마다 그 단 전체를 덮는 세로선 사이가 열이다. 같은 열이 여러 단에서 잡히면 가장 긴 단을 쓴다.
    cols = {}
    for a, b in bands:
        xs = sorted((x, t) for x, y0, y1, t in lines if y0 <= a + near and y1 >= b - near)
        for (x0, t0), (x1, t1) in zip(xs, xs[1:]):
            if x1 - x0 <= max(8, longest / 100):
                continue
            same = [k for k in cols if abs(k[0] - x0) < same_x and abs(k[1] - x1) < same_x and k[2] < b and k[3] > a]
            if all(k[3] - k[2] < b - a for k in same):
                for k in same:
                    del cols[k]
                cols[(x0, x1, a, b)] = separators(dark, x0, x1, a, b, margin=max(t0, t1) // 2 + 2,
                                                  edge_gap=max(4, longest / 300))
    # 정간 열은 칸 높이가 쪽의 정간 높이(모든 칸 높이의 중간값)에 가깝다. 각 구분 굵은 선만 가로지르는 가사·빈 열은
    # 칸이 정간 여러 개 높이라 가로선 없는 열로 본다.
    spans = {k: rows([y for y, _ in seps], k[2], k[3]) for k, seps in cols.items() if seps}
    heights = sorted(v - u for r in spans.values() for u, v in r)
    if heights:
        pitch = heights[len(heights) // 2]
        cols = {k: (seps if k in spans and np.median([v - u for u, v in spans[k]]) <= pitch * 1.5 else [])
                for k, seps in cols.items()}
    # 단별로 열을 오른쪽부터 보며 가로선 있는 열이 이어진 묶음(run)을 모은다.
    #   run 길이 = parts: 가사 열에 가로선이 없는 판형. run이 한 각 묶음이고 오른쪽 열부터 1성부다.
    #   run 길이가 짝수(성부 하나): 가사 열에도 가로선이 있어 율명·가사 열이 번갈아 있다. 먹이 많은 쪽(한자)이 율명 열이다.
    ink = lambda run: np.mean([(gray[int(a):int(b), int(c[0]) + 3:int(c[1]) - 2] < 160).mean() for c in run])
    by_band = {}
    for (x0, x1, a, b), seps in cols.items():
        by_band.setdefault((a, b), []).append((x0, x1, seps))
    per_band = []  # (단 위, [악보 덩어리]), 덩어리 = [(묶음 x, [성부별 칸 목록])] 오른쪽부터
    for (a, b), band_cols in by_band.items():
        band_cols.sort(key=lambda c: -c[0])
        runs, prev = [], None
        for c in band_cols:
            if c[2] and prev and prev[2] and abs(prev[0] - c[1]) < same_x:
                runs[-1].append(c)
            elif c[2]:
                runs.append([c])
            prev = c
        groups = []
        for run in runs:
            # 가사 열은 율명 열보다 넓지 않다. 오른쪽이 넓은 제목 칸인 열(장단 열)은 빼낸다.
            # 가사 열의 오른쪽 세로선이 일부 단에서 끊긴 판형(박연폭포)이 있어 가사 열은 위아래로 겹치는 다른 단에서도 찾는다.
            lyric = any(abs(k[0] - run[0][1]) < same_x and k[2] < b and k[3] > a and not seps
                        and k[1] - k[0] <= (run[0][1] - run[0][0]) * 1.05 for k, seps in cols.items())
            if len(run) == parts and lyric:
                chains = [run]
            elif allow_alternating_lyrics and parts == 1 and len(run) % 2 == 0:
                chains = [[c] for c in max(run[0::2], run[1::2], key=ink)]
            else:
                continue
            for ch in chains:
                part_cells = []
                for x0, x1, seps in ch:
                    width = dict(seps)
                    part_cells.append([dict(x=x0, y=t, w=x1 - x0, h=u - t, line=width.get(t))
                                       for t, u in rows([y for y, _ in seps], a, b)])
                groups.append((ch[0][0], part_cells))
        # 한 단에서 묶음 간격이 평소의 1.3배를 넘으면 다른 악보로 나눈다.
        if groups:
            steps = [p[0] - q[0] for p, q in zip(groups, groups[1:])]
            step = sorted(steps)[len(steps) // 2] if steps else 0
            per_band.append((a, [[groups[0]]]))
            for p, q in zip(groups, groups[1:]):
                if p[0] - q[0] > step * 1.3:
                    per_band[-1][1].append([])
                per_band[-1][1][-1].append(q)
    per_band.sort(key=lambda band: band[0])
    # 정간 크기는 악보 안에서 거의 같다. 중간값과 높이가 2배 넘게, 너비가 1.5배 넘게 다른 칸이 있는 묶음
    # (장단 열, 단 사이 빈 줄까지 덮는 바깥 테두리, 머리 그림의 작은 무늬 등)은 정간 열이 아니다.
    boxes = [c for _, bl in per_band for blk in bl for g in blk for p in g[1] for c in p]
    if boxes:
        med = lambda v: sorted(v)[len(v) // 2]
        h, w = med([c['h'] for c in boxes]), med([c['w'] for c in boxes])
        fits = lambda g: all(h / 2 <= c['h'] <= h * 2 and w / 1.5 <= c['w'] <= w * 1.5 for p in g[1] for c in p)
        per_band = [(a, [blk for blk in ([g for g in blk if fits(g)] for blk in bl) if blk]) for a, bl in per_band]
        per_band = [(a, bl) for a, bl in per_band if bl]
    # 단마다 악보가 하나이고 단끼리 위아래로 겹치지 않으면 여러 단을 한 악보로 잇는다.
    # 아니면(나란한 악보) 덩어리를 왼쪽부터 늘어놓는다.
    bottoms = [(a, max(c['y'] + c['h'] for g in blk for p in g[1] for c in p)) for a, (blk, *_) in per_band]
    if all(len(blocks) == 1 for _, blocks in per_band) and all(p[1] <= q[0] + 4 for p, q in zip(bottoms, bottoms[1:])):
        blocks = [[(band, blk) for band, (_, (blk,)) in enumerate(per_band)]]
    else:
        blocks = sorted(([(0, blk)] for _, bl in per_band for blk in bl), key=lambda b: b[-1][1][-1][0])
    return [[[dict(c, band=band, col=col, row=row)
              for band, blk in block for col, g in enumerate(blk) for row, c in enumerate(g[1][k])]
             for k in range(parts)] for block in blocks]


def find_blocks(gray, parts=1, *, allow_alternating_lyrics=True):
    """find_grid의 칸을 (x, y, w, h) 상자로만 돌려준다. 한 단에 악보가 나란히 여럿(예: 소금보·단소보)이면
    왼쪽부터 덩어리가 나뉜다. 여러 단은 한 덩어리로 잇는다."""
    return [[[(c['x'], c['y'], c['w'], c['h']) for c in part] for part in block]
            for block in find_grid(gray, parts, allow_alternating_lyrics=allow_alternating_lyrics)]


def gaks(cells, fits=None):
    """find_grid의 칸(한 성부)을 각으로 나눈다 (S15P31E102-1077 D-1·D-3). 각은 dict(cells, source, mismatch)다.

    칸 위 가로선이 쪽 가로선 굵기 중간값의 1.8배 이상이면서 2px 넘게 굵으면 굵은 각 구분선이다. 표준악보집의
    1px·2px 선 차이는 굵은 선이 아니다. 열의 첫 칸과 굵은 선 아래 칸이 각의 첫 칸이다(source='line').
    그렇게 나눈 각이 fits에 맞지 않는데 열 전체가 맞으면 굵은 선은 박 구분선이고 열이 각 하나다(source='column').
    쪽에 굵은 선이 없으면 열 하나가 각 하나다(source='column'). fits(정간 수)가 열 길이를 받지 않으면
    fits가 받는 가장 작은 약수로 열을 나눈다(source='jangdan'). 굵은 선 없는 긴 각의 박 환산은 하지 않는다(1076).
    fits가 각의 정간 수를 받지 않으면 mismatch다. fits가 없으면 어떤 정간 수든 받는다.
    """
    fits = fits or (lambda n: True)
    columns = []
    for c in cells:
        if not columns or (c['band'], c['col']) != (columns[-1][0]['band'], columns[-1][0]['col']):
            columns.append([])
        columns[-1].append(c)
    lines = sorted(c['line'] for c in cells if c['line'])
    thin = lines[len(lines) // 2] if lines else 0
    thick = lambda c: c['line'] and c['line'] >= thin * 1.8 and c['line'] > thin + 2
    out = []
    if any(thick(c) for c in cells):
        for col in columns:
            split = []
            for c in col:
                if c is col[0] or thick(c):
                    split.append(dict(cells=[], source='line'))
                split[-1]['cells'].append(c)
            # 굵은 선으로 나눈 각이 장단에 맞지 않고 열 전체가 맞으면 굵은 선은 박 구분선이다(표준악보집 늴리리야).
            if not all(fits(len(g['cells'])) for g in split) and fits(len(col)):
                split = [dict(cells=col, source='column')]
            out += split
    else:
        for col in columns:
            size = len(col) if fits(len(col)) else next(
                (d for d in range(2, len(col)) if len(col) % d == 0 and fits(d)), len(col))
            source = 'column' if size == len(col) else 'jangdan'
            out += [dict(cells=col[i:i + size], source=source) for i in range(0, len(col), size)]
    return [dict(g, mismatch=not fits(len(g['cells']))) for g in out]
