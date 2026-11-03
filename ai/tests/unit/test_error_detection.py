"""오류 검출 평가의 지표·집계를 녹음 없이 정확한 f0 배열로 검사한다."""

import json

import numpy as np
import pytest

from ai_service.pipelines.scoring import HOP_S, HWANG_HZ, NoteResult, Tolerance
from evaluation.error_detection import (
    Trial,
    classify,
    code_hash,
    evaluate,
    expected_letters,
    score_notes,
    variables,
    wilson,
)
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


def test_score_notes_reads_yul_outside_danso_pitch_table():
    # 소금 악보에는 단소 음 표(pitches.json)에 없는 고·남이 나온다.
    score = {"music": {"ticksPerBeat": 1}, "events": [
        {"kind": "NOTE", "yul": y, "octave": o, "startTick": i, "durationTick": 1, "sigimsae": []}
        for i, (y, o) in enumerate([("고", 1), ("남", 0), ("응", 1)])
    ]}
    assert [n.semitone for n in score_notes(score, 0, 2)] == [16, 9, 23]


def test_score_notes_matches_danso_pitch_table():
    rows = json.loads((REPO / "data/reference/pitches.json").read_text(encoding="utf-8"))["rows"]
    score = {"music": {"ticksPerBeat": 1}, "events": [
        {"kind": "NOTE", "yul": r["yul"], "octave": r["octave"], "startTick": i, "durationTick": 1, "sigimsae": []}
        for i, r in enumerate(rows)
    ]}
    assert [n.semitone for n in score_notes(score, 0, len(rows) - 1)] == [r["semitone"] for r in rows]


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


def test_expected_letters_mark_intended_errors_from_first_note():
    # 곡 중간 10~17번 연습 구간. 13번 지연, 15번 옥타브를 의도했다.
    label = {"recordingId": "t", "errors": [{"idx": 13, "kind": "TIMING"}, {"idx": 15, "kind": "OCTAVE"}]}
    t = Trial(label, score_notes(SCORE, 10, 17), np.array([]), None)
    assert expected_letters([t]) == {"t": {"start": 10, "letters": "CCCTCOCC"}}


def test_clean_recordings_report_normal_score():
    report = evaluate([trial({})], [Tolerance()])
    assert report[0]["normal"] == {"recordings": 1, "meanTotal": 100.0}


def test_code_hash_follows_content_not_line_endings(tmp_path):
    (tmp_path / "pkg").mkdir()
    (tmp_path / "pkg/a.py").write_bytes(b"x = 1\n")
    (tmp_path / "notes.md").write_bytes(b"not code")
    first = code_hash(tmp_path)
    (tmp_path / "pkg/a.py").write_bytes(b"x = 1\r\n")
    (tmp_path / "notes.md").write_bytes(b"changed")  # .py가 아니면 무시
    assert code_hash(tmp_path) == first
    (tmp_path / "pkg/a.py").write_bytes(b"x = 2\n")
    assert code_hash(tmp_path) != first


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
    assert first["scoring"]["modelVersion"] == "pyin-5"
    assert first["synthetic"] == [{"kind": "SYNTHETIC", "seed": 0}, {"kind": "SYNTHETIC", "seed": 1}]


def test_duplicate_recording_ids_are_rejected():
    with pytest.raises(ValueError, match="recordingId"):
        expected_letters([trial({}), trial({})])


def test_same_basename_keeps_all_hashes(tmp_path):
    paths = []
    for folder in ("a", "b"):
        path = tmp_path / folder / "take.json"
        path.parent.mkdir()
        path.write_text(folder)
        paths.append(path)
    labels = [{"conditions": {"kind": "SYNTHETIC"}} for _ in paths]
    before = variables(paths, labels)
    assert len(before["data"]["labels"]) == 2
    paths[0].write_text("changed")
    assert before["data"]["datasetHash"] != variables(paths, labels)["data"]["datasetHash"]


def test_same_basename_without_common_drive_keeps_hashes(tmp_path, monkeypatch):
    import evaluation.error_detection as ed
    paths = [tmp_path / folder / "take.json" for folder in ("a", "b")]
    for path in paths:
        path.parent.mkdir()
        path.write_text(str(path))
    def no_common_drive(paths):
        raise ValueError("Paths don't have the same drive")
    monkeypatch.setattr(ed.os.path, "commonpath", no_common_drive)
    labels = [{"conditions": {"kind": "SYNTHETIC"}} for _ in paths]
    assert len(variables(paths, labels)["data"]["labels"]) == 2
