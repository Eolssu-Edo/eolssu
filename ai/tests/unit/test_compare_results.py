"""평가 결과 두 개의 전후 비교: 변인 통제, 맥니마 검정, 바뀐 음 목록."""

import copy

import pytest

from evaluation.error_detection import CompareError, compare, mcnemar

TOL = {"pitch_cent": 50.0, "onset_beat": 0.125, "duration_ratio": 0.25, "max_tuning_cent": 100.0}


def result(verdicts="CPCC", recall=1.0, rate=0.0, model="pyin-2"):
    return {
        "variables": {
            "data": {"datasetHash": "d1"},
            "scoring": {"modelVersion": model, "commit": "a"},
            "environment": {"packages": {"numpy": "2.5.4"}},
            "synthetic": [{"kind": "SYNTHETIC", "seed": 0}],
            "performed": [],
        },
        "expected": {"r1": {"start": 10, "letters": "CPCO"}},
        "results": [
            {
                "tolerance": TOL,
                "normal": {"meanTotal": 95.0},
                "detection": {"recall": recall},
                "falsePositive": {"rate": rate},
                "verdicts": {"r1": verdicts},
            }
        ],
    }


@pytest.mark.parametrize(
    "b, c, p",
    [(0, 0, 1.0), (0, 6, 0.03125), (1, 9, 0.021484), (10, 30, 0.001565)],  # 25 미만은 이항 정확 검정, 이상은 χ²
)
def test_mcnemar(b, c, p):
    assert mcnemar(b, c) == pytest.approx(p, abs=1e-5)


def test_same_result_has_no_change():
    report = compare(result(), result(), vary=set())
    row = report["rows"][0]
    assert (row["b"], row["c"], row["p"]) == (0, 0, 1.0)
    assert report["changedNotes"] == [] and report["changedVariables"] == []


def test_undeclared_change_is_refused_with_its_name():
    after = result()
    after["variables"]["environment"]["packages"]["numpy"] = "2.6.0"
    with pytest.raises(CompareError, match="environment.packages.numpy"):
        compare(result(), after, vary={"scoring"})


def test_declared_change_is_compared_and_listed():
    # 13번(PITCH 의도) 음을 전에는 놓쳤고(C) 후에는 잡았다. 12번 맞는 음을 후에 오탐했다(T).
    before, after = result("CCCO", recall=0.5), result("CPTO", recall=1.0, rate=0.5, model="pyin-3")
    report = compare(before, after, vary={"scoring"})
    assert report["changedVariables"] == ["scoring.modelVersion: pyin-2 → pyin-3"]
    row = report["rows"][0]
    assert (row["b"], row["c"]) == (1, 1)
    assert row["recall"] == (0.5, 1.0) and row["falsePositiveRate"] == (0.0, 0.5)
    assert report["changedNotes"] == [
        {"recordingId": "r1", "idx": 11, "expected": "P", "before": "C", "after": "P"},
        {"recordingId": "r1", "idx": 12, "expected": "C", "before": "C", "after": "T"},
    ]


def test_octave_counts_as_pitch_when_pitch_was_intended():
    # 정답 P 자리에 O(옥타브 의심이 붙은 음정 오류)가 나와도 맞게 잡은 것이다.
    report = compare(result("CPCO"), result("COCO"), vary=set())
    assert (report["rows"][0]["b"], report["rows"][0]["c"]) == (0, 0)


@pytest.mark.parametrize("group", ["data", "synthetic", "performed"])
def test_data_groups_cannot_be_declared(group):
    with pytest.raises(CompareError, match=group):
        compare(result(), result(), vary={group})


def test_different_dataset_is_refused():
    after = copy.deepcopy(result())
    after["variables"]["data"]["datasetHash"] = "d2"
    with pytest.raises(CompareError, match="data.datasetHash"):
        compare(result(), after, vary={"scoring", "environment"})
