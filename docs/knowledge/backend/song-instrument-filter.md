# 곡 목록 악기 필터 구현·검증

작업: S15P31E102-408. 계약은 [곡 목록 악기 필터](../../specs/song-instrument-filter.md), 원본 악보·음원은 [카탈로그 API](../../specs/catalog-read-apis.md)를 따른다.

## 구현 선택

서버는 선택적 `instrumentId`의 형식과 활성 여부를 검사한 뒤 기존 게시·미삭제 조회에 파라미터 조건을 추가한다. 필터 생략의 의미·ID 순서·응답 JSON은 보존한다. 스키마나 음원 저장소를 바꾸지 않는다.

FE의 평가 곡 목록과 연습용 제공 곡 목록은 같은 Repository와 회원의 공통 악기 선택을 사용한다. `/api/instruments`의 실제 ID·이름을 사용하므로 정악대금과 산조대금을 enum 순번으로 합치지 않는다. 곡 상세·악보·음원에는 선택한 곡의 기존 songId를 전달한다. 악기 변경 때 곡·선택을 비우고 이전 요청을 취소하며 요청 세대도 검사한다. 현재 악기의 재시도, 평가 가능 조건, 연습 전용 곡을 보존한다. 최초 구현의 화면별 악기 칩은 같은 날 사용자 실기기 확인 후 제거하고 마이페이지 설정으로 통일했다.

## 재현 가능한 검사

2026-11-03, Windows·JDK21·MySQL8.4의 폐기 가능한 전용 DB에서 실행한다. 운영·공유 개발 DB를 사용하지 않는다.

- BE: `backend/`에서 `./gradlew.bat clean build databaseTest`. 실제 `TEST_DB_URL/USERNAME/PASSWORD`를 주입한다. 관련 검사는 `SongControllerTests`, `SongInstrumentApiDatabaseTests`다.
- FE: `frontend/`에서 `./gradlew.bat :shared:testAndroidHostTest :shared:jsTest :shared:wasmJsTest :webApp:wasmJsBrowserDistribution`. Android SDK와 Chrome이 필요하다. 관련 검사는 `ApiSongRepositoryTest`, `SongInstrumentFilterTest`다.
- 컨트롤러의 잘못된 ID 검사는 구현 전400 대신200으로 실패했다. FE 파라미터 검사는 선택 ID 대신null로 실패했고, 두 ViewModel 검사는 악기 목록·선택 전환 누락으로 실패했다. 구현 후 각각 통과했다.
- MySQL 검사는 활성 악기 네 개·곡 없는 활성 악기·비활성 악기를 독립 fixture로 만들고 실제 HTTP에서 필터·전체 조회·HIDDEN/DRAFT/삭제 제외·ID 순서·정수 버전·원본 score와 모범 음원 경로를 검사한다. 만료 쿠키를 넣어도 기존 공개 GET 접근을 보존한다.
- FE 검사는 서버 ID를 그대로 전송하고 잘못된 ID·다른 악기 응답을 거절하는지, 빠른 전환과 취소를 무시하는 늦은 응답에도 최신 선택을 유지하는지 확인한다. 검색 초기화·중복 탭·재시도·연습 전용 곡도 확인한다.

네 악기 공개 데이터의 배포는 선행 S15P31E102-407의 책임이다. 이 작업은 해당 마이그레이션을 중복 추가하지 않는다. 실제 음원 객체 재생·음악적 정확도·모바일 실기기·iOS·운영 적용은 이 자동 검사의 증명 범위에 포함하지 않는다.

JS에서는 테스트 대역이 즉시 응답하면 로딩 상태를 검사하기 전에 새 목록이 반영될 수 있다. 처음에는 이 시점 차이로 빈 목록 검사가 실패했다. `CompletableDeferred`로 응답 완료를 제어하여 요청 중 목록·선택 초기화와 완료 후 새 목록을 각각 검사한다. 제품 코드에 불필요한 지연을 추가하지 않는다.

407이 병합된 `develop`의 `6899998b`를 충돌 없이 통합했다. 별도 읽기 검토에서 서버 ID 전달·게시 조건·취소 및 늦은 응답 방어와 407의 `serverCode` 매핑 보존을 확인했고 추가 결함은 발견하지 못했다. 이 검토는 실행·실기기 검증과 구분한다.

최초 BE `clean build`가 통과한 뒤 최신 `develop` 통합에 영향받는 검사를 다시 실행했다. 통합 후 결과는 다음과 같다. 생략한 검사는 통과로 계산하지 않는다.

| 검사 | 통합 후 결과 |
| --- | --- |
| BE `build` | 일반 테스트 536개 통과 |
| BE `databaseTest` | 209개 중 208개 통과, 외부 corpus 1개 생략 |
| FE `testAndroidHostTest` | 653개 중 650개 통과, 기존 opt-in 3개 생략 |
| FE `jsTest` | ChromeHeadless에서 639개 통과 |
| FE `wasmJsTest` | ChromeHeadless에서 639개 통과 |
| FE `wasmJsBrowserDistribution` | 배포용 최적화·Webpack 패키징 성공 |

웹 검사는 공유 Kotlin daemon의 메모리 압박을 피하기 위해 실행 명령에만 `--max-workers=1 --no-configuration-cache -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs="-Xmx6144m -Dfile.encoding=UTF-8"`을 추가했다. 저장소의 빌드 설정은 변경하지 않았다.

## 네 악기 데이터와 실행 JAR의 연결

408의 실행 JAR을 별도 폐기 스키마 `eolssu_408_smoke`의 V1~V16과 localhost:18408에 띄우고, 407의 `5933efd7`에 있는 V17 시드 SQL을 이 스키마에만 적용했다. 408 소스 브랜치와 운영 DB에는 407 마이그레이션을 복사·적용하지 않았다.

각 활성 악기에 기존 단소 악보를 복사한 조회 경로 검증용 곡·합성 음원 메타데이터를 하나씩 넣었다. 원본의 악기 코드 필드만 해당 카탈로그 코드로 바꿨으며 음악적 이조·음역 검증 자료가 아니다.

| 카탈로그 코드 | 서버 instrumentId | 검증 songId | 악기별 곡 수 | 악보 / 음원 메타데이터 |
| --- | --- | --- | --- | --- |
| DANSO | 1 | 9940801 | 5 | 200 / 200 |
| SOGEUM | 2 | 9940802 | 1 | 200 / 200 |
| JEONGAK_DAEGEUM | 3 | 9940803 | 1 | 200 / 200 |
| SANJO_DAEGEUM | 4 | 9940804 | 1 | 200 / 200 |

위 ID는 이 폐기 DB의 관찰값이며 앱의 고정 매핑이 아니다. 전체 조회8개, ID 오류400·미등록404, 정수 버전·원본 score 동등성과 `songId/model-audio/play` 경로를 추가 확인했다. 실제 음원 파일은 만들거나 재생하지 않았다. 검증 후 해당 실행 JAR은 종료했다.
