# Mattermost 서버 점검 중계의 선택과 제약

## edo2 자체 점검 확장

[S15P31E102-133](../../plans/S15P31E102-133.md)에서 같은 표·판정 체계를 edo2에 확장했다. 서버별 기대 구성을 분리해 edo1의 웹·API·MySQL 필수 컨테이너를 edo2에 요구하지 않는다. 실제 edo2는 Docker 엔진이 정상 응답하고 등록 컨테이너가 0개다. 조회 실패는 미측정, 조회한 빈 목록은 해당 없음으로 구분한다.

중계 계정의 `NoNewPrivileges=yes`를 유지하면 로컬 sudo로 권한을 올릴 수 없다. 기존 중계 계정의 nologin과 보호 설정을 유지하고 별도 잠긴 수집 계정·loopback SSH·root 소유 forced command·세 고정 sudo 명령으로 경계를 재사용했다. 실제 NNP 상태에서도 세 점검은 성공하고 셸·추가 인자·SFTP·포워딩·임의 sudo는 거부됐다. 서버는 command와 해당 토큰으로 결정하며 결과의 서버 식별자까지 대조한다.

systemd daemon은 active/running, timer는 active와 다음 예약 시각, oneshot은 실행 이력·결과·종료 코드로 구분한다. oneshot의 inactive만으로 실패라고 할 수 없으며 실행 이력 없는 success/0 기본값도 성공 증거가 아니다. 실제 백업 복구 검증 unit의 실행 이력이 없어 미검증으로 표시했다. 이 점검은 백업 작업을 실행하거나 저장 파일의 무결성을 검증하지 않는다.

edo2의 자기 공인 IP HTTPS는 자체 관측이며 외부 관측으로 부르지 않는다. 실제 Mattermost 명령·공개 callback으로 외부 흐름을 별도 검증한다. 160시간 IP 인증서에 7일 경고를 적용하면 항상 주의가 되므로 edo2는 만료 24시간 미만을 경고하고 갱신 timer도 확인한다. systemd 시각의 `:00:`이 Mattermost 이모지로 바뀌는 현상을 실제 표에서 관찰해 표 안의 시각도 inline code로 감쌌다.

## 2026-10-26 후속 관측

실행 중·검사 통과·검사 없음·측정 실패를 같은 판정으로 묶으면 장애가 아닌 설정 참고가 경보로 보인다. 검사 없음은 미검증으로, stats·용량·로그 누락은 미측정으로 구분했다. Docker의 옛 healthy 값보다 현재 실행 상태를 먼저 판단한다. stats가 실패해도 이미 읽은 inspect 정보는 버리지 않는다.

같은 날 사용자의 후속 요청으로 상태에만 이모지와 굵은 글씨를 적용했다. 정상·검사 통과는 체크, 장애는 X, 주의는 경고, 미검증·미측정은 물음표로 구분하고 판정 글자를 함께 둔다. 숫자·경로 전체를 강조하지 않고 상단 건수도 0보다 큰 항목만 강조한다. 원래의 판정과 집계 후 최종 출력 단계에서 정확한 상태 값만 변환하므로 unhealthy·실행만 확인된 상태에 체크가 붙지 않는다.

추가 화면 피드백으로 흰색 물음표를 제거하고 미검증은 빨간 ❓, 미측정은 🚫, 설정 참고는 ℹ️로 구분했다. 상태 단어가 좁은 표 열에서 한 글자씩 갈라지지 않게 word joiner와 줄바꿈 없는 공백을 사용한다. 모호한 추세 확인 대신 숫자·시간 범위·다음 확인 대상을 표시한다. 80/90% 및 30초 간격 3회는 이 프로젝트의 점검·재확인 기준이며 보편적인 장애 판정 공식이 아니다.

독립 점검에서 최근 24시간 OOM 매칭 로그를 현재 서비스 실패와 합쳐 장애로 분류하는 문제를 확인했다. 둘을 분리하고 과거 OOM은 주의로 표시한다. 매칭 로그는 하나의 사건에서 여러 줄 나올 수 있어 사건 수로 표기하지 않는다. 디스크·inode 90%도 긴급 용량 주의이며 현재 서비스 장애를 확정하지 않는다. API 404는 해당 URL 검증 실패로만 표현하고 실제 health URL·프록시 경로를 대조한다.

현재 세 명령은 인프라 자원·컨테이너·기본 응답의 수시 점검으로 적합하다. 지속적인 서비스 운영 판단에는 요청 지연·요청량·오류율·포화도를 시간별로 관찰해야 하며 현재 명령의 스냅샷은 이를 대신하지 않는다. [Google SRE의 네 가지 신호](https://sre.google/sre-book/monitoring-distributed-systems/)

실행 상태와 healthcheck 통과는 별개이며 Docker healthy는 정의된 검사에 한정된다. HTTP 준비 응답과 실제 사용자 기능 검사도 구분한다. [Docker HEALTHCHECK](https://docs.docker.com/reference/dockerfile/#healthcheck), [준비 상태의 의미](https://kubernetes.io/docs/concepts/workloads/pods/probes/#readiness-probe)

사용자의 API 미검증 해소 요청으로 404 원인을 실제 운영 응답·소스와 대조했다. Nginx는 `/api/`를 보존해 전달하고 백엔드 JSON 404가 반환됐으며, Actuator 의존성·health 엔드포인트가 없고 DB 프로필도 비활성이었다. Actuator의 readiness 그룹을 `/api/actuator/health/readiness`에 추가하고 운영 `ops`에서 readinessState와 DB를 함께 확인한다. 기존 DB의 자동 DDL 실행을 피하기 위해 Flyway·SQL 초기화를 끄고 기본 개발 모드·마이그레이션용 db 프로필과 구분한다. health만 읽기 전용으로 노출하고 상세·컴포넌트·기타 관리 API·JMX는 공개하지 않는다. [Spring Boot health·probes](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.endpoints.kubernetes-probes)

실제 HTTP 격리 검사에서 DB 연결 불가 시 readiness 503/DOWN, liveness 200/UP·Flyway Bean 부재를 확인했다. 운영과 같은 JRE의 임시 후보를 기존 DB 네트워크에 붙여 ops readiness 200/UP과 env·beans·configprops 404를 확인한 후 후보·임시 비밀 파일을 정리했다. 업무 데이터·스키마를 변경하는 테스트는 운영 DB에서 수행하지 않았다.

기존 로그 집계는 inspect의 LogPath가 비어 있는 local 드라이버를 처리하지 못했다. 실제 `local-logs/container.log*` 파일 크기를 확인해 주·회전 파일 메타데이터만 합산했다. [Docker local 드라이버](https://docs.docker.com/engine/logging/drivers/local/)

Jenkins bind의 전체 사용량은 실제 약 5.3GiB·8.5초로 기존 3초 제한을 넘었다. 요청 내 중복 측정을 제거하고 경로 12초·총 bind 25초를 상한으로 두었다. 무제한 탐색이나 상시 캐시는 추가하지 않았다. 시간 초과 시 부분 용량을 전체로 표시하지 않는다.

n8n은 Docker healthcheck가 없어도 고정 loopback readiness에서 HTTP 200·status ok를 반환했다. 이를 실제 준비 상태로 확인했다. API health는 HTTP 200만으로 정상이라고 할 수 없고 기대 JSON status까지 검증해야 한다. [n8n 준비 검사 근거](https://github.com/n8n-io/n8n-docs/blob/main/docs/deploy/host-n8n/keep-n8n-running/monitor-n8n.md)

아래는 초기 선택·제약 기록이며 현재 실행·판정 안내는 [운영 안내](../../../infra/edo-check/README.md)를 따른다.

2026-10-23 [S15P31E102-95](../../plans/S15P31E102-95.md)의 실제 edo1·edo2와 Mattermost 11.7.2 화면을 확인한 결과다. 사용자는 이 대화에서 도메인 없이 edo2 중계 배포, CI/CD 채널만 실행, 전용 계정·키·제한 권한을 승인했다. 실행·복구 명령은 [운영 안내](../../../infra/edo-check/README.md)에 있다.

## 선택한 구조

Mattermost POST → edo2 Nginx HTTPS → 비특권 Python 중계 → edo1의 전용 SSH 강제 명령 → root 소유 읽기 전용 수집기 → Mattermost callback 순서다. 요청은 즉시 개인 접수 안내를 반환하고 수집 결과만 같은 채널에 공개한다. 이 방식은 빠른 명령 응답과 시간이 걸리는 점검을 분리한다. [Mattermost 문서](https://docs.mattermost.com/developers/integrate/slash-commands/custom)

중계는 Python 3.12 표준 라이브러리·systemd를 사용한다. 이 작업에는 별도 큐·DB·Docker 실행 환경이 필요하지 않아 관리 대상을 늘리지 않았다. 중계가 관리자 SSH 키나 Docker 소켓을 직접 갖지 않도록 edo1에 별도 강제 명령 경계를 둔다. 컨테이너 내부에 임의 명령을 실행하거나 환경변수·로그 본문을 채널로 보내지 않는다. 키가 유출되더라도 edo2 출발지·고정 점검·시간 제한이 적용되지만 허용된 점검 정보 노출 위험은 남는다.

도메인이 없어서 현재 공인 IPv4에 Let’s Encrypt IP 인증서를 발급했다. IP 인증서는 160시간이므로 자동 갱신·Nginx reload를 함께 구성했고 실제 staging 갱신 모의 검사를 통과했다. [IP 인증서 안내](https://letsencrypt.org/2026/01/15/6day-and-ip-general-availability), [Certbot 지원 안내](https://letsencrypt.org/2026/03/11/shorter-certs-certbot)

## 실제 적용 시 주의점

| 확인한 사실 | 적용·해석 |
| --- | --- |
| UFW에 443이 허용돼도 Lightsail에 규칙이 없으면 외부 연결은 시간 초과 | 양쪽 22·80·443을 확인한다. 80은 ACME challenge만 제공한다. 외부 IPv4 HTTPS와 실제 Mattermost 호출을 함께 검증했다. |
| 초기 공인 IPv4는 정적 IP가 아니었음 | 같은 날 정적 IP 15.165.144.7을 연결하고 인증서·명령 URL·Nginx·SSH 출발지 제한을 함께 전환했다. |
| Docker가 인식하는 호스트 RAM·파일시스템과 컨테이너 제한은 다름 | 공유 가능한 전체·여유, 실제 사용량, 개별 제한을 구분한다. 현재 5개 컨테이너의 CPU·RAM·쓰기 영역 제한은 미설정이다. |
| 이미지·캐시·공유 볼륨은 단순 합산 시 중복될 수 있음 | 이미지 계층 전체 크기와 고유 볼륨을 집계하고 분류별 값을 유지한다. 누적 IO는 디스크 점유량으로 더하지 않는다. |
| running·Docker healthy·실제 HTTP 준비 상태는 서로 다른 검사 | frontend·mysql만 Docker health가 설정돼 있다. Grafana·Metabase는 별도 HTTP 본문을 확인하며 API health 404는 미설정이다. |
| callback 전달까지 실패하면 채널에 실패 메시지도 남길 수 없음 | 요청 ID·모드를 journal에 남긴다. 모호한 전송 실패 뒤 자동 재시도로 중복 보고를 만들지 않는다. |

이 결과는 시점 점검이며 장기 성능 추세·최대 사용량·모든 장애를 검출하는 상시 모니터링을 뜻하지 않는다. 미래 인증서 갱신과 실제 외부 IPv6 연결은 실행 시점의 환경에 영향을 받는다. 판정 임계값과 수집 한계는 운영 안내에 명시했다.

Mattermost는 표 셀의 HTML 줄바꿈을 지원하지 않는다. 2026-10-23 후속 사용자 요청에 따라 서버 상세는 같은 2열 표에 포함하고, 컨테이너는 자원·상태와 저장소·실행 상세 표로 나눈다. 확인 사항은 별도 표로 표시한다. 표 수를 줄이려고 의미가 다른 내용을 강제로 합치지 않는다. 수집 시각은 인라인 코드로 감싸 `:숫자:` 조각의 이모지 변환을 피한다. [서식 공식 안내](https://docs.mattermost.com/messaging/formatting-text)

사용자 승인 후 같은 날 16:41 KST 운영 중계에 반영했다. MM 채널 입력창에서 세 명령을 직접 전송해 서버 표 1개, 컨테이너 본문 표 2개와 확인 사항 표, health 결과와 확인 사항 표를 확인했다. 세 공개 보고 모두 수집 시각이 이모지 없이 code로 렌더링됐다. 배포 백업·해시·요청 ID·공개 결과 링크는 [작업 기록](../../plans/S15P31E102-95.md)에 남겼다.

## 2026-10-29 운영 구성 변화와 판정 보완

[S15P31E102-271](../../plans/S15P31E102-271.md)에서 실제 운영 수집기를 대조했다. 저장소에서는 Metabase 검사를 제거했지만 edo1의 구형 수집기에는 검사가 남아 Prometheus가 사용하는 3001 포트의 `/api/health`를 Metabase 장애로 보고했다. edo2에도 삭제 전 공통 코드가 남아 있었다. 저장소 변경과 실제 수집기 배포 여부를 별도로 검증해야 한다.

AI의 Docker healthy는 현재 8000 TCP 연결 성공만 검사한다. 분석 HTTP 준비나 추론 성공으로 확대 해석하지 않고 미검증 범위를 표시한다. Prometheus 3001의 준비 응답과 node·eolssu-backend 수집 성공·최근 90초 이내를 별도로 확인한다. 90초는 15초 수집 주기에 여유를 둔 이 수동 점검의 기준이다.

edo2에서 백업 상태·복구 검증 서비스가 실패했고 실제 복구 결과 파일도 failed였다. 상태 초기화로 숨기지 않고, backup metadata 최신성·암호문 존재·마지막 복구 결과를 분리했다. db 90분·다른 종류 27시간·복구 8일 기준은 기존 `infra/backup/backup.py`의 status 함수와 같다. 이 검사는 암호문 내용·복호화 키를 읽거나 대규모 해시 재검사·복구를 실행하지 않는다.
