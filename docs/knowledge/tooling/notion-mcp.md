# Notion API 명세 행 생성의 제목 필드

## 확인 결과

2026-10-30, S15P31E102-296의 `/webhooks/kakao/events` 행 생성에서 확인했다. 대상은 기존 API 명세 데이터 소스 `collection://80da0252-aa45-82ff-bd4d-87829106fa20`다.

`fetch`가 반환한 제목 속성은 스키마 키 `userDefined:URL`, 실제 이름 `URL`, 타입 `title`이었다. **SQL 조회에는 `userDefined:URL`, 페이지 생성에는 실제 이름 `URL`을 사용했을 때 성공했다.** 이 데이터 소스에서 관찰한 결과이며 모든 예약 이름에 일반화하지 않는다.

| 실행 | 입력 차이 | 결과 |
| --- | --- | --- |
| 기존 행 중복 조회 | SQL에서 `"userDefined:URL"`로 정확한 경로 검색 | 0행 |
| 최소 생성 | parent는 위 collection URL, properties는 `{"userDefined:URL":"/webhooks/kakao/events"}`만 지정 | INVALID_ARGUMENT, 생성 ID 없음 |
| 최소 생성 | 같은 parent에서 properties만 `{"URL":"/webhooks/kakao/events"}`로 변경 | 페이지1개 생성 |
| 속성·본문 추가 | 생성 ID로 POST·auth-service·In progress·담당자·명세 본문 갱신 | 성공 |
| 중복·속성 재조회 | SQL의 `"userDefined:URL"`로 검색 | 정확히1행, 지정한 상태·담당자·HTTP 일치 |

생성 성공 응답에는 실제 이름 `URL`을 스키마 키 `userDefined:URL`로 썼으며 이후에는 그 키를 사용하라는 경고가 있었다. 그러나 그 권장 키를 사용한 최소 생성은 위와 같이 실패했다. 조회용 이름과 쓰기용 이름을 혼동하지 말고, 이 경고를 무조건 따르며 생성 요청을 반복하지 않는다. MCP 내부의 정확한 실패 구현은 확인할 수 없다.

## 적용 방법과 범위

1. 데이터 소스를 fetch하고 실제 속성 이름과 조회용 별칭을 구분한다. 읽기용 시스템 `url`과 사용자가 만든 제목 `URL`은 다르다.
2. 정확한 경로로 중복을 조회한다. 생성 결과가 불명확하면 재조회하며, ID가 생겼으면 해당 ID를 보존하고 갱신한다.
3. 이 데이터 소스의 제목은 생성 요청에 `URL`을 사용한다. 기존 필드 이름이나 데이터베이스 구조를 바꿀 필요는 없다.
4. 상태·담당자·본문은 생성된 같은 페이지에 추가한다. 이 사례는 권한 변경·새 연결·브라우저/API 우회 없이 MCP만으로 해결했다.

생성한 [웹훅 명세](https://app.notion.com/409a0252aa4581c782a6cfe9d03ae1ab)와 [Task 계획](../../plans/S15P31E102-296.md)에 결과를 연결했다. Notion MCP의 fetch/create_pages/update_page/query_data_sources 실행 결과가 근거이며, 백엔드 코드는 변경하지 않았다.
