"""U1: 기존 채점 호출에서 얻은 DTW 경로와 실패 상태를 고정한다."""
import numpy as np

from evaluation.dtw_baseline import measure_case, run_case, tie_probes


def test_single_frame_has_both_endpoints_and_zero_cost():
    result = run_case("single")
    assert result["score"]["status"] == "DONE"
    assert result["score"]["total"] == 100
    assert result["alignment"]["path"] == [[0, 0]]
    assert result["alignment"]["totalCost"] == 0


def test_rest_costs_and_requested_note_ids_are_preserved():
    result = run_case("rest")
    assert result["alignment"]["shape"] == [4, 4]
    np.testing.assert_allclose(result["alignment"]["smallCost"], [
        [0, 300, 300, 200], [300, 0, 0, 300],
        [300, 0, 0, 300], [200, 300, 300, 0],
    ], atol=1e-9)
    assert result["alignment"]["path"] == [[3, 3], [2, 2], [1, 1], [0, 0]]
    assert [n["idx"] for n in result["score"]["notes"]] == [7, 9]


def test_silence_is_a_failure_without_fabricated_missed_notes():
    result = run_case("silence")
    assert result["score"]["status"] == "FAILED"
    assert result["score"]["fail_reason"] == "NO_SOUND"
    assert result["score"]["notes"] == []
    assert result["alignment"] is None


def test_unequal_lengths_prefer_diagonal_when_predecessor_costs_tie():
    result = run_case("unequal")
    assert result["alignment"]["shape"] == [3, 2]
    assert result["alignment"]["path"] == [[2, 1], [1, 0], [0, 0]]
    assert result["alignment"]["totalCost"] == 50


def test_hand_calculated_costs_show_horizontal_wins_over_vertical():
    result = tie_probes()
    assert result["horizontalBeforeVertical"]["totalCost"] == 200
    assert result["horizontalBeforeVertical"]["path"] == [[2, 2], [2, 1], [1, 0], [0, 0]]


def test_repeated_notes_keep_all_requested_results():
    a, b = run_case("repeated"), run_case("repeated")
    assert a == b
    assert [n["verdict"] for n in a["score"]["notes"]] == ["CORRECT"] * 3


def test_budget_skip_happens_before_score(monkeypatch):
    def fail(_):
        raise AssertionError("score must not run above budget")
    monkeypatch.setattr("evaluation.dtw_baseline.run_case", fail)
    result = measure_case("normal-8s", max_bytes=1)
    assert result["status"] == "skipped_budget"
    assert result["estimatedDtwBytes"] == 24 * 800 * 800


def test_measurement_keeps_snapshot_separate_from_variable_metrics():
    report = measure_case("single")
    assert report["status"] == "measured"
    assert report["snapshot"] == run_case("single")
    assert report["tracemallocPeakBytes"] > 0
    assert report["scoreSeconds"] >= 0
