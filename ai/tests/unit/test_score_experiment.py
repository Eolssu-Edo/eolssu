from dataclasses import asdict

import numpy as np
import pytest

from ai_service.pipelines.scoring import HWANG_HZ, Note, Tolerance, score
from evaluation.score_experiment import Experiment, credit, score_experiment


def sample():
    notes = [Note(i % 2 * 2, 1, sigimsae=(i == 2), idx=i) for i in range(16)]
    f0 = np.empty(800)
    for i, note in enumerate(notes):
        f0[i * 50:(i + 1) * 50] = HWANG_HZ * 2 ** ((note.semitone + 0.2) / 12)
    f0[150:200] = np.nan
    return f0, notes


def test_default_matches_real_score_including_missed_and_sigimsae():
    f0, notes = sample()
    actual = score_experiment(f0, notes, 120, HWANG_HZ, Experiment())
    expected = score(f0, notes, 120, HWANG_HZ)
    assert asdict(actual['result']) == asdict(expected)
    assert actual['onset_tolerance_ms'] > 0


def test_partial_credit_does_not_change_verdicts_and_cannot_increase_score():
    f0, notes = sample()
    base = score_experiment(f0, notes, 120, HWANG_HZ, Experiment())['result']
    changed = score_experiment(f0, notes, 120, HWANG_HZ, Experiment(pitch_penalty=0.2, timing_penalty=0.2))['result']
    assert [n.verdict for n in changed.notes] == [n.verdict for n in base.notes]
    assert changed.pitch <= base.pitch and changed.timing <= base.timing
    assert changed.duration == base.duration


def test_zero_weights_and_invalid_parameters_fail():
    with pytest.raises(ValueError):
        Experiment(weights=(0, 0, 0)).validate()
    with pytest.raises(ValueError):
        Experiment(pitch_cent=0).validate()
    with pytest.raises(ValueError):
        Experiment(timing_penalty=1.1).validate()


def test_selected_tolerance_keeps_actual_score_verdicts():
    f0, notes = sample()
    spec = Experiment(pitch_cent=30, duration_ratio=0.2, onset_spread=3)
    actual = score_experiment(f0, notes, 120, HWANG_HZ, spec)['result']
    expected = score(f0, notes, 120, HWANG_HZ, Tolerance(pitch_cent=30, duration_ratio=0.2, onset_spread=3))
    assert asdict(actual) == asdict(expected)


def test_curve_and_large_error_boundaries():
    assert credit(0, 50, 0.2, 2) == 1
    assert credit(25, 50, 0.2, 2) == pytest.approx(0.9)
    assert credit(50, 50, 0.2, 2) == pytest.approx(0.8)
    assert credit(75, 50, 0.2, 2) == pytest.approx(0.2)
    assert credit(100, 50, 0.2, 2) == 0


def test_weight_changes_total_without_changing_components():
    f0, notes = sample()
    base = score_experiment(f0, notes, 120, HWANG_HZ, Experiment())['result']
    result = score_experiment(f0, notes, 120, HWANG_HZ, Experiment(weights=(1, 0, 0)))['result']
    assert (result.pitch, result.timing, result.duration) == (base.pitch, base.timing, base.duration)
    assert result.total == result.pitch


def test_selected_settings_change_the_expected_axis():
    notes = [Note(i % 2 * 2, 1, idx=i) for i in range(16)]
    f0 = np.full(800, np.nan)
    delays = [0, 4, 1, 7, 2, 9, 1, 6, 0, 10, 2, 8, 1, 7, 2, 9]
    for i, note in enumerate(notes):
        cents = 40 if i % 4 == 0 else 0
        f0[i * 50 + delays[i]:(i + 1) * 50 - 5] = HWANG_HZ * 2 ** ((note.semitone + cents / 100) / 12)
    def run(spec):
        return score_experiment(f0, notes, 120, HWANG_HZ, spec)['result']
    base = run(Experiment())
    pitch = run(Experiment(pitch_cent=30))
    timing = run(Experiment(onset_spread=1))
    duration = run(Experiment(duration_ratio=0.1))
    assert pitch.pitch < base.pitch and pitch.duration == base.duration
    assert any(n.verdict == 'PITCH' for n in pitch.notes)
    assert timing.timing < base.timing and timing.pitch == base.pitch
    assert any(n.verdict == 'TIMING' for n in timing.notes)
    assert duration.duration < base.duration and duration.pitch == base.pitch


def test_default_matches_real_score_at_half_point_rounding_boundary():
    shifts = [-60, 40, -20, 70, -30, 40, -30, 0, -30, -80, -20, 10, 100, -90, 100, 0]
    notes = [Note(i % 2 * 5, .25, idx=i) for i in range(len(shifts))]
    f0 = np.concatenate([np.full(13, HWANG_HZ * 2 ** ((n.semitone + cents / 100) / 12))
                         for n, cents in zip(notes, shifts)])
    expected = score(f0, notes, 120, HWANG_HZ)
    assert expected.pitch == 77
    assert asdict(score_experiment(f0, notes, 120, HWANG_HZ, Experiment())['result']) == asdict(expected)
