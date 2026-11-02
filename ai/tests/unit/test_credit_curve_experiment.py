import pytest

from evaluation.credit_curve_experiment import candidate_credit, current_credit, score_axis


@pytest.mark.parametrize("ratio,expected", [(0, 1), (0.5, 1), (1, 1), (1.5, 0.5), (2, 0), (3, 0)])
def test_current_curve(ratio, expected):
    assert current_credit(ratio) == pytest.approx(expected)


@pytest.mark.parametrize("ratio,expected", [(0, 1), (0.5, 0.9), (1, 0.8), (1.5, 0.4), (2, 0), (3, 0)])
def test_candidate_curve(ratio, expected):
    assert candidate_credit(ratio, 0.2) == pytest.approx(expected)


def test_missed_notes_count_as_zero_and_other_notes_are_not_removed():
    assert score_axis([0, 0.5, 1, None], lambda ratio: candidate_credit(ratio, 0.2)) == 68


def test_invalid_candidate_parameter_is_rejected():
    with pytest.raises(ValueError):
        candidate_credit(0.5, 1.1)


@pytest.mark.parametrize("penalty", [0.1, 0.2])
def test_candidate_never_increases_when_error_grows(penalty):
    credits = [candidate_credit(i / 100, penalty) for i in range(301)]
    assert all(a >= b for a, b in zip(credits, credits[1:]))
    assert candidate_credit(-0.5, penalty) == candidate_credit(0.5, penalty)
