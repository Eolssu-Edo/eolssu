# 기본음·시김새 운영 게시 기록

관련 작업은 S15P31E102-368이며 [스펙](../../specs/reference-audio-publication.md)과 [계획](../../plans/S15P31E102-368.md)을 따른다. 이 기록은 로컬 통합 검증과 운영 적용 결과를 구분한다. 앱 재생 UI는 S15P31E102-367 범위다.

## 통합 배포 사전 점검

2026-11-02 develop `2a0d4d9b`에는 `V13__reference_audio_assets.sql`과 `V13__learning_tracks_and_progress.sql`이 함께 있었다. Flyway는 동일 버전 두 파일을 허용하지 않는다. 먼저 병합된 오디오 V13을 보존하고 학습 파일·사전 확인 이름을 V14로 옮긴다. 학습의 36개 시드와 DDL 내용은 그대로 유지한다.

학습 V14는 기존 `lessons`·`lesson_progress`가 비어 있고 삭제할 V1 제약·인덱스가 있어야 한다. `chk_v14_preconditions` 실패는 학습 DDL 전에 멈추지만 MySQL DDL 전체가 트랜잭션으로 되돌아가는 것은 아니다. 아래 운영 적용에서는 두 테이블이 모두 0행임을 확인한 뒤 진행했다.

## 로컬 검증

검증일은 2026-11-02이며 MySQL 8.4 컨테이너 `eolssu-reference-audio-364-pitch`의 새 DB `eolssu_audio_deploy_368_tests`를 사용했다. 기존 V13 검증 DB는 변경하거나 repair하지 않았다. 비밀값은 기존 컨테이너 환경에서 프로세스에만 주입했다.

- 수정 전: 기존 `DatabaseIntegrationTests`의 마이그레이션 검사 1개를 실행해 `Found more than one migration with version 13`으로 실패함을 확인했다. 두 V13 파일이 오류에 함께 표시됐다.
- 수정 후: 저장소 루트에서 `./backend/gradlew.bat -p backend clean build databaseTest --console=plain` 실행, 2분 25초, 종료 0. 단위 483개·DB 184개가 실패·오류·건너뜀 없이 통과했다. 전체 DB 검사에는 마이그레이션 재실행·검증과 실제 HTTP OpenAPI의 업무 API 58개 일치가 포함된다.
- 최종 DB 읽기: Flyway V1~V14 모두 성공, V13 오디오·V14 학습 파일명 확인. 기준 음 22개, 단소 범위 15개, 학습 트랙 4개 각각 9행, 진도 0행, 오디오 카탈로그 두 테이블 존재.
- 원본 대조: 오디오 V13은 Git 원본과 동일하다(SHA-256 `ecc8c110a1497ce27a3951dd395d4d278f10a065e99313c0e6b0cd5391a9a646`). 학습 SQL은 버전용 임시 테이블·제약 이름과 설명 이외에 달라진 내용이 없다. 카탈로그 importer·매핑·공개 API 구현도 수정하지 않았다.
- 생성 JAR: `backend/build/libs/eolssu-0.0.1-SNAPSHOT.jar`, 72,085,473바이트, SHA-256 `a1e31e02b6665683061b2f740909464db8f933324d939026da7f10116406ba0c`.
- 근거 위치: ignored `data/processed/reference-audio-deploy-368-verification/` 아래 `duplicate-v13-red.log`·`duplicate-v13-red.xml`, `backend-validation.log`, `validation-summary.json`, `migration-preservation.json`, `final14-database-state.tsv`. JUnit XML은 `backend/build/test-results/{test,databaseTest}/`에 있다. 검증 후 전용 컨테이너를 정지했다.

초기 기대대로 학습 마이그레이션 번호만 조정해 통합 기동 충돌을 해소했다. 원래 기능·데이터 계약에서 추가 변경은 없다. 이 결과는 로컬 통합 검증이며 운영 게시 성공을 뜻하지 않는다.

## 최신 develop 통합 검증

`ba888d04`에는 최근 점수 추이 API(346)와 학습 V14 rename이 추가됐다. upstream의 V14는 내부 사전 확인 이름·설명이 v13으로 남아 있어, 이미 적용한 `00540403`의 V14를 유지했다. 보존한 Git blob은 `251ad94bef38550d3d6e887299658caec0388fe6`, SHA-256은 `699162e013324db46b2598f70d52ad5361bc237610dc2ee5f0a230e5cdfac7ee`다. README는 V14 안내, OpenAPI 검사는 새 경로를 포함한 59개로 통합했다.

같은 명령 `./backend/gradlew.bat -p backend clean build databaseTest --console=plain`을 이미 V14가 적용된 `eolssu_audio_deploy_368_tests`에 실행했다. 2분 40초, 종료 0, 단위 498개·DB 187개 모두 통과(실패·오류·건너뜀 0). `flyway.migrate().migrationsExecuted == 0`과 `flyway.validate()` 검사가 통과했고 Flyway 이력의 installed_rank·version·script·checksum·success 전후 TSV가 동일했다. checksum repair나 기존 데이터베이스 재생성은 하지 않았으며 전용 컨테이너를 정지했다.

- 통합 JAR: 72,129,238바이트, SHA-256 `61c40605e443e493526799ec08e3b9ac997403146c07dc3ef602be50b073e5d2`.
- 근거: 위 ignored 검증 폴더의 `merged-backend-validation.log`, `merged-validation-summary.json`, `merged-before-flyway.tsv`, `merged-after-flyway.tsv`. 이력 TSV SHA-256은 양쪽 모두 `6cba14ca0d23b807816f112080ad0e5f32c3ea63e26ceef5c233e80e24d4e673`이다.
- 게시 helper는 같은 폴더의 `publish_verify.py`에 두고 루트 배포 담당자에게 인계했다. 서명 URL을 기록하지 않으며 원본 metadata는 양쪽을 MySQL native JSON으로 저장한 표현으로 비교한다. canary 8개 다음 전체 430개를 게시하고 실패 시 초기 DRAFT의 ID·해시·object key가 같은 행만 DRAFT로 복구한다. 로컬 self-test 5개·실제 pinned manifest 430개 검사와 독립 읽기 전용 재검토를 통과했다. 운영 실행 결과는 아래에 별도로 기록한다.

## 운영 적용

2026-11-02 16:06:55 KST(07:06:55 UTC)에 edo1 게시를 완료했다. 사용자 요청대로 서버 게시를 수행했고 앱 내부 재생·출처 안내·실기기 청취는 FE367로 넘겼다. FE MR !214의 병합이나 앱 검증을 서버 게시 완료로 간주하지 않는다.

### 고정 입력과 R2

- 원본 카탈로그: BE364의 커밋 `2553450`, manifest SHA-256 `5450071ea3f8cf6c53e7a20c3800be015cdc755717213c339475ee3696c0a6fb`.
- 배포 패키지: 40,939,310바이트, SHA-256 `b40a186e71c05d3408cdeb60be201e4599b4ebce4763551ce4f74d7050ceb02d`. 430개 M4A의 합계는 40,899,691바이트다.
- 기존 비공개 버킷 `eolssu-recordings`의 `reference-audio/v1/{datasetId}/{assetId}/{sha256}.m4a` 경로에 430개를 새로 업로드했다. 기존 `recordings/` 객체를 변경하거나 삭제하지 않았다. 버킷 공개 정책·권한·토큰을 새로 만들지 않았다.
- PUT에 `If-None-Match: *`를 서명해 기존 객체 덮어쓰기를 차단했다. 기존 서버 Boto3 1.34.46이 새 입력 필드를 지원하지 않아 공식 before-call/before-send 훅으로 조건 헤더와 서명 포함 여부를 검사했다. 글로벌 패키지 변경은 없다.
- 전체 430개를 SDK GET으로 내려받아 SHA-256·크기·`audio/mp4`를 대조했다. 전체 응답에 만료 헤더가 없었다. R2 문서에 따르면 새 객체에는 해당 삭제 규칙의 만료 헤더가 즉시 반영되므로, 이번 업로드가 녹음용 30일 삭제 대상이 아님을 객체 단위로 확인했다. [조건부 PUT](https://developers.cloudflare.com/r2/api/s3/extensions/), [수명 주기](https://developers.cloudflare.com/r2/buckets/object-lifecycles/), [Boto3 이벤트](https://docs.aws.amazon.com/boto3/latest/guide/events.html#before-call).
- 현재 토큰으로 버킷 lifecycle 조회는 403이었다. Cloudflare 대시보드의 현재 공개 설정은 이번 실행에서 확인하지 못했다. 서명 없는 S3 객체 GET은 400으로 거부됐지만 이것만으로 모든 공개 도메인 설정을 검증했다고 보고하지 않는다.

### DB와 읽기 설정

DB 변경 전 실제 상태는 V1~V12 성공, pitches 11개, 오디오 테이블 없음, lessons·lesson_progress 각각 0행이었다. 기존 DB·설정의 edo2 수신을 확인한 뒤 `00540403`의 고정 JAR로 한 번만 Flyway를 실행했다. ops 앱의 자동 마이그레이션 설정은 바꾸지 않았다.

| 운영 반영 결과 | 값 |
| --- | --- |
| Flyway | V1~V14 성공, 실패 0 |
| 음원 V13 checksum | 867008972 |
| 학습 V14 checksum | 1541415930 |
| 음원 자산 | 430, 최종 PUBLISHED |
| 기본음 FK 연결 | 219개 |
| 시김새 역할 연결 | 207개 자산, 315개 관계 |
| 미연결 | 라벨 대안이 남은 기본음 4개 |
| 검수·채점 | 전체 inferred, eligibleForNoteScoring=false |
| 보존 확인 | 기존 11개 기준 음 전체 값, 악기 음역, 원본 metadata·연결 근거 |

기본음 4개의 대안은 [카탈로그 안내](../../../infra/catalog/README.md#명목상-기준-음-연결)에 기록한 그대로다. 이번 게시가 그 라벨의 최종 판정이나 채점 정답 승격은 아니다.

서버 `.env`와 관리 Compose에 `REFERENCE_AUDIO_STORAGE_ENDPOINT`, `BUCKET`, `ACCESS_KEY_ID`, `SECRET_ACCESS_KEY`, `REGION` 5개를 연결했다(각 이름은 `REFERENCE_AUDIO_STORAGE_` 접두사 포함). 기존 `RECORDING_STORAGE_*`의 실제 R2 설정을 서버 내부에서 재사용했다. 새 S3 저장소나 공개 주소는 필요 없다. 변경 당시 실행 JAR·다른 서비스 ID·MySQL 볼륨을 보존하고 백엔드만 재생성해 healthy와 readiness UP을 확인했다. 비밀값은 증적에 저장하지 않았다.

작업 중 Jenkins가 develop `12b0f373`을 자동 배포했다. 기존 `2a0d4d9b`와 reference audio 관련 클래스·리소스 20개가 바이트 단위로 같고 5개 env가 유지됨을 확인했다. 마지막 확인 JAR SHA-256은 `a8187ab345ebd4063fca05872376e28e63b61f34c481f292c73218c4d51613c4`다. 이 브랜치에도 `12b0f373`을 통합했으며 `4cfcf4c5` 이후 변경은 upstream FE 운지 표기 5파일뿐이라 백엔드·인프라 검증을 반복하지 않았다.

운영에 적용한 V14와 upstream의 V14는 설명·임시 검사 이름이 달라 체크섬이 다르다. 이 MR은 적용본의 정확한 바이트를 보존한다. 이후 Flyway를 실행하기 전에 이 소스가 develop에 병합돼야 한다. 이력 repair로 차이를 숨기지 않는다.

### 게시와 HTTP 검증

원본 metadata를 MySQL JSON 표현으로 대조해 430개 전부 일치함을 확인했다. 8개 상태 변경 SQL을 실제 트랜잭션 안에서 실행 후 ROLLBACK해 원래 스냅샷 보존을 먼저 검사했다. 이후 악기별 기본음·시김새 1개씩 총 8개를 시험 게시하고 검증을 통과한 뒤 430개를 게시했다. 대상 ID·SHA-256·object key·원본/관계 fingerprint를 잠금 상태에서 검사하고 status만 변경했다.

- 4개 목록의 정확한 ID·응답 필드: 단소 56, 정악대금 107, 산조대금 121, 소금 146.
- 430개 상세 응답의 원본 metadata, 기본음 FK, 역할 관계·근거 경로 일치.
- 430개 `/api/reference-audios/{assetId}/play`: 302, `Cache-Control: no-store`, 올바른 버킷·객체의 서명 URL, `X-Amz-Expires=900`.
- 서명 URL의 실제 GET 430개: 200, `audio/mp4`, 전체 크기·SHA-256 일치, 만료 헤더 없음. 대표 8개 `Range: bytes=0-31`: 206과 본문 일치.
- 최종 430개 모두 PUBLISHED, status 이외 fingerprint와 근거 보존, readiness UP. 900초 실제 경과 후 만료 검사는 이번 실행에서 반복하지 않았다.
- 07:05:37~07:10:33 UTC 관측: backend root 전용 syslog의 새 25줄에서 ERROR/WARN 0. backend/frontend/mysql/ai 모두 healthy. readiness·단소 목록·약관·웹은 200(각각 92/667/237/19ms, 단일 요청 관측치이며 성능 기준은 아님).
- 후속 자동 재생성 후 07:11:17 UTC에 동일 JAR·카탈로그 20항목·env, 전체 DB fingerprint·4개 목록 및 대표 8개 실제 GET/Range를 다시 확인했다.

### 백업·복구·증적

공용 배포 lock 아래 변경 전 DB·설정을 보관했다. 변경 후 설정 및 DB 사본도 edo2에 수신됐으며 07:08:51 UTC `backup.py status`의 stale 목록은 비어 있었다.

| 시점 | 종류 | 수신 확인 run_id |
| --- | --- | --- |
| 적용 전 | DB | 20261102T064538Z-347c2ddb766d |
| 적용 전 | 설정 | 20261102T064540Z-ce82feea0381 |
| 게시 후 | 설정 | 20261102T070743Z-6d594a75aef2 |
| 게시 후 | DB | 20261102T070809Z-78202bb0dc2c |

각 수신 해시·크기는 private receipt에 있다. 기존 격리 복원 검사는 2026-10-31 19:41:06 UTC에 통과했으며, 이번 게시 후 사본으로 새 복원 검사를 실행한 것은 아니다.

실패 기준은 자산·근거 불일치, API/GET 오류 또는 readiness 실패다. 게시 중 실패하면 helper가 최초 DRAFT였던 동일 ID·SHA·key의 자산만 DRAFT로 복구한다. 수동 회수 명령과 상태 확인은 [운영 명령](../../../infra/catalog/README.md#2026-11-02-운영-게시-확인과-회수)을 따른다. 발급된 서명 URL은 최대 900초 남을 수 있다. R2 객체·관계·원본 근거는 삭제하지 않는다. 스키마는 status 회수와 별개이며 전체 DB를 자동 덮어쓰거나 Flyway repair하지 않는다. DB 복구가 필요하면 변경 전 사본을 새 격리 환경에 복원해 차이를 확인한다.

서버 증적은 root 전용 `/opt/eolssu/ops368-reference-audio-20261102/`의 `r2-receipt.json`, `database-receipt.json`, `reader-receipt.json`, `publication-preflight.json`, `canary-receipt.json`, `publication-receipt.json`, `postflight-receipt.json`, `final-runtime-receipt.json`이다. `publication-before.json`은 정확한 회수 대상이며 `env.before`·`compose.before.yml`은 설정 복구 원본이다. 서명 URL과 비밀값을 출력·공유하지 않는다. 안전한 영수증 사본은 작업자 ignored `data/processed/reference-audio-deploy-368/live-evidence/`에 보관했다.

실행한 게시 helper SHA-256은 `82203e6a7312245d7e02d5a5db935c991c39399ac366b55c36e12662c6026eae`다. 독립 검토자는 통합 커밋 `4cfcf4c5`의 V13/V14 보존·JUnit 498/187·API59·기존 이력 validate/migrate0를 읽기 전용으로 확인했고 차단 결함이 없었다. 앱 내 재생·파일명 미노출·출처/가공 안내·실기기 청취는 FE367의 남은 완료 조건이다.
