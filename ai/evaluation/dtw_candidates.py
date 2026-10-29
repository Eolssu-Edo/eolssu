"""연구용 메모리 절감 DTW. 서비스 기본 정렬에서는 사용하지 않는다."""
import math

import numpy as np
from numba import njit

from ai_service.pipelines.scoring import _STRETCH_COST, _UNVOICED_COST


def _arrays(expected, detected):
    expected, detected = np.asarray(expected, dtype=np.float64), np.asarray(detected, dtype=np.float64)
    if any(x.ndim != 1 or not len(x) or np.isinf(x).any() for x in (expected, detected)):
        raise ValueError("nonempty 1D cents arrays without infinity required; NaN means unvoiced")
    return expected, detected


@njit(cache=True)
def _distance(a, b):
    if np.isnan(a) or np.isnan(b):
        return 0. if np.isnan(a) and np.isnan(b) else _UNVOICED_COST
    return abs(a - b)


@njit(cache=True)
def _advance(expected, detected, first, stop, boundary, record):
    """경계 누적 비용 행에서 진행한다. 기록할 때만 블록의 이동 방향을 저장한다."""
    m = len(detected)
    previous = boundary.copy()
    current = np.full(m + 1, np.inf)
    directions = np.empty((stop - first if record else 0, m if record else 0), dtype=np.uint8)
    for i in range(first, stop):
        current.fill(np.inf)
        for j in range(m):
            cost = _distance(expected[i], detected[j])
            best, move = previous[j] + cost, 0
            # 기존 librosa처럼 cost에 가산 비용을 먼저 더하고 누적 비용에 더한다.
            left = current[j] + (cost + _STRETCH_COST)
            if left < best:
                best, move = left, 1
            up = previous[j + 1] + (cost + _STRETCH_COST)
            if up < best:
                best, move = up, 2
            current[j + 1] = best
            if record:
                directions[i - first, j] = move
        previous, current = current, previous
    return previous, directions


def checkpoint_path(expected, detected, block_rows=None):
    """A: 중간 행 저장과 블록 재계산으로 전체 DTW의 경로를 복원한다."""
    expected, detected = _arrays(expected, detected)
    n, m = len(expected), len(detected)
    k = min(n, math.ceil(math.sqrt(8 * n))) if block_rows is None else block_rows
    if not isinstance(k, int) or k < 1:
        raise ValueError("block_rows must be a positive integer")
    boundary = np.full(m + 1, np.inf)
    boundary[0] = 0.
    checkpoints = [boundary]
    for first in range(0, n, k):
        boundary, _ = _advance(expected, detected, first, min(n, first + k), boundary, False)
        checkpoints.append(boundary)
    path = [(n - 1, m - 1)]
    i, j = path[-1]
    while i or j:
        first = (i // k) * k
        _, directions = _advance(expected, detected, first, i + 1, checkpoints[first // k], True)
        while i >= first and (i or j):
            move = directions[i - first, j]
            if move == 0:
                i, j = i - 1, j - 1
            elif move == 1:
                j -= 1
            else:
                i -= 1
            path.append((i, j))
        del directions  # 다음 블록을 만들 때 이전 블록의 표가 함께 남지 않게 한다.
    return np.asarray(path, dtype=np.int64)


class NoPath(ValueError):
    """선택한 폭 안에 시작부터 끝까지 이어지는 경로가 없다."""


@njit(cache=True)
def _band_steps(expected, detected, starts, stops, offsets):
    width = np.max(stops - starts)
    previous, current = np.full(width, np.inf), np.full(width, np.inf)
    directions = np.full(offsets[-1], 255, dtype=np.uint8)
    previous_start, previous_count = 0, 0
    for i in range(len(expected)):
        current.fill(np.inf)
        start, stop = starts[i], stops[i]
        for j in range(start, stop):
            local = j - start
            cost = _distance(expected[i], detected[j])
            best, move = np.inf, 255
            diagonal = j - 1 - previous_start
            if i == 0 and j == 0:
                best, move = cost, 0
            elif 0 <= diagonal < previous_count:
                best, move = previous[diagonal] + cost, 0
            if local:
                left = current[local - 1] + (cost + _STRETCH_COST)
                if left < best:
                    best, move = left, 1
            up_index = j - previous_start
            if 0 <= up_index < previous_count:
                up = previous[up_index] + (cost + _STRETCH_COST)
                if up < best:
                    best, move = up, 2
            current[local] = best
            directions[offsets[i] + local] = move
        previous, current = current, previous
        previous_start, previous_count = start, stop - start
    return previous[previous_count - 1], directions


def band_bounds(n, m, half_width_frames):
    """B가 계산하는 행별 열 범위 [start, stop). |j*(N-1) - i*(M-1)| <= width*(N-1)를 정수로 계산한다.
    N 또는 M이 1이면 유일한 경로의 칸 전체다."""
    if n == 1 or m == 1:
        return np.zeros(n, dtype=np.int64), np.full(n, m, dtype=np.int64)
    denominator = n - 1
    centers = np.arange(n, dtype=np.int64) * (m - 1)
    radius = half_width_frames * denominator
    starts = np.maximum(0, (centers - radius + denominator - 1) // denominator)
    stops = np.minimum(m, (centers + radius) // denominator + 1)
    return starts, stops


def band_path(expected, detected, half_width_frames=50):
    """B: 길이를 맞춘 중심선 주변의 칸만 계산하고 이동 방향을 저장한다."""
    expected, detected = _arrays(expected, detected)
    if not isinstance(half_width_frames, int) or half_width_frames < 0:
        raise ValueError("half_width_frames must be a nonnegative integer")
    n, m = len(expected), len(detected)
    if n == 1:
        return np.array([(0, j) for j in range(m - 1, -1, -1)], dtype=np.int64)
    if m == 1:
        return np.array([(i, 0) for i in range(n - 1, -1, -1)], dtype=np.int64)
    starts, stops = band_bounds(n, m, half_width_frames)
    if np.any(stops <= starts):
        raise NoPath("band has an empty row")
    offsets = np.concatenate((np.array([0]), np.cumsum(stops - starts)))
    total, directions = _band_steps(expected, detected, starts, stops, offsets)
    if not np.isfinite(total):
        raise NoPath("band does not connect both endpoints")
    i, j = n - 1, m - 1
    path = [(i, j)]
    while i or j:
        move = directions[offsets[i] + j - starts[i]]
        if move == 0:
            i, j = i - 1, j - 1
        elif move == 1:
            j -= 1
        else:
            i -= 1
        path.append((i, j))
    return np.asarray(path, dtype=np.int64)
