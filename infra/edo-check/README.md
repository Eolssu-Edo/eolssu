# Mattermost edo1 점검 중계

관련 작업은 [S15P31E102-95](../../docs/plans/S15P31E102-95.md)다. 명령 등록·초기 UFW 적용 및 실제 배포 증거는 해당 기록을 따른다.

| 명령 | 실행 경로·결과 |
| --- | --- |
| `/edo1-server` | edo2 → 전용 SSH → edo1 서버·Docker 전체 현황 표 |
| `/edo1-containers` | edo2 → 전용 SSH → edo1 컨테이너별 자원·상태·저장공간 표 |
| `/edo1-health` | edo1 내부 health와 edo2의 외부 HTTPS·인증서 검사 표 |

세 명령의 Mattermost Request URL은 `https://15.165.144.7/integrations/mattermost/commands`, 메서드는 POST다. Team 이도 (CI/CD), 채널 ID `7pqaof6zkt8rdbqbdjjsfpfscw`만 허용한다. 다른 채널과 잘못된 요청에는 실행자에게만 거부 안내를 반환한다. 성공·수집 실패·시간 초과 보고는 요청의 `response_url`에 `in_channel`로 전송해 같은 채널 전체에 표시한다. 명령 인자는 지원하지 않는다.

## 구성과 권한

- edo2: Ubuntu 24.04, Python 3.12 표준 라이브러리, Ubuntu Nginx, 공식 Certbot snap 5.8.0. 런타임 Python 외부 패키지는 없다.
- `edo-check.service`는 로그인 불가 `edo-check` 사용자로 실행한다. 앱은 `127.0.0.1:8765`만 수신하고 Nginx만 443을 공개한다. `/healthz`는 중계 프로세스의 liveness이며 edo1·Mattermost 응답까지 보장하지 않는다.
- 비밀 설정은 edo2의 `/etc/edo-check/relay.json`에 root:edo-check, 640으로 둔다. [예시](relay.example.json)는 토큰 자리표시자만 포함한다. 실제 토큰·SSH 개인 키·인증서 개인 키를 저장소에 넣지 않는다.
- edo2 개인 키는 `/var/lib/edo-check/ssh/id_ed25519`, known_hosts는 같은 디렉터리에 있으며 edo-check 소유 600이다. edo1의 호스트 키는 기존 관리 SSH로 확인해 고정한다. StrictHostKeyChecking을 끄지 않는다.
- edo1의 `/opt/edo-check/collector.py`와 `ssh-dispatch.sh`는 root 소유다. edo1 전용 계정은 Docker 그룹에 넣지 않는다. root 소유 authorized_keys에서 edo2 IPv4만 허용하고 `restrict`와 강제 dispatch로 일반 셸·SFTP·포트 포워딩·PTY를 차단한다.
- sudoers는 [세 고정 명령](edo-check.sudoers)만 허용한다. 80초가 지나면 TERM, 추가 5초 후 KILL로 수집기와 하위 작업을 제한한다. 관리자 계정·키와 앱 컨테이너는 사용·수정하지 않는다.

## 요청 처리와 제한

명령별 토큰을 상수 시간 비교하고 채널을 확인한 후 작업을 접수한다. 요청은 8KiB 이하, 중복 form 필드는 거부한다. 동일 `response_url` 요청은 30분간 중복 실행하지 않고, 같은 채널·같은 명령은 10초 간격으로 제한한다. 동시 수집은 최대 3개다. 이 상태는 단일 중계 프로세스 메모리에 있어 재시작하면 초기화된다. 별도 큐나 완료 이력 저장소는 두지 않는다.

응답 URL은 고정 Mattermost HTTPS 호스트·443과 `/hooks/commands/<26자리 ID>`만 허용한다. 쿼리·userinfo·다른 경로와 redirect를 허용하지 않는다. DNS의 모든 주소가 공인 주소인지 확인하고 검증한 주소에 연결하면서 TLS 호스트 검증을 유지한다. 콜백 실패는 요청 ID·모드만 journal에 기록하며, 응답 유실 뒤 중복 게시를 피하기 위해 자동 재전송하지 않는다. 콜백 URL·토큰·개인 키는 로그에 남기지 않는다.

## 지표 해석

보고서는 **제목·종합 판정 → 먼저 확인할 항목 → 상세 표** 순서로 읽는다. `장애`는 중지·unhealthy·명시적인 실패 응답, `주의`는 용량·메모리 임계값, `미검증`은 실행만 확인되고 준비 상태 검사가 없는 경우, `미측정`은 값을 읽지 못한 경우다. 상한이 없는 메모리는 `설정 참고`이며 장애 건수에 넣지 않는다. 수집 시작·최종 완료·소요 시간을 표시한다. 외부 HTTP·TLS 검사까지 끝난 시간이 최종 수집 시각이며 기존 게시물은 자동 갱신하지 않는다. 세 명령은 매번 새로 수집한다.

| 명령 | 먼저 볼 곳 | 의미 |
| --- | --- | --- |
| `/edo1-server` | RAM 가용량·파일시스템 공간과 inode, 시스템 실패·OOM | 서버 자원 압박과 시스템 오류 확인. Docker 데몬 응답과 서비스 준비 상태는 별도다. |
| `/edo1-containers` | 실행 상태·CPU·RAM·쓰기 계층·로그 | CPU 100%는 1코어. 메모리 상한이 있으면 상한 대비 비율, 없으면 호스트 RAM 대비 비율을 표시한다. |
| `/edo1-health` | 장애·미검증 항목의 근거와 다음 조치 | healthcheck 없음은 장애 확정이 아니다. Grafana·Metabase·n8n은 별도 HTTP 준비 응답으로 확인한다. |

표 본문의 값 전체를 굵게 하지 않고 제목·구분·상태 판정을 강조한다. 상태는 ✅ 정상·검사 통과, ❌ 장애, ⚠️ 주의, ❔ 미검증·미측정으로 구분하며 텍스트를 함께 표시한다. 단순 실행 중·검사 시작 중·과거 healthy에는 체크를 붙이지 않는다. 상단 건수는 0보다 큰 항목만 이모지와 굵은 글씨로 강조한다. 이 표시는 최종 출력에만 적용하며 판정·집계 기준은 바꾸지 않는다. 컨테이너 이름은 웹·API·배포 등 역할로 표시하고 실행 상세에 원래 식별자를 보존한다. 로그·볼륨·bind·쓰기 계층을 구분하고 저장소마다 한 행을 사용한다. 누락은 0으로 치환하지 않으며 같은 원인을 중복 집계하지 않는다.

- CPU는 Docker의 코어 합산 기준으로 100%를 넘을 수 있다. 서버 CPU는 200ms 표본이고 Load는 1·5·15분 평균이다. 장기 추세나 피크를 의미하지 않는다.
- 서버 RAM 사용량은 전체에서 MemAvailable을 뺀 값이다. 컨테이너 RAM은 Docker CLI와 같은 inactive file cache 제외 기준이다. 설정하지 않은 제한은 `미설정`, 수집하지 못한 값은 `확인 불가`로 표시한다. 컨테이너 합계는 서버 전체 사용량과 같지 않다.
- 파일시스템 용량과 Docker 저장공간을 구분한다. Docker Root Dir의 파일시스템 전체·여유는 호스트와 공유하는 용량이며 예약량이 아니다. 컨테이너 쓰기 계층의 `StorageOpt.size`가 없으면 제한 미설정으로 표시하며 로그·볼륨은 그 제한의 대상이 아니다. 이미지 공유 계층은 Engine `LayersSize`를 사용하고 볼륨은 고유 볼륨별로 집계한다. Docker의 회수 가능량을 그대로 표시하며 자동 정리하지 않는다. 이미지·캐시 등 분류 사이의 공유 공간 때문에 각 분류를 합해 물리 점유량으로 해석하면 안 된다.
- 컨테이너 쓰기 계층, 회전 파일을 포함한 로컬 로그, 볼륨·bind mount를 구분한다. 공유 볼륨은 `공유`라고 표시하며 컨테이너 표의 저장소 값을 서버 전체로 합산하지 않는다. bind는 요청 내 같은 경로를 한 번만 `nice du -sx`로 읽고 경로당 최대 12초·전체 bind 측정 25초로 제한한다. Jenkins 실제 측정 8.5초를 확인해 기존 3초 제한을 보완했다. 전체 수집 80초·SSH 90초 상한은 유지한다. `json-file`과 `local` 로그의 주·회전 파일 크기만 읽으며 내용은 열지 않는다. local은 Docker Root Dir 아래 유효한 64자리 컨테이너 ID의 `local-logs/container.log*`를 읽는다. 심볼릭 링크를 제외하고 100파일을 넘거나 경로가 없으면 미측정이다.
- NET·BLOCK I/O는 누적 전송량이며 디스크 점유량이 아니다. 메모리·디스크·inode 80% 이상은 주의 항목으로 알리고, 디스크·inode 90% 이상은 실패로 표시한다. 이는 이번 기본 기준이며 임계값 변경은 운영 요구에 맞춰 검토한다.
- frontend·backend·mysql의 실제 컨테이너 이름을 필수 대상으로 확인한다. 중지·일시정지를 예전 healthy 값으로 정상 판정하지 않는다. health 모드에서는 inspect만 수집하며 stats 실패가 실행 정보까지 지우지 않도록 분리한다. API `/api/actuator/health`의 404·HTML 200·예상하지 않은 JSON은 미검증이며 `status=UP`만 정상, 명시적 `DOWN/OUT_OF_SERVICE`는 장애다. Grafana·Metabase는 loopback `/api/health`, n8n은 `127.0.0.1:5688/healthz/readiness`의 상태·본문을 확인한다. Docker 검사가 없어도 해당 HTTP 준비 검사가 통과하면 중복된 미검증 행을 표시하지 않는다. 외부 검사는 edo2에서 수행한다.
- 표는 의미가 같은 정보끼리 묶는다. 서버는 값·해석·확인 기준의 4열 표, 컨테이너는 자원·상태 6열·저장공간 4열·실행 상세 6열 표, health는 대상·판정·근거·다음 조치·응답 시간 5열 표다. 필요한 조치가 있으면 앞에 요약 표를 추가한다. 표 셀에 HTML 개행을 넣지 않는다. 상단 수집 시각은 인라인 코드로 감싸 콜론 사이 숫자가 이모지로 해석되지 않게 한다. 표마다 최대 100행, 전체 약 14,000자 제한을 넘으면 생략을 명시한다. 전체 점검 과정에서 애플리케이션·DB·Docker 구성을 변경하지 않는다.

## 확인과 갱신

```sh
python3 -B -m unittest discover -s infra/edo-check/tests -v
sh -n infra/edo-check/ssh-dispatch.sh
ssh edo1 'sudo -n visudo -cf /etc/sudoers.d/edo-check'
ssh edo2 'sudo -n systemctl status edo-check --no-pager'
ssh edo2 'sudo -n nginx -t'
curl --fail --max-time 10 https://15.165.144.7/healthz
```

테스트는 loopback 임시 포트만 사용한다. 소켓 생성이 차단된 샌드박스에서는 해당 실행 권한이 필요하다. 실제 Mattermost 흐름은 CI/CD 채널에서 세 명령을 각각 실행하고 표가 공개 메시지로 남는지 확인한다. 토큰 검증·다른 채널 거부·잘못된 callback·중복 요청과 SSH의 `id`·`health; id` 거부도 확인한다. 실제 운영 컨테이너를 중단해 장애를 만들지 않는다.

Let’s Encrypt IPv4 인증서는 `shortlived` 프로필이며 `/etc/letsencrypt/live/edo2-static-ip/`에 있다. 갱신은 `snap.certbot.renew.timer`가 수행한다. 갱신 후 deploy hook이 `nginx -t` 통과 뒤 Nginx를 reload한다. 아래 모의 검사는 클라이언트의 무작위 갱신 대기 시간이 포함될 수 있다.

정적 IPv4 `15.165.144.7`은 Lightsail `edo2-static-ip`로 edo2에 연결돼 있다. 인증서는 이 IPv4 SAN을 포함한다. 주소 변경 시 인증서·명령 URL·Nginx·edo1 키의 from·관리 SSH 별칭을 함께 변경한다. 이전 IP 갱신 설정은 root 백업으로 옮겨 비활성화했다.

```sh
ssh edo2 'sudo -n systemctl list-timers --all --no-pager'
ssh edo2 'sudo -n /snap/bin/certbot renew --cert-name edo2-static-ip --dry-run --run-deploy-hooks'
```

## 코드 반영·복구

변경 파일을 root 소유 `/opt/edo-check/`에 반영하기 전에 기존 파일을 root 전용 백업에 보관한다. 수집기·dispatch·sudoers는 edo1, relay·systemd unit·Nginx는 edo2 대상이다. 실제 비밀 설정·키·인증서는 코드 배포로 덮어쓰지 않는다.

sudoers 후보를 `visudo -cf`, Nginx 후보를 `nginx -t`, unit을 `systemd-analyze verify`로 검증한다. sudoers·dispatch의 고정 명령은 함께 맞춘다. 변경 후 edo2 중계 서비스만 재시작하고 Nginx를 reload한다. edo1 앱 컨테이너 재시작은 필요하지 않다. 부팅 시 edo-check·Nginx·UFW·인증서 갱신 타이머가 활성화됐는지 확인한다.

실패하면 백업한 같은 Task의 파일을 복구하고 같은 검사·재시작·reload를 수행한다. 초기 배포 전 Nginx 원본은 edo2의 `/var/backups/edo2-ufw/nginx.default.before-check`, HTTP 전용 설정은 `nginx.http.before-tls`다. HTTPS를 제거하는 초기 복구에서는 먼저 중계를 중지해 명령 수신을 차단한다. UFW를 꺼서 우회하지 않는다. 임의 sudo 실행은 [sudoers](edo-check.sudoers)로 거부되며, 필요 시 해당 전용 키를 회수해 점검 접근만 중단할 수 있다.
