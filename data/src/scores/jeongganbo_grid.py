"""정간 상자를 열·줄 격자 자리에 맞춘다. 모델 없이 상자 좌표와 쪽 흑백 그림만 쓴다.

리더는 가는 가로선을 놓치면 두 정간을 한 상자로, 워터마크가 선에 겹치면 정간 하나를 빼고 낸다.
jeongganbo_omr.py는 정간 순번을 한 각의 정간 수로 나눠 각을 정하므로, 이런 칸 하나가 뒤 각을 모두 민다.
여기서 칸을 격자 자리 하나씩으로 맞춰 오류가 그 자리에만 남게 한다.

  * 자리 높이는 상자 높이의 중앙값이다. 줄 위치는 보통 상자의 위쪽 y와, 자리 여러 개 높이인 상자 안을
    자리 수로 나눈 위치를 모아 묶은 것이다. 모든 열에서 같은 줄이 합쳐져도 그 상자 안에서 줄을 얻는다.
  * 줄 k개(2개 이상)를 덮는 상자는 줄 위치에서 k칸으로 나누고 split을 붙인다. 부르는 쪽이 다시 읽는다.
  * 열에서 비어 있는 줄은 안쪽에 먹이 있으면 채운다(기존 --fill-grid 동작). 먹이 없어도 같은 열의 위·아래
    칸 사이에 있고 열의 세로선이 그 줄까지 이어지면 빈 정간으로 채운다. 채운 칸에는 filled를 붙인다.
    열 아래쪽 끝의 먹 없는 자리는 곡이 끝난 자리일 수 있어 채우지 않는다.
  * 칸 오류가 없으면(모든 상자가 줄 하나씩 덮고 빈 줄이 없으면) 열을 그대로 둔다.
"""

INK = 160  # 이보다 어두운 픽셀을 먹으로 본다. jeongganbo_omr.py의 ink 계산과 같다
LINE = 200  # 세로선 픽셀로 볼 밝기 상한
TALL = 1.5  # 자리 높이의 이 배수 이상인 상자를 여러 자리로 본다


def _med(values):
    return sorted(values)[len(values) // 2]


def _ink(gray, x, y, w, h):
    inner = gray[y + h * 15 // 100:y + h * 85 // 100, x + w * 15 // 100:x + w * 85 // 100]
    return float((inner < INK).mean()) if inner.size else 0.0


def _border(gray, x, y0, y1, reach=5):
    """x 근처(±reach)에서 y0~y1을 가장 많이 덮는 세로선의 덮은 비율."""
    band = gray[max(0, y0):max(0, y1), max(0, x - reach):max(0, x + reach + 1)]
    return float((band < LINE).mean(axis=0).max()) if band.size else 0.0


def _printed(gray, x, y, w, h):
    """열의 양쪽 세로선이 이 자리를 덮으면 인쇄된 정간이다. 머리글·빈 여백에 칸을 만들지 않게 한다."""
    y0, y1 = y + h // 5, y + h * 4 // 5
    return _border(gray, x - 2, y0, y1) >= .8 and _border(gray, x + w + 1, y0, y1) >= .8


def rows_of(cells, pitch):
    """줄 위치(위쪽 y) 목록. 보통 상자는 y 하나, 높은 상자는 안을 자리 수로 나눈 y를 낸다."""
    anchors = []
    for c in cells:
        k = round(c["h"] / pitch) if c["h"] >= pitch * TALL else 1
        anchors += [c["y"] + round(i * c["h"] / k) for i in range(k)]
    rows = []
    for y in sorted(anchors):
        if rows and y - rows[-1][-1] < pitch / 2:
            rows[-1].append(y)
        else:
            rows.append([y])
    return [_med(r) for r in rows]


def covered(cell, rows, pitch):
    """상자가 덮는 줄 위치들. 자리보다 낮은 상자도 위쪽 y에서 반 자리 안의 줄은 덮는다."""
    top = cell["y"] - pitch / 2
    return [r for r in rows if top <= r < top + max(cell["h"], pitch)]


def fit_columns(columns, gray, *, inside_only=False):
    """열 목록(열마다 상자 dict 목록)을 격자 자리에 맞춘다. 열을 제자리에서 바꾸고 다시 읽을 칸을 돌려준다.

    상자 dict에는 x, y, w, h가 있어야 한다. 나눈 칸은 split=True, 채운 칸은 filled=True이고 text는 비어 있다.
    inside_only는 열의 첫 칸과 끝 칸 사이만 채운다. 단마다 열을 따로 받은 경우(grid_boxes) 다른 단의 줄에
    칸을 만들지 않게 한다.
    """
    cells = [c for col in columns for c in col]
    if not cells:
        return []
    w, pitch = _med([c["w"] for c in cells]), _med([c["h"] for c in cells])
    rows = rows_of(cells, pitch)
    reread = []
    for col in columns:
        parts, kept = [], []
        for c in col:
            span = covered(c, rows, pitch)
            if len(span) < 2 or c["h"] < pitch * TALL:
                kept.append(c)
                continue
            tops = [c["y"]] + span[1:]
            pieces = [dict(text="", conf=None, x=c["x"], y=top, w=c["w"], h=end - top, split=True)
                      for top, end in zip(tops, tops[1:] + [c["y"] + c["h"]])]
            kept += pieces
            parts += pieces
        col[:] = kept
        x = _med([c["x"] for c in col])
        taken = {r for c in col for r in covered(c, rows, pitch)}
        inside = [i for i, r in enumerate(rows) if r in taken]
        for i, y in enumerate(rows):
            if y in taken:
                continue
            between = bool(inside) and inside[0] < i < inside[-1]
            if inside_only and not between:
                continue
            has_ink = _ink(gray, x, y, w, pitch) >= .005
            if not has_ink and not (between and _printed(gray, x, y, w, pitch)):
                continue
            cell = dict(text="", conf=None, x=x, y=y, w=w, h=pitch, filled=True)
            col.append(cell)
            parts.append(cell)
        col.sort(key=lambda c: c["y"])
        reread += parts
    return reread
