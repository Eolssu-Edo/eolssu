# 모니터링 Metabase 제거와 Prometheus 도입

- 작업: [S15P31E102-202](https://ssafy.atlassian.net/browse/S15P31E102-202)
- 상태: 제안. 담당자 사지한이 2026-10-28에 결정했고, 수치 주소를 숨기는 방법(M2)도 같은 날 다시 확인해 유지했다. 백엔드 담당 합의는 MR에서 받는다.
- 관련: [채점 품질 통계 대시보드](scoring-quality-dashboard.md), [infra/monitoring/README.md](../../infra/monitoring/README.md)

## 목적

모니터링 화면을 Grafana 하나로 정리한다. Grafana가 운영 MySQL(서비스 데이터)과 Prometheus(백엔드 수치) 두 데이터 소스를 읽는다. Metabase는 내린다.

## 기능 묶음

세 묶음은 따로 확인할 수 있다. M3만 M2에 의존한다.

| ID | 내용 | 의존 |
| --- | --- | --- |
| M1 | Metabase 제거와 곡별 통계의 Grafana 이전 | 없음 |
| M2 | 백엔드 수치 주소 열기와 인터넷 차단 | 없음 |
| M3 | Prometheus 수집과 백엔드 성능 대시보드 | M2 |

## M1. Metabase 제거

- Metabase의 곡별·악기별 연습 통계 SQL(곡, 악기, 연습 횟수, 평균 점수)을 운영 현황 대시보드의 표 패널로 옮긴다. 기간은 연습 시작 시각(`practice_sessions.created_at`) 기준이고 거르기는 없다. 처음에는 채점 품질 대시보드처럼 거르기를 두려 했으나, 운영 현황 대시보드에는 거르기가 없어 그대로 두었다.
- `infra/monitoring/compose.yml`, `.env.example`, README에서 Metabase를 뺀다. 테이블 보기는 Grafana Explore와 표 패널로 한다고 README에 적는다.
- 운영 서버에서 Metabase 컨테이너를 내리고, 운영 MySQL의 `metabase` 계정과 `metabase` DB를 백업 없이 지운다. 실행 직전에 사지한의 허락을 받는다.
- Grafana 계정 권한은 바꾸지 않는다. 곡별 통계가 읽는 `practice_sessions`·`evaluations`·`songs`·`instruments`는 S15P31E102-199에서 이미 부여됐다.

## M2. 수치 주소

- 백엔드가 8080에서 `/api/actuator/prometheus`를 Prometheus 형식으로 응답한다. 수치는 Spring 기본만 쓴다(HTTP 요청 응답 시간·상태 코드, JVM 메모리·GC·스레드, DB 연결 풀). 코드에 수치를 따로 넣지 않는다.
- 백엔드로 넘기는 모든 Nginx 설정에서 `/api/actuator/` 아래는 `/api/actuator/health/readiness`만 통과시키고 나머지는 404를 돌려준다. 대상은 `infra/runtime/nginx.conf`, `infra/runtime/nginx.tls.conf.template`, `infra/nginx/default.conf`다.

인터넷에서 보이는 결과:

| 주소 | 지금 | 바꾼 뒤 |
| --- | --- | --- |
| `/api/actuator/health/readiness` | `{"status":"UP"}` | 그대로 |
| `/api/actuator/health`, `/api/actuator/health/liveness` | 보임 | 404 |
| `/api/actuator/prometheus` | 없음 | 404 |

서버 안에서는 지금처럼 모두 열려 있다. Docker 상태 검사는 `127.0.0.1:8080`, Prometheus는 `backend:8080`으로 Nginx를 거치지 않는다.

## M3. Prometheus와 대시보드

- `infra/monitoring/compose.yml`에 Prometheus를 더한다. 운영 스택 내부 네트워크에서 `backend:8080/api/actuator/prometheus`를 읽고, 데이터는 15일 보관한다. 포트는 서버 밖에 열지 않는다.
- Grafana에 Prometheus 데이터 소스를 더하고, 백엔드 성능 대시보드 JSON을 저장소에 둔다. 패널은 API 응답 시간, 상태 코드별 요청 수(오류율), JVM 메모리, DB 연결 풀 사용량이다.
- 서버에 컨테이너를 띄우는 일은 사지한의 허락을 받고 한다.

## 바뀌지 않는 것

- readiness 응답 내용과 Docker 상태 검사, Mattermost 점검 중계의 API 확인.
- `env`·`beans`·`configprops` 같은 다른 점검 주소는 계속 열지 않는다(`BackendApplicationTests`).
- 운영 현황·채점 품질 대시보드와 Grafana 계정 권한.

## 완료 조건

- M1: Grafana에서 곡별·악기별 연습 통계를 볼 수 있다. 저장소와 운영 서버에 Metabase 컨테이너·계정·DB가 없다.
- M2: 인터넷에서 위 표대로 응답한다. 백엔드 테스트가 prometheus 주소의 응답과 다른 점검 주소의 404를 확인한다.
- M3: Grafana에서 API 응답 시간·오류율·JVM 메모리·DB 연결 풀 그래프를 볼 수 있다. Prometheus가 서버 밖에서 접속되지 않는다.

## 검증

- M2 백엔드 동작은 백엔드 테스트로, Nginx 차단은 로컬 Nginx 컨테이너에 세 설정을 띄워 `curl`로 확인한다.
- M1·M3 대시보드는 196과 같이 로컬 Grafana·MySQL·Prometheus에서 Import와 패널 쿼리를 확인한다.
- 운영 확인(인터넷에서 404, Grafana 그래프, Metabase 제거)은 배포 뒤 사지한이 하거나 허락받고 한다.

## 이번에 하지 않는 것

- 채점 요청 수 같은 우리 수치, 알림, 관리 포트 분리.

## 남은 결정

- M2에서 버린 대안: 점검 주소를 별도 포트로 옮기는 방법. 구조적으로는 더 안전하지만 밖에서 쓰는 health 주소가 바뀌어 compose·edo-check·테스트·README를 함께 고치고 밖에서 확인할 길을 새로 만들어야 해서 택하지 않았다.
- 백엔드 담당이 수치 주소 열기에 동의하는지.
