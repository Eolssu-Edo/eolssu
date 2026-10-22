"""Compare two NGC recordings using an explicit seven-yul sequence hypothesis.

The resulting yulmyeong labels are supported inferences, not producer labels.
"""

import argparse
import json
from pathlib import Path

import numpy as np


YUL = ["임", "남", "무", "황", "태", "고", "중"]


def group_repeated_regions(regions, maximum_gap=.4, maximum_cents=20):
    groups = []
    for region in regions:
        hz = region["median_hz"]
        if hz is None:
            raise ValueError("Missing pitch in reference region")
        merge = (groups and region["start_seconds"]-groups[-1][-1]["end_seconds"] <= maximum_gap
                 and abs(1200*np.log2(hz/groups[-1][-1]["median_hz"])) <= maximum_cents)
        if merge:
            groups[-1].append(region)
        else:
            groups.append([region])
    # Keep the actual median of the longest region, not an invented averaged F0.
    return [{"regionIds": [r["region_id"] for r in group],
             "startSeconds": group[0]["start_seconds"], "endSeconds": group[-1]["end_seconds"],
             "estimatedF0Hz": max(group, key=lambda r: r["end_seconds"]-r["start_seconds"])["median_hz"]}
            for group in groups]


def align_to_hypothesis(basic, groups):
    if len(groups) != 18:
        raise ValueError("The declared 7+7+4 hypothesis requires 18 reference groups")
    reference_hz = np.array([g["estimatedF0Hz"] for g in groups])
    if np.any(np.diff(reference_hz) <= 0):
        raise ValueError("Reference groups must ascend for this hypothesis")
    rows, previous = [], -1
    for segment in basic:
        distances = 1200*np.log2(segment["estimatedF0Hz"]/reference_hz)
        order = np.argsort(abs(distances))
        index = int(order[0])
        if abs(distances[index]) > 80 or index <= previous:
            raise ValueError("No distinct ordered pitch correspondence within 80 cents")
        previous = index
        register = ["저취", "평취", "역취"][index//7]
        rows.append({"segmentId": segment["segmentId"], "groupIndex": index+1,
                     "referenceRegionIds": groups[index]["regionIds"],
                     "referenceF0Hz": groups[index]["estimatedF0Hz"],
                     "differenceCents": round(float(distances[index]), 2),
                     "runnerUpMarginCents": round(float(abs(distances[order[1]])-abs(distances[index])), 2),
                     "yulmyeong": YUL[index % 7], "register": register, "status": "inferred"})
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference-analysis", type=Path, required=True)
    parser.add_argument("--mapping", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    reference = json.loads(args.reference_analysis.read_text(encoding="utf-8"))
    mapping = json.loads(args.mapping.read_text(encoding="utf-8"))
    if reference["summary"]["id"] != "ngc-sanjo-2551" or mapping["sampleId"] != "ngc-sanjo-basic-2550":
        raise ValueError("This comparison is scoped to the declared NGC recording pair")
    groups = group_repeated_regions(reference["regions"])
    rows = align_to_hypothesis(mapping["segments"], groups)
    result = {"schemaVersion": 1, "task": mapping["task"], "checkedOn": "2026-10-22",
              "referenceSampleId": reference["summary"]["id"],
              "referenceSha256": reference["summary"]["sha256"],
              "basicSampleId": mapping["sampleId"], "basicSha256": mapping["sourceSha256"],
              "hypothesis": "오름차순 18음군에 교육용 주요 7율명 임·남·무·황·태·고·중을 7+7+4로 적용. 제작 라벨이 아닌 분석 가설.",
              "basisUrl": "https://d-sce.pusan.ac.kr/kor/CMS/Lecture/stuView.do?cource_gbn=A&lect_no=A20240323&mCode=",
              "basisObservation": "교육기관 검색 색인에서 기본 율명 7개 확인. 원문 직접 요청은 HTTP503으로 재열람 불가.",
              "settings": {"repeatMaximumGapSeconds": .4, "repeatMaximumCents": 20,
                           "matchingMaximumCents": 80, "assignment": "nearest, distinct, increasing"},
              "groups": groups, "matches": rows,
              "limitations": ["제작처의 음별 라벨이나 독립 실연 검수가 아님",
                              "13음은 이 녹음의 음 수이며 악기 전체 음 수나 공통 13음 표준이 아님",
                              "제조사·실물 일치나 단일 고정 Hz·운지를 가정하지 않음"]}
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    print(f"Reference regions {len(reference['regions'])} -> {len(groups)} groups; {len(rows)} matches")
    for row in rows:
        print(row["segmentId"], row["register"], row["yulmyeong"], row["referenceRegionIds"], row["differenceCents"])


if __name__ == "__main__":
    main()
