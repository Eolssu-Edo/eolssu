"""글자 층이 있는 정간보 PDF(교과서 표준악보집)를 JG-like 텍스트로 쓴다. OMR 없이 글자·선 좌표를 읽는다.

실행: python data/src/scores/standard_score_text.py <pdf> <출력 폴더> [--title 제목]

출력
  <제목>.txt   JG-like 텍스트. 한 줄이 각(악보의 한 열), 정간은 '|', 정간 안은 ':위치 율명'.
               jeongganbo-generator의 tools/import-ngc-omr.mjs가 그대로 읽는다.
  report.json  열·정간 수, 반칸으로 나뉜 정간, 읽지 않은 글자(시김새 등) 목록
  page.png, cells.json  검토 화면(review_page.py)용 쪽 그림과 정간별 상자·결과

전제
  * 세로쓰기: 열은 오른쪽에서 왼쪽으로, 열 안은 위에서 아래로 읽고, 한 열이 한 각이다.
  * 정간 세로선은 악보 높이 거의 전체를 잇고, 정간 가로선은 점선(짧은 선분 여러 개)이다.
  * 율명 글자는 아래 표의 한자, '-'(이음), '△'(쉼표)뿐이다. 그 밖의 글자는 report.json에 남긴다.
"""
import argparse
import json
import re
import sys
import unicodedata
from collections import defaultdict
from pathlib import Path

import pymupdf

# 율명 한자 → 한글. 우물사이(jeongganbo-generator js/app.js OCT_HANJA)와 같은 표.
BASE = dict(황="黃", 대="大", 태="太", 협="夾", 고="姑", 중="仲", 유="蕤", 임="林", 이="夷", 남="南", 무="無", 응="應")
OCT = {
    "청": dict(황="潢", 대="汏", 태="汰", 협="浹", 고="㴌", 중="㳞", 유="㶋", 임="淋", 이="洟", 남="湳", 무="潕", 응="㶐"),
    "배": dict(황="僙", 대="㐲", 태="㑀", 협="俠", 고="㑬", 중="㑖", 임="㑣", 이="侇", 남="㑲", 무="㒇", 응="㒣"),
    "중청": dict(황="㶂", 태="㳲", 협="㴺", 고="㵈", 중="㴢", 유="㶙", 임="㵉", 이="㴣", 남="㵜", 무="㶃", 응="㶝"),
    "하배": dict(황="㣴", 대="㣕", 태="㣖", 협="㣣", 고="㣨", 중="㣡", 유="㣸", 임="㣩", 남="㣮", 무="㣳", 응="㣹"),
}
NOTE = {h: y for y, h in BASE.items()} | {h: p + y for p, t in OCT.items() for y, h in t.items()}
NOTE |= {"-": "-", "－": "-", "△": "쉼표"}

IMAGE_DPI = 150  # 검토 화면용 page.png 해상도
NO_YUL_EXIT = 3  # 율명 글자가 없는 쪽의 종료 코드. 악보 인식 서비스가 이 값으로 OMR 경로를 고른다
NO_GRID_EXIT = 4  # 율명은 있지만 정간 격자를 읽지 못한 쪽. 그 밖의 오류는 1(파이썬 기본)이다

# 정간 안 위치 번호(JG-like). 행 수별로 [행][왼·통째·오른].
POS = {1: [(None, 0, None)], 2: [(12, 10, 13), (14, 11, 15)], 3: [(1, 2, 3), (4, 5, 6), (7, 8, 9)]}


def glyphs(page):
    out = []
    for block in page.get_text("rawdict")["blocks"]:
        for line in block.get("lines", []):
            for span in line["spans"]:
                for ch in span["chars"]:
                    if ch["c"].strip():
                        x0, y0, x1, y1 = ch["bbox"]
                        # 일부 곡은 호환용 한자(U+F9F5 淋 등)로 들어 있어 NFKC로 표준 한자에 맞춘다
                        c = unicodedata.normalize("NFKC", ch["c"])
                        # i: 그린 순서. 같은 자리에 겹친 글자는 나중에 그린 것이 보인다
                        out.append(dict(c=c, x=(x0 + x1) / 2, y=(y0 + y1) / 2, size=span["size"], i=len(out)))
    return out


def vector_symbols(page, glyphs_):
    """글자 대신 도형으로 그린 삼각 쉼표를 읽는다. 점으로 음역을 추정하지 않는다."""
    sizes = sorted(n["size"] for n in glyphs_ if n["c"] in NOTE and n["c"] not in "-△")
    if not sizes:
        return glyphs_
    size = sizes[len(sizes) // 2]
    rests, clips = [], []
    for d in page.get_drawings(extended=True):
        while clips and clips[-1][0] >= d["level"]:
            clips.pop()
        if d["type"] == "clip":
            clips.append((d["level"], d["scissor"] if not clips else d["scissor"] & clips[-1][1]))
            continue
        if d["type"] == "group":
            continue
        r, items = d["rect"], d["items"]
        if clips and not clips[-1][1].contains(r):
            continue
        cx, cy = (r.x0 + r.x1) / 2, (r.y0 + r.y1) / 2
        if not (len(items) == 3 and all(it[0] == "l" for it in items) and
                0.35 * size <= r.width <= 1.8 * size and 0.6 <= r.height / r.width <= 1.4):
            continue
        # 닫힌 위쪽 삼각형만 쉼표다. 꺾쇠·화살표·작은 격자 선분은 제외한다.
        points = [it[1] for it in items]
        if any(abs(items[i][2] - items[(i + 1) % 3][1]) > 0.1 for i in range(3)):
            continue
        top = min(points, key=lambda p: p.y)
        base = [p for p in points if p is not top]
        if abs(top.x - cx) > r.width * 0.12 or abs(base[0].y - base[1].y) > r.height * 0.12:
            continue
        if any(n["c"] == "△" and abs(n["x"] - cx) < size / 2 and abs(n["y"] - cy) < size / 2
               for n in glyphs_):
            continue
        rests.append(dict(c="△", x=cx, y=cy, size=size, i=len(glyphs_) + len(rests)))

    return glyphs_ + rests


def lines(page):
    """보이는 가로·세로 선분. 일부 PDF(소금·단소 표준악보집)는 긴 선을 그린 뒤 잘라내기 영역(clip)으로 칸 안만
    보이게 해서, 잘라내기 영역과 겹치는 부분만 남긴다."""
    h, v, clips = [], [], []  # clips: (단계, 보이는 영역). 단계가 더 깊은 그림에만 적용된다
    for d in page.get_drawings(extended=True):
        while clips and clips[-1][0] >= d["level"]:
            clips.pop()
        if d["type"] == "clip":
            r = d["scissor"] if not clips else d["scissor"] & clips[-1][1]
            clips.append((d["level"], r))
            continue
        if d["type"] == "group":
            continue
        r = clips[-1][1] if clips else page.rect
        for it in d["items"]:
            if it[0] != "l":
                continue
            a, b = it[1], it[2]
            if abs(a.y - b.y) < 0.5 and r.y0 <= a.y <= r.y1:
                x0, x1 = max(min(a.x, b.x), r.x0), min(max(a.x, b.x), r.x1)
                if x1 > x0:
                    h.append((a.y, x0, x1))
            elif abs(a.x - b.x) < 0.5 and r.x0 <= a.x <= r.x1:
                y0, y1 = max(min(a.y, b.y), r.y0), min(max(a.y, b.y), r.y1)
                if y1 > y0:
                    v.append((a.x, y0, y1))
    return h, v


def cluster(values, tol):
    out = []
    for x in sorted(values):
        if out and x - out[-1][-1] <= tol:
            out[-1].append(x)
        else:
            out.append([x])
    return [sum(g) / len(g) for g in out]


def cell_text(notes, top, bottom):
    """정간 하나의 글자들 → ':위치 율명 …'. 행은 글자 y로, 반칸은 같은 행의 글자 x로 가른다."""
    if not notes:
        return "", False
    size = max(n["size"] for n in notes)
    rows = []
    for n in sorted(notes, key=lambda n: n["y"]):
        if rows and n["y"] - rows[-1][-1]["y"] < size * 0.6:
            rows[-1].append(n)
        else:
            rows.append([n])
    # 같은 자리에 겹쳐 그린 글자는 나중에 그린 것만 보인다(밀양아리랑 단소의 무 위 청무, 소금의 청남 아래 '-').
    # 검수 정답도 보이는 글자로 읽었다.
    rows = [[n for n in row if not any(m["i"] > n["i"] and abs(m["x"] - n["x"]) < size * 0.5 for m in row)]
            for row in rows]
    split = any(len(r) > 1 for r in rows)
    if len(rows) == 1 and not split and abs(rows[0][0]["y"] - (top + bottom) / 2) > (bottom - top) * 0.2:
        # 가운데가 아닌 한 글자: 위·아래 반 중 어디인지로 2행 정간으로 본다(빈 행은 앞 음이 이어짐).
        rows = [rows[0], []] if rows[0][0]["y"] < (top + bottom) / 2 else [[], rows[0]]
    if len(rows) == 1 and len(rows[0]) == 2:
        # 한 행을 좌우 반칸으로 나눈 정간. JG-like에는 1행 좌우 위치가 없어, 길이가 같은 2행(위·아래 반)으로 쓴다.
        rows = [[n] for n in sorted(rows[0], key=lambda n: n["x"])]
    if len(rows) not in POS:
        raise ValueError(f"정간 안 행 {len(rows)}개는 지원하지 않음: {''.join(n['c'] for n in notes)}")
    toks = []
    for r, row in enumerate(rows):
        if not row:
            continue  # 빈 행은 쓰지 않는다. 변환 도구가 앞 음이 이어지는 것으로 읽는다
        row = sorted(row, key=lambda n: n["x"])
        left, whole, right = POS[len(rows)][r]
        slots = [whole] if len(row) == 1 else [left, right] if len(row) == 2 else None
        if slots is None or None in slots:
            raise ValueError(f"한 행에 글자 {len(row)}개는 지원하지 않음: {''.join(n['c'] for n in row)}")
        toks += [f":{p} {NOTE[n['c']]}" for p, n in zip(slots, row)]
    return " ".join(toks), split


class NoYulText(ValueError):
    """쪽에 율명 글자가 없다. 오선보이거나 글자 층이 없는(스캔한) PDF라 OMR로 읽어야 한다."""


def runs(vlines, gap=1.0):
    """같은 x에서 끊김 없이 이어지는(틈 gap pt 이하) 세로 선분을 하나로 잇는다. 정간 칸마다 잘라 그린 선을 열 경계로 되살린다."""
    groups = []  # x가 0.5pt 안인 선분 묶음
    for line in sorted(vlines):
        if groups and line[0] - groups[-1][-1][0] < 0.5:
            groups[-1].append(line)
        else:
            groups.append([line])
    out = []
    for group in groups:
        x, merged = group[0][0], []
        for _, y0, y1 in sorted(group, key=lambda l: l[1]):
            if merged and y0 <= merged[-1][2] + gap:
                merged[-1] = (x, merged[-1][1], max(merged[-1][2], y1))
            else:
                merged.append((x, y0, y1))
        out += merged
    return out


MIN_BORDER = 100  # 열 경계로 볼 세로선의 최소 길이(pt). 정간 높이(약 40~50pt)보다 길다
# 2중주 이상 열 머리 표시. '단소1'·'단소Ⅱ'·'1단소'·'단소2:' 모두 받는다
_INSTRUMENT, _NUMBER = r"(?:단소|소금|대금|피리|해금)", r"([1-9]|Ⅰ|Ⅱ|Ⅲ|Ⅳ)"
VOICE = re.compile(rf"^\s*(?:{_INSTRUMENT}\s*{_NUMBER}|{_NUMBER}\s*{_INSTRUMENT})\s*:?\s*$")
ROMAN = {"Ⅰ": 1, "Ⅱ": 2, "Ⅲ": 3, "Ⅳ": 4}


def voices(page, cols):
    """2중주 쪽이면 열마다 성부 번호, 아니면 None. 열 위 머리 표시('단소1'·'단소2')의 x가 든 열이 그 성부다.

    머리 표시는 맨 위 단에만 있어 아래 단의 열은 같은 x의 표시를 따른다. 표시가 없는 음 열이 있으면 가를 수 없어 멈춘다.
    """
    labels = []
    for block in page.get_text("dict")["blocks"]:
        for line in block.get("lines", []):
            for span in line["spans"]:
                m = VOICE.match(span["text"])
                if m:
                    number = m.group(1) or m.group(2)
                    labels.append(((span["bbox"][0] + span["bbox"][2]) / 2, ROMAN.get(number) or int(number)))
    if len({v for _, v in labels}) < 2:
        return None
    out = {}
    for c in cols:
        found = {v for x, v in labels if c[0] < x < c[1]}
        if len(found) != 1:
            raise ValueError("2중주 성부를 열마다 가르지 못함")
        out[c] = found.pop()
    return out


def percussion_marks(page, note_size):
    """원 안 세로획(장구의 덩 표기)만 찾는다. 단순 원·숨표·점은 제외한다."""
    drawings = page.get_drawings()
    strokes = [(a, b) for drawing in drawings for kind, *points in drawing["items"]
               if kind == "l" for a, b in [points] if abs(a.x - b.x) < 0.5]
    out = []
    for drawing in drawings:
        rect, items = drawing["rect"], drawing["items"]
        if (len(items) != 4 or any(item[0] != "c" for item in items) or
                not note_size * 0.4 < rect.width < note_size * 1.7 or
                not 0.85 < rect.width / max(rect.height, 0.01) < 1.15):
            continue
        x, y = (rect.x0 + rect.x1) / 2, (rect.y0 + rect.y1) / 2
        if any(abs(a.x - x) < rect.width * 0.15 and
               abs(min(a.y, b.y) - rect.y0) < rect.height * 0.15 and
               abs(max(a.y, b.y) - rect.y1) < rect.height * 0.15 for a, b in strokes):
            out.append(dict(x=x, y=y))
    return out


def read_page(page):
    g = vector_symbols(page, glyphs(page))
    hanja = [n for n in g if n["c"] in NOTE and n["c"] not in "-△"]
    if not hanja:
        raise NoYulText("율명 글자가 없음(오선보이거나 글자 층이 없는 PDF)")
    hlines, vlines = lines(page)

    # 율명 글자마다 좌우로 가장 가까운 긴 세로선 = 그 글자가 든 열의 경계. 두 선이 함께 덮는 높이가 열의 위·아래다.
    # 율명 한자가 있는 칸만 음 열이다(가사·장단 열 제외).
    borders = [l for l in runs(vlines) if l[2] - l[1] >= MIN_BORDER]
    def box_of(n):
        covering = [l for l in borders if l[1] <= n["y"] <= l[2]]
        left = max((l for l in covering if l[0] < n["x"]), key=lambda l: l[0], default=None)
        right = min((l for l in covering if l[0] > n["x"]), key=lambda l: l[0], default=None)
        return (left[0], right[0], max(left[1], right[1]), min(left[2], right[2])) if left and right else None

    boxes_ = {b for b in map(box_of, hanja) if b}
    # 장단 예시에는 이음 '-'도 있어 인접 선율 격자로 편입될 수 있다.
    # 율명이 없는 열에서 명시적인 장구 표기가 확인될 때만 제외한다.
    note_size = sorted(n["size"] for n in hanja)[len(hanja) // 2]
    accompaniment = {b for mark in percussion_marks(page, note_size) for b in [box_of(mark)] if b} - boxes_
    # 이음·쉼표만 든 단(곡 끝 '- - - △' 등)도 같은 x의 음 열이면 읽는다. 가사 칸의 '-'는 x가 달라 들어오지 않는다.
    xs = {(a, b) for a, b, _, _ in boxes_}
    boxes_ |= {b for n in g if n["c"] in "-－△" for b in [box_of(n)]
               if b and b not in accompaniment and (n["c"] == "△" or b[:2] in xs)}
    # 처음 나타난 x에도 이음만 있는 각이 올 수 있다. 같은 단의 이웃 악보 열과
    # 너비·위아래 경계가 맞는 격자만 이어 붙여 가사 칸의 '-'와 구분한다.
    tie_boxes = {b for n in g if n["c"] in "-－" for b in [box_of(n)] if b} - boxes_ - accompaniment
    while tie_boxes:
        adjacent = {b for b in tie_boxes if any(
            abs(b[2] - a[2]) < 1 and abs(b[3] - a[3]) < 1 and
            0.8 < (b[1] - b[0]) / (a[1] - a[0]) < 1.2 and
            min(abs(b[0] - a[1]), abs(a[0] - b[1])) < a[1] - a[0]
            for a in boxes_)}
        if not adjacent:
            break
        boxes_ |= adjacent
        tie_boxes -= adjacent
    if not boxes_:
        raise ValueError("정간 열을 찾지 못함(율명은 있지만 정간 격자가 없는 쪽)")
    width = sorted(b - a for a, b, _, _ in boxes_)[len(boxes_) // 2]
    # 넓은 칸(설명문)이나 좁은 칸(음계표)의 한자는 악보가 아니다
    cols = [c for c in boxes_ if width * 0.5 < c[1] - c[0] < width * 1.5]
    voice = voices(page, cols)
    if voice:  # 2중주 이상은 첫 성부만 읽는다(악보 인식 서비스는 VOICE_GUESSED로 알린다)
        cols = [c for c in cols if voice[c] == 1]
    notes = [n for n in g if n["c"] in NOTE and any(c[0] < n["x"] < c[1] and c[2] <= n["y"] <= c[3] for c in cols)]

    # 열마다 정간을 위에서부터 나누고, 얇은 틈(단 사이 빈 줄·곡 끝 이중선)에서 단을 끊는다. 한 단의 한 열이 한 각이다.
    # 쪽을 여러 단으로 나눈 판형(소금·단소 표준악보집 일부)은 단을 위에서부터, 단 안의 열을 오른쪽부터 읽는다.
    pieces, heights = [], []  # pieces: (단 위 y, 열 왼쪽 x의 음수, 열, [(위, 아래)])
    for a, b, col_top, col_bottom in cols:
        # 이 열 안의 가로선: 점선 조각을 합쳐 열 너비의 30% 이상 덮는 y(점선은 약 40%를 덮는다)
        cover = defaultdict(float)
        for y, x0, x1 in hlines:
            if col_top - 2 <= y <= col_bottom + 2:
                cover[round(y)] += max(0.0, min(x1, b) - max(x0, a))
        rows_y = cluster([y for y, l in cover.items() if l > (b - a) * 0.3], 2.0)
        bands = list(zip(rows_y, rows_y[1:]))
        if not bands:
            raise ValueError("악보 쪽이 아님(정간 가로선이 없는 열)")
        typical = sorted(b2 - t for t, b2 in bands)[len(bands) // 2]
        heights += [b2 - t for t, b2 in bands if b2 - t > typical * 0.3]
        tier = [[]]
        for t, b2 in bands:
            if b2 - t > typical * 0.3:
                tier[-1].append((t, b2))
            elif tier[-1]:
                tier.append([])
        pieces += [(cells[0][0], -a, (a, b), cells) for cells in tier if cells]
    # 단은 위 끝이 정간 높이(모든 열의 중앙값)의 절반 안이면 같은 단으로 묶는다
    typical = sorted(heights)[len(heights) // 2]
    pieces.sort(key=lambda p: p[0])
    tier_top, ordered = None, []
    for p in pieces:
        if tier_top is None or p[0] - tier_top > typical / 2:
            tier_top = p[0]
        ordered.append((tier_top, p[1], p[2], p[3]))
    pieces = sorted(ordered, key=lambda p: (p[0], p[1]))

    # 정간 수가 대부분의 각과 다른 열은 악보가 아니다(옆 쪽 장식음 표 등). 곡 끝 각은 짧을 수 있어,
    # 마지막 각이 다른 각과 같은 x 열이거나 바로 앞 각 열 옆(열 너비 2배 안)에 붙어 있으면 둔다(소금 취타 끝 4정간).
    counts = [len(p[3]) for p in pieces]
    mode = max(set(counts), key=counts.count)
    xs = [p[2] for p in pieces]

    def song_end(i):
        if i == 0:
            return False
        if i == len(pieces) - 1 and xs.count(xs[i]) > 1:  # 마지막 단의 짧은 각
            return True
        last_in_tier = i == len(pieces) - 1 or pieces[i + 1][0] != pieces[i][0]
        (_, b), (prev_a, _) = xs[i], xs[i - 1]
        return last_in_tier and pieces[i - 1][0] == pieces[i][0] and 0 <= prev_a - b < width * 2

    dropped = [p for i, p in enumerate(pieces) if counts[i] != mode and not song_end(i)]
    pieces = [p for p in pieces if p not in dropped]
    # 악보다운 쪽만 받는다. 설명문 상자 하나에 섞인 율명 한자를 가짜 음표로 만들지 않는다.
    if len(pieces) < 2 or counts.count(mode) * 2 < len(counts):
        raise ValueError(f"악보 쪽이 아님(각 {len(pieces)}개, 정간 수 {counts})")

    gaks, boxes = [], []
    report = dict(columns=len(pieces), jeonggans=[], split_cells=[], ignored=[], ignored_at=[],
                  excluded_accompaniment=[dict(x=round(a, 1), y=round(t, 1), w=round(b-a, 1), h=round(z-t, 1),
                                               reason="PERCUSSION_SYMBOL") for a, b, t, z in sorted(accompaniment)],
                  voices=max(voice.values()) if voice else 1,
                  dropped=[dict(x=round(a, 1), y=round(bands[0][0], 1), w=round(b - a, 1),
                                h=round(bands[-1][1] - bands[0][0], 1)) for _, _, (a, b), bands in dropped])
    for ci, (_, _, (a, b), bands) in enumerate(pieces):
        cells = []
        for top, bottom in bands:
            inside = [n for n in notes if a < n["x"] < b and top < n["y"] < bottom]
            text, split = cell_text(inside, top, bottom)
            if split:
                report["split_cells"].append(f"열{ci + 1} 정간{len(cells) + 1}")
            cells.append(text)
            boxes.append(dict(jg=text, gak=ci, conf=None, x=a, y=top, w=b - a, h=bottom - top))
        ignored = [n for n in g if a < n["x"] < b and bands[0][0] < n["y"] < bands[-1][1]
                   and n not in notes and n["c"] not in "｜|"]
        report["ignored"] += [n["c"] for n in ignored]
        # 확인 필요 칸 표시용 위치(PDF 좌표, pt)와 그 글자를 덮는 정간(이 쪽의 각 번호 gak, 정간 번호 jeonggan)
        report["ignored_at"] += [dict(c=n["c"], x=round(n["x"], 1), y=round(n["y"], 1), size=round(n["size"], 1),
                                      gak=ci, jeonggan=min(range(len(bands)), key=lambda k: abs(sum(bands[k]) / 2 - n["y"])))
                                 for n in ignored]
        report["jeonggans"].append(len(cells))
        gaks.append(" | ".join(cells))
    read_notes = sum(1 for n in notes if any(c[0] < n["x"] < c[1] for _, _, c, _ in pieces))
    if read_notes <= len(report["ignored"]):
        raise ValueError(f"악보 쪽이 아님(율명 {read_notes}, 읽지 않은 글자 {len(report['ignored'])})")
    return gaks, boxes, report

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("pdf", type=Path)
    parser.add_argument("out", type=Path)
    parser.add_argument("--title", default=None)
    parser.add_argument("--page", type=int, default=0, help="읽을 쪽 번호(0부터)")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    title = args.title or args.pdf.stem
    with pymupdf.open(args.pdf) as doc:
        try:
            gaks, boxes, report = read_page(doc[args.page])
        except NoYulText as e:
            print(e, file=sys.stderr)
            sys.exit(NO_YUL_EXIT)
        except ValueError as e:  # 율명은 있지만 이 판형의 정간 격자를 읽지 못했다
            print(e, file=sys.stderr)
            sys.exit(NO_GRID_EXIT)
        doc[args.page].get_pixmap(dpi=IMAGE_DPI).save(str(args.out / "page.png"))
    scale = IMAGE_DPI / 72  # PDF 좌표(pt) → page.png 픽셀
    for b in boxes:
        b.update({k: round(b[k] * scale) for k in ("x", "y", "w", "h")})
    (args.out / f"{title}.txt").write_text("\n".join(gaks) + "\n", encoding="utf-8")
    # 검토 화면(review_page.py)이 읽는 형식. jeongganbo_omr.py와 같다(conf는 글자 층이라 없음).
    (args.out / "cells.json").write_text(json.dumps(dict(title=title, image="page.png", beats=None, cells=boxes),
                                                    ensure_ascii=False, indent=1), encoding="utf-8")
    (args.out / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{title}: 열 {report['columns']}, 정간 {report['jeonggans']}, 반칸 {len(report['split_cells'])}, 읽지 않은 글자 {len(report['ignored'])}")
