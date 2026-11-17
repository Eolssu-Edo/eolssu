"""정간보 사진의 기울기·원근·조명 보정(S15P31E102-1059).

MALerLab 리더는 240 고정 임계로 이진화한 뒤 70px 1차원 열림 연산과 5px 간격 묶음으로 선·칸을 찾고,
grid_boxes는 한 x 열에서 60px 넘게 이어진 세로선을 찾는다. 둘 다 축에 맞은 선과 흰 종이를 전제하므로
기울어진 사진·원근·그늘진 종이에서는 격자를 놓친다. 여기서는 격자선 네 개로 쪽을 펴고, 종이가 어두우면
밝기를 고른 뒤 리더에 넘긴다. 보정이 필요 없는 그림은 원래 파일을 그대로 돌려줘 결과가 바뀌지 않는다.

opencv는 scanner 단계에만 있어(기본 runtime·단위 테스트 환경에 없음) numpy와 Pillow만 쓴다.
"""
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image

ESTIMATE_SIDE = 1200  # 추정용 축소본 긴 변의 상한(px)
TILE = 32  # 종이 밝기를 재는 축소본 칸(px)
MIN_ANGLE = .5  # 경계선이 이보다 덜 기울면 펴지 않는다(°). 리더 허용치(약 1.5°, 코드 분석)보다 작게 잡았다
MAX_ANGLE = 15  # 이보다 기운 추정은 믿지 않는다
MIN_SIDE_RATIO = .6  # 마주 보는 변 길이 비가 이보다 작으면(지나친 원근) 믿지 않는다
INK = .8  # 종이 밝기 대비 이보다 어두우면 선 후보다
THIN = 4  # 축소본에서 선 굵기 상한의 절반(px)
TREND = .2  # 기울기 추세에서 이만큼(°) 벗어난 선은 격자선이 아니다
DARK_PAPER = 215  # 종이가 이보다 어두우면 grid_boxes(215)·리더(240) 모두 바탕을 먹으로 본다
READER_PAPER = 242  # 종이가 리더 임계(240) 근처이고 고르지 않으면 밝기를 고른다
UNEVEN = 25  # 종이 밝기 10~90번째 백분위 차가 이보다 크면 그늘이 졌다고 본다
WHITE = .9  # 밝기를 고를 때 종이 밝기의 이 비율 이상은 흰색(255)으로 둔다
MARGIN = 1.  # 편 격자 둘레에 남길 여백(격자 크기 대비). 일부 격자만 잡혀도 나머지 내용이 잘리지 않게 넓게 둔다


@dataclass(frozen=True)
class Rectified:
    """리더에 넘길 쪽 그림. matrix는 이 그림 좌표 → 원래 쪽 그림 좌표의 3×3 투영이며 None이면 같은 좌표다."""
    path: Path
    width: int
    height: int
    source_size: tuple[int, int]
    matrix: tuple[tuple[float, ...], ...] | None = None
    angle: float = 0.  # 펴기 전 경계선의 최대 기울기(°)
    flattened: bool = False

    @property
    def changed(self) -> bool:
        return self.matrix is not None or self.flattened

    def to_source(self, box, scale=1) -> list[int]:
        """리더 그림(배율 scale)의 (x, y, w, h) → 원래 쪽 그림의 상자. 펴진 상자는 네 모서리의 외접 상자다."""
        if self.matrix is None:
            return [round(v / scale) for v in box]
        x, y, w, h = (v / scale for v in box)
        m = np.array(self.matrix)
        corners = np.array([[x, y, 1], [x + w, y, 1], [x + w, y + h, 1], [x, y + h, 1]]) @ m.T
        xs, ys = corners[:, 0] / corners[:, 2], corners[:, 1] / corners[:, 2]
        sw, sh = self.source_size
        x0, x1 = np.clip([xs.min(), xs.max()], 0, sw)
        y0, y1 = np.clip([ys.min(), ys.max()], 0, sh)
        return [round(x0), round(y0), round(x1 - x0), round(y1 - y0)]


def rectify(path: Path, out: Path) -> Rectified:
    """쪽 그림을 펴고 밝기를 고른 그림을 out에 쓴다. 보정이 필요 없거나 추정을 믿을 수 없으면 원래 파일을 그대로 돌려준다."""
    with Image.open(path) as image:
        image.load()
        size = image.size
        same = Rectified(path, *size, source_size=size)
        if min(size) < 64:
            return same
        mean, low, k = _small(image)
        background = _paper(mean)
        flatten = _needs_flatten(background)
        geometry = _geometry(low, background, k, size)
        if geometry is None and not flatten:
            return same
        work = image.convert('RGB')
    if flatten:
        work = _flatten(work, background, k)
    matrix, angle, canvas = geometry or (None, 0., size)
    if matrix is not None:
        coeffs = tuple((matrix / matrix[2, 2]).ravel()[:8])
        work = work.transform(canvas, Image.Transform.PERSPECTIVE, coeffs, Image.Resampling.BICUBIC,
                              fillcolor=(255, 255, 255))
    work.save(out, compress_level=1)
    return Rectified(out, *work.size, source_size=size,
                     matrix=None if matrix is None else tuple(map(tuple, (matrix / matrix[2, 2]).tolist())),
                     angle=angle, flattened=flatten)


def _small(image):
    """긴 변 ESTIMATE_SIDE 이하의 흑백 축소본 두 개. 평균본은 종이 밝기, 최솟값본은 얇은 선을 지킨다."""
    gray = np.asarray(image.convert('L'))
    k = max(1, -(-max(gray.shape) // ESTIMATE_SIDE))
    h, w = gray.shape[0] // k * k, gray.shape[1] // k * k
    blocks = gray[:h, :w].reshape(h // k, k, w // k, k)
    return blocks.mean(axis=(1, 3), dtype=np.float32), blocks.min(axis=(1, 3)).astype(np.float32), k


def _paper(mean):
    """칸(TILE)마다 90번째 백분위 밝기. 글자가 많은 칸은 이웃 칸의 밝은 값으로 메운다."""
    h, w = mean.shape
    th, tw = -(-h // TILE), -(-w // TILE)
    padded = np.pad(mean, ((0, th * TILE - h), (0, tw * TILE - w)), mode='edge')
    tiles = np.percentile(padded.reshape(th, TILE, tw, TILE), 90, axis=(1, 3))
    edged = np.pad(tiles, 1, mode='edge')
    return np.max([edged[1 + dy:1 + dy + th, 1 + dx:1 + dx + tw] for dy in (-1, 0, 1) for dx in (-1, 0, 1)], axis=0)


def _needs_flatten(background):
    p10, p90 = np.percentile(background, [10, 90])
    return bool(p10 < DARK_PAPER or (p10 < READER_PAPER and p90 - p10 > UNEVEN))


def _upsample(background, size):
    return np.asarray(Image.fromarray(background.astype(np.float32), 'F').resize(size, Image.Resampling.BILINEAR))


def _flatten(image, background, k):
    """종이 밝기로 나눠 바탕을 흰색으로 만든다. 메모리를 아끼려고 512줄씩 처리한다.

    종이 밝기(90번째 백분위)로만 나누면 그보다 어두운 종이 잡음이 240 아래 회색으로 남아, 모델이 칸 위아래에
    없는 이음표를 읽었다(register 그늘 사진 9/24). 종이 밝기의 WHITE배 이상은 흰색으로 올린다."""
    w, h = image.size
    tiles = background.shape
    # 칸 하나는 원래 그림의 TILE*k 픽셀이다. 칸 중심 사이를 보간하도록 칸 격자 크기 그대로 늘린다.
    paper = _upsample(background, (min(w, tiles[1] * TILE * k), min(h, tiles[0] * TILE * k)))
    if paper.shape != (h, w):
        paper = np.pad(paper, ((0, h - paper.shape[0]), (0, w - paper.shape[1])), mode='edge')
    rgb = np.asarray(image)
    out = np.empty_like(rgb)
    for top in range(0, h, 512):
        scale = 255 / (np.maximum(paper[top:top + 512], 1) * WHITE)[..., None]
        out[top:top + 512] = np.clip(rgb[top:top + 512] * scale, 0, 255).astype(np.uint8)
    return Image.fromarray(out, 'RGB')


def _geometry(low, background, k, size):
    """격자선으로 (이 그림 → 원래 그림 투영, 최대 기울기, 캔버스 크기)를 구한다. 펼 필요가 없거나 못 믿으면 None."""
    verticals, horizontals = _grid_lines(low, background)
    w, h = size
    if len(verticals) >= 2 and len(horizontals) >= 2:
        left, right = verticals[0], verticals[-1]
        top, bottom = horizontals[0], horizontals[-1]
        angle = max(abs(line[1]) for line in (left, right, top, bottom))
        if angle < MIN_ANGLE or angle > MAX_ANGLE:
            return None
        quad = np.array([_cross(v, hz) for hz, v in ((top, left), (top, right), (bottom, right), (bottom, left))]) * k
        target = _target(quad, w, h)
        if target is None:
            return None
        dst, canvas_box = target
        try:
            to_flat = _homography(quad, dst)
        except np.linalg.LinAlgError:
            return None
    else:
        family = verticals or horizontals
        if not family:
            return None
        # 한쪽 선만 믿을 수 있으면 회전만 바로잡는다. 가로선 기울기는 세로선과 부호가 반대다.
        angle = float(np.median([line[1] for line in family]))
        if not MIN_ANGLE <= abs(angle) <= MAX_ANGLE:
            return None
        a = np.radians(angle if verticals else -angle)
        to_flat = np.array([[np.cos(a), -np.sin(a), 0], [np.sin(a), np.cos(a), 0], [0, 0, 1]])
        canvas_box = None
        angle = abs(angle)
    corners = _apply(to_flat, np.array([[0, 0], [w, 0], [w, h], [0, h]], float))
    x0, y0 = corners.min(axis=0)
    x1, y1 = corners.max(axis=0)
    if canvas_box is not None:
        x0, y0 = max(x0, canvas_box[0]), max(y0, canvas_box[1])
        x1, y1 = min(x1, canvas_box[2]), min(y1, canvas_box[3])
    canvas = (int(np.ceil(x1 - x0)), int(np.ceil(y1 - y0)))
    if min(canvas) < 64 or canvas[0] * canvas[1] > Image.MAX_IMAGE_PIXELS:
        return None
    shift = np.array([[1, 0, -x0], [0, 1, -y0], [0, 0, 1]])
    return np.linalg.inv(shift @ to_flat), float(angle), canvas


def _grid_lines(low, background):
    """축소본의 세로선 x = c + y·tan(a)와 가로선 y = c + x·tan(a) 목록 (c, a°)."""
    paper = _upsample(background, (low.shape[1], low.shape[0]))
    ink = np.pad(low < paper * INK, THIN)
    core = ink[THIN:-THIN, THIN:-THIN]
    # 선은 가늘다. 양옆(세로선)·위아래(가로선)가 THIN 떨어진 곳에서 비는 점만 쓴다. 넓은 먹 덩어리(사진 바탕·그림자)는 빠진다.
    across = core & ~ink[THIN:-THIN, :-2 * THIN] & ~ink[THIN:-THIN, 2 * THIN:]
    along = core & ~ink[:-2 * THIN, THIN:-THIN] & ~ink[2 * THIN:, THIN:-THIN]
    h, w = low.shape
    return _lines(*_points(across, transpose=False), h, w), _lines(*_points(along, transpose=True), w, h)


def _points(mask, transpose):
    """선 후보 점 (u, v). 세로선은 (x, y), 가로선은 (y, x)이며 많으면 고정 시드로 30만 개만 고른다."""
    ys, xs = np.nonzero(mask)
    if len(xs) > 300_000:
        pick = np.random.default_rng(0).choice(len(xs), 300_000, replace=False)
        ys, xs = ys[pick], xs[pick]
    u, v = (ys, xs) if transpose else (xs, ys)
    return u.astype(np.float64), v.astype(np.float64)


def _lines(u, v, extent, span):
    """u = c + v·tan(a) 꼴의 긴 직선 (c, a°)을 c 순서로 돌려준다.

    원근이 있으면 선마다 기울기가 달라 각도 하나로는 모든 선이 모이지 않는다. 각도·위치 누적표(허프 변환)에서
    봉우리를 고른 뒤 선마다 각도를 다시 맞춘다. 위치는 그림 가운데(v = extent/2)에서 잰다."""
    if len(u) < 200:
        return []
    mid = extent / 2
    angles = np.arange(-MAX_ANGLE, MAX_ANGLE + .01, .25)
    slopes = np.tan(np.radians(angles))
    base = u.min() - mid * slopes.max() - 2
    size = int(np.ceil(u.max() + mid * slopes.max() - base)) + 3
    acc = np.array([np.bincount(np.rint(u - (v - mid) * t - base).astype(int), minlength=size)[:size] for t in slopes],
                   dtype=np.float32)
    acc = (acc[:, :-2] + 2 * acc[:, 1:-1] + acc[:, 2:]) / 4
    padded = np.pad(acc, 1, constant_values=-1)
    neighbours = np.max([padded[1 + dy:1 + dy + acc.shape[0], 1 + dx:1 + dx + acc.shape[1]]
                         for dy in (-1, 0, 1) for dx in (-1, 0, 1) if dy or dx], axis=0)
    rows, cols = np.nonzero((acc >= neighbours) & (acc >= .3 * acc.max()))
    order = np.argsort(-acc[rows, cols])
    lines, tried = [], []  # lines: (가운데 위치, c, a). 같은 선의 이웃 봉우리는 한 번만 맞춘다
    for r, col in zip(rows[order], cols[order]):
        at = col + 1 + base
        if any(abs(at - t) <= 3 for t in tried) or len(tried) >= 120:
            continue
        tried.append(at)
        near = np.abs(u - (v - mid) * slopes[r] - at) < 4
        line = _refine(u[near], v[near], angles[r], extent, span)
        if line is None:
            continue
        centre = line[0] + mid * np.tan(np.radians(line[1]))
        if all(abs(centre - other[0]) > 3 for other in lines):
            lines.append((centre, *line))
    return _consistent([line[1:] for line in sorted(lines)])


def _consistent(lines):
    """격자선의 기울기는 위치에 따라 고르게 변한다(원근). 그 추세에서 벗어난 선(사진 가장자리·바탕 경계)은 뺀다."""
    if len(lines) < 3:
        return lines
    # 바깥쪽의 벗어난 선은 최소제곱 직선을 끌어가므로 쌍별 기울기의 중앙값(Theil–Sen)으로 추세를 잡는다.
    c, a = np.array(lines).T
    i, j = np.triu_indices(len(c), 1)
    slope = float(np.median((a[j] - a[i]) / (c[j] - c[i])))
    trend = np.median(a - slope * c) + slope * c
    return [line for line, r in zip(lines, np.abs(a - trend)) if r <= TREND]


def _refine(u, v, angle, extent, span):
    """후보 주변 점으로 한 직선의 각도·위치를 다시 맞춘다. 짧거나 끊긴 점 무리(글자 열)는 버린다."""
    if len(u) < 20:
        return None
    angles = np.arange(angle - .3, angle + .31, .05)
    best = None
    for a in angles:
        proj = u - v * np.tan(np.radians(a))
        offset = proj.min()
        hist = np.bincount(np.rint(proj - offset).astype(int))
        peak = int(hist.argmax())
        if best is None or hist[peak] > best[0]:
            best = (hist[peak], a, peak + offset)
    _, a, c = best
    t = np.tan(np.radians(a))
    for width in (2, 1.5):  # 봉우리 근처 점으로 최소제곱을 두 번 맞춰 픽셀보다 작은 기울기 차이까지 잡는다
        on = np.abs(u - v * t - c) <= width
        if on.sum() < 20:
            return None
        t, c = np.polyfit(v[on], u[on], 1)
    a = np.degrees(np.arctan(t))
    on = np.abs(u - v * t - c) <= 1.5
    if on.sum() < 20:
        return None
    lo, hi = np.percentile(v[on], [1, 99])
    # 실선·점선은 길이의 30% 이상에 점이 있다. 세로로 늘어선 글자는 그보다 성기다.
    if hi - lo < .15 * extent or on.sum() < .3 * (hi - lo):
        return None
    # 그림 테두리에 붙은 선(사진 가장자리의 바탕 띠)은 격자선으로 믿지 않는다.
    ends = c + np.array([lo, hi]) * t
    if ends.min() < .01 * span or ends.max() > .99 * span:
        return None
    return float(c), float(a)


def _cross(vertical, horizontal):
    """x = c + y·tan(a)와 y = d + x·tan(b)의 교점."""
    c, a = vertical[0], np.tan(np.radians(vertical[1]))
    d, b = horizontal[0], np.tan(np.radians(horizontal[1]))
    x = (c + d * a) / (1 - a * b)
    return x, d + x * b


def _target(quad, w, h):
    """격자 사각형을 펼 직사각형과 캔버스 범위. 사각형이 비정상이면 None."""
    tl, tr, br, bl = quad
    top, bottom = np.linalg.norm(tr - tl), np.linalg.norm(br - bl)
    left, right = np.linalg.norm(bl - tl), np.linalg.norm(br - tr)
    if min(top, bottom, left, right) < 32:
        return None
    if min(top, bottom) / max(top, bottom) < MIN_SIDE_RATIO or min(left, right) / max(left, right) < MIN_SIDE_RATIO:
        return None
    if not (np.all(quad >= [-.1 * w, -.1 * h]) and np.all(quad <= [1.1 * w, 1.1 * h])):
        return None
    edges = [quad[(i + 1) % 4] - quad[i] for i in range(4)]
    cross = [edges[i][0] * edges[(i + 1) % 4][1] - edges[i][1] * edges[(i + 1) % 4][0] for i in range(4)]
    if not (all(z > 0 for z in cross) or all(z < 0 for z in cross)):
        return None
    # 마주 보는 변 중 긴 쪽(카메라에 가까운 쪽) 길이로 펴서 어느 부분도 해상도를 잃지 않게 한다.
    # 평균을 쓰면 칸 너비가 리더 고정 범위(85~110px) 아래로 줄어 열을 놓친다(아리랑 원근 0.12에서 36/48).
    width, height = max(top, bottom), max(left, right)
    # 원래 그림의 격자 왼쪽 위 위치를 기준으로 두어 펴진 그림이 원래와 비슷한 자리에 놓이게 한다.
    x, y = tl
    dst = np.array([[x, y], [x + width, y], [x + width, y + height], [x, y + height]])
    pad = MARGIN * max(width, height)
    return dst, (x - pad, y - pad, x + width + pad, y + height + pad)


def _homography(src, dst):
    """네 점 src → dst의 3×3 투영."""
    rows, rhs = [], []
    for (x, y), (u, v) in zip(src, dst):
        rows += [[x, y, 1, 0, 0, 0, -u * x, -u * y], [0, 0, 0, x, y, 1, -v * x, -v * y]]
        rhs += [u, v]
    return np.append(np.linalg.solve(np.array(rows), np.array(rhs)), 1).reshape(3, 3)


def _apply(matrix, points):
    p = np.c_[points, np.ones(len(points))] @ matrix.T
    return p[:, :2] / p[:, 2:]
