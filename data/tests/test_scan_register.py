"""An OMR token keeps its pitch and position; two written water radicals restore register."""
import json
from pathlib import Path
import sys

import cv2
import numpy as np
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'src/scores'))
from octave_check import recover_water_register

FIXTURE = Path(__file__).parent / 'fixtures/scores/double-water'
CELLS = json.loads((FIXTURE / 'cells.json').read_text(encoding='utf-8'))
PAGE = cv2.imread(str(FIXTURE / 'register.png'), cv2.IMREAD_GRAYSCALE)


def picture(cell):
    x, y, w, h = cell['box']
    return PAGE[y:y + h, x:x + w]


@pytest.mark.parametrize('cell', CELLS, ids=[str(i + 1) for i in range(len(CELLS))])
def test_written_register_survives_missing_model_vocabulary(cell):
    assert recover_water_register(cell['modelText'], picture(cell)) == cell['expected']


@pytest.mark.parametrize('scale', [.65, 1, 1.5, 2])
def test_same_register_survives_size_and_jpeg_loss(scale):
    for cell in CELLS:
        image = cv2.resize(picture(cell), None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
        ok, encoded = cv2.imencode('.jpg', image, [cv2.IMWRITE_JPEG_QUALITY, 65])
        assert ok
        decoded = cv2.imdecode(encoded, cv2.IMREAD_GRAYSCALE)
        assert recover_water_register(cell['modelText'], decoded) == cell['expected']


def test_each_written_note_keeps_its_slot_and_ornament():
    upper, lower = picture(CELLS[6]), picture(CELLS[9])
    canvas = np.vstack([upper, lower])
    assert recover_water_register('청태_농음표:10 청중:11', canvas) == '중청태_농음표:10 청중:11'


@pytest.mark.parametrize('text', ['쉼표:5', '-:5', '니나*:5', '청태:16', '중청태:5', '청태', ''])
def test_non_notes_and_already_double_register_are_not_guessed(text):
    assert recover_water_register(text, picture(CELLS[6])) == text


def test_missing_or_clipped_ink_does_not_promote_a_note():
    assert recover_water_register('청태:5', np.empty((0, 0), np.uint8)) == '청태:5'
    assert recover_water_register('청태:5', np.full((60, 60), 255, np.uint8)) == '청태:5'
    image = picture(CELLS[6])
    # Outer radical removed: the remaining single radical cannot prove a double one.
    assert recover_water_register('청태:5', image[:, 41:]) == '청태:5'
