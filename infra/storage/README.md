# 국내 비공개 녹음 저장 전환

Task: S15P31E102-300. [구현 계약](../../docs/specs/terms-release.md).

`RECORDING_STORAGE_TYPE=local`일 때 backend는 `/var/lib/eolssu/recordings`에 원본을 저장한다. 호스트는 `/opt/eolssu/recordings`이며 웹 루트에 연결하지 않는다. backend UID/GID 10001에만 디렉터리 접근·파일 읽기/쓰기를 허용한다. 로컬 서명 키는 JWT 키와 별도로 32바이트 이상 무작위 값을 서버 `.env`에만 설정한다.

AI용 읽기 기준 URL은 Compose 내부 `http://backend:8080/api/recordings/read`다. 외부 사용자에게 이 내부 주소를 반환하지 않는다. 원본 다시 듣기를 앱에 연결할 때는 로그인한 본인 세션 확인 후 외부 HTTPS 서명 URL을 발급하는 별도 흐름이 필요하다. 이번 변경만으로 앱의 원본 다시 듣기 UI가 완성됐다고 보지 않는다.

## 사전 대조 및 준비 복사

이관 도구는 운영 서버에 설치된 boto3를 사용한다. 기본은 목록 조회이며 파일·원본·DB를 변경하지 않는다.

```sh
sudo python3 migrate_r2.py --env-file /opt/eolssu/.env --destination /opt/eolssu/recordings
```

`--stage --manifest <새 root 전용 JSON 경로>`는 운영 업로드를 유지한 채 준비 사본을 복사한다. R2 원본은 지우지 않는다. 새 사본은 크기·SHA-256을 대조하고 목록에 기록된 원래 업로드 시각을 mtime으로 보존한다. R2 HTTP 날짜는 초 단위, 목록은 소수 초를 포함할 수 있어 동일 초를 비교하고 ETag IfMatch로 원본 변경을 거절한다. 기존 국내 파일이 다르면 덮어쓰지 않는다. 예상 형식 밖 키·심볼릭 링크·크기 불일치는 중단한다.

2026-10-30 서울 리전(ap-northeast-2)을 인스턴스 메타데이터로 확인했다. 준비 복사는 12개, 1,602,140바이트를 검증했다. 사본 경로 `/opt/eolssu/recordings`, 검증 manifest `/opt/eolssu/recordings-stage-300-20261030.json`(root 전용). 원본·DB·운영 설정 전환은 수행하지 않았다.

## 최종 전환

1. 사용 중인 배포 lock과 백업 절차를 지키고 녹음 업로드를 잠시 중지한다. 분석 중 작업이 끝났는지 확인한다.
2. R2 목록 및 DB의 recording_url을 다시 대조한다. `--apply --uploads-paused --manifest <다른 새 경로>`로 준비 복사 이후 차분까지 검증한다. 이 옵션은 실제 업로드 중지를 대신하지 않는다.
3. 지정 녹음 디렉터리의 경로와 파일 목록을 확인한 뒤 UID/GID 10001, 디렉터리 0700·원본 0600을 적용한다. manifest는 별도 root 전용으로 유지한다.
4. 검증된 backend JAR와 Compose·Nginx를 배포하고 `RECORDING_STORAGE_TYPE=local`, 로컬 서명 키 및 디스크 마운트를 반영한다. R2 설정은 검증 전 복구를 위해 보존한다.
5. 실제 녹음 업로드·AI 다운로드·채점, 서명 만료·위조 거절, 삭제 후 404와 탈퇴 배치를 확인한다. 새 파일이 국내 경로에 생성되는지 확인한다.
6. 국내 사본 검증·운영 전환 후 R2 잔존 원본 삭제 계획을 별도로 승인받아 처리한다. 이 도구에는 원본 삭제 기능이 없다.

30일이 된 원본은 즉시 읽기를 거절하고 매시 15분에 디스크에서 정리한다. 정리 중 장애가 없으면 물리 삭제까지 최대 약 1시간 지연된다. 실패·프로세스 중단의 고아 업로드 임시파일도 시간별로 정리한다. 정상 업로드 임시파일은 성공·실패 시 즉시 제거한다.

## R2 복귀 (S15P31E102-333)

위 국내 디스크 저장을 대체한다. [변경 계약](../../docs/specs/terms-release.md#2026-10-30-변경-r2-복귀와-국외-이전-고지-s15p31e102-333), [작업 기록](../../docs/plans/S15P31E102-333.md).

`migrate_to_r2.py`는 기본이 조회만 한다. 쓰기·삭제는 각 옵션을 명시해야 한다. 키·서명 URL·개인정보를 출력하지 않는다.

| 명령 옵션 | 동작 |
| --- | --- |
| (없음) | 버킷 확인(HeadBucket 권한 포함) 후 디스크 녹음 수·바이트·만료 수와 DB가 참조하지 않는 수(`unreferenced`) 조회. backend·mysql 컨테이너가 실행 중이어야 함 |
| `--apply --uploads-paused --manifest <새 root 전용 경로>` | 버킷 존재를 먼저 확인하고, DB가 아직 참조하는(`recording_url` 있고 `record_deleted_at` 없음) 30일 미만 파일만 R2에 복사. 메타데이터에 원래 업로드 시각(`original-uploaded-at`)·SHA-256을 남기고 크기·해시를 재확인. 같은 키가 다르면 중단 |
| `--purge-expired [--delete]` | 메타데이터가 있는 객체 중 원래 업로드 시각 기준 30일이 지난 것을 조회·삭제. 백엔드가 새로 올린 객체는 R2 수명 주기 규칙에 맡긴다. 저장 여부를 보지 않으므로 S15P31E102-375 전환 뒤에는 쓰지 않는다 |
| `--remove-local --manifest <--apply 기록> [--delete]` | 버킷 존재를 먼저 확인한다. DB가 아직 참조하는 30일 미만 파일이 모두 `--apply` 기록에 있고 R2에서 같은 크기·SHA-256일 때만 디스크 녹음·`.upload-*` 임시파일을 조회·삭제. DB에서 이미 삭제된 녹음의 디스크 파일은 R2 확인 없이 삭제. R2에 없다는 사실만으로는 삭제를 판단하지 않으며 하나라도 어긋나면 아무것도 지우지 않음. R2 객체는 지우지 않으므로 중단 뒤 재실행해도 안전 |

DB 조회는 등록 도구(`infra/terms/publish.py`)와 같은 방식으로 backend 컨테이너의 `DB_URL`에서 DB 이름을 찾고 MySQL 컨테이너 안의 암호를 쓴다. 출력에는 개수만 남긴다.

운영 순서(각 단계 승인 후, 배포 lock·백업 절차 준수). 아래 `<checkout>`은 검토한 저장소 체크아웃이다. 국외 이전은 고지 공개·시행 뒤에만 일어나야 하므로 **3단계는 2026-11-02 00:00 KST 이후**에 시작한다.

1. MR 병합·배포를 확인한다. 이 시점 운영은 local이며 `2026-10-30`과 앱·웹이 연결하는 `/api/terms/documents/current/*`가 국내 저장 고지를 보여 준다.
2. Cloudflare 대시보드에서 버킷 `eolssu-recordings`의 `recordings/` 30일 삭제 규칙, 공개 접근 비활성, 토큰 상태를 확인한다. 서버 `.env`의 R2 값 5개가 남아 있는지 확인한다. 3단계의 조회 명령을 전환 전에 미리 실행해, 토큰이 버킷 확인(HeadBucket)을 허용하는지와 DB 조회가 되는지 확인한다.
3. 녹음 업로드를 중지하고 진행 중 분석이 끝난 것을 확인한다. 디스크 현황을 조회한 뒤 `.env`의 `RECORDING_STORAGE_TYPE`을 `s3`로 바꾸고 backend를 재시작한다. `/api/terms/documents/current/privacy`가 200이고 국외 이전 문구를 포함하는지 확인한다. 503이면 R2 값 누락이므로 `local`로 되돌린다. 이어서 [약관 등록](../terms/README.md#버전-등록)을 2026-11-02 KST 시각으로 실행하고, Play Console 개인정보처리방침 URL을 `https://k15e102.p.ssafy.io/api/terms/documents/current/privacy`로 바꾼다.

   ```sh
   sudo python3 <checkout>/infra/storage/migrate_to_r2.py --env-file /opt/eolssu/.env --source /opt/eolssu/recordings
   ```

4. 고지가 공개된 뒤 업로드 중지 상태에서 3단계에 이어 바로 기존 녹음을 복사하고 매시 정리를 바로 등록한다. 탈퇴 일괄 삭제가 도는 04:00 KST 전후에는 3~5단계를 하지 않는다. 그 사이 삭제된 녹음은 DB 참조 대조로 복사에서 빠진다. 처리방침이 "시행일에 이전"이라고 고지하므로 이 단계는 2026-11-02 KST 안에 마친다. 복사가 실패하면 원인을 해결하고 **새 manifest 경로**로 다시 실행한다(기존 경로는 덮어쓰지 않는다). 이미 옮긴 사본은 확인만 하고 새 기록에 다시 담기므로, 5단계에는 마지막 실행의 manifest를 쓴다. 정리 항목은 `--purge-expired`의 `tracked`가 0이 되면(마지막 원래 업로드 시각 + 30일 이후) 지운다. 실패하면 cron 메일로 확인한다.

   ```sh
   sudo python3 <checkout>/infra/storage/migrate_to_r2.py --env-file /opt/eolssu/.env --source /opt/eolssu/recordings --apply --uploads-paused --manifest /root/recordings-to-r2-333.json
   echo '45 * * * * root /usr/bin/python3 <checkout>/infra/storage/migrate_to_r2.py --env-file /opt/eolssu/.env --source /opt/eolssu/recordings --purge-expired --delete' | sudo tee /etc/cron.d/eolssu-r2-purge
   ```

5. 업로드를 재개하고 실제 녹음 업로드·AI 분석과 R2 새 객체를 확인한 뒤 같은 날 디스크 사본을 조회·삭제한다.

   ```sh
   sudo python3 <checkout>/infra/storage/migrate_to_r2.py --env-file /opt/eolssu/.env --source /opt/eolssu/recordings --remove-local --manifest /root/recordings-to-r2-333.json
   sudo python3 <checkout>/infra/storage/migrate_to_r2.py --env-file /opt/eolssu/.env --source /opt/eolssu/recordings --remove-local --manifest /root/recordings-to-r2-333.json --delete
   ```

되돌릴 때는 `.env`를 `local`로 바꾸면 `2026-10-30`과 `current`가 국내 저장 고지로 돌아간다. 이때 등록된 `2026-11-02` 약관 행의 본문 주소는 503이 되어 가입 동의 화면의 본문 열기가 실패하므로, 약관 버전도 새로 준비해야 한다. R2로 옮긴 녹음은 자동으로 되돌리지 않는다.

## 저장한 기록 녹음 보관 전환 (S15P31E102-375)

위 30일 수명 주기 규칙과 매시 정리를 대체한다. [계약](../../docs/specs/recording-retention.md), [작업 기록](../../docs/plans/S15P31E102-375.md). 저장한 기록의 녹음은 계속 보관하고, 저장하지 않은 녹음은 백엔드 정리 작업(매일 04:30 KST, `ended_at` 30일 경과)이 지운다.

각 단계는 승인 뒤 배포 lock 아래에서 변경 전 백업을 받고 한다. 정리 작업이 배포되기 전에 규칙을 지우면 미저장 녹음이 30일 넘게 남으므로 순서를 바꾸지 않는다.

1. 375 병합·배포를 확인한다. `current/privacy`의 시행일이 새 판(`2026-11-03`)이고, backend 로그에 정리 작업 실패가 없는지 본다. 이어 약관 버전을 등록한다([약관 등록](../terms/README.md#버전-등록)).

   ```sh
   sudo python3 <checkout>/infra/terms/publish.py
   sudo python3 <checkout>/infra/terms/publish.py --apply --effective-at YYYY-MM-DDTHH:MM:SSZ
   ```

2. Cloudflare 대시보드(계정 소유자)에서 `eolssu-recordings`의 `recordings/` 30일 삭제 규칙을 지운다. 공개 접근 비활성은 그대로 둔다.
3. 매시 정리를 지운다. 이 정리는 저장 여부를 보지 않아 저장한 이관 녹음까지 지운다.

   ```sh
   sudo rm /etc/cron.d/eolssu-r2-purge
   ```

4. R2 규칙으로 이미 파일이 지워진 저장한 녹음 수를 조회한다. 저장 기능이 2026-11-02에 배포됐으므로 2026-12-02 전에 전환하면 0건이다. 0건이 아니면 해당 객체가 실제로 없는지 확인한 뒤 `recording_url` 정리를 따로 승인받는다.

   ```sql
   SELECT COUNT(*) FROM practice_sessions
   WHERE saved_at IS NOT NULL AND record_deleted_at IS NULL AND recording_url IS NOT NULL
     AND ended_at < UTC_TIMESTAMP(6) - INTERVAL 30 DAY;
   ```

5. 다음 날 04:30 KST 뒤 backend 로그의 `Unsaved recording purge finished` 줄에서 `failed=0`과 `restored` 수를 확인한다. `restored`가 이어지면 저장소 삭제 권한·연결을 확인한다.

되돌릴 때는 R2 규칙을 다시 추가하고 이전 JAR로 배포한다. 규칙을 지운 동안 저장한 녹음은 다시 30일 기준으로 지워지므로, 되돌리기 전에 처리방침 판도 함께 정한다.

## Naver 인증 메일

메일 전환은 다른 담당자가 완료했다는 사용자 안내에 따라 이번 작업에서 제외했다. 운영 계정·비밀번호·호스트·포트를 변경하지 않는다. 기존 Naver 설정을 사용하고 실제 운영 API 확인은 해당 담당자의 변경과 대조한다.

Gmail의 기존 STARTTLS 지원은 유지한다. 이 작업에서 추가했던 465 지원과 전환 도구는 복구 가능한 로컬 보류 자료로 보존하고 활성 소스·배포 범위에서 제외했다. Google 소셜 로그인은 SMTP와 별개다.

## 검증

- `python -m unittest discover -s infra/storage -p 'test_*.py' -v`
- backend에서 `gradlew.bat clean build`. 로컬 저장·HTTP Range·변조·만료·삭제 및 공개 문서 인증 경계를 포함한다.
- `docker compose --env-file infra/runtime/.env.example -f infra/runtime/compose.yml config --quiet`는 비밀값 없는 합성 MYSQL_PASSWORD·MYSQL_ROOT_PASSWORD를 환경으로 지정해 문법을 검사한다.
