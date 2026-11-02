# 기본음·시김새 M4A의 DB/API 연결

Task S15P31E102-364. 2026-11-02. [기능 계약](../../specs/reference-audio-catalog.md), [적재 절차](../../../infra/catalog/README.md).

최종 상태는 **기본음219개 연결·211개 미연결, 기준 음22개·단소 범위15개**다. V15 확장과 사용자 선택을 반영한 마지막 절이 현재 결과이며, 앞의30/141개 결과는 단계별 검증 이력이다.

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

pitches는 서비스의 율명/옥타브/반음 기준, reference_audio_assets는 악기·취법·주법·구간별 파일이다. 여러 파일과 시김새를 한 pitches행의 soundUrl로 압축하지 않는 물리적 분리는 적절하다. 최초 분할 때는 관계를 보류했으나, 아래 후속 요청에서 명목상 기본음 관계를 확정했다. 실측 검수·대표 파일 선정과는 구분한다.

R2의 endpoint는 S3 호환 프로토콜을 쓰는 R2 주소다. AWS S3 서비스를 따로 추가하는 설정이 아니다. 기존241/272/335와 [R2 검증 기록](../infra/r2-storage-verification.md)에 서버 설정·서명 읽기 검증 이력이 있다. 같은 계정의 endpoint와auto리전은 재사용할 수 있으며 인프라는 버킷/권한/비밀값을 확인해 주입한다. 학습 음원용 prefix의 영구 보관과 최소 읽기 권한은 개발 요구사항으로 전달한다. REFERENCE_AUDIO_STORAGE_*는 별도 물리 저장소를 강제하지 않는다. 실제 선택과 운영 적용은368에서 검증한다.

## MR 분리

366 M4A 변환 MR을 먼저 검토/병합하고 이 DB/API364를 이어서 검토한다. 이 MR에는 backend와 검증된 SQL 적재/운영 설정 예시가 포함된다. 프론트 구현은367, 실제 운영 적용은368이며 이 MR을 병합해도 음원이 자동 게시되지 않는다. 최초 분할 당시 backend/data 변환/FE 제품 트리는 검증 스냅샷 fa7f2849와 영역별로 같았으며, 아래 관계 보완을 후속으로 추가했다.

## 364 후속: 명목상 기본음 관계와 실제 30개 연결

사용자의 관계 보완 요청으로 물리적 테이블 분리는 유지하면서 V14 nullable FK를 추가했다. 기존 V13은 수정하지 않았다. `reference_audio_assets.pitch_id`는 `pitches.id`를 가리키며 `pitch_mapping_basis`와 함께 저장한다. 기본음만 연결할 수 있고, 자산 여러 개가 같은 pitch를 참조한다. 앱은 목록·상세의 `pitchId: string | null`과 선택 필터를 사용할 수 있다. 추가 운영 env는 없다.

대안은 모든 음원에 율명만으로 FK를 채우거나 시김새의 마지막 안정음을 같은 관계로 취급하는 것이었다. `pitches`는 현재 단소 1차 기준 11개만 갖고 있고, 다른 악기의 음역·조율 기준과 시김새의 시간적 의미가 다르므로 선택하지 않았다. 별도 [명시적 매핑](../../../infra/catalog/pitch-mappings/danso-basic-v1.json)을 사용해 명목상 관계와 실측 검수를 분리했다. CLI는 기존 manifest/파일을 읽기만 하며, assetId·datasetId·labelId·SHA-256·분류·취법·율명과 근거를 대조한 뒤 `(yul,octave)`로 대상 ID를 찾는다. 같은 파일을 다시 변환하거나 업로드하지 않는다.

[단소 라벨링](../data/danso.md)의 기본 5음과 상하 옥타브, [pitches.json](../../../data/reference/pitches.json), V5가 일치하는 30개를 먼저 연결했다. 평취 중·임·무=octave0, 황·태=octave1이고 역취는 +1이다. 각 평취 pitch에 4개, 역취 pitch에 2개 클립이 연결된다. 최초 검증에서는 나머지400개를 null로 남겼다. 다른 악기의 기존 근거까지 일괄 보류한 판단은 아래 재대조에서 정정한다. 산조 ‘본청 C’는 황종 C를 뜻한다고 확인된 근거가 아니므로 그 해석을 제외한다. 기존 `inferred`, 채점 제외, 원본 metadata는 그대로다.

검증 환경은 JDK21, 폐기 가능한 MySQL8.4 컨테이너 `eolssu-reference-audio-364-pitch`(localhost13364), 별도 DB `eolssu_pitch_test`/`reference_audio_import_test`/`eolssu_pitch_real430`이다. 서비스 DB·R2는 사용하지 않았다.

- 실패→성공: 잘못된 pitchId가 기존 API에서 무시되어 200이던 테스트가 실패했고, 변경 후 공통400으로 통과했다. 실제 MySQL 검사에서 발견한 문자열 collation 충돌은 바이트 비교로 수정한 뒤 재검증했다.
- `./gradlew.bat clean build databaseTest`: 단위435개·DB161개, 실패/오류/건너뜀0. V1~V14 Flyway 적용·재실행, 고아 FK·시김새·근거 없는 연결 거부, 여러 클립의 동일 pitch 조회와 null 응답을 포함한다.
- Python 적재/매핑 검사21개: 중복/파일 불일치/시김새/근거 누락, 기존 V13 행의 V14 확장, 없는 pitch/자산, 충돌 시 배치 전체 거부, 잘못된 `--force`에서도 부분 갱신 방지, 동일 SQL 재적용과 기존 import의 전체 행 보존을 확인했다.
- 실제 manifest 430개로 CLI를 실행하고 import→매핑→동일 매핑→동일 import를 적용했다. 결과는 430개·30연결·400null·기준 음10개·inferred430개·채점 가능0개·DRAFT430개·40,899,691바이트였다. 원본 manifest 해시는 위 기록과 동일하다.
- 최종 JAR를 localhost18364의 ops로 실행해 430개 DRAFT 비공개 → 테스트용 게시 후 56/107/121/146개 전체 ID 집합, 10종 pitch 필터, 상세의 문자열/미연결 null, metadata 원본 일치, 잘못된 ID400, OpenAPI nullable string/선택 필터를 확인했다. 기존302/900초/no-store 계약도 통과했다. 종료 시 테스트 행을 DRAFT로 복구하고 앱을 종료했다.

최종 JAR SHA-256은 `fd57637660c5c4556146445715742f115ea3f0f5e45b08c2191544fa3d1eae5b`이다. 작업 worktree의 Git 제외 `data/processed/reference-audio-relation-verification/`에 `backend-validation.log`, `db-verification.json`, `mr-http-verification.json`, `verify_http.py`와 생성 SQL을 남겼다. 운영 마이그레이션·명시적 매핑·R2 게시·실기기 청취는 후속368에서 수행하며 이 검증 결과로 대체하지 않는다.

## 기존 라벨 재대조: 기준 행 추가 전 141개 연결

이 절은 V15 기준 음 추가를 선택하기 전의 중간 검증 이력이다. 현재 매핑 파일과 완료 수량은 다음 절의219개 결과를 따른다.

사용자의 지적에 따라 현재 기준 행 누락과 라벨 근거 부족을 구분했다. 단소 외 음원에도 이미 취법·율명·옥타브 순서의 근거가 있었으므로 400개 전체를 보류한 것은 잘못이었다. 단소30개 매핑/근거를 바꾸지 않고 [정악대금27개](../../../infra/catalog/pitch-mappings/jeongak-daegeum-basic-v1.json), [소금61개](../../../infra/catalog/pitch-mappings/sogeum-basic-v1.json), [산조대금23개](../../../infra/catalog/pitch-mappings/sanjo-daegeum-basic-v1.json)를 별도 파일로 추가했다. 스키마·기준 음·백엔드·CLI 실행 코드는 변경하지 않았다.

정악대금은 [기존 조사](../data/jeongak-daegeum.md#취법율명-대응)와 세 기본음 그룹의 `analysisBasis`를 재사용한다. 각 소스2557/2558/2559의 tone007·008·010·011·012·014·015·017·018이 현재 기준 음에 대응해 각각9개다. 소금은 [기존 취법·율명 근거](../data/sogeum.md#취법율명-연결)대로 평취 임0·무0, 황1·태1·중1과 역취 임1·무1·황2·태2·중2를 연결한다. 각각8·6·6·6·6·6·6·6·5·6개로 합계61개이며 중청임을 임1로 축약하지 않는다.

산조는 [기존 음렬·옥타브 대응](../data/sanjo-daegeum.md)과 [국립국악원 대금 항목④](https://www.gugak.go.kr/ency/topic/view/108)의 “정간보에서는 전폐음을㑣으로” 표기 규칙(2026-11-02 확인)을 함께 적용한다. 저취 임−1, 황0의 명목상 옥타브 경계와 평취+1·역취+2를 추론한 것이며, 실제 F0·조율값을 검증했거나 전문가가 라벨을 검수했다는 뜻이 아니다. 원본2550/2551/2552에서 각각7/9/7개가 기존 기준 행에 대응한다. 본청 C를 황종 C로 치환하지 않는다.

| 미연결 사유 | 개수 | 근거/다음 범위 |
| --- | ---: | --- |
| 시김새 | 207 | 현재 FK는 기본음만 지원한다. 안정음·시간 순서 관계는 별도다. |
| 명목상 음은 알려졌으나 기준 행 없음 | 78 | 정악27·소금29·산조22. 현재11개 기준 행을 확대하는 작업은 포함하지 않았다. |
| 개별 라벨 대안 | 4 | 소금3개와 산조1개의 원래 evidence를 보존한다. |

소금 예외 ID는 `ngc-sogeum-2654.group-006`, `ngc-sogeum-2655.group-006`, `ngc-sogeum-2656.group-006`이다. 각 `labels.json`의 `groups[].evidence[id].sequenceDecision`과 `alternatives`에 협/고 대안이 있다. 산조 예외는 `ngc-sanjo-2552.region-004`이며 같은 evidence 위치의 `alternatives`에 기존 무 라벨과 비교하는 황/남 후보가 남아 있다. 이 네 구간을 자동 확정하거나 원음을 다시 분석하지 않았다.

새 데이터 회귀 검사는 기존30개만 있는 상태에서 목표141개와 불일치해 실패한 뒤, 실제 annotations의 행 ID·분류·취법·율명·그룹 근거·대안과 명시적 매핑을 대조해 통과했다. Python 전체26개가 통과했다. 실제430개 DB에 네 매핑을 각각2회 적용한 뒤 기존 import를 재실행해 141연결·289null·기준 음11개·inferred430·채점 가능0·DRAFT430·40,899,691바이트를 확인했다. 기존 단소 행 전체와 모든 파일 SHA/metadata/상태가 보존됐고 재실행 후 전체 행 스냅샷도 같았다. 앞 절의 백엔드 단위435·DB161 검증은 실행 코드가 동일하므로 재사용한다.

## 사용자 선택 후 최종 결과: 기본음219개·전역22음·단소15음

사용자가 “현재 API 방식 유지: 단소 범위 내15음을 모두 표시”를 선택해 V15에서 누락 기준11행을 추가했다. 기존 V5의11개 ID·반음과 단소의 범위 `[5,29]`를 보존하며 ID는 MySQL이 할당한다. 한자는 `data/src/scores/review_page.html`의 HANJA 표와 대조했다. 단소 전사용 `data/reference/pitches.json`, 기존 단소30개 매핑/근거, 서비스의 범위 조회 쿼리는 변경하지 않았다.

최종 연결은 단소30·정악54·소금90·산조45, 합계219개다. 위 중간 단계의 기준 행 누락78개를 모두 연결했고, 미연결211개는 시김새207개와 명시적 라벨 대안4개뿐이다. 신규 기준 음이나 명목상 FK를 추가한 것이 실측 주파수 검수·운지 검수·채점 승인이라는 뜻은 아니다.

- 실패→성공: 실제 HTTP 검사에서 단소 목록 예상15/실제11로 실패한 뒤 V15 적용 후 통과했다. 데이터 회귀도 기존141/예상219로 실패한 뒤 실제 annotations와 전체219매핑이 일치했다.
- `clean build databaseTest` 재실행: 단위435개·DB161개, 실패/오류/건너뜀0. 전역22·단소15·기존ID1~11/반음/한글 표기 보존과 Flyway V15 적용/재실행을 확인했다. V15와 충돌하던 황0/반음0 합성 테스트6곳은 사적인 시험용 음으로 옮겼으며 검증 목적과 정리 범위는 유지했다.
- Python 전체26개 통과. 실제 annotations의 분류·취법·율명·그룹 근거·개별 대안, 정악18음 순서, 소금의 중청임 구분, 산조의 명목상 옥타브, 남은211개 사유를 대조했다.
- 새 폐기 DB `eolssu_pitch_complete430`에 V1~V15, 기존430개 import와 단소 매핑을 적용한 뒤 네 매핑을 각각2회 적용하고 기존 import를 재실행했다. 219연결·211null·기준22개·단소범위15개·inferred430·채점 가능0·DRAFT430·40,899,691바이트였다. 기존 단소 행 전체, 파일 SHA·원본metadata·게시/검수 상태를 보존했고 재실행 후 전체 행 스냅샷도 같았다.
- 최종 JAR의 실제 ops HTTP에서 네 데이터셋 전체430개 목록·상세, 각 dataset/pitch 필터, 동일 pitch의 여러 클립, 전역22·단소15, 400/404, nullable 문자열/미연결null, OpenAPI와 기존302/900초/no-store 계약이 통과했다. API metadata는 DB에 저장된 JSON과 같았다. 기존 MySQL JSON은 일부 부동소수점 측정값의 마지막 자리를 정규화한다(예: encoding.peak의 약1.4×10⁻¹⁷ 차이). 이를 새 매핑 변경으로 취급하거나 원본 파일 해시가 바뀌었다고 해석하지 않는다.
- 별도 읽기 전용 리뷰에서 V15 키/한자/반음,219개 실제 manifest 대응, 정확한4개 예외, 기존 기준/단소 매핑 보존을 확인했고 조치가 필요한 결함은 없었다.

최종 JAR SHA-256은 `ed69688907d9ec534f70ca43e58bd5b90b03201c19e060038398347fbfcf09cf`이다. 같은 Git 제외 검증 폴더에 `complete-backend-validation.log`, `complete-db-verification.json`, `complete-http-verification.json`, `verify_complete_mappings.py`, `verify_complete_http.py`, `complete-mappings/*.sql`을 남겼다. HTTP 검증 후 테스트 행을 DRAFT로 복구하고 앱을 종료했다. 운영 DB·R2·배포·실기기 청취는 수행하지 않았다.
