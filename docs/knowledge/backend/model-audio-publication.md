# 모범 음원 객체 키 전환과 재생 검증

S15P31E102-339, 2026-11-02. 사용자는 전문가 청음 대신 우선 사용하고 문제 곡을 나중에 숨기거나 교체하는 방식을 선택했다. [스펙](../../specs/model-audio-catalog-load.md)과 [운영 도구](../../../infra/catalog/model-audio.md)에 반영했다. FE380은 별도 Draft MR !222로 검토한다.

## 저장과 전달

기본음과 모범 연주는 모두 DB에 객체 키를 저장한다. URL은 재생 요청 시 생성한다. 모범 음원은 악보 버전·BPM·길이·해시와 함께 수행 시간표 키와 공통 원문 해시를 연결한다. CAT-05는 같은 곡의 고정 재생 경로를 반환하고, 서버가 매번 게시 상태를 확인한 뒤15분 읽기 서명으로 이동한다. 수행 JSON은 같은 서버 경로에서 크기2MiB·총10초 제한으로 읽는다. 녹음 저장 정책과 버킷 공개 설정은 바꾸지 않는다.

V15는 기존107개의 정확한 출처·객체 경로를 확인한 뒤 키를 추출하고 내부 생성 근거를 description에서 generation_metadata로 옮긴다. 악보 원문·음원 바이트·버전·해시·시간은 보존한다. 구 서버와의 전환 호환성을 위해 기존 audio_url은 남기되 새 코드와 적재 도구는 읽거나 생성하지 않는다. DDL은 트랜잭션으로 되돌려지지 않으므로 스키마 확장 후 충돌하면 부분 적용을 확인해야 한다.

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

운영에는107곡·음원이 DRAFT로 적재돼 있고 V15·새 재생 API·게시 전환은 아직 적용하지 않았다. 사람이 BE339와 FE380 MR을 병합하고 호환 Android 앱을 배포한 뒤, 공용 배포 lock·변경 전 백업 아래 V15와 BE 배포를 적용한다. 목록·원문·수행·재생 연결을 확인하고 게시 도구의 --application-ready와 --apply로107개를 제공한다. 게시 후 DB 백업과 API readback을 남긴다. 전문가 청음을 다시 승인 단계로 요구하지 않는다.

구 앱은 장단 NULL에서 목록 전체가 실패하므로 앱 업데이트 순서가 필요하다. 특정 곡을 숨기면 새 조회는404가 되며 이미 발급된 읽기 서명은 최대15분 유효하다. 숨김 도구는 나머지106곡이나 참조 카탈로그의 변경 때문에 해당 곡을 숨길 수 없게 만들지 않는다.
