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
    out = []
    for x, y0, y1 in sorted(vlines, key=lambda l: (round(l[0], 1), l[1])):
        if out and abs(out[-1][0] - x) < 0.5 and y0 <= out[-1][2] + gap:
            out[-1] = (out[-1][0], out[-1][1], max(out[-1][2], y1))
        else:
            out.append((x, y0, y1))
    return out


MIN_BORDER = 100  # 열 경계로 볼 세로선의 최소 길이(pt). 정간 높이(약 40~50pt)보다 길다


def read_page(page):
    g = glyphs(page)
    hanja = [n for n in g if n["c"] in NOTE and n["c"] not in "-△"]
    if not hanja:
        raise NoYulText("율명 글자가 없음(오선보이거나 글자 층이 없는 PDF)")
    hlines, vlines = lines(page)

    # 율명 글자마다 좌우로 가장 가까운 긴 세로선 = 그 글자가 든 열의 경계. 두 선이 함께 덮는 높이가 열의 위·아래다.
    # 율명 한자가 있는 칸만 음 열이다(가사·장단 열 제외).
    borders = [l for l in runs(vlines) if l[2] - l[1] >= MIN_BORDER]
    boxes_ = set()
    for n in hanja:
        covering = [l for l in borders if l[1] <= n["y"] <= l[2]]
        left = max((l for l in covering if l[0] < n["x"]), key=lambda l: l[0], default=None)
        right = min((l for l in covering if l[0] > n["x"]), key=lambda l: l[0], default=None)
        if left and right:
            boxes_.add((left[0], right[0], max(left[1], right[1]), min(left[2], right[2])))
    if not boxes_:
        raise ValueError("정간 열을 찾지 못함(율명은 있지만 정간 격자가 없는 쪽)")
    width = sorted(b - a for a, b, _, _ in boxes_)[len(boxes_) // 2]
    # 옆 쪽 설명문처럼 넓은 칸이나 음계표처럼 좁은 칸의 한자는 악보가 아니다
    cols = [c for c in boxes_ if width * 0.5 < c[1] - c[0] < width * 1.5]
    notes = [n for n in g if n["c"] in NOTE and any(c[0] < n["x"] < c[1] and c[2] <= n["y"] <= c[3] for c in cols)]

    # 열마다 정간을 위에서부터 나누고, 얇은 틈(단 사이 빈 줄·곡 끝 이중선)에서 단을 끊는다. 한 단의 한 열이 한 각이다.
    # 쪽을 여러 단으로 나눈 판형(소금·단소 표준악보집 일부)은 단을 위에서부터, 단 안의 열을 오른쪽부터 읽는다.
    pieces = []  # (단 위 y, 열 왼쪽 x의 음수, 열, [(위, 아래)])
    for a, b, col_top, col_bottom in cols:
        # 이 열 안의 가로선: 점선 조각을 합쳐 열 너비의 30% 이상 덮는 y(점선은 약 40%를 덮는다)
        cover = defaultdict(float)
        for y, x0, x1 in hlines:
            if col_top - 2 <= y <= col_bottom + 2:
                cover[round(y)] += max(0.0, min(x1, b) - max(x0, a))
        rows_y = cluster([y for y, l in cover.items() if l > (b - a) * 0.3], 2.0)
        bands = list(zip(rows_y, rows_y[1:]))
        typical = sorted(b2 - t for t, b2 in bands)[len(bands) // 2]
        tier = [[]]
        for t, b2 in bands:
            if b2 - t > typical * 0.3:
                tier[-1].append((t, b2))
            elif tier[-1]:
                tier.append([])
        pieces += [(cells[0][0], -a, (a, b), cells) for cells in tier if cells]
    # 단은 위 끝이 정간 높이의 절반 안이면 같은 단으로 묶는다
    pieces.sort(key=lambda p: p[0])
    tier_top, ordered = None, []
    for p in pieces:
        if tier_top is None or p[0] - tier_top > typical / 2:
            tier_top = p[0]
        ordered.append((tier_top, p[1], p[2], p[3]))
    pieces = sorted(ordered, key=lambda p: (p[0], p[1]))

    gaks, boxes = [], []
    report = dict(columns=len(pieces), jeonggans=[], split_cells=[], ignored=[], ignored_at=[])
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
        # 확인 필요 칸 표시용 위치(PDF 좌표, pt)
        report["ignored_at"] += [dict(c=n["c"], x=round(n["x"], 1), y=round(n["y"], 1)) for n in ignored]
        report["jeonggans"].append(len(cells))
        gaks.append(" | ".join(cells))
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
