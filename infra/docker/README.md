# Docker 기본 로그 회전

`daemon-log-rotation.json`은 edo1·edo2의 신규 `json-file` 컨테이너에 파일당 10 MiB, 최대 3개 파일의 기본값을 제공한다. 이 값은 크기 기준이며, 기존 rsyslog의 앱 일반 로그 최대 14일 정책은 계속 별도로 적용한다. `syslog`와 `local`을 명시한 서비스에는 각 드라이버의 설정을 사용한다. [Docker 로깅 설정](https://docs.docker.com/engine/logging/configure/) · [json-file 옵션](https://docs.docker.com/engine/logging/drivers/json-file/)

## 합성 로그 회전 시험 재현

edo2에 `nginx:1.30.6-alpine` 이미지와 Python 3가 준비돼 있을 때 아래 명령을 실행한다. 2026-11-10 시험은 Docker 29.1.3에서 이 태그를 로컬 이미지 ID로 해석해 사용했다. 재현 실행은 Docker 버전과 실제 사용한 이미지 ID도 출력한다. 시험 컨테이너는 네트워크·포트·볼륨 연결 없이 실행하고 정리한다.

입력은 `00001 `부터 `04600 `까지의 번호, `X` 8,192개, 개행으로 구성한 4,600줄이다. 파일 3개, 파일당 10 MiB에 한 레코드 여유 10,000바이트를 더한 크기 미만, 오래된 첫 줄 제거와 마지막 줄 `04600` 조회를 검사한다. JSON 메타데이터에 따라 전체 바이트와 남은 첫 줄 번호는 달라질 수 있다.

```bash
ssh -T -o ClearAllForwardings=yes -o BatchMode=yes -o ConnectTimeout=7 edo2 \
  'sudo -n nice -n 15 python3 -B -' <<'PY'
import json
from pathlib import Path
import subprocess
import uuid


def docker(*args):
    result = subprocess.run(["docker", *args], check=True, capture_output=True, text=True)
    return result.stdout.strip()


name = "edo-log-rotation-713-" + uuid.uuid4().hex[:8]
image = docker("image", "inspect", "--format", "{{.Id}}", "nginx:1.30.6-alpine")
print("docker_version", docker("version", "--format", "{{.Server.Version}}"), "image_id", image, flush=True)
created = False
try:
    docker("create", "--name", name, "--pull=never", "--network=none", "--read-only",
           "--cap-drop=ALL", "--pids-limit=32", "--memory=32m", "--cpus=0.25",
           "--log-driver=json-file", "--log-opt=max-size=10m", "--log-opt=max-file=3",
           "--entrypoint", "/bin/sh", image, "-c",
           r'''awk 'BEGIN {
               for (j=0; j<8192; j++) payload=payload "X";
               for (i=1; i<=4600; i++) printf "%05d %s\n", i, payload
           }' ''')
    created = True
    docker("start", "--attach", name)
    assert docker("inspect", "--format", "{{.State.Status}}:{{.State.ExitCode}}", name) == "exited:0"
    logging = json.loads(docker("inspect", "--format", "{{json .HostConfig.LogConfig}}", name))
    log = Path(docker("inspect", "--format", "{{.LogPath}}", name))
    sizes = [path.stat().st_size for path in log.parent.glob(log.name + "*")]
    first = last = None
    with subprocess.Popen(["docker", "logs", name], stdout=subprocess.PIPE) as process:
        for line in process.stdout:
            if first is None:
                first = int(line[:5])
            last = int(line[:5])
    assert process.returncode == 0
    assert logging == {"Type": "json-file", "Config": {"max-size": "10m", "max-file": "3"}}
    assert len(sizes) == 3 and max(sizes) < 10 * 1024 * 1024 + 10000
    assert first is not None and first > 1 and last == 4600
    print("rotation_verified", "file_count", len(sizes), "total_bytes", sum(sizes),
          "oldest_fixture_line", first, "latest_fixture_line", last, flush=True)
finally:
    if created:
        docker("rm", "-f", name)
        assert not docker("ps", "--all", "--quiet", "--filter", f"name=^/{name}$")
        print("fixture_removed", flush=True)
PY
```

종료 코드 `0`과 `rotation_verified file_count 3 … latest_fixture_line 4600`, `fixture_removed`를 확인한다. 기존 시험의 전체 크기와 첫 줄 번호는 [713 검증 기록](../../docs/plans/S15P31E102-713.md#검증과-적용-상태)에 있다.

## edo2 단계별 적용

edo2의 기존 Compose 서비스에 아래 옵션을 하나씩 추가한다. Docker 데몬은 그대로 실행하므로 각 단계에서 지정한 컨테이너의 재생성만 필요하다. 이 경로의 회전 설정은 Compose에 지정한 서비스에 적용된다. 호스트 기본값을 바꾸는 작업은 별도 변경 창에서 판단한다. [Compose 재생성과 볼륨 보존](https://docs.docker.com/reference/cli/docker/compose/up/)

```yaml
logging:
  driver: json-file
  options:
    max-size: "10m"
    max-file: "3"
```

### 시작 전 확인

- edo2가 대기계이며 승격·백업·배포 작업이 진행 중인지 확인한다. edo1 공개 웹·API와 edo2 대기 웹·API, 복제 상태가 정상일 때 시작한다.
- 각 단계 직전에 적용할 Compose 파일 원본과 대상 컨테이너 ID·이미지 ID·로그 설정·볼륨·네트워크를 기록한다. 재생성으로 사라질 로그 중 보존 대상은 기존 정책에 맞춰 운영 서버의 제한된 경로에 보관한다.
- 기존 파일과 같은 디렉터리에서 후보 파일을 검증한다. `docker compose -f "$compose_file" config --quiet`를 실행하고 병합된 설정에서 대상의 `logging`만 달라지는지 확인한다. 현재 실행 이미지 ID와 로컬 태그의 이미지 ID가 다르거나 원본과 실행 설정이 어긋나면 중단하고 차이를 먼저 해결한다. 전체 렌더링 결과에는 환경 값이 들어가므로 서버 안에서 비교한다.

### 적용 순서

| 순서 | 대상 | 다음 단계 조건 |
| --- | --- | --- |
| 1 | 대기 OMR `omr` | 새 컨테이너에서 회전 옵션과 healthcheck를 확인한다. 다른 컨테이너 ID가 유지되고 대기 API와 복제가 정상인지 확인한다. |
| 2 | 대기 `ai` → `playback` → `mysql` → `backend` → `frontend` | 한 번에 한 서비스만 전환한다. 각각의 healthcheck 또는 HTTPS 응답을 확인한다. 대기 MySQL 전환 뒤에는 백엔드 준비 상태가 돌아온 뒤 진행한다. |
| 3 | 운영 읽기 전용 복제 MySQL `mysql` | 별도 변경 구간에서 복제 전후의 볼륨·읽기 전용·GTID를 아래 기준으로 확인한다. |

대기 프로젝트 원본은 `/opt/eolssu-standby-579/compose.yml`, 복제 프로젝트 원본은 `/opt/eolssu-replication-579/replica.compose.yml`이다. 한 서비스의 후보가 검증되면 해당 원본을 교체하고 지정한 서비스만 적용한다. 단계 직전 원본은 다음 롤백에 사용한다. `compose_file`과 `service`를 이번 단계의 파일과 서비스로 지정한다.

OMR·AI·음원 재생·백엔드·프런트엔드에는 아래 명령을 사용한다.

```sh
sudo docker compose -f "$compose_file" config --quiet &&
    sudo docker compose -f "$compose_file" up -d --no-deps --pull never --no-build --wait --wait-timeout 125 "$service" &&
    sudo docker compose -f "$compose_file" ps "$service"
```

대기·복제 MySQL에는 `mysql_action=apply`를 지정하고, 롤백에는 `mysql_action=rollback`을 지정한 뒤 아래 블록을 한 번 실행한다. 조회가 성공해 Compose 서비스 컨테이너가 하나로 확인되고 상태가 `running`일 때 재생성한다. 롤백에서는 `exited`가 확인된 경우도 진행하며, 종료 이벤트 조회를 생략한 이유를 출력한다. 정상 종료에 최대 60초를 주고 새 컨테이너의 기동·헬스체크에는 별도로 최대 125초를 준다. [Compose 실행 옵션](https://docs.docker.com/reference/cli/docker/compose/up/) · [종료 유예](https://docs.docker.com/reference/compose-file/services/#stop_grace_period)

```sh
(
    old_mysql_id=$(sudo docker compose -f "$compose_file" ps --all --quiet mysql) || exit 1
    case "$old_mysql_id" in
        ''|*[!0-9a-f]*)
            printf '%s\n' 'MySQL 컨테이너 ID를 하나로 확인할 수 없습니다.' >&2
            exit 1
            ;;
    esac

    old_mysql_state=$(sudo docker inspect --type container \
        --format '{{index .Config.Labels "com.docker.compose.oneoff"}}:{{.State.Status}}' "$old_mysql_id") || exit 1
    case "${mysql_action-}:$old_mysql_state" in
        apply:False:running|rollback:False:running|rollback:False:exited) ;;
        *)
            printf '%s\n' 'MySQL 작업 유형과 서비스 컨테이너 상태를 확인하세요.' >&2
            exit 1
            ;;
    esac

    sudo docker compose -f "$compose_file" config --quiet || exit 1
    change_started=$(date -u +%Y-%m-%dT%H:%M:%S.%NZ) || exit 1
    up_status=0
    sudo docker compose -f "$compose_file" up -d --no-deps --pull never --no-build --timeout 60 --wait --wait-timeout 125 mysql || up_status=$?

    diagnostic_status=0
    if [ "$old_mysql_state" = False:running ]; then
        change_finished=$(date -u +%Y-%m-%dT%H:%M:%S.%NZ) &&
            sudo docker events --since "$change_started" --until "$change_finished" \
                --filter type=container --filter "container=$old_mysql_id" --filter event=kill --filter event=die \
                --format '{{.Action}} signal={{index .Actor.Attributes "signal"}} exit={{index .Actor.Attributes "exitCode"}}' ||
            diagnostic_status=$?
    elif [ "$up_status" -eq 0 ]; then
        printf '롤백 시작 전에 MySQL %s의 exited 상태를 확인했습니다.\n' "$old_mysql_id" || diagnostic_status=$?
    fi

    if [ "$up_status" -ne 0 ]; then
        exit "$up_status"
    fi
    exit "$diagnostic_status"
)
```

적용 또는 롤백 직전 MySQL이 실행 중이었다면 `up --wait`가 실패해도 기존 ID로 종료 이벤트를 조회한다. `up`이 실패하면 그 종료 코드를 반환한다. `up`이 성공하고 종료 이벤트 조회가 실패하면 조회 실패를 반환한다. `die`의 종료 코드가 `0`이고 `kill`의 시그널 `9`가 없을 때 정상 종료로 판정한다. 종료 증거가 없거나 강제 종료가 관측되면 다음 단계를 보류한다. 롤백 전 기존 MySQL의 `exited`를 확인했고 `up`도 성공했으면 그 상태를 기록하고 새 MySQL의 기동과 복제를 확인한다. [Docker 이벤트 조회](https://docs.docker.com/reference/cli/docker/system/events/)

대상에 `json-file`과 `max-size=10m`·`max-file=3`가 적용됐는지 `docker inspect`로 확인한다. 같은 이미지와 볼륨·네트워크를 사용하고 다른 컨테이너 ID가 유지됐는지도 대조한다. 대기 웹은 `https://localhost:18443/`, 대기 API는 같은 주소의 `/api/actuator/health/readiness`와 `/api/terms`를 대기계 인증서 `/opt/eolssu-standby-579/tls/localhost.crt`로 확인한다. 세 경로 모두 HTTP 200이어야 하며, 준비 상태의 `status`는 `UP`이어야 한다.

### 복제 MySQL 전후 확인

1. 각 단계 전후에 아래 점검을 실행한다. 두 유닛과 복제 점검기가 모두 성공해야 다음 단계로 갈 수 있다. 복제 MySQL 전환 전에는 `@@GLOBAL.read_only=1`, `@@GLOBAL.super_read_only=1`, `@@GLOBAL.gtid_mode=ON`, `@@GLOBAL.server_id=57902`도 확인한다. 복제본의 실행 GTID, `eolssu-replica-579_db` 볼륨, 고정 네트워크 주소를 기록한다.

   ```sh
   sudo systemctl is-active eolssu-replication-tunnel.service &&
       sudo systemctl is-active eolssu-replication-watch.timer &&
       sudo /usr/bin/python3 -B /opt/eolssu-replication-579/check-replication.py
   ```

2. 복제 프로젝트의 `mysql`만 재생성한 뒤 같은 볼륨과 고정 주소, 읽기 전용·GTID 설정을 확인한다. MySQL 준비 상태를 확인한 직후 감시 조회의 기준 시각을 잡고, 두 유닛을 각각 확인한다. 기준 시각부터 최대 60초 동안 아래 journal 조회에서 시작과 정상 완료(`Starting` → `Finished`)가 모두 찍힌 서로 다른 두 실행을 확인한다. 실패가 끼면 연속 성공 횟수를 다시 센다. 점검기는 수신·적용 모두 `Yes`, 양쪽 오류 번호 `0`, 원본 ID `1`, GTID 자동 위치 `1`, 지연 `0–10초`를 검사한다. 두 실행의 완료 시각을 기록하고 1번의 전체 점검을 다시 실행한다.

   ```sh
   watch_since=$(date +%s.%6N)
   watch_until=$(date -d '+60 seconds' +%s.%6N)
   # 아래 조회만 반복해 기준 시각 이후의 감시 실행을 확인한다.
   sudo journalctl -u eolssu-replication-watch.service --since "@$watch_since" --until "@$watch_until" \
       --output=short-iso-precise --no-pager JOB_TYPE=start
   ```

3. edo1 원본에서 읽기 쿼리 `SELECT @@GLOBAL.gtid_executed`로 재생성 이후의 기준점을 잡는다. edo2 복제본에서 `WAIT_FOR_EXECUTED_GTID_SET(기준점, 30)`이 `0`을 반환하고, `GTID_SUBSET(재생성 전 복제본 GTID, @@GLOBAL.gtid_executed)`가 `1`인지 확인한다. 기존 DB 접근 경로를 사용하며 작업 기록에는 판정과 소요 시간만 남긴다. 원본 기준점에 새 GTID가 없으면 기존 기준점 도달로 기록한다. [MySQL 복제 상태](https://dev.mysql.com/doc/refman/8.4/en/show-replica-status.html) · [GTID 함수](https://dev.mysql.com/doc/refman/8.4/en/gtid-functions.html)

### 단계별 롤백

- **중단 기준** · 후보 구성이나 사전 점검이 실패하면 교체를 보류한다. 적용 후 기동·HTTPS·로그 옵션·MySQL 종료 증거·복제 점검·감시 2회·GTID 검증 중 하나라도 실패하거나, 이미지·볼륨·네트워크·다른 컨테이너 ID가 예상과 다르면 다음 단계로 넘어가지 않는다.
- **파일과 서비스 복구** · 변경한 서비스의 기동이나 설정 문제로 확인됐고 원래 이미지·볼륨·네트워크가 식별되면 단계 직전 Compose 파일을 복원한다. 해당 서비스만 위의 적용 명령으로 되돌리고 MySQL에는 같은 종료 유예를 적용한다. 이전 로그 옵션과 HTTPS·복제를 다시 확인한다.
- **볼륨·네트워크 불일치** · 현재 상태와 복제 볼륨을 보존한다. 사전 기록과 대조해 원래 볼륨·네트워크·고정 주소를 확인한 뒤 서비스 복구 여부를 판단한다.
- **복제 확인 실패** · 볼륨과 네트워크가 일치하고 비활성 터널이나 타이머가 원인이면 Docker 상태와 기존 유닛 구성을 확인한 뒤 해당 유닛을 복구한다. 새 기준 시각으로 감시 2회와 GTID를 재검증한다. 수신·적용 오류, 지연, 감시 미통과 또는 GTID 불일치가 해소되지 않으면 복제 장애로 전환하고 볼륨과 상태를 보존한다.
- 단계별 중단 시간, 다른 컨테이너 ID 유지 여부, 복제 재접속·GTID 확인 시간, 새 로그 옵션을 기록한다. edo2 결과를 검토한 뒤 edo1 적용 여부를 결정한다.

## edo1 복제 프록시 전환과 복구

이 절차는 `eolssu-replication-proxy-579`의 로그 회전만 전환할 때 사용한다. 프록시가 바뀌는 동안 edo2의 기존 복제 수신 연결은 끊겼다가 다시 붙는다. 원본·복제 MySQL, SSH 터널, 감시 타이머는 각자 계속 실행한다.

### 적용 전 확인

- edo1의 원본 프록시 ID와 이미지 ID, `/opt/eolssu-replication-579/source-stream.conf`의 파일 해시, 컨테이너에 마운트된 설정 해시를 기록한다. `127.0.0.1:13306` 수신, `eolssu-runtime_default` 네트워크와 `mysql:3306` 대상, 기존 웹·API·원본 MySQL 상태를 확인한다. 배포·백업·승격이 진행 중이면 전환을 보류한다.
- edo2에서 터널 서비스와 감시 타이머를 각각 확인하고 기존 `check-replication.py`를 실행한다. 원본 `server_id=1`, 복제본 `server_id=57902`, `read_only=1`, `super_read_only=1`, 양쪽 GTID 모드 `ON`을 확인한다. 복제 MySQL ID·볼륨·네트워크 주소와 원본 및 복제본의 실행 GTID를 기록한다. 비밀번호와 원시 GTID는 화면이나 공유 기록에 출력하지 않는다. [기존 복제 점검 명령](#복제-mysql-전후-확인)

### 후보 준비와 전환

작업 창을 잡기 전에 저장소 루트에서 아래 명령으로 중지 후보를 검증한다. 실행기는 원본의 이미지 ID와 설정으로 Docker Engine의 `containers/create`를 호출하고 `AttachStdout`·`AttachStderr`를 둘 다 `false`로 지정한다. 검증 명령은 일회용 후보를 `created` 상태에서 비교하고 ID를 확인해 삭제한다. [Docker Engine API](https://docs.docker.com/reference/api/engine/version/v1.56/#tag/Container/operation/ContainerCreate)

```bash
python3 -B infra/docker/rotate-replication-proxy-20261113.py --check-candidate
```

승인된 작업 창의 실행기에서도 같은 후보 생성과 비교를 수행한다. 증거는 호스트의 root 전용 경로에 보관한다.

1. 원본이 실행 중이며 후보가 시작 이력 없이 `created`인지 확인한다. 두 ID로 이미지, 실행 명령·사용자·라벨, 표준 출력·오류 연결, 포트, 네트워크 모드, 마운트·설정 해시, 읽기 전용 루트, `/tmp` tmpfs, CPU·메모리·스왑 제한, 재시작 정책을 대조한다. 예상 변경은 로그 옵션이다. 자동 생성 호스트명은 ID에 따라 달라진다. cgroup v2에서 `OomKillDisable`의 `null`과 `false`는 기본값 표기 차이로 기록한다. 다른 차이가 있거나 기존 컨테이너 ID·롤백 이름을 확보하지 못하면 전환을 보류한다. [Docker cgroup v2](https://docs.docker.com/engine/containers/runmetrics/#running-docker-on-cgroup-v2)
2. 긴 복제 연결을 기다리는 이미지 기본 `SIGQUIT` 대신 전환 시에만 `sudo docker stop --signal SIGTERM --timeout 10 "$old"`를 쓴다. 원본이 `exited`이고 종료 코드가 `0`인지 확인한 뒤 이름을 `"$proxy-before-713-$stamp"`로 바꾼다. 후보를 원래 이름으로 바꾸고 후보 ID로 시작한다. 기존 포트와 연결된 네트워크, `json-file`의 `10m`·`3`을 확인한다. 원본은 중지 상태로 보관한다. [Nginx 종료 신호](https://nginx.org/en/docs/control.html) · [Docker 종료 옵션](https://docs.docker.com/reference/cli/docker/container/stop/)
3. 새 프록시 시작 뒤 10초 안에 `127.0.0.1:13306` 수신을 확인한다. 원본·복제 MySQL의 ID와 볼륨, 프록시 외 컨테이너 ID를 직전 기록과 대조하고 웹·약관 API HTTP 200과 준비 상태 `UP`을 확인한다.

### edo2 검증과 롤백 판단

- **복제 회복** · 로컬 수신이 확인되면 edo2에서 기준 시각을 잡고 터널과 감시 타이머를 개별 확인한다. 60초 이내에 감시가 연속 2회 시작·정상 완료됐는지 확인한다. 수신·적용 `Yes`, 양쪽 오류 번호 `0`, 원본 `172.30.79.1:13306` 및 ID `1`, GTID 자동 위치 `1`, 지연 0–10초가 기준이다. 운영 설정의 수신 타임아웃은 15초, 원본 재접속 간격은 3초, 감시 주기는 20초다. 연결 교체 구간의 일시적인 수신 재접속은 이 시간 내 회복 여부로 판정한다.
- **GTID** · 전환 후 원본의 실행 GTID를 읽어 `WAIT_FOR_EXECUTED_GTID_SET(기준점, 30)`이 `0`을 반환하는지 확인한다. 전환 전 복제본 GTID는 `GTID_SUBSET(이전 복제본 GTID, 현재 복제본 GTID) = 1`로 확인한다. 원본의 전후 차집합이 비어 있으면 기존 기준점 도달로 기록한다. 새로운 원본 거래가 관찰됐으면 복제본 도달 여부도 기록한다. [GTID 함수](https://dev.mysql.com/doc/refman/8.4/en/gtid-functions.html)
- **복구 개시** · 후보 기동·로컬 수신·실행 설정 검증 실패, 다른 컨테이너 상태 변화, 운영 웹·API 악화, 잘못된 원본·적용 오류, 시간 내 복제 감시·GTID 기준 미달이면 프록시만 되돌린다. 후보를 ID로 확인해 실행 중일 때만 `SIGTERM`으로 정지하고 보관 이름으로 바꾼다. 원본 ID의 기존 컨테이너에 원래 이름을 돌려주고 시작한다. 원본이 계속 실행 중이면 그 상태에서 후보 교체를 보류한다. 포트 소유자나 원본 ID가 예상과 다르면 강제로 정지하거나 삭제하기 전에 상태를 보존한다.
- **복구 완료** · 기존 프록시 ID·포트·로그 옵션과 웹·API·원본 MySQL을 확인한 뒤 복제 감시 2회와 GTID를 다시 확인한다. 회복되지 않으면 복제 장애 대응으로 전환해 양쪽 DB와 기존 채널·볼륨을 보존한다. 프록시 복구에는 복제 채널 재구성, 데이터 초기화나 승격이 포함되지 않는다.

## 호스트 기본값 적용 절차

1. 변경 창 전에 현재 `/etc/docker/daemon.json`의 유무, 데몬 실행 인자, 컨테이너별 드라이버·옵션을 확인한다. 기존 파일이 생겼으면 다른 설정과 기존 로깅 선택을 검토한 뒤 호환되는 경우에만 키를 병합한다. 파일을 통째로 교체하기 전에 원본을 백업하고, 원래 파일이 없었다면 그 상태를 기록한다.
2. 구성 후보를 `sudo dockerd --validate --config-file <후보 파일>`로 검사한다. 데몬 재시작 전 대기계·복제·배포 작업과 서비스별 재기동 영향을 확인한다. 승인된 변경 창에 설정을 설치하고 Docker 데몬을 재시작한 뒤 필수 서비스와 복제 상태를 확인한다.
3. 기본값은 새로 생성되는 컨테이너부터 적용된다. 기존 컨테이너는 보존이 필요한 로그를 먼저 확인하고 다음 승인된 배포나 재생성에서 서비스별로 전환한다. 재생성 직후 헬스체크와 `docker inspect --format '{{json .HostConfig.LogConfig}}' <컨테이너>`에서 드라이버·옵션을 확인한다. 컨테이너를 제거하거나 재생성하기 전 해당 컨테이너의 로컬 로그 보존 필요 여부를 확인한다.

복구 시 기존 데몬 파일 또는 파일이 없던 상태를 되돌리고 구성 검증과 재시작을 반복한다. 변경 이후에 생성된 컨테이너는 기존대로 자동 복구되지 않으므로 해당 서비스의 다음 재생성 시 드라이버·옵션을 다시 확인한다. [작업·검증 기록](../../docs/plans/S15P31E102-713.md)
