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

1. 대상 DB의 백업·버전 확인 후 V13를 적용한다. 운영 ops는 자동 마이그레이션을 하지 않으므로 새 API 배포 전에 완료한다.
2. M4A와 manifest를 검증하고 DRAFT 적재 결과를 대조한다.
3. 승인된 비공개 자산 저장소에 manifest의 objectKey와 `Content-Type: audio/mp4`로 업로드한다. `recordings/` 경로 및 녹음용 30일 삭제 정책을 사용하지 않는다. 버킷 공개 정책은 변경할 필요가 없다.
4. 업로드한 각 객체의 전체 다운로드 SHA-256·크기와 재생을 대조한다. 단순 ETag를 SHA-256으로 취급하지 않는다. 장기 보관 규칙과 클라이언트 읽기 정책도 확인한다.
5. 검증된 정확한 assetId만 PUBLISHED로 변경한다. 일괄 무조건 게시하지 않는다. rejected 라벨은 게시할 수 없다. inferred는 재생 참고 자료이며 채점 정답으로 승격하지 않는다.
6. `REFERENCE_AUDIO_STORAGE_*`에 별도 읽기용 설정을 주입한다. 이 설정은 사용자 녹음의 `RECORDING_STORAGE_TYPE`과 독립적이다.
7. 실제 조회·302 재생 이동·음원 디코딩·만료와 앱 재생을 확인한다. soundUrl은 상대 API 경로이므로 앱은 서버 기준 주소로 해석한다. 임시 서명 URL을 DB나 장기 캐시에 저장하지 않는다.

실제 R2 게시·운영 DB 적용·서비스 재시작은 이 구현 작업에서 실행하지 않았다. 기존 CAT-06의 대표 음원 선정과 FE 연결은 별도 작업으로 남는다.
