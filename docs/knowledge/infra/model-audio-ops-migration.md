# 운영 DB V15·V16 적용과 모범 음원 audio_url 삭제

- Jira: [S15P31E102-395](https://ssafy.atlassian.net/browse/S15P31E102-395). 관련: 모범 음원 [S15P31E102-339](https://ssafy.atlassian.net/browse/S15P31E102-339)([운영 도구](../../../infra/catalog/model-audio.md)), 내 악보 [S15P31E102-390](https://ssafy.atlassian.net/browse/S15P31E102-390)([스펙](../../specs/user-sheets.md)).
- 일시: 2026-11-02 13:10~13:14 UTC. 운영 서버 작업 폴더 `/opt/eolssu/ops395-v15-v16-20261102`(스크립트·로그·영수증, root 전용).

## 배경

Jenkins가 develop `d1e7a80f`를 13:01 UTC에 자동 배포했다. 이 릴리스에는 339의 객체 키 조회 코드와 390의 내 악보 API가 들어 있지만 운영 DB는 V14였다. 그 결과 `GET /api/songs/{songId}/model-audio`는 500이었고 `user_sheets` 테이블이 없었다. 운영 `ops`는 Flyway를 실행하지 않으므로 마이그레이션을 따로 적용해야 한다.

- `songs.jangdan_id`는 V15 전부터 nullable이었다. 107곡 최초 적재 때 [선택 장단 DDL](../../../infra/catalog/proposals/optional_jangdan.sql)만 먼저 적용했기 때문이다.
- 적용 전 상태: Flyway V1~V14 성공, 곡 111행, 모범 음원 107행(모두 DRAFT), `object_key` 없음.

## 1단계: V15·V16 적용

오늘 V12 적용(`/opt/eolssu/ops356-v12-20261102/apply-v12.py`)과 같은 방식으로 `apply-v15-v16.py`를 실행했다.

1. 공용 배포 lock(`/run/lock/eolssu-backend-deploy.lock`)을 비대기로 얻는다.
2. 사전 확인: 컨테이너 3개 healthy, readiness UP, `deployment.json`의 SHA·JAR 해시와 실행 컨테이너 마운트 일치, JAR 안 마이그레이션 V1~V16, Flyway V1~V14, `object_key`·`user_sheets` 없음.
3. `/opt/eolssu-backup/backup.py send prechange`로 DB·설정 백업을 edo2에서 받았는지 확인한다.
4. 실행 중인 `d1e7a80f` JAR로 일회성 컨테이너에서 Flyway를 실행한다(`db` 프로필, `--spring.flyway.target=16`, 웹 서버·스케줄러·메일 비활성).
5. 사후 확인 후 lock을 놓고 `backup.py send db`로 사후 DB 백업을 남긴다.

| 결과 | 값 |
| --- | --- |
| Flyway | V1~V16 성공. V15 checksum `-1537280281`, V16 `-274577693` |
| 모범 음원 | 107행 모두 `object_key` 채움, 107곡 DRAFT 유지, 곡 111행 유지 |
| 내 악보 | `user_sheets` 생성, 0행 |
| 서비스 | 기존 컨테이너 ID·시작 시각·MySQL 볼륨 보존, readiness UP |
| HTTP | model-audio 500 → 404(게시 음원 없음), `/api/users/me/sheets` 비로그인 401, `/api/songs`·`/api/terms` 200 |
| 백업 | prechange DB·설정, 사후 DB(run `20261102T131136Z-84fb5a86d8be`) edo2 수신 |

새 백엔드 배포는 하지 않았다. 대상 릴리스가 이미 실행 중이었기 때문이다.

## 2단계: audio_url 삭제

`d1e7a80f`의 `infra/catalog/drop_model_audio_url.py`와 함께 쓰는 `load_model_audio.py`·`stage_model_audio.py`를 그대로 서버 `tools/`에 두었다.

- 검사 모드(`audio-url-check.json`): `validated-dry-run`. 배포 기록·JAR·실행 마운트 일치, 실행 JAR의 `ModelAudioMapper`가 `object_key`만 사용, V15 성공, 107개 객체 키·수행 연결 형식 통과.
- 적용(`audio-url-drop.json`): 도구가 lock 안에서 prechange 백업 → 재검사 → `ALTER TABLE song_model_audios DROP COLUMN audio_url`. `status=applied`, 곡 111행·음원 107행의 행 해시가 삭제 전후 동일.
- 사후: `audio_url` 없음, readiness 200, 컨테이너 4개 healthy. 사후 DB 백업 run `20261102T131435Z-b65c80a60727` 수신.

DROP은 되돌릴 수 없다. 이제 객체 키를 지원하지 않는 339 이전 백엔드로 단독 롤백하면 모범 음원 조회가 실패한다. 이전 버전이 필요하면 백업으로 컬럼·URL을 복원하는 DB 복구를 먼저 계획한다.

## 남은 일

- 107곡 게시(`publish_model_audio.py --apply --application-ready`)는 하지 않았다. [운영 도구](../../../infra/catalog/model-audio.md#게시곡별-숨김)의 조건대로 FE380 병합과 장단 null을 처리하는 Android 앱 배포를 확인한 뒤 진행한다. 입력(묶음·난이도·참조 계획·업로드 영수증)은 서버 `/opt/eolssu/catalog-staging`에 있다.
- 로그인한 사용자로 내 악보 API를 운영에서 호출해 보지는 않았다. 스키마와 비로그인 401만 확인했다.

## 다음 운영 마이그레이션에 쓸 점

- develop 병합은 Jenkins가 바로 배포한다. 스키마가 필요한 코드가 병합되면 같은 시점에 운영 마이그레이션을 맞추지 않으면 해당 API가 500이 된다.
- Flyway 번호가 겹치면 develop 병합 순서대로 번호를 옮긴다(이번에는 내 악보를 V15에서 V16으로 옮김). 운영은 이력 순서대로만 적용되므로 앞 번호가 미적용이면 뒤 번호도 적용할 수 없다.
