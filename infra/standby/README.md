# edo2 격리 대기 환경

[S15P31E102-579 계획·검증](../../docs/plans/S15P31E102-579.md)에 원본 릴리스와 실제 검증 결과를 기록한다. 운영과 같은 웹·백엔드·AI·OMR·재생 이미지를 사용한다. DB와 녹음·악보 파일은 edo2의 별도 볼륨과 디렉터리에 저장한다. 운영 계정이나 데이터베이스 백업을 가져올 필요 없이 JAR의 마이그레이션으로 스키마와 기본 카탈로그를 준비한다.

## 배치

- edo2의 전용 경로는 `/opt/eolssu-standby-579`이고 Compose 프로젝트 이름은 `eolssu-standby-579`다. `compose.yml`과 `nginx.conf`는 이 디렉터리에 둔다.
- `.env`에 `STANDBY_NGINX_IMAGE`, `STANDBY_BACKEND_JAR`, `STANDBY_WEB_DIR`, `STANDBY_PLAYBACK_ASSETS`를 지정한다. 운영에서 확인한 이미지 ID·JAR 해시·웹 파일 목록 해시로 대조한 아티팩트만 사용한다. edo2에서 이미 쓰는 Nginx 태그와 겹치지 않는 별도 태그를 붙인다.
- `STANDBY_DB_PASSWORD`, `STANDBY_DB_ROOT_PASSWORD`, `STANDBY_JWT_SECRET`, `STANDBY_RECORDING_SECRET`은 이 환경만의 값으로 만들고 `.env`는 root 전용 0600으로 보관한다. `recordings/`와 `sheets/`는 컨테이너의 UID/GID 10001이 쓸 수 있게 만든다. `tls/localhost.crt`와 `tls/localhost.key`에는 격리 검사 전용 localhost 인증서와 키를 둔다.
- 앱과 DB는 Docker 내부망을 쓴다. 프록시의 HTTPS 포트만 edo2의 `127.0.0.1:18443`에 게시한다. 기존 호스트 Nginx·점검 포트와 도메인 설정은 별개다. 예약 작업은 끄고 녹음 직후의 실제 비동기 채점은 실행한다.

edo2의 전용 디렉터리에서 실행한다.

```sh
sudo docker compose config --quiet
sudo docker compose up -d --pull never --wait --wait-timeout 125
sudo docker compose ps
sudo curl --fail --cacert tls/localhost.crt https://localhost:18443/api/actuator/health/readiness
```

## 접속과 종료

로컬 장치에서 공개 인증서만 가져온 뒤 SSH 터널로 접속한다. 인증서 파일 경로는 로컬 작업 디렉터리를 기준으로 한다.

```sh
ssh -o ClearAllForwardings=yes edo2 'sudo -n cat /opt/eolssu-standby-579/tls/localhost.crt' > standby-579.crt
ssh -N -o ExitOnForwardFailure=yes -L 18443:127.0.0.1:18443 edo2
```

다른 터미널에서 `curl --fail --cacert standby-579.crt https://localhost:18443/api/actuator/health/readiness`를 실행하면 준비 상태를 확인할 수 있다. 서비스를 정지할 때는 edo2 전용 디렉터리에서 `sudo docker compose stop`을 사용한다. 이 명령은 격리 DB와 테스트 결과를 보존한다.

## 운영 읽기 전용 복제본

edo2의 운영 복제본은 `/opt/eolssu-replication-579`의 `replica.compose.yml`과 `eolssu-replica-579_db` 볼륨을 사용한다. 위 격리 앱과 DB는 별도다. edo1의 `eolssu-replication-proxy-579`는 127.0.0.1의 13306에서만 듣고 기존 Docker 네트워크의 MySQL로 전달한다. edo2의 systemd SSH 터널은 내부 브리지에만 바인딩한다. 방화벽은 복제 DB의 IPv4에서 터널 주소로 들어오는 연결 하나를 허용한다. edo1의 전용 키는 edo2 고정 IP와 단일 대상 전달로 제한한다. SSH 개인 키, 로컬 MySQL 비밀번호, 초기화 비밀번호는 각각 호스트의 보호 경로에서만 다룬다.

edo2에서 상태를 점검한다.

```sh
sudo systemctl status eolssu-replication-tunnel.service eolssu-replication-watch.timer --no-pager
sudo /usr/bin/python3 -B /opt/eolssu-replication-579/check-replication.py
sudo journalctl -u eolssu-replication-watch.service -n 20 --no-pager
```

상태 검사는 20초마다 실행한다. 수신·적용 스레드 오류, 10초 초과 또는 알 수 없는 적용 지연이 있으면 실패로 기록한다. 실패는 systemd와 기존 edo2 건강 점검에서 확인한다. 복제가 끊겨도 edo1이 계속 쓰기를 받는 정책이다. 마지막 복제 이후의 쓰기는 복제본이 원본을 따라잡기 전에 원본이 사라지면 유실될 수 있다.

초기 사본 생성은 사용자 데이터가 없는 복제본에만 `seed-replica.py`를 사용한다. edo1 기존 배포 잠금을 잡고 임시 초기화 권한을 발급한 뒤 실행하며, 대상 DB가 이미 있는 경우에는 데이터 보존과 별도 복구 판단을 우선한다. 사본 해시를 대조하고 쓰기 금지 상태에서 복제를 시작한다. 수신·적용 상태와 원본 GTID를 따라잡은 결과를 확인한 뒤 임시 초기화 계정을 제거한다.

복제 연결을 운영에서 빼야 하면 edo2 복제본에서 `STOP REPLICA`를 실행하고 터널과 상태 검사 타이머를 중지한다. 이렇게 하면 edo1의 앱과 쓰기 DB는 계속 요청을 받는다. 복제 볼륨과 마지막 GTID를 보존한다. GTID 설정의 원복이 별도로 필요한 경우 모든 복제 채널을 먼저 중지하고 원본을 확인한 뒤 격리 검증을 거친 역순 절차를 적용한다. 승격은 기존 쓰기 원본을 차단하고 받은 거래를 모두 적용한 뒤 운영자가 수행한다.
