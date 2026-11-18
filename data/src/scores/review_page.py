"""변환 결과를 브라우저에서 바로 여는 검토 화면 HTML 한 장으로 묶는다.

실행: python data/src/scores/review_page.py <결과 폴더 또는 .jgb.json> [...] --jgb <.jgb.json 폴더> \
        --umulsai <jeongganbo-generator 폴더> --out <HTML 폴더>

결과 폴더는 jeongganbo_omr.py·standard_score_text.py의 출력 폴더(page.png + cells.json)다. --jgb는 그 결과를
import-ngc-omr.mjs로 처음 변환한 .jgb.json 폴더다. 정간 상자 없이 .jgb.json만 있으면 파일을 직접 주고,
같은 폴더에서 이름이 맞는 원본 PDF의 첫 쪽을 그림으로 붙인다. 이때는 문서의 선율이 처음 결과이고 --jgb는 필요 없다.
곡마다 <제목>.review.html을 만든다.
화면에서 악보 그림과 정간 상자를 보며 결과를 고치고, 고친 우물사이 문서(.jgb.json)와 검토 기록(.json)을
내려받는다. 문서의 레이아웃·대강 등은 처음 변환한 문서를 그대로 쓰고 선율·각 수·각별 정간 수만 바꾼다.
HTML에 원본 악보 그림이 들어가므로 공유 위치에 올리지 않는다.
"""
import argparse
import base64
import json
import subprocess
import sys
from pathlib import Path

import numpy as np

from grid_boxes import find_blocks

TEMPLATE = Path(__file__).with_name("review_page.html")
DPI = 150  # 정간 상자 탐지 해상도
IMAGE_DPI = 300  # 원본 칸 확대에 쓰는 그림 해상도

parser = argparse.ArgumentParser()
parser.add_argument("inputs", nargs="+", type=Path, help="결과 폴더 또는 .jgb.json 파일")
parser.add_argument("--jgb", type=Path, help="처음 변환한 <제목>.jgb.json이 있는 폴더. 결과 폴더를 줄 때 필요하다")
parser.add_argument("--umulsai", type=Path, required=True, help="jeongganbo-generator 클론 폴더. 시김새 그림을 가져온다")
parser.add_argument("--out", type=Path, required=True)
args = parser.parse_args()
args.out.mkdir(parents=True, exist_ok=True)
template = TEMPLATE.read_text(encoding="utf-8")
png_uri = lambda b: "data:image/png;base64," + base64.b64encode(b).decode()


def from_folder(folder):
    data = json.loads((folder / "cells.json").read_text(encoding="utf-8"))
    data["image"] = png_uri((folder / data["image"]).read_bytes())
    data["source"] = folder.name
    data["doc"] = json.loads((args.jgb / f"{data['title']}.jgb.json").read_text(encoding="utf-8"))
    return data


def from_jgb(path):
    """문서의 선율을 처음 결과로 쓰고, 정간 상자는 PDF 첫 쪽의 선에서 찾는다(grid_boxes.py).
    성부가 여럿이면 각 번호를 성부 순서로 이어 붙인다."""
    import pymupdf
    stem = path.name.removesuffix(".jgb.json")
    # '기초연습곡1·2_…-PDF14_소금연습곡1'처럼 한 PDF에서 여러 문서가 나오므로 이름이 앞부분과 맞는 가장 긴 PDF를 고른다.
    pdf = max((p for p in path.parent.glob("*.pdf") if stem.startswith(p.stem)), key=lambda p: len(p.stem), default=None)
    if pdf is None:
        sys.exit(f"{path.name}에 맞는 PDF가 같은 폴더에 없음")
    doc = json.loads(path.read_text(encoding="utf-8"))
    cells, info = [], []
    for part in doc.get("parts") or [doc]:
        rows = [r for r in part["melody"].split("\n") if r.strip()]
        lyrics = (part.get("lyrics") or "").split("\n")
        gak0 = sum(p["gaks"] for p in info)
        info.append(dict(name=part.get("name") or f"{len(info) + 1}성부", gaks=len(rows)))
        for g, row in enumerate(rows):
            lyric = lyrics[g].split("|") if g < len(lyrics) else []
            for k, cell in enumerate(row.split("|")):
                # 가사는 선율과 같은 자리(각·정간)에 있다. 세로표는 가사 줄 이음 표시라 뺀다.
                text = lyric[k].replace("{세로표}", "").strip() if len(lyric) == len(row.split("|")) else ""
                cells.append(dict(um=cell.strip(), gak=gak0 + g, conf=None, x=None, lyric=text))
    # ponytail: 첫 쪽만 붙인다. 여러 쪽 PDF가 생기면 쪽마다 그림을 이어 붙인다.
    page = pymupdf.open(pdf)[0]
    image = png_uri(page.get_pixmap(dpi=IMAGE_DPI).tobytes("png"))
    # 그림의 선으로 정간 상자를 찾아 칸 수가 문서와 같은 악보에 순서대로 붙인다.
    pix = page.get_pixmap(dpi=DPI, colorspace=pymupdf.csGRAY)
    gray = np.frombuffer(pix.samples, np.uint8).reshape(pix.height, pix.width)
    by_part = [[c for c in cells if gak0 <= c["gak"] < gak0 + p["gaks"]]
               for p, gak0 in zip(info, np.cumsum([0] + [p["gaks"] for p in info]))]
    blocks = [b for b in find_blocks(gray, len(info)) if [len(p) for p in b] == [len(p) for p in by_part]]
    if len(blocks) > 1:
        # 칸 수가 같은 악보가 나란하면(소금보·단소보, 연습곡 1·2) 오른쪽 제목 칸에 '소금'(이름 뒤에 붙은 곡 이름)이 든 것
        key = stem[len(pdf.stem):].strip("_") or "소금"
        blocks = [b for b in blocks if any(key in t for t in text_columns_right_of(page, b[0]))]
    if len(blocks) == 1:
        for part_cells, boxes in zip(by_part, blocks[0]):
            for c, (x, y, w, h) in zip(part_cells, boxes):
                c.update(x=round(x * IMAGE_DPI / DPI), y=round(y * IMAGE_DPI / DPI),
                         w=round(w * IMAGE_DPI / DPI), h=round(h * IMAGE_DPI / DPI))
    else:
        print(f"{stem}: 칸 수가 맞는 악보 {len(blocks)}개라 정간 상자 없이 만든다")
    return dict(title=stem, source=path.parent.name, image=image, cells=cells, doc=doc,
                parts=info if "parts" in doc else None)


def text_columns_right_of(page, boxes):
    """상자들 오른쪽(정간 너비 세 배 안)의 글자를 세로 줄별로 위→아래로 읽는다. 세로쓰기 제목·가사 줄이 한 줄씩 나온다."""
    right = max(x + w for x, y, w, h in boxes)
    width = sorted(w for x, y, w, h in boxes)[len(boxes) // 2]
    top, bottom = min(y for x, y, w, h in boxes), max(y + h for x, y, w, h in boxes)
    k = DPI / 72  # PDF 좌표(pt) → 그림 픽셀
    chars = sorted((ch["bbox"][0] * k, ch["bbox"][1], ch["c"]) for block in page.get_text("rawdict")["blocks"]
                   for line in block.get("lines", []) for span in line["spans"] for ch in span["chars"]
                   if right < ch["bbox"][0] * k < right + width * 3 and top < ch["bbox"][1] * k < bottom and ch["c"].strip())
    columns = []
    for x, y, c in chars:
        if columns and x - columns[-1][-1][0] <= 10:
            columns[-1].append((x, y, c))
        else:
            columns.append([(x, y, c)])
    return ["".join(c for _, _, c in sorted(col, key=lambda t: t[1])) for col in columns]


# 우물사이 기호 사전(js/symbols-registry.js)과 기호 그림(js/symbols-data.js, MIT)을 읽어
# 시김새 한글 이름 → SVG data URI 표를 만든다. 두 파일이 JS라 Node로 읽는다.
JS = """
const fs = require("fs"), g = {}, d = process.argv[1];
new Function("window", fs.readFileSync(d + "/js/symbols-registry.js", "utf8") + ";" + fs.readFileSync(d + "/js/symbols-data.js", "utf8"))(g);
const out = {};
for (const e of g.JGB_SYM.list) if (g.SYM_DATA[e.id]) out[e.ko] = g.SYM_DATA[e.id];
process.stdout.write(JSON.stringify(out));
"""
symbols = json.loads(subprocess.run(["node", "-e", JS, str(args.umulsai.resolve())], capture_output=True,
                                    check=True, encoding="utf-8").stdout)
for path in args.inputs:
    data = from_jgb(path) if path.name.endswith(".jgb.json") else from_folder(path)
    data["symbols"] = symbols
    # </script>가 데이터 안에 있어도 스크립트가 끊기지 않게 '/'를 이스케이프한다.
    html = template.replace("/*DATA*/null", json.dumps(data, ensure_ascii=False).replace("</", "<\\/"))
    out = args.out / f"{data['title']}.review.html"
    out.write_text(html, encoding="utf-8")
    print(f"{out}  정간 {len(data['cells'])}개")
