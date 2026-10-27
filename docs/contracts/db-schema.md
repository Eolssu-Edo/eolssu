# DB 스키마

- 상태: 초안. [S15P31E102-3](https://ssafy.atlassian.net/browse/S15P31E102-3)에서 설계했고, 1차 MVP API 명세에 맞춘 V2 보완은 [S15P31E102-142](https://ssafy.atlassian.net/browse/S15P31E102-142)([보완안·합의 필요 항목](../plans/S15P31E102-142.md))입니다. 아래 미결 사항이 남아 있습니다.
- 정본: SQL 파일입니다. ERD 그림과 SQL이 다르면 SQL을 따릅니다.

| 대상 | DB | 파일 | 적용 방식 |
| --- | --- | --- | --- |
| 운영 DB | `eolssu` | [V1__initial_schema.sql](../../backend/src/main/resources/db/migration/V1__initial_schema.sql), [V2__auth_user_practice_extensions.sql](../../backend/src/main/resources/db/migration/V2__auth_user_practice_extensions.sql) | Flyway 마이그레이션. 버전 순서대로 모두 적용한 결과가 정본 |
| 집계 DB | `eolssu_mart` | [data/mart/schema.sql](../../data/mart/schema.sql) | 일 배치 계정으로 직접 실행 |

## ERD

- 원본: [ERDCloud](https://www.erdcloud.com/d/aB9vM5edsqHqmedcE)
- 2026-10-22 기준 내보내기(V1). V2 변경은 아직 그림에 반영하지 않았습니다:

![얼쑤 ERD](db-schema-erd.png)

## 테이블 구성

| 영역 | 테이블 | 설명 |
| --- | --- | --- |
| 기준 데이터 | `pitches`, `instruments`, `fingering_charts`, `jangdans`, `songs`, `notation_symbols` | 율명·악기·운지표·장단·곡·기호처럼 운영자가 넣는 데이터 |
| 사용자 | `users`, `user_song_settings` | 탈퇴를 요청하면 `WITHDRAWAL_PENDING`으로 30일 보관하고, 그 안에 로그인하면 취소합니다. 30일 뒤 일괄 처리에서 개인정보 칸을 비우고 `DELETED`로 바꾸며 행은 남깁니다. 재가입은 새 행입니다. 곡별 BPM만 서버에 둡니다. |
| 인증 | `user_social_accounts`, `terms`, `user_term_agreements`, `email_verifications`, `refresh_tokens` | 간편 로그인 연결(제공자·제공자 ID 유일), 버전별 약관과 동의 기록, 이메일 인증번호·인증 토큰(해시만), 리프레시 토큰 회전(해시만) |
| 연습·평가 | `practice_sessions`, `evaluations`, `note_results` | 데이터 파이프라인이 읽는 기록. 세션당 평가 1개, 평가당 음표별 결과. 연습 기록 저장·메모·확인·삭제 칸은 사용자가 나중에 바꿉니다. |
| 성장 | `score_views` | 악보 화면 체류 기록. 연습 시간 집계 후보(회의 E1 결정안) |
| 학습 | `lessons`, `lesson_progress` | 율명·단계·시김새 학습과 사용자별 진도 |
| 집계 | `mart_daily_practice`, `mart_song_accuracy`, `mart_pitch_error` | 일 배치가 운영 DB를 읽어 만드는 통계. 외래키 없음 |

공통 규칙은 운영 DB SQL 파일 머리말에 있습니다. 시각은 UTC로 저장하고, 상태 칸은 ENUM 대신 문자열과 CHECK 제약을 씁니다.

## 미결 사항

| 항목 | 내용 |
| --- | --- |
| V2 합의 항목 | 회원 필수 칸 제약 교체, `deleted_at` 의미 변경, 기본 표기 HANJA, `bpm_unit` 기본값 없음, 기록 소프트 삭제, 악보 스냅샷 방식 등. [S15P31E102-142](../plans/S15P31E102-142.md#합의가-필요한-변경)에서 관련 파트와 확인합니다. |
| `mission_id` 외래키 | missions 테이블은 MIS 작업에서 추가하고 그때 `practice_sessions.mission_id`에 외래키를 겁니다. |
| 재채점 정책 | `evaluations`는 세션당 1행입니다. 같은 세션을 다시 채점할 때 덮어쓸지, 이력을 남길지 정해야 합니다. |
| `practice_sessions` 주석 | V1은 "추가만 하고 status만 바뀐다"고 적혀 있지만 `ended_at`, `recording_url`, `fail_reason`과 V2의 `saved_at`·`memo`·`viewed_at`·`record_deleted_at`도 나중에 바뀝니다. |
| 판정과 점수 축 | 점수는 음높이·박자·길이 3축인데 `note_results.verdict`에는 길이 판정 값이 없습니다. |
| `songs` 상태 칸 | `status`, `is_scorable`, `deleted_at` 세 칸이 공개 여부를 함께 나타냅니다. 역할을 나눌지 정해야 합니다. |
| DB 이름 | 영문명은 `eolssu`로 정했지만 현재 서버 DB는 `eolsu`입니다. 서버 DB 이름 변경은 Infra와 정해야 합니다. |
| 운영 DB 적용 | 백엔드의 `db` 프로필에 MyBatis·Flyway·MySQL 연결 구성이 있습니다. 실제 운영 DB 연결·V1·V2 적용과 기존 DB의 Flyway 편입은 별도 작업입니다. [실행 방법](../../backend/README.md#mysql-연결과-flyway)을 따릅니다. V2는 장단·연습 세션 행이 이미 있으면 `bpm_unit` 제약으로 실패하고, MySQL DDL은 되돌려지지 않습니다. |

Flyway는 이미 적용한 마이그레이션 파일을 수정하면 실행을 막습니다. 서버에 V1을 적용한 뒤에는 변경을 새 버전 파일로 추가합니다.
