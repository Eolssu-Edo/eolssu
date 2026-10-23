# edo1의 eolssu 운영 구성

운영 Compose 원본은 `/opt/eolssu/config/compose.yml`, 실제 환경변수는 `/opt/eolssu/.env`입니다. 이 폴더의 `compose.yml`·`nginx.conf`는 서버에서 확인한 프론트·백엔드·MySQL 구성을 관리합니다. 루트의 개발/운영 Compose 두 서비스 예시와 구분합니다.

## 이름과 데이터 보존

| 대상 | 운영 값 |
| --- | --- |
| Compose 프로젝트 | `eolssu-runtime` |
| 컨테이너 | `eolssu-runtime-frontend-1`, `eolssu-runtime-backend-1`, `eolssu-runtime-mysql-1` |
| 설정·릴리스 경로 | `/opt/eolssu/config`, `/opt/eolssu/releases` |
| HTTP | 기존 공개 바인딩·80 포트 유지 |
| MySQL 물리 볼륨 | `eolsu-runtime_mysql_data`를 external volume으로 재사용 |
| 앱 내부 패키지·JAR·DB 식별자 | 이번 작업에서 변경하지 않음 |

물리 볼륨의 기존 이름은 데이터 식별자이므로 서비스명 변경에 맞춰 새 볼륨을 만들지 않습니다. `MYSQL_DATA_VOLUME`이 없으면 구성 검사를 실패시키며, 명시한 외부 볼륨이 없으면 실행도 실패합니다. 기존 `.env`의 DB 이름·사용자·비밀번호를 보존합니다.

## 확인·실행·복구

실제 환경변수 값이 출력되지 않도록 구성 검사는 `--quiet`로 실행합니다.

```sh
sudo docker compose --env-file /opt/eolssu/.env \
  -f /opt/eolssu/config/compose.yml config --quiet
sudo docker compose --env-file /opt/eolssu/.env \
  -f /opt/eolssu/config/compose.yml ps
curl -i http://127.0.0.1/
curl -i http://127.0.0.1/api/infra-smoke
```

프론트 `/`는 200, 아직 등록되지 않은 `/api/infra-smoke`는 백엔드 JSON 404가 정상입니다. Nginx HTML 404나 502와 구분합니다. 배포 변경 후에는 이 응답, Nginx 설정 검사, MySQL health·물리 볼륨을 함께 확인합니다.

이름 전환 전 설정·릴리스와 SQL 전체 백업은 서버의 `/opt/backups/eolssu-S15P31E102-65-20261022T072645Z/`에 root 전용으로 보관했습니다. `receipt.json`에는 아티팩트 SHA-256·DB 서버 UUID·물리 볼륨·검증 결과가 있습니다. SQL과 실제 `.env`는 저장소나 로그로 복사하지 않습니다. `/opt/eolsu`도 변경 전 복귀 경로로 보존합니다.

전체 이름 전환을 되돌려야 하면 배포 담당자가 현재 작업·접속을 확인하고 새 프로젝트를 중지한 뒤 기존 프로젝트를 실행합니다. 볼륨을 삭제하지 않습니다.

```sh
sudo docker compose --env-file /opt/eolssu/.env \
  -f /opt/eolssu/config/compose.yml down --timeout 30
sudo docker compose --env-file /opt/eolsu/.env \
  -f /opt/eolsu/config/compose.yml up -d --pull never --wait --wait-timeout 120
```

## Let's Encrypt와 HTTPS

`/opt/app/infra`는 서버 소스 체크아웃이며 운영 설정은 `/opt/eolssu/config`에 있습니다. 소스가 오래되었거나 스크립트가 없는 버전에서는 `./scripts/init-letsencrypt.sh`를 실행할 수 없습니다. develop 병합 후 다음과 같이 소스를 갱신하고 실행합니다. 기존 작업 트리가 깨끗한지 먼저 확인합니다.

```sh
cd /opt/app
git status --short
git pull --ff-only origin develop
sudo apt-get install -y certbot
cd infra
./scripts/init-letsencrypt.sh
```

기본 도메인은 `k15e102.p.ssafy.io`이며 `--domain`으로 DNS 이름을 지정할 수 있습니다. 선택한 연락처는 `--email`로 전달합니다. 생략하면 이메일 없는 ACME 계정으로 등록합니다. DNS가 이 서버를 가리키고 외부에서 TCP 80·443에 접속할 수 있어야 합니다. 현재 Compose는 `/opt/eolssu/acme/.well-known`을 프론트의 `.well-known`에 읽기 전용으로 mount합니다. 초기화 도구는 이 mount를 찾아 고정 `/opt/eolssu/acme`를 Certbot webroot로 사용합니다. 이전 구성에는 실제 웹 릴리스 경로를 사용합니다. 이 방식은 [Certbot webroot 안내](https://eff-certbot.readthedocs.io/en/stable/using.html#webroot)를 따릅니다.

스크립트는 필요할 때 sudo로 재실행합니다. 기존 운영 `.env`와 기본 Compose를 보존하고, 인증서 발급·Nginx 설정 검사를 통과한 다음 `compose.tls.yml`로 프론트에 443과 인증서 읽기 전용 mount를 추가합니다. TLS 도메인의 HTTP 요청은 경로·쿼리·메서드를 유지하는 308로 HTTPS에 전환합니다. `/.well-known/acme-challenge/`는 같은 webroot의 파일만 HTTP로 제공하고, 토큰이 없으면 404를 반환합니다. 도메인 전용 80 server가 이 동작을 담당하며, `127.0.0.1`을 Host로 사용하는 기존 Jenkins 검사와 최초 인증서 발급은 기본 HTTP server를 계속 사용합니다. 백엔드·MySQL·DB 볼륨은 재생성하지 않습니다. 교체 중에는 프론트 요청이 잠시 끊길 수 있습니다. 설정·HTTPS 검증에 실패하면 이전 프론트 구성을 복구하며 설정 백업은 `/opt/eolssu/tls-*`에 남습니다. Jenkins 배포와 같은 lock을 사용합니다.

발급한 인증서는 `/etc/letsencrypt`에만 보관합니다. `certbot.timer`가 갱신을 확인하며, 갱신 성공 후 root 소유 `/etc/letsencrypt/renewal-hooks/deploy/eolssu-nginx`가 Nginx 설정 검사·reload를 수행합니다. 재실행은 유효한 인증서를 강제로 재발급하지 않습니다.

```sh
curl -I 'http://k15e102.p.ssafy.io/nested?check=redirect'
curl -I https://k15e102.p.ssafy.io/
curl -i https://k15e102.p.ssafy.io/api/infra-smoke
sudo certbot renew --dry-run
sudo systemctl status certbot.timer --no-pager
python3 -B -m unittest discover -s infra/scripts/tests -v
RUN_NGINX_TESTS=1 python3 -B -m unittest discover -s infra/scripts/tests -v
```

HTTP 검사는 `308`과 원래 경로·쿼리가 포함된 HTTPS `Location`을 확인합니다. 기본 Python 검사는 외부 도구 없이 TLS 초기화의 복구 계약을 확인합니다. `RUN_NGINX_TESTS=1` 검사는 Docker·OpenSSL이 필요하며, 임시 인증서와 loopback 임의 포트의 격리 Nginx로 실제 HTTP 전환·ACME·내부 검사·HTTPS를 확인한 뒤 컨테이너와 fixture를 정리합니다.

HTTPS 적용 후 프론트를 수동 재생성할 때는 두 Compose 파일을 함께 사용합니다. 백엔드만 바꾸는 Jenkins 배포에는 기존 명령을 그대로 사용합니다.

```sh
sudo docker compose --env-file /opt/eolssu/.env \
  -f /opt/eolssu/config/compose.yml -f /opt/eolssu/config/compose.tls.yml \
  up -d --no-deps --pull never frontend
```

HTTPS를 되돌릴 때는 기본 Compose만으로 프론트를 재생성합니다. 인증서·원래 HTTP 설정·DB 데이터는 유지됩니다.

```sh
sudo docker compose --env-file /opt/eolssu/.env \
  -f /opt/eolssu/config/compose.yml up -d --no-deps --pull never frontend
```

## Jenkins

서버에 Jenkins LTS `2.580.1`을 설치했습니다. 서비스는 `jenkins.service`, 홈은 `/var/lib/jenkins`이며 `127.0.0.1:8080`에서만 수신합니다. 관리자 인증·CSRF를 사용하고 익명 API 접근·사용자 직접 가입·인바운드 에이전트 포트는 허용하지 않습니다. Java 21 JRE의 글꼴 라이브러리 의존성이 필요합니다.

관리자 이름은 `eolssu-admin`입니다. 초기 비밀번호는 서버의 `/var/lib/jenkins/secrets/eolssu-admin-password`에 0600으로 보관하며 채팅·저장소에 기록하지 않습니다. 사용자 본인의 SSH 터미널에서 다음과 같이 조회할 수 있습니다.

```sh
ssh edo1 'sudo cat /var/lib/jenkins/secrets/eolssu-admin-password'
ssh -N -L 18080:127.0.0.1:8080 edo1
```

터널을 유지한 상태에서 브라우저로 `http://127.0.0.1:18080/`에 접속합니다. 로컬 18080이 사용 중이면 다른 로컬 포트를 선택합니다. Jenkins의 인증 정보를 실제로 변경했다면 위 초기 비밀번호 파일과 서버 전용 CLI 인증 파일도 함께 정리해야 합니다.

백엔드 잡은 `eolssu-backend`, GitLab 연결은 Credentials `eolssu-gitlab`을 사용합니다. 기존 서버 Git 연결 정보를 Jenkins의 암호화된 Credentials 저장소에 옮기고 잡에는 비밀값 없는 저장소 URL·`develop`만 지정합니다. 구성 원본은 [infra/jenkins/backend.Jenkinsfile](../jenkins/backend.Jenkinsfile)이며 서버 복사본은 `/opt/eolssu/config/backend.Jenkinsfile`입니다.

현재 MR의 인프라 파일이 아직 develop에 없으므로 잡은 서버의 관리된 Pipeline 정의에서 develop 소스를 명시적으로 checkout합니다. Dockerfile과 Dockerfile 전용 ignore 규칙도 `/opt/eolssu/config`의 관리 파일을 사용합니다. 앱 소스는 수정하지 않습니다. CI 구성 검사는 실제 DB 비밀번호 대신 `ci.env.example`의 고정 데이터만 사용하며 서비스를 기동하지 않습니다.

잡은 `develop`을 1분마다 SCM polling으로 확인합니다. 변경이 있을 때만 실행하며 직접 Push로 develop이 바뀌는 경우도 감지합니다. 별도 GitLab webhook이나 Jenkins 공개 포트는 필요하지 않습니다. 정확한 ref `refs/heads/develop`만 checkout하고, 동시 실행을 막으며 20분 제한을 둡니다. 경로 필터는 없습니다.

실행 순서는 Gradle `clean build`·JUnit 수집 → Compose/Nginx 검사 → 커밋 revision label을 포함한 이미지 빌드 → JAR 보관 → 후보 실행·백엔드 배포입니다. 이미지에서 추출한 JAR을 `/opt/eolssu/releases/<40자리 SHA>/app.jar`에 보관하고, 실제 `.env`의 `BACKEND_JAR`만 원자적으로 교체합니다. 기존 JRE+읽기 전용 bind mount 구성을 유지합니다. 프론트와 MySQL은 재생성하지 않고 컨테이너 ID·물리 볼륨이 같은지 검사합니다.

후보 이미지는 포트를 공개하지 않는 임시 internal network에서 실행합니다. 운영 DB 비밀값은 전달하지 않습니다. 호스트가 후보 IP의 `/api/infra-smoke`에서 Spring JSON 404를 확인해야 배포합니다. 현재는 별도 readiness API가 없으므로 이 응답이 서버 기동·프록시 검증 기준입니다. 향후 DB 의존성이나 인증이 추가되면 해당 변경에서 전용 readiness 계약과 격리된 검사 환경을 함께 조정해야 합니다.

배포 도구 원본은 [deploy-backend.py](../jenkins/deploy-backend.py), 서버 설치 위치는 `/opt/eolssu/bin/deploy-backend`입니다. root 소유 0755로 설치하고 Jenkins에는 아래 도구의 sudo 실행만 허용합니다. 운영 `.env`는 root 전용 0600을 유지합니다. Jenkins의 기존 Docker 접근도 서버 수준 권한이므로 잡 설정·실행은 승인된 관리자만 사용합니다.

```sudoers
jenkins ALL=(root) NOPASSWD: /opt/eolssu/bin/deploy-backend *
```

도구는 인자 하나의 40자리 SHA·이미지 revision·원격 develop의 현재 SHA를 확인합니다. Python isolated mode와 고정 PATH를 사용하고 임의 파일·셸 명령 인자를 받지 않습니다. 배포 직전 develop이 바뀌면 이전 후보를 배포하지 않고 실패시키며 다음 polling이 최신 변경을 실행합니다.

## 프론트 웹 자동 배포

`eolssu-backend`가 develop 변경을 1분마다 감지하고 백엔드 배포까지 성공하면, 같은 전체 SHA를 `REVISION`으로 전달해 `eolssu-frontend`를 시작합니다. 상위 잡은 프론트 실행을 예약한 뒤 executor를 반환합니다. 프론트 완료는 `eolssu-frontend`의 SUCCESS와 `/opt/eolssu/frontend-deployment.json`을 기준으로 확인합니다. 프론트 잡에는 별도 polling을 중복 등록하지 않습니다.

프론트 잡은 다음 명령을 Jenkins 사용자로 실행합니다. JDK 21·Docker·Google Chrome이 필요하며 Node·Yarn·Wasm tooling은 Gradle이 관리합니다. Linux 서버에서 웹 타깃만 빌드·테스트합니다. Android·iOS 패키지 배포는 포함하지 않습니다.

```sh
cd frontend
CHROME_BIN=/usr/bin/google-chrome ./gradlew --no-daemon \
  :shared:wasmJsTest :shared:jsTest :webApp:wasmJsBrowserDistribution
```

JUnit 결과와 `webApp/build/dist/wasmJs/productionExecutable/`를 보관하고 [frontend.Dockerfile](../jenkins/frontend.Dockerfile)로 커밋 revision label을 붙인 `eolssu-frontend:ci-<SHA>`를 만듭니다. 관리된 Pipeline 원본은 [frontend.Jenkinsfile](../jenkins/frontend.Jenkinsfile)입니다. 초기 샘플 테스트는 JS·Wasm 합계 4개이며 업무 기능 검증 범위는 앱 테스트가 추가되는 대로 넓혀야 합니다.

[deploy-frontend.py](../jenkins/deploy-frontend.py)를 root 소유 `/opt/eolssu/bin/deploy-frontend`에 설치하고 Jenkins에는 이 도구의 실행만 추가로 허용합니다. SHA 검증·원자적 파일 교체·HTTP 검사·공유 lock은 인접한 root 소유 `deploy-backend`의 검증된 공통 함수를 사용하므로 두 도구를 함께 관리합니다. Jenkins에는 운영 `.env` 읽기 권한을 주지 않습니다.

```sudoers
jenkins ALL=(root) NOPASSWD: /opt/eolssu/bin/deploy-frontend *
```

도구는 SHA·이미지 label·최신 원격 develop을 확인하고, 비공개 internal network의 후보 Nginx에서 **모든 정적 파일의 SHA-256과 Wasm MIME**을 검사합니다. HTML fallback이 JS 누락을 가리는 경우도 실패합니다. symlink·특수 파일·필수 번들 누락·256MiB/5,000개 초과 산출물은 거부합니다. 통과한 이미지에서 추출한 파일만 `/opt/eolssu/releases/<SHA>/web`에 보관하며 다른 내용으로 덮어쓰지 않습니다.

배포 journal에 이전 웹 경로를 기록한 다음 실제 `.env`의 `WEB_DIST_DIR`만 원자적으로 교체하고, HTTP+TLS Compose로 프론트만 재생성합니다. HTTP 정적 자산·HTTPS 인증서·API 프록시·실제 bind mount와 백엔드/MySQL ID·DB 볼륨을 확인합니다. 새 릴리스의 `/__eolssu_release.json`에서 SHA·manifest 해시를 조회할 수 있습니다. 프론트 교체 중에는 짧은 연결 중단이 있을 수 있습니다.

백엔드·프론트 배포는 같은 lock을 사용하고 최대 120초 기다립니다. 실패하면 이전 프론트를 복구하고, 강제 종료로 남은 `deploying`·`rollback_failed` journal은 다음 실행에서 먼저 복구합니다. 동일 SHA·이미지·정적 파일이 이미 배포돼 있으면 상태·실제 응답을 확인하고 재생성하지 않습니다. 실패한 동일 커밋을 자동으로 무한 재시도하지 않습니다. 원인을 수정한 커밋을 병합하거나, 원인 제거 후 상위 잡을 수동 실행해 같은 흐름을 재개합니다.

```sh
ssh edo1 'sudo cat /opt/eolssu/frontend-deployment.json'
curl -fsS https://k15e102.p.ssafy.io/__eolssu_release.json
python3 -B -m unittest discover -s infra/jenkins/tests -v
```

수동 복구가 필요하면 실행 중인 배포가 없는지 확인하고 자동 트리거를 잠시 중지합니다. journal의 `previous_web`를 확인해 `sudoedit /opt/eolssu/.env`에서 `WEB_DIST_DIR`만 복구하고 위의 두 Compose를 사용하는 프론트 재생성 명령을 실행합니다. 이전 릴리스·journal·설정 백업은 보존하고 DB·볼륨을 내리지 않습니다.

이전 인증서 설정을 고정 경로로 옮길 때는 프론트에 ACME mount를 적용한 후 **도메인별 매핑도 함께** 바꿉니다. 서버의 Certbot 2.9는 `reconfigure --webroot-path`로 기존 `webroot_map`을 바꾸지 못하고, `--webroot-map`을 추가하면 도메인 변경으로 인식해 거부합니다. 실제 시험에서 두 동작을 확인했습니다. 이번 설치에서는 root 전용으로 원본을 백업한 뒤 ConfigObj로 갱신 설정의 `renewalparams.webroot_path`와 `renewalparams.webroot_map` 두 항목만 원자적으로 변경하고, 아래 명령으로 **저장된 설정 자체**와 reload hook을 검증했습니다. 실패하면 설정·프론트를 이전 상태로 복구했습니다. 운영 인증서는 강제 재발급하지 않았습니다.

```sh
sudo certbot renew --cert-name k15e102.p.ssafy.io --dry-run \
  --run-deploy-hooks --no-random-sleep-on-renew
```

실제 설치·활성화·자동 실행·복구 검증과 백업 위치는 [Task 73 작업 기록](../../docs/plans/S15P31E102-73.md)을 따릅니다.

`/run/lock/eolssu-backend-deploy.lock`은 중복 배포를 막고 `/opt/eolssu/deployment.json`에 배포 전 이전 JAR·후보·해시·상태를 원자적으로 기록합니다. 재실행한 동일 이미지가 이미 실행 중이면 재시작하지 않습니다. 교체 또는 HTTP 검증이 실패하면 이전 JAR로 복구합니다. 강제 종료로 상태가 `deploying`에 남거나 `rollback_failed`라면 다음 실행은 복구부터 수행합니다. 실패 후 자동으로 무한 재시도하지 않으며 원인을 수정한 뒤 같은 잡을 수동 실행할 수 있습니다. 임시 후보 컨테이너·네트워크는 정리하고 강제 종료 잔여물은 다음 실행에서 회수합니다.

잡은 `deployment.jsonl`을 보관합니다. 최종 활성화 여부와 실제 자동 실행 번호·배포 SHA는 [작업 기록](../../docs/plans/S15P31E102-65.md)을 따릅니다. root 상태 파일에서 선택된 JAR과 이전 릴리스를 조회할 수 있습니다. 아티팩트·기존 백업은 자동 삭제하지 않습니다.

```sh
# 비밀값 없는 배포 영수증
ssh edo1 'sudo cat /opt/eolssu/deployment.json'
# 로컬 회귀 검사
python3 -B -m unittest discover -s infra/jenkins/tests -v
```

운영에서 수동으로 이전 릴리스로 돌아가야 하면 polling을 잠시 끄고 Jenkins 실행이 없는지 먼저 확인합니다. 배포 lock을 잡은 상태에서 `deployment.json`의 `previous_jar`로 `.env`의 `BACKEND_JAR`만 바꾸고 아래 명령으로 백엔드만 재생성합니다. 실제 환경 파일 전체를 로그에 출력하지 않습니다. 자동 복구가 실패한 경우에도 같은 절차를 사용하며 프론트·DB 또는 볼륨을 내리지 않습니다.

```sh
sudo docker compose --env-file /opt/eolssu/.env \
  -f /opt/eolssu/config/compose.yml up -d --no-deps --force-recreate --pull never backend
curl -i http://127.0.0.1/api/infra-smoke
```

서버의 관리된 Pipeline·Dockerfile·배포 도구는 MR 병합만으로 자동 덮어쓰지 않습니다. 이 파일 자체를 바꿀 때는 서버 복사본을 별도 검토·설치하고 linter와 배포 검사 후 잡 정의도 갱신합니다. 앱 소스는 매 실행 develop에서 새로 checkout합니다.

```sh
sudo systemctl status jenkins.service
sudo journalctl -u jenkins.service --since today --no-pager
```

원시 Jenkins 설정·로그에는 인증 정보가 포함될 수 있으므로 공유 전 비밀값을 확인합니다. 잡 실행 결과·최종 배포 상태는 [작업 기록](../../docs/plans/S15P31E102-65.md)을 따릅니다.
