from evaluation.dtw_performance import estimate_bytes, summarize


def test_budget_estimate_uses_both_input_lengths():
    assert estimate_bytes(200, 300) == 1_440_000


def test_summary_keeps_failures_out_of_measured_values():
    rows = [
        {'status': 'DONE', 'seconds': 1.0, 'peakRssBytes': 100},
        {'status': 'DONE', 'seconds': 3.0, 'peakRssBytes': 120},
        {'status': 'TIMEOUT', 'seconds': None, 'peakRssBytes': None},
    ]
    result = summarize(rows)
    assert result['statusCounts'] == {'DONE': 2, 'TIMEOUT': 1}
    assert result['medianSeconds'] == 2.0
    assert result['maxPeakRssBytes'] == 120
    assert result['attempts'] == 3
