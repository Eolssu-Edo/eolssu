# 공개 곡·악기 조회와 공통 악보 앱 변환

상태: 2026-10-29 Task261 앱 연결. 256의 MR !151과 후속 285/245/250/191을 통합했고, [서버 MR !166](https://lab.ssafy.com/s15-final/S15P31E102/-/merge_requests/166)의 CAT03~10을 소비한다. 활성 악기 API 구현은 BE288이 맡는다. 최신 검증·MR·차단 항목은 [앱 통합 기록](app-catalog-api-integration.md)을 따른다. 운영 배포·실기기 검증은 별도다.

## 근거와 경계

- [공통 악보 schemaVersion 1](../../../data/src/scores/score_data.schema.json): 표시 `columns/cells/units`와 재생·채점 `events`를 eventId로 연결한다. 시간은 정수 tick이며 박의 단위는 `music.bpmUnit`, 박 수는 tick/ticksPerBeat다.
- [곡 reference](../../../data/reference/songs.json): 난이도 1/2/3은 기존 FE 입문/초급/중급 대응이다. source는 URL이 아닌 자료 식별자다. 설명은 근거가 없어 null이다.
- [V6](../../../backend/src/main/resources/db/migration/V6__seed_danso_songs.sql): 승인된 공개 곡 4개·scoreVersion1. reference의 DRAFT를 임의 수정하지 않는다. 공개 상태 변경·원본 교정은 이번 변환 범위 밖이다.
- [ScoreJsonNotes](../../../backend/src/main/kotlin/com/ssafy/eolssu/backend/feature/analysis/infrastructure/persistence/ScoreJsonNotes.kt): NOTE만 순서대로 0부터 번호를 부여한다. REST와 이음 표시를 새 음표 번호로 만들지 않는다.
- S15P31E102-256 공개 곡 API와 281의 데이터 검수는 develop에 병합됐다. 245의 세션·업로드 연결도 병합된 구현을 재사용한다. 261은 이 API를 재구현하지 않고 실제 응답을 소비한다.

## 실제 연결 계약

모든 응답은 기존 `{httpStatus,isSuccess,message,code,result}` envelope를 쓴다. ID는 DB Long을 10진 문자열로 표현한다. 숫자로 내려 JS에서 정밀도를 잃거나, 제목/원본 sourceId로 DB ID를 대체하지 않는다. 목록은 안정된 ID 순서로 반환한다. 현 4곡에 임의 페이지·검색 API를 추가하지 않는다(2026-11-02 S15P31E102-345 보류, [재개 조건](../../plans/S15P31E102-345.md)).

| 경로 | result | 조건 |
| --- | --- | --- |
| GET /api/instruments | `{items:[{instrumentId,code,name}]}` | 활성 악기만. 모르는 code를 단소로 바꾸지 않는다 |
| GET /api/songs | `{items:[SongItem]}` | PUBLISHED + deleted_at IS NULL. isScorable=false도 공개 곡이면 포함 |
| GET /api/songs/{songId} | `SongItem` | 목록과 같은 공개 조건. 없는/비공개/삭제 곡은 404 |

`SongItem`은 문자열 songId·instrumentId, title, nullable description, difficulty, defaultBpm, isScorable, scoreVersion, noteCount, `jangdan{jangdanId,name,bpmUnit,pattern{beats,group}}`, `score` 원본 JSON 객체다. 목록 항목은 S15P31E102-419부터 `score`를 싣지 않으며, 앱은 목록의 `score`를 선택값으로 받고 내부 목록 모델은 요약만 유지한다. 목록별 상세 N+1 요청은 하지 않는다. 최초 제안의 flat jangdanName·instrumentCode·scoreData 필드는 실제 계약으로 대체했다.

상세 응답의 scoreVersion·noteCount·장단/BPM 단위와 원본 score의 값이 다르면 실패 상태로 보낸다. 이제 상세 화면은 CAT02 메타데이터와 CAT04 `/api/songs/{songId}/score`를 함께 조회한다. 두 응답의 곡·버전·박 단위·음표 수와 원본 악보, CAT04의 eventId·noteIdx·tick·위치를 대조한다. 실패를 샘플 악보로 대체하지 않는다.

악기 선택은 이 목록의 ID를 기존 `PATCH /api/users/me/preferences`로 저장한다. 200 응답의 instrumentId를 확인한 뒤 로컬 선택에 반영한다. signup·/me·preferences의 실제 기존 계약은 유지한다. seed의 `1`이나 enum 순서로 ID를 정하지 않는다. 악기 목록이 없는 동안 선택을 저장 성공으로 표시하지 않는다. 모르는 ID/code·실패·로그아웃 때 표시 선택을 null로 비워 이전 계정의 악기로 표시하지 않는다. 늦은 /me 또는 목록 응답이 후속 선택/로그아웃을 덮지 않도록 revision을 검증한다.

모범 음원은 CAT05 `/api/songs/{songId}/model-audio`의 현재 악보 버전과 일치하는 공개 메타데이터를 사용한다. 등록이 없으면 404 안내를 표시한다. data playback CLI 생성은 정석 연주·권리 검수를 대신하지 않는다. 가짜 URL·재생 성공을 만들지 않는다.

## 앱 변환과 필수 검사

목록은 `SongSummary`, 상세는 `Song`과 `CommonScoreData`로 분리한다. 목록마다 모든 악보를 요청하지 않는다. display unit의 row/col, 원본 eventId, tie, 숨·시김새 메타데이터와 정수 tick을 보존한다. 원본 악보의 column은 장단 1주기와 동일하다고 가정하지 않는다.

| 고정 입력 | NOTE 수 | column×정간 | ticksPerBeat/ticksPerJeonggan |
| --- | ---: | --- | --- |
| 아리랑 p173 | 61 | 4×12 | 3/3 |
| 도라지타령 p171 | 91 | 28×3 | 3/3 |
| 밀양아리랑 p172 | 85 | 16×3 | 3/3 |
| 늴리리야 p174 | 68 | 6×12 | 6/2 |

이 값은 V6의 읽기 전용 파싱 결과이며 281의 음악적 검수나 운영 DB 확인을 대체하지 않는다. 테스트는 이음/쉼/12정간 보존, NOTE-only idx, 다른 tick 비율, 잘못된 schema와 참조, 빈 목록/404/오프라인/재시도/선택 보존을 다룬다. 실제 API·서버·Android 실행 결과는 Task261 계획과 후속 증거에 따로 기록한다.

## 최초 연결 실행 증거와 한계 (versionCode3, 후속 검사로 확대하지 않음)

격리 MySQL 8.4.13과 실제 Spring ops 서버 `127.0.0.1:18081`에서 곡 4개와 활성 악기 1개를 읽었다. `CatalogLocalServerTest`는 EOLSSU_CATALOG_TEST_URL이 있을 때만 이 서버에 접근하며, 미지정은 명시적 skipped다. 운영/공유 DB는 사용하지 않는다. BE 기본318개·DB100개가 통과했고 FE 통합검사는 Task261 후속 결과표를 따른다.

Android debug만 `-Peolssu.debugApiBaseUrl=http://127.0.0.1:18081`와 `adb reverse tcp:18081 tcp:18081`로 이 서버를 사용한다. debug network-security-config는 loopback만 평문을 허용한다. release BuildConfig는 production HTTPS로 고정되고 이 debug manifest는 포함되지 않는다. 테스트의 첫실행 완료 fixture는 공개 곡 화면 진입을 위한 것으로 로그인·동의·녹음·분석 성공 증거가 아니다.

운영 GET /api/songs는 병합 확인 직후 404였다. 배포는 수행하지 않았다. 245가 연결되지 않은 실제 녹음 업로드·분석·결과/이력, 공식 서명키·정책·심사 계정과 실기기 제한은 이전 release 차단 목록을 그대로 적용한다.


최종 후속 검사: 371개/59suite, 실패·오류·skip0, lint error0/경고각18, debug/release APK/AAB와 JS/Wasm 컴파일 통과. versionCode3의 로컬 검증 서명 패키지를 API24 폰·API37 폰/태블릿에 설치했고 AAB 유래 APK 설치본 해시를 확인했다. 곡 실제 ID/악보는 격리 서버 debug에서 확인했으며 운영 배포/인증부터 결과까지는 여전히 차단이다. 긴 이음 박 전환·지연 회원 응답·큰 글꼴 분할 문제의 독립 검토 지적을 수정했다. 200%에서 배지·머리글·태블릿 하단 정간과 다음 각 접근을 관찰했고 실제 TalkBack/오디오·실기기는 미검증이다. 상세 표와 재현 증거는 Task261 계획 및 보존된 android-release-catalog-20261029 결과 묶음을 따른다.
