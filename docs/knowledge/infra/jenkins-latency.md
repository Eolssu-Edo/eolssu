# Jenkins 전체 시간과 Wasm 최적화 지연 조사

- 작업: [S15P31E102-510](https://ssafy.atlassian.net/browse/S15P31E102-510), Story S15P31E102-40, Epic S15P31E102-21.
- 확인일: 2026-11-05 KST. 독자는 배포·CI 담당자이며 목적은 기존 실행의 병목과 개선 순서를 판단하는 것이다.
- 저장소 기준: `3002e124d2f370ca683d5b0470a9284c09413e7b`의 `infra/jenkins/*.Jenkinsfile`, `backend/Dockerfile`. 운영 잡은 inline Pipeline이므로 실제 `config.xml`과 완료 빌드를 별도로 확인했다.
- [측정값과 현재 설치 Pipeline 해시](evidence/jenkins-latency510.json), [읽기 전용 재현 명령](../../../infra/README.md#jenkins-전체-시간과-단계-시간-대조).

## 결론과 측정

백엔드 #259에서 단계 밖에 더해진 시간의 대부분은 **Linux executor 할당 대기**다. 종료 후처리나 하위 프론트 빌드 완료 대기가 아니다. 프론트 #186은 같은 자원 대기에 더해 **production Wasm 최적화가 812.672초 동안 끝나지 않아** 파이프라인의 20분 실행 제한으로 중단됐다. 최적화 내부의 특정 pass·CPU·I/O 병목은 이 이력만으로 확정하지 못했다.

| 잡·빌드 | 결과 | 전체(초) | 첫 stage 이전(초) | 첫~마지막 stage 경과(초) | 마지막 stage 이후(초) |
| --- | --- | ---: | ---: | ---: | ---: |
| backend #257 | SUCCESS | 694.054 | 12.908 | 680.885 | 0.261 |
| backend #258 | SUCCESS | 433.722 | 0.636 | 432.794 | 0.292 |
| backend #259 | SUCCESS | 1594.110 | 853.945 | 739.802 | 0.363 |
| frontend #185 | FAILURE | 1613.958 | 828.567 | 785.001 | 0.390 |
| frontend #186 | ABORTED | 2182.348 | 975.936 | 1205.924 | 0.488 |

전체는 `build.xml`의 `duration`이다. 첫 stage 이전은 `min(top-level startTimeMillis) - startTime`이다. 초기화도 포함하므로 순수 queue 시간의 정밀 측정으로 취급하지 않는다. `Still waiting to schedule task` 로그와 실제 executor 점유 구간이 대기를 뒷받침한다. 빌드 번호 생성 전의 queue 체류는 별도이며 이 계산에 추가하지 않았다.

stage 경과에는 stage 사이 전환 비용이 포함된다. 중첩·병렬 배포 phase는 상위 stage와 겹치므로 더하지 않는다. backend #259의 상위 stage 합계는 738.401초로 경과 739.802초와 약 1.4초 차이다.

## 백엔드: 26분 34초 중 약 14분 14초가 첫 stage 이전

2026-11-05 KST 기준 #259 실행 기록은 11:45:01.299에 시작됐지만 첫 `Clean workspace`는 11:59:15.244에 시작했다. 종료는 12:11:35.409다. 단계 실행은 약 12분 20초, 종료 후처리는 0.363초다.

운영 구성은 controller executor 0, `edo1-host` executor 1(NORMAL, labels `edo1 linux docker`), `mac-ios` executor 1(EXCLUSIVE, labels `macos ios`)이었다. backend/frontend는 최상위 `agent any`를 사용한다. 로그의 Mac 예약 안내와 노드 설정을 함께 보면 이 두 잡은 Linux executor 하나를 공유했다.

#259 대기 구간에는 frontend #185(11:43:32.534~11:56:37.925), Android QA #96(11:56:37.993~11:59:15.193)이 차례로 실행됐다. Android QA #95도 그 앞의 Linux 실행 구간을 사용했다. agent를 기다리는 동안 전체 시간은 증가하지만 업무 stage는 아직 시작하지 않는다.

설치된 backend Pipeline은 Android QA·iOS QA·frontend를 모두 `wait: false`로 예약한다. 따라서 #259의 숨은 14분을 하위 잡 완료 대기로 설명할 수 없다.

최상위 agent의 timeout은 agent를 할당한 뒤 시작한다. 그래서 `timeout(time: 20, unit: 'MINUTES')`여도 대기를 포함한 전체 시간이 20분을 넘을 수 있다. [Jenkins 공식 구문](https://www.jenkins.io/doc/book/pipeline/syntax/#differences-between-top-level-agents-and-stage-level-agents)과 #186의 대기·실행 시간을 대조했다.

## 프론트: 최적화 실행 비용과 제한 초과

#186은 첫 stage 전에 975.936초를 보냈다. 실행 후 `Compile production Wasm`는 139.138초, `Optimize production Wasm`는 812.672초를 사용했다. 로그에는 최적화 task 시작, `Cancelling nested steps due to timeout`, 최적화 task FAILED 순서가 남았다. 실행 구간 전체는 약 20분 6초다. **812.672초는 최적화 완료 시간이 아니다.** 번들·이미지·배포 단계는 건너뛰었다.

서버 작업공간에서 Kotlin 2.4.20, Compose Multiplatform 1.12.1과 Binaryen `binaryen-version_130/bin/wasm-opt` 설치를 확인했다. Kotlin/Wasm production 빌드는 Binaryen 최적화를 기본 적용한다. [Kotlin 공식 설명](https://kotlinlang.org/docs/whatsnew20.html#optimized-production-builds-by-default-using-binaryen).

캐시가 전부 사라진 사례로 볼 근거는 없다. #186 로그에는 configuration cache 재사용과 일부 컴파일 task 재사용이 있고 최적화 task에는 `UP-TO-DATE`가 붙지 않았다. [Task 292의 같은 SHA 재빌드](frontend-ci-cache.md)는 최적화 task가 UP-TO-DATE일 때 9.111초였고 빈 작업공간에서는 481.726초였다. 같은 입력의 재사용 성과이며 최적화 입력이 바뀐 빌드의 비용을 없앤 결과는 아니다. 당시 SHA와 #186이 달라 시간 증가율을 직접 비교하지 않는다.

조사 시점 서버는 4 vCPU, 약 16GB RAM이었다. timeout 이후 프로세스 조회에서 남아 있는 `wasm-opt`는 없었고 `vmstat`의 후속 두 표본은 swap-in/out 0이었다. 이는 #186 최적화 실행 당시의 프로파일이 아니므로 당시 자원 포화·swap·특정 Binaryen pass를 원인으로 확정하지 않는다.

## 추가로 확인한 비용

- **Gradle 반복 시작:** 설치 Pipeline은 frontend 10회, backend 7회 `./gradlew --no-daemon`을 호출한다. #186은 최적화까지 도달한 8회 모두 single-use daemon 시작 메시지가 있다. 설정 캐시와 JVM 재사용은 별개다. [Gradle 공식 설명](https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:disabling_the_daemon). 프로세스 시작 비용만 분리한 실험은 하지 않았다.
- **백엔드 이미지 재컴파일:** Jenkins의 `bootJar`/`build` 후 `/opt/eolssu/config/backend.Dockerfile` build stage가 `bootJar`를 다시 실행한다. 설치 파일은 저장소 `backend/Dockerfile`과 같은 구조다. #259 이미지 빌드는 123.479초, Docker 내부 Gradle 빌드는 1분 51초였다. Docker layer가 적중하면 재실행하지 않지만 이 빌드에서는 실제 실행했다.
- **백엔드 출력 삭제:** backend는 매번 `deleteDir()`와 `clean`으로 프로젝트 출력·설정 캐시를 지운다. 홈의 의존성 캐시까지 지운다는 뜻은 아니다. frontend에는 Task 292의 작업공간 보존이 적용돼 있다.

## 개선 우선순위와 후속 검증

아래는 제안이며 운영 변경은 적용하지 않았다.

1. 잡별 대기 시간을 노출하고 동일 SHA 중복 예약·이미 대체된 SHA 실행 여부를 확인한다. 구버전 생략을 도입한다면 배포 transaction 밖에서 판정하고 최신 검증을 보존한다. 배포 중인 잡을 무조건 abort하는 방식은 사용하지 않는다.
2. 백엔드 이미지에 검증한 JAR을 재사용하는 방안을 준비한다. 이미지 JAR과 테스트한 JAR의 SHA-256·revision 일치와 기존 비특권 사용자·ffmpeg·실행 경로를 검증한다. 이미지 단계 123초 전체가 없어질 것으로 예측하지 않는다.
3. Gradle 호출 통합 또는 daemon 재사용을 같은 입력으로 비교한다. 단계별 실패 식별, JS/Wasm·media 테스트와 산출물 검사는 유지한다. 백엔드 작업공간 보존도 입력 무효화·소스 정리 검증이 필요하다.
4. CI 담당자가 배포하지 않는 격리 작업공간에서 #186과 같은 입력으로 최적화만 프로파일링한다. 실행 전 운영 빌드와 CPU·RAM 경쟁을 피할 조건을 정한다. task 시간, CPU/RSS·I/O, 종료 코드, Wasm 크기·브라우저 동작을 남긴 뒤 옵션·버전 변경을 비교한다.
5. executor 증설이나 노드 분리는 측정 후 결정한다. 같은 4 vCPU 서버의 executor 수만 늘리면 동시 JVM·최적화와 운영 서비스가 경쟁할 수 있다. timeout 증가는 병목을 줄이는 조치가 아니다.

## 확인 범위

기존 완료 빌드·잡 XML·설치 파일·노드 구성과 짧은 현재 자원 표본을 읽었다. 빌드 재실행·운영 설정·서비스/DB 변경·배포는 수행하지 않았다. 최적화 pass별 원인과 개선 후 성능은 미검증이다. 재현 명령과 보존 JSON의 수치·문서 링크·공백 검사 결과는 [작업 계획](../../plans/S15P31E102-510.md)에 기록한다.
