"""글자 층이 있는 정간보 PDF(교과서 표준악보집)를 JG-like 텍스트로 쓴다. OMR 없이 글자·선 좌표를 읽는다.

실행: python data/src/scores/standard_score_text.py <pdf> <출력 폴더> [--title 제목]

출력
  <제목>.txt   JG-like 텍스트. 한 줄이 각(악보의 한 열), 정간은 '|', 정간 안은 ':위치 율명'.
               jeongganbo-generator의 tools/import-ngc-omr.mjs가 그대로 읽는다.
  report.json  열·정간 수, 반칸으로 나뉜 정간, 읽지 않은 글자(시김새 등) 목록

전제
  * 세로쓰기: 열은 오른쪽에서 왼쪽으로, 열 안은 위에서 아래로 읽고, 한 열이 한 각이다.
  * 정간 세로선은 악보 높이 거의 전체를 잇고, 정간 가로선은 점선(짧은 선분 여러 개)이다.
  * 율명 글자는 아래 표의 한자, '-'(이음), '△'(쉼표)뿐이다. 그 밖의 글자는 report.json에 남긴다.
"""
import argparse
import json
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
                        out.append(dict(c=c, x=(x0 + x1) / 2, y=(y0 + y1) / 2, size=span["size"]))
    return out


def lines(page):
    h, v = [], []
    for d in page.get_drawings():
        for it in d["items"]:
            if it[0] != "l":
                continue
            a, b = it[1], it[2]
            if abs(a.y - b.y) < 0.5:
                h.append((a.y, min(a.x, b.x), max(a.x, b.x)))
            elif abs(a.x - b.x) < 0.5:
                v.append((a.x, min(a.y, b.y), max(a.y, b.y)))
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
    split = any(len(r) > 1 for r in rows)
    if len(rows) == 1 and not split and abs(rows[0][0]["y"] - (top + bottom) / 2) > (bottom - top) * 0.2:
        # 가운데가 아닌 한 글자: 위·아래 반 중 어디인지로 2행 정간으로 본다(빈 행은 앞 음이 이어짐).
        rows = [rows[0], []] if rows[0][0]["y"] < (top + bottom) / 2 else [[], rows[0]]
    if len(rows) not in POS:
        raise ValueError(f"정간 안 행 {len(rows)}개는 지원하지 않음: {''.join(n['c'] for n in notes)}")
    toks = []
    for r, row in enumerate(rows):
        row = sorted(row, key=lambda n: n["x"])
        left, whole, right = POS[len(rows)][r]
        slots = [whole] if len(row) == 1 else [left, right] if len(row) == 2 else None
        if slots is None or None in slots:
            raise ValueError(f"한 행에 글자 {len(row)}개는 지원하지 않음: {''.join(n['c'] for n in row)}")
        toks += [f":{p} {NOTE[n['c']]}" for p, n in zip(slots, row)]
    return " ".join(toks), split


def read_page(page):
    g = glyphs(page)
    hanja = [n for n in g if n["c"] in NOTE and n["c"] not in "-△"]
    if not hanja:
        raise ValueError("율명 글자가 없음(오선보이거나 글자 층이 없는 PDF)")
    hlines, vlines = lines(page)

    # 악보 높이 거의 전체를 잇는 세로선 = 열 경계. 율명 한자가 있는 칸만 음 열이다(가사·장단 열 제외).
    span = max(n["y"] for n in hanja) - min(n["y"] for n in hanja)
    vx = cluster([x for x, y0, y1 in vlines if y1 - y0 > span * 0.8], 1.0)
    cols = [(a, b) for a, b in zip(vx, vx[1:]) if any(a < n["x"] < b for n in hanja)]
    width = sorted(b - a for a, b in cols)[len(cols) // 2]
    cols = [(a, b) for a, b in cols if b - a < width * 1.5]  # 옆 쪽 설명문처럼 넓은 칸의 한자는 악보가 아니다
    notes =[n for n in g if n["c"] in NOTE and any(a < n["x"] < b for a, b in cols)]
    cols.sort(key=lambda c: -c[0])  # 오른쪽 열부터

    gaks, report = [], dict(columns=len(cols), jeonggans=[], split_cells=[], ignored=[])
    for ci, (a, b) in enumerate(cols):
        # 이 열 안의 가로선: 점선 조각을 합쳐 열 너비의 30% 이상 덮는 y(점선은 약 40%를 덮는다)
        cover = defaultdict(float)
        for y, x0, x1 in hlines:
            cover[round(y)] += max(0.0, min(x1, b) - max(x0, a))
        rows_y = cluster([y for y, l in cover.items() if l > (b - a) * 0.3], 2.0)
        bands = list(zip(rows_y, rows_y[1:]))
        typical = sorted(b2 - t for t, b2 in bands)[len(bands) // 2]
        bands = [(t, b2) for t, b2 in bands if b2 - t > typical * 0.3]  # 곡 끝 이중선 사이의 얇은 틈은 정간이 아니다
        cells = []
        for top, bottom in bands:
            inside = [n for n in notes if a < n["x"] < b and top < n["y"] < bottom]
            text, split = cell_text(inside, top, bottom)
            if split:
                report["split_cells"].append(f"열{ci + 1} 정간{len(cells) + 1}")
            cells.append(text)
        report["ignored"] += [n["c"] for n in g if a < n["x"] < b and bands[0][0] < n["y"] < bands[-1][1]
                              and n not in notes and n["c"] not in "｜|"]
        report["jeonggans"].append(len(cells))
        gaks.append(" | ".join(cells))
    return gaks, report


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("pdf", type=Path)
    parser.add_argument("out", type=Path)
    parser.add_argument("--title", default=None)
    args = parser.parse_args()
    with pymupdf.open(args.pdf) as doc:
        gaks, report = read_page(doc[0])
    args.out.mkdir(parents=True, exist_ok=True)
    title = args.title or args.pdf.stem
    (args.out / f"{title}.txt").write_text("\n".join(gaks) + "\n", encoding="utf-8")
    (args.out / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{title}: 열 {report['columns']}, 정간 {report['jeonggans']}, 반칸 {len(report['split_cells'])}, 읽지 않은 글자 {len(report['ignored'])}")
