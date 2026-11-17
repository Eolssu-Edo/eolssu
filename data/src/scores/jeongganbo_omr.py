"""정간보 악보 PDF 한 쪽을 MALerLab/jeongganbo-omr로 읽어 JG-like 텍스트로 쓴다.

실행 (OMR 저장소의 가상 환경 Python으로):
  <omr>/.venv/Scripts/python.exe data/src/scores/jeongganbo_omr.py <pdf> <쪽(0부터)> <출력 폴더> \
      --omr-dir <omr> --dpi 150 --beats 3

출력
  <제목>.txt       JG-like 텍스트. 한 줄이 각, 정간은 '|', 정간 안은 ':위치 율명'.
                   jeongganbo-generator의 tools/import-ngc-omr.mjs가 그대로 읽는다.
  page.png         렌더링한 쪽
  boxes.png        잘라 낸 정간 상자와 읽는 순서
  cells.json       정간마다 OMR 원문·JG-like 결과·각 번호·신뢰도·상자. 검토 화면(review_page.py)이 읽는다.

전제
  * 세로쓰기 정간보: 열은 오른쪽에서 왼쪽으로, 열 안은 위에서 아래로 읽는다.
  * 리더의 정간 크기 기준(너비 85~110px)은 국립국악원 정악보 300dpi에 맞춰져 있다.
    다른 악보는 정간 너비가 이 범위에 들도록 --dpi를 고른다(e-국악아카데미 교실 악보는 150).
  * 리더가 각을 나누지 못하는 쪽이 있어 각은 --beats(한 각의 정간 수)로 다시 나눈다.
  * 세로선이 연하고 끊긴 스캔은 --gap-fill 10 --line-threshold 250으로 칸 찾기 그림만 보정한다.
    율명 인식은 보정하지 않은 원래 그림을 잘라서 한다.
  * --fill-grid는 격자 줄 위치로 두 줄 높이로 합쳐진 상자를 나누고 놓친 정간을 채워 다시 읽는다(jeongganbo_grid.py).
    칸 하나의 오류가 뒤 각 구분으로 번지지 않게 한다. 나눈 칸은 split, 채운 칸은 filled를 cells.json에 남긴다.
  * --yul은 모델이 고를 수 있는 율명을 악기 음역이나 곡의 율명으로 좁힌다. 모델의 해독 함수를 바꾸므로 격자 검출·
    --fill-grid·--grid-boxes·--batch-size의 모든 읽기에 적용된다. 신뢰도는 제한 전 분포에서 고른 토큰의 확률이다.
    --read-dpi는 칸은 --dpi에서 찾고 율명은 더 높은 해상도로 다시 그린 쪽에서 잘라 모든 정간을 다시 읽는다.
  * --octave-check는 모델이 낸 청태·청황·중청황을 300dpi 그림의 글자 모양으로 다시 가려 중청태·중청황을 고친다
    (octave_check.py, 평균 모양 파일은 그 스크립트로 만든다).
  * --side-marks는 율명 오른쪽의 작은 시김새 획(물결선·`)`·`^`·`И`)을 모양으로 찾아 토큰에 붙이거나, 모델이 물결선을
    정악보 이름으로 읽은 것을 고친다(side_marks.py). 바뀐 정간은 cells.json에 modelText·sideMarks를 남긴다.
"""
import argparse
import json
import os
import re
import sys
import shutil
from pathlib import Path
from jeongganbo_grid import fit_columns
from jeongganbo_tokens import jg_like
from octave_check import recover_water_register
from side_marks import mark_cell, unify_short_waves

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
parser.add_argument("--image", action="store_true", help="렌더링된 쪽 그림을 입력한다. page는 0이다")
parser.add_argument("--model-cache-dir", type=Path, help="변환한 체크포인트의 요청별 쓰기 경로")
parser.add_argument("--batch-size", type=int, help="CPU 추론 배치 상한")
parser.add_argument("--keep-empty-columns", action="store_true", help="율명을 못 읽은 격자 열도 보존한다")
parser.add_argument("--grid-boxes", action="store_true", help="얇은 선과 여러 단은 기존 기하 격자 검출기로 읽는다")
parser.add_argument("--grid-address", action="store_true",
                    help="선으로 찾은 칸 주소(단·열·줄)와 칸 위 선 굵기를 내고 각은 나누지 않는다(S15P31E102-1077)")
parser.add_argument("--gap-fill", type=int, default=0, help="칸 찾기 전에 세로로 메울 선 틈(px). 0이면 보정 없음")
parser.add_argument("--line-threshold", type=int, default=240, help="이보다 어두운 픽셀을 선으로 본다. 리더 기본 240")
parser.add_argument("--fill-grid", action="store_true", help="격자 줄 위치로 합쳐진 정간을 나누고 놓친 정간을 채운다")
parser.add_argument("--yul", default=None,
                    help="고를 수 있는 율명: 악기 이름(" + ", ".join("danso sogeum jeongak-daegeum".split()) + ") 또는 '중,임,무'처럼 쉼표 목록")
parser.add_argument("--read-dpi", type=int, default=None, help="율명은 이 해상도로 다시 그린 쪽에서 칸을 잘라 읽는다(예: 300)")
parser.add_argument("--octave-check", type=Path, default=None, help="청성·중청 글자 평균 모양 파일(octave_check.py로 만든 JSON)")
parser.add_argument("--side-marks", action="store_true", help="율명 오른쪽의 작은 시김새 획을 모양으로 찾아 붙인다")
args = parser.parse_args()

pdf, out, omr_dir = args.pdf.resolve(), args.out.resolve(), args.omr_dir.resolve()
model_cache = args.model_cache_dir.resolve() if args.model_cache_dir else None
out.mkdir(parents=True, exist_ok=True)
if args.octave_check:
    sys.path.insert(0, str(Path(__file__).resolve().parent))  # 아래 chdir 전에 같은 폴더의 octave_check를 찾게
    from octave_check import fix_cells
    templates = json.loads(args.octave_check.read_text(encoding="utf-8"))
sys.path.insert(0, str(omr_dir))
os.chdir(omr_dir)  # OMR 기본 경로(checkpoints/best/...)가 저장소 기준 상대 경로라서
from jngbomr import JeongganboReader, INFERENCER_DEFAULT_KWARGS

# 공개 체크포인트는 옛 x-transformers의 LayerNorm 이름(gamma·beta)으로 저장돼 있다.
# beta는 모두 0이라 gamma를 weight로 바꾸고 beta를 버리면 같은 계산이 된다.
ckpt = Path("checkpoints/best/model.pt")
converted = (model_cache / "model_weight-names.pt" if model_cache
             else ckpt.with_name("model_weight-names.pt"))
if not converted.exists():
    saved = torch.load(ckpt, map_location="cpu", weights_only=True)
    state = saved["model"]
    assert all(state[k].abs().max() == 0 for k in state if k.endswith(".beta")), "beta가 0이 아님"
    saved["model"] = {(k[:-6] + ".weight" if k.endswith(".gamma") else k): v
                      for k, v in state.items() if not k.endswith(".beta")}
    torch.save(saved, converted)

png = out / "page.png"
if args.image:
    if args.read_dpi or args.octave_check:
        parser.error("--image는 PDF 재렌더링 옵션과 함께 쓸 수 없다")
    shutil.copyfile(pdf, png)
else:
    with pdfplumber.open(pdf) as doc:
        doc.pages[args.page].to_image(resolution=args.dpi).save(str(png))

reader = JeongganboReader(run_omr=True, inferencer_kwargs=dict(
    INFERENCER_DEFAULT_KWARGS, device="cpu", model_weights=str(converted)))
if args.batch_size:
    if args.batch_size < 1:
        parser.error("--batch-size는 양수여야 한다")
    inferencer = reader.omr

    class BoundedBatches:
        def __getattr__(self, name):
            return getattr(inferencer, name)

        def __call__(self, images):
            if not isinstance(images, list):
                return inferencer(images)
            predictions, confidences = [], []
            for offset in range(0, len(images), args.batch_size):
                p, c = inferencer(images[offset:offset + args.batch_size])
                predictions.extend(p)
                confidences.append(c)
            return predictions, torch.cat(confidences)

    reader.omr = BoundedBatches()
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

# 악기별 율명 후보. 근거는 docs/knowledge/data/jeongganbo-omr-accuracy.md "악기별 율명 후보".
YUL_PRESETS = {
    # 2022 교과서 표준악보집 단소 쪽(160~185쪽)에 나오는 율명 전부
    "danso": "중 임 무 청황 청태 청중 청임 청무 중청황 중청태",
    # 같은 책 소금 쪽(186~203쪽)에 나오는 율명 전부
    "sogeum": "임 남 청황 청태 청고 청중 청임 청남 청무 중청황 중청태 중청고",
    # 국악원 정악보 91곡 대금 파트(OMR 데이터셋)에서 40번 넘게 나온 율명
    "jeongak-daegeum": "배임 배남 배무 황 태 고 중 임 남 무 이 청황 청태 청협 청고 청중 청임 청남 청무 중청황",
}
if args.yul:
    allowed = set(YUL_PRESETS[args.yul].split() if args.yul in YUL_PRESETS else args.yul.split(","))
    tok = reader.omr.tokenizer
    yul_re = re.compile(r"^(하하배|하배|중청|배|청)?[황대태협고중유임이남무응]$")
    missing = sorted(allowed - set(tok.vocab))
    if missing:
        # 모델 어휘에 중청 계열은 중청황만 있다. 없는 율명은 모델이 고를 수 없으니 알리고 넘어간다.
        print(f"모델 어휘에 없는 율명(제한과 상관없이 읽지 못함): {', '.join(missing)}")
    banned = torch.tensor([i for i, t in enumerate(tok.vocab) if yul_re.match(t) and t not in allowed])
    model = reader.omr.model

    @torch.inference_mode()  # 원래 inference처럼 기울기 그래프를 만들지 않는다
    def constrained_inference(x, max_len=None, batch=True, return_confidence=False):
        """jngbomr TransformerOMR.inference와 같은 탐욕 해독인데, 허용하지 않은 율명 토큰은 고르지 못하게 한다."""
        x = model.run_img_cnn(x)
        x += model.encoder_pos_enc(x)
        x = model.encoder(x)
        dec = torch.ones((x.shape[0], 1), dtype=torch.long)
        ended = torch.zeros((x.shape[0]), dtype=torch.bool)
        conf = torch.ones((x.shape[0]))
        for _ in range(max_len or 50):
            y = model.decoder(model.dec_embedder(dec) + model.decoder_pos_enc(dec), x)[:, -1:]
            logit = model.proj(y)
            limited = logit.clone()
            limited[..., banned] = float("-inf")
            new = torch.argmax(limited, dim=-1)
            ended = ended | (new[:, 0] == 2)
            dec = torch.cat([dec, new], dim=1)
            # 신뢰도는 제한 전 분포에서 고른 토큰의 확률이다. 남은 후보로 다시 나누면 금지된 율명을
            # 가장 높게 본 칸도 높은 신뢰도가 되어 확인 표시가 사라진다. 고른 토큰이 제한 전 최댓값이면 원래 값과 같다.
            prob = torch.softmax(logit, dim=-1)[:, 0].gather(1, new)[:, 0]
            conf = torch.where(ended, conf, conf * prob)  # 원래 코드처럼 끝 토큰을 고른 단계부터는 곱하지 않는다
            if ended.all():
                break
        return (dec, conf) if return_confidence else dec
    model.inference = constrained_inference

if args.grid_boxes or args.grid_address:
    # 기존 검토 도구의 검출기는 칸 너비 제한 없이 얇은 선·단의 순서를 보존한다.
    # 나란한 여러 악보를 임의 선택하지 않고, 단일 악보로 확인되는 경우만 읽는다.
    from collections import Counter
    from grid_boxes import find_blocks, find_grid
    image = cv2.imread(str(png))
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    if args.grid_address:
        # 각·정간 번호는 부르는 쪽이 칸 주소와 선 굵기로 정한다(grid_boxes.gaks). 칸 순번으로 각을 나누지 않는다.
        blocks = find_grid(gray, allow_alternating_lyrics=False)
        if len(blocks) != 1 or len(blocks[0]) != 1 or not blocks[0][0]:
            sys.exit(4)
        boxes = [dict(band=c['band'], col=c['col'], row=c['row'], line=int(c['line']) if c['line'] else None,
                      **{k: int(round(float(c[k]))) for k in 'xywh'}) for c in blocks[0][0]]
        beats = 0
    else:
        blocks = find_blocks(gray, allow_alternating_lyrics=False)
        if len(blocks) != 1 or len(blocks[0]) != 1 or not blocks[0][0]:
            sys.exit(4)
        boxes = [tuple(round(v) for v in box) for box in blocks[0][0]]
        groups = []
        for box in boxes:
            if not groups or abs(groups[-1][-1][0] - box[0]) >= box[2] / 2 or box[1] <= groups[-1][-1][1]:
                groups.append([])
            groups[-1].append(box)
        groups = [[dict(x=x, y=y, w=w, h=h) for x, y, w, h in group] for group in groups]
        if args.fill_grid:
            # 가로선을 놓쳐 두 줄 높이가 된 상자를 나눈다. 단마다 열이 따로라 열 안쪽만 채운다.
            fit_columns(groups, gray, inside_only=True)
        beats = args.beats or Counter(map(len, groups)).most_common(1)[0][0]
        boxes = [c for group in groups for c in group]
    if args.grid_address:
        # 칸 상자는 선 가운데에서 잘라 굵은 각 구분선의 절반이 칸 끝에 들어간다. 모델이 이를 이음표로 읽으므로
        # 리더가 선 안쪽만 자르듯 칸 크기의 4%(최소 2px)만큼 안으로 잘라 읽는다. 결과 상자는 그대로 둔다.
        inset = lambda c: max(2, round(min(c["w"], c["h"]) * .04))
        crops = [image[c["y"] + inset(c):c["y"] + c["h"] - inset(c), c["x"] + inset(c):c["x"] + c["w"] - inset(c)]
                 for c in boxes]
    else:
        crops = [image[c["y"]:c["y"] + c["h"], c["x"]:c["x"] + c["w"]] for c in boxes]
    predictions, confidences = reader.omr(crops)
    cells = []
    for i, (box, text, confidence) in enumerate(zip(boxes, predictions, confidences)):
        x, y, w, h = (box[k] for k in ('x', 'y', 'w', 'h'))
        cell = dict(text=recover_water_register(text, gray[y:y + h, x:x + w]))
        if args.side_marks:
            mark_cell(cell, gray[y:y + h, x:x + w])
        inner = gray[y + h * 15 // 100:y + h * 85 // 100, x + w * 15 // 100:x + w * 85 // 100]
        place = ({k: box[k] for k in ('band', 'col', 'row', 'line')} if args.grid_address
                 else dict(gak=i // beats, jeonggan=i % beats))
        cells.append(dict(cell, jg=jg_like(cell['text']), conf=round(float(confidence), 3),
                          x=x, y=y, w=w, h=h, **place,
                          ink=round(float((inner < 160).mean()), 4) if inner.size else 0,
                          **{k: True for k in ('split', 'filled') if box.get(k)}))
    if args.side_marks and unify_short_waves(cells):
        for cell in cells:
            cell['jg'] = jg_like(cell['text'])
    title = args.title or pdf.stem
    (out / 'cells.json').write_text(json.dumps(dict(title=title, image='page.png', beats=beats, cells=cells),
                                              ensure_ascii=False, indent=1), encoding='utf-8')
    if args.grid_address:  # 열마다 한 줄
        lines = [' | '.join(c['jg'] for c in cells if (c['band'], c['col']) == key)
                 for key in dict.fromkeys((c['band'], c['col']) for c in cells)]
    else:
        lines = [' | '.join(c['jg'] for c in cells[i:i + beats]) for i in range(0, len(cells), beats)]
    (out / f'{title}.txt').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    sys.exit(0)

result = reader(str(png))
# 정간을 못 찾으면 (빈 쪽, False), 제목 칸 오른쪽에도 정간이 있으면 (왼쪽, 오른쪽)을 돌려준다.
if isinstance(result, tuple) and isinstance(result[1], bool):
    print(f"정간을 찾지 못함. --dpi를 바꿔 정간 너비를 85~110px에 맞춘다(지금 {args.dpi}).", file=sys.stderr)
    sys.exit(4)
parts = [(result[0], 0), (result[1], result[0].img.shape[1])] if isinstance(result, tuple) else [(result, 0)]
if args.beats == 0:
    from collections import Counter
    sizes = [len(gak) for part, _ in parts for gak in part.gaks if not gak.is_jangdan]
    if not sizes:
        sys.exit(4)
    args.beats = Counter(sizes).most_common(1)[0][0]
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
if not args.keep_empty_columns:
    columns = [col for col in columns if any(has_pitch(c) for c in col)]

if args.fill_grid:
    # 정간보 격자는 규칙적이라 한 열에서 정간이 잡힌 줄은 옆 열에서도 정간이 있는 줄이다.
    # 두 줄 높이로 합쳐진 상자는 줄 위치에서 나누고, 선이 끊겨 놓친 자리는 옆 열의 줄 위치로 채워 다시 읽는다
    # (jeongganbo_grid.py). 그래야 칸 하나의 오류가 뒤 각 구분으로 번지지 않는다. 장단 열을 거른 뒤에 맞추므로
    # 장단 열 아래의 '자진모리장단'·'강원도민요' 같은 글씨는 정간이 되지 않는다.
    page_img = cv2.imread(str(png))
    added = fit_columns(columns, cv2.cvtColor(page_img, cv2.COLOR_BGR2GRAY))
    if added:
        preds, confs = reader.omr([page_img[c["y"]:c["y"] + c["h"], c["x"]:c["x"] + c["w"]] for c in added])
        for c, p, k in zip(added, preds, confs):
            c["text"], c["conf"] = p, round(float(k), 3)
    print(f"격자로 나눈 정간 {sum(1 for c in added if c.get('split'))}개, "
          f"채운 정간 {sum(1 for c in added if c.get('filled'))}개")

if args.read_dpi:
    # 칸은 정간 크기 기준에 맞춘 --dpi 그림에서 찾았고, 율명은 같은 칸을 고해상도 그림에서 잘라 다시 읽는다.
    # 모델은 칸 그림을 160×140으로 줄여 읽으므로, 작은 그림을 늘리는 것보다 큰 그림을 줄이는 편이 획이 또렷하다.
    hi_png = out / "page-read.png"
    with pdfplumber.open(pdf) as doc:
        doc.pages[args.page].to_image(resolution=args.read_dpi).save(str(hi_png))
    hi = cv2.imread(str(hi_png))
    k = args.read_dpi / args.dpi
    keep = [c for col in columns for c in col]
    crops = [hi[round(c["y"] * k):round((c["y"] + c["h"]) * k), round(c["x"] * k):round((c["x"] + c["w"]) * k)] for c in keep]
    preds, confs = reader.omr(crops)
    for c, p, q in zip(keep, preds, confs):
        c["text"], c["conf"] = p, round(float(q), 3)

ordered = [c for col in columns for c in sorted(col, key=lambda c: c["y"])]

source_image = cv2.imread(str(png), cv2.IMREAD_GRAYSCALE)
for i, c in enumerate(ordered):
    x, y, w, h = (c[k] for k in ('x', 'y', 'w', 'h'))
    c['text'] = recover_water_register(c['text'], source_image[y:y + h, x:x + w])
    if args.side_marks:
        mark_cell(c, source_image[y:y + h, x:x + w])
    c["jg"], c["gak"], c["jeonggan"] = jg_like(c["text"]), i // args.beats, i % args.beats
if args.side_marks and unify_short_waves(ordered):
    for c in ordered:
        c["jg"] = jg_like(c["text"])
if args.octave_check:
    oct_png = out / "page-read.png"
    if args.read_dpi != 300:
        with pdfplumber.open(pdf) as doc:
            doc.pages[args.page].to_image(resolution=300).save(str(oct_png))
    oct_img = cv2.imread(str(oct_png), cv2.IMREAD_GRAYSCALE)
    print(f"청성·중청 다시 가린 율명 {fix_cells(ordered, oct_img, 300 / args.dpi, templates)}개")
lines = [" | ".join(c["jg"] for c in ordered[i:i + args.beats]) for i in range(0, len(ordered), args.beats)]
title = args.title or pdf.stem
(out / f"{title}.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
# 검토 화면(review_page.py)이 읽는 형식. standard_score_text.py와 같다.
for cell in ordered:
    x, y, w, h = (cell[k] for k in ('x', 'y', 'w', 'h'))
    inner = source_image[y + h * 15 // 100:y + h * 85 // 100, x + w * 15 // 100:x + w * 85 // 100]
    cell['ink'] = round(float((inner < 160).mean()), 4) if inner.size else 0
(out / "cells.json").write_text(json.dumps(dict(title=title, image="page.png", beats=args.beats, cells=ordered),
                                           ensure_ascii=False, indent=1), encoding="utf-8")

img = cv2.imread(str(png))
for i, c in enumerate(ordered):
    cv2.rectangle(img, (c["x"], c["y"]), (c["x"] + c["w"], c["y"] + c["h"]), (0, 0, 255), 2)
    cv2.putText(img, str(i), (c["x"] + 3, c["y"] + 18), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 0, 0), 2)
cv2.imwrite(str(out / "boxes.png"), img)
print(f"정간 {len(ordered)}개, 열 {len(columns)}개, 각 {len(lines)}개 → {out / (title + '.txt')}")
