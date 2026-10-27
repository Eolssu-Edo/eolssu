from evaluation.measure_latency import dtw_bytes, long_notes
from evaluation.synthetic import BPM


def test_long_notes_cover_requested_length():
    for seconds in (15, 60, 600):
        notes = long_notes(seconds)
        assert sum(n.beats for n in notes) * 60 / BPM == seconds


def test_dtw_estimate_grows_with_square_of_length():
    assert dtw_bytes(120) == 4 * dtw_bytes(60)
    # 60초는 1GiB 안팎, 600초는 수십 GiB라 실행하면 안 되는 크기다.
    assert dtw_bytes(60) < 2**31
    assert dtw_bytes(600) > 32 * 2**30
