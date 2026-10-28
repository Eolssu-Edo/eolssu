# 인프라 실행과 검증

edo1의 DB·설정을 edo2에 암호화 보관하는 자동 백업과 격리 실제 복원은 [백업 운영 안내](backup/README.md)를 따릅니다.

edo1의 실제 프론트·백엔드·MySQL 운영 구성과 웹·백엔드 자동 배포, `eolssu` 이름 전환·Jenkins 접속은 [운영 서버 문서](runtime/README.md)를 따릅니다. 아래 내용은 앞서 만든 백엔드·프록시 두 서비스 실행 예시입니다.

## edo2 점검 중계 서버 준비

2026-10-23 `ssh edo2` 접속과 UFW 활성화를 확인했습니다. IPv4·IPv6 모두 인바운드는 기본 차단하고 TCP 22(SSH), 80(ACME 검증), 443(HTTPS)만 허용합니다. 아웃바운드는 허용합니다. 적용 전 설정은 서버의 root 전용 `/var/backups/edo2-ufw/`에 보관했습니다.

상태를 읽을 때는 다음 명령을 사용합니다. 방화벽 변경 뒤에는 기존 연결을 재사용하지 않는 새 SSH 접속으로 관리 경로를 검증합니다.

```sh
ssh edo2 'sudo -n ufw status verbose'
ssh -o BatchMode=yes -o ConnectTimeout=10 -o ControlMaster=no -o ControlPath=none edo2 'sudo -n ufw status numbered'
```

Mattermost 명령 3개와 HTTPS 인증서·중계 서비스·edo1 전용 SSH 수집기를 배포했습니다. Lightsail에도 TCP 443을 IPv4·IPv6로 허용했고, 외부 HTTPS 접속과 CI/CD 채널의 세 공개 보고를 검증했습니다. 실행·권한·지표·갱신·복구는 [점검 중계 안내](edo-check/README.md), 합의한 출력·등록 결과·실제 검증·후속 범위는 [S15P31E102-95 작업 기록](../docs/plans/S15P31E102-95.md)을 따릅니다. 정적 IP `15.165.144.7`을 연결했고 인증서·Mattermost URL·SSH 출발지 제한과 관리 SSH 별칭을 함께 전환했습니다.

## 백엔드·프록시 실행 예시

`/opt/app/infra`에서 실행하는 `./scripts/init-letsencrypt.sh`의 준비·실행·인증서 갱신·복구 방법도 [운영 서버 문서](runtime/README.md#lets-encrypt와-https)를 따릅니다.

운영 MySQL에 읽기 계정으로 붙는 Grafana·Metabase의 계정 준비·실행·SSH 터널 접속은 [모니터링 문서](monitoring/README.md)를 따릅니다.

현재 구현된 Spring Boot 백엔드를 Nginx 뒤에서 실행하는 구성입니다. 개발·운영은 각각 `eolsu-dev`, `eolsu-prod` Compose 프로젝트이며 DB·AI 서비스는 포함하지 않습니다. Docker Engine과 Compose v2 이상이 필요합니다. 아래 명령은 저장소 루트에서 실행합니다.

| 파일 | 역할 |
| --- | --- |
| `docker-compose.dev.yml` / `docker-compose.prod.yml` | 개발·운영의 백엔드와 Nginx 구성 |
| `.env.dev.example` / `.env.prod.example` | Compose 치환용 환경변수 예시 |
| `backend/Dockerfile` / `backend/.dockerignore` | JDK 21 빌드, JRE 21 실행(녹음 검사용 ffmpeg 버전 고정 설치), 소스·Wrapper만 포함하는 빌드 컨텍스트. 서버의 `/opt/eolssu/config/backend.Dockerfile`과 맞춘다 |
| `infra/nginx/default.conf` | URI를 유지해 `backend:8080`으로 전달하는 HTTP 프록시 |
| `backend/Jenkinsfile` | 백엔드 검증·이미지 빌드·JAR 보관 |

## 환경 설정

로컬에서 처음 준비할 때 예시를 복사합니다. 기존 파일이 있으면 덮어쓰지 말고 필요한 변수만 반영합니다.

```sh
test -e .env.dev || (umask 077; cp .env.dev.example .env.dev)
test -e .env.prod || (umask 077; cp .env.prod.example .env.prod)
```

| 변수 | 의미 | 개발 / 운영 예시 |
| --- | --- | --- |
| `BACKEND_IMAGE` | 로컬 빌드 이미지 이름·태그 | `eolsu-backend:dev` / `eolsu-backend:prod` |
| `HTTP_BIND_ADDRESS` | Nginx의 호스트 바인딩 주소 | `127.0.0.1` / `127.0.0.1` |
| `HTTP_PORT` | Nginx의 호스트 포트. 누락·빈 값은 구성 검사 실패 | `8080` / `8081` |

포트는 실행 예시이며 확정된 서버 공개 포트가 아닙니다. 두 환경을 동시에 실행하면 서로 다른 포트와 이미지 태그를 사용합니다. 백엔드의 컨테이너 내부 포트는 8080이며 호스트에 직접 공개하지 않습니다.

`.env.dev`·`.env.prod`는 Git 제외 대상입니다. `--env-file`은 Compose 변수 치환에 사용하며, 파일 전체가 앱에 자동 주입되지는 않습니다. 현재 앱에는 Compose의 `environment`로 `SERVER_PORT=8080`만 전달합니다. DB·인증 등 실제 앱 설정이 추가되면 필요한 변수만 명시적으로 연결합니다. 비밀값은 예시·Dockerfile·빌드 인자·로그에 넣지 않습니다.

## 구성 검사와 실행

비밀값 없는 예시만으로 구성 검사를 할 수 있습니다. 이 명령은 컨테이너를 시작하지 않습니다.

```sh
docker compose --env-file .env.dev.example -f docker-compose.dev.yml config --quiet
docker compose --env-file .env.prod.example -f docker-compose.prod.yml config --quiet
docker build --tag eolsu-backend:check backend
docker run --rm --network none \
  --mount "type=bind,src=$(pwd)/infra/nginx/default.conf,dst=/etc/nginx/conf.d/default.conf,readonly" \
  nginx:1.30.6-alpine nginx -t
```

개발 환경:

```sh
docker compose --env-file .env.dev -f docker-compose.dev.yml up -d --build
docker compose --env-file .env.dev -f docker-compose.dev.yml ps
docker compose --env-file .env.dev -f docker-compose.dev.yml logs --tail=100 backend nginx
curl -i http://127.0.0.1:8080/
```

운영 구성은 배포 담당자가 서버 상태와 설정을 확인한 뒤 별도로 실행합니다.

```sh
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
docker compose --env-file .env.prod -f docker-compose.prod.yml logs --tail=100 backend nginx
curl -i http://127.0.0.1:8081/
```

주소·포트를 바꿨다면 `curl`도 그 값에 맞춥니다. 현재 백엔드에는 업무 API가 없어 프록시를 통과한 `/` 요청은 Spring Boot의 JSON `404`가 정상입니다. 연결 거부나 Nginx의 `502`는 성공이 아닙니다. `depends_on`은 시작 순서만 정하므로 앱 기동 중에는 일시적으로 `502`가 날 수 있습니다. Nginx는 Docker DNS로 백엔드 주소를 재조회하여 컨테이너 재생성 후의 주소 변경을 반영합니다.

중지·재기동은 해당 환경의 명령만 사용합니다. 영속 볼륨은 구성하지 않았으며 `down -v`는 사용하지 않습니다.

```sh
docker compose --env-file .env.dev -f docker-compose.dev.yml down
docker compose --env-file .env.prod -f docker-compose.prod.yml down
```

롤백할 때는 앞서 검증한 Git 커밋의 구성·Dockerfile로 복귀해 같은 환경의 `up -d --build`를 실행합니다. 실제 환경변수 파일은 유지합니다. 아직 배포 이력이 없으므로 최초 배포 전에는 담당자가 복귀할 커밋·이미지를 정해야 합니다.

## 기존 서버의 빈 파일 처리

2026-10-22 확인한 `/opt/app`에는 같은 이름의 미추적 Compose 두 개와 환경변수 파일 두 개가 모두 비어 있었습니다. 이 상태에서 같은 이름의 추적 파일을 pull하면 충돌할 수 있습니다. 서버에 반영하는 담당자는 먼저 `git status --short`와 파일 크기를 다시 확인하고, Compose는 저장소 밖으로 옮겨 보존한 뒤 병합된 `develop`을 fast-forward로 받습니다.

```sh
cd /opt/app
backup_dir="$(mktemp -d "$HOME/eolsu-infra-backup.XXXXXX")"
cp -p .env.dev .env.prod "$backup_dir/"
mv docker-compose.dev.yml docker-compose.prod.yml "$backup_dir/"
git pull --ff-only origin develop
```

이 명령은 해당 파일이 여전히 미추적일 때만 사용합니다. 비어 있는 환경변수 파일은 백업을 확인한 뒤 예시의 변수를 채웁니다. 이번 작업에서는 서버 파일 이동·pull·앱 실행을 수행하지 않습니다.

## Jenkins 연결

Jenkins 잡의 SCM 브랜치를 `develop`, Script Path를 `backend/Jenkinsfile`로 지정합니다. Linux 에이전트에는 JDK 21, Git, Docker CLI와 Compose, 동일 호스트 Docker daemon 접근 권한이 필요합니다. Nginx 검사에서 workspace를 bind mount하므로 원격 daemon이나 workspace 경로가 다른 컨테이너형 에이전트에는 그대로 적용하지 않습니다. Pipeline·Pipeline: Declarative·JUnit 플러그인을 사용합니다. Git 인증은 Jenkins Credentials에서 관리합니다.

파이프라인은 경로 필터 없이 checkout → `clean build`·JUnit 결과 수집 → 개발/운영 Compose 검사 → 커밋별 이미지 `eolsu-backend:ci-<Git SHA>` 빌드 → Nginx 검사 → 실행 JAR 보관을 수행합니다. Dockerfile은 `bootJar`만 빌드하므로 테스트는 앞선 Gradle 단계에서 필수로 실행합니다. 이미지가 Jenkins 에이전트에 남으므로 보관 기간과 해당 이미지의 정리 정책은 잡 운영자가 관리합니다.

실제 Jenkins 설치·잡 등록·webhook·이미지 레지스트리·배포 인증은 아직 확인되지 않았습니다. Jenkinsfile은 트리거·레지스트리 Push·서버 배포를 정의하지 않습니다. 팀의 실제 트리거 설정이 정해지면 연결합니다. 기술 블로그는 develop이 아닌 `blog` 브랜치의 `Jenkinsfile`을 별도 잡 `eolssu-blog`가 사용합니다.

## 검증 기준과 기록

변경 시 `backend/README.md`의 필수 `clean build`, 두 Compose의 `config --quiet`, Docker 이미지 빌드, `nginx -t`, 격리된 개발·운영 Compose에서 프록시 경유 HTTP를 확인합니다. 테스트용 Compose는 `-p`로 별도 이름을 주고 사용한 컨테이너·네트워크만 정리합니다. 이 기준은 운영 서버의 배포 성공이나 실제 Jenkins 잡 실행을 대신하지 않습니다.

- [현재 구성의 근거·제약](../docs/knowledge/infra/server-configuration.md)
- [S15P31E102-65 작업·검증 기록](../docs/plans/S15P31E102-65.md)
