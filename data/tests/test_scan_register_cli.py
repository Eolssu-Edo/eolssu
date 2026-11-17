"""Actual reader CLI and glyph pixels, with only external model predictions controlled."""
import json
import math
import os
from pathlib import Path
import subprocess
import sys

import cv2
import numpy as np
import pytest

ROOT = Path(__file__).resolve().parents[2]
FIXTURE = ROOT / 'data/tests/fixtures/scores/double-water'
CELLS = json.loads((FIXTURE / 'cells.json').read_text(encoding='utf-8'))


def register_page(tmp_path):
    """Six note columns, each followed by an explicit unruled lyric column."""
    # Strict grid detection must not guess that a second ruled voice is lyrics.
    original = cv2.imread(str(FIXTURE / 'register.png'), cv2.IMREAD_GRAYSCALE)
    image = np.full((440, 820), 255, np.uint8)
    predictions = []
    for i, cell in enumerate(CELLS):
        x, y, w, h = cell['box']
        left, top = 20 + (5 - i // 4) * 130, 20 + i % 4 * 100
        glyph = original[y + 3:y + h - 3, x + 3:x + w - 3]
        image[top + 4:top + 4 + glyph.shape[0], left + 4:left + 4 + glyph.shape[1]] = glyph
        predictions.append({**cell, 'box': [left, top, 100, 100]})
    for left in range(20, 800, 130):
        for x in (left, left + 100, left + 130):
            image[20:421, x] = 0
        for y in range(120, 421, 100):
            image[y, left:left + 101] = 0
    image[20, 20:801] = image[420, 20:801] = 0
    source, record = tmp_path / 'register.png', tmp_path / 'predictions.json'
    cv2.imwrite(str(source), image)
    record.write_text(json.dumps(predictions), encoding='utf-8')
    model = tmp_path / 'model'
    weights = model / 'checkpoints/best/model_weight-names.pt'
    weights.parent.mkdir(parents=True)
    weights.touch()  # The external model double does not load weights.
    return source, record, model


def run_reader(tmp_path, source, model, record, *options):
    target = tmp_path / 'output'
    command = [sys.executable, str(ROOT / 'data/src/scores/jeongganbo_omr.py'),
               str(source), '0', str(target), '--image', '--omr-dir', str(model),
               '--beats', '4', '--keep-empty-columns', '--title', 'register', *options]
    result = subprocess.run(command, env={**os.environ, 'REGISTER_FIXTURE': str(record)},
                            capture_output=True, text=True, encoding='utf-8', timeout=20)
    assert result.returncode == 0, result.stderr
    return result, json.loads((target / 'cells.json').read_text(encoding='utf-8'))


@pytest.mark.parametrize('grid', [False, True], ids=['model-grid', 'geometric-grid'])
def test_reader_writes_recovered_text_and_jg_in_both_grid_paths(tmp_path, grid):
    source, record, model = register_page(tmp_path)
    (model / 'jngbomr.py').write_text('''
import json
import cv2
import os
from pathlib import Path
from types import SimpleNamespace as Box
INFERENCER_DEFAULT_KWARGS = {}
cells = json.loads(Path(os.environ['REGISTER_FIXTURE']).read_text())
class Predictions:
    def __call__(self, images):
        return [c['modelText'] for c in cells], [.91] * len(cells)
class JeongganboReader:
    def __init__(self, **kwargs): self.omr = Predictions()
    def __call__(self, path):
        notes = [Box(x=c['box'][0], y=c['box'][1], w=c['box'][2], h=c['box'][3],
                     omr_text=c['modelText'], omr_confidence=.91) for c in cells]
        return Box(img=cv2.imread(path), gaks=[Box(is_jangdan=False, jeonggans=notes[i:i+4]) for i in range(0,len(notes),4)])
''', encoding='utf-8')
    _, report = run_reader(tmp_path, source, model, record, *(['--grid-boxes'] if grid else []))
    assert len(report['cells']) == 24
    assert [c['text'] for c in report['cells']] == [c['expected'] for c in CELLS]
    assert [c['jg'] for c in report['cells']] == [':0 ' + c['expected'].split(':')[0] for c in CELLS]
    assert all(c['conf'] == .91 for c in report['cells'])


# 제한 해독을 실제로 거치는 가짜 모델. 모든 칸에서 첫 단계는 이 > 청태 > 무 > 황 순으로 보고,
# 둘째 단계는 위치 :5, 셋째 단계는 끝 토큰을 고른다. 이·황은 단소 목록 밖이고 중청태는 어휘에 없다.
VOCAB = ['<pad>', '<s>', '</s>', '이', '청태', '무', '황', '중청황', ':5']
STEPS = {1: [0, 0, 0, 3, 2, 1, .5, 0, 0], 2: [0, 0, 0, 0, 0, 0, 0, 0, 4], 3: [0, 0, 5, 0, 0, 0, 0, 0, 0]}
DECODING_MODEL = f'''
import json
import cv2
import os
import torch
from pathlib import Path
from types import SimpleNamespace as Box
INFERENCER_DEFAULT_KWARGS = {{}}
VOCAB, STEPS = {VOCAB!r}, {STEPS!r}
cells = json.loads(Path(os.environ['REGISTER_FIXTURE']).read_text())
calls = []
class Model:
    def run_img_cnn(self, x): return torch.zeros(x.shape[0], 1, 1)
    def encoder_pos_enc(self, x): return torch.zeros_like(x)
    def encoder(self, x): return x
    def dec_embedder(self, dec): return torch.zeros(dec.shape[0], dec.shape[1], 1)
    def decoder_pos_enc(self, dec): return torch.zeros(dec.shape[0], dec.shape[1], 1)
    def decoder(self, h, x): return torch.full((h.shape[0], h.shape[1], 1), float(h.shape[1]))
    def proj(self, y): return torch.tensor(STEPS[int(y[0, 0, 0])], dtype=torch.float).expand(y.shape[0], 1, len(VOCAB)).clone()
    def inference(self, x, max_len=None, batch=True, return_confidence=False):
        dec, conf = torch.ones((x.shape[0], 1), dtype=torch.long), torch.ones(x.shape[0])
        for _ in range(3):
            logit = self.proj(self.decoder(self.dec_embedder(dec), x)[:, -1:])
            new = torch.argmax(logit, dim=-1)
            dec = torch.cat([dec, new], dim=1)
            if new[0, 0] != 2:
                conf = conf * torch.softmax(logit, dim=-1)[:, 0].max(dim=1)[0]
        return (dec, conf) if return_confidence else dec
class Inferencer:
    tokenizer = Box(vocab=VOCAB)
    def __init__(self): self.model = Model()
    def __call__(self, images):
        calls.append(len(images))
        dec, conf = self.model.inference(torch.zeros(len(images), 1), return_confidence=True)
        texts = [''.join(VOCAB[t] for t in row[1:] if t != 2) for row in dec.tolist()]
        return texts, conf
class JeongganboReader:
    def __init__(self, **kwargs): self.omr = Inferencer()
    def __call__(self, path):
        image = cv2.imread(path)
        boxes = [c['box'] for c in cells]
        texts, confs = self.omr([image[y:y + h, x:x + w] for x, y, w, h in boxes])
        Path(os.environ['REGISTER_FIXTURE']).with_name('batches.json').write_text(json.dumps(calls))
        notes = [Box(x=x, y=y, w=w, h=h, omr_text=t, omr_confidence=float(k))
                 for (x, y, w, h), t, k in zip(boxes, texts, confs)]
        return Box(img=image, gaks=[Box(is_jangdan=False, jeonggans=notes[i:i+4]) for i in range(0,len(notes),4)])
'''


def softmax(row, index):
    return math.exp(row[index]) / sum(math.exp(v) for v in row)


@pytest.mark.parametrize('grid', [False, True], ids=['model-grid', 'geometric-grid'])
def test_instrument_pitch_limit_reaches_bounded_batches_and_keeps_unlimited_confidence(tmp_path, grid):
    source, record, model = register_page(tmp_path)
    (model / 'jngbomr.py').write_text(DECODING_MODEL, encoding='utf-8')
    grid_options = ['--grid-boxes'] if grid else []

    _, plain = run_reader(tmp_path, source, model, record, '--batch-size', '5', *grid_options)
    assert {c['text'] for c in plain['cells']} == {'이:5'}  # 목록이 없으면 모델의 첫 후보 그대로다
    top = round(softmax(STEPS[1], 3) * softmax(STEPS[2], 8), 3)
    assert {c['conf'] for c in plain['cells']} == {top}

    result, limited = run_reader(tmp_path, source, model, record, '--batch-size', '5', '--yul', 'danso', *grid_options)
    notice = next(line for line in result.stdout.splitlines() if line.startswith('모델 어휘에 없는 율명'))
    assert '중청태' in notice.split(': ', 1)[1].split(', ')
    # 단소 목록 밖의 이 대신 청태를 고른다. 중청태는 모델이 내지 못하고, 글자에 물수변이 둘이면 뒤 단계에서 올린다.
    expected = ['중청태:5' if c['expected'].startswith('중청') else '청태:5' for c in CELLS]
    assert [c['text'] for c in limited['cells']] == expected
    assert any(t == '중청태:5' for t in expected) and any(t == '청태:5' for t in expected)
    # 신뢰도는 제한 전 분포에서 청태의 확률이라 확인 기준 0.8보다 낮게 남는다.
    assert {c['conf'] for c in limited['cells']} == {round(softmax(STEPS[1], 4) * softmax(STEPS[2], 8), 3)}
    assert all(c['conf'] < top < .8 for c in limited['cells'])
    if not grid:
        assert json.loads((tmp_path / 'batches.json').read_text()) == [5, 5, 5, 5, 4]
