# edo1 → edo2 자동 백업과 격리 복원

작업: [S15P31E102-112](../../docs/plans/S15P31E102-112.md). 2026-10-25 사용자가 edo2 보관·추천 주기 자동화·격리 복원을 승인했다. 실제 실행 증거와 남은 범위는 작업 기록을 따른다.

## 실행 구조와 경로

edo1의 root systemd 작업이 DB·설정을 수집해 암호화한 뒤 edo2에 SSH로 전송한다. 수신 측은 암호문 해시, 복호화, 각 아카이브 파일의 해시와 경로를 검사한 뒤 새 사본을 확정한다. 같은 전송 ID·내용은 재시도할 수 있고 다른 내용으로 덮어쓰지 못한다. 실패한 전송은 edo1의 outgoing에 남아 다음 실행에서 같은 ID로 재개한다.

| 대상 | 경로 |
| --- | --- |
| 양 서버의 실행 코드 | `/opt/eolssu-backup/backup.py` |
| edo1 SSH·공개 수신 인증서 | `/etc/eolssu-backup/` |
| edo1 전송 대기·최근 48시간 암호문 캐시 | `/var/lib/eolssu-backup/outgoing`, `cache/<종류>` |
| edo2 완료 사본·수신 영수증 | `/var/lib/eolssu-backups/<종류>/<UTC 시각-ID>.cms`, `.json` |
| edo2 복호화 비밀키 | `/var/lib/eolssu-backup-keys/restore-key.pem` |
| edo2 Jenkins 키의 별도 암호화 사본 | `/var/lib/eolssu-backup-keys/controller/controller-key/` |
| edo2 최근 실제 복원 결과 | `/var/lib/eolssu-backup/restore-last.json` |

실행 코드 디렉터리는 root 소유 0755, 비밀 설정·보관·복원 작업 공간은 root 전용 0700/0600이다. SSH 전용 계정은 Docker 그룹에 들어가지 않으며, `43.203.215.107` 출발지의 전용 키와 강제 수신 명령만 허용한다. 일반 셸·포워딩·파일 읽기·삭제 명령은 허용하지 않는다. 암호화에는 edo2의 공개 인증서를 사용하고 복호화 키는 edo2에만 둔다. 이 키를 잃으면 암호문을 복구할 수 있으므로 별도의 안전한 오프라인 키 보관은 운영자가 추가로 준비해야 한다. 키를 채팅·Git·일반 백업에 넣지 않는다.

## 주기·기간(KST)

| 작업 | 주기 | 보관 |
| --- | --- | --- |
| DB | 매시 10분 | 시간별 7일 + UTC 날짜별 최초 성공본 최대 30일(일별 정리를 위해 29일 경과부터 정리) |
| 앱·프록시·인증서·활성/이전 릴리스·백업 실행 설정 | 매일 03:20, Jenkins 배포 전·후 | 30일 |
| Grafana | 매일 03:30 | 30일 |
| Jenkins 핵심 설정·잡·플러그인·Credentials | 매일 03:40 | 14일 |
| 별도 Jenkins 복구 키 | 매일 03:45 | 자동 삭제하지 않음 |
| 보관 정리 | 매일 04:20 | 최신 성공본도 종류별 보유 상한 적용. DB 사본 30일 경과 시 제거 |
| 최신 사본·주간 복원 상태 검사 | 15분마다 | DB 90분, 기타 27시간, 복원 8일 초과/실패는 비정상 |
| 격리 실제 복원 | 일요일 04:40 | 결과 파일에 대상 ID·비교·소요 시간 기록 |

DB·설정 백업 작업은 기존 배포 lock과 전용 백업 lock을 순서대로 사용한다. 배포 도구의 `prechange`·`postchange`·`postmigration`은 이미 배포 lock을 보유한 호출만 사용한다. `prechange`와 스키마를 바꾼 배포 뒤의 `postmigration`은 DB·설정을, `postchange`는 설정만 보낸다(S15P31E102-426). 외부 백업 실패 시 새 배포를 시작하지 않는다. 배포 후 사본 실패는 이미 실행된 배포의 자동 롤백을 의미하지 않으며 배포 기록과 백업 실패를 각각 확인한다. 수동 스키마·설정 변경 전에는 다음 명령으로 먼저 사본을 확보한다.

```sh
ssh -o ClearAllForwardings=yes edo1 'sudo flock -w 120 /run/lock/eolssu-backend-deploy.lock /opt/eolssu-backup/backup.py send prechange'
```

DB와 설정은 서로 다른 스냅샷이다. 저장된 시각·릴리스·스키마 호환성을 확인해야 하며, 백업 주기만으로 엄격한 1시간 RPO를 보장하지 않는다. 원본 MySQL binlog의 외부 전송·시점 재생은 포함하지 않는다. 설정 변경은 공용 lock과 변경 전 명령을 사용하는 운영 절차를 따른다.

큰 JSON 행의 기본 키 정렬에서 MySQL Error 1038이 발생하지 않도록 덤프 연결에만 `sort_buffer_size=8388608`(8 MiB)을 지정한다. 운영 덤프와 격리 복원의 데이터 대조 덤프가 같은 옵션을 사용하며 전역 DB 설정은 바꾸지 않는다. [751 재현·검증](../../docs/plans/S15P31E102-751.md)

## 상태·수동 실행

```sh
ssh -o ClearAllForwardings=yes edo1 'sudo systemctl start eolssu-backup@db.service'
ssh -o ClearAllForwardings=yes edo2 'sudo /opt/eolssu-backup/backup.py status'
ssh -o ClearAllForwardings=yes edo2 'sudo cat /var/lib/eolssu-backup/restore-last.json'
ssh -o ClearAllForwardings=yes edo1 'sudo systemctl list-timers --all "eolssu-backup*"'
ssh -o ClearAllForwardings=yes edo2 'sudo systemctl list-timers --all "eolssu-backup*"'
ssh -o ClearAllForwardings=yes edo1 'sudo systemctl list-units --failed "eolssu-backup*"'
ssh -o ClearAllForwardings=yes edo2 'sudo systemctl list-units --failed "eolssu-backup*"'
```

실패는 systemd 종료 코드·journal과 마지막 성공 영수증/복원 결과로 확인한다. 업로드 완료 시각과 DB 스냅샷 시각을 구분한다. 암호화/전송 실패를 성공으로 처리하지 않는다. 15분 상태 검사 실패는 systemd failed 상태로 드러나며 Mattermost 등 외부 메시지 알림은 연결하지 않았다.

## 격리 복원 검증

```sh
ssh -o ClearAllForwardings=yes edo2 'sudo systemctl start eolssu-backup-restore-test.service'
```

완료 사본을 다시 해시·복호화 검증한 후 root 전용 임시 디렉터리에만 복원한다. MySQL 8.4.13 새 데이터 디렉터리, 복원한 JAR·웹, Grafana DB·설정, Jenkins 핵심 홈·별도 키를 사용한다. MySQL은 계정 생성 → 스키마·데이터 → 권한 적용 순서로 복원한다. DEFINER 계정은 스키마보다 먼저, 테이블 단위 GRANT는 테이블보다 나중에 있어야 한다. 각 단계의 SQL 실패는 검증 실패로 처리한다. 테이블/뷰 목록·전체 테이블의 행 수와 데이터 해시, 웹 원문·Nginx 경유 API readiness의 HTTP 200/JSON `status=UP`, Grafana DB 상태·Jenkins 인증·Credentials 복호화를 검사한다. 계정별 grants를 원본에 다시 조회해 대조하는 검사는 포함하지 않는다. MySQL 초기화 중 임시 서버와 구분하기 위해 최종 TCP 준비 상태를 확인한다.

2026-10-29의 SQL 1146 장애는 테이블 생성 전에 테이블 단위 권한을 적용한 순서 문제였다. 실패 재현·수정 후 실제 복원 결과와 원본 코드 복구 위치는 [Task 293 기록](../../docs/plans/S15P31E102-293.md)을 따른다. 실패 이력을 초기화해서 정상화하지 않고 실제 복원 통과 후 상태 검사를 다시 실행한다.

복원 컨테이너는 무작위 실행 ID의 internal network, 자원 제한, 외부 공개 포트 없음으로 실행한다. Jenkins executor는 0이며 운영 에이전트를 연결하지 않는다. 종료 시 소유 라벨을 확인한 컨테이너·익명 볼륨·네트워크와 자체 임시 디렉터리만 정리한다. 실패 시에도 같은 정리를 수행하며 정리 실패는 성공으로 보고하지 않는다. `restore-last.json`은 임시 디렉터리 밖에 보존된다.

이 검사는 업무 API의 완성도나 서버 프로비저닝 시간까지 검증하지 않는다. DB 백업은 `eolssu`만 담는다. Metabase와 `metabase` DB는 S15P31E102-202에서 없앴다. 복원 검사는 가장 최근 백업을 쓰므로, 이 버전을 설치한 직후에는 백업을 한 번 돌린 뒤 복원 검사를 한다. 예전 백업으로 돌리면 `metabase` 항목이 남아 있어 실패로 잘못 보고된다. 원본과 무관한 `hey-*` 서비스의 PostgreSQL·n8n 데이터는 백업 범위에 포함하지 않았다.

## 설치·활성화·복구

`install.py`는 관리자 SSH 별칭 edo1·edo2로 이 Task의 소유 표식이 있는 코드, 새 전용 계정·권한, transport key·공개 수신 인증서, 비활성 타이머를 준비한다. 기존 비밀키를 재생성하지 않는다. 코드 변경 설치 전에는 해당 백업 작업이 실행 중이지 않은지 확인하고 타이머를 일시 중지한다. edo1·edo2 주소가 바뀌면 코드의 고정 주소·출발지 제한과 확인한 호스트 키를 함께 검토한다. 별도 Docker 실행 환경과 위 버전의 공식 이미지가 edo2에 필요하다.

```sh
python3 -B -m unittest discover -s infra/backup/tests -v
python3 -B infra/backup/install.py
```

첫 백업·외부 읽기 검증·실제 복원 성공 후에만 활성화한다.

```sh
ssh -o ClearAllForwardings=yes edo1 'sudo systemctl enable --now eolssu-backup-db.timer eolssu-backup-config.timer eolssu-backup-grafana.timer eolssu-backup-jenkins.timer eolssu-backup-controller-key.timer'
ssh -o ClearAllForwardings=yes edo2 'sudo systemctl enable --now eolssu-backup-prune.timer eolssu-backup-status.timer eolssu-backup-restore-test.timer'
```

문제 발생 시 해당 타이머를 `disable --now`해 신규 실행을 중단하고 진행 중인 작업을 확인한다. 기존 사본·복호화 키·이전 배포 도구는 보존한다. 설치 전 도구는 `/opt/backups/eolssu-backup112-<UTC 시각>/`에 있다. 배포 훅을 되돌릴 때는 같은 공용 배포 lock을 사용하며 앱·DB·기존 볼륨을 내리지 않는다. 전체 장애 복구는 비밀키·대상 사본을 확보하고 새 환경에 먼저 검증한 뒤 트래픽을 전환한다. 이 도구에는 운영 데이터 위에 자동 덮어쓰기 복원하는 기능이 없다.
