# 최신 운영 구성의 수동 서버 점검

S15P31E102-271. 2026-10-29 사용자 요청에 따라 edo1·edo2의 기존 여섯 Mattermost 점검 명령을 갱신한다. 명령 이름·URL·토큰·채널 제한·읽기 전용 권한은 유지한다.

- edo1의 필수 대상은 runtime frontend/backend/mysql/ai와 monitoring grafana/prometheus/node-exporter다. 누락을 장애로 표시한다. AI의 현재 Docker healthcheck는 TCP 연결 확인으로, healthy여도 분석 HTTP 준비/추론 검증은 미검증으로 구분한다. AI 앱이나 healthcheck를 변경하지 않는다.
- Prometheus는 loopback 3001의 /-/ready 응답과 /api/v1/targets를 검사한다. node와 eolssu-backend job이 모두 존재하고 up이며 최근 90초 내 수집됐는지 확인한다. 정상/실패/미검증/미측정을 구분하며 외부 주소를 새로 공개하지 않는다.
- edo1의 Docker·SSH 서비스와 기존 백업 timer/oneshot 상태를 확인한다. edo2의 기존 상태 검사에 백업 종류별 snapshot_at·암호문 존재 및 restore-last.json의 status·완료 시각을 추가한다. DB는 90분, 다른 백업은 27시간, 복구 검증은 8일의 기존 백업 기준을 따른다. 백업 데이터·키·로그 본문을 공개하거나 복원/암호문 해시 재계산을 실행하지 않는다. 메타데이터 최신성과 실제 복원 성공을 구분한다.
- 서버 자원·컨테이너 자원은 기존 동적 수집을 유지한다. 이미 제거된 Metabase 검사가 남은 운영 수집기를 최신 공통 코드로 동기화한다. 실제 백업 실패를 숨기지 않는다.
- 검증: 고정 데이터 회귀 검사, 기존 전체 검사, 두 서버의 제한된 실제 SSH 수집 및 외부 HTTP/TLS 검사. 실제 서비스 중단이나 복구 작업 실행은 제외한다.
