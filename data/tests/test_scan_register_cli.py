"""Actual reader CLI and glyph pixels, with only external model predictions controlled."""
import json
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


@pytest.mark.parametrize('grid', [False, True], ids=['model-grid', 'geometric-grid'])
def test_reader_writes_recovered_text_and_jg_in_both_grid_paths(tmp_path, grid):
    # Six note columns, each followed by an explicit unruled lyric column.
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
    target = tmp_path / 'output'
    command = [sys.executable, str(ROOT / 'data/src/scores/jeongganbo_omr.py'),
               str(source), '0', str(target), '--image', '--omr-dir', str(model),
               '--beats', '4', '--keep-empty-columns', '--title', 'register']
    if grid:
        command.append('--grid-boxes')
    result = subprocess.run(command, env={**os.environ, 'REGISTER_FIXTURE': str(record)},
                            capture_output=True, text=True, encoding='utf-8', timeout=20)
    assert result.returncode == 0, result.stderr
    report = json.loads((target / 'cells.json').read_text(encoding='utf-8'))
    assert len(report['cells']) == 24
    assert [c['text'] for c in report['cells']] == [c['expected'] for c in CELLS]
    assert [c['jg'] for c in report['cells']] == [':0 ' + c['expected'].split(':')[0] for c in CELLS]
    assert all(c['conf'] == .91 for c in report['cells'])
