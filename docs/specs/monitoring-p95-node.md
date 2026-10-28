# 백엔드 성능 대시보드 p95와 서버 자원

- 작업: [S15P31E102-218](https://ssafy.atlassian.net/browse/S15P31E102-218)
- 상태: 제안. 담당자 사지한이 2026-10-28에 범위를 골랐고, 백엔드 담당 확인은 MR에서 받는다.
- 바탕: [모니터링 Metabase 제거와 Prometheus 도입](monitoring-prometheus.md)

## 목적

202의 백엔드 성능 대시보드(`eolssu-backend`)에서 평균에 묻히는 느린 요청과 서버 자원 추이를 본다.

## 묶음

| ID | 내용 | 의존 |
| --- | --- | --- |
| P1 | 응답 시간·DB 연결 대기 p95 | 없음 |
| P2 | 서버 CPU·메모리·디스크 | 없음 |
| P3 | 5xx 오류율 패널이 요청 없을 때 0 | 없음 |

## P1. p95

- 백엔드가 HTTP 요청(`http.server.requests`)과 DB 연결 얻기 대기(`hikaricp.connections.acquire`)의 응답 시간 분포(히스토그램 구간)를 Prometheus 형식으로 낸다. 다른 수치에는 켜지 않는다.
- 대시보드의 "API 평균 응답 시간" 패널은 주소별 평균과 p95를 함께 그린다. 최대 응답 시간 패널은 그대로 둔다.
- DB 연결 풀 행에 "DB 연결 대기 시간 (p95)" 패널을 더한다. MySQL에 붙는 `ops` 프로필에서만 값이 있다.
- 수집 요청(`/api/actuator`)은 지금처럼 뺀다.

## P2. 서버 자원

- 모니터링 Compose에 서버 수집기(Prometheus node exporter)를 더한다. 서버 루트를 **읽기 전용**으로만 붙이고, 서버 네트워크·프로세스 공간은 공유하지 않는다. 포트는 서버에도 열지 않고 Prometheus가 Docker 내부 네트워크로 읽는다.
- 대시보드에 "서버" 행을 더하고 CPU 사용률, 메모리 사용률(캐시 제외 실제 사용), 루트(`/`) 디스크 사용률 패널을 둔다. edo1은 디스크가 루트 하나이고 Docker 저장 공간도 그 안에 있다(2026-10-28 확인).
- 서버 네트워크 사용량은 이번에 보지 않는다.

## P3. 5xx 오류율

- 요청이 하나도 없으면 No data 대신 0%를 보인다. 요청이 있으면 지금과 같은 값이다.

## 바뀌지 않는 것

- 인터넷에서 점검 주소 차단(readiness만 통과)과 다른 점검 주소 비공개.
- 기존 수치 이름과 다른 패널, Prometheus 15일 보관.

## 완료 조건

- P1: 백엔드 `/api/actuator/prometheus`에 `http_server_requests_seconds_bucket`이 있다. `ops` 프로필에서 `hikaricp_connections_acquire_seconds_bucket`이 있다. 패널에서 주소별 평균과 p95가 함께 보인다.
- P2: 서버 CPU·메모리·디스크 패널에 값이 있고, 루트 디스크 사용률이 `df`와 같은 수준이다. 수집기 포트가 서버에서 열려 있지 않다.
- P3: 요청이 없을 때 5xx 오류율 패널이 0%다.

## 검증

- P1: 백엔드 테스트에 bucket 수치 확인을 더한다(HTTP). Hikari는 DB가 필요해 로컬 MySQL과 `ops` 프로필로 한 번 확인한다.
- P2·P3: 202 때처럼 로컬 Prometheus·Grafana에 수집기와 실제 백엔드를 붙여 패널 쿼리를 확인한다. 로컬 PC는 Linux가 아니라 값의 정확도는 운영에서 `df`와 비교한다.
- 운영 반영(백엔드 배포, compose 교체, 대시보드 다시 가져오기)은 사지한이 하거나 허락받고 한다.

## 이번에 하지 않는 것

- 알림, 채점 수치, 서버 네트워크 사용량, Grafana 설정 파일 관리.
