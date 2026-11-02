# 검수대기 악보·음원 적재

S15P31E102-339의 [적재 계약](../../docs/specs/model-audio-catalog-load.md)을 따른다. 공개 승인 도구가 아니며 새 곡·음원은 DRAFT, 채점은 불가 상태로 추가한다. 파일·DB 모두 기존 자료와 다르면 중단하고 덮어쓰지 않는다.

입력은 원본 공통 악보와 생성 파일을 담은 `eolssu-draft-model-audio-batch/v1` 묶음이다. 압축을 푼 폴더에는 manifest가 나열한 파일만 있어야 한다. 임시 난이도·참조 계획·receipt는 묶음 밖에 둔다. 실제107개 JSON·음원·서버 설정·receipt는 Git에 넣지 않는다.

## 실행

난이도는 저장소 루트에서 계산한다. 결과는 새 파일에만 저장한다.

```sh
python -m data.src.catalog.model_audio_difficulty --bundle /path/bundle --out /path/difficulty.json
```

파일 적재는 기본적으로 로컬 무결성만 검사한다. `--upload`를 명시해야 서버 설정을 읽고 기존 R2의 비공개 `model-audio/drafts/<manifest-sha256>/`에 조건부 업로드한다. 서버의 기존 boto3를 사용하며 공개 ACL·버킷 정책·삭제·덮어쓰기를 수행하지 않는다.

```sh
python3 stage_model_audio.py --bundle /path/bundle --env-file /opt/eolssu/.env --receipt /path/file-check.json
python3 stage_model_audio.py --bundle /path/bundle --env-file /opt/eolssu/.env --receipt /path/upload.json --upload --workers 8
```

DB 도구는 기본적으로 입력 검증과 현재 운영 DB 조회만 한다. 참조 계획은 필요한 율명과 새 비활성 악기, 재사용할 장단의 이름·BPM 단위를 명시한다. receipt가 같은 manifest의 모든 업로드 바이트 검증을 증명해야 한다.

```sh
python3 load_model_audio.py --bundle /path/bundle --difficulty /path/difficulty.json --reference-plan /path/references.json --upload-receipt /path/upload.json --receipt /path/db-check.json
```

실제 적재는 `--apply`를 추가한다. 먼저 공용 배포 lock 아래 [변경 전 백업](../backup/README.md)을 성공시키고 V15 객체 키 전환을 적용한다. 최초107개 적재 당시에는 [선택 장단 DDL](proposals/optional_jangdan.sql)만 적용했고, V15 후에는 새 도구를 사용한다. 새 도구는 주소 대신 `object_key`를 쓰므로 스토리지 환경 파일을 받지 않는다. DDL과 데이터 트랜잭션은 별개다. 도구는 실행 중인 backend 컨테이너에서 DB명만 알아내고 MySQL 컨테이너 내부 비밀번호를 사용한다. 데이터는 단일 트랜잭션으로 추가하며 이후 별도 접속에서 원본·음원·실제 ID를 다시 확인한다.

V15는 기존107개 URL·근거를 검증하고 객체 키, 수행 키, 공통 원문 해시, 생성 근거 JSON으로 옮긴다. 알 수 없는 호스트·식별자 또는 사전 검사 이후의 행 변경은 중단한다. 기존 `audio_url` 값은 구 서버와의 전환 호환성만을 위해 유지한다. 새 코드·적재는 이 값을 읽거나 생성하지 않으며, 새 BE 배포 확인 후 폐기할 수 있다. 내부 근거는 사용자용 곡 설명에서 제거한다.

## 게시·곡별 숨김

사용자 후속 결정으로 전문가 청음은 게시 선행 조건이 아니다. 새 BE와 NULL 장단을 처리하는 Android 앱이 실제 배포되고 목록·수행 시간표·재생 경로를 확인한 뒤 게시한다. 현재 단계의 MR을 사람이 병합하기 전에 운영 상태만 바꾸면 구 앱 목록이 실패할 수 있다. `--application-ready`는 이 기술 확인에 대한 실행자의 표시이며 청음·권리 승인을 생성하지 않는다.

```sh
python3 publish_model_audio.py --bundle /path/bundle --difficulty /path/difficulty.json --reference-plan /path/references.json --upload-receipt /path/upload.json --receipt /path/publish-check.json --all --action publish --from-status DRAFT
```

기본은 읽기 전용이다. 실제 게시에는 새 receipt 경로와 `--apply --application-ready`를 추가한다. 개별 숨김은 `--all` 대신 정확한 `--case danso-015-p1`, `--action hide --from-status PUBLISHED --apply`를 사용한다. 숨김은 앱 준비 표시가 필요하지 않다. 대상 곡·판본·파일 해시가 바뀌었거나 현재 상태 쌍이 예상과 다르면 중단하며, 목표 상태가 이미 같으면 행·갱신 시각을 보존한다.

게시 결과는 곡/음원 PUBLISHED/PUBLISHED, 숨김은 HIDDEN/DRAFT다. 원본 객체·악보·비채점 상태를 보존하며 다른 곡의 변경 때문에 특정 곡을 숨기지 못하게 하지 않는다. 새 재생 요청은 숨김 직후404가 되지만 이미 발급된 읽기 서명은 최대15분 유효할 수 있다. 파일 교체는 새로운 키·해시·악보 버전을 확인하는 별도 적재이며 기존 객체를 덮어쓰지 않는다.

완료 후 기존 행·공개 API 불변, 신규107곡/음원의 DRAFT·비채점 상태, 장단4연결/103미연결, 난이도39/41/27, 객체 해시와 비공개 접근을 확인한다. 기존 `postchange` 백업은 설정만 담으므로 공용 lock을 놓은 뒤 `sudo /opt/eolssu-backup/backup.py send db`로 적재 후 DB도 따로 백업한다. 이 명령은 자체적으로 같은 lock을 얻는다. 변경 전후 카탈로그 스냅샷과 receipt를 제한된 서버 경로에 보존한다.

## 재실행·실패

같은 입력은 새 receipt 경로로 재실행한다. 기존 객체·행이 정확히 같으면 재사용하며 제목으로 합치지 않는다. DB 작업 중 연결이 끊기면 자동 재접속하지 않는다. `apply-outcome-unknown` 또는 `committed-readback-pending`이면 현재 행을 먼저 조회하고 결과를 확인한다. 오류만 보고 커밋이 취소됐다고 단정하지 않는다.

업로드 실패 시 receipt의 검증 완료 객체를 확인하고 같은 묶음으로 다시 실행할 수 있다. DB 실패가 객체를 지우지는 않는다. 기존 행이나 참조가 생긴 신규 행을 자동 삭제하지 않는다. `jangdan_id`를 다시 NOT NULL로 만들려면 NULL인 행의 처리와 참조 관계를 먼저 확인해야 한다. 운영 전체 백업 덮어쓰기는 복구 절차로 검토하며 이 도구에서 수행하지 않는다.

## 로컬 검사

```sh
python -m unittest discover -s infra/catalog -p 'test_*.py'
python -m unittest data.tests.test_model_audio_difficulty
```

SQL 변경 검증에는 실제 격리 MySQL 8.4가 필요하다. 운영과 같은 카탈로그 스키마에서107개 적재, 재실행 추가0, 기존 행 충돌 시 무변경, 중간 CHECK 실패 시 전체 롤백을 확인한다. 이 검증을 운영 DB에서 수행하지 않는다. 선택 장단의 API 영향은 backend의 `build databaseTest`로 검사한다.
