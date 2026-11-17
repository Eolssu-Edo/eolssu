"""정간 그림에서 율명 오른쪽의 작은 시김새 획을 찾아 OMR 토큰에 붙인다. 모델 재학습 없이 쓰는 보정이다.

MALerLab 모델은 국악원 정악보의 기호 크기·모양으로 배웠다. 실기교재처럼 율명 옆에 작게 그린 획은
놓치거나(니레 `^`, 니나 `И`, 흘림표 `)`), 자리는 맞히고 정악보 이름으로 잘못 읽는다(물결선 → 나니나·나느나·흘림표·끊는표).
근거와 측정은 docs/knowledge/data/jeongganbo-omr-accuracy.md의 "시김새 획 보정"에 있다.

규칙(악보 그림을 학습하지 않고 획 모양만 본다)
  * 한 줄에 율명 하나만 있는 자리(위치 0·2·5·8·10·11)만 본다. 반칸 둘이 있는 줄은 획이 어느 음 것인지 정할 수 없어 건너뛴다.
  * 줄마다 글자 덩어리(열 왼쪽에서 시작해 가로로 겹치는 요소)를 묶고, 그 오른쪽 끝보다 오른쪽에서 시작하고
    글자 왼쪽 끝에서 글자 높이만큼 떨어진 연결 요소만 획 후보다. 한자는 대개 너비와 높이가 비슷하다.
  * 세로 물결선: 길이가 칸 높이의 0.65 이상이면 걸친 줄의 율명마다 농음표, 그보다 짧으면 시작 줄의 율명에 풀어내림표.
    모델이 그 음에 정악보 이름(나니나·나느나·흘림표·끊는표)을 냈으면 바꾸고, 아무것도 안 냈으면 붙인다.
    농음표·풀어내림표를 이미 냈으면 그대로 둔다. 짧은 물결선의 이름은 교재마다 달라 unify_short_waves가 쪽 안에서 맞춘다.
  * 오른쪽 볼록 `)`·꺾인 `╮`은 흘림표, 위쪽 꼭짓점 `^`은 니레, 양쪽 세로획과 사선 `И`는 율명에 붙은 니나(우물사이 `{니나}`).
    이 셋은 모델이 그 음에 아무 시김새도 내지 않았을 때만 붙인다.
"""
import re

import cv2
import numpy as np

YUL = re.compile(r"^(하하배|하배|중청|배|청)?[황대태협고중유임이남무응]$")
WHOLE_ROW = {0: 0, 2: 0, 5: 1, 8: 2, 10: 0, 11: 1}  # 한 줄을 통째로 쓰는 위치 → 줄
WAVE_NAMES = {"농음표", "풀어내림표"}
WAVE_CONFUSED = {"나니나", "나느나", "흘림표", "끊는표"}  # 실기교재 물결선을 모델이 읽은 정악보 이름
NAMES = {"paren": "흘림표", "caret": "니레", "n": "니나*"}  # 니나*: 우물사이 변환에서 {니나}(И 모양)가 된다
INK = 160
LONG_WAVE = .65


def rows_of(position):
    return 1 if position == 0 else (3 if position <= 9 else 2)


def _components(gray):
    h, w = gray.shape
    ink = (gray < INK).astype(np.uint8)
    edge = max(2, round(min(h, w) * .04))  # 칸 선
    ink[:edge], ink[-edge:], ink[:, :edge], ink[:, -edge:] = 0, 0, 0, 0
    _, labels, stats, _ = cv2.connectedComponentsWithStats(ink, 8)
    return labels, [dict(i=i, x=int(s[0]), y=int(s[1]), w=int(s[2]), h=int(s[3]), area=int(s[4]))
                    for i, s in enumerate(stats) if i and s[4] >= 4]


def _turns(xs, min_step=1):
    """세로로 훑은 x 값의 좌우 방향 바뀜 수. min_step px보다 작은 흔들림은 무시한다."""
    turns, direction, anchor = 0, 0, xs[0] if len(xs) else 0
    for x in xs[1:]:
        if abs(x - anchor) < min_step:
            continue
        d = 1 if x > anchor else -1
        if direction and d != direction:
            turns += 1
        direction, anchor = d, x
    return turns


def classify(mask, cell_h):
    """획 하나(요소 상자 안의 bool 그림)의 모양 → 'wave'|'paren'|'caret'|'n'|None."""
    h, w = mask.shape
    lines = [np.nonzero(row)[0] for row in mask if row.any()]
    if h < 6 or w < 2 or len(lines) < 4:
        return None
    left = np.array([r.min() for r in lines])
    right = np.array([r.max() for r in lines])
    centre = (left + right) / 2
    edge_turns = max(_turns(left), _turns(right))
    if h >= 4 * w and h >= cell_h * .18 and edge_turns >= 4:
        return "wave"
    fifth = max(1, len(centre) // 5)
    bulge = centre[len(centre) // 3:2 * len(centre) // 3 + 1].mean() - (centre[:fifth].mean() + centre[-fifth:].mean()) / 2
    if 1.2 * w <= h <= 4.5 * w and h >= cell_h * .1 and _turns(centre, max(1., w * .25)) <= 1:
        if bulge >= w * .15:
            return "paren"
        top = lines[0]
        if top.min() < w * .4 and top.max() > w * .6 and right[:len(right) // 3].max() >= w - 2 and bulge > 0:
            return "paren"  # 꺾인 ╮: 위 가로획 뒤에 오른쪽으로 내려온다
    if h > cell_h * .2 or mask.sum() / (h * w) >= .6:
        return None
    top_x = np.nonzero(mask[:max(1, h // 4)].any(0))[0]
    bottom_x = np.nonzero(mask[-max(1, h // 4):].any(0))[0]
    if w >= h * .9 and len(top_x) and len(bottom_x) and bottom_x.min() < w * .3 and bottom_x.max() > w * .7 \
            and w * .25 < top_x.mean() < w * .75 and top_x.max() - top_x.min() < w * .6:
        return "caret"
    cols = mask.sum(0) / h
    if .55 * h <= w <= 1.6 * h and cols[:max(1, w // 4)].max() > .6 and cols[-max(1, w // 4):].max() > .6 \
            and cols[w // 3:2 * w // 3 + 1].max() < .7:
        return "n"
    return None


def _glyph_extent(comps, top, bottom, w):
    """줄 [top, bottom)의 글자 덩어리 (왼쪽 끝, 오른쪽 끝, 높이). 열 왼쪽 절반에서 시작한 요소부터 가로로 겹치는 요소를 묶는다."""
    inside = [c for c in comps if c["y"] < bottom - 2 and c["y"] + c["h"] > top + 2]
    seed = [c for c in inside if c["x"] < w * .5 and c["h"] > (bottom - top) * .25]
    if not seed:
        return None
    left, right = min(c["x"] for c in seed), max(c["x"] + c["w"] for c in seed)
    grown = True
    while grown:
        grown = False
        for c in inside:
            if left <= c["x"] < right and c["x"] + c["w"] > right and c["h"] <= (bottom - top) * 1.2:
                right, grown = c["x"] + c["w"], True
    height = max(c["h"] for c in seed if c["h"] <= (bottom - top) * 1.2) if any(
        c["h"] <= (bottom - top) * 1.2 for c in seed) else bottom - top
    return left, right, height


def side_marks(text, gray):
    """OMR 원문 토큰과 정간 그림(흑백) → (고친 원문, 찾은 획 목록). 모델 출력의 위치·율명은 바꾸지 않는다."""
    tokens = [t.rsplit(":", 1) for t in text.split()]
    if not tokens or not all(len(t) == 2 and t[1].isdigit() for t in tokens):
        return text, []
    n_rows = rows_of(int(tokens[0][1]))
    if any(rows_of(int(p)) != n_rows for _, p in tokens):
        return text, []
    per_row = {}
    for k, (_, p) in enumerate(tokens):
        per_row.setdefault(WHOLE_ROW.get(int(p), (int(p) - 1) // 3 if 1 <= int(p) <= 9 else
                                         {12: 0, 13: 0, 14: 1, 15: 1}.get(int(p))), []).append(k)
    h, w = gray.shape
    labels, comps = _components(gray)
    band = h / n_rows
    glyphs = {r: _glyph_extent(comps, r * band, (r + 1) * band, w) for r in range(n_rows)}
    found, edits = [], {}
    for c in sorted(comps, key=lambda c: (c["y"], c["x"])):
        rows_hit = [r for r in range(n_rows) if c["y"] < (r + 1) * band - 2 and c["y"] + c["h"] > r * band + 2]
        extents = [glyphs[r] for r in rows_hit if glyphs[r]]
        if not extents or c["x"] + c["w"] >= w - 1:
            continue
        left = min(e[0] for e in extents)
        right = max(e[1] for e in extents)
        height = max(e[2] for e in extents)
        if c["x"] < right or c["x"] - left < height * .97:
            continue
        mask = labels[c["y"]:c["y"] + c["h"], c["x"]:c["x"] + c["w"]] == c["i"]
        kind = classify(mask, h)
        if kind is None:
            continue
        if kind == "wave":
            long_wave = c["h"] >= h * LONG_WAVE
            target_rows, name = (rows_hit, "농음표") if long_wave else (rows_hit[:1], "풀어내림표")
        else:
            long_wave = False
            row = rows_hit[0] if kind == "caret" else min(n_rows - 1, int((c["y"] + c["h"] / 2) // band))
            target_rows, name = [row], NAMES[kind]
        for r in target_rows:
            ks = per_row.get(r, [])
            if len(ks) != 1 or int(tokens[ks[0]][1]) not in WHOLE_ROW:
                continue  # 반칸 둘이거나 비어 있는 줄
            k = ks[0]
            core, *attached = tokens[k][0].split("_")
            if not YUL.match(core):
                continue
            current = edits.get(k, attached)
            if kind == "wave":
                if WAVE_NAMES & set(current):
                    if not long_wave and k not in edits:  # 모델이 낸 이름만 쪽 안 이름 맞춤의 근거로 남긴다
                        found.append(dict(name=next(a for a in current if a in WAVE_NAMES), token=k, action="kept",
                                          model=attached, bbox=[c["x"], c["y"], c["w"], c["h"]], short=True))
                    continue
                kept = [a for a in current if a not in WAVE_CONFUSED]
                action = "renamed" if len(kept) != len(current) else "added"
                edits[k] = kept + [name]
            else:
                if current:
                    continue
                action = "added"
                edits[k] = [name]
            found.append(dict(name=name, token=k, action=action, model=attached,
                              bbox=[c["x"], c["y"], c["w"], c["h"]], short=kind == "wave" and not long_wave))
    if not edits:
        return text, found
    out = [f"{'_'.join([name.split('_')[0]] + edits[k]) if k in edits else name}:{p}"
           for k, (name, p) in enumerate(tokens)]
    return " ".join(out), found


def mark_cell(cell, gray):
    """리더의 정간 dict(text)를 고친다. 바뀌면 모델 원문(modelText)을, 획을 찾으면 sideMarks를 남긴다."""
    text, found = side_marks(cell["text"], gray)
    if text != cell["text"]:
        cell["modelText"], cell["text"] = cell["text"], text
    if found:
        cell["sideMarks"] = found
    return cell


def unify_short_waves(cells):
    """짧은 물결선 이름을 쪽 안에서 맞춘다.

    같은 모양의 짧은 물결선을 실기교재 정답은 풀어내림표, 진도아리랑 교재 정답은 농음표로 적었다. 모양만으로는 가릴 수 없어
    모델이 같은 쪽의 검출된 짧은 물결선을 이미 물결선 이름으로 읽었으면 그 이름(많은 쪽, 같으면 풀어내림표)을 쓰고,
    없으면 풀어내림표로 둔다. 바꾼 정간 수를 돌려준다.
    """
    kept = [m["name"] for c in cells for m in c.get("sideMarks", []) if m["action"] == "kept" and m.get("short")]
    if kept.count("농음표") <= kept.count("풀어내림표"):
        return 0
    changed = 0
    for c in cells:
        marks = [m for m in c.get("sideMarks", []) if m["action"] != "kept" and m.get("short")]
        if not marks:
            continue
        tokens = c["text"].split()
        for m in marks:
            name, position = tokens[m["token"]].rsplit(":", 1)
            tokens[m["token"]] = "_".join("농음표" if part == "풀어내림표" else part
                                          for part in name.split("_")) + ":" + position
            m["name"] = "농음표"
        c["text"] = " ".join(tokens)
        changed += 1
    return changed
