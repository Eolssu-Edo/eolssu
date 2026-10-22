"""같은 합성 입력으로 음높이 추정 방법별 채점 결과를 비교한다.

실행: uv run --group eval python -m evaluation.run_evaluation [--methods pYIN ...] [--out 결과.md]

새 방법을 비교하려면 `(audio: np.ndarray, sr: int) -> f0` 함수를 METHODS에 추가한다.
f0는 scoring.HOP_S(10ms) 간격의 Hz 배열이고, 소리가 없는 프레임은 NaN이어야 한다.
"""

import argparse
import time
from dataclasses import dataclass, field

import numpy as np

from ai_service.pipelines.scoring import Note, crepe_f0, frame_rms, pyin_f0, score
from evaluation.synthetic import BPM, REF_HZ, SCALE, SR, synth

METHODS = {
    "pYIN": pyin_f0,
    "torchcrepe full": crepe_f0,
    "torchcrepe tiny": lambda audio, sr: crepe_f0(audio, sr, model="tiny"),
}

N = len(SCALE)
ALL_NOTES = range(N)
YOSEONG = (150, 6, -75)  # 한쪽으로 크게 꺾는 떨림: 폭 150센트, 6Hz, 중심 75센트 아래
# 같은 높이를 두 번씩 부는 음계. 같은 음 사이는 혀로 끊어 40ms 비운다.
REPEATED = [Note(s, 1) for s in (0, 0, 2, 2, 5, 5, 7, 7, 5, 5, 0)]


@dataclass
class Case:
    name: str
    what: str  # 이 입력으로 확인하려는 것
    synth_args: dict = field(default_factory=dict)
    expected: dict = field(default_factory=dict)  # CORRECT가 아닌 음표의 정답 판정
    notes: list = field(default_factory=lambda: SCALE)
    fail: str | None = None  # 채점 대신 기대하는 실패 사유


CASES = [
    # 기본 판정
    Case("정상", "만점 기준선"),
    Case("음 바꿈", "반음 틀린 음 2개", {"shift": {3: 1, 7: -1}}, {3: "PITCH", 7: "PITCH"}),
    Case("옥타브 위로 붊", "실제로 한 옥타브 위를 분 음", {"shift": {5: 12}}, {5: "PITCH"}),
    Case("늦은 시작", "150ms 늦게 바뀐 음", {"delay": {4: 0.15}}, {4: "TIMING"}),
    Case("이른 시작", "150ms 일찍 바뀐 음", {"delay": {4: -0.15}}, {4: "TIMING"}),
    Case("빠진 음", "한 음을 쉬어 버림", {"drop": (6,)}, {6: "MISSED"}),
    # 조율
    Case("조율 -30센트", "보정 범위 안", {"cents": -30}),
    Case("조율 +45센트", "보정 범위 안, 허용 범위 근처", {"cents": 45}),
    Case("조율 -120센트", "보정 범위 밖", {"cents": -120}, {i: "PITCH" for i in ALL_NOTES}),
    # 연주 표현
    Case("작은 떨림", "모든 음에 ±30센트 5Hz 떨림", {"vibrato": {i: (30, 5) for i in ALL_NOTES}}),
    Case(
        "요성 표시",
        "요성 음표 2개를 시김새로 표시하면 음정 판정 제외",
        {"vibrato": {3: YOSEONG, 7: YOSEONG}},
        notes=[Note(n.semitone, n.beats, sigimsae=i in (3, 7)) for i, n in enumerate(SCALE)],
    ),
    Case("요성 미표시", "같은 떨림을 표시 없이 채점하면 음정 틀림", {"vibrato": {3: YOSEONG, 7: YOSEONG}}, {3: "PITCH", 7: "PITCH"}),
    Case("끊어 불기", "음마다 끝 80ms를 비움", {"gap": 0.08}),
    Case("짧은 음", "반 박(250ms) 음표", notes=[Note(n.semitone, 0.5) for n in SCALE]),
    Case("높은 음역", "음계 전체를 한 옥타브 위로", notes=[Note(n.semitone + 12, 1) for n in SCALE]),
    # 같은 음 반복
    Case("같은 음 반복", "같은 높이를 끊어서 두 번씩 붊", {"gap": 0.04}, notes=REPEATED),
    Case("같은 음 반복 늦은 시작", "반복된 두 번째 음이 150ms 늦음", {"gap": 0.04, "delay": {3: 0.15}}, {3: "TIMING"}, notes=REPEATED),
    Case("같은 음 반복 빠진 음", "반복된 두 번째 음을 쉼", {"gap": 0.04, "drop": (5,)}, {5: "MISSED"}, notes=REPEATED),
    # 메트로놈 소리
    Case("메트로놈 섞임", "박마다 딸깍 소리(신호 대비 0dB)", {"click_db": 0}),
    Case("메트로놈 크게 섞임", "딸깍 소리가 신호보다 10dB 큼", {"click_db": 10}),
    Case("메트로놈+빠진 음", "쉰 자리에서 딸깍 소리만 들림", {"click_db": 0, "drop": (6,)}, {6: "MISSED"}),
    # 소리 조건
    Case("약한 기본음", "기본음보다 2배음이 큰 소리", {"harmonics": (0.2, 1.0, 0.5)}),
    Case("작은 음량", "음량을 1/100로", {"gain": 0.003}),
    Case("숨소리", "음 시작마다 60ms 잡음(신호 대비 0dB)", {"breath_db": 0}),
    Case("잡음 20dB", "약한 배경 잡음", {"noise_db": 20}),
    Case("잡음 10dB", "강한 배경 잡음", {"noise_db": 10}),
    Case("잡음 0dB", "신호와 같은 크기의 잡음", {"noise_db": 0}),
    Case("음 바꿈+잡음 10dB", "잡음 속 틀린 음 검출", {"shift": {3: 1, 7: -1}, "noise_db": 10}, {3: "PITCH", 7: "PITCH"}),
    # 실패 처리
    Case("무음", "0점이 아니라 실패", {"gain": 0.0}, fail="NO_SOUND"),
    Case("짧은 입력", "정답 길이의 절반 미만", {"seconds": 1.0}, fail="TOO_SHORT"),
]


def evaluate(case, method, f0_fn):
    audio = synth(case.notes, **case.synth_args)
    started = time.perf_counter()
    f0 = f0_fn(audio, SR)
    elapsed = time.perf_counter() - started
    result = score(f0, case.notes, BPM, REF_HZ, rms=frame_rms(audio, SR))
    row = {"case": case.name, "method": method, "seconds": elapsed, "total": result.total}
    if case.fail or result.status != "DONE":
        row.update(matched=int(result.fail_reason == case.fail), count=1, detail=result.fail_reason or result.status)
        return row
    got = {n.idx: n.verdict for n in result.notes}
    wrong = [i for i in range(len(case.notes)) if got[i] != case.expected.get(i, "CORRECT")]
    # 일부러 바꾼 음표와 시김새 음표는 오차가 나는 것이 정상이므로 오차 평균에서 뺀다.
    normal = [
        n
        for n in result.notes
        if n.idx not in case.expected and not case.notes[n.idx].sigimsae and n.pitch_error_cent is not None
    ]
    row.update(
        matched=len(case.notes) - len(wrong),
        count=len(case.notes),
        pitch_err=np.mean([abs(n.pitch_error_cent) for n in normal]) if normal else None,
        onset_err=np.mean([abs(n.onset_error_ms) for n in normal]) if normal else None,
        octave=sum(n.octave_suspect for n in result.notes),
        detail=", ".join(f"{i}:{got[i]}" for i in wrong),
    )
    return row


def fmt(value, digits):
    return "-" if value is None else f"{value:.{digits}f}"


def render(rows, methods):
    lines = [
        "| 입력 | 방법 | 판정 일치 | 총점 | 음정 오차(센트) | 시작 오차(ms) | 옥타브 의심 | 추정(초) | 어긋난 판정 |",
        "| --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for r in rows:
        lines.append(
            f"| {r['case']} | {r['method']} | {r['matched']}/{r['count']} | {r['total']} "
            f"| {fmt(r.get('pitch_err'), 1)} | {fmt(r.get('onset_err'), 0)} | {r.get('octave', '-')} "
            f"| {r['seconds']:.2f} | {r['detail'] or ''} |"
        )
    lines += ["", "| 방법 | 판정 일치 합계 | 전부 맞은 입력 | 추정 시간 중앙값(초) |", "| --- | --- | --- | --- |"]
    for m in methods:
        mine = [r for r in rows if r["method"] == m]
        lines.append(
            f"| {m} | {sum(r['matched'] for r in mine)}/{sum(r['count'] for r in mine)} "
            f"| {sum(r['matched'] == r['count'] for r in mine)}/{len(mine)} "
            f"| {np.median([r['seconds'] for r in mine]):.2f} |"
        )
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--methods", nargs="+", choices=list(METHODS), default=list(METHODS))
    parser.add_argument("--out", help="결과 표를 저장할 Markdown 파일")
    args = parser.parse_args()

    for method in args.methods:  # 첫 호출의 준비 시간(JIT·모델 로딩)을 측정에서 뺀다
        METHODS[method](synth(SCALE[:2]), SR)
    rows = [evaluate(case, m, METHODS[m]) for case in CASES for m in args.methods]
    table = render(rows, args.methods)
    print(table)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(table + "\n")


if __name__ == "__main__":
    main()
