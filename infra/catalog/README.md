# 기본음·시김새 카탈로그 적재

비행기 56의 구판 기록을 보존하는 CAT77 게시기는 [판본 보존 게시 절차](airplane-revision.md)를 따른다. 기본 실행은 dry-run이며 R2 업로드와 현재 행 교체를 구분한다.

곡 전체의 악보·모범 음원 적재는 [별도 안내](model-audio.md)를 따른다. 아래는 기본음·시김새 단음 자산의 적재 절차다.

Task S15P31E102-364. [입출력·API 계약](../../docs/specs/reference-audio-catalog.md). 운영 변경 전에 검증된 구현·마이그레이션·자산 게시 결과를 확인한다.

## SQL 준비

M4A 변환 도구가 만든 schemaVersion 1 manifest와 같은 폴더의 파일을 사용한다. 저장소 루트에서 실행한다.

```powershell
python infra/catalog/import_reference_audio.py --manifest data/processed/reference-audio-v1/manifest.json --output data/processed/reference-audio-v1/import-draft.sql
python -m unittest discover -s infra/catalog -p "test_*.py" -v
```

출력 경로는 새 파일이어야 한다. 도구는 모든 파일의 경로·크기·SHA-256, dataset/label의 안정 ID, reference-audio 전용 키, 형식·길이·출처 메타데이터·검수 상태를 확인한다. 미디어의 실제 AAC 디코딩과 길이 대조는 앞선 변환 도구가 수행한다. SQL 준비는 DB/R2에 접속하지 않는다.

SQL은 V13 적용 이후 **전용 연결에서 `mysql`의 `--force` 없이** 실행한다. 비밀값은 환경이나 권한 제한된 클라이언트 설정으로 주입한다. 임시 테이블 생성 권한이 필요한 적재 전용 작업이며 공개 HTTP 쓰기 API는 없다. SQL에는 DB 이름·접속 비밀값이 없다. 적용 전 선택한 DB를 확인한다.

MySQL 회귀 검사는 실행자가 별도로 만든 `eolssu-reference-audio-*` 이름의 폐기 가능한 MySQL 8.4 Docker 컨테이너만 사용한다. `REFERENCE_AUDIO_TEST_MYSQL_CONTAINER`와 `REFERENCE_AUDIO_TEST_MYSQL_PASSWORD`를 그 컨테이너의 값으로 지정한 뒤 위 unittest 명령을 실행한다. 도구는 컨테이너 안의 `reference_audio_import_test` DB를 만들고 그 안의 테스트 테이블만 재생성한다. 이 환경변수가 없으면 MySQL 검사는 건너뛰며, 이를 통과로 보고하지 않는다.

- 새 항목은 DRAFT다. 데이터 파일·객체가 자동 업로드되거나 게시되지 않는다.
- 동일 자산·메타데이터 재실행은 행을 늘리지 않고 기존 게시 상태를 유지한다.
- 같은 assetId 또는 datasetId/labelId에 다른 내용이 있으면 CHECK 오류로 신규 삽입 전에 중단한다. 오류를 무시하는 클라이언트로 실행하지 않는다. 연결 종료 시 진행 중 트랜잭션이 롤백된다.
- 삽입문도 충돌 검사 통과 행을 요구하므로 오류를 잘못 무시한 경우에도 신규 행을 추가하지 않는다.
- 교체가 필요하면 출처·음원 변경을 먼저 검토하고 명시적인 교체 절차를 마련한다. 이 도구는 기존 행을 덮어쓰지 않는다.

## 운영 게시 순서

364 사전 점검 당시(2026-11-02) edo1은 Flyway V1~V12, 카탈로그 테이블 없음, pitches11행이었다. 미적용 개발 버전V13~V16을 최종 V13 하나로 통합했다. 이미 예전 개발판을 적용한 로컬DB는 새 폐기DB로 교체하고 checksum repair로 이력을 맞추지 않는다. 이후 INF368에서 운영 V13·학습V14와 430개 게시까지 적용했다. MySQL DDL은 전체 트랜잭션 롤백을 보장하지 않으므로 실패하면 백업·부분 적용 상태를 확인한 뒤 복구한다.


현재 통합 백엔드에는 학습 V14도 포함된다. 아래 카탈로그 V13 적용과 별도로 V14의 빈 학습 테이블·기존 제약 사전 확인을 통과해야 하며, 실제 배포 순서와 결과는 [368 게시 기록](../../docs/knowledge/infra/reference-audio-publication.md)을 따른다.

1. 대상 DB의 백업·버전 확인 후 최종 V13 하나를 적용한다. 운영 ops는 자동 마이그레이션을 하지 않으므로 새 API 배포·매핑 적용 전에 완료한다.
2. M4A와 manifest를 검증하고 DRAFT 적재 결과를 대조한다.
3. 승인된 비공개 자산 저장소에 manifest의 objectKey와 `Content-Type: audio/mp4`로 업로드한다. `recordings/` 경로 및 녹음용 30일 삭제 정책을 사용하지 않는다. 버킷 공개 정책은 변경할 필요가 없다.
4. 업로드한 각 객체의 전체 다운로드 SHA-256·크기와 재생을 대조한다. 단순 ETag를 SHA-256으로 취급하지 않는다. 장기 보관 규칙과 클라이언트 읽기 정책도 확인한다.
5. 검증된 정확한 assetId만 PUBLISHED로 변경한다. 일괄 무조건 게시하지 않는다. rejected 라벨은 게시할 수 없다. inferred는 재생 참고 자료이며 채점 정답으로 승격하지 않는다.
6. `REFERENCE_AUDIO_STORAGE_*`에 별도 읽기용 설정을 주입한다. 이 설정은 사용자 녹음의 `RECORDING_STORAGE_TYPE`과 독립적이다.
7. INF368은 실제 조회·302·서명GET·해시·범위 응답을 확인한다. 앱 내부 재생·출처/가공 안내·실기기 청취는 FE367에서 확인한다. soundUrl은 상대 API 경로이므로 앱은 서버 기준 주소로 해석한다. 임시 서명 URL을 DB나 장기 캐시에 저장하지 않는다.

364는 구현·검증을 담당했고 실제 R2 게시·운영 DB 적용·읽기 설정은 INF368에서 완료했다. 기존 CAT-06의 대표 음원 선정은 별도이며 앱 재생 연결·검증은 FE367의 완료 조건이다.

## 명목상 기준 음 연결

V13는 `pitch_id` FK와 `pitch_mapping_basis`를 nullable로 추가한다. 마이그레이션만으로 기존 자산을 연결하지 않는다. 이미 DRAFT 또는 PUBLISHED로 적재된 행에 아래 별도 매핑 SQL을 적용할 수 있다.

```powershell
python infra/catalog/link_reference_audio_pitches.py --manifest data/processed/reference-audio-v1/manifest.json --mapping infra/catalog/pitch-mappings/danso-basic-v1.json --output data/processed/reference-audio-v1/link-pitches.sql
python infra/catalog/link_reference_audio_pitches.py --manifest data/processed/reference-audio-v1/manifest.json --mapping infra/catalog/pitch-mappings/jeongak-daegeum-basic-v1.json --output data/processed/reference-audio-v1/link-jeongak-pitches.sql
python infra/catalog/link_reference_audio_pitches.py --manifest data/processed/reference-audio-v1/manifest.json --mapping infra/catalog/pitch-mappings/sogeum-basic-v1.json --output data/processed/reference-audio-v1/link-sogeum-pitches.sql
python infra/catalog/link_reference_audio_pitches.py --manifest data/processed/reference-audio-v1/manifest.json --mapping infra/catalog/pitch-mappings/sanjo-daegeum-basic-v1.json --output data/processed/reference-audio-v1/link-sanjo-pitches.sql
```

생성한 SQL 네 파일을 같은 대상 DB의 전용 mysql 연결에서 각각 `--force` 없이 실행한다. 연결 순서는 **V13 최종 스키마/기준 음 → 기존 manifest 적재 → 매핑 SQL 네 파일**이다. 파일별 트랜잭션이므로 앞 파일 성공 후 다음 파일이 실패하면 성공한 연결은 보존된다. 원인을 수정하고 같은 파일을 다시 적용할 수 있다. 파일 업로드·PUBLISHED 전환과는 별도이며 URL을 입력하지 않는다. 새 CLI의 환경변수는 없다.

- 명시적 매핑 네 파일은 단소30·정악대금54·소금90·산조대금45, 총219개의 labelId와 명시적 대상 yul/octave를 열거한다. datasetId는 파일 공통이며 assetId·파일 SHA-256·분류·취법·율명은 해시로 고정한 manifest에서 읽는다. `basis`에 자료·버전·명목상 연결임을 남긴다. 기존 단소 목표/근거는 보존했다. 실행할 때 이름으로 새 매핑을 추정하지 않는다.
- manifest 전체의 원래 파일/해시/출처 검증 후, 각 매핑이 실제 자산과 일치하는지 검사한다. 중복·미등록 자산·시김새·잘못된 목표 음·빈 근거는 SQL 생성 전에 거부한다.
- SQL은 `(yul, octave)`로 `pitches`를 조회한다. ID 숫자를 환경 간 복사하지 않는다. 대상 pitch나 DB 자산이 없거나 파일/분류/기존 연결·근거가 다르면 배치 전체를 거부한다. 확인 전에 대상 행을 잠그고 검증 통과 때만 갱신한다.
- 동일 매핑 재실행은 연결과 갱신 시각을 그대로 둔다. 기존 import도 연결·게시·검수 상태를 보존한다. 기존 연결을 다른 기준으로 덮어쓰는 도구가 아니므로 수정은 별도 검토가 필요하다.
- 평취 중·임·무는 octave0, 황·태는 octave1이며 역취는 각각 +1이다. 기준은 [단소 라벨링](../../docs/knowledge/data/danso.md), [기준 음 데이터](../../data/reference/pitches.json), V5다. `review=inferred`, `eligibleForNoteScoring=false`는 유지한다.
- 정악대금은 기존 저취 임 시작·18음 순서와 공식 음역을 대조한다. 저취 임/남/무=octave−1, 황/태/고/중=octave0이며 평취+1·역취+2다. 소금은 평취 임/남/무=octave0, 황/태/고/중=octave1이며 역취+1, 중청임=임2다. 필요한 기준 음은 V13에서 보충하며 기존11개 ID/반음은 보존한다. 현재 범위 조회 방식상 전역22개·단소15개가 반환된다.
- 산조대금도 [공식 대금 항목④](https://www.gugak.go.kr/ency/topic/view/108)의 정간보 전폐음㑣 표기(2026-11-02 확인)와 기존 저취 임 시작·상하 옥타브 음렬을 결합해 명목상 관계를 정했다. 실제 조율이나 본청 C를 황종 C로 해석한 것이 아니며 검수 상태는 유지한다.
- 기본음 FK가 없는211개 중 시김새207개는 아래 V13 역할별 관계로 연결한다. 자산 전체에서 미연결은 개별 라벨 대안4개다. 대안은 소금 `ngc-sogeum-2654.group-006`·`ngc-sogeum-2655.group-006`·`ngc-sogeum-2656.group-006`의 협/고와 산조 `ngc-sanjo-2552.region-004`의 무/황/남이다. 미연결 전체를 라벨 근거 부족으로 취급하지 않는다.

검사: `python -m unittest discover -s infra/catalog -p 'test_*.py'`. 실제 MySQL 검사는 `REFERENCE_AUDIO_TEST_MYSQL_CONTAINER`를 `eolssu-reference-audio-`로 시작하는 폐기 가능한 컨테이너, `REFERENCE_AUDIO_TEST_MYSQL_PASSWORD`를 그 테스트 비밀번호로 지정한다. `reference_audio_import_test` DB에서 합성 테이블을 재생성하므로 서비스 DB를 지정하지 않는다.

## 시김새의 역할별 음 연결

V13 `reference_audio_pitch_relations`는 시김새의 행 라벨과 내부 안정음을 구분한다. 모든207자산에 LABEL을 연결하고 실제 근거가 있는88개 SEQUENCE, 다루치기의10개 LOWER_ANCHOR와10개 DESTINATION을 추가한다. 총315관계이며 기본음219개와 합쳐426자산이 연결된다. 4개 기본음 대안은 그대로 남는다.

```powershell
python infra/catalog/link_reference_audio_roles.py --manifest data/processed/reference-audio-v1/manifest.json --mapping infra/catalog/pitch-mappings/danso-sigimsae-v1.json --output data/processed/reference-audio-v1/link-danso-roles.sql
python infra/catalog/link_reference_audio_roles.py --manifest data/processed/reference-audio-v1/manifest.json --mapping infra/catalog/pitch-mappings/jeongak-daegeum-sigimsae-v1.json --output data/processed/reference-audio-v1/link-jeongak-roles.sql
python infra/catalog/link_reference_audio_roles.py --manifest data/processed/reference-audio-v1/manifest.json --mapping infra/catalog/pitch-mappings/sogeum-sigimsae-v1.json --output data/processed/reference-audio-v1/link-sogeum-roles.sql
python infra/catalog/link_reference_audio_roles.py --manifest data/processed/reference-audio-v1/manifest.json --mapping infra/catalog/pitch-mappings/sanjo-daegeum-sigimsae-v1.json --output data/processed/reference-audio-v1/link-sanjo-roles.sql
```

V13과 기존 manifest 적재 후 생성 SQL 네 파일을 전용 mysql 연결에서 각각 `--force` 없이 적용한다. 추가 env나 재변환·업로드는 없다. 연결은 기존22개 pitches의 `(yul,octave)`를 사용한다. 기존 기본음 scalar FK/필터를 확장하지 않고 `relatedPitchId`로 두 관계를 함께 찾는다.

CLI는 고정한 manifest 전체 SHA-256과 실제 자산·파일, 전체 역할 집합·순서·선택 anchor 일치·분석창 범위를 확인하고 근거 경로를 복원한다. SQL은 자산을 잠그고 원본 metadata를 같은 MySQL JSON 표현으로 대조한다. DB 관계가 없으면 전체 집합을 추가하고, 완전히 같으면 재실행은 no-op이다. 기존 집합의 일부 누락·추가·대상/근거 변경, 없는 pitch, DB metadata 변경은 전체 파일 배치를 거부한다. 기존 import도 다른 metadata를 덮어쓰지 않는다.

SEQUENCE의 index만 관측 순서다. 다루치기 역할의 DB index=0은 키용이며 시간순을 뜻하지 않는다. LABEL은 시작음/전체구간 단음 보장이 아니다. 원음 기준 분석창·F0·대안·labelAnchorIndex·반복 횟수와 inferred/채점 제외 상태는 metadata에 보존하며 음표 이벤트를 새로 만들지 않는다. 적재 뒤 시김새207개·관계315개·전체426연결/4미연결을 확인하고, 서버 게시 검증은 INF368, 실제 앱 재생은 FE367에서 수행한다.

매핑 JSON basis의 파생 SQL 참조도 최종 `V13__reference_audio_assets.sql` 및 명시적 기본음 매핑으로 정렬했다. 단소30개 기존 근거와 모든 원본 라벨·metadata는 그대로이며 이전 개발 참조는 커밋4c2ff7d7·266b5d66에 보존된다.

## 매핑 JSON schemaVersion 2

8개 매핑 파일은 원본 manifest에서 읽을 수 있는 반복값을 제거했다. 파일 경로의 `-v1.json`은 기존 배포 자료 이름을 유지한 것이며 파서 형식은 내용의 `schemaVersion: 2`가 정한다. 내보낸 미디어 manifest 자체의 schemaVersion1은 변경하지 않는다. 공개 API와 SQL 형식도 그대로다.

| 파일 공통 필드 | 의미 |
| --- | --- |
| schemaVersion | 정수2 |
| datasetId | 해당 파일의 악기 데이터셋 |
| manifestSha256 | 원본 manifest 파일 바이트 전체 SHA-256. 같은 바이트를 한 번 읽어 해시를 검사한 뒤 해석 |
| basis | 기존 명목상 연결 근거 문자열. SQL 재실행의 동등성을 위해 그대로 보존 |
| mappings | 아래 명시적 자산별 선택 목록 |

기본음 행은 원본 labelId와 검토된 목표만 쓴다.

```json
{"labelId": "ngc-danso-2338.tone-001", "yul": "중", "octave": 0}
```

시김새 행은 의미가 드러나는 역할명으로 목표를 쓴다. sequence는 원본 orderedAnchors 순서이며 원본에 없으면 생략한다. lowerAnchor와 destination도 원본 gesture가 있을 때만 함께 쓴다.

```json
{"labelId": "ngc-danso-2344.gesture-001", "label": {"yul": "태", "octave": 1}, "sequence": [{"yul": "중", "octave": 1}, {"yul": "태", "octave": 1}]}
{"labelId": "ngc-sanjo-2566.region-001", "label": {"yul": "남", "octave": -1}, "lowerAnchor": {"yul": "임", "octave": -1}, "destination": {"yul": "남", "octave": -1}}
```

자산 한 행은 한 줄로 두며 목표를 자동 추론하거나 청/탁 접두사를 일괄 처리하지 않는다. assetId·파일해시·분류·취법·율명·metadata·근거경로는 검증된 manifest에서 복원한다. 이 정보는 삭제된 것이 아니라 원본에 한 번만 보관된다. 전체 manifest 해시가 원본 metadata도 고정하므로 행마다 metadata 해시를 반복하지 않는다.

두 linker는 schema2만 받는다. 내부 Python 함수의 입력은 `load_mappings(mapping_path, manifest_path)`이고 확인하지 않은 assets 리스트를 직접 넘기지 않는다. 형식/중복JSON키·labelId/unknown field/잘못된dataset·category/manifest 변경/파일손상/목표타입·범위/누락·추가 역할/sequence수/같은 원본음의 목표불일치는 SQL 파일을 만들기 전에 거부한다. SQL의 전체배치 충돌·재실행 검사는 그대로이며 DB/R2에 자동 쓰지 않는다.

재검증은 위 unittest 명령과 기존8개 CLI 명령을 사용한다. 각 출력은 새 경로에 생성한다. 기준 manifest 파일의 SHA-256은 `5450071ea3f8cf6c53e7a20c3800be015cdc755717213c339475ee3696c0a6fb`다. 원본 manifest가 바뀌면 기존 매핑을 그대로 적용하지 않고 해당자료와 명시적 선택을 다시 대조한다.

## 2026-11-02 운영 게시 확인과 회수

INF368에서 430개를 게시했다. 단소56·정악대금107·산조대금121·소금146이며 [실행·검증·백업 기록](../../docs/knowledge/infra/reference-audio-publication.md#운영-적용)을 따른다. 서버의 기존 R2 버킷·자격증명을 읽기 env5개에 연결했고 URL을 DB에 입력하지 않았다.

```sh
curl --fail --silent --show-error https://k15e102.p.ssafy.io/api/actuator/health/readiness
curl --fail --silent --show-error 'https://k15e102.p.ssafy.io/api/reference-audios?datasetId=danso'
ssh -o ClearAllForwardings=yes edo2 'sudo /opt/eolssu-backup/backup.py status'
```

게시 회수가 필요할 때 아래 고정 helper는 최초 DRAFT였던 430개 중 ID·SHA·object key가 같은 행만 DRAFT로 돌린다. 실행 시 공용 배포 lock을 획득하며 실패하면 재실행 전에 원인을 확인한다. 객체·원본 metadata·pitch 관계는 삭제하지 않는다. 이미 발급한 서명 URL은 최대900초 유효할 수 있다.

```sh
ssh -o ClearAllForwardings=yes edo1 'sudo sha256sum /opt/eolssu/ops368-reference-audio-20261102/publish_verify.py'
# SHA-256: 82203e6a7312245d7e02d5a5db935c991c39399ac366b55c36e12662c6026eae
ssh -o ClearAllForwardings=yes edo1 'sudo python3 /opt/eolssu/ops368-reference-audio-20261102/publish_verify.py --rollback'
```

기본 인자 없는 helper 실행은 신규 게시용이며 현재 상태에 재실행하지 않는다. 설정 원본은 같은 root 전용 폴더의 `env.before`와 `compose.before.yml`이다. 이후 다른 배포의 변경이 있는지 먼저 대조하고 필요한 설정만 공용 lock 아래 복구한 뒤 백엔드만 재생성한다. 파일 전체를 출력하거나 현재 설정에 무조건 덮어쓰지 않는다. DB 스키마는 별도 복구 대상이며 V14 checksum1541415930을 repair하지 않는다. 운영 로그는 Docker 중복 캐시가 꺼져 있으므로 root 전용 `/var/log/eolssu/ordinary/eolssu-backend.*.log`에서 확인한다.

## 소금 채점 켜기 계획

Task S15P31E102-387. S15P31E102-339가 운영 DB에 넣은 소금 아리랑(96)·도라지타령(93)·밀양아리랑(95)·늴리리야(91)에 사람 검수한 숨표와 `provenance.review: reviewed`를 반영하고 `is_scorable`을 켠다. 판본은 교과서가 있으면 교과서(아리랑·도라지타령, `school-textbook-sogeum-p15`·`p16`), 없으면 표준악보집(`ngc-standard-2022-p193`·`p196`)이고, 운영 악보와 음·길이·이벤트 id가 같음을 운영 DB 읽기로 확인했다. 악기 행·율명·게시 상태·`score_version`은 바꾸지 않는다(모범 음원은 같은 `score_version`일 때만 나온다). 근거는 [소금 서비스 4곡 기록](../../docs/knowledge/data/sogeum-service-scores.md)이다.

403 계획 파일에는 기존 4곡과 새로 검수한 14곡을 넣었다. 북천이 소금 1(104)은 운영 악보와 음이 다르고 취타 고급(109)은 시김새 표현이 달라 제외했다. 2026-11-04 S15P31E102-430에서 사용자가 edo1 `/opt/eolssu/ops430-sogeum-scoring-20261104/`에서 아래 두 명령으로 14곡을 적용했고, 재실행 대기 0곡과 공개 곡 상세로 18곡을 확인했다([실행 기록](../../docs/knowledge/data/sogeum-service-scores.md#운영-적용-s15p31e102-430)). 339 적재 도구는 기존 곡의 `is_scorable=0`을 요구하므로 채점을 켠 뒤 다시 적재하면 멈춘다.

```sh
sudo python3 enable_scoring.py --plan scoring-plans/sogeum.json --receipt /tmp/sogeum-scoring-check.json
sudo python3 enable_scoring.py --plan scoring-plans/sogeum.json --receipt /tmp/sogeum-scoring-apply.json --apply
```

기본은 읽기 전용이다. 곡마다 id·출처·`score_version`·삭제 여부와 현재 악보 해시가 계획 파일과 같아야 하고, 하나라도 다르면 쓰기 전에 멈춘다. `--apply`는 한 트랜잭션에서 행마다 바뀐 수가 1인지 확인하고 커밋한 뒤 다시 읽어 대조한다. 이미 바뀐 곡은 다시 쓰지 않는다. 결과는 새 receipt 파일에 남는다. 바뀌는 열은 `score_json`·`is_scorable`과 DB가 자동으로 갱신하는 `updated_at`이다. 시간 초과나 연결 오류로 receipt가 `apply-outcome-unknown`이면 서버에서 커밋됐을 수 있으므로, 다시 쓰기 전에 `--apply` 없이 실행해 실제 상태(`pending`이 비었는지)를 확인한다.

```powershell
python -m unittest discover -s infra/catalog -p "test_enable_scoring.py" -v
```

## 단소 채점 켜기 계획

Task S15P31E102-404. S15P31E102-339가 적재한 단소 곡 중 사람 검수를 마친 30곡이다. 운영 악보와 음·쉼 종류, 율명·옥타브, 시작·길이, 이벤트 ID, 시김새 유무, 박 단위가 같음을 2026-11-04 공개 곡 상세로 확인했다. 숨표 66개는 검수한 정답의 숨표이며, 이 중 43개는 운영 악보에 없던 원본 숨표다. 운영 30은 어이 얼어자리 단소2(`p183-part-2`), 31은 단소1(`p183-part-1`)이다. 북천이 단소1(25)은 운영 음이 원본과 달라 S15P31E102-451로 넘겼고, 독립 시김새 6곡은 S15P31E102-449, 차이 4곡은 451이다. 2026-11-04 S15P31E102-433에서 사용자가 edo1 `/opt/eolssu/ops433-danso-scoring-20261104/`에서 아래 두 명령을 실행해 30곡을 적용했고, 재실행 대기 0곡과 공개 곡 상세로 결과를 확인했다([실행 기록](../../docs/knowledge/data/danso-loaded-scores.md#운영-적용-s15p31e102-433)).

```sh
sudo python3 enable_scoring.py --plan scoring-plans/danso.json --receipt /tmp/danso-scoring-check.json
sudo python3 enable_scoring.py --plan scoring-plans/danso.json --receipt /tmp/danso-scoring-apply.json --apply
```

S15P31E102-451에서 5곡을 더했다(35항목). 북천이 단소1(25)과 천안삼거리(44)는 운영 음표가 원본과 달라 **악보 교체 항목**이다. 28 싸름·43 청어엮기·49 진도아리랑은 운영이 원본과 같아 숨표 항목이다. 2026-11-04 S15P31E102-476에서 사용자가 edo1 `/opt/eolssu/ops476-danso-scoring-20261104/`에서 적용했다. 5곡만 바뀌었고 재실행 대기는 0곡이다([실행 기록](../../docs/knowledge/data/danso-loaded-scores.md#운영-적용-s15p31e102-476)).

악보 교체 항목은 `targetScore`(계획 옆 `danso-scores/<곡 id>.json`)와 `targetScoreVersion`(현재 + 1)을 가진다. 도구가 하는 일은 이렇다.

- 목표 파일의 해시가 `targetSha256`과 같은지 확인한다. 목표 파일의 `score.scoreVersion`이 다음 버전인지, 숨표가 `breathEventIds`와 같은지도 확인한다.
- 같은 트랜잭션에서 `score_json`을 그 파일로 바꾼다. 동시에 `score_version`을 다음 버전으로, `note_count`를 새 음 수로 바꾸고 `is_scorable`을 켠다.
- 완료 판정은 "새 버전 + 목표 해시 + 채점 켜짐"이다.
- 서버에는 `enable_scoring.py`, `scoring-plans/danso.json`, `scoring-plans/danso-scores/`를 같은 상대 위치로 둔다.

목표 파일은 `enable_scoring.target_score(운영 악보, convert_answer(정답))`로 만든다. 운영 곡 정보·출처 ID·황 음높이는 운영 값을 두고, 음표·정간·틱은 정답 값을 쓴다. 박 단위나 정간당 박이 다르면 멈춘다. 악보 버전을 올리면 그 곡의 모범 음원은 새 버전 음원이 연결될 때까지 나오지 않는다(`ModelAudioMapper.xml`). 그래서 적용 시점은 모범 음원 담당과 맞춘다.

## 기존 악보를 유지한 36곡 채점 켜기

S15P31E102-704는 699의 교정 10곡을 제외한 36곡을 별도로 처리한다. [근거와 한계](../../docs/knowledge/data/existing-score-enablement.md), [계획](scoring-plans/existing-704.json)을 따른다. 기존 악보·검수 표시·판본·모범 음원을 바꾸지 않고 `is_scorable`과 DB 자동 `updated_at`만 바꾼다. `enable_scoring.py`의 기존 교정 CLI와 구별하며, 새 도구는 그 파일의 DB 접속·해시 함수만 가져온다.

운영 담당자가 두 Python 파일과 계획을 같은 상대 경로로 준비하고, **공유 배포 lock `/run/lock/eolssu-backend-deploy.lock`을 잡은 상태에서 DB 백업을 먼저 완료한 뒤** 아래를 실행한다. 새 receipt 경로만 허용한다. 기본 실행은 조회이고, 전체 36곡의 식별·악보 해시·현재 판본과 빠르기의 공개 음원이 맞아야 적용할 수 있다.

```sh
sudo python3 enable_existing_scores.py --plan scoring-plans/existing-704.json --receipt /root/existing-704-check.json
sudo python3 enable_existing_scores.py --plan scoring-plans/existing-704.json --receipt /root/existing-704-apply.json --apply
sudo python3 enable_existing_scores.py --plan scoring-plans/existing-704.json --receipt /root/existing-704-after.json
```

한 트랜잭션에서 대상 곡과 모든 판본의 음원 행을 잠그고 직전 조회의 열 해시·행 수·채점 상태를 대조한다. 충돌 시 전부 롤백하며 이미 켜진 곡은 다시 쓰지 않는다. 적용 후 별도 조회로 보존과 활성화 여부를 확인한다. `apply-outcome-unknown` 또는 `committed-readback-pending`이면 커밋 여부를 추측하지 않고 새 receipt를 사용한 조회 모드부터 실행한다. 출력에 `updated: []`, `pending: []`이면 해당 실행에서 쓴 곡이 없고 전체가 이미 활성화된 상태다.

빠른 검사는 `python -m unittest discover -s infra/catalog -p "test_enable_existing_scores.py" -v`다. 실제 MySQL 트랜잭션 검증은 작업 전용 컨테이너만 허용한다. 아래 비밀번호는 일회성 로컬 테스트 전용이며 포트를 노출하지 않는다. 준비 상태를 `mysqladmin ping`으로 확인한 뒤 테스트한다.

```powershell
docker run --detach --name eolssu-existing-score-704-test --env MYSQL_ROOT_PASSWORD=local-704-test-only mysql:8.4 --skip-log-bin
docker exec -e MYSQL_PWD=local-704-test-only eolssu-existing-score-704-test mysqladmin -uroot ping --silent
$env:EXISTING_SCORE_TEST_MYSQL_CONTAINER='eolssu-existing-score-704-test'
$env:EXISTING_SCORE_TEST_MYSQL_PASSWORD='local-704-test-only'
python -m unittest discover -s infra/catalog -p "test_enable_existing_scores.py" -v
docker rm --force --volumes eolssu-existing-score-704-test
```

검증은 36곡 활성화, 원본 악보·검수·음원 보존, 재실행 변경 0, 부분 완료 시 기존 시각 보존, 늦은 곡/음원 충돌·삭제·추가·동시 플래그 변경의 원자적 롤백을 포함한다. 실제 AI 추론과 공개 API 결과는 운영 실행 담당자가 별도로 기록한다.

## 교정 9곡의 악보·음원 동시 적용 (699)

`publish_scoring_repairs.py`는 정확히 `19,32,37,45,50,63,65,104,109`만 처리한다. 62는 포함할 수 없다. `release.json`의 schema는 `eolssu-scoring-repair-release/v1`, task는 `S15P31E102-699`이며 각 entry에 변경 전 곡·v1 음원 행, 목표 score/audio/performance 파일의 상대 경로·SHA-256·bytes와 목표 음원 메타데이터를 담는다. 실제 적용 묶음의 SHA와 검증·복구 근거는 [699 실행 기록](../../docs/plans/S15P31E102-699.md#2026-11-10-운영-적용-결과)을 따른다.

Python 파일 세 개(`publish_scoring_repairs.py`, `enable_scoring.py`, `stage_model_audio.py`)와 검증한 bundle을 서버에 준비한다. receipt는 매번 새 경로를 쓴다.

```sh
sudo python3 publish_scoring_repairs.py --bundle release --receipt 699-check.json
sudo flock -w 120 /run/lock/eolssu-backend-deploy.lock python3 publish_scoring_repairs.py --bundle release --receipt 699-apply.json --apply
sudo python3 publish_scoring_repairs.py --bundle release --receipt 699-after.json
sudo /opt/eolssu-backup/backup.py send db
```

적용 모드는 새 해시 prefix에 조건부 R2 PUT·전체 읽기 SHA·MIME를 검사하고, lock 안에서 DB·설정 prechange 백업을 완료한다. 곡·음원 행 잠금과 변경 전 값·음원 수·세션 부재를 다시 확인한 한 트랜잭션에서 v2 음원 추가·악보 교체·채점 활성화를 수행한다. 기존 v1 음원과 조회 이력은 유지한다. 운영 드리프트나 일부만 적용된 상태이면 중단한다. timeout/`apply-outcome-unknown`이면 새 receipt의 조회 모드로 커밋 여부부터 확인한다. 롤백이 필요하면 신규 연습 세션 유무를 다시 확인하고 보관한 beforeSong에 대한 대상별 역변경을 준비한다. 공유 DB 전체를 과거 백업으로 덮어쓰지 않는다.

빠른 검사: `python -m unittest discover -s infra/catalog -p test_publish_scoring_repairs.py`. 실제 DB 검사는 일회성 `eolssu-existing-score-704-test` MySQL 컨테이너와 별도 `repair_score_699_test` DB를 쓴다. `REPAIR_SCORE_TEST_MYSQL_CONTAINER`, `REPAIR_SCORE_TEST_MYSQL_PASSWORD`를 지정하면 성공·재실행 보존과 마지막 곡의 메타데이터/음원/추가 판본/세션 충돌에서 전체 롤백을 검사한다.

## 장단·빠르기와 모범 음원 동시 교정 (728)

`publish_score_tempo.py`는 검수한 기존 곡에 다음 판본의 음원을 추가하고 악보·기본 BPM을 한 트랜잭션에서 바꾼다. 원래 음원 행·객체와 완료 세션을 보존하며, 악보에서는 `score.scoreVersion`과 `music.jangdan`만 변경할 수 있다. 별도 `beatNotationCorrection: {"basis": "원보 대응 근거"}`가 있으면 `ticksPerBeat`만 추가로 바꿀 수 있고, 이때 `이전 BPM × 이전 ticksPerBeat = 새 BPM × 새 ticksPerBeat`로 실제 재생 속도를 보존해야 한다. 음표·가사·시김새·정간 tick·채점 가능 여부·게시 상태는 그대로 둔다. 다른 작업에서 처리하는 비행기 56·132·133·134번은 거부한다.

서버에는 `publish_score_tempo.py`, `enable_scoring.py`, `stage_model_audio.py`를 같은 디렉터리에 준비한다. `--song-id`를 반복하면 읽기 전용으로 `songs`와 모든 판본의 `audios`, 활성 세션 수를 JSON으로 내보낸다. 이 스냅샷의 곡 행과 현재 판본 음원 행을 각각 `beforeSong`, `beforeAudio`에 넣는다.

파일 경로에는 곡의 숫자 ID 대신 기존 source의 case ID를 쓴다. 예를 들어 `mr203-fixed-danso-003-p1`의 음원은 `cases/danso-003-p1/danso.m4a`, 시간표는 같은 폴더의 `performance.common.json`이다. 파일명 악기와 case 악기는 공통 악보의 instrument와 일치해야 한다. V15의 `chk_model_audio_object_key`와 `chk_model_audio_performance`가 이 형식을 요구하므로 검증기는 업로드 전에 source·악기·경로 일치를 검사한다. MySQL 회귀 검사도 실제 V15 마이그레이션의 두 CHECK를 적용한다.

묶음의 `release.json`은 `schema: eolssu-score-tempo-release/v1`, `task: S15P31E102-728`, `entries`를 가진다. 각 entry는 `songId`, `beforeSong`, `beforeAudio`, `targetBpm`, `scoreFile`, `audioFile`, `performanceFile`, `audioMetadata`와 필요 시 위 박 표기 근거를 담는다. 세 파일 명세는 상대 `path`, `sha256`, `bytes`이며 음원은 `.m4a`, 나머지는 `.json`이다. `audioMetadata`에는 `source_common_sha256`, `bpm`, `bpm_unit`, `duration_ms`, `start_offset_ms`, `content_type`, `sha256`, `status`, `generation_metadata`를 넣는다. 목표 파일의 실제 코덱·음악적 해석과 디코딩 길이는 생성 단계에서 검증한다. 적용기는 파일 해시·악보 변경 범위·수행 시간표의 판본/해시/메타데이터를 다시 검사한다. 시간표는 원보 격자의 모든 분할 위치를 순서대로 한 번씩 포함해야 하며, 정간·행·열·이벤트 ID·tick 범위·시김새 대응과 고유 occurrenceId를 대조한다. 각 시작·끝 시간은 목표 BPM과 원보 tick에서 계산한 명목 시각과 1ms 이내여야 한다. 이 교정 도구는 한 번의 악보 순회·고정 빠르기만 지원하고 반복이나 표현용 길이·템포 변화를 승인하지 않는다.

```sh
sudo python3 publish_score_tempo.py --song-id 54 --song-id 55 > before-728.json
sudo python3 publish_score_tempo.py --bundle release --receipt 728-check.json
sudo python3 publish_score_tempo.py --bundle release --receipt 728-upload.json --upload-only
# 먼저 세션 생성·분석 재시도를 차단하고 이미 들어온 요청이 모두 끝났음을 확인한다.
sudo python3 publish_score_tempo.py --bundle release --receipt 728-apply.json --apply --upload-receipt 728-upload.json --traffic-quiesced
sudo python3 publish_score_tempo.py --bundle release --receipt 728-after.json
sudo /opt/eolssu-backup/backup.py send db
```

receipt는 묶음 밖의 새 파일에 쓴다. 업로드는 `model-audio/drafts/<release.json SHA256>/`의 새 키에 조건부 PUT하고 전체 GET 해시·MIME·만료 설정을 확인한다. 적용은 같은 묶음과 정확히 일치하는 업로드 receipt를 요구한다. **적용기가 배포 lock을 직접 얻으므로 바깥에서 같은 lock을 다시 잡지 않는다.** 잠금이 사용 중이면 변경 없이 실패한다. 잠금 안에서 prechange 백업을 완료하고 곡·음원 원래 값과 활성 `RECORDING`/`PENDING` 세션 부재를 재검사한다. 트랜잭션에서도 같은 조건을 검사하며, 발견된 다른 변경이나 활성 세션에는 대상 전체를 롤백한다. `HIDDEN`/`DRAFT` 쌍도 그대로 유지한다.

**행 잠금만으로 세션 생성 경쟁을 막을 수는 없다.** 기존 요청이 이전 악보 판본을 읽고 INSERT 직전에 잠기면 적용 COMMIT 뒤 낡은 판본으로 재개할 수 있다. 실행자는 세션 생성과 분석 재시도를 운영 진입점에서 임시 차단하고, 이미 진입한 HTTP 요청을 모두 drain한 뒤에만 `--traffic-quiesced`를 지정해야 한다. 이 플래그는 실행자의 외부 조치 확인이며 도구 자체가 차단·drain을 수행하거나 자동 증명하지 않는다. 차단은 DB 적용과 사후 재조회가 끝날 때까지 유지하고, 새 판본의 API·음원 연결을 확인한 뒤 해제한다. 플래그 없는 `--apply`는 DB 조회·쓰기 전에 거부한다.

edo1의 기존 Nginx 진입점에는 같은 디렉터리의 `publish_score_tempo_quiesced.py`를 쓸 수 있다. root로 실행하며 공통 적용기의 배포 잠금을 한 번만 얻는다. 현재 `active.conf` 해시를 보존하고, 단독 소유한 `tempo-728-gate.conf`를 배타적으로 생성해 `/api/practice-sessions` 하위 요청을 잠시 503으로 차단한다. Nginx 설정 검사·reload 후 이전 worker 종료와 backend의 활성 POST 처리 수 0을 확인한 뒤 백업·DB 교체에 진입한다. 지표 누락·NaN·음수는 실패 처리한다. 완료·실패 모두 자신의 gate만 제거하고 설정 검사·reload·readiness·세션 경로 복구와 기존 route 해시를 확인한다. 관측 결과는 apply receipt 옆 `.gate.json`에 남긴다.

```sh
sudo python3 publish_score_tempo_quiesced.py --bundle release --receipt 728-apply.json --upload-receipt 728-upload.json
```

서버의 ingress/container/metric 구성이 달라지면 이 고정 운영 wrapper를 그대로 사용하지 않는다. 강제 종료 후 gate가 남았을 때는 receipt·진행 중 작업·현재 DB를 먼저 확인하고, 소유권이 확인된 `/opt/eolssu/config/backend-route/tempo-728-gate.conf` **한 파일만** 제거한다. `docker exec eolssu-runtime-frontend-1 nginx -t`가 성공하면 `nginx -s reload`하고 readiness 200과 연습 경로의 503 해제를 확인한다. `active.conf`와 다른 include는 삭제하거나 덮어쓰지 않는다. 정상 스크립트의 `restore-failed`는 수동 복구 확인 전 완료로 보고하지 않는다.

적용 직전과 COMMIT 이후 재조회 전에 receipt를 디스크에 동기화한다. `apply-outcome-unknown`/`committed-readback-pending`이나 연결 중단 후에는 새 receipt의 조회 모드로 실제 상태부터 확인한다. 이미 목표 판본·음원과 일치하면 재실행은 쓰지 않는다. 부분 적용이나 미래 판본이 있으면 중단한다. 복구 자료는 변경 전 행을 포함한 불변 `release.json`, 보존된 이전 음원·객체, prechange 백업이다. 역변경은 현재 목표 행과 신규 활성/사용 세션을 다시 확인한 대상별 CAS SQL로 준비한다. 이 도구는 자동 역변경이나 과거 DB 전체 덮어쓰기를 수행하지 않는다.

S15P31E102-1019는 같은 도구로 강원도아리랑·옹헤야의 빠르기 단위를 정간(`JEONGGAN`)으로 바꿨다. 한 박이 정간 하나가 되는(`ticksPerBeat == ticksPerJeonggan`) 박 표기 교정과 함께일 때만 단위 변경을 허용한다([적용 기록](../../docs/knowledge/data/score-tempo-1019.md)).

빠른 검사: `python -m unittest discover -s infra/catalog -p test_publish_score_tempo.py -v`. 실제 MySQL 검사는 포트를 노출하지 않는 별도 `eolssu-tempo-728-test` MySQL 8.4 컨테이너의 `tempo728_test` 스키마만 사용한다. `TEMPO_TEST_MYSQL_CONTAINER`와 `TEMPO_TEST_MYSQL_PASSWORD`를 해당 로컬 값으로 지정하면 원자적 변경·이전 음원/완료 세션 보존·늦은 메타데이터 충돌·활성 세션 추가 시 전체 롤백을 검사한다. 운영 API·앱 재생 확인은 적용 뒤 별도 수행한다.

728 wrapper의 prechange config 백업은 gate가 활성화된 동안 생성된다. 이 config 백업을 복구하면 임시 gate도 복원될 수 있으므로, 복구 종료 전에 위 소유권 확인·gate 제거·Nginx reload·503 해제 절차를 반드시 확인한다. DB만 되돌리는 경우에도 새 판본을 사용하는 활성/완료 세션을 먼저 조사한다.
