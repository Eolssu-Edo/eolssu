# 짧은 옥타브 위 음 채점 오류: 원인과 수정

- 작업: [S15P31E102-251](https://ssafy.atlassian.net/browse/S15P31E102-251)
- 상태: 구현·합성 입력 검증 완료(2026-11-01). `modelVersion` `pyin-3`. 채점 동작이 바뀌므로 MR에서 팀 합의를 받는다.
- 기준 결과: [pyin-3](../../../ai/evaluation/baselines/arirang-synthetic-pyin-3.json), 비교 대상 [pyin-2](../../../ai/evaluation/baselines/arirang-synthetic-pyin-2.json)
- 코드: [`pyin_f0`·`_undo_subharmonic`·`_align`](../../../ai/src/ai_service/pipelines/scoring.py), 연구용 후보 A의 [`_distance`](../../../ai/evaluation/dtw_candidates.py)

## 결론

| 지표(아리랑 합성 묶음, 허용값 16조합) | pyin-2 | pyin-3 |
| --- | --- | --- |
| 옥타브 오류 맞힘 / 종류 틀림 / 놓침 | 6 / 2 / 7 | 15 / 0 / 0 |
| 전체 오류 검출률(시작 허용 0.125박 / 0.2박) | 82% / 72% | 97% / 87% |
| 정상 음 오탐 | 9~10 / 733 | 4~5 / 733 |
| 맞음→틀림 b / 틀림→맞음 c, 맥니마 p | — | 0 / 14, 0.000122 (16조합 모두 같음) |

바뀐 14음은 모두 옥타브 오류 녹음 안에 있다. 오류 음이 OCTAVE로 잡히고, 그 음 때문에 MISSED·TIMING이 된 이웃 정상 음이 CORRECT로 돌아왔다. 음 바꿈·박자·누락 녹음의 판정은 바뀌지 않았다.

## 원인

두 단계가 겹쳤다.

1. **pYIN이 짧은 옥타브 위 음을 원래 옥타브로 잡는다.** 놓친 7음은 모두 0.21~0.42초의 옥타브 위 음이었다. 같은 음을 혼자 불면 맞게 잡지만, 앞뒤에 평취 음이 있으면 그 음의 구간 전체를 원래 옥타브로 잡았다. 0.62초부터는 맞았다. 2f로 반복되는 소리는 f로도 반복되므로 f도 그럴듯한 후보가 되고, pYIN은 녹음 전체에서 큰 도약을 피하는 경로를 고른다(관찰에서 추론한 메커니즘).
2. **정렬이 옥타브 어긋난 음을 이웃 음에 넘긴다.** 1을 고친 뒤에도 정렬 비용이 1200센트여서, 정렬이 그 음을 한 프레임으로 줄이고 프레임을 이웃 음에 넘겼다. 그래서 오류 음은 MISSED, 이웃 음은 TIMING이나 MISSED가 됐다. 아래로 분 긴 옥타브 오류 2음도 같은 이유였다.

실제 단소 단음 측정에서 옥타브 오인식이 0%였던 것([조율 측정](danso-tuning-variation.md))은 길게 혼자 분 음이라 1의 조건에 해당하지 않는다.

## 선택한 수정

- **f/2f 재판정:** pYIN 뒤 소리 있는 프레임마다 f0와 2f0 자리의 스펙트럼 크기(FFT 4096, 가장 가까운 칸과 양옆 한 칸의 최대)를 비교해 A(f0)/A(2f0) < 0.1이면 2f0로 고친다. 실제 단소 NGC 5개(ngc-danso-2338~2342)의 라벨 구간 안 소리 있는 프레임 14,685개에서 이 비의 하위 1%는 평취 1.90, 역취 0.82였다(시제품의 ±반 반음 띠 기준). 구현한 함수는 파일 전체의 소리 있는 프레임 14,750개 중 하나도 바꾸지 않았다. 126초 입력에서 약 0.8초가 더 든다(전체 요청 시간은 따로 재지 않음).
- **옥타브 접은 정렬 비용:** 비용 = min(|e−d|, ||e−d|−1200| + 300). 300은 기존 무음 비용 `_UNVOICED_COST`다. 판정은 정렬 비용이 아니라 원래 음높이로 하므로 옥타브 오류와 옥타브 표시는 그대로 남는다. 연구용 후보 A의 거리 함수도 같게 바꿔 기존 경로 동일성 검사를 유지했다.

## 비교한 대안

pYIN 단계는 평취(반음 10)–짧은 음–평취(14) 합성 입력과 실제 단소 NGC 5개로 먼저 걸렀다.

| 후보 | 0.21초 옥타브 위 | 0.42초 | 정상 음 | 실제 단소 옥타브 맞음 | 판단 |
| --- | --- | --- | --- | --- | --- |
| pYIN 기본 | 틀림 | 틀림 | 맞음 | 100% | 문제 그대로 |
| pYIN `max_transition_rate` 100 | 틀림 | 맞음 | 맞음 | 99.99% | 0.21초 못 고침 |
| pYIN `max_transition_rate` 200 | 틀림 | 틀림 | 틀림 | 82.3% | 정상 연주를 망침 |
| 프레임별 YIN | 맞음 | 맞음 | 맞음 | 99.80% | 합성 묶음 오탐 15/733 |
| f/2f 재판정 | 맞음 | 맞음 | 맞음 | 100% | 선택 |

torchcrepe는 실행 환경에 없어 이번 비교에서 빠졌다. 정렬 비용은 f/2f 재판정 뒤 합성 묶음 첫 조합으로 비교했다. 옥타브 접기(벌점 100·300)와 상한 300은 모두 옥타브 15/0/0, 오탐 5였고 상한 600은 13/2/0, 오탐 6이었다. 상한 300은 300센트보다 큰 차이를 모두 같은 비용으로 만든다. 접기는 750센트보다 큰 차이부터 비용을 줄이고(800 → 700, 1000 → 500) 1200센트에 가까울수록 많이 줄여, 그보다 작은 음정 차이의 구별은 그대로 둔다. 템포가 흔들릴 때 음높이로 음을 찾는 힘을 덜 잃을 것으로 보고 접기를 골랐다. 이 차이를 템포 변화 입력으로 재지는 않았다.

## 재현

저장소 루트의 PowerShell에서 실행한다. Windows의 긴 작업 경로에서는 [ai/README](../../../ai/README.md)대로 `NUMBA_CACHE_DIR`를 짧게 잡는다.

```powershell
Set-Location ai
uv sync --locked
uv run --locked python -m evaluation.synthetic_set
$labels = @(Get-ChildItem ../data/processed/recordings/danso/arirang-synthetic/*.label.json | ForEach-Object { $_.FullName })
uv run --locked python -m evaluation.error_detection @labels --out evaluation/baselines/arirang-synthetic-pyin-3.json --compare evaluation/baselines/arirang-synthetic-pyin-2.json --vary scoring
```

같은 명령을 다시 실행해 기준 결과와 음별 판정·지표·변인이 같음을 확인했다(`--compare` pyin-3, 달라진 변인 없음). 회귀 테스트는 `ai/tests/unit/test_scoring.py`의 `test_short_octave_up_between_lower_notes_is_octave_error`, `test_octave_error_frames_stay_with_their_note`다. pyin-2 코드에서는 두 테스트 모두 옥타브 음이 CORRECT로 실패한다. 두 번째 테스트는 f/2f 재판정만 넣고 정렬 비용을 바꾸기 전에는 {2: MISSED, 3: TIMING}로 실패한다.

## 한계

- 아리랑 한 곡의 합성 입력(사인파와 배음 3개)이다. 실제 짧은 역취가 평취 사이에 있을 때 f 자리 에너지가 0.1 아래로 내려가는지는 확인하지 않았다. [S15P31E102-158](https://ssafy.atlassian.net/browse/S15P31E102-158) 녹음으로 확인한다.
- 실제 단소 확인은 한 연주자·악기의 단음 녹음이다.
- 정렬 비용 변경이 템포 변화 입력([DTW 비교](dtw-memory-comparison.md)의 합성 품질 입력)에서 어떤 차이를 내는지는 다시 측정하지 않았다.
- `modelVersion`이 바뀌어 백엔드는 pyin-2와 pyin-3 결과를 같은 비교 대상으로 보지 않는다.
