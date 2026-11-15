# 인프라 실행과 운영 문서

이 폴더는 운영 서버 edo1·edo2의 구성 원본과 점검·배포·복구 도구를 관리합니다. 서버의 `/opt/eolssu/config/` 등에 복사해 쓰는 파일은 저장소를 바꿔도 서버에 자동 반영되지 않으므로, 각 문서의 반영 절차를 함께 따릅니다. 작업 지침은 [AGENTS.md](AGENTS.md)를 따릅니다.

## 운영 구성 한눈에

| 서버 | 구성 | 원본 |
| --- | --- | --- |
| edo1 (운영) | Nginx(웹 정적 파일·`/api/` 프록시·TLS) → backend 블루그린 · ai · omr · playback · MySQL 8.4 | [runtime](runtime/README.md) |
| edo1 (CI·관측) | Jenkins 컨트롤러와 호스트 에이전트, Prometheus·Grafana, Jenkins 실패 Mattermost 알림, 배포 산출물 보존 정리 | [jenkins/controller](jenkins/controller/README.md) · [monitoring](monitoring/README.md) · [jenkins/notifications](jenkins/notifications/README.md) · [retention](retention/README.md) |
| edo2 (보조) | DB·설정 암호화 백업과 격리 복원, MySQL 읽기 전용 복제본·격리 대기 환경, Mattermost 점검 중계와 상태 변화 알림 | [backup](backup/README.md) · [standby](standby/README.md) · [edo-check](edo-check/README.md) |

- backend: develop 병합을 Jenkins가 감지해 블루그린으로 자동 배포합니다([runtime](runtime/README.md#jenkins)).
- 웹: Jenkins 수동 잡으로 배포합니다([runtime](runtime/README.md#프론트-웹-수동-배포)).
- ai·omr·playback: Jenkins가 다시 만들지 않으며, 담당자가 edo1에서 이미지를 빌드해 해당 서비스만 재생성합니다([runtime](runtime/README.md#ai-분석-서비스)).
- 최종 발표용 RC 선정·테스트 영수증·main 반영·태그·시연 동결·장애 판단은 [릴리스 절차](release/README.md)를 따릅니다.

## 문서 안내

| 분류 | 문서 |
| --- | --- |
| 전체 구조 | [시스템 아키텍처](../docs/knowledge/infra/system-architecture.md) · [운영·품질 실측 근거](../docs/knowledge/infra/ops-quality-evidence.md) |
| 배포·릴리스 | [운영 구성·배포·복구](runtime/README.md) · [릴리스 절차](release/README.md) · [Jenkins 컨트롤러](jenkins/controller/README.md) · [iOS Ad Hoc 자동 배포](ios-qa/README.md) |
| 백업·복제·대기 | [자동 백업과 격리 복원](backup/README.md) · [edo2 격리 대기·복제본](standby/README.md) · [edo2 장애 전환 런북](standby/failover-runbook.md) |
| 부하 확인 | [시연 규모 동시 접속 리허설](loadtest/README.md) |
| 점검·알림·관측 | [Mattermost 점검 중계·상태 변화 알림](edo-check/README.md) · [Jenkins 실패 알림](jenkins/notifications/README.md) · [Grafana·Prometheus](monitoring/README.md) · [운영 MySQL 웹 조회](mysql-admin/README.md) |
| 저장공간·로그 | [배포 산출물 보존 정리](retention/README.md) · [Docker 기본 로그 회전](docker/README.md) |
| 데이터·파일 | [R2 파일·폴더 업로드](r2/README.md) · [기본음·시김새 카탈로그 적재](catalog/README.md) · [국내 비공개 녹음 저장 전환](storage/README.md) |
| 약관·개인정보 | [약관 본문 공개와 버전 등록](terms/README.md) · [개인정보 보유기간과 관리자 처리 기록](privacy/README.md) |
| QA·팀 도구 | [실제 Android 기기 QA 웹](qa-phone/README.md) · [Eolssu Updates 앱](tool-updates/README.md) |

구성 선택·병목 측정·배포 제약의 근거는 [docs/knowledge/infra](../docs/knowledge/infra/)에 있습니다.

## Jenkins 파이프라인 원본

운영 Jenkins의 backend·웹·QA 잡은 파이프라인을 SCM이 아니라 잡 설정에 직접 둡니다. 저장소에는 아래 원본을 두고, 바꿀 때는 각 문서의 서버 반영 절차를 따릅니다([runtime](runtime/README.md#jenkins)). 기술 블로그만 `blog` 브랜치의 `Jenkinsfile`을 SCM으로 읽습니다. 잡 목록과 실패 알림 대상은 [Jenkins 실패 알림](jenkins/notifications/README.md)을 따릅니다.

| 파일 | 용도 |
| --- | --- |
| [jenkins/backend.Jenkinsfile](jenkins/backend.Jenkinsfile) | backend 검증·이미지·운영 DB 마이그레이션·블루그린 배포(`eolssu-backend`, 서버 복사본 `/opt/eolssu/config/backend.Jenkinsfile`) |
| [jenkins/frontend.Jenkinsfile](jenkins/frontend.Jenkinsfile) | 검증된 `REVISION`의 웹 빌드·배포(수동 잡) |
| [qa-phone/android.Jenkinsfile](qa-phone/android.Jenkinsfile) · [ios-qa/ios.Jenkinsfile](ios-qa/ios.Jenkinsfile) | Android·iOS QA 앱 빌드·배포 |
| [release/release-candidate.Jenkinsfile](release/release-candidate.Jenkinsfile) | 릴리스 후보 검사 잡 초안([릴리스 절차](release/README.md)) |

초기 CI 정의였던 `backend/Jenkinsfile`은 운영 잡이 쓰지 않아 [S15P31E102-942](../docs/plans/S15P31E102-942.md)에서 삭제했습니다. backend CI를 바꿀 때는 `jenkins/backend.Jenkinsfile`과 서버 복사본을 고칩니다.

## edo2 점검 중계 서버 준비

2026-10-23 `ssh edo2` 접속과 UFW 활성화를 확인했습니다. IPv4·IPv6 모두 인바운드는 기본 차단하고 TCP 22(SSH), 80(ACME 검증), 443(HTTPS)만 허용합니다. 아웃바운드는 허용합니다. 적용 전 설정은 서버의 root 전용 `/var/backups/edo2-ufw/`에 보관했습니다.

상태를 읽을 때는 다음 명령을 사용합니다. 방화벽 변경 뒤에는 기존 연결을 재사용하지 않는 새 SSH 접속으로 관리 경로를 검증합니다.

```sh
ssh edo2 'sudo -n ufw status verbose'
ssh -o BatchMode=yes -o ConnectTimeout=10 -o ControlMaster=no -o ControlPath=none edo2 'sudo -n ufw status numbered'
```

Mattermost 명령과 HTTPS 인증서·중계 서비스·edo1 전용 SSH 수집기의 실행·권한·지표·갱신·복구는 [점검 중계 안내](edo-check/README.md), 초기 합의와 검증은 [S15P31E102-95 작업 기록](../docs/plans/S15P31E102-95.md)을 따릅니다. 정적 IP `15.165.144.7`을 연결했고 인증서·Mattermost URL·SSH 출발지 제한과 관리 SSH 별칭을 함께 전환했습니다.

## 로컬 두 서비스 예시 (운영 미사용)

루트의 `docker-compose.dev.yml`·`docker-compose.prod.yml`은 초기(S15P31E102-65)에 만든 backend·Nginx 두 서비스 예시입니다. 운영 edo1은 [runtime/compose.yml](runtime/compose.yml)을 사용하며 이 두 파일을 쓰지 않습니다. backend는 DB 없는 기본 프로필로 뜨므로 업무 API·DB 확인에는 [backend README](../backend/README.md)의 `db` 프로필과 실제 MySQL 검사를 사용합니다.

| 파일 | 역할 |
| --- | --- |
| `docker-compose.dev.yml` / `docker-compose.prod.yml` | 개발·운영 이름의 backend·Nginx 예시(`eolsu-dev`, `eolsu-prod` 프로젝트) |
| `.env.dev.example` / `.env.prod.example` | Compose 치환용 환경변수 예시 |
| `backend/Dockerfile` / `backend/Dockerfile.dockerignore` | JDK 21 빌드, JRE 21 실행(녹음 검사용 ffmpeg 버전 고정). 운영 실행 이미지는 [runtime](runtime/README.md#backend-실행-이미지)을 따름 |
| `nginx/default.conf` | URI를 유지해 `backend:8080`으로 전달하는 HTTP 프록시 |

| 변수 | 의미 | 개발 / 운영 예시 |
| --- | --- | --- |
| `BACKEND_IMAGE` | 로컬 빌드 이미지 이름·태그 | `eolsu-backend:dev` / `eolsu-backend:prod` |
| `HTTP_BIND_ADDRESS` | Nginx의 호스트 바인딩 주소 | `127.0.0.1` / `127.0.0.1` |
| `HTTP_PORT` | Nginx의 호스트 포트. 누락·빈 값은 구성 검사 실패 | `8080` / `8081` |

`.env.dev`·`.env.prod`는 Git 제외 대상입니다. 예시를 복사할 때 기존 파일을 덮어쓰지 않습니다. 앱에는 Compose의 `environment`로 `SERVER_PORT=8080`만 전달합니다.

```sh
test -e .env.dev || (umask 077; cp .env.dev.example .env.dev)
docker compose --env-file .env.dev.example -f docker-compose.dev.yml config --quiet
docker compose --env-file .env.dev -f docker-compose.dev.yml up -d --build
curl -i http://127.0.0.1:8080/
docker compose --env-file .env.dev -f docker-compose.dev.yml down
```

프록시를 통과한 `/` 요청은 Spring Boot의 JSON `404`가 정상입니다. 연결 거부나 Nginx의 `502`는 성공이 아닙니다. 영속 볼륨은 없으며 `down -v`는 사용하지 않습니다.

## Jenkins 전체 시간과 단계 시간 대조

edo1 운영 Jenkins 완료 빌드를 읽기 전용으로 확인한다. SSH 별칭 `edo1`, sudo 읽기 권한, 서버 Python 3이 필요하다. 아래 번호는 2026-11-06 완료된 백엔드와 수동 웹 빌드다. 다른 이력은 실제 존재하는 잡 이름과 번호로 바꾼다. 보관 정책으로 빌드 또는 Graph View 캐시가 없으면 측정 불가이며 전체 시간을 0으로 처리하지 않는다.

```sh
ssh -o BatchMode=yes -o ClearAllForwardings=yes -o ConnectTimeout=10 edo1 'sudo -n python3 -' <<'PY'
from pathlib import Path
import json
import xml.etree.ElementTree as ET

for job, number in [('eolssu-backend', 282), ('eolssu-frontend-web-수동배포용', 1)]:
    path = Path('/var/lib/jenkins/jobs') / job / 'builds' / str(number)
    build = ET.parse(path / 'build.xml')
    stages = json.loads((path / 'pipeline-graph-view-tree.v3.json').read_text())['stages']
    if build.findtext('result') is None or not stages:
        raise RuntimeError('Completed build with stage records required')
    start = int(build.findtext('startTime'))
    duration = int(build.findtext('duration'))
    first = min(s['startTimeMillis'] for s in stages)
    last = max(s['startTimeMillis'] + s['totalDurationMillis'] for s in stages)
    print(json.dumps({
        'job': job, 'build': number, 'result': build.findtext('result'),
        'total_s': duration / 1000,
        'pre_stage_s': (first - start) / 1000,
        'stage_span_s': (last - first) / 1000,
        'post_stage_s': (start + duration - last) / 1000,
    }))
PY
```

`pre_stage_s`는 agent 할당 대기와 초기화의 합이다. Console의 `Still waiting to schedule task`, 노드 executor 설정과 동시간대 다른 잡 실행 구간으로 대기를 판별한다. 상위 stage 안의 병렬 배포 phase를 다시 더하지 않는다. 최상위 agent 할당 대기는 그 뒤 시작하는 Pipeline timeout에 포함되지 않는다. 운영 연결은 [runtime 문서](runtime/README.md#jenkins), 실제 수치·개선 순서·한계는 [Task 510 조사](../docs/knowledge/infra/jenkins-latency.md)를 따른다.

## 검증

- 폴더별 테스트·검사 명령은 각 README를 따릅니다. Python 도구 테스트는 대부분 `python3 -B -m unittest discover -s infra/<폴더>/tests` 형식입니다.
- 문서 상대 링크: `python3 -B infra/scripts/check-markdown-links.py` (끊긴 링크가 있으면 `파일:행: 대상`을 출력하고 1로 종료). 검사기 테스트: `python3 -B -m unittest discover -s infra/scripts/tests -p test_check_markdown_links.py`.
- 저장소 검사는 운영 서버의 배포 성공이나 실제 Jenkins 잡 실행을 대신하지 않습니다.
