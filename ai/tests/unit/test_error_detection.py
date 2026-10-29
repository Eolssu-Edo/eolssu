"""오류 검출 평가의 지표·집계를 녹음 없이 정확한 f0 배열로 검사한다."""

import json

import numpy as np
import pytest

from ai_service.pipelines.scoring import HOP_S, HWANG_HZ, NoteResult, Tolerance
from evaluation.error_detection import Trial, classify, evaluate, score_notes, variables, wilson
from evaluation.labels import REPO

SCORE = json.loads((REPO / "data/tests/fixtures/scores/ngc-standard-2022-p173.score.json").read_text(encoding="utf-8"))


@pytest.mark.parametrize(
    "x, n, low, high",
    [(9, 10, 0.596, 0.982), (10, 10, 0.722, 1.0), (0, 61, 0.0, 0.059)],  # 손계산 값
)
def test_wilson_interval(x, n, low, high):
    assert wilson(x, n) == pytest.approx((low, high), abs=0.001)


def test_wilson_without_samples_is_empty():
    assert wilson(0, 0) is None


def test_score_notes_count_only_notes_and_keep_rests_in_start_beat():
    notes = score_notes(SCORE, 17, 18)
    assert [n.idx for n in notes] == [17, 18]
    assert [n.semitone for n in notes] == [7, 10]  # 임, 무
    assert (notes[0].start_beat, notes[1].start_beat) == pytest.approx((34 / 3, 12))  # 사이에 1/3박 쉼


def results(*verdicts, octave=()):
    return [NoteResult(i, v, octave_suspect=i in octave) for i, v in enumerate(verdicts)]


def test_classify_needs_the_intended_kind():
    errors = {1: "PITCH", 2: "TIMING", 3: "MISSED", 4: "OCTAVE", 5: "OCTAVE"}
    got = classify(results("CORRECT", "PITCH", "PITCH", "CORRECT", "PITCH", "PITCH", "TIMING", octave=(4,)), errors)
    assert got == ["TN", "TP", "WRONG_KIND", "FN", "TP", "WRONG_KIND", "FP"]


def f0_of(notes, cents=None):
    """음표마다 정확한 높이를 채운 f0. cents는 음표 순번별 어긋남, 쉼은 NaN."""
    cents = cents or {}
    beat = 60 / 120
    frames = np.full(round((notes[-1].start_beat + notes[-1].beats - notes[0].start_beat) * beat / HOP_S), np.nan)
    for k, n in enumerate(notes):
        start = round((n.start_beat - notes[0].start_beat) * beat / HOP_S)
        frames[start : start + round(n.beats * beat / HOP_S)] = HWANG_HZ * 2 ** ((100 * n.semitone + cents.get(k, 0)) / 1200)
    return frames


def trial(errors, cents=None):
    notes = score_notes(SCORE, 0, 17)
    label = {"recordingId": "t", "bpm": 120, "errors": [{"idx": i, "kind": k} for i, k in errors.items()]}
    return Trial(label, notes, f0_of(notes, cents), None)


def test_evaluate_applies_each_tolerance():
    # 2번 음이 40센트 높고(의도하지 않음) 5번 음은 반음 높게(의도함) 불었다.
    report = evaluate([trial({5: "PITCH"}, {2: 40, 5: 100})], [Tolerance(pitch_cent=30), Tolerance(pitch_cent=50)])
    tight, loose = report
    assert (tight["detection"]["tp"], tight["falsePositive"]["fp"]) == (1, 1)
    assert (loose["detection"]["tp"], loose["falsePositive"]["fp"]) == (1, 0)
    assert loose["falsePositive"]["rate"] == 0 and loose["detection"]["recall"] == 1
    assert loose["verdicts"]["t"][5] == "P" and loose["verdicts"]["t"][2] == "C"


def test_clean_recordings_report_normal_score():
    report = evaluate([trial({})], [Tolerance()])
    assert report[0]["normal"] == {"recordings": 1, "meanTotal": 100.0}


def test_variables_name_every_group_and_hash_the_labels(tmp_path):
    a, b = tmp_path / "a.json", tmp_path / "b.json"
    a.write_text('{"x": 1}', encoding="utf-8")
    b.write_text('{"x": 2}', encoding="utf-8")
    labels = [{"conditions": {"kind": "SYNTHETIC", "seed": 0}}, {"conditions": {"kind": "SYNTHETIC", "seed": 1}}]
    first = variables([a, b], labels)
    assert set(first) == {"data", "scoring", "environment", "synthetic", "performed"}
    assert first["data"]["datasetHash"] == variables([b, a], labels[::-1])["data"]["datasetHash"]  # 순서와 무관
    b.write_text('{"x": 3}', encoding="utf-8")
    assert variables([a, b], labels)["data"]["datasetHash"] != first["data"]["datasetHash"]
    assert first["scoring"]["modelVersion"] == "pyin-2"
    assert first["synthetic"] == [{"kind": "SYNTHETIC", "seed": 0}, {"kind": "SYNTHETIC", "seed": 1}]
