"""정간보 사진 기울기·원근·조명 보정(page_rectify)과 스캔 엔진 연결 검사. 모델 없이 그림 계산만 본다."""
import io
import json
import os
import sys
from pathlib import Path

import numpy as np
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from omr_service import api, page_rectify
from grid_boxes import find_blocks  # omr_service가 data/src/scores를 경로에 넣는다

ROOT = Path(__file__).resolve().parents[3]
REGISTER = ROOT / 'data/tests/fixtures/scores/double-water/register.png'  # 팀이 만든 정간보 격자 24칸


def photo(rot=0., persp=0., shade=False):
    """격자 그림에 여백을 붙이고 회전·원근(위 변을 persp만큼 줄임)·그늘을 준 사진과, 사진 → 평평한 그림 투영."""
    base = Image.open(REGISTER).convert('RGB')
    pad = 110
    flat = Image.new('RGB', (base.width + 2 * pad, base.height + 2 * pad), 'white')
    flat.paste(base, (pad, pad))
    w, h = flat.size
    corners = np.array([[0, 0], [w, 0], [w, h], [0, h]], float)
    moved = corners + np.array([[persp * w / 2, 0], [-persp * w / 2, 0], [0, 0], [0, 0]])
    a = np.radians(rot)
    turn = np.array([[np.cos(a), -np.sin(a)], [np.sin(a), np.cos(a)]])
    moved = (moved - [w / 2, h / 2]) @ turn.T + [w / 2, h / 2]
    to_flat = page_rectify._homography(moved, corners)
    image = flat.transform((w, h), Image.Transform.PERSPECTIVE, tuple((to_flat / to_flat[2, 2]).ravel()[:8]),
                           Image.Resampling.BICUBIC, fillcolor=(120, 110, 100))
    if shade:
        light = .62 + .25 * np.linspace(0, 1, w)[None, :, None] + .1 * np.linspace(0, 1, h)[:, None, None]
        image = Image.fromarray(np.clip(np.asarray(image) * light, 0, 255).astype(np.uint8))
    return image, to_flat, flat, pad, base.size


def boxes(path):
    blocks = find_blocks(np.asarray(Image.open(path).convert('L')), allow_alternating_lyrics=False)
    return sum(len(part) for block in blocks for part in block)


def residual_angle(result, to_flat, pad, size):
    """편 그림의 세로·가로선이 평평한 원래 그림에서 얼마나 기우는지(°). 0이면 완전히 펴졌다."""
    whole = to_flat @ np.array(result.matrix)
    def apply(m, points):
        p = np.c_[points, np.ones(len(points))] @ m.T
        return p[:, :2] / p[:, 2:]
    grid = np.array([[pad, pad], [pad + size[0], pad], [pad + size[0], pad + size[1]], [pad, pad + size[1]]], float)
    (x0, y0), (x1, y1) = (lambda q: (q.min(0), q.max(0)))(apply(np.linalg.inv(whole), grid))
    worst = 0
    for t in np.linspace(0, 1, 5):
        col = apply(whole, np.array([[x0 + t * (x1 - x0), y] for y in np.linspace(y0, y1, 20)]))
        row = apply(whole, np.array([[x, y0 + t * (y1 - y0)] for x in np.linspace(x0, x1, 20)]))
        worst = max(worst, np.degrees(np.arctan2(np.ptp(col[:, 0]), np.ptp(col[:, 1]))),
                    np.degrees(np.arctan2(np.ptp(row[:, 1]), np.ptp(row[:, 0]))))
    return worst


def test_straight_white_page_is_passed_through_unchanged(tmp_path):
    image, *_ = photo()
    image.save(tmp_path / 'page.png')
    result = page_rectify.rectify(tmp_path / 'page.png', tmp_path / 'out.png')
    assert result.path == tmp_path / 'page.png' and not result.changed
    assert not (tmp_path / 'out.png').exists()
    assert result.to_source([10, 20, 31, 41], .5) == [20, 40, 62, 82]  # 기존 /scale 복원과 같다


@pytest.mark.parametrize('rot,persp', [(2, 0), (-5, 0), (8, 0), (0, .12), (3, .06), (-2, .12)])
def test_tilted_or_perspective_photo_is_flattened_until_grid_boxes_find_every_box(tmp_path, rot, persp):
    image, to_flat, flat, pad, size = photo(rot, persp)
    flat.save(tmp_path / 'flat.png')
    image.save(tmp_path / 'photo.jpg', quality=85)
    expected = boxes(tmp_path / 'flat.png')
    assert expected == 20
    assert boxes(tmp_path / 'photo.jpg') < expected  # 보정 전에는 격자를 다 찾지 못한다
    result = page_rectify.rectify(tmp_path / 'photo.jpg', tmp_path / 'out.png')
    assert result.matrix is not None and result.path == tmp_path / 'out.png'
    assert residual_angle(result, to_flat, pad, size) < .1
    assert boxes(result.path) == expected


def test_shaded_paper_is_brightened_without_moving_pixels(tmp_path):
    image, *_ = photo(shade=True)
    image.save(tmp_path / 'photo.png')
    assert boxes(tmp_path / 'photo.png') == 0  # 바탕이 grid_boxes 임계(215)보다 어둡다
    result = page_rectify.rectify(tmp_path / 'photo.png', tmp_path / 'out.png')
    assert result.flattened and result.matrix is None
    assert (result.width, result.height) == image.size
    assert result.to_source([10, 20, 30, 40]) == [10, 20, 30, 40]
    assert np.median(np.asarray(Image.open(result.path).convert('L'))) >= 245
    assert boxes(result.path) == 20


def test_slight_tilt_below_reader_tolerance_is_left_alone(tmp_path):
    image, *_ = photo(rot=.3)
    image.save(tmp_path / 'photo.png')
    assert not page_rectify.rectify(tmp_path / 'photo.png', tmp_path / 'out.png').changed


@pytest.mark.parametrize('make', [
    lambda: Image.new('RGB', (1, 1), 'white'),
    lambda: Image.new('RGB', (400, 300), 'white'),
    lambda: Image.fromarray(np.random.default_rng(0).integers(230, 256, (300, 400, 3), dtype=np.uint8)),
])
def test_pages_without_lines_are_passed_through(tmp_path, make):
    make().save(tmp_path / 'page.png')
    assert not page_rectify.rectify(tmp_path / 'page.png', tmp_path / 'out.png').changed


def test_box_is_mapped_back_as_bounding_box_of_projected_corners_and_clipped():
    turn = np.radians(10)
    # 편 그림 → 원래 그림: 10° 회전 뒤 (50, -20) 이동
    matrix = ((np.cos(turn), -np.sin(turn), 50.), (np.sin(turn), np.cos(turn), -20.), (0., 0., 1.))
    result = page_rectify.Rectified(Path('x.png'), 500, 400, (300, 300), matrix=matrix)
    x, y, w, h = 100, 100, 40, 20
    corners = np.array([[x, y], [x + w, y], [x + w, y + h], [x, y + h]], float)
    moved = corners @ np.array(matrix)[:2, :2].T + [50, -20]
    expected = [round(moved[:, 0].min()), round(moved[:, 1].min())]
    expected += [round(moved[:, 0].max()) - expected[0], round(moved[:, 1].max()) - expected[1]]
    got = result.to_source([2 * x, 2 * y, 2 * w, 2 * h], scale=2)
    assert all(abs(g - e) <= 1 for g, e in zip(got, expected))
    assert result.to_source([0, 0, 2000, 2000]) == [0, 0, 300, 300]  # 원래 쪽 밖은 자른다


def _post(png, instrument='SOGEUM'):
    api.app.dependency_overrides[api.get_fetcher] = lambda: lambda url, p, limit, deadline: p.write_bytes(png)
    try:
        return TestClient(api.app).post('/v1/recognitions', json=dict(
            conversionId='rectify', notation='JEONGGANBO', jangdan='4박 기본 반주', instrument=instrument,
            files=[dict(url='https://local.invalid/page.png', contentType='image/png')])).json()
    finally:
        api.app.dependency_overrides.clear()


def _png(image):
    buf = io.BytesIO()
    image.save(buf, 'PNG')
    return buf.getvalue()


def test_straight_page_reaches_reader_as_original_file(tmp_path, monkeypatch):
    from omr_service import jeongganbo_scan as scan
    calls = tmp_path / 'calls.jsonl'
    script = tmp_path / 'reader.py'
    script.write_text(f'''import json,sys
open({str(calls)!r},'a',encoding='utf-8').write(json.dumps(sys.argv[1])+'\\n')
sys.exit(4)
''', encoding='utf-8')
    monkeypatch.setattr(scan, 'READER', [sys.executable, str(script)])
    monkeypatch.setenv('JEONGGANBO_OMR_DIR', str(tmp_path))
    image, *_ = photo()
    assert _post(_png(image))['failReason'] == 'NO_GRID_FOUND'
    images = [Path(json.loads(line)).name for line in calls.read_text(encoding='utf-8').splitlines()]
    assert images[0] == 'source-0' and not any('rectified' in name for name in images)
    assert len(images) == 4  # 보정이 없으면 원래 재시도 4번만 한다


def test_rectify_error_falls_back_to_original_reading(tmp_path, monkeypatch):
    from omr_service import jeongganbo_scan as scan
    calls = tmp_path / 'calls.jsonl'
    script = tmp_path / 'reader.py'
    script.write_text(f'''import json,sys
open({str(calls)!r},'a',encoding='utf-8').write(json.dumps(sys.argv[1])+'\\n')
sys.exit(4)
''', encoding='utf-8')
    monkeypatch.setattr(scan, 'READER', [sys.executable, str(script)])
    monkeypatch.setenv('JEONGGANBO_OMR_DIR', str(tmp_path))

    def broken(path, out):
        raise MemoryError()
    monkeypatch.setattr(page_rectify, 'rectify', broken)
    image, *_ = photo(rot=4)
    assert _post(_png(image))['failReason'] == 'NO_GRID_FOUND'
    images = [Path(json.loads(line)).name for line in calls.read_text(encoding='utf-8').splitlines()]
    assert images[0] == 'source-0' and len(images) == 4


def test_tilted_page_reads_rectified_image_then_falls_back_to_original(tmp_path, monkeypatch):
    from omr_service import jeongganbo_scan as scan
    calls = tmp_path / 'calls.jsonl'
    script = tmp_path / 'reader.py'
    script.write_text(f'''import json,sys
open({str(calls)!r},'a',encoding='utf-8').write(json.dumps(sys.argv[1])+'\\n')
sys.exit(4)
''', encoding='utf-8')
    monkeypatch.setattr(scan, 'READER', [sys.executable, str(script)])
    monkeypatch.setenv('JEONGGANBO_OMR_DIR', str(tmp_path))
    image, *_ = photo(rot=4)
    assert _post(_png(image))['failReason'] == 'NO_GRID_FOUND'
    images = [Path(json.loads(line)).name for line in calls.read_text(encoding='utf-8').splitlines()]
    assert images[0] == 'rectified-0.png'
    assert 'source-0' in images  # 편 그림에서 못 찾으면 원래 그림으로 기존 재시도를 한다


@pytest.mark.skipif(not os.getenv('JGB_GENERATOR_DIR'), reason='Node 변환기 필요')
def test_rectified_cell_boxes_are_returned_in_original_photo_coordinates(tmp_path, monkeypatch):
    from omr_service import jeongganbo_scan as scan
    script = tmp_path / 'reader.py'
    # 편 그림에서만 격자를 찾는 리더. 칸 상자는 편 그림 좌표로 낸다.
    script.write_text('''import json,sys
from pathlib import Path
if 'rectified' not in Path(sys.argv[1]).name:
    sys.exit(4)
out=Path(sys.argv[3]);out.mkdir(parents=True,exist_ok=True)
cells=[dict(text='청태:5',conf=.2,ink=.1,x=300,y=200,w=100,h=100) for _ in range(8)]
(out/'cells.json').write_text(json.dumps(dict(beats=4,cells=cells)))
''', encoding='utf-8')
    monkeypatch.setattr(scan, 'READER', [sys.executable, str(script)])
    monkeypatch.setattr(scan, 'image_marks', lambda image, cells: [])  # 한배 표시 검출은 opencv가 필요하다
    monkeypatch.setenv('JEONGGANBO_OMR_DIR', str(tmp_path))
    image, *_ = photo(rot=4)
    png = _png(image)
    (tmp_path / 'photo.png').write_bytes(png)
    expected = page_rectify.rectify(tmp_path / 'photo.png', tmp_path / 'same.png').to_source([300, 200, 100, 100])
    out = _post(png)
    assert out['status'] == 'DONE', out
    assert out['pages'] == [{'index': 0, 'width': image.width, 'height': image.height}]
    assert out['engineVersion'] == scan.ENGINE_VERSION
    assert out['issues'][0]['bbox'] == expected != [300, 200, 100, 100]
