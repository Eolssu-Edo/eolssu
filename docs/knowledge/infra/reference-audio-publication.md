# 기본음·시김새 운영 게시 기록

관련 작업은 S15P31E102-368이며 [스펙](../../specs/reference-audio-publication.md)과 [계획](../../plans/S15P31E102-368.md)을 따른다. 이 기록은 로컬 통합 검증과 운영 적용 결과를 구분한다. 앱 재생 UI는 S15P31E102-367 범위다.

## 통합 배포 사전 점검

2026-11-02 develop `2a0d4d9b`에는 `V13__reference_audio_assets.sql`과 `V13__learning_tracks_and_progress.sql`이 함께 있었다. Flyway는 동일 버전 두 파일을 허용하지 않는다. 먼저 병합된 오디오 V13을 보존하고 학습 파일·사전 확인 이름을 V14로 옮긴다. 학습의 36개 시드와 DDL 내용은 그대로 유지한다.

학습 V14는 기존 `lessons`·`lesson_progress`가 비어 있고 삭제할 V1 제약·인덱스가 있어야 한다. `chk_v14_preconditions` 실패는 학습 DDL 전에 멈추지만 MySQL DDL 전체가 트랜잭션으로 되돌아가는 것은 아니다. 현재 운영의 학습 데이터 유무는 배포 담당자가 별도로 확인한다.

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

아직 이 문서 작성자가 실행하지 않았다. 루트 배포 담당자가 서버 사전 점검·백업·게시·읽기 확인 결과를 이어 기록한다. 비밀값과 서명 URL은 기록하지 않는다.
