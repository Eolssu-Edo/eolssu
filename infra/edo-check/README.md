# Mattermost edo1·edo2 점검 중계

관련 작업은 edo1 [S15P31E102-95](../../docs/plans/S15P31E102-95.md), edo2 확장 [S15P31E102-133](../../docs/plans/S15P31E102-133.md)이다. 명령 등록·접근 제한·실제 배포 증거는 해당 기록을 따른다.

| 명령 | 실행 경로·결과 |
| --- | --- |
| `/edo1-server` | edo2 → 전용 SSH → edo1 서버·Docker 전체 현황 표 |
| `/edo1-containers` | edo2 → 전용 SSH → edo1 컨테이너별 자원·상태·저장공간 표 |
| `/edo1-health` | edo1 내부 health와 edo2의 외부 HTTPS·인증서 검사 표 |
| `/edo2-server` | edo2 → 전용 loopback SSH → edo2 서버·Docker 전체 현황 |
| `/edo2-containers` | edo2 컨테이너별 자원·상태·저장량, 등록 0개는 해당 없음 |
| `/edo2-health` | edo2 서비스·타이머·oneshot·중계 내부 HTTP·자체 HTTPS·IP 인증서 |

여섯 명령의 Mattermost Request URL은 `https://15.165.144.7/integrations/mattermost/commands`, 메서드는 POST다. Team 이도 (CI/CD,서버점검), 채널 ID `7pqaof6zkt8rdbqbdjjsfpfscw`만 허용한다. 명령마다 서로 다른 토큰을 사용하며 command/token에서 서버와 모드를 결정한다. form의 target·mode 덮어쓰기와 명령 인자는 거부한다. 다른 채널과 잘못된 요청에는 실행자에게만 거부 안내를 반환한다. 성공·수집 실패·시간 초과 보고는 요청의 `response_url`에 `in_channel`로 전송해 같은 채널 전체에 표시한다.

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

명령별 토큰을 상수 시간 비교하고 채널을 확인한 후 작업을 접수한다. 요청은 8KiB 이하, 중복 form 필드는 거부한다. 동일 `response_url` 요청은 30분간 중복 실행하지 않고, 같은 채널·같은 서버·같은 모드는 10초 간격으로 제한한다. 동시 수집은 두 서버 합계 최대 3개다. 이 상태는 단일 중계 프로세스 메모리에 있어 재시작하면 초기화된다. 별도 큐나 완료 이력 저장소는 두지 않는다.

응답 URL은 고정 Mattermost HTTPS 호스트·443과 `/hooks/commands/<26자리 ID>`만 허용한다. 쿼리·userinfo·다른 경로와 redirect를 허용하지 않는다. DNS의 모든 주소가 공인 주소인지 확인하고 검증한 주소에 연결하면서 TLS 호스트 검증을 유지한다. 콜백 실패는 요청 ID·모드만 journal에 기록하며, 응답 유실 뒤 중복 게시를 피하기 위해 자동 재전송하지 않는다. 콜백 URL·토큰·개인 키는 로그에 남기지 않는다.

## 지표 해석

보고서는 **제목·종합 판정 → 먼저 확인할 항목 → 상세 표** 순서로 읽는다. `장애`는 중지·unhealthy·명시적인 실패 응답, `주의`는 용량·메모리 임계값, `미검증`은 실행만 확인되고 준비 상태 검사가 없는 경우, `미측정`은 값을 읽지 못한 경우다. 상한이 없는 메모리는 `설정 참고`이며 장애 건수에 넣지 않는다. 수집 시작·최종 완료·소요 시간을 표시한다. 외부 HTTP·TLS 검사까지 끝난 시간이 최종 수집 시각이며 기존 게시물은 자동 갱신하지 않는다. 세 명령은 매번 새로 수집한다.

| 명령 | 먼저 볼 곳 | 의미 |
| --- | --- | --- |
| `/edo1-server` | RAM 가용량·파일시스템 공간과 inode, 시스템 실패·OOM | 서버 자원 압박과 시스템 오류 확인. Docker 데몬 응답과 서비스 준비 상태는 별도다. |
| `/edo1-containers` | 실행 상태·CPU·RAM·쓰기 계층·로그 | CPU 100%는 1코어. 메모리 상한이 있으면 상한 대비 비율, 없으면 호스트 RAM 대비 비율을 표시한다. |
| `/edo1-health` | 장애·미검증 항목의 근거와 다음 조치 | healthcheck 없음은 장애 확정이 아니다. Grafana·n8n은 별도 HTTP 준비 응답으로, Prometheus는 준비 HTTP와 실제 수집 대상 상태도 확인한다. |

표 본문의 값 전체를 굵게 하지 않고 제목·구분·상태 판정을 강조한다. 상태는 ✅ 정상·검사 통과, ❌ 장애, ⚠️ 주의, ❓ 미검증, 🚫 미측정, ℹ️ 설정 참고로 구분하며 텍스트를 함께 표시한다. 접수 중은 ⏳, 수집 실패도 🚫로 표시해 서비스 장애와 구분한다. 단순 실행 중·검사 시작 중·과거 healthy에는 체크를 붙이지 않는다. 상단 건수는 0보다 큰 항목만 이모지와 굵은 글씨로 강조한다. 좁은 상태 열에서 한글이 한 글자씩 갈라지지 않도록 상태 단어에 word joiner, 아이콘 뒤에 줄바꿈 없는 공백을 사용한다. 컨테이너 이름은 웹·API·배포 등 역할로 표시하고 실행 상세에 원래 식별자를 보존한다. 로그·볼륨·bind·쓰기 계층을 구분하고 저장소마다 한 행을 사용한다. 누락은 0으로 치환하지 않으며 같은 원인을 중복 집계하지 않는다.

- CPU는 Docker의 코어 합산 기준으로 100%를 넘을 수 있다. 서버 CPU는 200ms 표본이고 Load는 1·5·15분 평균이다. 장기 추세나 피크를 의미하지 않는다.
- 서버 RAM 사용량은 전체에서 MemAvailable을 뺀 값이다. 컨테이너 RAM은 Docker CLI와 같은 inactive file cache 제외 기준이다. 설정하지 않은 제한은 `미설정`, 수집하지 못한 값은 `확인 불가`로 표시한다. 컨테이너 합계는 서버 전체 사용량과 같지 않다.
- 파일시스템 용량과 Docker 저장공간을 구분한다. Docker Root Dir의 파일시스템 전체·여유는 호스트와 공유하는 용량이며 예약량이 아니다. 컨테이너 쓰기 계층의 `StorageOpt.size`가 없으면 제한 미설정으로 표시하며 로그·볼륨은 그 제한의 대상이 아니다. 이미지 공유 계층은 Engine `LayersSize`를 사용하고 볼륨은 고유 볼륨별로 집계한다. Docker의 회수 가능량을 그대로 표시하며 자동 정리하지 않는다. 이미지·캐시 등 분류 사이의 공유 공간 때문에 각 분류를 합해 물리 점유량으로 해석하면 안 된다.
- 컨테이너 쓰기 계층, 회전 파일을 포함한 로컬 로그, 볼륨·bind mount를 구분한다. 공유 볼륨은 `공유`라고 표시하며 컨테이너 표의 저장소 값을 서버 전체로 합산하지 않는다. bind는 요청 내 같은 경로를 한 번만 `nice du -sx`로 읽고 경로당 최대 12초·전체 bind 측정 25초로 제한한다. Jenkins 실제 측정 8.5초를 확인해 기존 3초 제한을 보완했다. 전체 수집 80초·SSH 90초 상한은 유지한다. `json-file`과 `local` 로그의 주·회전 파일 크기만 읽으며 내용은 열지 않는다. local은 Docker Root Dir 아래 유효한 64자리 컨테이너 ID의 `local-logs/container.log*`를 읽는다. 심볼릭 링크를 제외하고 100파일을 넘거나 경로가 없으면 미측정이다.
- NET·BLOCK I/O는 누적 전송량이며 디스크 점유량이 아니다. 메모리·디스크·inode 80% 이상은 주의 항목으로 알리고, 디스크·inode 90% 이상은 긴급 용량 점검이 필요한 주의로 표시한다. 용량만으로 서비스 장애를 확정하지 않는다. 이는 이 프로젝트의 기본 기준이며 보편적인 장애 임계값은 아니다.
- Load는 CPU 사용률과 다르며 실행 가능 작업과 중단 불가 대기의 평균이다. 5분 또는 15분 값이 코어 수를 넘으면 컨테이너 CPU 상위부터 보고, CPU가 낮으면 호스트 I/O 대기를 확인한다. 이는 수동 확인 기준으로 자동 장애 건수에 넣지 않는다. CPU는 0.2초 표본이므로 한 번의 값만으로 지속성을 판정하지 않는다. RAM 80% 이상 등 자원 주의 시 사용자가 30초 간격 3회 재실행해 값을 비교하도록 안내하며 명령이 자동 대기·반복하지 않는다.
- 실패 서비스는 현재 systemd 실패 상태이며 1개 이상이면 `systemctl --failed`로 대상을 확인한다. OOM은 최근 24시간 커널에서 매칭된 로그 줄 수이며 사건 건수가 아니다. 과거 기록은 주의로 구분해 시각·대상을 현재 RAM·한도와 비교한다. Docker OOM 플래그와 재시작 횟수도 최근 24시간 발생 횟수를 뜻하지 않는다.
- frontend·backend·mysql·ai와 Grafana·Prometheus·node-exporter의 실제 컨테이너 이름을 필수 대상으로 확인한다. 중지·일시정지를 예전 healthy 값으로 정상 판정하지 않는다. health 모드에서는 inspect만 수집하며 stats 실패가 실행 정보까지 지우지 않도록 분리한다. API `/api/actuator/health/readiness`의 404·HTML 200·예상하지 않은 JSON은 미검증이며 `status=UP`만 정상, 명시적 `DOWN/OUT_OF_SERVICE`는 장애다. 운영 API readiness는 애플리케이션 준비 상태와 기존 MySQL 연결을 함께 검사하며 자세한 DB 정보는 공개하지 않는다. Grafana는 loopback `/api/health`, n8n은 `127.0.0.1:5688/healthz/readiness`의 상태·본문을 확인한다. Docker 검사가 없어도 해당 HTTP 준비 검사가 통과하면 중복된 미검증 행을 표시하지 않는다. Docker healthy 자체는 설정된 healthcheck의 통과이며 별도 준비 검사의 범위를 보장하지 않는다. 외부 검사는 edo2에서 수행한다.

서버별 세 명령은 수시 인프라 점검이다. 장기 요청량·오류율·p95/p99 지연·I/O 지연, 실제 사용자 업무 기능은 실행하지 않는다. 백업 복구는 마지막 검증 결과 파일만 읽으며 이 점검에서 복구를 실행하지 않는다. 자원과 기본 응답이 정상이어도 이를 전체 서비스 정상으로 확대 해석하지 않는다. 상시 지표와 업무 기능 검증의 추가 여부는 별도 요구 범위로 정한다.

### edo2의 기대 구성

edo2는 Docker가 설치·활성화된 서버다. 2026-10-29에는 KVM 없는 환경에서 시도한 `eolssu-android-remote`를 관리자가 의도적으로 중지했고 이미지·두 볼륨·로그를 보존했다. 엔진 조회 성공과 빈 목록은 `해당 없음`으로, 엔진·socket 조회 실패는 `미측정`으로 구분한다. edo1의 웹·API·MySQL 필수 컨테이너와 Grafana·Prometheus·n8n 검사를 edo2에 적용하지 않는다.

#### 의도한 실험 중지 기록

`/etc/edo-check/expected-stops.json`은 관리자가 승인한 **특정 종료 사건**만 기록하는 root 소유 일반 JSON 파일이다. 0600을 권장하고 그룹·기타 쓰기 권한, 심볼릭 링크, 16KiB 초과 파일은 인정하지 않는다. 읽기·파싱·필드 확인에 실패하면 기존 장애 판정을 유지한다. `relay.json`·SSH 권한·토큰과는 별도이며 점검 명령이 자동 생성하거나 갱신하지 않는다.

배열의 각 항목은 `target`(`edo2`), `name`, 전체 64자리 `id`, Docker `State.FinishedAt` 그대로의 `finished_at`, `exit_code`(`143`), 중지 근거 `reason`을 가진다. 현재 inspect의 ID·이름·종료 시각·코드가 모두 같고 `Status=exited`, `Running=false`, `OOMKilled=false`일 때만 `실험 중지`로 표시한다. 계정·시각을 확인하지 않은 컨테이너 이름만으로 등록하지 않는다. 실행·준비 정상으로 바꾸지 않으며 CPU·RAM·NET·IO 미측정도 유지한다. 재실행·재생성 후의 새 종료, OOM·다른 종료 코드는 기존 장애 판정을 받는다.

기록할 때는 관리자 SSH에서 `sudo docker inspect --format '{{.Id}} {{.Name}} {{.State.Status}} {{.State.Running}} {{.State.ExitCode}} {{.State.OOMKilled}} {{.State.FinishedAt}}' eolssu-android-remote`로 사건을 먼저 대조한다. 비밀 환경변수를 포함한 inspect 전체를 출력하지 않는다. root 전용 후보 파일에 위 항목을 작성하고 `python3 -m json.tool`로 문법만 확인한 뒤 root:root 0600으로 설치한다. **새 종료 사건의 등록은 관리자의 의도 확인 후 수동으로 수행**한다. 현재 Task의 실제 기록·검증·복구 위치는 [293 기록](../../docs/plans/S15P31E102-293.md)을 따른다. 기록 제거는 실험 컨테이너·이미지·볼륨 삭제를 의미하지 않는다.

health는 nginx·edo-check·docker·ssh daemon, Certbot 갱신·백업 status/prune/restore-test timer, backup status/restore-test oneshot 이력과 Nginx 구성·UFW·127.0.0.1:8765 `/healthz`를 검사한다. timer는 active와 다음 실행 예약, oneshot은 실행 시각·결과·종료 코드를 확인한다. inactive는 oneshot 성공 후 정상 대기일 수 있다. 실행 이력이 없거나 이번 실행 중이면 `미검증`이며 작업을 자동 실행하지 않는다. systemd 실행 성공은 백업 파일의 무결성·복구 성공을 보증하지 않는다.

edo2 HTTPS는 자기 공인 IP의 `/healthz`와 JSON `status=ok`를 확인하는 **자체 관측**이다. 실제 Mattermost 입력·공개 callback 성공은 별도 검증한다. IP 인증서는 수명 160시간이므로 만료 24시간 미만을 주의 기준으로 사용하고 갱신 timer를 함께 확인한다. 일반 서비스 인증서의 7일 기준과 구분한다.

### 확장 배포·복구

코드·설정·unit을 root 전용으로 함께 백업한다. 기존 edo1 토큰 3개·채널 제한을 보존하고 새 edo2 토큰 3개와 targets를 비공개 설정에 추가한다. `validate_config`로 명령 그룹 완전성·토큰 중복·필수 SSH 설정을 검증하고, sudoers는 visudo·dispatch는 sh 문법·unit은 systemd-analyze로 검증한다. 중계를 멈춘 짧은 구간에 후보 파일들을 교체하고 한 번 재시작한다. 실패하면 같은 백업의 코드·설정·unit을 함께 복구한다. 앱·Docker·백업 작업·방화벽은 재시작하거나 변경하지 않는다.

기존 edo1 3개 토큰/전역 SSH 설정의 기동 호환을 지원한다. 새 형식에서 설정되지 않은 edo2를 edo1로 대체하지 않으며, 서버별 토큰은 세 명령 한 묶음으로 설정한다. 토큰 값은 채팅·로그·저장소에 남기지 않는다.
- 표는 의미가 같은 정보끼리 묶는다. 서버는 항목·현재값·기준/조치 3열, 컨테이너는 자원 5열·저장공간 3열·실행 상세 5열, health는 대상·판정·근거/소요·조치 4열이다. health의 조치는 같은 행에 한 번만 표시하며 수집 전체의 별도 문제가 있을 때만 앞에 요약 표를 추가한다. 표 셀에 HTML 개행을 넣지 않는다. 상단 수집 시각은 인라인 코드로 감싸 콜론 사이 숫자가 이모지로 해석되지 않게 한다. 표마다 최대 100행, 전체 약 14,000자 제한을 넘으면 생략을 명시한다. 점검 명령 자체는 애플리케이션·DB·Docker 구성을 변경하지 않는다.

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

테스트는 loopback 임시 포트만 사용한다. 소켓 생성이 차단된 샌드박스에서는 해당 실행 권한이 필요하다. 실제 Mattermost 흐름은 CI/CD 채널에서 양 서버 여섯 명령을 각각 실행하고 대상 서버와 수집 시각이 맞는 공개 메시지가 남는지 확인한다. 토큰 검증·다른 채널 거부·잘못된 callback·중복 요청과 양 서버의 전용 점검 SSH에서 `id`·`health; id`·빈 명령·추가 인자·SFTP·포워딩 거부도 확인한다. 실제 운영 컨테이너를 중단해 장애를 만들지 않는다.

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

## 최신 운영 구성 점검 (S15P31E102-271)

edo1 필수 컨테이너는 runtime frontend·backend·mysql·ai 및 monitoring grafana·prometheus·node-exporter다. AI Docker healthy는 현재 TCP 연결 검사 통과만 의미하므로 health 보고에서는 분석 HTTP 준비·추론 기능을 `미검증`으로 명시한다. Prometheus는 127.0.0.1:3001 `/-/ready`와 `/api/v1/targets`를 확인하며 node·eolssu-backend job의 up 및 마지막 수집 90초 이내를 검사한다. node 수집이 정상인 경우 중복된 node-exporter healthcheck 미설정 행을 생략한다. edo1 Docker·SSH·Certbot timer와 종류별 백업 timer·oneshot도 검사한다.

edo2는 db·config·jenkins·grafana·controller-key 백업의 최신 메타데이터와 암호문 존재를 검사한다. db 90분, 나머지 27시간 기준이며 암호문 무결성을 다시 계산하거나 복호화하지 않는다. `/var/lib/eolssu-backup/restore-last.json`의 완료 상태·8일 이내 여부를 별도로 검사한다. systemd 성공, 백업 최신성, 실제 복구 검증은 서로 다른 근거로 표시한다. 실제 실패 상태는 지우지 않는다.

[갱신 스펙](../../docs/specs/server-inspection-current-runtime.md)과 [실행 기록](../../docs/plans/S15P31E102-271.md)을 따른다. 등록된 명령 이름·URL·토큰·채널 권한 변경은 필요하지 않다. 두 서버 수집기를 공통 최신 코드로 맞추고 edo2 중계만 재시작하면 다음 명령부터 적용된다.
