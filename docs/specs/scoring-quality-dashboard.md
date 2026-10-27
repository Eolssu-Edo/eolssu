# 채점 품질 통계 대시보드

- 작업: [S15P31E102-196](https://ssafy.atlassian.net/browse/S15P31E102-196)
- 상태: 합의됨 (2026-10-27, 사지한)

## 목적

채점이 실제 연주에서 어떻게 동작하는지 개발자가 Grafana에서 본다. 운영 현황 대시보드([eolssu-overview.json](../../infra/monitoring/dashboards/eolssu-overview.json))와 따로 둔다.

## 데이터와 제약

- 운영 DB `eolssu`의 `evaluations`·`note_results`·`practice_sessions`·`songs`·`instruments`·`pitches`를 읽는다. Grafana 계정은 `eolssu` 읽기 권한만 있어 집계 DB `eolssu_mart`는 쓰지 않는다.
- 서버 설정·컨테이너·DB 계정은 바꾸지 않는다. 결과물은 Import용 JSON 파일 하나와 README 안내다.
- 오차 부호는 채점 코드([scoring.py](../../ai/src/ai_service/pipelines/scoring.py))를 따른다. 음높이 +는 높게, 박자 +는 늦게, 길이 +는 길게 분 것이다.
- 음을 못 잡은 `MISSED` 행은 오차가 비어 있어 분포 패널에서 빠지고 판정 비율에만 들어간다.

## 거르기

화면 위에 선택상자 3개를 두고 모든 패널에 적용한다. 각각 여러 값과 전체를 고를 수 있다.

| 선택상자 | 값 | 적용 |
| --- | --- | --- |
| 악기 | `instruments.name` | 곡을 거쳐 연결 |
| 모델 버전 | `evaluations.model_version` | 채점 결과가 있는 패널만. 실패 세션 패널에는 적용하지 않는다 |
| 곡 | `songs.title`. 고른 악기의 곡만 보인다 | 세션의 곡 |

기간은 Grafana 시간 선택을 따른다. 채점 패널은 `evaluated_at`, 실패 세션 패널은 `practice_sessions.created_at` 기준이다.

## 패널

| 패널 | 보여 주는 것 | 판단에 쓰는 법 |
| --- | --- | --- |
| 음높이 오차 분포 | `pitch_error_cent`를 일정 구간으로 묶은 막대 | 0에서 한쪽으로 쏠리면 기준음이나 음높이 추정이 치우쳐 있다 |
| 박자 오차 분포 | `onset_error_ms` 구간 막대 | 한쪽으로 쏠리면 시작 시각 계산이 밀려 있다 |
| 길이 오차 분포 | `duration_error_ms` 구간 막대 | 판정에는 없고 점수에만 들어가는 축이다 |
| 음별 판정 비율 | 정답 음(`expected_pitch_id`의 율명)별 CORRECT·PITCH·TIMING·MISSED 비율 | 특정 음만 MISSED·PITCH가 많으면 그 음의 인식 문제다 |
| 곡별 판정 비율 | 곡별 같은 네 비율과 음 수 | 어려운 곡과 채점이 이상한 곡을 가린다 |
| 모델 버전별 점수 분포 | 버전별 총점·음높이·박자·길이 점수의 평균과 건수 | 새 모델이 점수를 바꿨는지 본다 |
| 실패 세션 원인 | `status = 'FAILED'` 세션의 `fail_reason`별 건수 | 채점까지 못 간 이유를 본다 |
| 채점 처리 시간 | `ended_at`부터 `evaluated_at`까지 걸린 초의 분포와 중앙값 | 채점 서버 지연을 본다. `ended_at`이 빈 세션은 뺀다 |

## 완료 조건

- Grafana 11.3 Dashboards → Import에서 기존 MySQL 데이터 소스를 골라 가져올 수 있다. uid는 기존 대시보드와 겹치지 않는다.
- 모든 패널 SQL이 [Flyway 마이그레이션](../../backend/src/main/resources/db/migration/)(V1·V2)을 적용한 DB에서 실행된다.
- 데이터가 없으면 패널이 오류 없이 `No data`를 보인다.
- [infra/monitoring/README.md](../../infra/monitoring/README.md)에 가져오기 방법과 패널 설명이 있다.

## 검증

- JSON 문법과 필수 항목(uid·datasource 입력·패널 목록)을 스크립트로 확인한다.
- SQL은 V1·V2를 적용한 MySQL에서 실행해 보는 게 가장 확실하다. 로컬 실행이 어려우면 열 이름 대조로 대신하고 그 사실을 남긴다.
- 실제 Grafana 가져오기는 서버 SSH 터널이 필요해 사용자가 확인한다. 확인 전까지는 검증 대기로 둔다.

## 남은 결정

- 시김새 음은 음높이 판정에서 빠지지만 `note_results`에는 시김새 여부가 없다. 그래서 음높이 오차 분포에 시김새 음의 큰 오차가 섞인다. 빼려면 `songs.score_json`을 열거나 열을 추가해야 하며, 이번 범위에서는 섞인 채로 두고 패널 설명에 적는다.
- 같은 세션을 다시 채점할 때의 정책이 정해지지 않았다([DB 스키마 남은 결정](../contracts/db-schema.md)). 지금은 세션당 평가 1행이라 중복 집계 문제가 없다.
