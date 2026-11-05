# Jenkins 검증 산출물과 Gradle JVM 재사용

- Task: S15P31E102-510. 2026-11-05 사용자가 지연 조사 결과의 중복 빌드·반복 JVM 시작 개선을 요청했다.
- 기존 조사: [측정과 한계](../knowledge/infra/jenkins-latency.md). 원래 읽기 전용 범위에서 구현으로 확장한 후속 요청이다.

## 동작과 범위

1. 운영 backend CI 이미지는 해당 실행에서 `bootJar`·테스트로 검증한 실행 JAR 하나를 복사한다. Docker 안에서 Gradle·컴파일러를 다시 실행하지 않는다. 로컬 개발용 `backend/Dockerfile`의 소스 빌드는 유지한다.
2. plain JAR은 선택하지 않는다. 실행 JAR이 없거나 여러 개면 이미지 생성 전에 실패한다. revision은 기존과 같이 소문자 40자리 SHA다.
3. 이미지의 revision label과 원본 JAR SHA-256을 기록한다. 이미지에서 추출한 `/app/app.jar`의 SHA-256이 원본과 같아야 빌드 단계를 성공시킨다. 검사용 컨테이너는 시작하지 않으며 고유 이름·검증 라벨로 식별하고 삭제한다. 복사 중 SIGTERM이면 실패로 종료하며 정리하고, 삭제 중 SIGTERM이면 삭제가 끝날 때까지 종료를 유예한다. SIGKILL이나 호스트 장애에서 즉시 회수할 수는 없다.
4. 기존 Java 21·ffmpeg 버전·비특권 UID/GID 10001·작업 경로·entrypoint를 유지한다. 임시 Docker context에는 선택한 `app.jar`만 넣는다.
5. backend/frontend Gradle 명령은 `--daemon -Dorg.gradle.daemon.idletimeout=60000`을 사용한다. 기존 stage·task·JUnit 수집·최적화 옵션·20분 제한·배포 순서와 `wait: false`를 유지한다. 다른 빌드의 daemon을 강제로 종료하지 않는다.
6. executor 증설, 구버전 잡 자동 취소, 테스트 생략과 Wasm 최적화 수준 변경은 이번 구현에 포함하지 않는다. 실행 시간 절감이 공유 executor 점유와 후속 대기를 줄이며, 자원 분리와 취소 정책은 별도 측정·결정이 필요하다.

## 완료 조건과 검증

- 빠른 자동 검사로 JAR 선택·오류·revision·원본/이미지 해시 불일치·검사용 컨테이너 정리를 검증한다. 실제 Docker의 복사·삭제 지점 중단에서 검사용 컨테이너와 임시 context가 회수되는지 별도로 확인한다.
- 운영과 분리한 임시 경로·전용 이미지 tag로 실제 Docker에 기존 성공 빌드 JAR을 넣고 bytes·label·UID·ffprobe를 확인한다. 운영 앱·DB를 실행하거나 배포하지 않는다.
- 실제 Gradle 9.5.1의 작은 독립 프로젝트에서 기존 no-daemon과 변경 옵션을 비교한다. 연속 호출의 JVM PID 재사용과 입력 변경의 출력 반영을 확인하고 시간을 기록한다. 이 fixture 수치를 전체 앱의 단축률로 확대하지 않는다.
- Jenkins 선언형 linter와 기존 Jenkins 도구·배포 hook 회귀를 수행한다. 기본 검사에서 Docker·전체 앱 빌드를 실행하지 않는다.
- 운영 관리 파일 교체에는 설치 순서·백업·잡 정의 해시 재확인을 준비한다. Push·MR·운영 반영 상태를 코드 구현 상태와 구분해 보고한다.
