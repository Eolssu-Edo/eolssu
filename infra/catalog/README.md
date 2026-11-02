# 기본음·시김새 카탈로그 적재

Task S15P31E102-364. [입출력·API 계약](../../docs/specs/reference-audio-catalog.md). 운영 변경 전에 검증된 구현·마이그레이션·자산 게시 결과를 확인한다.

## SQL 준비

M4A 변환 도구가 만든 schemaVersion 1 manifest와 같은 폴더의 파일을 사용한다. 저장소 루트에서 실행한다.

```powershell
python infra/catalog/import_reference_audio.py --manifest data/processed/reference-audio-v1/manifest.json --output data/processed/reference-audio-v1/import-draft.sql
python -m unittest discover -s infra/catalog -p "test_*.py" -v
```

출력 경로는 새 파일이어야 한다. 도구는 모든 파일의 경로·크기·SHA-256, dataset/label의 안정 ID, reference-audio 전용 키, 형식·길이·출처 메타데이터·검수 상태를 확인한다. 미디어의 실제 AAC 디코딩과 길이 대조는 앞선 변환 도구가 수행한다. SQL 준비는 DB/R2에 접속하지 않는다.

SQL은 V13 적용 이후 **전용 연결에서 `mysql`의 `--force` 없이** 실행한다. 비밀값은 환경이나 권한 제한된 클라이언트 설정으로 주입한다. 임시 테이블 생성 권한이 필요한 적재 전용 작업이며 공개 HTTP 쓰기 API는 없다. SQL에는 DB 이름·접속 비밀값이 없다. 적용 전 선택한 DB를 확인한다.

MySQL 회귀 검사는 실행자가 별도로 만든 `eolssu-reference-audio-*` 이름의 폐기 가능한 MySQL 8.4 Docker 컨테이너만 사용한다. `REFERENCE_AUDIO_TEST_MYSQL_CONTAINER`와 `REFERENCE_AUDIO_TEST_MYSQL_PASSWORD`를 그 컨테이너의 값으로 지정한 뒤 위 unittest 명령을 실행한다. 도구는 컨테이너 안의 `reference_audio_import_test` DB를 만들고 그 안의 테스트 테이블만 재생성한다. 이 환경변수가 없으면 MySQL 검사 3개는 건너뛰며, 이를 통과로 보고하지 않는다.

- 새 항목은 DRAFT다. 데이터 파일·객체가 자동 업로드되거나 게시되지 않는다.
- 동일 자산·메타데이터 재실행은 행을 늘리지 않고 기존 게시 상태를 유지한다.
- 같은 assetId 또는 datasetId/labelId에 다른 내용이 있으면 CHECK 오류로 신규 삽입 전에 중단한다. 오류를 무시하는 클라이언트로 실행하지 않는다. 연결 종료 시 진행 중 트랜잭션이 롤백된다.
- 삽입문도 충돌 검사 통과 행을 요구하므로 오류를 잘못 무시한 경우에도 신규 행을 추가하지 않는다.
- 교체가 필요하면 출처·음원 변경을 먼저 검토하고 명시적인 교체 절차를 마련한다. 이 도구는 기존 행을 덮어쓰지 않는다.

## 운영 게시 순서

1. 대상 DB의 백업·버전 확인 후 V13~V15를 순서대로 적용한다. 운영 ops는 자동 마이그레이션을 하지 않으므로 새 API 배포·매핑 적용 전에 완료한다.
2. M4A와 manifest를 검증하고 DRAFT 적재 결과를 대조한다.
3. 승인된 비공개 자산 저장소에 manifest의 objectKey와 `Content-Type: audio/mp4`로 업로드한다. `recordings/` 경로 및 녹음용 30일 삭제 정책을 사용하지 않는다. 버킷 공개 정책은 변경할 필요가 없다.
4. 업로드한 각 객체의 전체 다운로드 SHA-256·크기와 재생을 대조한다. 단순 ETag를 SHA-256으로 취급하지 않는다. 장기 보관 규칙과 클라이언트 읽기 정책도 확인한다.
5. 검증된 정확한 assetId만 PUBLISHED로 변경한다. 일괄 무조건 게시하지 않는다. rejected 라벨은 게시할 수 없다. inferred는 재생 참고 자료이며 채점 정답으로 승격하지 않는다.
6. `REFERENCE_AUDIO_STORAGE_*`에 별도 읽기용 설정을 주입한다. 이 설정은 사용자 녹음의 `RECORDING_STORAGE_TYPE`과 독립적이다.
7. 실제 조회·302 재생 이동·음원 디코딩·만료와 앱 재생을 확인한다. soundUrl은 상대 API 경로이므로 앱은 서버 기준 주소로 해석한다. 임시 서명 URL을 DB나 장기 캐시에 저장하지 않는다.

실제 R2 게시·운영 DB 적용·서비스 재시작은 이 구현 작업에서 실행하지 않았다. 기존 CAT-06의 대표 음원 선정과 FE 연결은 별도 작업으로 남는다.

## 명목상 기준 음 연결

V14는 `pitch_id` FK와 `pitch_mapping_basis`를 nullable로 추가한다. 마이그레이션만으로 기존 자산을 연결하지 않는다. 이미 DRAFT 또는 PUBLISHED로 적재된 행에 아래 별도 매핑 SQL을 적용할 수 있다.

```powershell
python infra/catalog/link_reference_audio_pitches.py --manifest data/processed/reference-audio-v1/manifest.json --mapping infra/catalog/pitch-mappings/danso-basic-v1.json --output data/processed/reference-audio-v1/link-pitches.sql
python infra/catalog/link_reference_audio_pitches.py --manifest data/processed/reference-audio-v1/manifest.json --mapping infra/catalog/pitch-mappings/jeongak-daegeum-basic-v1.json --output data/processed/reference-audio-v1/link-jeongak-pitches.sql
python infra/catalog/link_reference_audio_pitches.py --manifest data/processed/reference-audio-v1/manifest.json --mapping infra/catalog/pitch-mappings/sogeum-basic-v1.json --output data/processed/reference-audio-v1/link-sogeum-pitches.sql
python infra/catalog/link_reference_audio_pitches.py --manifest data/processed/reference-audio-v1/manifest.json --mapping infra/catalog/pitch-mappings/sanjo-daegeum-basic-v1.json --output data/processed/reference-audio-v1/link-sanjo-pitches.sql
```

생성한 SQL 네 파일을 같은 대상 DB의 전용 mysql 연결에서 각각 `--force` 없이 실행한다. 연결 순서는 **V14 관계 + V15 기준 음 → 기존 manifest 적재 → 매핑 SQL 네 파일**이다. 파일별 트랜잭션이므로 앞 파일 성공 후 다음 파일이 실패하면 성공한 연결은 보존된다. 원인을 수정하고 같은 파일을 다시 적용할 수 있다. 파일 업로드·PUBLISHED 전환과는 별도이며 URL을 입력하지 않는다. 새 CLI의 환경변수는 없다.

- 명시적 매핑 네 파일은 단소30·정악대금54·소금90·산조대금45, 총219개의 datasetId·labelId·assetId·파일 SHA-256·분류·취법·율명과 대상 yul/octave를 열거한다. `basis`에 자료·버전·명목상 연결임을 남긴다. 기존 단소 파일/근거는 보존했다. 실행할 때 이름으로 새 매핑을 추정하지 않는다.
- manifest 전체의 원래 파일/해시/출처 검증 후, 각 매핑이 실제 자산과 일치하는지 검사한다. 중복·미등록 자산·시김새·잘못된 목표 음·빈 근거는 SQL 생성 전에 거부한다.
- SQL은 `(yul, octave)`로 `pitches`를 조회한다. ID 숫자를 환경 간 복사하지 않는다. 대상 pitch나 DB 자산이 없거나 파일/분류/기존 연결·근거가 다르면 배치 전체를 거부한다. 확인 전에 대상 행을 잠그고 검증 통과 때만 갱신한다.
- 동일 매핑 재실행은 연결과 갱신 시각을 그대로 둔다. 기존 import도 연결·게시·검수 상태를 보존한다. 기존 연결을 다른 기준으로 덮어쓰는 도구가 아니므로 수정은 별도 검토가 필요하다.
- 평취 중·임·무는 octave0, 황·태는 octave1이며 역취는 각각 +1이다. 기준은 [단소 라벨링](../../docs/knowledge/data/danso.md), [기준 음 데이터](../../data/reference/pitches.json), V5다. `review=inferred`, `eligibleForNoteScoring=false`는 유지한다.
- 정악대금은 기존 저취 임 시작·18음 순서와 공식 음역을 대조한다. 저취 임/남/무=octave−1, 황/태/고/중=octave0이며 평취+1·역취+2다. 소금은 평취 임/남/무=octave0, 황/태/고/중=octave1이며 역취+1, 중청임=임2다. 필요한 기준 음은 V15에서 보충하며 기존11개 ID/반음은 보존한다. 현재 범위 조회 방식상 전역22개·단소15개가 반환된다.
- 산조대금도 [공식 대금 항목④](https://www.gugak.go.kr/ency/topic/view/108)의 정간보 전폐음㑣 표기(2026-11-02 확인)와 기존 저취 임 시작·상하 옥타브 음렬을 결합해 명목상 관계를 정했다. 실제 조율이나 본청 C를 황종 C로 해석한 것이 아니며 검수 상태는 유지한다.
- 나머지211개는 시김새207개와 개별 라벨 대안4개다. 대안은 소금 `ngc-sogeum-2654.group-006`·`ngc-sogeum-2655.group-006`·`ngc-sogeum-2656.group-006`의 협/고와 산조 `ngc-sanjo-2552.region-004`의 무/황/남이다. 미연결 전체를 라벨 근거 부족으로 취급하지 않는다.

검사: `python -m unittest discover -s infra/catalog -p 'test_*.py'`. 실제 MySQL 검사는 `REFERENCE_AUDIO_TEST_MYSQL_CONTAINER`를 `eolssu-reference-audio-`로 시작하는 폐기 가능한 컨테이너, `REFERENCE_AUDIO_TEST_MYSQL_PASSWORD`를 그 테스트 비밀번호로 지정한다. `reference_audio_import_test` DB에서 합성 테이블을 재생성하므로 서비스 DB를 지정하지 않는다.
