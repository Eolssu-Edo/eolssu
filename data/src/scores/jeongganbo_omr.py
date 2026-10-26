"""정간보 악보 PDF 한 쪽을 MALerLab/jeongganbo-omr로 읽어 JG-like 텍스트로 쓴다.

실행 (OMR 저장소의 가상 환경 Python으로):
  <omr>/.venv/Scripts/python.exe data/src/scores/jeongganbo_omr.py <pdf> <쪽(0부터)> <출력 폴더> \
      --omr-dir <omr> --dpi 150 --beats 3

출력
  <제목>.txt       JG-like 텍스트. 한 줄이 각, 정간은 '|', 정간 안은 ':위치 율명'.
                   jeongganbo-generator의 tools/import-ngc-omr.mjs가 그대로 읽는다.
  page.png         렌더링한 쪽
  boxes.png        잘라 낸 정간 상자와 읽는 순서
  cells.json       정간마다 OMR 원문·신뢰도·상자

전제
  * 세로쓰기 정간보: 열은 오른쪽에서 왼쪽으로, 열 안은 위에서 아래로 읽는다.
  * 리더의 정간 크기 기준(너비 85~110px)은 국립국악원 정악보 300dpi에 맞춰져 있다.
    다른 악보는 정간 너비가 이 범위에 들도록 --dpi를 고른다(e-국악아카데미 교실 악보는 150).
  * 리더가 각을 나누지 못하는 쪽이 있어 각은 --beats(한 각의 정간 수)로 다시 나눈다.
  * 세로선이 연하고 끊긴 스캔은 --gap-fill 10 --line-threshold 250으로 칸 찾기 그림만 보정한다.
    율명 인식은 보정하지 않은 원래 그림을 잘라서 한다.
"""
import argparse
import json
import os
import re
import sys
from pathlib import Path

import cv2
import numpy as np
import pdfplumber
import torch

parser = argparse.ArgumentParser()
parser.add_argument("pdf", type=Path)
parser.add_argument("page", type=int)
parser.add_argument("out", type=Path)
parser.add_argument("--omr-dir", type=Path, required=True, help="MALerLab/jeongganbo-omr 클론 폴더")
parser.add_argument("--dpi", type=int, default=300)
parser.add_argument("--beats", type=int, required=True, help="한 각의 정간 수")
parser.add_argument("--title", default=None, help="출력 파일 이름. 기본은 PDF 이름")
parser.add_argument("--gap-fill", type=int, default=0, help="칸 찾기 전에 세로로 메울 선 틈(px). 0이면 보정 없음")
parser.add_argument("--line-threshold", type=int, default=240, help="이보다 어두운 픽셀을 선으로 본다. 리더 기본 240")
args = parser.parse_args()

pdf, out, omr_dir = args.pdf.resolve(), args.out.resolve(), args.omr_dir.resolve()
out.mkdir(parents=True, exist_ok=True)
sys.path.insert(0, str(omr_dir))
os.chdir(omr_dir)  # OMR 기본 경로(checkpoints/best/...)가 저장소 기준 상대 경로라서
from jngbomr import JeongganboReader, INFERENCER_DEFAULT_KWARGS

# 공개 체크포인트는 옛 x-transformers의 LayerNorm 이름(gamma·beta)으로 저장돼 있다.
# beta는 모두 0이라 gamma를 weight로 바꾸고 beta를 버리면 같은 계산이 된다.
ckpt = Path("checkpoints/best/model.pt")
converted = ckpt.with_name("model_weight-names.pt")
if not converted.exists():
    saved = torch.load(ckpt, map_location="cpu")
    state = saved["model"]
    assert all(state[k].abs().max() == 0 for k in state if k.endswith(".beta")), "beta가 0이 아님"
    saved["model"] = {(k[:-6] + ".weight" if k.endswith(".gamma") else k): v
                      for k, v in state.items() if not k.endswith(".beta")}
    torch.save(saved, converted)

png = out / "page.png"
with pdfplumber.open(pdf) as doc:
    doc.pages[args.page].to_image(resolution=args.dpi).save(str(png))

reader = JeongganboReader(run_omr=True, inferencer_kwargs=dict(
    INFERENCER_DEFAULT_KWARGS, device="cpu", model_weights=str(converted)))
if args.gap_fill or args.line_threshold != 240:
    # 리더는 _process_img의 흑백 그림으로만 선·칸을 찾고, 정간 그림은 원래 그림에서 자른다.
    # 그래서 이 함수만 바꾸면 보정이 칸 찾기에만 쓰인다. 리더는 160px보다 짧은 세로 조각을 지우므로
    # 스캔에서 끊긴 세로선을 세로 방향 침식(어두운 픽셀 늘리기)으로 먼저 잇는다.
    def process_img(image):
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        if args.gap_fill:
            gray = cv2.erode(gray, np.ones((args.gap_fill, 1), np.uint8))
        return 255 - cv2.threshold(gray, args.line_threshold, 255, cv2.THRESH_BINARY)[1]
    reader._process_img = process_img
result = reader(str(png))
# 정간을 못 찾으면 (빈 쪽, False), 제목 칸 오른쪽에도 정간이 있으면 (왼쪽, 오른쪽)을 돌려준다.
if isinstance(result, tuple) and isinstance(result[1], bool):
    sys.exit(f"정간을 찾지 못함. --dpi를 바꿔 정간 너비를 85~110px에 맞춘다(지금 {args.dpi}).")
parts = [(result[0], 0), (result[1], result[0].img.shape[1])] if isinstance(result, tuple) else [(result, 0)]
cells = [dict(text=jg.omr_text, conf=round(jg.omr_confidence, 3), x=int(jg.x + dx), y=int(jg.y), w=int(jg.w), h=int(jg.h))
         for page, dx in parts for gak in page.gaks if not gak.is_jangdan for jg in gak.jeonggans]  # 장단 열은 음이 아니다

# 읽는 순서: x가 가까운 상자를 한 열로 묶고, 열은 오른쪽부터, 열 안은 위부터.
cells.sort(key=lambda c: -c["x"])
columns = []
for c in cells:
    if columns and abs(columns[-1][0]["x"] - c["x"]) < c["w"] / 2:
        columns[-1].append(c)
    else:
        columns.append([c])
# 리더가 장단 열을 못 가리는 판형이 있어, 율명이 하나도 없는 열(장단 기호만 있는 열)은 뺀다.
PITCH = re.compile(r"^(하하배|하배|중청|배|청)?[황대태협고중유임이남무응]$")
def has_pitch(c):
    return any(PITCH.match(t.rsplit(":", 1)[0].split("_")[0]) for t in c["text"].split())
columns = [col for col in columns if any(has_pitch(c) for c in col)]
ordered = [c for col in columns for c in sorted(col, key=lambda c: c["y"])]

def jg_like(text):
    """OMR 원문 '율명:위치 …'를 데이터셋 표기 ':위치 율명 …'로 바꾼다.
    정간 가운데 음 하나(:5)는 데이터셋에서 정간 전체(:0)로 적는다.
    모델은 시김새를 '청무_흘림표'처럼 율명에 밑줄로 붙여 낸다. 데이터셋처럼 '청무 흘림표'로 나눠야
    변환 도구가 율명을 알아보고 시김새는 기호로 붙인다(붙인 채로 두면 율명까지 모르는 기호가 된다)."""
    toks = [t.rsplit(":", 1) for t in text.split()]
    if len(toks) == 1 and toks[0][1] == "5":
        toks = [[toks[0][0], "0"]]
    return " ".join(f":{pos} {name.replace('_', ' ')}" for name, pos in toks)

lines = [" | ".join(jg_like(c["text"]) for c in ordered[i:i + args.beats])
         for i in range(0, len(ordered), args.beats)]
title = args.title or pdf.stem
(out / f"{title}.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
(out / "cells.json").write_text(json.dumps(ordered, ensure_ascii=False, indent=1), encoding="utf-8")

img = cv2.imread(str(png))
for i, c in enumerate(ordered):
    cv2.rectangle(img, (c["x"], c["y"]), (c["x"] + c["w"], c["y"] + c["h"]), (0, 0, 255), 2)
    cv2.putText(img, str(i), (c["x"] + 3, c["y"] + 18), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 0, 0), 2)
cv2.imwrite(str(out / "boxes.png"), img)
print(f"정간 {len(ordered)}개, 열 {len(columns)}개, 각 {len(lines)}개 → {out / (title + '.txt')}")
