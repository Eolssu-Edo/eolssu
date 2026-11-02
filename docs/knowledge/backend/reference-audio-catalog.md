# 기본음·시김새 M4A의 DB/API 연결

Task S15P31E102-364. 2026-11-02. [기능 계약](../../specs/reference-audio-catalog.md), [적재 절차](../../../infra/catalog/README.md).

## 선택과 범위

기존 `pitches`는 서비스의 율명/옥타브 기준이며 특정 악기·취법의 실제 녹음 ID가 아니다. 4악기 430개에는 같은 율명에 여러 주법·발음 구간이 있으므로 공통 soundUrl 한 개를 임의 선택하면 데이터가 손실된다. 새 `reference_audio_assets`와 dataset/label별 조회를 추가했다. 기존 CAT-06과 시김새 화면의 합성 소리는 바꾸지 않았다. 이후 사용자 추가 요청으로 운지법·기초 학습의 실제 녹음 선택을 후속 Task367에서 연결한다. 임의 대표음 선택은 계속 하지 않는다.

파일은 원본 WAV와 배포용 M4A를 구분한다. assetId는 datasetId와 원래 labelId의 SHA-256이며, objectKey에는 자산 ID와 파일 SHA-256을 함께 사용한다. 같은 라벨의 원본을 덮어쓰지 않고 변환/출처 정보를 추적한다. DB에는 만료되는 서명 URL을 저장하지 않는다. 게시 자산의 상대 `/play` 경로가 요청 시 새 서명 URL을 발급하며 자산용 설정은 사용자 녹음 설정과 독립적이다.

운영 연결은 `M4A 업로드 → 같은 objectKey로 DRAFT 적재 → 실제 객체 확인 → PUBLISHED 전환 → 앱의 목록 조회·선택·/play 요청` 순서다. V13은 빈 테이블만 만든다. API 배포만으로 430개 음원이 자동 게시되지는 않는다.

| 서버 환경변수 | 용도 |
| --- | --- |
| `REFERENCE_AUDIO_STORAGE_ENDPOINT` | R2의 HTTPS S3 API 주소 |
| `REFERENCE_AUDIO_STORAGE_BUCKET` | 영구 학습 음원을 담은 버킷 |
| `REFERENCE_AUDIO_STORAGE_ACCESS_KEY_ID` | 해당 버킷 읽기용 키 ID |
| `REFERENCE_AUDIO_STORAGE_SECRET_ACCESS_KEY` | 읽기용 비밀 키, 프론트/저장소에 포함하지 않음 |
| `REFERENCE_AUDIO_STORAGE_REGION` | 서명 리전, 기본값 `auto` |

이 값은 서버 실행 설정이며 사용자에게 받는 URL 입력란이 아니다. 모두 준비되지 않으면 목록·상세는 조회할 수 있지만 `/play`는503이다. 자산이 없거나 DRAFT이면404다. 기존 `RECORDING_STORAGE_*` 설정 및 녹음 삭제 작업과 연결하지 않는다.

## 실제 입력과 변환 결과

최신 라벨링 결과의 schemaVersion 2 catalog 4개와 WAV를 입력으로 사용했다. 국립국악원 출처·이용 조건, 라벨의 inferred 및 채점 제외 상태를 metadata에 보존했다. AAC-LC 목표 192kbps, 원래 mono·44.1/48kHz, 리샘플링·음고 보정·정규화·재분할 없음.

| 데이터셋 | M4A |
| --- | ---: |
| danso | 56 |
| jeongak-daegeum | 107 |
| sanjo-daegeum | 121 |
| sogeum | 146 |
| 합계 | 430 |

- 입력 WAV 183,845,674바이트 → M4A 40,899,691바이트. 원음 합계 1,973.870847초.
- FFmpeg/ffprobe 9.0.2, 생성·전체 디코드 검증 150.20초, 실패 0.
- M4A 430개 codec/rate/channels/시작시각/길이·전체 float PCM의 유한값 확인. 최대 디코드 피크 0.958663.
- WAV 430개와 catalog 4개 SHA-256 불변, M4A 430개 파일·ID·SHA 대응 재조회.
- manifest SHA-256 `5450071ea3f8cf6c53e7a20c3800be015cdc755717213c339475ee3696c0a6fb`.
- 생성물: Git 제외 `data/processed/reference-audio-v1/manifest.json`, M4A, `import-draft.sql`. 자동 검사는 전문가 청감/율명 정답 검수를 대신하지 않는다.

## 적재 검증

MySQL 8.4.13의 폐기 가능한 로컬 DB에 V12를 적용하고 실제 manifest 430개를 DRAFT로 적재했다. 같은 SQL 재실행 후에도 430개·채점 가능 0개·파일 바이트 합계 40,899,691로 동일했다.

적재 도구는 경로 이탈·파일 손상·ID/키 불일치·중복·검수 상태 승격·출처 누락을 거절한다. 사용자 문자열은 UTF-8 hex SQL 값으로 인코딩한다. 기존과 같은 행은 게시 상태를 보존하며, 다른 내용과 신규 행이 섞인 입력은 신규 행도 넣지 않는다. MySQL CHECK 오류 후 계속 실행하는 잘못된 클라이언트에서도 삽입 조건이 빈 검증 테이블을 확인해 신규 행이 생기지 않음을 검사했다.

실제 CLI 적재에서 ASCII 해시 컬럼과 한글 분류 컬럼의 CONCAT 비교가 서로 다른 collation으로 실패하는 것을 확인했다. V12의 objectKey CHECK를 바이트 비교로 고쳐 JDBC와 mysql CLI 모두 같은 키 규칙을 적용했다.

검증: 신규 변환 검사 10개, 기존 data 검사 90개(환경별 45+45), 적재 단위 9개·MySQL 3개 통과. Compose 구성 검사 통과.

백엔드 최종 `clean build`의 단위 검사 433개, `databaseTest` 150개가 실패·건너뜀 없이 통과했다. 테스트 DB 기본 collation은 기존 서비스와 같은 utf8mb4_0900_ai_ci, 새 자산 테이블만 정확한 라벨 구분을 위해 as_cs다. 캐시된 Spring 컨텍스트의 연결 수 때문에 폐기 가능한 테스트 MySQL의 max_connections만 1000으로 조정했다. 운영 설정에는 적용하지 않았다. 잘못된 테스트 DB 기본 collation으로 발생한 중간 실패는 올바른 새 DB에서 전체 재검증했다.

OpenAPI 전체 경로 검증에서 기존 기대 개수 41과 실제 기존 경로 45의 차이, HTML/파일 4개 경로의 문서 누락을 확인했다. 새 3개를 포함한 48개 실제 매핑과 문서의 집합 일치를 유지하고, 날짜 정규식 경로를 OpenAPI 표현으로 정규화했다. 기존 HTML/파일 응답을 JSON 성공 envelope로 잘못 바꾸지 않도록 Content-Type·오류 설명과 검사를 보완했다. 기존 API 실행 동작은 변경하지 않았다.

## 실제 HTTP 연결

새 백엔드 JAR를 localhost의 ops 프로필과 별도 MySQL 테스트 DB로 실행했다. 설정은 합성 S3 자격증명과 example.test 엔드포인트이며 실제 R2에는 요청하지 않았다. 단순 Controller 대역과 구분되는 실제 서버·MyBatis·SQL·AWS 서명기 경로다.

- DRAFT 430개일 때 네 dataset의 목록은 모두 0개, 자산 상세는 404.
- 테스트 DB에서 정확한 430개 ID만 게시한 뒤 목록 ID 집합과 개수(56/107/121/146)가 manifest와 일치.
- 실제 상세 응답 metadata와 manifest metadata가 같고 SHA-256이 보존됨.
- dataset/category/technique/register/yulmyeong 복합 필터 결과가 원본 목록의 대상 1개와 일치.
- 재생 경로는 302, Cache-Control=no-store, DB의 정확한 objectKey에 대한 900초 서명 GET URL을 반환.
- 확인 후 테스트 행은 DRAFT로 복구하고 임시 앱을 종료함.

검증 JAR SHA-256: `c3c6142feafd59a4c84364c722d6e3dcff5a7701ba994626596097e60737890b`. 로컬 실행 증거는 생성 폴더의 `http-verification.json`, `verify_http.py`다. 실제 R2 객체 다운로드·앱 청취를 검증한 것으로 확대하지 않는다.

## 최신 develop 통합 후 재검증

MR 준비 중 develop `9ea74044`에 소셜 가입 V12가 추가됐다. 아직 운영에 적용하지 않은 음원 스키마를 `V13__reference_audio_assets.sql`로 옮기고 적재 테스트·운영 안내를 함께 수정했다. 위 V12·433/150·48경로·JAR 해시는 최초 구현 검증 기록이다.

통합본에서 `clean build` 433개, `databaseTest` 160개, 적재 검사 12개가 실패·건너뜀 없이 통과했다. 새 MySQL DB에 V1~V13 적용·재실행과 OpenAPI 51개 실제 경로의 문서 일치를 확인했다. 같은 430개 manifest를 두 번 적재해 DRAFT 430개·40,899,691바이트를 확인하고, 위 HTTP 검증도 새 JAR로 다시 통과했다. 변환기와 원본은 그대로여서 M4A를 재생성하지 않았다.

통합 검증 JAR SHA-256: `d06b00ab180a359d599c9bd91f7c527bd136441e5460b2e129395eb23440882e`. 로컬 증거는 생성 폴더의 `mr-backend-validation.log`, `mr-http-verification.json`, `verify_http_mr.py`다. 운영 DB·R2에는 적용하지 않았다.

## 운영 경계

조회 시점 운영 서버는 R2 접근이 가능하지만 사용자 녹음 저장 방식은 local이었다. 이 구현은 운영 R2 게시·DB 마이그레이션·서버 재시작·기존 녹음 정책을 변경하지 않았다. 최종 게시 전 실제 버킷 보관 정책, 객체 다운로드 해시와 재생, 앱의 상대 URL 해석을 확인해야 한다. R2 lifecycle/CORS 관리 조회는 당시 키의 403으로 재확인하지 못했으며 기존 문서의 설정을 현재 검증으로 대체하지 않는다.

## 테이블 분리와 기존 R2 재사용에 대한 보충

pitches는 서비스의 율명/옥타브/반음 기준, reference_audio_assets는 악기·취법·주법·구간별 파일이다. 여러 파일과 시김새를 한 pitches행의 soundUrl로 압축하지 않는 물리적 분리는 적절하다. 다만 검수된 음높이 대응이 확정되면 악기·취법·주법을 포함한 관계를 서버에서 명시적으로 관리해야 한다. 현재 inferred 라벨은 검색 후보이며 음높이 FK나 대표 파일을 확정하지 않았다.

R2의 endpoint는 S3 호환 프로토콜을 쓰는 R2 주소다. AWS S3 서비스를 따로 추가하는 설정이 아니다. 기존241/272/335와 [R2 검증 기록](../infra/r2-storage-verification.md)에 서버 설정·서명 읽기 검증 이력이 있다. 같은 계정의 endpoint와auto리전은 재사용할 수 있으며 인프라는 버킷/권한/비밀값을 확인해 주입한다. 학습 음원용 prefix의 영구 보관과 최소 읽기 권한은 개발 요구사항으로 전달한다. REFERENCE_AUDIO_STORAGE_*는 별도 물리 저장소를 강제하지 않는다. 실제 선택과 운영 적용은368에서 검증한다.

## MR 분리

366 M4A 변환 MR을 먼저 검토/병합하고 이 DB/API364를 이어서 검토한다. 이 MR에는 backend와 검증된 SQL 적재/운영 설정 예시만 포함한다. 프론트 구현은367, 실제 운영 적용은368이며 이MR을병합해도음원이자동게시되지않는다. backend/data변환/FE제품트리는 기존검증스냅샷 fa7f2849와 영역별로동일하다.
