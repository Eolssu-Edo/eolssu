# 서버 인프라 구성의 근거와 제약

- 작업: [S15P31E102-65](https://ssafy.atlassian.net/browse/S15P31E102-65)
- 확인일: 2026-10-22
- 상태: 실행 가능한 초기 구성. 운영 배포·서버 공개 정책은 미확정.

## 확인한 상태

SSH 별칭 `edo1`의 `/opt/app`과 저장소를 읽기 전용으로 대조했다. 서버의 개발·운영 Compose와 환경변수 파일 네 개는 모두 0바이트였고 Docker Compose 프로젝트·실행 컨테이너가 없었다. Nginx·Jenkins 실행 파일도 확인되지 않았다. 이 파일들은 기존 운영 구성을 추출한 결과가 아니라, 현재 백엔드 구현에 맞춰 새로 작성한 실행 기반이다.

`backend/build.gradle.kts`, Wrapper, `backend/src/main/resources/application.yml`에서 Java 21·Spring Boot·`bootJar`·`SERVER_PORT`를 확인했다. DB·인증·AI 호출은 아직 앱에 연결되지 않았다. 블로그 파이프라인은 `blog/Jenkinsfile`에 이미 있으므로 백엔드는 별도 `backend/Jenkinsfile`을 사용한다.

## 구성 선택과 적용점

- Docker 멀티 스테이지에서 JDK 21로 실행 JAR을 빌드하고 JRE 21에서 UID/GID 10001로 실행한다. 빌드 컨텍스트는 필요한 소스·Wrapper만 허용하고 실제 환경 설정과 키 파일은 제외한다.
- 개발·운영은 독립 Compose 프로젝트와 이미지 태그를 사용한다. Nginx만 호스트 포트에 연결하고 백엔드는 내부 네트워크에 둔다. 예시 포트 8080/8081과 loopback은 기동 예시이며 외부 공개 정책을 확정하지 않는다.
- Nginx는 URI를 변경하지 않고 전달하며 Docker DNS로 백엔드 주소 변경을 재조회한다. TLS·도메인·업로드 제한·timeout은 실제 요구가 정해질 때 구성한다.
- Jenkins는 검증·이미지 빌드·JAR 보관을 수행한다. 경로 필터를 두지 않아 공통 설정·문서 변경도 검사 대상에서 빠지지 않는다. 배포 대상·레지스트리·인증·트리거를 추측해 자동 배포를 넣지 않는다.
- 실제 서버의 미추적 Compose는 같은 이름의 추적 파일과 충돌하므로 반영 전에 저장소 밖으로 백업해야 한다. 환경변수 원본도 보존한다. 실행·백업·복구 절차는 [infra/README](../../../infra/README.md)에 둔다.

## 출처

- 저장소: `backend/README.md`, `backend/build.gradle.kts`, `backend/src/main/resources/application.yml`, `blog/Jenkinsfile`.
- 직접 점검: `/opt/app`의 `git status`, 파일 크기·내용, `docker compose ls`, `docker ps`, 실행 파일 존재 여부. 비밀값은 수집하지 않았다.
- [Docker Compose 변수 치환](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/): `--env-file`과 필수 변수의 역할.
- [Dockerfile 공식 참조](https://docs.docker.com/reference/dockerfile/): 멀티 스테이지·캐시 mount·실행 사용자.
- [공식 Nginx 이미지](https://hub.docker.com/_/nginx): 작성 시 stable 계열인 `1.30.6-alpine` 사용.
- [Jenkins Declarative Pipeline](https://www.jenkins.io/doc/book/pipeline/syntax/): stages·steps·post·artifact 구문.

## 미확인 사항

실제 Jenkins 잡 실행, 외부 공개 주소·TLS, 배포 트리거와 레지스트리, 배포 이력·롤백 대상은 준비되지 않았다. 검증 결과와 환경 한계는 [작업 기록](../../plans/S15P31E102-65.md)에 남긴다. DB 제품·AI API 방식은 이 작업에서 결정하지 않는다.

## 후속 서버 반영 — 2026-10-22

후속 요청 시 서버에는 `/opt/eolsu/config/compose.yml`의 프론트·백엔드·MySQL이 이미 실행 중이었다. 기존의 두 서비스 초기 예시로 덮어쓰지 않고 실제 운영 구성을 `infra/runtime/`에 기록했다. 사용자가 서버 반영·`eolssu` 이름 전환과 edo1의 Jenkins 신규 설치를 승인했다.

설정·릴리스·MySQL 전체 SQL을 먼저 백업하고 `/opt/eolssu`·`eolssu-runtime`으로 전환했다. 기존 `eolsu-runtime_mysql_data` 물리 볼륨을 external volume으로 재사용하며 DB 서버 UUID가 같은지 확인했다. 앱 내부 패키지·JAR·DB 이름은 변경 대상에서 제외했다. 프론트 200·백엔드 프록시 JSON 404·Nginx 설정 검사·DB 상태가 유지됐다.

Jenkins LTS는 공식 apt 저장소에서 설치하고 loopback 수신·관리자 인증·CSRF·가입 차단을 설정했다. 처음 기동할 때 `libfontmanager.so`가 요구하는 `libharfbuzz.so.0`이 누락되어 실패했으며 공식 Java 21 JRE 의존성 설치 후 기동했다. 일반 `ldd`에서 보이는 `libjvm.so`는 Java loader 경로를 반영하지 못한 결과이므로 실제 Jenkins 기동·인증 API를 최종 판정으로 사용했다.

- [Jenkins Ubuntu 설치](https://www.jenkins.io/doc/book/installing/linux/)
- [Jenkins CLI와 플러그인 설치](https://www.jenkins.io/doc/book/managing/plugins/)
- [Jenkins CSRF](https://www.jenkins.io/doc/book/security/csrf-protection/)
- [Compose external volume](https://docs.docker.com/reference/compose-file/volumes/)

현재 운영 절차·백업 위치·Jenkins 접속 방법은 [운영 문서](../../../infra/runtime/README.md), 실제 잡 실행과 최종 검증 결과는 [작업 기록](../../plans/S15P31E102-65.md)을 따른다. 이전 절의 미확인 사항은 최초 작성 당시의 상태다.


## develop 자동 배포 연결 — 2026-10-22

사용자가 develop 변경의 자동 빌드·테스트·서버 배포를 승인했다. Jenkins가 loopback에서만 수신하는 현재 구조를 유지하기 위해 1분 SCM polling을 선택했다. `refs/heads/develop`을 명시하고 변경이 없으면 빌드하지 않는다. 병합과 직접 Push를 구분하는 트리거가 아니라 develop의 커밋 변경을 감지한다.

배포는 현재 런타임에 맞춰 백엔드 JAR만 교체한다. 검증한 커밋별 이미지에 revision label을 넣고, 포트 공개·운영 비밀값 없이 internal network에서 후보를 실행한 뒤 같은 이미지의 `/app/app.jar`을 추출한다. Docker Compose 5.6.1의 internal network에서는 published port가 할당되지 않아 초기 후보 검사가 실패했다. 내부 IP로 호스트에서 검사하도록 수정해 격리를 유지했다.

상태 파일과 `.env`를 원자적으로 교체하고 배포 전 이전 JAR을 기록한다. 실패 시 이전 JAR을 다시 선택하고 백엔드만 재생성한다. 강제 종료는 다음 실행에서 journal을 읽고 먼저 복구한다. 같은 SHA·이미지·JAR이 이미 배포됐다면 실제 응답·bind mount를 확인하고 재시작하지 않는다. 프론트·MySQL 컨테이너 ID와 물리 볼륨은 배포 전후 같아야 한다.

단위 검사 5개에서 성공·동일 아티팩트 재실행·실패 복구·중단 복구·복구 실패 후 재개·잘못된 SHA를 확인했다. 실제 임시 Compose에서도 깨진 JAR을 배포한 뒤 readiness 실패와 이전 JAR 복구를 확인했다. 운영 데이터·서비스와 분리된 fixture는 제거했다. 활성화·실제 자동 실행 증거와 권한 승인 상태는 작업 기록을 따른다.

- [Jenkins polling trigger](https://www.jenkins.io/doc/book/pipeline/syntax/#triggers)
- [Jenkins SCM checkout와 정확한 branch ref](https://www.jenkins.io/doc/pipeline/steps/params/scmgit/)

자동 승인 검토는 Jenkins에 root 배포 도구의 영구 sudo 실행 권한을 추가하는 단계를 별도 승인이 필요한 보안 경계 변경으로 거부했다. 우회하지 않고 검증을 마친 도구와 제한된 권한을 제시해 사용자 승인을 요청했다.

사용자가 배포 도구의 sudo 실행 권한을 명시적으로 승인해 해당 한 줄만 설치하고 `visudo` 검사 후 트리거를 활성화했다.

## Let's Encrypt 실행 파일 누락 — 2026-10-22

`/opt/app/infra/scripts/init-letsencrypt.sh`의 파일 없음은 실행 권한 문제가 아니었다. 서버 develop은 `23e1d26`이었고 피처 브랜치에도 해당 파일이 없었다. 실제 서비스는 별도 `/opt/eolssu/config/compose.yml`로 동작하므로 소스 갱신만으로 운영 TLS가 활성화되지는 않는다.

사용자가 `k15e102.p.ssafy.io`의 HTTPS 적용을 승인했다. 현재 프론트 webroot로 HTTP-01을 처리하고, 발급·Nginx 검사를 통과한 뒤 TLS Compose 추가 구성으로 프론트만 재생성한다. 기본 HTTP와 백엔드·MySQL·외부 데이터 볼륨은 유지한다. 초기화 실패 시 기존 설정을 복구하며 Jenkins 배포 lock과 조율한다. 자동 갱신에는 Ubuntu Certbot timer와 갱신 후 Nginx reload hook을 사용한다.

격리된 TLS 회귀 검사 7개, 실제 서버 Compose·Nginx TLS 검사와 Let's Encrypt staging의 실제 도메인 발급 dry-run을 통과했다. rollback을 제거한 격리 복제본은 회귀 검사에서 실패했다. 운영 인증서 발급·최종 배포 결과는 [작업 기록](../../plans/S15P31E102-65.md)에 이어 기록한다.

## HTTP의 HTTPS 자동 전환 — 2026-10-22

HTTPS 응답과 HTTP 자동 전환은 별도 설정이다. Task 65의 TLS 템플릿에는 443 server만 있어 도메인의 HTTP 요청도 기본 HTTP server에서 200으로 응답했다. [Task 75](../../plans/S15P31E102-75.md)는 TLS 템플릿에 도메인 전용 80 server를 추가하고 일반 요청에 `308 https://<인증서 도메인>$request_uri`를 반환한다. 목적지는 요청의 Host 값 대신 검증한 인증서 도메인으로 고정한다.

ACME 경로는 HTTP로 토큰 파일을 제공하고 누락 토큰에는 404를 반환한다. 리다이렉트는 `location /` 안에 두므로 ACME location보다 먼저 실행되지 않는다. 기본 HTTP server는 유지하므로 `http://127.0.0.1/`와 `/api/infra-smoke`를 쓰는 기존 Jenkins 검사와 초기 인증서 발급에 영향이 없다. 프론트 배포에서 webroot를 교체할 때는 Task 73의 고정 ACME 경로와 이 location을 함께 맞춰야 한다.

근거: [Nginx return 지시어](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html#return), [Let's Encrypt HTTP-01](https://letsencrypt.org/docs/challenge-types/#http-01-challenge). 실제 이미지의 격리 HTTP 검사에서 수정 전 200·누락 ACME 토큰의 SPA 200을 재현한 뒤, 수정 후 308·ACME 200/404·loopback 200·HTTPS 200과 설정 검사를 통과했다. 명령과 운영 반영 결과는 Task 75 작업 기록을 따른다.

- [Certbot webroot와 HTTP-01](https://eff-certbot.readthedocs.io/en/stable/using.html#webroot)
- [Certbot 자동 갱신과 deploy hook](https://eff-certbot.readthedocs.io/en/stable/using.html#renewing-certificates)
- [실행·검증·복구](../../../infra/runtime/README.md#lets-encrypt와-https)

## Jenkins 컨테이너 전환 준비 — 2026-10-22

사용자가 edo1의 기존 설정·빌드·배포를 보존하는 컨테이너 전환을 승인했다. 컨트롤러를 동일 버전 공식 이미지로 옮기고 기존 UID/GID와 홈을 유지한다. 호스트의 Docker·Chrome·sudo 배포 명령은 같은 서버의 WebSocket 에이전트에서 그대로 실행한다. Pipeline의 실행 환경까지 컨테이너로 옮기는 변경은 포함하지 않는다.

격리된 이미지 기동에서는 `JENKINS_HOME`만 바꾸면 이미지의 기존 `COPY_REFERENCE_FILE_LOG` 경로 때문에 쓰기 실패했다. 로그 경로도 기존 홈으로 지정해 UID 111·network none·tmpfs 홈에서 실제 API 응답을 확인했다. 기존 Jenkins의 API에서 WebSocket launcher 생성과 명시적 `setWebSocket(true)`를 검증했으며 운영 노드를 추가하지 않았다. 원본 controller executor를 첫 컨테이너 기동 전에 0으로 바꾸는 것은 재기동 시 quiet-down이 풀려도 호스트 도구가 없는 컨트롤러에서 잡이 실행되지 않게 하기 위함이다.

이 구성은 컨트롤러에 Docker 소켓을 추가하지 않지만, 호스트 에이전트는 기존 `jenkins` 계정의 Docker·sudo 권한과 홈 접근을 유지한다. 호스트 권한을 격리했다고 주장하지 않는다. 다른 서버 작업이 끝난 뒤 전환하도록 사용자가 지시했으므로 실제 전환·보존 대조·빌드·배포 결과는 [Task 76](../../plans/S15P31E102-76.md)에 이어 기록한다.

- [공식 Docker 이미지의 홈과 사용자 설정](https://github.com/jenkinsci/docker/blob/master/README.md)
- [Jenkins 에이전트와 executor](https://www.jenkins.io/doc/book/using/using-agents/)
- [WebSocket inbound agent·secret 파일 인자](https://github.com/jenkinsci/remoting/blob/master/docs/inbound-agent.md)
- [서버 실행·복구 절차](../../../infra/jenkins/controller/README.md)

### 2026-10-23 실제 전환·보존 검증

선행 프론트 CI 작업 종료를 확인한 뒤 전환했다. 첫 에이전트 연결은 `secrets/jenkins.slaves.JnlpSlaveAgentProtocol.secret`을 신규 생성한다. 파일 목록까지 완전 일치시키던 검사가 이를 감지해 자동 복구했으며, 실패 시점 홈을 보존하고 기존 홈·apt 서비스·관리자 인증을 실제 복원했다. 기존 파일 변경·삭제는 없었다. 등록 전에는 엄격한 목록 대조를 유지하고 등록 후에만 이 에이전트 키 파일의 생성을 구분해 재시도했다.

기존 110개 파일의 해시와 executor 외 root 설정이 일치했고, 에이전트 키 생성 후 컨트롤러·에이전트 재기동에서 111개 파일과 노드 재연결을 확인했다. 같은 SHA `5414c84`의 실제 백엔드 #20·자동 연계 프론트 #11이 모두 SUCCESS이며 두 배포 영수증의 `deployed`를 확인했다. 테스트 1+4개와 배포 도구·Nginx 검사 32개가 통과했고, 공개 HTTPS 릴리스와 정적 파일 8개 전체 해시가 일치했다. 설정 보존과 실행 결과를 구분해 기록했고, 단순 컨테이너 기동만으로 완료 판정하지 않았다.

실제 데이터 홈은 계속 `/var/lib/jenkins`다. 이미지의 기본 VOLUME `/var/jenkins_home`에 보이는 별도 볼륨은 사용하지 않는 기본 경로이며, 실제 홈 bind mount와 혼동하지 않는다. 기존 apt 패키지는 남기되 서비스는 inactive/disabled로 유지한다. 최종 백업은 `/opt/backups/eolssu-jenkins76-20261023T080439Z/`, 실행·복구·검증 영수증은 [Task 76](../../plans/S15P31E102-76.md)을 따른다.

## 프론트 웹 자동 배포 연결 — 2026-10-22

Task S15P31E102-73은 기존 backend SCM polling이 성공한 동일 SHA를 frontend 잡에 전달한다. 서버 executor가 1개이므로 상위 잡이 하위 잡을 기다리면서 executor를 보유하면 하위 잡이 시작하지 못한다. `wait: false`로 예약 후 executor를 반환하며 두 잡의 최종 상태를 각각 확인한다. 배포 도구는 공유 lock을 최대 120초 기다리고 배포 직전 최신 develop SHA를 다시 확인한다.

운영 프론트는 기존 Nginx·HTTPS 구성을 유지하고 검증한 이미지에서 추출한 웹 파일만 불변 릴리스로 선택한다. 모든 파일 해시·Wasm MIME·필수 번들·symlink 차단을 검사하므로 SPA HTML fallback이 파일 누락을 숨기지 못한다. journal·원자적 env 교체·이전 릴리스로 복구하며 backend·MySQL·볼륨을 보존한다. 같은 SHA·이미지·파일이면 재생성하지 않는다.

웹 릴리스 경로를 Certbot webroot로 계속 쓰면 프론트 교체 후 인증서 갱신이 이전 경로에 challenge를 만들 수 있다. 고정 ACME 디렉터리를 `.well-known`에 별도로 mount하고 Certbot의 도메인별 매핑을 옮긴다. Certbot 2.9의 실제 staging 시험에서는 `--webroot-path`만 바꾸면 기존 `webroot_map`이 우선해 404가 발생했다. `--webroot-map`을 함께 지정하면 이 버전은 도메인 변경으로 인식해 reconfigure를 거부했다. 원본을 root 전용 백업하고 ConfigObj로 renewalparams의 webroot 두 항목만 원자적으로 바꾼 뒤 저장된 설정 자체로 staging 갱신·reload를 검증하는 방법을 적용했다. 실패한 설정 변경은 매번 복구 후 HTTPS를 확인했다.

Jenkins 사용자로 기존 Kotlin 웹 빌드·Chrome JS/Wasm 테스트 4개를 실행했고 첫 cold build는 6분 16초였다. 정적 파일 8개 후보 검증, 실제 Nginx의 읽을 수 없는 JS 배포 후 복구와 배포/TLS 회귀 검사를 수행했다. 최종 운영 활성화·실행 결과는 [Task 73](../../plans/S15P31E102-73.md)에 기록한다.

### 운영 활성화 검증 — 2026-10-23

사용자가 프론트 도구의 제한 sudo 권한을 승인해 root 소유 설치본·visudo·Jenkins 사용자 권한을 검증한 뒤 두 잡을 연결했다. 호스트 Jenkins의 backend 1분 SCM polling을 유지하며 frontend는 성공한 backend에서 같은 SHA로 시작한다. 병합 소스는 `ce06c5e7`이고 설치된 Pipeline의 SHA-256을 저장소와 대조했다. 운영 비밀 파일의 읽기 권한은 추가하지 않았다.

backend #9를 수동으로 한 번 시작하자 frontend #1이 upstream 원인으로 자동 시작해 양쪽 SUCCESS와 같은 배포 SHA를 확인했다. frontend 실행은 146.046초, 배포/TLS/Nginx 검사 32개와 Chrome 테스트 4개가 통과했다. 공개 HTTPS의 모든 정적 자산 8개·Wasm MIME·앱 버튼 동작과 HTTP 308·실제 ACME HTTP 200을 검증했다. 새 병합을 따로 만들지는 않았으며, 기존 backend #8에서 해당 develop 커밋의 SCM 감지 성공을 확인했다.

프론트 교체 중 backend·MySQL·볼륨, WEB_DIST_DIR 이외 env, 인증서·갱신 설정을 보존했다. 같은 아티팩트 재실행은 실제 도구에서 `already_deployed`와 컨테이너 ID 보존을 확인했다. 운영 증거는 root 전용 `/opt/eolssu/frontend-activation-receipt.json`, 복구용 백업은 `/opt/backups/eolssu-activate73-20261023/before.tgz`에 있다. 생성된 Kotlin/Wasm 번들의 memory deprecation 진단은 남아 있으며 실제 앱 표시 전환은 동작했다.

- [Kotlin/JS 브라우저 테스트와 설치된 Chrome](https://kotlinlang.org/docs/js-running-tests.html)
- [Jenkins 하위 잡 build step의 wait 옵션](https://www.jenkins.io/doc/pipeline/steps/pipeline-build-step/)
- [Certbot 갱신 설정 변경](https://eff-certbot.readthedocs.io/en/stable/using.html#modifying-the-renewal-configuration-of-existing-certificates)

## 기술 블로그 분리 — 2026-10-26

백엔드 develop 잡은 경로 필터 없이 develop의 모든 커밋을 검사·배포하고 프론트 잡을 이어 실행한다(위 '구성 선택과 적용점'). 그래서 develop의 `blog/`에 글을 머지해도 백엔드(약 1분 30초~3분)·프론트(약 3분 30초~4분) 빌드·운영 배포가 함께 일어났다. "develop에는 관리 대상 코드만 둔다"는 팀 의견에 따라 경로 필터를 넣는 대신 블로그를 develop과 기록을 공유하지 않는 `blog` 브랜치로 옮겼다. 앱 잡의 "공통 설정·문서 변경도 검사" 원칙은 그대로 유지된다.

블로그 잡 `eolssu-blog`는 `blog` 브랜치를 1분 polling해 Hugo 빌드 후 GitHub Pages(`team-ido/team-ido.github.io`)에 결과물만 push한다. 글 이미지는 저장소 clone이 커지지 않도록 GitHub `team-ido/blog-images`에 둔다. 구성·결정 기록은 [S15P31E102-128](../../plans/S15P31E102-128.md), [S15P31E102-137](../../plans/S15P31E102-137.md)을 따른다.
