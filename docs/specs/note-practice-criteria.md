# 한 음씩 연습 판정 기준 조회 (PRA-06, S15P31E102-493)

상태: 구현 범위. 값은 **AI 미확정 초안**이다. 계약 기준은 [Notion `/note-practice/criteria`](https://app.notion.com/p/40da0252aa458187aa0ce70a52d59315)이고 FE 소비자는 S15P31E102-341이다.

## 계약

- `GET /api/note-practice/criteria?instrumentId=`는 공개 조회다. 만료 로그인 쿠키에 영향받지 않는다. 판정은 기기에서 실시간으로 하고 서버는 기준만 내려준다. 점수·세션·기록을 만들지 않는다.
- 응답 `result`는 문자열 `instrumentId`와 `criteriaVersion`, `pitchToleranceCent`, `holdMs`, `minConfidence`, `minLevelDbfs`, `octaveMustMatch`, `tuning`(NONE·FIRST_NOTE), `maxTuningOffsetCent`, `sigimsaeMode`(MAIN_PITCH·SKIP), `hintAfterMs`다.
- `instrumentId`는 선행 0 없는 양의 1~18자리 십진 문자열이다. 누락·형식 오류는 400 `INVALID_REQUEST`다.
- 없는·비활성 악기와 기준이 등록되지 않은 악기는 404 `RESOURCE_NOT_FOUND`이며 앱은 한 음씩 연습 버튼을 숨긴다.

## 저장 위치

- 기준은 새 테이블 없이 `application.yml`의 `eolssu.note-practice.criteria`에 악기 `code`별로 둔다. DB ID는 환경마다 달라질 수 있어 키로 쓰지 않는다. Flyway를 쓰지 않는다.
- 값을 바꾸면 백엔드만 다시 배포하면 된다. 모바일 앱 배포는 필요 없고, 서버는 시작할 때 값을 읽으므로 재시작 전에는 바뀌지 않는다. 값이 바뀌면 `criteria-version`을 올린다.
- 잘못된 값(빈 버전, 0~1 밖의 신뢰도, 음수 시간·허용 오차, 0보다 큰 dBFS)은 서버 시작을 막는다.

## 초안 값

네 악기(DANSO·SOGEUM·JEONGAK_DAEGEUM·SANJO_DAEGEUM)에 명세의 초안 한 벌을 그대로 등록했다. 악기별로 측정하거나 조정한 값이 아니다. `pitch-tolerance-cent` 50은 V17의 네 악기 `pitch_tolerance_cent`와 같다.

| 필드 | 초안 |
| --- | --- |
| `criteriaVersion` | `note-step-1` |
| `pitchToleranceCent` | 50 |
| `holdMs` | 300 |
| `minConfidence` | 0.8 |
| `minLevelDbfs` | -45 |
| `octaveMustMatch` | true |
| `tuning` | FIRST_NOTE |
| `maxTuningOffsetCent` | 50 |
| `sigimsaeMode` | MAIN_PITCH |
| `hintAfterMs` | 8000 |

AI 담당이 악기별 값을 확정하면 해당 악기 블록만 바꾼다. 어떤 악기를 아직 제공하지 않으려면 그 키를 지우면 404가 된다.

## 제외

FE 연결(341), 기기 음높이 추정기, 점수·기록 저장. 운영 배포는 이 작업에 포함하지 않는다.
