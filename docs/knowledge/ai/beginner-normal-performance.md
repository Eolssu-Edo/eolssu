# 입문자 정상 연주 채점: 박자 판정과 소금 정답

- 작업: [S15P31E102-158](https://ssafy.atlassian.net/browse/S15P31E102-158) 중 정상 연주 검증. 일부러 틀린 실제 녹음과 시김새 처리는 남아 있다.
- 상태: 구현·검증 완료(2026-11-01). `modelVersion` `pyin-4`. 채점 기준은 채점 담당(사지한)이 정했다.
- 목표: 리듬게임처럼 후하게 판정한다. 사람이 들어 문제없는 입문자 연주를 틀렸다고 하지 않는다.
- 기준 결과: 실제 [pyin-4](../../../ai/evaluation/baselines/team-normal-pyin-4.json)·[pyin-3](../../../ai/evaluation/baselines/team-normal-pyin-3.json), 합성 [pyin-4](../../../ai/evaluation/baselines/arirang-synthetic-pyin-4.json)
- 코드: [`score`의 박자 판정](../../../ai/src/ai_service/pipelines/scoring.py), [평가 도구의 12율 계산](../../../ai/evaluation/error_detection.py), [공통 악보 변환기](../../../data/src/scores/score_data.py)

## 결론

기본 허용값(음정 50센트, 시작 0.125박, 길이 25%)에서 음 수 기준이다.

| 녹음 | pyin-3 맞음 | pyin-4 맞음 | 박자 판정을 끈 경우 |
| --- | --- | --- | --- |
| 단소 아리랑(P01) | 2/61 | 54/61 | 55/61 |
| 소금 아리랑(P02) | 2/60 | 57/60 | 57/60 |
| 소금 도라지타령(P02) | 2/66 | 60/66 | 60/66 |
| 정상 점수 평균 | 54 | 84 | |

- pyin-3에서는 세 녹음 모두 50음 넘게 박자 오류였다. pyin-4는 박자 판정을 끈 결과와 거의 같다. 단소에서 남은 박자 오류 1개는 첫 음이며, 녹음 앞 1.3~2.7초의 숨소리 탓으로 본다.
- 아리랑 합성 묶음은 pyin-3과 16조합 모두 판정이 같다(맞음→틀림 0, 틀림→맞음 0). 박자 오류 14/15, 정상 음 오탐 5/733을 그대로 유지한다.
- 남은 틀림(음정 2, 놓침 1~4)은 박자와 관계없다. 실제로 틀린 음인지는 듣고 확인하지 않았다.

## 입력

- 녹음: 2026-10-29 팀원 녹음. 악기마다 입문자 1명(단소 P01·I01, 소금 P02·I02), 갤럭시 S24+ 기본 녹음 앱, 마이크 거리 약 20~30cm(기록 25), 조용한 소회의실, 테이크 1. 온도는 재지 않아 22℃로 적은 추정값이다. 원본은 Git에 넣지 않고 `data/raw/recordings/team-2026-10-29/`에 둔다.
- 정답 기록: [`data/annotations/recordings/`](../../../data/annotations/recordings/)의 3개. 의도한 오류는 없다.
- 빠르기: 메트로놈 없이 분 녹음이다. pYIN 소리 구간(첫·끝 소리 프레임) 길이 ÷ 악보 박 수로 BPM을 추정했다(76.3, 68.2, 65.8). 앱에서는 정해진 빠르기에 맞춰 부르므로 서비스 상황과 다르다.
- 악보: 단소는 표준악보집 아리랑(`ngc-standard-2022-p173`). 소금은 학교 교과서 발췌(`school-textbook-sogeum-p15` 아리랑, `p16` 도라지타령). 교과서 이름·출판사·이용 조건은 확인하지 못해 [목록](../../../data/catalog/collected-scores/sources.json)에 미확인으로 두었다. 악보 아래 "국립국악원 악보"라는 표기만 있다.

## 원인

1. **빠르기 흔들림이 박자 오류로 쌓였다.** pyin-3은 첫 음부터 정해진 BPM으로 각 음의 시작 시각을 계산한다. 연주가 조금씩 느려지거나 빨라지면 차이가 뒤로 갈수록 쌓여 거의 모든 음이 허용 범위(이 녹음들의 BPM에서 98~114ms)를 넘었다. 음정만 보면 단소 아리랑은 93점이었다.
2. **소금은 단소 악보와 다른 악보를 불었다.** 소금 단음 녹음의 가장 낮은 안정음은 464.8Hz(임, 단소 기준 466Hz)였다. 단소 아리랑의 가장 낮은 음 중(415Hz)은 소금으로 낼 수 없어, 소금 악보는 곡을 2반음 올려 적는다. 도라지타령은 교과서 판본(66음)을 불었고 표준악보집 판본은 91음이다. 단소 악보로 채점하면 64·58점, 소금 교과서 악보로 채점하면 97·100점이다(박자 판정을 끄고 잰 값).
3. **평가 도구가 소금 율을 읽지 못했다.** 평가 도구와 공통 변환기가 율 이름을 단소 11음 표(`pitches.json`)로 바꾸거나 검사해, 소금 악보의 고(청고)·남에서 멈췄다. 서비스 채점은 반음 번호를 받으므로 소금 율도 이미 채점할 수 있다.

## 선택한 방법

- **박자 판정(pyin-4):** 놓치지 않은 음마다 곡 처음 기준 늦음(시작 오차)을 구한다. 그 음의 늦음에서 앞뒤 3음(놓친 음 제외) 늦음의 중앙값을 뺀 값을 "앞뒤 음 기준 늦음"으로 쓴다. 곡 처음 기준과 앞뒤 음 기준 중 절댓값이 작은 쪽이 허용을 넘을 때만 박자 오류다. 허용 = max(`onset_beat` 박, `onset_spread` × 이 연주의 흔들림)이고, 흔들림은 앞뒤 음 기준 늦음의 MAD × 1.4826(정규분포라면 표준편차와 같아지게 맞춘 값)이다. `onset_spread`는 4다.
  - 연주가 전체적으로 빨라지거나 느려지면 앞뒤 음도 같이 늦어 오류가 아니다. 박을 크게 흔드는 연주일수록 허용이 넓어진다. 박이 고른 연주에서는 흔들림이 거의 0이라 지금처럼 0.125박을 쓴다.
  - 작은 쪽을 쓰는 이유: 앞뒤 음 기준만 쓰면 이웃 음의 측정 오차(약 10ms)가 더해져, 요성 다음 음(−60ms, 허용 62.5ms)처럼 예전에 맞던 음이 박자 오류가 됐다. 작은 쪽을 쓰면 어떤 음도 예전보다 엄격해지지 않는다.
  - 음별 `onsetErrorMs`는 지금처럼 곡 처음 기준 늦음이다. 백엔드 저장값의 뜻은 그대로이며, 빠르기가 흔들린 연주에서는 이 값이 커도 판정은 맞음일 수 있다.
- **소금 정답:** 공통 변환기가 `songs.json`에 없는 곡은 검토 기록의 `song`(제목·악기·장단)에서 읽고, 율명 표 검사는 단소에만 한다. 서비스 기준 데이터(`songs`·`instruments`·`pitches`)와 백엔드는 바꾸지 않아 소금은 서비스 곡 목록에 나타나지 않는다.
- **평가 도구:** 율 이름을 12율 순서와 옥타브로 반음 번호로 바꾼다. 허용값 칸이 늘어난 결과(`onset_spread`)도 이전 결과와 공통 칸으로 비교한다.

## 비교한 대안

시제품(저장소 밖 스크립트)으로 음정 판정은 그대로 두고 박자만 다시 판정했다. 소금 아리랑 악보는 검토 전 사본(1정간 다름)이라 최종 결과와 1음 안팎 다를 수 있다.

| 방식 | 단소 아리랑 | 소금 아리랑 | 소금 도라지 | 합성 박자 오류 잡음 | 합성 정상 음 박자 오탐 |
| --- | --- | --- | --- | --- | --- |
| 곡 처음 기준 0.125박(pyin-3) | 2/61 | 2/60 | 2/66 | 14/15 | 3/731 |
| 곡 처음 기준 0.5박 | 11/61 | 10/60 | 10/66 | 0 | 1 |
| 앞뒤 3음 기준 0.25박 | 46/61 | 49/60 | 39/66 | 4 | 2 |
| 앞뒤 3음 기준 0.5박 | 53/61 | 53/60 | 56/66 | 0 | 1 |
| 앞뒤 3음 기준, 흔들림 3배·최소 0.125박 | 54/61 | 53/60 | 59/66 | 14 | 4 |
| 앞뒤 3음 기준, 흔들림 4배·최소 0.125박 | 54/61 | 55/60 | 59/66 | 14 | 4 |
| 박자 판정 끄기 | 55/61 | 57/60 | 60/66 | 0 | 0 |

- 허용만 넓히면 차이가 쌓이는 문제가 남아 효과가 작다. 고정 박 수로 후하게 하면 합성의 100~200ms 박자 오류를 놓친다.
- 흔들림에 맞춘 허용은 연주마다 저절로 허용이 정해져(실제 녹음 4배일 때 0.79~1.05박) 곡·연주자·악기마다 따로 정하지 않아도 된다. 리듬게임처럼 후하게 하려고 4배를 골랐다. 3배와는 실제 녹음에서 2음만 다르다.
- 연주자의 지난 기록으로 사람별 허용을 쌓는 방법은 백엔드 저장이 필요해 이번에 하지 않았다.

## 재현

저장소 루트의 PowerShell에서 실행한다. 녹음 3개를 `data/raw/recordings/team-2026-10-29/`에 두고, Windows의 긴 작업 경로에서는 [ai/README](../../../ai/README.md)대로 `NUMBA_CACHE_DIR`를 짧게 잡는다.

```powershell
Set-Location ai
uv sync --locked
$real = @(Get-ChildItem ../data/annotations/recordings/*/*.json | ForEach-Object { $_.FullName })
uv run --locked python -m evaluation.error_detection @real --out evaluation/baselines/team-normal-pyin-4.json --compare evaluation/baselines/team-normal-pyin-3.json --vary scoring
uv run --locked python -m evaluation.synthetic_set
$synth = @(Get-ChildItem ../data/processed/recordings/danso/arirang-synthetic/*.label.json | ForEach-Object { $_.FullName })
uv run --locked python -m evaluation.error_detection @synth --out evaluation/baselines/arirang-synthetic-pyin-4.json --compare evaluation/baselines/arirang-synthetic-pyin-3.json --vary scoring
```

회귀 테스트는 `ai/tests/unit/test_scoring.py`의 `test_gradually_slower_playing_is_not_timing_error`(pyin-3에서는 박자 오류 다수로 실패)와 `test_late_note_next_to_missed_note_is_still_timing_error`, 소금 정답은 `data/tests/test_score_data.py`의 `test_answers_outside_songs_take_song_info_from_review`·`test_sogeum_samples_match_the_current_conversion`이다.

## 한계

- 녹음 3개, 연주자 2명, 한 번씩이다. 박을 고르게 부는 실제 연주자와 메트로놈에 맞춘 녹음으로 다시 확인해야 한다.
- 실제 녹음에 일부러 틀린 음이 없어 실제 오류 검출률은 재지 않았다. 합성 박자 오류는 박이 고른 연주에 넣은 100~200ms 지연뿐이다.
- 박을 크게 흔드는 연주는 박자 오류를 거의 받지 않는다(의도한 성질). 박자 연습 기능으로는 약하다.
- 소금 아리랑은 도라지타령에 맞춰 교과서 판본으로 정했고 연주자에게 확인하지 않았다. 표준악보집 판본과는 네 곳(길이 셋, 음 하나)이 달라 1~2음이 영향을 받을 수 있다.
- 녹음 앞 숨소리·잡음이 첫 음 시작을 흔든다. 녹음 시작을 다듬는 처리는 하지 않았다.
