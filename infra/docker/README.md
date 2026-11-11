# Docker 기본 로그 회전

`daemon-log-rotation.json`은 edo1·edo2의 신규 `json-file` 컨테이너에 파일당 10 MiB, 최대 3개 파일의 기본값을 제공한다. 이 값은 크기 기준이며, 기존 rsyslog의 앱 일반 로그 최대 14일 정책은 계속 별도로 적용한다. `syslog`와 `local`을 명시한 서비스에는 각 드라이버의 설정을 사용한다. [Docker 로깅 설정](https://docs.docker.com/engine/logging/configure/) · [json-file 옵션](https://docs.docker.com/engine/logging/drivers/json-file/)

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
    sudo docker compose -f "$compose_file" up -d --no-deps --pull never --no-build --timeout 60 --wait --wait-timeout 125 mysql || exit 1

    if [ "$old_mysql_state" = False:running ]; then
        change_finished=$(date -u +%Y-%m-%dT%H:%M:%S.%NZ) || exit 1
        sudo docker events --since "$change_started" --until "$change_finished" \
            --filter type=container --filter "container=$old_mysql_id" --filter event=kill --filter event=die \
            --format '{{.Action}} signal={{index .Actor.Attributes "signal"}} exit={{index .Actor.Attributes "exitCode"}}'
    else
        printf '롤백 시작 전에 MySQL %s의 exited 상태를 확인했습니다.\n' "$old_mysql_id"
    fi
)
```

MySQL이 실행 중이었다면 `die`의 종료 코드가 `0`이고 `kill`의 시그널 `9`가 없을 때 정상 종료로 판정한다. 종료 증거가 없거나 강제 종료가 관측되면 다음 단계를 보류한다. 롤백에서 `exited`가 확인됐으면 해당 상태를 기록하고 새 MySQL의 기동과 복제 확인을 진행한다. [Docker 이벤트 조회](https://docs.docker.com/reference/cli/docker/system/events/)

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

## 호스트 기본값 적용 절차

1. 변경 창 전에 현재 `/etc/docker/daemon.json`의 유무, 데몬 실행 인자, 컨테이너별 드라이버·옵션을 확인한다. 기존 파일이 생겼으면 다른 설정과 기존 로깅 선택을 검토한 뒤 호환되는 경우에만 키를 병합한다. 파일을 통째로 교체하기 전에 원본을 백업하고, 원래 파일이 없었다면 그 상태를 기록한다.
2. 구성 후보를 `sudo dockerd --validate --config-file <후보 파일>`로 검사한다. 데몬 재시작 전 대기계·복제·배포 작업과 서비스별 재기동 영향을 확인한다. 승인된 변경 창에 설정을 설치하고 Docker 데몬을 재시작한 뒤 필수 서비스와 복제 상태를 확인한다.
3. 기본값은 새로 생성되는 컨테이너부터 적용된다. 기존 컨테이너는 보존이 필요한 로그를 먼저 확인하고 다음 승인된 배포나 재생성에서 서비스별로 전환한다. 재생성 직후 헬스체크와 `docker inspect --format '{{json .HostConfig.LogConfig}}' <컨테이너>`에서 드라이버·옵션을 확인한다. 컨테이너를 제거하거나 재생성하기 전 해당 컨테이너의 로컬 로그 보존 필요 여부를 확인한다.

복구 시 기존 데몬 파일 또는 파일이 없던 상태를 되돌리고 구성 검증과 재시작을 반복한다. 변경 이후에 생성된 컨테이너는 기존대로 자동 복구되지 않으므로 해당 서비스의 다음 재생성 시 드라이버·옵션을 다시 확인한다. [작업·검증 기록](../../docs/plans/S15P31E102-713.md)
