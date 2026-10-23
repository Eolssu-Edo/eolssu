# Mattermost 서버 점검 중계의 선택과 제약

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

Mattermost는 표 셀의 HTML 줄바꿈을 지원하지 않는다. 핵심 표를 줄이고 상세를 문단·목록으로 분리해 명령마다 표 하나를 유지했다. [서식 공식 안내](https://docs.mattermost.com/messaging/formatting-text)
