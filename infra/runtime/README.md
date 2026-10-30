# edo1의 eolssu 운영 구성

## Android App Links 준비 (S15P31E102-211)

정확한 경로 `/.well-known/assetlinks.json`은 Nginx가 백엔드로 프록시합니다. SPA나 리다이렉트가 아닙니다. `AUTH_ANDROID_APP_SHA256_FINGERPRINTS`에는 실제 설치할 Android Studio APK의 공개 SHA-256 인증서 지문만 쉼표로 구분해 설정합니다. 비어 있으면 서버는 기동하지만 검증 문서는404이며, 잘못된 지문은 기동 시 거절됩니다. 정상 설정은 com.ssafy.eolssu 대상의200 JSON 배열을 제공합니다. 이 값은 비밀키가 아니며, 키스토어·키 비밀번호는 필요하지 않습니다. 지문은 추측하지 않고 APK 제작자에게 받습니다. 환경 주입·Nginx/백엔드 배포·실기기 verified 확인은 별도이며, 준비 코드만으로 도메인 검증 완료로 보지 않습니다. [현재 계약·인계](../../docs/specs/social-auth.md)를 참고합니다.

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
curl -i http://127.0.0.1/api/actuator/health/readiness
curl -i http://127.0.0.1/api/terms
```

프론트 `/`는 200, readiness는 200과 `{"status":"UP"}`, 공개 업무 API `/api/terms`는 200 JSON이 정상입니다. Nginx HTML 404나 502와 구분합니다. 등록 안 된 `/api/...` 경로는 인증 때문에 401이라 확인 기준으로 쓰지 않습니다. 배포 변경 후에는 이 응답, Nginx 설정 검사, MySQL health·물리 볼륨을 함께 확인합니다.

API의 실제 준비 상태는 `/api/actuator/health/readiness`로 검사합니다. Nginx는 `/api/` 경로를 보존해 전달합니다. 단, 점검 주소(`/api/actuator`)는 readiness만 백엔드로 넘기고 나머지는 Nginx가 404로 막습니다. Prometheus 수치(`/api/actuator/prometheus`)는 서버 안에서 `backend:8080`으로만 읽습니다. 저장소의 Nginx 설정 3개(`nginx.conf`, `nginx.tls.conf.template`, `infra/nginx/default.conf`)를 바꿔도 서버의 `/opt/eolssu/config/` 파일은 따로 고쳐야 하므로, 이 규칙은 백엔드 배포보다 먼저 서버에 반영합니다. backend는 `ops` 프로필에서 기존 `.env`의 MySQL DB·사용자·비밀번호로 업무 API를 제공하고, 애플리케이션 준비 상태와 DB 연결을 함께 확인합니다. Flyway·SQL 초기화는 비활성화하며 스키마·물리 볼륨을 변경하지 않습니다. 스키마 변경은 마이그레이션 Task에서 따로 적용합니다. Docker healthcheck도 같은 경로의 HTTP 성공과 정확한 `{"status":"UP"}`를 확인합니다. 주기는 30초, 요청 제한 3초, healthcheck 제한 5초, 초기 유예 30초, 실패 허용 3회입니다. 운영 JRE 이미지 안의 curl을 확인해 사용합니다.

### backend 실행 이미지

backend는 `eclipse-temurin:21-jre`에 녹음 검사용 `ffmpeg=7:8.0.1-3ubuntu2`만 더한 `eolssu-backend-runtime:21-jre-ffmpeg8.0.1`로 실행하고, Jenkins가 만든 JAR을 `/app/app.jar`에 읽기 전용으로 붙입니다. Jenkins가 서버 `backend.Dockerfile`로 만드는 `eolssu-backend:ci-<SHA>`는 후보 검증과 JAR 추출에만 쓰이고 운영 컨테이너 이미지가 되지 않으므로, 운영 실행에 필요한 도구는 이 실행 이미지에 넣어야 합니다. 배포는 `--pull never`로 재생성하므로 이 이미지는 edo1에서 직접 빌드해 둡니다.

```sh
D=$(mktemp -d) && cp /opt/eolssu/config/backend-runtime.Dockerfile "$D/Dockerfile"
sudo docker build --tag eolssu-backend-runtime:21-jre-ffmpeg8.0.1 "$D" && rm -rf "$D"
sudo docker exec eolssu-runtime-backend-1 ffprobe -version
```

원본은 저장소 `infra/runtime/backend-runtime.Dockerfile`이며 서버 `/opt/eolssu/config/backend-runtime.Dockerfile`과 같게 유지합니다. 베이스 JRE 보안 업데이트를 반영하려면 같은 태그로 다시 빌드한 뒤 backend만 재생성하고 readiness·ffprobe를 확인합니다. 베이스의 Ubuntu가 바뀌어 고정 ffmpeg 버전 설치가 실패하면 `backend/Dockerfile`과 함께 버전을 갱신합니다.

### ai 분석 서비스

연주 분석 API([ai README](../../ai/README.md#분석-api))는 `ai` 서비스로 실행합니다. 인증이 없으므로 호스트 포트를 열지 않고, backend가 Compose 내부망의 `http://ai:8000`으로만 부릅니다(`ANALYSIS_AI_BASE_URL`). 주소는 비밀값이 아니라 Compose에 직접 적습니다. AI가 없거나 응답하지 않으면 업로드한 세션은 `SERVER_ERROR` 또는 제한 시간(60초) 뒤 `TIMEOUT`이 되고, 다른 API에는 영향이 없습니다.

이미지는 저장소 `ai/Dockerfile`로 edo1에서 직접 빌드한 `eolssu-ai:runtime`을 씁니다(약 1.65GB, 빌드에 2GB 넘는 디스크 여유 필요). 배포는 `--pull never`이고 Jenkins는 이 서비스를 다시 만들지 않으므로, `ai/` 변경을 반영할 때는 같은 태그로 다시 빌드한 뒤 `ai`만 재생성합니다. 빌드한 develop 커밋을 아래 작업 기록에 남깁니다.

```sh
# 로컬 저장소에서: 반영할 커밋의 ai/만 서버 임시 폴더로 보내 빌드한다.
D=$(ssh edo1 mktemp -d)
git archive origin/develop ai | ssh edo1 "tar -x -C $D"
ssh edo1 "sudo docker build --tag eolssu-ai:runtime $D/ai && rm -rf $D"
# edo1에서: ai만 (재)생성하고 backend에서 연결을 확인한다.
sudo docker compose --env-file /opt/eolssu/.env \
  -f /opt/eolssu/config/compose.yml up -d --no-deps --pull never ai
sudo docker compose --env-file /opt/eolssu/.env \
  -f /opt/eolssu/config/compose.yml ps ai
sudo docker exec eolssu-runtime-backend-1 \
  curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' -d '{}' http://ai:8000/v1/analyses
```

마지막 명령은 422가 정상입니다(빈 요청을 계약 위반으로 거절). 연결 거부·이름 풀이 실패는 `ai`가 없거나 내부망이 다른 것입니다.

- 컨테이너 파일 시스템은 읽기 전용이고 `/tmp`만 512MiB tmpfs로 씁니다(녹음 임시 파일, numba 캐시).
- 메모리 한도는 6GiB입니다. AI는 채점 DTW 예상 메모리가 4GiB(120초 × 120초)를 넘는 요청을 먼저 거절하고, 120초 녹음 채점에 약 3GiB가 듭니다([측정 기록](../../docs/knowledge/ai/analysis-latency.md)). backend는 분석을 기본 2개까지 동시에 보내므로, 긴 녹음 두 개가 겹치면 한도를 넘어 `ai`가 재시작되고 두 세션이 `SERVER_ERROR`가 될 수 있습니다.
- 요청 본문의 녹음 서명 URL은 uvicorn 접근 로그(경로만 남김)와 분석 로그(`analysis session=… attempt=… result=… ms=…`)에 남지 않습니다. 배포 뒤 `sudo docker logs eolssu-runtime-ai-1`에서 `X-Amz`·`audioUrl`이 없는지 확인합니다.

backend가 분석을 시작하지 못하면 업로드마다 경고 로그가 남습니다. 배포 뒤 `sudo docker logs eolssu-runtime-backend-1 2>&1 | grep -i wiring`이 비어 있어야 합니다.

### backend 비밀값

인증 비밀값은 `/opt/eolssu/.env`에만 두고 Compose의 backend `environment`에 필요한 변수만 연결합니다. 값은 저장소·채팅·로그·빌드 인자에 넣지 않습니다.

| 변수 | 용도 | 값이 없을 때 |
| --- | --- | --- |
| `AUTH_JWT_SECRET` | 로그인 토큰 서명 키. 32바이트 이상 무작위 값 | backend는 기동하고 로그인 토큰만 모두 거절 |
| `AUTH_CODE_HMAC_SECRET` | 이메일 인증번호 해시 키. 32바이트 이상 무작위 값 | 인증번호 발송·확인 거절. 이메일 가입·비밀번호 재설정도 불가 |
| `AUTH_MAIL_HOST`·`AUTH_MAIL_PORT` | 인증 메일 SMTP 서버. 운영은 `smtp.gmail.com`·587(STARTTLS는 백엔드 설정에 고정) | 호스트가 없으면 메일 발송만 거절. 포트는 Compose 기본 587 |
| `AUTH_MAIL_USERNAME`·`AUTH_MAIL_PASSWORD` | 발송 계정 `eolssu.mailservice@gmail.com`과 Gmail 앱 비밀번호(계정 비밀번호 아님) | 메일 발송만 거절 |
| `AUTH_MAIL_FROM`·`AUTH_MAIL_FROM_NAME` | 발신 주소·이름(`얼쑤`) | 주소는 Compose가 `AUTH_MAIL_USERNAME`으로 채움. 이름이 없으면 주소만 표시 |
| `AUTH_OAUTH_{KAKAO,GOOGLE,NAVER}_CLIENT_ID`·`_CLIENT_SECRET` | 간편 로그인 제공자별 앱 키. 백엔드가 개발자 콘솔에서 발급해 별도 경로로 전달 | 그 제공자의 로그인만 거절 |
| `RECORDING_STORAGE_ENDPOINT`·`_BUCKET`·`_ACCESS_KEY_ID`·`_SECRET_ACCESS_KEY`·`_REGION` | 녹음 원본 저장소(Cloudflare R2) S3 API 주소·버킷과 버킷 전용 R2 API 토큰의 S3 키. 지역은 `auto`. 준비 방법은 [backend README](../../backend/README.md#녹음-업로드와-원본-저장소pra-02) | 녹음 업로드만 503으로 거절 |

Compose는 값이 없는 변수를 빈 문자열로 넘기므로 백엔드는 빈 값을 미설정과 같게 처리합니다. `AUTH_COOKIE_SECURE`는 연결하지 않습니다. 백엔드 기본값이 `true`이고 운영은 HTTPS라 넣을 필요가 없습니다. 값을 넣거나 바꿀 때는 `.env`를 root 전용으로 백업한 뒤 `sudoedit`로 고치고, 아래 절차대로 backend만 재생성합니다. 서명 키를 바꾸면 기존 로그인 토큰은 모두 무효가 되고, 인증번호 해시 키를 바꾸면 발송 중인 인증번호가 모두 무효가 됩니다. Gmail 앱 비밀번호는 Google 계정 보안 설정에서 발급·폐기하며, 교체하면 이전 값은 바로 쓸 수 없습니다.

```sh
openssl rand -base64 48
```

운영 설정을 바꿀 때는 기존 Compose를 root 전용으로 백업하고 `config --quiet`를 통과한 뒤 backend만 재생성합니다. Jenkins는 관리 파일 `/opt/eolssu/config/compose.yml`을 사용하므로 저장소 변경만으로 서버 파일이 바뀌지는 않습니다. 배포 후 readiness HTTP 200·UP, Docker healthy, 프론트·MySQL ID와 물리 볼륨 보존을 확인합니다.

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
curl -i https://k15e102.p.ssafy.io/api/actuator/health/readiness
sudo certbot renew --dry-run
sudo systemctl status certbot.timer --no-pager
python3 -B -m unittest discover -s infra/scripts/tests -v
RUN_NGINX_TESTS=1 python3 -B -m unittest discover -s infra/scripts/tests -v
```

HTTP 검사는 `308`과 원래 경로·쿼리가 포함된 HTTPS `Location`을 확인합니다. 기본 Python 검사는 외부 도구 없이 TLS 초기화의 복구 계약을 확인합니다. `RUN_NGINX_TESTS=1` 검사는 Docker·OpenSSL이 필요하며, 임시 인증서와 loopback 임의 포트의 격리 Nginx로 실제 HTTP 전환·ACME·내부 검사·HTTPS를 확인한 뒤 컨테이너와 fixture를 정리합니다. 같은 조건에서 `test_nginx_actuator.py`는 가짜 백엔드를 붙인 Nginx 설정 3개가 readiness와 일반 API만 넘기고 다른 점검 주소와 우회 경로(이중 슬래시·URL 인코딩·`../`·`;`)는 404로 막는지, `/api/` 본문을 25MiB까지 넘기고(녹음 업로드의 20MiB 판정은 백엔드) 그보다 크면 413으로 막는지 확인합니다.

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

컨테이너 구성·경로·호스트 에이전트·백업·복구 절차는 [Jenkins 컨트롤러 운영 문서](../jenkins/controller/README.md)를 따릅니다. 실제 전환·검증 결과는 [Task 76 작업 기록](../../docs/plans/S15P31E102-76.md)에 남깁니다.

Jenkins LTS `2.580.1` 컨트롤러는 `/opt/eolssu/jenkins/compose.yml`의 `eolssu-jenkins-controller-1` 컨테이너로 실행합니다. 기존 홈 `/var/lib/jenkins`와 UID/GID 111:113을 유지하며 `127.0.0.1:8080`에서만 접속할 수 있습니다. 관리자 인증·CSRF·가입 차단·인바운드 TCP 에이전트 포트 차단은 보존했습니다. 기존 apt의 `jenkins.service`는 중단·비활성화했고 패키지는 복구를 위해 남겼습니다.

빌드·배포는 같은 서버의 `eolssu-jenkins-agent.service`가 기존 `jenkins` 계정·호스트 도구·Docker 그룹·sudoers로 실행합니다. `edo1-host` 노드의 executor는 1개이고 컨트롤러는 0개입니다. 현재 workspace는 `/var/lib/jenkins-agent/workspace`이며 과거 workspace와 빌드 이력은 보존합니다. 에이전트는 loopback HTTP 주소로 WebSocket 연결합니다.

관리자 이름은 `eolssu-admin`입니다. 초기 비밀번호는 서버의 `/var/lib/jenkins/secrets/eolssu-admin-password`에 0600으로 보관하며 채팅·저장소에 기록하지 않습니다. 사용자 본인의 SSH 터미널에서 다음과 같이 조회할 수 있습니다.

```sh
ssh edo1 'sudo cat /var/lib/jenkins/secrets/eolssu-admin-password'
ssh -N -L 18080:127.0.0.1:8080 edo1
```

터널을 유지한 상태에서 브라우저로 `http://127.0.0.1:18080/`에 접속합니다. 로컬 18080이 사용 중이면 다른 로컬 포트를 선택합니다. Jenkins의 인증 정보를 실제로 변경했다면 위 초기 비밀번호 파일과 서버 전용 CLI 인증 파일도 함께 정리해야 합니다.

백엔드 잡은 `eolssu-backend`, GitLab 연결은 Credentials `eolssu-gitlab`을 사용합니다. 기존 서버 Git 연결 정보를 Jenkins의 암호화된 Credentials 저장소에 옮기고 잡에는 비밀값 없는 저장소 URL·`develop`만 지정합니다. 구성 원본은 [infra/jenkins/backend.Jenkinsfile](../jenkins/backend.Jenkinsfile)이며 서버 복사본은 `/opt/eolssu/config/backend.Jenkinsfile`입니다.

현재 MR의 인프라 파일이 아직 develop에 없으므로 잡은 서버의 관리된 Pipeline 정의에서 develop 소스를 명시적으로 checkout합니다. Dockerfile과 Dockerfile 전용 ignore 규칙도 `/opt/eolssu/config`의 관리 파일을 사용합니다. 앱 소스는 수정하지 않습니다. CI 구성 검사는 실제 DB 비밀번호 대신 `ci.env.example`의 고정 데이터만 사용하며 서비스를 기동하지 않습니다.

잡은 `develop`을 1분마다 SCM polling으로 확인합니다. 변경이 있을 때만 실행하며 직접 Push로 develop이 바뀌는 경우도 감지합니다. 별도 GitLab webhook이나 Jenkins 공개 포트는 필요하지 않습니다. 정확한 ref `refs/heads/develop`만 checkout하고, 동시 실행을 막으며 20분 제한을 둡니다. 경로 필터는 없습니다.

실행 순서는 Gradle `clean build`·`mediaTest`(edo1 호스트 ffmpeg로 실제 녹음 파일 검사)·JUnit 수집 → Compose/Nginx 검사 → 커밋 revision label을 포함한 이미지 빌드 → JAR 보관 → 후보 실행·백엔드 배포입니다. 이미지에서 추출한 JAR을 `/opt/eolssu/releases/<40자리 SHA>/app.jar`에 보관하고, 실제 `.env`의 `BACKEND_JAR`만 원자적으로 교체합니다. 기존 JRE+읽기 전용 bind mount 구성을 유지합니다. 프론트와 MySQL은 재생성하지 않고 컨테이너 ID·물리 볼륨이 같은지 검사합니다.

후보 이미지는 포트를 공개하지 않는 임시 internal network에서 실행합니다. 운영 DB 비밀값은 전달하지 않습니다. 호스트가 후보 IP의 `/api/actuator/health/liveness`에서 200과 `{"status":"UP"}`를 확인해야 배포합니다(후보는 DB 없이 뜨므로 readiness가 아니라 liveness). 운영 교체 뒤에는 Nginx를 거친 `/api/actuator/health/readiness`가 `UP`이어야 합니다. 예전 기준이던 `/api/infra-smoke`의 404는 인증(`/api/**` 로그인 필요)이 생긴 뒤 401이 되어 배포가 모두 실패했습니다(S15P31E102-229).

배포 도구 원본은 [deploy-backend.py](../jenkins/deploy-backend.py), 서버 설치 위치는 `/opt/eolssu/bin/deploy-backend`입니다. root 소유 0755로 설치하고 Jenkins에는 아래 도구의 sudo 실행만 허용합니다. 운영 `.env`는 root 전용 0600을 유지합니다. Jenkins의 기존 Docker 접근도 서버 수준 권한이므로 잡 설정·실행은 승인된 관리자만 사용합니다.

```sudoers
jenkins ALL=(root) NOPASSWD: /opt/eolssu/bin/deploy-backend *
```

도구는 인자 하나의 40자리 SHA·이미지 revision·원격 develop 이력 소속을 확인합니다. Python isolated mode와 고정 PATH를 사용하고 임의 파일·셸 명령 인자를 받지 않습니다. 빌드 중 develop이 전진해도 선택한 SHA를 유지합니다. 공유 lock 안에서 중단된 배포를 먼저 복구한 후 현재 활성 JAR 릴리스 SHA와 Git 조상 관계를 비교하고, 이미 배포된 후속 버전을 이전 후보가 덮어쓰는 경우를 차단합니다. 같은 SHA는 기존 불변 파일·건강 검사를 유지합니다. 실패 journal의 후보 SHA는 배포 기준으로 쓰지 않습니다. Git 이력을 확인할 수 없거나 이력이 분기되면 순서를 증명할 수 없어 실패합니다. SHA 도입 전 초기 릴리스에는 비교 기준이 없으므로 첫 관리 배포를 허용합니다.

## 프론트 웹 자동 배포

`eolssu-backend`가 develop 변경을 1분마다 감지하고 백엔드 배포까지 성공하면, 같은 전체 SHA를 `REVISION`으로 전달해 `eolssu-frontend`를 시작합니다. 상위 잡은 프론트 실행을 예약한 뒤 executor를 반환합니다. 프론트 완료는 `eolssu-frontend`의 SUCCESS와 `/opt/eolssu/frontend-deployment.json`을 기준으로 확인합니다. 프론트 잡에는 별도 polling을 중복 등록하지 않습니다.

프론트 잡은 다음 명령을 Jenkins 사용자로 실행합니다. JDK 21·Docker·Google Chrome이 필요하며 Node·Yarn·Wasm tooling은 Gradle이 관리합니다. Linux 서버에서 웹 타깃만 빌드·테스트합니다. Android·iOS 패키지 배포는 포함하지 않습니다.

```sh
cd frontend
CHROME_BIN=/usr/bin/google-chrome ./gradlew --no-daemon \
  :shared:wasmJsTest :shared:jsTest :webApp:wasmJsBrowserDistribution
```

JUnit 결과와 `webApp/build/dist/wasmJs/productionExecutable/`를 보관하고 [frontend.Dockerfile](../jenkins/frontend.Dockerfile)로 커밋 revision label을 붙인 `eolssu-frontend:ci-<SHA>`를 만듭니다. 관리된 Pipeline 원본은 [frontend.Jenkinsfile](../jenkins/frontend.Jenkinsfile)입니다. 초기 샘플 테스트는 JS·Wasm 합계 4개이며 업무 기능 검증 범위는 앱 테스트가 추가되는 대로 넓혀야 합니다.

프론트 `Clean workspace`는 기존 Git 작업공간에서 추적 파일을 복원하고, Git이 무시하지 않는 미추적 파일만 정리합니다. `.gradle`, `.kotlin`, `build`, `node_modules`의 캐시·산출물은 유지하며 Gradle의 입력/출력 검증으로 재사용 여부를 결정합니다. 소스는 매번 전달받은 전체 `REVISION`으로 checkout합니다. 처음 실행하거나 Git 작업공간이 없으면 전체 정리 후 checkout합니다. clone 중단으로 `.git`만 남고 유효한 HEAD 커밋이 없을 때도 helper의 종료 코드 2를 받아 재초기화합니다. 그 밖의 정리 오류는 작업공간을 삭제하지 않고 빌드를 실패시킵니다. 단계 이름, 테스트 명령, `--no-daemon`, production 최적화와 20분 제한은 유지합니다.

[prepare-frontend-workspace.sh](../jenkins/prepare-frontend-workspace.sh)를 root 소유 0755의 `/opt/eolssu/config/prepare-frontend-workspace.sh`에 **먼저** 설치한 뒤 관리된 `frontend.Jenkinsfile`을 갱신합니다. 스크립트는 저장소 루트에서만 실행됩니다. 캐시 손상이 의심될 때는 실행 중인 잡이 없는지 확인하고 해당 프론트 작업공간의 캐시만 수동 초기화합니다. Jenkins 사용자 홈 전체의 Gradle 캐시는 삭제하지 않습니다.

[deploy-frontend.py](../jenkins/deploy-frontend.py)를 root 소유 `/opt/eolssu/bin/deploy-frontend`에 설치하고 Jenkins에는 이 도구의 실행만 추가로 허용합니다. SHA 검증·원자적 파일 교체·HTTP 검사·공유 lock은 인접한 root 소유 `deploy-backend`의 검증된 공통 함수를 사용하므로 두 도구를 함께 관리합니다. Jenkins에는 운영 `.env` 읽기 권한을 주지 않습니다.

```sudoers
jenkins ALL=(root) NOPASSWD: /opt/eolssu/bin/deploy-frontend *
```

도구는 SHA·이미지 label·원격 develop 이력 소속과 현재 활성 웹 릴리스 대비 배포 순서를 확인하고, 비공개 internal network의 후보 Nginx에서 **모든 정적 파일의 SHA-256과 Wasm MIME**을 검사합니다. HTML fallback이 JS 누락을 가리는 경우도 실패합니다. symlink·특수 파일·필수 번들 누락·256MiB/5,000개 초과 산출물은 거부합니다. 통과한 이미지에서 추출한 파일만 `/opt/eolssu/releases/<SHA>/web`에 보관하며 다른 내용으로 덮어쓰지 않습니다.

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
curl -i http://127.0.0.1/api/actuator/health/readiness
curl -i http://127.0.0.1/api/terms
```

서버의 관리된 Pipeline·Dockerfile·배포 도구는 MR 병합만으로 자동 덮어쓰지 않습니다. 이 파일 자체를 바꿀 때는 서버 복사본을 별도 검토·설치하고 linter와 배포 검사 후 잡 정의도 갱신합니다. 앱 소스는 매 실행 develop에서 새로 checkout합니다.

```sh
sudo docker compose -f /opt/eolssu/jenkins/compose.yml ps
sudo docker compose -f /opt/eolssu/jenkins/compose.yml logs --tail 50 controller
sudo systemctl status eolssu-jenkins-agent.service
sudo journalctl -u eolssu-jenkins-agent.service --since today --no-pager
```

원시 Jenkins 설정·로그에는 인증 정보가 포함될 수 있으므로 공유 전 비밀값을 확인합니다. 잡 실행 결과·최종 배포 상태는 [작업 기록](../../docs/plans/S15P31E102-65.md)을 따릅니다.


### 단계별 실패 표시 (S15P31E102-262)

운영 backend는 15개, frontend는 20개 상위 stage로 나눠 컴파일·테스트·최적화·번들·구성 검사·이미지·보관·후속 잡 예약을 구분합니다. frontend의 JS/Wasm JUnit은 각 browser test 단계에서 따로 보관합니다. Gradle 명령 분리에 따라 JVM 시작 비용이 추가되며 기존 20분 제한은 유지합니다. 전체 앱 빌드는 다음 정상 CI 실행의 소요 시간과 결과로 확인합니다.

배포 stage는 [deployment-stages.groovy](../jenkins/deployment-stages.groovy)를 `/opt/eolssu/config/deployment-stages.groovy`에서 읽습니다. 이 파일도 root 소유 0644로 관리합니다. 실제 배포 프로세스가 출력하는 phase 이벤트를 동시에 관찰해 잠금·백업·순서 검사·후보 기동·산출물·서비스 재생성·상태 검사·롤백·정리를 개별 하위 stage로 표시합니다. 별도 배포 명령으로 쪼개지 않아 공유 lock·복구 journal의 일관성을 유지합니다. 실패한 첫 phase는 빌드 설명에 남으며, 실패·강제 종료로 완료 이벤트가 없거나 phase가 전혀 없어도 후속 잡 예약을 막습니다. stdout/stderr는 실패하더라도 `deployment.jsonl`/`frontend-deployment.jsonl`로 보관합니다.

Pipeline Graph View에서 빌드를 열어 `Stages`의 `Deploy ... transaction`과 phases 분기를 펼쳐 세부 실패 stage를 확인합니다. stage 실행 시간은 관찰 시점 기준이고 정확한 프로세스 프로파일링 수치는 아닙니다. 기존 실행 이력에는 과거 stage 구조가 남으며 새 실행부터 적용됩니다.

블로그 잡은 별도 blog 브랜치의 `Jenkinsfile`·`scripts/deploy.sh`를 사용합니다. 작업 S15P31E102-262의 blog 브랜치 변경은 정리·checkout·Hugo 준비/빌드·산출물 검사·Pages 원격 준비·파일 복사·커밋·Push의 9단계로 나눕니다. blog 변경은 blog 대상 MR로 전달하고 기존 SCM 정의와 polling을 보존합니다. 원격 반영/병합 전에는 운영 blog 잡에 새 stage가 적용되지 않습니다.

검증은 `python3 -B -m unittest discover -s infra/jenkins/tests -v`, `python3 -B -m unittest discover -s infra/backup/tests -p test_deploy_hooks.py -v`로 수행합니다. 별도 임시 Jenkins 잡에서 성공·phase 실패/롤백·프로세스 중단·로그 없는 실패를 주입해 실제 stage 결과와 후속 단계 차단을 검증했습니다. 앱 배포나 공개 Pages 게시를 이 검증에 사용하지 않습니다.

## 테스트 회원의 인증 만료 예외

`AUTH_NON_EXPIRING_USER_IDS`는 기본 빈 목록이며, 검토한 테스트 회원 ID에만 설정합니다. 이 설정을 전달하는 관리된 Compose와 V7이 적용된 백엔드 릴리스가 필요합니다. 운영 `ops`는 Flyway를 실행하지 않으므로 마이그레이션은 먼저 `db` 프로필로 별도 수행합니다. 실제 계정 생성·배포 순서와 검증은 [S15P31E102-260](../../docs/plans/S15P31E102-260.md)을 따릅니다. 설정을 비우고 백엔드에 다시 주입하면 예외 토큰을 거부합니다. 기존 비밀값을 출력하거나 전체 `.env`를 덮어쓰지 않습니다.

구버전 JAR로 롤백할 때는 허용 목록을 끄는 것만으로 충분하지 않습니다. NULL 만료 세션을 먼저 만료·폐기해 구버전의 non-null 매핑과 호환시킨 뒤 JAR을 바꿉니다. 검증한 SQL과 순서는 [Task 260의 복구 절차](../../docs/plans/S15P31E102-260.md#예외-중단과-바이너리-롤백)를 따릅니다.

## R2 버킷명·보관 규칙 점검

`RECORDING_STORAGE_BUCKET`에는 R2 버킷 이름을 지정합니다. API 토큰의 표시 이름과 구분합니다. 현재 버킷은 `eolssu-recordings`이며 토큰 이름 `eolssu-backend-recordings`를 넣으면 객체 PUT·DELETE가 403 AccessDenied가 됩니다. readiness는 R2 객체 접근을 검사하지 않으므로 변수 존재·기동 성공만으로 저장소 연결을 판정하지 않습니다.

Cloudflare 버킷 Settings에서 사용자 도메인 없음·Public Development URL disabled, `recordings-expire-after-30-days`의 접두사 `recordings/`·객체 생성 후 30일 삭제·Enabled를 확인합니다. 기본 미완료 multipart 7일 중단 규칙은 별도로 보존합니다. 새 검증 객체의 `x-amz-expiration`에서 30일 만료 시각과 규칙명을 확인하며, 실제 자동 삭제 실행과 구분합니다. 저장소 접근 키·서명 URL은 출력하지 않습니다.

2026-10-29의 버킷명 정정·backend 재생성·서명 읽기·만료·삭제 검증과 복구 위치는 [Task 272](../../docs/plans/S15P31E102-272.md)·[R2 검증 기록](../../docs/knowledge/infra/r2-storage-verification.md)을 따릅니다. 일반 재현은 테스트 전용 버킷을 쓰며 운영 객체 검사는 범위·정리 대상을 먼저 승인받습니다.
