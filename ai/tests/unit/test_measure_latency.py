import importlib
import sys

import evaluation.measure_latency as ml
from evaluation.measure_latency import decide, dtw_bytes, long_notes
from evaluation.synthetic import BPM

GIB = 2**30


def test_long_notes_cover_requested_length():
    for seconds in (15, 60, 600):
        notes = long_notes(seconds)
        assert sum(n.beats for n in notes) * 60 / BPM == seconds


def test_dtw_estimate_grows_with_square_of_length():
    assert dtw_bytes(120) == 4 * dtw_bytes(60)
    # 60초는 1GiB 안팎, 600초는 수십 GiB라 실행하면 안 되는 크기다.
    assert dtw_bytes(60) < 2**31
    assert dtw_bytes(600) > 32 * GIB


def test_scores_only_when_dtw_estimate_fits_memory_limit():
    limit_gib = 6.0
    assert decide(60, limit_gib, last=None, max_seconds=600, failed=False) == (True, None)
    assert decide(300, limit_gib, last=None, max_seconds=600, failed=False) == (False, None)
    # 한도와 같은 크기는 채점한다. GiB 단위를 MiB로 섞으면 이 경계가 틀린다.
    exact_gib = dtw_bytes(60) / GIB
    assert decide(60, exact_gib, last=None, max_seconds=600, failed=False)[0] is True
    assert decide(60, exact_gib * 0.99, last=None, max_seconds=600, failed=False)[0] is False


def test_skips_longer_lengths_after_failure_or_slow_estimate():
    _, skip = decide(30, 6.0, last=None, max_seconds=600, failed=True)
    assert skip and "실패" in skip
    # 15초에 400초 걸렸으면 30초는 800초로 예상해 한도 600초를 넘는다.
    _, skip = decide(30, 6.0, last=(15, 400.0), max_seconds=600, failed=False)
    assert skip and "800" in skip
    assert decide(30, 6.0, last=(15, 100.0), max_seconds=600, failed=False)[1] is None


def test_imports_without_resource_module(monkeypatch):
    # Windows에는 resource 모듈이 없다. 그래도 모듈을 불러오고 메모리만 비워 둔다.
    monkeypatch.setitem(sys.modules, "resource", None)
    module = importlib.reload(ml)
    try:
        assert module.peak_mb() is None
    finally:
        monkeypatch.undo()
        importlib.reload(ml)
