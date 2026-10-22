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
