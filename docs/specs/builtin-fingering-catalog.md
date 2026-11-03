# 4악기 내장 운지 자료의 서버 카탈로그 연결

- 작업: S15P31E102-409, 관련 Story S15P31E102-29.
- 상태: 2026-11-03 사용자 “ㄱㄱ” 승인으로 API 표현·DB 관계·브랜치 표기를 확정한 로컬 구현 계약. 운영 배포와 별개다.
- 기준: origin/develop `6899998bcd06434da4d499bc7c1bf710ba77ed41`.
- 선행: S15P31E102-407의 4악기 식별자·율명 등록은 위 develop 병합 커밋에서 확인했다. V17과 FE code·이체자 연결을 그대로 사용한다. 사용자에 따르면 S15P31E102-408은 별도 진행 중이며 이번 작업 범위에 포함하지 않는다.

## 범위와 원본

CAT-07의 악기별 목록과 `pitchId` 필터에 FE 내장 운지 자료를 연결한다. 원본은 `frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/core/model/fingering/`의 `Fingering.kt`, `FingeringTables.kt`, `YulNotation.kt`다. 원본 표의 단소 12행, 소금 20행, 정악대금 30행, 산조대금 30행을 대상으로 한다. 산조는 원본처럼 정악 기본틀을 공유한다.

율명 한자·이름·읽는 법·기본음 여부·구멍 상태·취법·출처·악기 설명·명목상 황종 기준을 보존한다. 서버 `pitchId`는 한자 문자열의 완전 일치나 ID 순서가 아니라 원본이 뜻하는 `(yul, octave)`에 대응한다. 이체자는 원본 표시와 서버 식별을 분리한다. 임의의 새 운지·실측 조율·전문가 검수 결과·사용 허가를 만들지 않는다.

운영 배포·DB 적용·Push·MR은 포함하지 않는다. 로컬 구현·검증·커밋이 범위다.

## API 표현

기존 `GET /api/instruments/{instrumentId}/fingerings?pitchId=...`의 경로·ID 문자열·정렬·400/404·빈 목록 규칙을 유지한다. 항목에 nullable `pattern`을 추가한다. 내장표와 연결되지 않은 기존 이미지형 항목은 `pattern: null`로 반환하며, 내장표와 연결된 기존 행은 원래 이미지·설명과 pattern을 함께 반환한다. 내장 자료는 이미지 URL을 만들지 않고 `imageUrl: null`로 반환한다. 기존 API의 ID/설명/이미지 필드는 보존한다.

아래는 단소 평취 중려의 응답 항목 예시다. ID 자리표시는 실제 실행 때 DB에서 조회한 문자열로 바꾼다.

```json
{
  "fingeringId": "<실제 운지 ID>",
  "instrumentId": "<DANSO의 실제 ID>",
  "pitchId": "<중·0옥타브의 실제 ID>",
  "imageUrl": null,
  "description": null,
  "pattern": {
    "blow": "MID",
    "hanja": "仲",
    "name": "중려",
    "reading": "중",
    "holes": ["CLOSED", "CLOSED", "CLOSED", "CLOSED", "OPEN"],
    "basic": true,
    "source": "출처: 국립국악원 e국악아카데미 「단소 안공법」. 높은 태는 제1공(뒷구멍)과 제3공을 막아요.",
    "instrumentNote": "뒷구멍은 왼손 엄지로 막아요. 역취는 입김을 세게 불어요. 높은 태(청태)는 낮은 태와 운지가 달라요.",
    "nominalHwangPitchClass": 3,
    "verificationStatus": "BUILTIN_REFERENCE"
  }
}
```

- `blow`: `LOW`(저취), `MID`(평취), `HIGH`(역취). 단소·소금에는 LOW를 생성하지 않는다.
- `holes`: `CLOSED`, `OPEN`, `HALF`, `UNKNOWN`. 단소는 뒷구멍·앞 1~4공 순서의 5개, 나머지는 1~6공 순서의 6개다. UNKNOWN은 확인 필요를 뜻하며 막힘/열림으로 추정하지 않는다.
- `nominalHwangPitchClass`: 원본의 C 기준 반음 수(단소·소금·정악 3, 산조 5). 실제 녹음의 주파수나 `pitches.semitone`의 전조값으로 해석하지 않는다.
- `verificationStatus`: `BUILTIN_REFERENCE`는 앱 내장 참고 자료라는 뜻이다. 전문가 검수·권리 승인·평가 정답을 뜻하지 않는다. 원본 구멍의 미확인은 UNKNOWN으로 따로 보존한다.
- 산조 `source`와 `instrumentNote`에는 원본의 정악 기본틀 공유·유파별 차이 문구를 그대로 남긴다.
- `structureImageUrl`은 근거 있는 별도 등록 자료가 없으므로 기존 null을 유지한다.

## DB 관계와 호환성

기존 `fingering_charts`의 `instrument_id → instruments.id`, `pitch_id → pitches.id`, `(instrument_id,pitch_id)` 유일 관계를 유지한다. 현재 원본 92행은 각 악기 안에서 같은 pitch가 취법 간 중복되지 않으므로 새 취법별 테이블은 필요하지 않다. 이후 같은 음의 대체 운지가 필요하면 별도 계약 변경으로 다룬다.

V18 Flyway 마이그레이션에서 `pattern_json JSON NULL`을 추가하고 `image_url`을 nullable로 바꾼다. 이미지 또는 구조화 자료 중 하나는 있어야 한다는 CHECK를 둔다. 기존 이미지·설명·ID는 보존하고, 자료는 실제 악기 code와 `(yul,octave)`로 찾아 연결한다. 같은 키의 기존 행에는 새 pattern만 채워 기존 이미지·설명을 덮어쓰지 않는다. 누락·충돌한 식별자는 조용히 생략하거나 임의 ID로 대체하지 않고 검증 실패로 드러낸다.

선행407의 최종 번호는 V17이고 이번 변경은 V18이다. 별도 작업408의 확인 시점에는 신규 마이그레이션이 없었다. 다른 미병합 브랜치의 번호 중복은 통합 시 정렬해야 한다. 기존 V1~V17이나 선행 작업의 SQL을 수정하지 않는다. `ops`는 Flyway를 끄므로 운영 적용은 별도 요청·절차가 필요하다. image_url을 필수로 가정하는 구버전 서버로의 코드 롤백은 새 null 행과 호환되지 않으므로, 적용 전에 이전 버전의 null 처리 호환성을 준비해야 한다.

대안은 92개 정적 이미지를 생성해 기존 필드만 쓰는 방식이다. 이 방식은 취법·구멍 상태·출처를 기계적으로 대조하기 어렵고 앱의 기존 그림 렌더링과 별도 자산을 관리해야 하므로 구조화 자료 추가를 권장한다.

## FE 연결과 검증

FE DTO는 nullable pattern을 읽고 legacy 이미지형 응답도 수용한다. pattern의 누락 필드·잘못된 enum·구멍 수·상태 표기·빈 원본 문구는 카탈로그 읽기 실패로 처리하고 UNKNOWN은 그대로 보존한다. 기존 내장 운지표와 그림 렌더링은 보존하고, 서버 자료가 내장 원본과 같은 음·취법·구멍을 가리키는지 확인한다. 선행 407의 명시적 대금 code·이체자 연결을 재구현하지 않는다.

### 후속 요청: 운지법 화면의 악기 목록

2026-11-03 사용자의 “그럼 그렇게 수정해줘” 요청으로 운지법 보기의 선택 목록은 CAT-01 `GET /api/instruments`를 기준으로 바꾼다. 처음에는 내장 enum 전체를 표시했지만, 이제 서버가 제공한 ID·이름·순서를 유지하고 앱이 그림을 지원하는 DANSO/SOGEUM/JEONGAK_DAEGEUM/SANJO_DAEGEUM만 표시한다. 일반 DAEGEUM이나 미지원 코드를 임의 대응하지 않는다. 다른 화면의 악기 선택 정책은 이번 변경 대상이 아니다.

- 진입 시 요청한 악기가 있으면 유지하고, 없으면 첫 지원 악기를 선택한다. 선택지 밖의 악기 전환은 무시한다.
- 목록 로딩·실패·지원 악기 없음은 별도 안내와 재시도로 표시한다. 이때 로컬 enum 목록이나 미선택 악기의 운지 그림으로 대체하지 않는다.
- 성공한 목록은 해당 ViewModel 동안 재사용해 악기 전환마다 CAT-01을 호출하지 않는다. 재진입은 새 목록을 조회하며 영구 오프라인 캐시는 추가하지 않는다.
- 음·운지 자료 조회 실패는 악기 목록 성공과 구분한다. 목록을 받았다면 해당 악기의 기존 내장 그림은 계속 표시한다.
- `fingering_charts.image_url`과 API `imageUrl`은 기존 이미지형 자료를 위해 유지한다. 목록의 기준 변경에 DB 열 삭제나 API 계약 변경은 필요하지 않다.

추가 검증: 서버 이름·순서·실제 ID, 미지원 코드 제외, 첫 선택 보정, 실패 재시도·빈 목록, 목록 캐시, 잘못된 선택 거부·이전 요청 취소를 검사하고 Compact/Medium/Expanded의 목록과 상태 안내를 확인한다.

완료 조건:

1. 네 악기의 12/20/30/30행 전체와 필터 결과가 원본·FK와 일치한다. 취법·구멍 순서·반 막음·단소 청태의 `10100`을 검증한다.
2. 산조의 source·설명·명목상 F 기준을 정악과 구분하고 정악 기본틀 공유의 한계를 보존한다.
3. 이미지형 기존 행·null 설명·비활성 악기404·미등록 pitch의 빈 목록·잘못된 ID400을 회귀 검사한다.
4. 실제 폐기 MySQL 8.4에서 마이그레이션·Mapper·JSON·FK·CHECK·공개 HTTP와 pitchId 필터를 검사한다. 운영 DB는 사용하지 않는다.
5. backend `test`의 관련 검사 후 필수 `clean build`와 `databaseTest`, FE 관련 common/Android host 검사를 수행한다. 실제 명령은 빌드 설정과 대조한다. iOS 실기기/UI 검수는 이 로컬 데이터 연결 검사의 통과로 보고하지 않는다.
6. OpenAPI·DB 계약·카탈로그 스펙을 실제 구현과 맞추고 전체 diff 자체 점검 후 Task 단위 커밋한다. 독립 리뷰는 별도 단계다.

V18은 원본의 버전별 스냅샷이다. 운지 원본이 바뀌면 과거 SQL을 덮어쓰지 않고 새 마이그레이션과 대응 검증으로 갱신한다. [구현·검증 기록](../knowledge/backend/builtin-fingering-catalog.md)을 함께 읽는다.
