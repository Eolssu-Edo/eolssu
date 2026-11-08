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
