# Mattermost edo1·edo2 점검 중계

Mattermost의 Team 이도 (CI/CD,서버점검) 채널에서 대상 호스트의 명령을 실행한다. [통합 작업](../../docs/plans/S15P31E102-701.md) · [edo1 최초 구성](../../docs/plans/S15P31E102-95.md) · [edo2 최초 구성](../../docs/plans/S15P31E102-133.md)

| 명령 | 실행 경로·결과 |
| --- | --- |
| `/edo1` | 전용 SSH로 edo1의 서비스·서버·컨테이너를 수집하고 edo2에서 공개 HTTPS·인증서를 검사 |
| `/edo2` | 전용 loopback SSH로 edo2의 서비스·서버·컨테이너를 수집하고 자체 HTTPS·IP 인증서를 검사 |

두 명령의 Mattermost Request URL은 `https://15.165.144.7/integrations/mattermost/commands`, 메서드는 POST다. 채널 ID `7pqaof6zkt8rdbqbdjjsfpfscw`에서만 허용한다. 명령마다 다른 토큰으로 대상을 결정한다. form의 target·mode 덮어쓰기와 명령 인자는 거부한다. 접수와 거부 안내는 실행자에게 보인다. 점검 결과는 요청의 `response_url`에 `in_channel`로 전송해 같은 채널에 게시한다.

## QA 앱 로그 수집

`/applog`는 위와 같은 Request URL을 사용한다. 명령을 실행하면 Windows 휴대폰 중계의 기존 역방향 SSH 포트 `127.0.0.1:27890`으로 연결한다. 시작할 때 확정한 순번까지 로그를 작은 묶음으로 받아 SQLite에 확정한 뒤 앱에 삭제 확인을 보낸다. 저장이나 응답이 끊기면 결과에 서버 저장 순번과 앱 삭제 확인 순번을 표시한다. 다시 실행하면 앱이 보관한 로그를 기준으로 이어가며 이미 저장된 같은 기록은 중복으로 남기지 않는다. 접수와 요약은 명령을 실행한 사람에게만 보인다.

활성화하려면 Windows QA 중계와 QA APK를 먼저 적용한다. EDO2의 `/etc/edo-check/relay.json`에 다음 객체를 추가하고, Mattermost에서 `/applog`의 토큰을 등록한다. 토큰은 기존 점검 토큰과 서로 달라야 한다. `tunnel_token`은 Windows QA 중계의 수집 전용 토큰과 일치해야 한다.

```json
{
  "applog": {
    "token": "REPLACE_WITH_APPLOG_MATTERMOST_TOKEN",
    "tunnel_token": "REPLACE_WITH_APPLOG_WINDOWS_BRIDGE_TOKEN",
    "team_id": "REPLACE_WITH_APPROVED_TEAM_ID",
    "channel_ids": ["REPLACE_WITH_APPROVED_CHANNEL_ID"],
    "user_ids": ["REPLACE_WITH_APPROVED_USER_ID"]
  }
}
```

서비스 유닛을 반영하고 `sudo systemctl daemon-reload`를 실행한 뒤 `sudo systemctl restart edo-check`로 적용한다. 앱 로그 수집만 중지하려면 설정에서 `applog` 객체를 제거한 뒤 같은 서비스를 재시작한다.

원문은 `/var/log/eolssu-qa-perf/events.sqlite3`에 저장한다. 서비스는 시작할 때와 매시간 7일을 지난 수집 자료를 정리한다. 요청 ID로 조회할 때는 SSH에 접속한 뒤 아래 쿼리의 값을 교체한다.

```sql
SELECT e.seq, e.body
FROM applog_requests AS r
JOIN applog_events AS e ON e.stream_id = r.stream_id
    AND e.seq > r.after AND e.seq <= r.saved
WHERE r.request_id = 'REPLACE_WITH_REQUEST_ID'
ORDER BY e.seq;
```

## 구성과 권한

- edo2: Ubuntu 24.04, Python 3.12 표준 라이브러리, Ubuntu Nginx, 공식 Certbot snap 5.8.0. 런타임 Python 외부 패키지는 없다.
- `edo-check.service`는 로그인 불가 `edo-check` 사용자로 실행한다. 앱은 `127.0.0.1:8765`만 수신하고 Nginx만 443을 공개한다. `/healthz`는 중계 프로세스의 liveness이며 edo1·Mattermost 응답까지 보장하지 않는다.
- 비밀 설정은 edo2의 `/etc/edo-check/relay.json`에 root:edo-check, 640으로 둔다. [예시](relay.example.json)는 토큰 자리표시자만 포함한다. 실제 토큰·SSH 개인 키·인증서 개인 키를 저장소에 넣지 않는다.
- edo2 개인 키는 `/var/lib/edo-check/ssh/id_ed25519`, known_hosts는 같은 디렉터리에 있으며 edo-check 소유 600이다. edo1의 호스트 키는 기존 관리 SSH로 확인해 고정한다. StrictHostKeyChecking을 끄지 않는다.
- edo1의 `/opt/edo-check/collector.py`와 `ssh-dispatch.sh`는 root 소유다. edo1 전용 계정은 Docker 그룹에 넣지 않는다. root 소유 authorized_keys에서 edo2 IPv4만 허용하고 `restrict`와 강제 dispatch로 일반 셸·SFTP·포트 포워딩·PTY를 차단한다.
- sudoers는 [세 고정 명령](edo-check.sudoers)만 허용한다. 80초가 지나면 TERM, 추가 5초 후 KILL로 수집기와 하위 작업을 제한한다. 관리자 계정·키와 앱 컨테이너는 사용·수정하지 않는다.
- edo2 자체 수집은 별도 `edo2-check` 계정으로 loopback SSH를 사용한다. 비밀번호는 잠그고 root 소유 `.ssh/authorized_keys`에 `from="127.0.0.1",restrict,command="/opt/edo-check/ssh-dispatch-edo2.sh"`를 지정한다. 공개 키 파일은 root:edo2-check 640, `.ssh`는 root:edo2-check 750으로 읽기만 가능하게 한다. 전용 개인 키는 기존 edo-check 소유 600, 호스트 키 파일은 root:edo-check 640이다. [edo2 dispatch](ssh-dispatch-edo2.sh)와 [sudoers](edo2-check.sudoers)는 profile `edo2`의 세 명령만 허용한다. 새 계정의 셸은 강제 명령 실행에 필요한 `/bin/sh`이지만 일반 셸·SFTP·포워딩은 키 옵션과 dispatch로 거부한다. 기존 중계 계정의 nologin·NoNewPrivileges는 유지한다.
- `targets` 설정은 서버별 SSH 주소·전용 키·known_hosts·HTTPS 대상을 분리한다. edo2는 `edo2-check@127.0.0.1`만 사용한다. 수집 결과의 target·mode가 요청과 다르면 공개 결과를 정상으로 표시하지 않고 수집 실패로 보고한다. 기존 edo1 수집기의 target 없는 응답은 edo1 요청에만 호환한다.

## 요청 처리와 제한

명령별 토큰을 상수 시간 비교하고 채널을 확인한 후 작업을 접수한다. 요청은 8KiB 이하, 중복 form 필드는 거부한다. 동일 `response_url` 요청은 30분간 중복 실행하지 않고, 같은 채널·같은 서버는 10초 간격으로 제한한다. 호스트 하나의 점검이 진행 중이면 같은 호스트의 중복 실행을 거부한다. 점검 작업은 전용 작업자 풀을 최대 세 개 사용하며 `/applog`는 별도 작업자 풀에서 처리한다. 접수 상태는 중계 프로세스 메모리에 있으며 재시작하면 초기화된다.

요청 하나에서 `health`, `server`, `containers`를 같은 호스트에 순차 실행한다. 각 수집기의 80초와 SSH의 90초 상한은 고정 명령마다 적용한다. 한 모드의 수집 실패나 시간 초과는 해당 영역의 미측정으로 기록한다. 다른 영역의 결과는 같은 요청 ID로 함께 게시한다.

응답 URL은 고정 Mattermost HTTPS 호스트·443과 `/hooks/commands/<26자리 ID>`만 허용한다. 쿼리·userinfo·다른 경로와 redirect를 허용하지 않는다. DNS의 모든 주소가 공인 주소인지 확인하고 검증한 주소에 연결하면서 TLS 호스트 검증을 유지한다. 게시물마다 같은 응답 URL에 차례대로 전송한다. 한 게시물 전송이 실패하면 이후 게시물을 계속 보내며 journal에 요청 ID와 게시물 번호를 기록한다. 중복 게시를 피하도록 자동 재전송은 꺼져 있다. 콜백 URL·토큰·개인 키는 로그에 남기지 않는다. [Mattermost 지연·복수 응답](https://docs.mattermost.com/developers/integrate/slash-commands/custom#delayed-and-multiple-responses)

## 지표 해석

첫 게시물에서 호스트, 수집 완료 시각·소요 시간·요청 ID와 영역별 판정 건수를 확인한다. `장애`는 중지·unhealthy·명시적인 실패 응답, `주의`는 용량·메모리 임계값, `미검증`은 실행만 확인되고 준비 상태 검사가 없는 경우, `미측정`은 값을 읽지 못한 경우다. 상한이 없는 메모리는 참고로 표시한다. edo1 Docker 저장공간과 Jenkins 데이터에는 최근 관측값을 우선 사용한다. 다른 값은 명령 실행 때 수집한다.

| 표 묶음 | 먼저 볼 곳 | 의미 |
| --- | --- | --- |
| 상태 | 서비스 · 호스트 · 백업 | 서비스 판정과 근거, 시스템 데몬과 보안 설정, 백업 최신성과 복구 검증을 각각 확인한다. |
| 서버 | 자원 · 저장공간 | CPU·RAM·시스템 실패·OOM과 파일시스템·inode·Docker 저장공간을 확인한다. |
| 컨테이너 | 상태 · 자원 · 데이터 저장공간 · 실행·포트 · 통신량·이미지 | 준비 검사와 CPU·RAM·쓰기 계층·로그를 각 표에서 확인한다. 원래 이름은 실행 상세에서 확인한다. |

edo1 상태는 API 라우팅 슬롯과 해당 컨테이너 상태를 한 줄로 표시한다. 컨테이너 점검은 수집·실행 개수와 CPU·RAM 상위 대상을 한 줄로 표시한다. 장애·주의·미측정·미검증 순서로 먼저 확인할 항목을 다섯 건까지 표시한다. 게시물은 최대 14,000자, 요청 하나에 최대 다섯 건이다. 후속 게시물은 첫 게시물과 같은 요청 ID를 사용한다. [메시지 스펙](../../docs/specs/mattermost-inspection-messages.md)

표 본문의 값 전체를 굵게 하지 않고 제목·구분·상태 판정을 강조한다. 상태는 ✅ 정상·검사 통과, ❌ 장애, ⚠️ 주의, ❓ 미검증, 🚫 미측정, ℹ️ 설정 참고로 구분하며 텍스트를 함께 표시한다. 접수 중은 ⏳, 수집 실패도 🚫로 표시해 서비스 장애와 구분한다. 단순 실행 중·검사 시작 중·과거 healthy에는 체크를 붙이지 않는다. 상단 건수는 0보다 큰 항목만 이모지와 굵은 글씨로 강조한다. 좁은 상태 열에서 한글이 한 글자씩 갈라지지 않도록 상태 단어에 word joiner, 아이콘 뒤에 줄바꿈 없는 공백을 사용한다. 컨테이너 이름은 웹·API·배포 등 역할로 표시하고 실행 상세에 원래 식별자를 보존한다. 로그·볼륨·bind·쓰기 계층을 구분하고 저장소마다 한 행을 사용한다. 누락은 0으로 치환하지 않으며 같은 원인을 중복 집계하지 않는다.

- CPU는 Docker의 코어 합산 기준으로 100%를 넘을 수 있다. 서버 CPU는 200ms 표본이고 Load는 1·5·15분 평균이다. 장기 추세나 피크를 의미하지 않는다.
- 서버 RAM 사용량은 전체에서 MemAvailable을 뺀 값이다. 컨테이너 RAM은 Docker CLI와 같은 inactive file cache 제외 기준이다. 설정하지 않은 제한은 `미설정`, 수집하지 못한 값은 `확인 불가`로 표시한다. 컨테이너 합계는 서버 전체 사용량과 같지 않다.
- 파일시스템 용량과 Docker 저장공간을 구분한다. Docker Root Dir의 파일시스템 전체·여유는 호스트와 공유하는 용량이며 예약량이 아니다. 컨테이너 쓰기 계층의 `StorageOpt.size`가 없으면 제한 미설정으로 표시하며 로그·볼륨은 그 제한의 대상이 아니다. 서버 점검은 Docker가 계산한 점유량과 회수 가능량을 표시한다. 저장공간을 읽지 못해도 Docker 상태와 RAM을 표시한다. 컨테이너 점검은 볼륨 사용량을 따로 조회한다. 이미지·캐시 등 분류 사이의 공유 공간 때문에 각 분류를 합해 물리 점유량으로 해석하면 안 된다.
- 컨테이너 쓰기 계층, 회전 파일을 포함한 로컬 로그, 볼륨·bind mount를 구분한다. 공유 볼륨은 `공유`라고 표시하며 컨테이너 표의 저장소 값을 서버 전체로 합산하지 않는다. bind는 요청 내 같은 경로를 한 번만 `nice du -sx`로 읽는다. Jenkins 관측과 직접 수집의 한도는 [edo1 저장공간 관측](#edo1-저장공간-관측)을 따른다. 저장소 읽기 실패·경로별 시간 제한·전체 예산 소진을 구분한다. node-exporter의 읽기 전용 호스트 루트는 서버 파일시스템 관측용으로 표시한다. 전체 수집 80초·SSH 90초 상한은 유지한다. `json-file`과 `local` 로그의 주·회전 파일 크기만 읽으며 내용은 열지 않는다. local은 Docker Root Dir 아래 유효한 64자리 컨테이너 ID의 `local-logs/container.log*`를 읽는다. edo1의 고정 syslog 태그는 로컬 rsyslog 일별 파일의 메타데이터를 읽고, 백엔드 양 슬롯은 API 공용 로그로 표시한다. 심볼릭 링크를 제외하고 100파일을 넘거나 경로가 없으면 미측정이다.
- NET과 블록 I/O는 별도의 누적 전송량이다. Docker 블록 I/O 필드가 없거나 null이면 확인된 cgroup v2 카운터를 읽고, 읽을 수 있는 빈 기록은 `기록 없음`으로 표시한다. 메모리·디스크·inode 80% 이상은 주의 항목으로 알리고, 디스크·inode 90% 이상은 긴급 용량 점검이 필요한 주의로 표시한다. 용량만으로 서비스 장애를 확정하지 않는다. 이는 이 프로젝트의 기본 기준이며 보편적인 장애 임계값은 아니다.
- Load는 CPU 사용률과 다르며 실행 가능 작업과 중단 불가 대기의 평균이다. 5분 또는 15분 값이 코어 수를 넘으면 컨테이너 CPU 상위부터 보고, CPU가 낮으면 호스트 I/O 대기를 확인한다. 이는 수동 확인 기준으로 자동 장애 건수에 넣지 않는다. CPU는 0.2초 표본이므로 한 번의 값만으로 지속성을 판정하지 않는다. RAM 80% 이상 등 자원 주의 시 사용자가 30초 간격 3회 재실행해 값을 비교하도록 안내하며 명령이 자동 대기·반복하지 않는다.
- 실패 서비스는 현재 systemd 실패 상태이며 1개 이상이면 `systemctl --failed`로 대상을 확인한다. OOM은 최근 24시간 커널에서 매칭된 로그 줄 수이며 사건 건수가 아니다. 과거 기록은 주의로 구분해 시각·대상을 현재 RAM·한도와 비교한다. Docker OOM 플래그와 재시작 횟수도 최근 24시간 발생 횟수를 뜻하지 않는다.
- frontend·backend·mysql·ai와 Grafana·Prometheus·node-exporter의 실제 컨테이너 이름을 필수 대상으로 확인한다. 중지·일시정지를 예전 healthy 값으로 정상 판정하지 않는다. health 모드에서는 inspect만 수집하며 stats 실패가 실행 정보까지 지우지 않도록 분리한다. API `/api/actuator/health/readiness`의 404·HTML 200·예상하지 않은 JSON은 미검증이며 `status=UP`만 정상, 명시적 `DOWN/OUT_OF_SERVICE`는 장애다. 운영 API readiness는 애플리케이션 준비 상태와 기존 MySQL 연결을 함께 검사하며 자세한 DB 정보는 공개하지 않는다. 상태·컨테이너 점검 모두 Grafana의 loopback `/api/health`와 n8n의 `127.0.0.1:5688/healthz/readiness`에서 상태·본문을 확인한다. 컨테이너 점검의 node-exporter 행에는 Prometheus의 `node` 수집 상태와 시각도 표시한다. Docker 검사가 없어도 해당 HTTP 준비 검사가 통과하면 중복된 미검증 행을 표시하지 않는다. Docker healthy 자체는 설정된 healthcheck의 통과이며 별도 준비 검사의 범위를 보장하지 않는다. 외부 검사는 edo2에서 수행한다.

edo1 상태 점검은 이전 배포용 블로그 편집기 보관본 중 Docker inspect가 정상 종료를 확인한 항목을 서비스 검사에서 제외한다. 컨테이너 인벤토리에서는 계속 확인할 수 있다. [판정 조건과 재현 기록](../../docs/plans/S15P31E102-662.md)

서버별 통합 명령은 수시 인프라 점검이다. 장기 요청량·오류율·p95/p99 지연·I/O 지연, 실제 사용자 업무 기능은 실행하지 않는다. 백업 복구는 마지막 검증 결과 파일만 읽으며 이 점검에서 복구를 실행하지 않는다. 자원과 기본 응답이 정상이어도 이를 전체 서비스 정상으로 확대 해석하지 않는다. 상시 지표와 업무 기능 검증의 추가 여부는 별도 요구 범위로 정한다.

### edo2의 기대 구성

edo2는 Docker 엔진이 활성화된 서버다. 2026-11-09 점검에서는 MySQL 복제본과 대기 스택 컨테이너 일곱 개를 확인했다. 상태 표는 관측된 복제본·대기 API의 Docker 검사 결과를 표시한다. 복제 지연과 장애 조치 확인은 별도다. KVM이 없는 edo2의 Android 실행 실험은 폐기했고, 2026-10-29 사용자 요청으로 컨테이너·Android 이미지 2개·볼륨 2개·컨테이너 로그 및 293의 점검 예외를 제거했다. 삭제·보존·검증 증거는 [294 기록](../../docs/plans/S15P31E102-294.md)을 따른다. 293 문서는 당시 상태의 이력으로 보존한다.

빈 목록이 수집됐으면 `해당 없음`/`컨테이너 없음`으로, 엔진·socket 조회 실패는 `미측정`으로 구분한다. 컨테이너 이름·종료 코드에 따른 중지 예외는 없다. edo1의 필수 컨테이너를 edo2에 적용하지 않는다. MySQL·Jenkins·Grafana·Nginx·JRE 이미지 5개는 주기적 격리 백업 복원에 필요하므로 그 이미지로 실행 중인 컨테이너가 없어도 보존한다.

공간을 정리할 때는 정확한 자원 ID·Compose 소유·다른 컨테이너 참조를 먼저 확인한다. `docker system prune -a --volumes`처럼 범위가 넓은 정리를 사용하지 않는다. 백업 암호문·키·운영/복구 설정은 보존한다. APT 캐시·과거 작업 설정은 용도·다음 실행 영향을 확인해 별도 승인된 대상만 정리한다.

health는 nginx·edo-check·docker·ssh daemon, Certbot 갱신·백업 status/prune/restore-test timer, backup status/restore-test oneshot 이력과 Nginx 구성·UFW·127.0.0.1:8765 `/healthz`를 검사한다. timer는 active와 다음 실행 예약, oneshot은 실행 시각·결과·종료 코드를 확인한다. inactive는 oneshot 성공 후 정상 대기일 수 있다. 실행 이력이 없거나 이번 실행 중이면 `미검증`이며 작업을 자동 실행하지 않는다. systemd 실행 성공은 백업 파일의 무결성·복구 성공을 보증하지 않는다.

edo2 대기 웹에 Docker 준비 검사가 미설정이면 대기 웹의 고정 loopback 포트에서 localhost 인증서와 `/index.html`의 HTML 200을 확인한다. 대기 웹 상태와 중계 상태를 각기 표시한다.

edo2 HTTPS는 자기 공인 IP의 `/healthz`와 JSON `status=ok`를 확인하는 **자체 관측**이다. 실제 Mattermost 입력·공개 callback 성공은 별도 검증한다. IP 인증서는 수명 160시간이므로 만료 24시간 미만을 주의 기준으로 사용하고 갱신 timer를 함께 확인한다. 일반 서비스 인증서의 7일 기준과 구분한다.

## 통합 명령 전환과 복구

1. 진행 중인 요청이 끝나면 edo2의 중계 코드와 비공개 설정을 root 전용으로 백업한다. Mattermost의 기존 점검 명령별 트리거·제목·자동 완성 설정을 확인한다.
2. Mattermost 통합 관리에서 기존 `edo1-health`, `edo2-health`를 각각 `edo1`, `edo2`로 수정한다. 수정하면 각 명령의 토큰이 유지된다. edo2의 `/etc/edo-check/relay.json`에서는 해당 두 토큰의 키만 `/edo1`, `/edo2`로 옮긴다. 채널·호스트 설정과 `/applog` 설정을 보존하고 `validate_config`로 후보를 검증한다. 중계를 재기동한 뒤 `/healthz`와 두 명령의 공개 게시를 확인한다. [Mattermost 명령 수정 구현](https://github.com/mattermost/mattermost/blob/v11.7.2/server/channels/app/command.go#L703-L733)
3. 두 명령이 정상이라면 나머지 네 점검 명령은 트리거를 보관용 이름으로 바꾸고 자동 완성을 해제한다. 중계 설정에서도 네 명령의 키를 제거한다. 새 트리거 두 개가 자동 완성되고 이전 트리거는 실행되지 않는지 확인한다.

복구할 때는 Mattermost의 트리거·제목·자동 완성 설정과 동일 배포에서 백업한 중계 코드·비공개 설정을 함께 복원한다. 재기동 후 `/healthz`와 복원한 명령을 확인한다.

## edo1 저장공간 관측

edo1의 [타이머](edo-check-storage.timer)는 매시 07·22·37·52분(KST)에 [저장공간 서비스](edo-check-storage.service)를 시작한다. 타이머가 꺼져 있는 동안 놓친 예약이 있으면 다시 켤 때 한 번 실행한다. 서비스는 Docker Unix 소켓에서 점유량·회수 가능량을 읽고 Jenkins의 `/var/lib/jenkins` 데이터 bind를 `du`로 측정한다. Docker 요청은 최대 120초, Jenkins 측정은 최대 90초, 서비스 전체는 종료 대기를 포함해 최대 4분이다. 측정 프로세스는 낮은 CPU·I/O 우선순위로 실행한다.

각 항목의 성공값과 측정 시각은 root 전용 `/var/lib/edo-check-storage/edo1.json`에 원자적으로 갱신한다. 실패한 항목은 기존 성공값과 시각을 보존한다. 45분 이내 관측값은 보고서에 측정 시각과 함께 표시한다. 관측값이 없거나 만료되면 edo1의 Docker 저장공간은 한 번 최대 40초, 검증된 Jenkins bind는 최대 30초 동안 직접 조회한다. 다른 bind는 경로당 최대 12초다. edo1의 bind 전체는 최대 36초이며 컨테이너 수집 시작 후 61초를 넘기지 않는다. edo2는 기존 Docker 저장공간 20초·bind 전체 25초 제한을 사용한다.

edo1에서 새 수집기와 유닛을 반영한 뒤 첫 측정을 실행하고 타이머를 켠다. 서비스의 성공과 상태 파일의 권한을 확인하고 `/edo1`의 서버 저장공간 및 Jenkins 행에서 값과 측정 시각을 확인한다.

```sh
sudo systemd-analyze verify /etc/systemd/system/edo-check-storage.service /etc/systemd/system/edo-check-storage.timer
sudo systemctl daemon-reload
sudo systemctl start edo-check-storage.service
sudo journalctl -u edo-check-storage.service -n 20 --no-pager
sudo stat -c '%a %U:%G %n' /var/lib/edo-check-storage /var/lib/edo-check-storage/edo1.json
sudo systemctl enable --now edo-check-storage.timer
systemctl list-timers edo-check-storage.timer --all --no-pager
```

복구할 때는 edo1에서 `sudo systemctl disable --now edo-check-storage.timer`와 `sudo systemctl stop edo-check-storage.service`를 실행한다. 이전 수집기를 복원하고 이 작업으로 추가한 두 유닛 파일을 제거한 뒤 `sudo systemctl daemon-reload`를 실행한다. 측정 파일은 보존하고 고정 SSH 명령의 수집을 다시 확인한다.

## edo1 R2와 독립 HTTPS 관측

`/edo1`의 R2 읽기 행은 공개 학습 음원 API의 302에서 서명 URL을 받아 R2의 HTTP 206과 고정 32바이트를 대조한다. 쓰기 행은 마지막 예약 실행의 합성 객체 쓰기·읽기·삭제 판정과 최근 성공 시각을 표시한다. `edo-check-r2-write.timer`는 KST 00:13부터 6시간 간격으로 실행한다. 작업은 운영 백엔드와 동일한 녹음 버킷 설정을 edo1의 기존 환경 파일에서 읽고 `recordings/verification/edo-check/`의 실행별 새 키만 사용한다. 조건부 PUT으로 기존 객체를 보호하고 생성이 확인된 자체 키만 삭제한다. 응답이 끊겨 생성 여부를 알 수 없는 키에는 기존 녹음 만료 정책이 적용된다. edo1의 시스템 Python에서 boto3가 필요하다.

`악보 음원 R2` 행은 `/edo1` 실행 시 활성 백엔드의 악보 전용 버킷 설정을 읽는다. `sheet-audio/verification/edo-check/` 아래 실행마다 고유한 합성 객체 하나를 조건부로 만들고, 90초 GET 서명의 응답 본문과 원본 바이트를 비교한다. DELETE 응답 204와 같은 객체 HEAD 404가 확인돼야 삭제를 통과로 판정한다. 쓰기 응답이 끊겨도 이번 키의 정리를 시도하며, 조건부 쓰기 충돌이면 그 키를 보존한다. 보고서에는 고정 판정과 소요 시간만 표시한다. 삭제 확인에 실패하면 Cloudflare의 악보 전용 버킷에서 위 점검 접두사의 잔여 객체만 확인한다.

`edo-check-outside.timer`는 edo1에서 매분 edo2의 공인 HTTPS `/healthz`와 공개 `/api/terms`를 조회한다. 중계의 `status=ok`와 약관 목록 JSON 계약을 확인한다. 두 결과는 edo1의 journal과 상태 파일에 기록한다. 보고서는 최근 검사 3분, R2 쓰기 최근 실행 6시간 30분, Docker·Jenkins 최근 실측 30분을 최신성 기준으로 사용한다. 작업 로그와 상태 파일에는 고정 판정과 시각만 기록한다.

edo1에 두 스크립트를 `/opt/edo-check/`, 두 서비스와 타이머를 `/etc/systemd/system/`에 root 소유로 설치한다. `systemd-analyze verify`와 `systemctl daemon-reload` 뒤 다음 순서로 확인한다.

```sh
sudo systemctl start edo-check-r2-write.service edo-check-outside.service
sudo journalctl -u edo-check-r2-write.service -u edo-check-outside.service -n 15 --no-pager
sudo systemctl enable --now edo-check-r2-write.timer edo-check-outside.timer
systemctl list-timers 'edo-check-*' --all --no-pager
```

복구할 때는 두 신규 타이머를 `disable --now`하고 해당 서비스를 중지한 뒤 두 스크립트와 신규 유닛을 제거한다. 같은 배포에서 보관한 edo1 수집기와 edo2 중계를 복원하고 `systemctl daemon-reload`를 실행한다. edo2에서만 중계를 재시작한다. 상태 파일은 보존한다. [운영 확인 기록](../../docs/plans/S15P31E102-709.md)

## edo1 독립 HTTPS 로그

`edo-check-outside.timer`는 매분 edo2 중계의 HTTPS `/healthz`와 공개 `/api/terms`의 응답을 확인한다. 검사 시각과 대상별 최근 판정은 `/var/lib/edo-check-outside/latest.json`에 매번 기록한다. 운영 보고서의 독립 HTTPS 행은 이 파일을 읽는다.

### edo2 중단 알림

edo2가 꺼지면 edo2의 [상태 변화 알림](#상태-변화-능동-알림)도 함께 멈춘다. 그래서 edo1의 이 검사가 edo2 중계 `/healthz` 결과를 같은 규칙으로 알린다. 같은 결과가 2회 연속이면(약 2분) 장애 1건, 복구도 2회 연속이면 복구 1건을 `e102-cicd`에 보낸다. 확실한 미전달만 다음 분에 다시 보내고, 결과가 모호한 전송은 다시 보내지 않는다. 약관 API는 edo2 감시가 이미 확인하므로 여기서는 알리지 않는다. [작업 기록](../../docs/plans/S15P31E102-944.md)

| 구성 | 위치 |
| --- | --- |
| 판정·전송 | 같은 디렉터리의 `/opt/edo-check/alert.py`(edo2와 같은 파일)를 불러 재사용한다 |
| webhook | edo1에 이미 있는 Jenkins 실패 알림용 `/etc/eolssu-jenkins-notify/webhook-url`(root 600, `e102-cicd`)을 `LoadCredential`로 재사용한다. 새 비밀값은 없다 |
| 상태 | `/var/lib/edo-check-outside/alert.json`(DynamicUser 전용). `latest.json`과 journal 기록은 그대로 유지한다 |

webhook 파일이 없으면 unit이 시작되지 않으므로, Jenkins 알림 webhook을 옮기거나 폐기할 때는 이 unit의 `LoadCredential`도 함께 바꾼다. `alert.py`가 없거나 webhook을 읽지 못하면 알림만 건너뛰고 검사·기록은 계속한다. 알림 오류는 journal의 `edo2_notice_error`로 남는다.

2026-11-15에 실제 장애·복구 알림을 확인했다(중지 후 약 1분 30초에 장애, 재시작 후 약 2분에 복구). 같은 확인은 edo2의 `sudo systemctl stop edo-check`로 중계를 잠시 멈추고, 약 2분 뒤 장애 1건을 확인한 뒤 `sudo systemctl start edo-check`로 되돌려 복구 1건을 확인한다. 그동안 `/edo1`·`/edo2` 명령은 응답하지 않는다.

`journalctl -u edo-check-outside.service`에서는 대상별 `outage_started`(warning)와 `recovered`(notice), 서비스 실행 오류를 확인한다. 서비스 단위에서 정상 시작·종료에 해당하는 info를 걸러낸다. journal에 최근 출력이 없어도 정상일 수 있으므로 마지막 검사 시각은 상태 파일에서 확인한다.

설치 또는 갱신 시 `outside.py`와 `edo-check-outside.service`를 한 세트로 취급한다. 기존 스크립트와 유닛을 백업한 뒤 타이머를 중지하고 서비스의 진행 중 실행이 끝난 뒤 두 파일을 기존 경로에 설치한다. `systemd-analyze verify`와 `systemctl daemon-reload`를 거쳐 타이머를 다시 시작한다. 연속된 두 실행에서 상태 파일의 수정 시각이 갱신되고, 정상이라면 새로운 결과 로그가 없으며, 운영 보고서가 두 판정을 표시하는지 확인한다. 복구 시 타이머를 중지하고 두 파일을 같이 복원한 뒤 `daemon-reload`와 타이머 시작을 반복한다. 상태 파일은 보존한다. [운영 로그 측정과 적용 범위](../../docs/knowledge/infra/edo-operational-logging.md)

## 상태 변화 능동 알림

`edo-check-alert.timer`는 edo2에서 매분 [감시기](alert.py)를 실행한다. 2026-11-15부터 `e102-cicd` 채널의 전용 webhook `edo 감시`로 운영 중이다. 상태가 확인된 뒤 바뀔 때만 Mattermost incoming webhook으로 게시물 한 건을 보낸다. 점검 명령과 판정을 맞추기 위해 중계의 외부 HTTPS 검사와 고정 SSH 수집(`health`, `server`)을 그대로 재사용한다. SSH 권한·sudoers·edo1은 바꾸지 않는다. [작업 기록](../../docs/plans/S15P31E102-919.md)

| 원천 | 주기 | 내용 |
| --- | --- | --- |
| `edo1:external` | 1분 | edo2→edo1 공개 웹 `/`, API readiness(`status=UP`), 서비스 인증서 7일 |
| `edo2:health` | 5분 | 백업 최신성(db 90분, 나머지 27시간)·복구 검증 8일, MySQL 복제 감시, edo2 서비스·timer, 대기 웹 |
| `edo1:server`, `edo2:server` | 15분 | 파일시스템 공간·inode 85% 주의, 95% 장애, systemd 실패 서비스 |
| `edo1:health` | 30분 | edo1 컨테이너 준비 상태, 백업 timer·oneshot, Prometheus 수집, R2 읽기·쓰기 |

- `edo1:health`는 실행마다 R2 합성 객체 쓰기·삭제와 서명 읽기를 수행하므로 30분 주기로 둔다.
- 판정: 점검 표의 `실패`는 장애, `주의`는 주의, `정상`은 정상이다. `미측정`·`미검증`은 장애나 복구의 근거로 쓰지 않는다. RAM·CPU 주의는 알리지 않는다. SSH 수집 자체가 실패하면 `… 점검 수집` 장애로 따로 알린다. 이때 그 원천의 다른 항목을 복구로 처리하지 않는다.
- 확인: 같은 판정이 **2회 연속**이어야 알린다. 원천에 의심되는 변화가 있으면 주기를 기다리지 않고 다음 실행(약 1분 뒤)에 다시 수집한다. 장애·주의 시작, 주의↔장애 변경, 복구를 각각 한 번만 알린다. 같은 상태가 계속되면 다시 알리지 않는다. 한 실행의 변화는 게시물 하나에 최대 20행으로 모은다.
- 전송: 상태 파일에 `sending`을 먼저 기록한다. 연결 실패나 HTTP 200이 아닌 응답처럼 전달되지 않은 것이 확실하면 상태를 되돌려 다음 실행에서 같은 변화를 다시 보낸다. 요청을 보낸 뒤 시간이 초과되는 등 결과가 모호하면 `uncertain`으로 남기고 재전송하지 않는다. Jenkins 알림과 같은 원칙이다.
- 본문은 URL·이메일·`@`·표 구분자를 가린다. `ALERT_MENTION=@here`를 설정하면 새 장애가 있을 때만 붙인다.

| 구성 | 위치 / 권한 |
| --- | --- |
| 프로그램 | `/opt/edo-check/alert.py`, root 644. 같은 디렉터리의 `relay.py`를 읽는다 |
| 실행 계정 | 기존 `edo-check`. 중계의 `/etc/edo-check/relay.json`과 전용 SSH 키를 읽기만 한다 |
| webhook | `/etc/edo-check-alert/webhook-url`, root 600, 상위 디렉터리 root 700. `LoadCredential`로만 전달 |
| 상태 | `/var/lib/edo-check-alert/state.json`, edo-check 600. 삭제하면 기준점이 사라지므로 보존한다 |
| 선택 설정 | drop-in의 `Environment=ALERT_CHANNEL=<채널 이름>`, `Environment=ALERT_MENTION=@here` |

### 적용

edo2에서 실행한다. webhook URL은 명령 인자·셸 기록·터미널 출력에 남기지 않는다.

```sh
sudo install -d -m 700 -o root -g root /var/backups/edo-check/S15P31E102-919
sudo cp -a /opt/edo-check /var/backups/edo-check/S15P31E102-919/opt-edo-check
sudo install -o root -g root -m 644 alert.py /opt/edo-check/alert.py
sudo install -o root -g root -m 644 edo-check-alert.service edo-check-alert.timer /etc/systemd/system/
sudo install -d -m 700 -o root -g root /etc/edo-check-alert
sudo sh -c 'umask 077; cat > /etc/edo-check-alert/webhook-url'   # URL 붙여넣기 후 Ctrl-D
sudo systemd-analyze verify /etc/systemd/system/edo-check-alert.service /etc/systemd/system/edo-check-alert.timer
sudo systemctl daemon-reload
```

먼저 전송 없이 현재 판정을 확인한다. 장애·주의 항목이 나오면 활성화 직후 그 항목이 한 번 알려진다.

```sh
D=$(sudo -u edo-check mktemp -d)
sudo -u edo-check env STATE_DIRECTORY="$D" /usr/bin/python3 -I -B /opt/edo-check/alert.py /etc/edo-check/relay.json --dry-run
sudo rm -rf "$D"
```

연결 시험 메시지 한 건을 보낸 뒤 첫 실행과 타이머를 켠다.

```sh
sudo systemd-run --wait --pipe -p User=edo-check -p StateDirectory=edo-check-alert \
  -p LoadCredential=webhook-url:/etc/edo-check-alert/webhook-url \
  /usr/bin/python3 -I -B /opt/edo-check/alert.py /etc/edo-check/relay.json --test-notice
sudo systemctl start edo-check-alert.service
sudo systemctl enable --now edo-check-alert.timer
sudo journalctl -u edo-check-alert.service -n 30 --no-pager
```

### 실제 경보 검증

서비스에 영향이 없는 edo2의 백업 **상태 확인 예약**을 잠시 멈춰 장애·복구 경로를 끝까지 확인한다. 백업·복구 작업 자체는 멈추지 않는다. 운영 중에 하는 조치이므로 사용자 승인 후 진행한다.

1. `sudo systemctl stop eolssu-backup-status.timer`를 실행한다. 다음 `edo2:health` 수집(최대 5분)에서 `백업 상태 확인 예약`이 실패로 보이고, 그다음 실행(약 1분 뒤)에 `❌ 장애` 게시물 1건이 와야 한다.
2. 이후 2분 이상 추가 게시물이 없어야 한다(중복 억제).
3. `sudo systemctl start eolssu-backup-status.timer`로 되돌린다. 약 1~6분 안에 `✅ 복구` 게시물 1건이 와야 한다.
4. `journalctl -u edo-check-alert.service`에서 `alert_transition` 두 줄과 `"state": "sent"`를 확인한다. 게시물에 URL·토큰이 없어야 한다.

외부 경로는 `--only edo1:external --dry-run`으로 수집 결과만 확인한다. 공개 서비스를 일부러 내리지 않는다. 복제 감시 경로는 [edo2 대기계 작업(579)](../../docs/plans/S15P31E102-579.md)의 장애 전환 리허설에서 복제 수신을 잠시 멈출 때 함께 확인한다.

### 끄기·복구

```sh
sudo systemctl disable --now edo-check-alert.timer
sudo systemctl stop edo-check-alert.service
```

알림만 멈추며 점검 명령과 다른 timer는 그대로 동작한다. edo2 자체의 중단은 edo1이 따로 알린다([edo2 중단 알림](#edo2-중단-알림)). 제거할 때는 `/opt/edo-check/alert.py`와 두 유닛을 지우고 `daemon-reload`한다. 중계 코드(`relay.py`)는 바꾸지 않았으므로 중계를 재시작할 필요가 없다. 상태 파일은 보존한다. webhook을 폐기할 때는 Mattermost에서 해당 incoming webhook을 삭제한 뒤 `/etc/edo-check-alert/`를 지운다.

`uncertain`이면 Mattermost 채널에서 같은 시각의 게시물부터 찾는다. 실제로 받지 못했을 때만 `/edo1`·`/edo2`로 현재 상태를 직접 확인한다. 재전송을 유도하려고 상태 파일을 지우지 않는다.

## 확인과 갱신

이미지 열은 이름·태그만 표시하고 태그와 함께 지정된 `@sha256:<64자리>`는 생략한다. 태그 없는 digest 참조·이미지 ID는 앞 12자리와 `…`를 남긴다. 표 표시만 축약하며 수집 원본·배포 pin은 보존한다. 전체 참조는 대상 서버에서 `docker inspect --format '{{.Config.Image}}' <컨테이너명>`으로 확인한다.

```sh
python3 -B -m unittest discover -s infra/edo-check/tests -v
sh -n infra/edo-check/ssh-dispatch.sh
sh -n infra/edo-check/ssh-dispatch-edo2.sh
ssh edo1 'sudo -n visudo -cf /etc/sudoers.d/edo-check'
ssh edo2 'sudo -n visudo -cf /etc/sudoers.d/edo2-check'
ssh edo2 'sudo -n systemctl status edo-check --no-pager'
ssh edo2 'sudo -n nginx -t'
curl --fail --max-time 10 https://15.165.144.7/healthz
```

테스트는 loopback 임시 포트만 사용한다. 소켓 생성이 차단된 샌드박스에서는 해당 실행 권한이 필요하다. CI/CD 채널에서 `/edo1`과 `/edo2`를 실행하고 대상·시각·요청 ID, 영역별 판정, 주제별 표, 후속 게시물의 연결을 확인한다. 토큰·채널·콜백·중복 요청과 부분 수집 실패는 자동 회귀로 확인한다. 전용 점검 SSH에서 `id`·`health; id`·빈 명령·추가 인자·SFTP·포워딩 거부도 확인한다.

Let’s Encrypt IPv4 인증서는 `shortlived` 프로필이며 `/etc/letsencrypt/live/edo2-static-ip/`에 있다. 갱신은 `snap.certbot.renew.timer`가 수행한다. 갱신 후 deploy hook이 `nginx -t` 통과 뒤 Nginx를 reload한다. 아래 모의 검사는 클라이언트의 무작위 갱신 대기 시간이 포함될 수 있다.

정적 IPv4 `15.165.144.7`은 Lightsail `edo2-static-ip`로 edo2에 연결돼 있다. 인증서는 이 IPv4 SAN을 포함한다. 주소 변경 시 인증서·명령 URL·Nginx·edo1 키의 from·관리 SSH 별칭을 함께 변경한다. 이전 IP 갱신 설정은 root 백업으로 옮겨 비활성화했다.

```sh
ssh edo2 'sudo -n systemctl list-timers --all --no-pager'
ssh edo2 'sudo -n /snap/bin/certbot renew --cert-name edo2-static-ip --dry-run --run-deploy-hooks'
```

## 코드 반영·복구

변경 파일을 root 소유 `/opt/edo-check/`에 반영하기 전에 기존 파일을 root 전용 백업에 보관한다. 수집기·전용 dispatch·sudoers는 각 대상 서버, relay·systemd unit·Nginx는 edo2 대상이다. 비밀 설정을 변경할 때는 기존 토큰·채널 제한을 보존한 후보를 검증하고 같은 배포의 코드·unit과 함께 교체·복구한다. 일반 코드 배포에서 비밀 설정·키·인증서를 덮어쓰지 않는다.

sudoers 후보를 `visudo -cf`, Nginx 후보를 `nginx -t`, unit을 `systemd-analyze verify`로 검증한다. sudoers·dispatch의 고정 명령은 함께 맞춘다. unit 교체·복구 후 systemctl daemon-reload를 수행하고 edo2 중계 서비스만 재시작한다. Nginx 설정을 변경한 경우에만 검사 후 reload한다. edo1 앱 컨테이너 재시작은 필요하지 않다. 부팅 시 edo-check·Nginx·UFW·인증서 갱신 타이머가 활성화됐는지 확인한다.

실패하면 백업한 같은 Task의 파일을 복구하고 같은 검사·재시작·reload를 수행한다. 초기 배포 전 Nginx 원본은 edo2의 `/var/backups/edo2-ufw/nginx.default.before-check`, HTTP 전용 설정은 `nginx.http.before-tls`다. HTTPS를 제거하는 초기 복구에서는 먼저 중계를 중지해 명령 수신을 차단한다. UFW를 꺼서 우회하지 않는다. 임의 sudo 실행은 [sudoers](edo-check.sudoers)로 거부되며, 필요 시 해당 전용 키를 회수해 점검 접근만 중단할 수 있다.

## 서비스 준비와 백업 점검

edo1 필수 컨테이너는 runtime frontend·라우팅 설정이 가리키는 backend 슬롯·mysql·ai 및 monitoring grafana·prometheus·node-exporter다. AI는 입력 검증 HTTP 응답이 기대와 일치하면 `통신 정상`으로 표시한다. Prometheus는 127.0.0.1:3001 `/-/ready`와 `/api/v1/targets`를 확인하며 node·eolssu-backend job의 up 및 마지막 수집 90초 이내를 검사한다. node 수집이 정상인 경우 중복된 node-exporter healthcheck 미설정 행을 생략한다. edo1 Docker·SSH·Certbot timer와 종류별 백업 timer·oneshot도 검사한다. [서비스 준비 검사](../../docs/specs/edo1-service-readiness.md)

edo2는 db·config·jenkins·grafana·controller-key 백업의 최신 메타데이터와 암호문 존재를 검사한다. db 90분, 나머지 27시간 기준이며 암호문 무결성을 다시 계산하거나 복호화하지 않는다. `/var/lib/eolssu-backup/restore-last.json`의 완료 상태·8일 이내 여부를 별도로 검사한다. systemd 성공, 백업 최신성, 실제 복구 검증은 서로 다른 근거로 표시한다. 실제 실패 상태는 지우지 않는다.

초기 판정 기준은 운영 구성 갱신 기록에서 확인할 수 있다. [운영 구성 갱신](../../docs/specs/server-inspection-current-runtime.md) · [실행 기록](../../docs/plans/S15P31E102-271.md)
