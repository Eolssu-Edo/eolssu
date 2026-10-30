"""U2: 손계산과 기존 DTW를 기준으로 정렬 교체 경계를 검사한다."""
import numpy as np
import pytest

from ai_service.pipelines import scoring
from evaluation.dtw_candidates import NoPath, band_path, checkpoint_path
from evaluation.dtw_candidate_check import check_case
from evaluation.dtw_baseline import CASES, run_case


def test_default_alignment_preserves_rest_and_backtracking_order():
    expected = np.array([0., np.nan, np.nan, 200.])
    np.testing.assert_array_equal(scoring._align(expected, expected),
                                  [[3, 3], [2, 2], [1, 1], [0, 0]])


@pytest.mark.parametrize("block_rows", [None, 1, 2, 3, 4])
def test_checkpoint_matches_dense_with_ties_rests_and_block_boundaries(block_rows):
    rng = np.random.default_rng(286)
    for n in (1, 2, 3, 7, 17):
        for m in (1, 2, 5, 11):
            expected = rng.choice([np.nan, -200., 0., 100., 350.], n)
            detected = rng.choice([np.nan, -200., 0., 100., 350.], m)
            np.testing.assert_array_equal(
                checkpoint_path(expected, detected, block_rows=block_rows),
                scoring._align(expected, detected), err_msg=f"shape={n,m}, K={block_rows}")


def test_checkpoint_does_not_call_dense_dtw(monkeypatch):
    def fail(*args, **kwargs):
        raise AssertionError("candidate must not allocate dense C or call librosa DTW")
    monkeypatch.setattr(scoring.librosa.sequence, "dtw", fail)
    path = checkpoint_path(np.zeros(17), np.zeros(11), block_rows=3)
    assert path[0].tolist() == [16, 10]
    assert path[-1].tolist() == [0, 0]


def test_checkpoint_preserves_float_operation_order():
    expected = np.array([0., 50.00000000000001, -50.00000000000001, np.nan, 600.25])
    detected = np.array([50., 0., np.nan, 600.2500000000001])
    np.testing.assert_array_equal(checkpoint_path(expected, detected, block_rows=2),
                                  scoring._align(expected, detected))
    # D+(C+50)과 (D+C)+50의 반올림 차이로 경로가 갈리는 입력(리뷰에서 찾은 반례)
    expected = np.array([-52.6, -83.4])
    detected = np.array([246.2387694666, -31.3804052489, -85.4505489302, -45.821677433, 299.7233729787])
    np.testing.assert_array_equal(checkpoint_path(expected, detected), scoring._align(expected, detected))


def test_full_band_matches_dense_for_rests_ties_and_unequal_lengths():
    rng = np.random.default_rng(287)
    for n, m in ((1, 5), (5, 1), (2, 9), (9, 2), (7, 11), (17, 13)):
        a = rng.choice([np.nan, -100., 0., 100., 250.25], n)
        b = rng.choice([np.nan, -100., 0., 100., 250.25], m)
        np.testing.assert_array_equal(band_path(a, b, half_width_frames=m), scoring._align(a, b))


def test_zero_width_band_uses_only_diagonal():
    path = band_path(np.zeros(5), np.zeros(5), half_width_frames=0)
    np.testing.assert_array_equal(path, [[4, 4], [3, 3], [2, 2], [1, 1], [0, 0]])


def test_disconnected_band_reports_no_path_without_dense_fallback(monkeypatch):
    def fail(*args, **kwargs):
        raise AssertionError("no fallback to dense DTW")
    monkeypatch.setattr(scoring.librosa.sequence, "dtw", fail)
    with pytest.raises(NoPath):
        band_path(np.zeros(2), np.zeros(6), half_width_frames=0)


@pytest.mark.parametrize("method", [checkpoint_path, band_path])
def test_candidates_reject_empty_or_infinite_input(method):
    with pytest.raises(ValueError):
        method(np.array([]), np.array([0.]))
    with pytest.raises(ValueError):
        method(np.array([np.inf]), np.array([0.]))


@pytest.mark.parametrize("method", ["A", "B"])
def test_candidate_score_matches_all_u1_snapshots(method):
    original = scoring._align
    for case in CASES:
        before, after = run_case(case), check_case(case, method)
        assert after["score"] == before["score"]
        if before["alignment"] is None:
            assert after["path"] is None
        else:
            assert after["path"] == before["alignment"]["path"]
            assert after["totalCost"] == before["alignment"]["totalCost"]
        assert scoring._align is original


def test_candidate_records_computed_cells_for_band_width():
    full = check_case("normal-8s", "A")["computedCells"]
    narrow = check_case("normal-8s", "B", half_width_seconds=0.5)["computedCells"]
    wide = check_case("normal-8s", "B", half_width_seconds=2)["computedCells"]
    assert narrow < wide < full


def test_checkpoint_counts_forward_and_recomputed_cells():
    result = check_case("normal-8s", "A")
    assert result["computedCells"] == 2 * 800 * 800


def test_band_single_frame_needs_no_dynamic_programming():
    assert check_case("single", "B")["computedCells"] == 0


def test_candidate_no_path_is_separate_failure_and_restores_default():
    original = scoring._align
    result = check_case("unequal", "B", half_width_seconds=0)
    assert result["status"] == "NO_PATH"
    assert result["score"] is None
    assert result["path"] is None
    assert scoring._align is original


@pytest.mark.parametrize("n,m,width", [(7, 11, 1), (11, 7, 2), (2, 6, 0), (17, 13, 3)])
def test_narrow_band_matches_dense_oracle_with_same_allowed_cells(n, m, width):
    rng = np.random.default_rng(288)
    a, b = rng.choice([0., 100., 300.], n), rng.choice([0., 100., 300.], m)
    cost = np.abs(a[:, None] - b[None, :])
    # 정수 부등식으로 허용 칸을 따로 정의해 후보의 실수 경계 계산과 대조한다.
    rows, columns = np.indices((n, m))
    cost[np.abs(columns * (n - 1) - rows * (m - 1)) > width * (n - 1)] = np.inf
    try:
        _, expected = scoring.librosa.sequence.dtw(
            C=cost, step_sizes_sigma=np.array([[1, 1], [0, 1], [1, 0]]), weights_add=np.array([0., 50., 50.]))
    except scoring.librosa.util.exceptions.ParameterError:
        with pytest.raises(NoPath):
            band_path(a, b, half_width_frames=width)
    else:
        np.testing.assert_array_equal(band_path(a, b, half_width_frames=width), expected)


def test_band_keeps_exact_integer_boundary_despite_float_roundoff():
    a, b = np.zeros(201), np.zeros(117)
    a[100], b[59] = 100., 100.
    rows, columns = np.indices((201, 117))
    cost = np.abs(a[:, None] - b[None, :])
    cost[np.abs(columns * 200 - rows * 116) > 200] = np.inf
    _, expected = scoring.librosa.sequence.dtw(
        C=cost, step_sizes_sigma=np.array([[1, 1], [0, 1], [1, 0]]), weights_add=np.array([0., 50., 50.]))
    assert [100, 59] in expected.tolist()  # 58 ± 1의 오른쪽 경계다.
    np.testing.assert_array_equal(band_path(a, b, half_width_frames=1), expected)
