# 모범 음원 객체 키 전환과 재생 검증

S15P31E102-339, 2026-11-02. 사용자는 전문가 청음 대신 우선 사용하고 문제 곡을 나중에 숨기거나 교체하는 방식을 선택했다. [스펙](../../specs/model-audio-catalog-load.md)과 [운영 도구](../../../infra/catalog/model-audio.md)에 반영했다. FE380은 별도 Draft MR !222로 검토한다.

## 저장과 전달

기본음과 모범 연주는 모두 DB에 객체 키를 저장한다. URL은 재생 요청 시 생성한다. 모범 음원은 악보 버전·BPM·길이·해시와 함께 수행 시간표 키와 공통 원문 해시를 연결한다. CAT-05는 같은 곡의 고정 재생 경로를 반환하고, 서버가 매번 게시 상태를 확인한 뒤15분 읽기 서명으로 이동한다. 수행 JSON은 같은 서버 경로에서 크기2MiB·총10초 제한으로 읽는다. 녹음 저장 정책과 버킷 공개 설정은 바꾸지 않는다.

V15는 기존107개의 정확한 출처·객체 경로를 확인한 뒤 키를 추출하고 내부 생성 근거를 description에서 generation_metadata로 옮긴다. 악보 원문·음원 바이트·버전·해시·시간은 보존한다. 구 서버와의 전환 호환성을 위해 V15 직후에는 audio_url을 남기고, 사용자 후속 요청에 따라 새 BE 배포 확인 후 전용 도구로 삭제한다. 새 코드와 적재 도구는 이 컬럼을 읽거나 생성하지 않는다. DDL은 트랜잭션으로 되돌려지지 않으므로 스키마 변경 후 실패하면 실제 적용 상태를 확인해야 한다.

## 배포 후 컬럼 삭제를 분리한 이유

실제 배포는 db 프로필의 Flyway 적용 이후 JAR를 교체한다. ops 프로필은 Flyway를 실행하지 않는다. 일반 V16에 DROP만 추가하면 기존의 전체 migrate 명령에서 구 BE 실행 중 컬럼이 사라질 수 있다. 따라서 V15 이력·checksum을 보존하고 `drop_model_audio_url.py`를 후처리 단계로 둔다. 현재 배포 완료 SHA·선택 JAR/해시·실제 mount·readiness와 JAR 내부의 객체 키 조회를 공용 배포 lock 안에서 확인한다. 삭제는 기본 조회, 명시적 적용, 변경 전 백업, 컬럼과 행 해시 readback, 재실행 무변경으로 관리한다. Flyway에 가짜 적용 이력을 쓰지 않고 삭제 receipt와 실제 스키마로 이 단계를 구분한다.

배포 실패 시 기존 배포 도구의 롤백이 끝날 때까지 삭제는 실행되지 않는다. 삭제 이후의 배포·복구 대상은 객체 키를 지원하는 BE여야 한다. 구 BE만 되돌리는 방식은 호환되지 않으며, 필요하면 백업을 바탕으로 컬럼과 기존 URL을 먼저 복구해야 한다. 실행 명령과 백업 위치는 [운영 안내](../../../infra/catalog/model-audio.md#새-be-배포-후-audio_url-삭제)를 따른다.

삭제 도구의9개 단위 검사와 독립 리뷰를 통과했다. MySQL8.4.13에서 실제 Flyway14 →111곡/107음원 과거 스냅샷 →V15 →삭제 도구를 실행해 audio_url만 없어지고 나머지 모든 컬럼·값·게시 상태·시각이 동일함을 확인했다. dry-run·구 JAR·백업 실패는 무변경, 삭제 재실행은 추가 변경/백업0이었다. 삭제 후 Flyway 검증·재실행0, 적재 재실행0, 게시107/재실행0, 숨김1/재실행0, 빈 스키마의 신규107적재도 통과했다. 배포 journal·readiness·백업은 로컬 fixture 경계이며 운영 실행을 검증한 것으로 보고하지 않는다.

컬럼을 실제로 삭제한 별도 API DB에서 기존195개 DB 검사의 최종 결과도 모두 통과했다. 첫 실행의 인증 롤백 검사3개는 폐기DB의 트리거 설정 누락(MySQL1419)으로 실패해 `log_bin_trust_function_creators=1`을 설정하고 관련 두 클래스29개를 재검증했다. 음원107개 조회·해시·DRAFT404 검사는 첫 실행부터 통과했다. catalog 전체는67개 중55개 통과·기존 reference-audio 외부DB 조건12개 생략이다. 증거는 Git 밖 `audio-publish-preflight/audio-url-drop-339/verification.json`, `after-drop-api-verification.json`에 보관했다.

## 검증 근거

- 입력: manifest `ff49ba1c9dfa084d8c559bf110fb6c91ca3842fb57e68b99acdab06f99234ebc`, 4악기107개와 기존4곡. 실제 파일과 서버 스냅샷은 Git 밖에 보관한다.
- develop284cfbd8 통합 후 backend `clean build`506개·실제 MySQL `databaseTest`195개 모두 통과했다. data130개 통과, catalog58개 중46개 통과·기존 reference-audio용 외부 DB 검사12개 조건부 생략이다. 별도로 수행한339 MySQL 실험은 모두 통과했다. FE380은 앞선 host569개 통과·opt-in2개 생략, 최신 통합 영향105개와 Android lint 통과이며 JS/Wasm 컴파일도 확인했다.
- MySQL8.4.13: 107개 키 전환과 원문·음원 필드 보존, 새 적재 시 URL0개, 재실행 추가0개, 전체 게시107개, 한 곡 숨김·재실행, 충돌 거부, 중간 CHECK 실패의 전체 DML 롤백을 확인했다.
- 최종 V15 SHA `97f6087b6a319ec39995db9d2074c1ff8a69f947eb554d7ecb3b25031f484d56`: 같은 행수를 유지한 PK 교체도 backfill 전에 거부한다. 검사 중 description/URL 변경과 알 수 없는 호스트도 거부한다. 동시 수정값은 보존한다.
- 실제 BE HTTP: 전체 목록111개와 장단 NULL103개, 신규107개의 악보·음원 조회와 원문/음원 해시 일치, DRAFT107개의 공개 조회 거부를 검사했다. FE는 이 실제 응답과107개 수행 파일을 결합하는 검사에 통과했다.
- Android API37 에뮬레이터: 단소 몽금포타령, 정악대금 “중고태황” 연습곡, 산조대금 늴리리야, 소금 개구리소리의 실제 R2 HTTPS 음원 재생·진행 표시를 확인했다. 단소는 일시정지38초→앞으로43초 이동, 정악대금은 준비 시28초→수행 반영29초도 확인했다. 테스트 앱 종료 후 네이티브 player는 남지 않았다.

Android 검사는 실제 BE 응답을 읽는 로컬 fixture와 가짜 로컬 로그인, 실제 비공개 R2 파일을 사용했다. 운영 API302를 Android에서 끝까지 통과한 검사나 실기기 스피커 청음은 아니다. Web/iOS 재생기는 현재 미지원이며 이번 변경에서 추가하지 않았다. 자동 난이도·inferred·serviceDatasetApproved=false·비채점 상태를 검수 완료로 바꾸지 않았다.

재현은 backend `clean build`와 `databaseTest`(폐기 가능한 DB의 TEST_DB_* 및 선택 MODEL_AUDIO_CORPUS_SNAPSHOT), `python -m unittest discover -s infra/catalog -p 'test_*.py'`, data README의 의존성을 갖춘 Python으로 `python -m unittest discover -s data/tests -p 'test_*.py'`를 사용한다. 외부 증거 디렉터리 `audio-publish-preflight`의 `delivery-api-339`, `publication-mysql-339`, `android-publication-339`에 응답·receipt·관찰 결과를 보관했다.

## 운영에 남은 절차

이번 작업에서는 운영 V15·새 재생 API·audio_url 삭제·게시 전환을 실행하지 않았다. 사람이 BE339와 FE380 MR을 병합하고 호환 Android 앱을 배포한 뒤, 공용 배포 lock·변경 전 백업 아래 V15와 BE 배포를 적용한다. 새 BE 배포 완료 확인 후 audio_url 삭제 도구를 적용하고 목록·원문·수행·재생 연결을 확인한다. 게시 도구의 --application-ready와 --apply로107개를 제공하고 사후 DB 백업과 API readback을 남긴다. 전문가 청음을 다시 승인 단계로 요구하지 않는다.

구 앱은 장단 NULL에서 목록 전체가 실패하므로 앱 업데이트 순서가 필요하다. 특정 곡을 숨기면 새 조회는404가 되며 이미 발급된 읽기 서명은 최대15분 유효하다. 숨김 도구는 나머지106곡이나 참조 카탈로그의 변경 때문에 해당 곡을 숨길 수 없게 만들지 않는다.
