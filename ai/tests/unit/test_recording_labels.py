"""녹음 정답 기록(의도한 오류·녹음 조건)의 형식과 검사를 작은 파일로 확인한다."""

import copy
import hashlib
import json

import pytest

from evaluation.labels import LabelError, load_label

SCORE = "data/tests/fixtures/scores/ngc-standard-2022-p173.score.json"  # 아리랑, NOTE 61개
PERFORMED = {
    "kind": "PERFORMED",
    "performer": "P01",
    "skill": "BEGINNER",
    "instrument": "I01",
    "device": "Galaxy S24",
    "app": "eolssu 0.1.0",
    "micDistanceCm": 30,
    "place": "방음 연습실",
    "noise": "조용함",
    "recordedOn": "2026-10-29",
    "temperatureC": 22.5,
    "take": 1,
    "errorMethod": "악보를 보며 표시한 음을 일부러 반음 높게 붊",
}


@pytest.fixture
def make(tmp_path):
    """녹음 파일과 정답 기록을 만들어 경로를 돌려준다. ROOT는 tmp_path, 악보는 저장소의 아리랑 샘플."""
    from evaluation.labels import REPO

    (tmp_path / "data/tests/fixtures/scores").mkdir(parents=True)
    score = (REPO / SCORE).read_bytes()
    (tmp_path / SCORE).write_bytes(score)
    audio = tmp_path / "rec.wav"
    audio.write_bytes(b"audio")

    def build(**change):
        label = {
            "schemaVersion": 1,
            "recordingId": "arirang-synth-001",
            "method": "tool:evaluation.trials@test",
            "review": "inferred",
            "audio": {"path": "rec.wav", "sha256": hashlib.sha256(b"audio").hexdigest()},
            "score": {"path": SCORE, "sha256": hashlib.sha256(score).hexdigest()},
            "range": {"startNoteIdx": 0, "endNoteIdx": 60},
            "bpm": 96,
            "bpmUnit": "DOTTED_QUARTER",
            "errors": [
                {"idx": 8, "kind": "PITCH", "semitones": 1},
                {"idx": 18, "kind": "TIMING", "ms": 100},
                {"idx": 36, "kind": "MISSED"},
                {"idx": 40, "kind": "OCTAVE", "octaves": 1},
            ],
            "conditions": {"kind": "SYNTHETIC", "generator": "sine-harmonics", "seed": 0, "noiseDb": None, "gapSeconds": 0.04},
        }
        for key, value in change.items():
            label[key] = value
        path = tmp_path / "label.json"
        path.write_text(json.dumps(label, ensure_ascii=False), encoding="utf-8")
        return path

    return lambda **change: load_label(build(**change), root=tmp_path)


def test_synthetic_and_performed_labels_load(make):
    assert make()["errors"][0] == {"idx": 8, "kind": "PITCH", "semitones": 1}
    assert make(conditions=PERFORMED)["conditions"]["performer"] == "P01"


def test_every_performed_condition_is_required(make):
    for key in PERFORMED:
        if key == "kind":
            continue
        with pytest.raises(LabelError, match=key):
            make(conditions={k: v for k, v in PERFORMED.items() if k != key})


@pytest.mark.parametrize(
    "change, message",
    [
        ({"performer": "김단소"}, "performer"),  # 이름 대신 P01 같은 ID만
        ({"recordedOn": "어제"}, "recordedOn"),
    ],
)
def test_performed_condition_values_are_checked(make, change, message):
    with pytest.raises(LabelError, match=message):
        make(conditions=PERFORMED | change)


@pytest.mark.parametrize(
    "errors, message",
    [
        ([{"idx": 8, "kind": "PITCH"}], "semitones"),  # 종류마다 크기가 필요하다
        ([{"idx": 8, "kind": "OCTAVE", "octaves": 0}], "octaves"),
        ([{"idx": 8, "kind": "SLOW"}], "kind"),
        ([{"idx": 61, "kind": "MISSED"}], "idx 61"),  # 아리랑 NOTE는 0~60
        ([{"idx": 8, "kind": "MISSED"}, {"idx": 8, "kind": "PITCH", "semitones": 1}], "idx 8"),
    ],
)
def test_bad_errors_are_rejected(make, errors, message):
    with pytest.raises(LabelError, match=message):
        make(errors=errors)


def test_error_outside_practice_range_is_rejected(make):
    with pytest.raises(LabelError, match="idx 8"):
        make(range={"startNoteIdx": 10, "endNoteIdx": 20}, errors=[{"idx": 8, "kind": "MISSED"}])


def test_range_beyond_song_is_rejected(make):
    with pytest.raises(LabelError, match="range"):
        make(range={"startNoteIdx": 0, "endNoteIdx": 61}, errors=[])


@pytest.mark.parametrize("part", ["audio", "score"])
def test_changed_file_is_rejected_by_hash(make, part):
    label = make()
    wrong = copy.deepcopy(label[part]) | {"sha256": "0" * 64}
    with pytest.raises(LabelError, match=part):
        make(**{part: wrong})
