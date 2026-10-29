"""아리랑 합성 시험 묶음의 오류 배치와 정답 기록을 검사한다."""

from collections import Counter

from evaluation.labels import REPO, load_label
from evaluation.synthetic_set import NOTES, SCORE, plan, write_set


def test_each_kind_has_fifteen_spread_errors():
    specs = plan()
    assert specs[0] == {"recordingId": "arirang-synth-normal", "errors": []}
    assert Counter(e["kind"] for s in specs for e in s["errors"]) == {"PITCH": 15, "OCTAVE": 15, "TIMING": 15, "MISSED": 15}
    for s in specs:
        idx = sorted(e["idx"] for e in s["errors"])
        assert all(b - a > 1 for a, b in zip(idx, idx[1:]))  # 이웃한 음에 같이 넣지 않는다
        assert all(0 < i < len(NOTES) - 1 for i in idx)  # 첫 음·끝 음은 피한다
        for e in s["errors"]:
            if e["kind"] in ("TIMING", "MISSED"):
                assert NOTES[e["idx"]].beats >= 2 / 3  # 짧은 음은 지연·누락이 다른 음과 섞인다


def test_plan_is_deterministic():
    assert plan() == plan()


def test_written_labels_pass_the_label_check(tmp_path):
    (tmp_path / SCORE).parent.mkdir(parents=True)
    (tmp_path / SCORE).write_bytes((REPO / SCORE).read_bytes())
    paths = write_set(tmp_path, plan()[:2], root=tmp_path)
    labels = [load_label(p, root=tmp_path) for p in paths]
    assert [lb["recordingId"] for lb in labels] == ["arirang-synth-normal", "arirang-synth-pitch-1"]
    assert labels[1]["conditions"] == {"kind": "SYNTHETIC", "generator": "sine-harmonics", "seed": 0, "noiseDb": None, "gapSeconds": 0.04}
    # 같은 계획이면 녹음 파일까지 같은 바이트로 다시 만든다(묶음 해시가 유지된다).
    again = [load_label(p, root=tmp_path) for p in write_set(tmp_path / "again", plan()[:2], root=tmp_path)]
    assert [lb["audio"]["sha256"] for lb in labels] == [lb["audio"]["sha256"] for lb in again]
