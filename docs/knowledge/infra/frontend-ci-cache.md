# 프론트 CI 캐시 보존 측정 (S15P31E102-292)

2026-10-29, `edo1-host`의 전용 잡 `verify-frontend-cache-S15P31E102-292`에서 측정했다. 같은 작업공간, SHA `d50abd7219006e1cc50385a8d1947518e0b313f3`, JDK/Gradle/Kotlin·전역 Gradle 캐시·단일 executor를 사용했다. 첫 실행은 빈 프로젝트 작업공간, 두 번째는 ignored 캐시·산출물을 유지했다. 운영 배포는 양쪽 모두 제외하고 별도 Docker image tag를 사용했다.

## 결과와 비교 경계

- 공통 18개 단계(Clean workspace부터 Build frontend image)의 벽시계 시간: **1195.116초 → 118.999초**, 약 **90.04% 단축 / 10.04배**. executor 할당·Pipeline 초기화는 별도 441ms / 352ms였고 대기열은 비어 있었다.
- 첫 실행 #1은 모든 Gradle 명령과 테스트·Docker image 생성에 성공했지만, 추가 측정용 manifest Python의 줄바꿈 escaping 오류로 Archive 단계에서 실패했다. 전체 실행은 FAILURE, 1196.358초다. 이 실행을 전체 CI 성공으로 취급하지 않는다.
- #2 전에 측정용 Python의 불필요한 줄바꿈만 제거했다. 앱 소스·10개 Gradle 명령·테스트·옵션·공통 18개 단계는 동일하다. 따라서 전체 Pipeline 해시는 서로 다르고, 비교에서 수정된 Archive 단계를 제외했다. **전체 CI의 전후 성공 시간 비교를 주장하지 않는다.**
- 재빌드 #2는 Archive까지 **SUCCESS, 123.577초**. 설정 캐시를 10/10회 재사용했고 최적화 작업은 UP-TO-DATE였다.
- JUnit: 양쪽 모두 744개, 실패 0, 스킵 0. #2에서는 동일 입력인 Gradle 테스트 task의 UP-TO-DATE 결과를 게시했다. 테스트 명령은 여전히 실행되며, 입력이 바뀌면 Gradle이 무효화한다.
- 첫 실행 직후 별도로 확보한 배포 산출물 76개와 #2의 산출물 SHA-256이 전부 같다. manifest SHA-256: `2f456b4728ed05f2ad671754ec6092f1efc5dfcec89daae438f31151562e3885`.
- 한 쌍의 관측이다. 분산·장기간 효과 또는 소스가 바뀐 빌드의 동일한 단축률을 주장하지 않는다. 첫 빌드·무효화된 최적화 비용과 10회 single-use daemon 시작 비용은 남는다. 변경의 효과가 크고 재사용 판정·산출물 일치가 확인되어 유지한다.

## Gradle 단계별 벽시계 시간 (초)

| 단계 | 빈 작업공간 #1 | 보존 재빌드 #2 |
| --- | ---: | ---: |
| Compile Wasm app | 84.665 | 10.513 |
| Compile Wasm tests | 50.070 | 9.946 |
| Wasm browser tests | 111.353 | 11.562 |
| Compile JavaScript | 78.149 | 8.222 |
| Compile JavaScript tests | 46.128 | 8.739 |
| JavaScript browser tests | 154.451 | 10.337 |
| Compile production Wasm | 111.139 | 9.785 |
| Optimize production Wasm | 481.726 | 9.111 |
| Bundle production web | 33.612 | 11.090 |
| Assemble web distribution | 22.929 | 11.628 |

[단계별 원본 측정](evidence/frontend-cache292.json), [전체 산출물 manifest](evidence/frontend-cache292-manifest.json). Jenkins의 전용 잡 기록은 비교를 위해 보존하고 추가 실행은 비활성화했다. 운영 세 잡의 활성 상태는 유지했다.

## 운영 반영 및 복구

기존 Git 작업공간에서 `git reset --hard HEAD`, `git clean -fd`로 소스만 복원·정리한다. ignored Gradle/Kotlin/Node/빌드 산출물을 유지하며, 소스는 기존과 같이 정확한 REVISION으로 checkout한다. 첫 실행은 deleteDir로 초기화한다. 기존 20개 stage 이름, Gradle 10개 명령, --no-daemon, 테스트, production 최적화, 20분 제한과 배포 순서 정책을 바꾸지 않았다.

- 관리 Pipeline 및 Jenkins 정의 SHA-256: `5a6c97d003bf9770d2cf4da84299d5aee3e9b07c6c91693ae06c475a6a1a662a`.
- root 소유 helper 0755 SHA-256: `1e5e89fc8ca3eb4bde03c780c1cee0d3b70c6acf5269d7fccc30df4452ada4f9`.
- 기존 파일·잡 XML 백업과 readback receipt: `/opt/backups/eolssu-cache292-20261029T090615Z/`.
- 컨트롤러는 컨테이너이므로 호스트 관리 파일을 Groovy File로 읽지 못했다. 파일 교체 후 CLI로 검증한 본문을 직접 전달해 잡 정의를 갱신했고 양쪽 해시 일치를 확인했다. 작업 중 운영 잡 비활성화·실행 취소·서비스 재기동은 없었다.
- 복구 시 백업 Pipeline을 관리 파일과 잡 정의 양쪽에 복원한다. helper는 그 뒤 제거할 수 있다. 실행 중인 작업공간을 수동 삭제하지 않는다.

## 검증

실제 Git fixture 4개(캐시 bytes 보존, dirty/untracked 소스 정리, 변경·삭제된 소스 checkout, 비루트/비저장소 거부). 기존 전체 Jenkins 도구 회귀 33/33, bash 문법, 실제 컨트롤러의 운영/측정 Pipeline declarative linter 통과. 구 정리 방식은 의도한 3개 fixture에서 실패했다. 동일 소스 재빌드·테스트 결과·최종 파일 일치를 실제 실행으로 확인했다. 운영 서비스 배포는 이번 비교에서 실행하지 않았다.
