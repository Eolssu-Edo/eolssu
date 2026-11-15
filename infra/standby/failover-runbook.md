# edo2 장애 전환·원복 런북

[S15P31E102-930 결정·검증](../../docs/plans/S15P31E102-930.md) · [대기 환경 구성](README.md) · [579 구성·리허설](../../docs/plans/S15P31E102-579.md)

edo1(운영, `43.203.215.107`)이 쓰기를 받지 못할 때 edo2(`15.165.144.7`)의 운영 복제본을 승격해 같은 주소로 서비스를 잇는 절차다. 명령은 저장소 루트 기준이 아니라 각 서버의 경로를 기준으로 한다. 비밀번호·토큰·원시 GTID는 화면이나 공유 기록에 출력하지 않는다.

## 목표와 전제

| 항목 | 기준 |
| --- | --- |
| 복구 목표(RTO) | 장애 선언부터 운영 주소에서 로그인·저장이 성공할 때까지 **30분** |
| 손실 허용(RPO) | 최근 **1분** 이내의 쓰기. 복제가 정상일 때만 지킬 수 있다. |
| 발표 중 장애 | [릴리스 절차](../release/README.md#장애-판단과-전환)대로 **녹화 영상으로 대체**한다. 서버 전환은 발표가 끝난 뒤 판단한다. |
| 주소(DNS) | `k15e102.p.ssafy.io` A 레코드는 SSAFY의 Route 53 `ssafy.io` 존에 있고 TTL은 300초다. 팀에는 편집 권한이 없다. 변경은 SSAFY 담당자에게 요청한다. 담당 경로는 [부탁 글](#dns-부탁-글)로 확인 중이다. |
| 인증서 | edo1 Let's Encrypt 인증서 사본을 쓴다. 사본은 기존 config 백업으로 매일 03:20과 배포 전후에 edo2에 암호화돼 도착한다. |
| 대기 앱 | 평소에는 테스트 DB만 쓴다. 운영 DB·운영 비밀값에는 **승격할 때만** 연결한다. |

DNS 담당 경로가 확인되기 전에 edo1 서버 전체가 멈추면, 아래 절차로 edo2를 준비해도 사용자가 같은 주소로 들어오는 시점은 SSAFY의 반영에 달려 있다. 이 경우 RTO 30분은 보장하지 않는다.

## 사전 준비

평시에 한 번 해 둔다. 서버에 파일을 두는 단계는 운영 변경이므로 승인받은 뒤 한다.

- 로컬 `~/.ssh/config`의 `edo1`은 운영 도메인을 쓴다. DNS를 바꾸면 이 별칭은 edo2로 연결된다. 장애 작업에서는 IP 별칭을 따로 쓴다. `HostKeyAlias`는 기존 edo1 호스트 키 항목을 가리키므로, IP로 붙어도 다른 서버면 연결이 거부된다.

  ```
  Host edo1-ip
      HostName 43.203.215.107
      HostKeyAlias k15e102.p.ssafy.io
      # 나머지 User·IdentityFile은 기존 edo1 항목과 같게
  ```

- `compose.promote.yml`, `replica.promote.compose.yml`, `edo2-domain.nginx.conf`를 저장소와 같은 해시로 edo2의 `/opt/eolssu-standby-579/`에 둔다. 장애 중에는 저장소를 받을 수 없을 수 있다.
- 시연 녹화 영상과 [DNS 부탁 글](#dns-부탁-글)의 장애 시 양식을 발표 PC와 운영 담당 메모에 둔다.

## 1. 판정 (0–5분)

장애 선언 시각(T0)을 UTC로 기록한다. 아래 표에서 상황을 고른다.

```sh
date -u +%FT%TZ
curl -sS -m 5 -o /dev/null -w '%{http_code}\n' https://k15e102.p.ssafy.io/api/actuator/health/readiness
ssh -o ConnectTimeout=5 edo1-ip 'sudo docker ps --format "{{.Names}} {{.Status}}"'
ssh -o ConnectTimeout=5 edo2 'sudo /usr/bin/python3 -B /opt/eolssu-replication-579/check-replication.py && echo replication-ok'
```

| 상황 | 판정 근거 | 조치 |
| --- | --- | --- |
| A. edo1 앱만 장애 | edo1 SSH 정상, MySQL healthy, 백엔드·웹 컨테이너 실패 | **edo2로 전환하지 않는다.** [runtime README](../runtime/README.md)의 재시작·이전 슬롯 복구를 따른다. |
| B. edo1 MySQL 장애 | edo1 SSH 정상, `eolssu-runtime-mysql-1` 실패 | 10분 안에 edo1 MySQL 재시작·복구를 시도한다. 실패하면 edo1 백엔드·Jenkins 백엔드 잡을 멈춘 뒤 C로 간다. |
| C. edo1 서버 전체 장애 | 1분 간격 두 번 모두 운영 HTTPS와 `edo1-ip` SSH가 실패 | 2단계부터 진행한다. |

C로 판단하면 바로 [장애 시 요청 양식](#장애-시-요청-양식)으로 SSAFY에 A 레코드 변경을 요청한다. 반영까지 가장 오래 걸리는 단계이므로 먼저 보낸다.

## 2. 기존 쓰기 차단

edo1에 접속할 수 있으면 운영 쓰기를 멈춘다. Jenkins `eolssu-backend` 잡이 1분마다 develop을 배포해 백엔드를 다시 띄우므로, Jenkins 관리 화면에서 잡을 먼저 일시 중지(Disable)한다.

```sh
ssh edo1-ip 'sudo docker stop eolssu-runtime-backend-1 eolssu-runtime-backend-green-1 2>/dev/null; sudo docker ps --format "{{.Names}}" | grep -c "runtime-backend" || true'
```

접속할 수 없으면 "edo1 쓰기 차단 미확인"으로 기록하고 진행한다. edo1이 다시 살아나면 [원복 0단계](#0-edo1-복귀-직후)를 무엇보다 먼저 실행한다.

## 3. 백업에서 운영 설정·인증서 꺼내기 (edo2)

최신 config 백업에는 edo1의 `/opt/eolssu/.env`, `/etc/letsencrypt`, 활성 JAR·웹이 들어 있다. db 백업의 `accounts.sql`에는 앱 계정 정의가 있다. 기존 복호화 키로 root 전용 디렉터리에만 푼다. 주간 복원 검사와 겹치지 않도록 같은 lock을 잡는다.

```sh
ssh edo2
sudo install -d -m 700 /root/failover-930
sudo python3 -I - <<'EOF'
import os, sys
sys.path.insert(0, '/opt/eolssu-backup'); os.umask(0o077)
import backup
from pathlib import Path
out = Path('/root/failover-930')
with backup.locked(backup.STORE / '.restore.lock'):
    for kind in ('config', 'db'):
        cipher, record = backup.latest(kind)
        plain = out / (kind + '.tgz')
        backup.decrypt(cipher, plain)
        backup.validate_bundle(plain, kind, out / kind)
        plain.unlink()
        print(kind, record['run_id'], 'snapshot', record['snapshot_at'])
EOF
```

- 출력한 config 사본 시각을 기록한다. 마지막 배포보다 오래됐으면 JAR이 DB 스키마보다 뒤처질 수 있다. 이때는 Flyway 실패 로그를 보고 판단한다.
- 대기 앱의 원래 JAR(`a28d80ee`)은 Flyway V30까지만 안다. 운영 DB는 V44(2026-11-15)이므로 **반드시 백업의 운영 JAR을 쓴다.**

## 4. 복제본 승격 (edo2)

복제본이 받은 거래를 모두 적용한 뒤 복제를 끊고 쓰기를 연다. 순서와 SQL은 [로컬 리허설](../../docs/plans/S15P31E102-930.md#로컬-승격-리허설)에서 확인했다.

```sh
cd /opt/eolssu-replication-579
rq() { sudo docker exec -i eolssu-replica-579-mysql-1 sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names'; }

# 4-1. 감시·터널 중지. 원본이 돌아와도 복제본에 다시 붙지 않게 한다.
sudo systemctl disable --now eolssu-replication-watch.timer eolssu-replication-tunnel.service

# 4-2. 마지막으로 적용한 원본 커밋 시각 = 손실 구간의 시작. T0와의 차이를 기록한다.
echo "SELECT MAX(LAST_APPLIED_TRANSACTION_ORIGINAL_COMMIT_TIMESTAMP) FROM performance_schema.replication_applier_status_by_worker;" | rq

# 4-3. 받은 거래를 모두 적용했는지 확인한다. 0이 아니면 멈추고 원인을 본다.
received=$(echo "SELECT RECEIVED_TRANSACTION_SET FROM performance_schema.replication_connection_status WHERE CHANNEL_NAME='';" | rq)
echo "SELECT WAIT_FOR_EXECUTED_GTID_SET('$received', 60);" | rq

# 4-4. 복제 해제와 쓰기 허용
echo "STOP REPLICA; RESET REPLICA ALL; SET PERSIST super_read_only=OFF; SET GLOBAL read_only=OFF;" | rq

# 4-5. 기동 옵션 --read-only=ON을 뺀 정의로 재생성한다. 이 단계를 빼면 재시작 때 다시 읽기 전용이 된다.
sudo sh -c 'REPLICA_ROOT_PASSWORD="$(docker exec eolssu-replica-579-mysql-1 printenv MYSQL_ROOT_PASSWORD)" \
  docker compose -f replica.compose.yml -f replica.promote.compose.yml up -d --pull never --wait'
echo "SELECT @@read_only, @@super_read_only, (SELECT COUNT(*) FROM performance_schema.replication_connection_configuration);" | rq   # 0 0 0

# 4-6. 앱 계정. 초기 사본은 eolssu 스키마만 복사해 복제본에는 root만 있다.
U=$(sudo sed -n 's/^MYSQL_USER=//p' /root/failover-930/config/files/opt/eolssu/.env | tr -d '"'); U=${U:-eolssu}
sudo grep -a -E "^(CREATE USER|GRANT .* TO) \`$U\`@" /root/failover-930/db/accounts.sql | rq
echo "SELECT COUNT(*) FROM mysql.user WHERE user='$U';" | rq   # 1 이상
```

복제 수신이 장애 전에 이미 끊겨 마지막 커밋 시각이 1분보다 오래됐으면 RPO를 넘은 것이다. 3단계 db 백업의 `snapshot_at`이 그보다 최신이면 [579 백업 복원 경로](../../docs/plans/S15P31E102-579.md#2026-11-09-백업-복원장애-전환원복-리허설)로 바꿀지 판단한다.

## 5. 대기 앱을 운영 모드로 기동 (edo2)

```sh
cd /opt/eolssu-standby-579
F=/root/failover-930/config/files
JAR=$(sudo sed -n 's/^BACKEND_JAR=//p' $F/opt/eolssu/.env | tr -d '"')
WEB=$(sudo sed -n 's/^WEB_DIST_DIR=//p' $F/opt/eolssu/.env | tr -d '"')
sudo install -d -m 755 releases/promote
sudo install -m 644 "$F$JAR" releases/promote/app.jar
sudo cp -a "$F$WEB" releases/promote/web && sudo find releases/promote/web -type d -exec chmod 755 {} + && sudo find releases/promote/web -type f -exec chmod 644 {} +
sudo sh -c "cat $F/opt/eolssu/.env > promote.env && printf 'STANDBY_BACKEND_JAR=./releases/promote/app.jar\nSTANDBY_WEB_DIR=./releases/promote/web\n' >> promote.env && chmod 600 promote.env"

sudo docker compose --env-file .env --env-file promote.env -f compose.yml -f compose.promote.yml config --quiet
sudo docker compose --env-file .env --env-file promote.env -f compose.yml -f compose.promote.yml \
  up -d --pull never --wait --wait-timeout 180 backend frontend
sudo curl -fsS --cacert tls/localhost.crt https://localhost:18443/api/actuator/health/readiness
```

- 뒤의 `--env-file`이 앞의 값을 덮는다. 운영 `.env`가 없거나 `MYSQL_PASSWORD`·`AUTH_JWT_SECRET`이 비면 `config` 단계에서 멈춘다.
- 예약 작업은 꺼진 채 기동한다(`PROMOTE_SCHEDULING_ENABLED` 기본 `false`). 9단계에서 켠다.
- AI·OMR·재생 이미지는 edo2에 있는 2026-11-09 판이다. 채점 결과가 오지 않으면 운영 이미지와의 차이를 의심한다.

## 6. 인증서와 운영 도메인 가상 호스트 (edo2)

```sh
C=/root/failover-930/config/files/etc/letsencrypt/live/k15e102.p.ssafy.io
D=/etc/eolssu-failover-tls/k15e102.p.ssafy.io
sudo install -d -m 700 $D
sudo install -m 644 $C/fullchain.pem $D/fullchain.pem
sudo install -m 600 $C/privkey.pem $D/privkey.pem
sudo openssl x509 -in $D/fullchain.pem -noout -subject -enddate -checkend 604800   # 7일 이상 남아야 한다
sudo bash -c "cmp <(openssl x509 -in $D/fullchain.pem -pubkey -noout) <(openssl pkey -in $D/privkey.pem -pubout) && echo key-matches"
sudo install -d -m 755 /var/www/eolssu-failover-acme
sudo install -m 644 /opt/eolssu-standby-579/edo2-domain.nginx.conf /etc/nginx/sites-enabled/eolssu-failover
sudo nginx -t && sudo systemctl reload nginx
```

## 7. DNS 반영 전 검증 (로컬)

DNS가 아직 edo1을 가리켜도 `--resolve`로 edo2를 직접 검사할 수 있다. 공개 인증서 검증을 끄지 않는다.

```sh
R='--resolve k15e102.p.ssafy.io:443:15.165.144.7'
curl -fsS $R https://k15e102.p.ssafy.io/api/actuator/health/readiness        # {"status":"UP"}
curl -sS $R -o /dev/null -w '%{http_code}\n' https://k15e102.p.ssafy.io/api/terms   # 200
curl -sS $R -o /dev/null -w '%{http_code}\n' http://k15e102.p.ssafy.io/       # 308
```

운영 점검 계정으로 로그인과 쓰기(연습 세션 생성) 한 건을 확인한다. 이 쓰기는 운영 DB에 남으므로 점검 계정만 쓴다.

## 8. DNS 반영 확인

```sh
dig +norecurse @ns-1469.awsdns-55.org k15e102.p.ssafy.io A +noall +answer
dig @1.1.1.1 k15e102.p.ssafy.io A +short
dig @8.8.8.8 k15e102.p.ssafy.io A +short
```

권한 서버에 `15.165.144.7`이 보인 뒤에도 기존 캐시는 TTL 300초 동안 edo1을 가리킬 수 있다. 휴대폰 LTE와 다른 네트워크에서 7단계 검사를 `--resolve` 없이 다시 실행한다. 이 시각을 서비스 복구 시각으로 기록한다.

## 9. 마무리

- edo1의 백엔드가 멈췄거나 서버가 꺼졌음을 확인한 뒤에만 예약 작업을 켠다. `promote.env`에 `PROMOTE_SCHEDULING_ENABLED=true`를 추가하고 5단계의 `up -d ... backend`를 다시 실행한다.
- `/root/failover-930`에는 복호화한 운영 비밀값이 있다. 앱이 정상 기동한 뒤 `sudo rm -rf /root/failover-930`으로 지운다. 백업 원본은 그대로 남는다.
- 승격 기간에는 edo2의 운영 DB를 다른 서버로 보내는 백업이 없다. 원복 전까지 하루 한 번 `mysqldump`를 edo2 root 전용 경로에 남긴다.
- T0, DNS 요청·반영, 승격, 앱 기동, 첫 쓰기, 손실 구간(4-2)을 장애 기록에 남긴다.

## 승격 기간의 인증서 갱신

- 평소 갱신 책임은 edo1의 `certbot.timer`다. 만료 30일 전에 갱신하고 deploy hook으로 edo1 Nginx를 다시 읽힌다. 사본은 다음 config 백업으로 edo2에 간다.
- 승격 중에는 edo2 운영자가 책임진다. 복사한 인증서의 만료가 30일 안으로 들어오면, DNS가 edo2를 가리키는 상태에서 edo2의 snap Certbot으로 새로 받는다. 가상 호스트의 두 경로를 `/etc/letsencrypt/live/k15e102.p.ssafy.io/`로 바꾸고 다시 읽힌다.

  ```sh
  sudo certbot certonly --webroot -w /var/www/eolssu-failover-acme -d k15e102.p.ssafy.io \
    --cert-name k15e102.p.ssafy.io --deploy-hook 'systemctl reload nginx'
  ```

- DNS-01 방식은 `ssafy.io` 존의 TXT 권한이 필요해 쓰지 않는다.

## 원복 (edo2 → edo1)

쓰기가 몇 분 멈추는 작업이라 공지한 변경 창에서 한다. 579 리허설에서 DB·녹음 복사는 17.52초였다. 실제 중단 시간은 DNS 반영이 좌우한다.

### 0. edo1 복귀 직후

edo1이 다시 켜지면 다른 무엇보다 먼저 아래를 실행한다. edo1의 예전 DB가 쓰기를 받거나 새 백업처럼 edo2에 전송되는 것을 막는다.

```sh
ssh edo1-ip 'sudo docker stop eolssu-runtime-backend-1 eolssu-runtime-backend-green-1 2>/dev/null; \
  sudo systemctl disable --now eolssu-backup-db.timer eolssu-backup-config.timer'
```

Jenkins `eolssu-backend` 잡도 일시 중지한다. edo1 MySQL 볼륨은 지우지 않고 증거로 보존한다.

### 1–6. 데이터 이동과 진입점 복귀

1. edo2 백엔드를 멈추고 승격 DB를 `SET GLOBAL super_read_only=ON`으로 잠근다.
2. edo2 DB를 덤프한다. `mysqldump --single-transaction --set-gtid-purged=OFF --hex-blob --databases eolssu`를 쓰고, 로컬 장치를 거쳐 edo1로 스트리밍한다. 덤프 파일은 원복이 끝날 때까지 edo2 root 전용 경로에 보관한다.
3. edo1에서 기존 `eolssu` 스키마를 root 전용 경로에 먼저 덤프해 둔 뒤 가져온다. 양쪽에서 모든 테이블의 `CHECKSUM TABLE` 결과를 대조한다.
4. edo1 백엔드를 [runtime README](../runtime/README.md) 절차로 기동하고 `--resolve k15e102.p.ssafy.io:443:43.203.215.107`로 7단계 검사를 한다.
5. SSAFY에 A 레코드를 `43.203.215.107`로 되돌려 달라고 요청한다. 반영되는 동안 edo2에 남은 요청을 edo1로 넘기려면 edo2 가상 호스트의 `proxy_pass`를 `https://43.203.215.107`, `proxy_ssl_name`을 `k15e102.p.ssafy.io`로 바꾼다. 이 중계는 아직 시험하지 않았다.
6. 반영 후 edo1의 백업 타이머와 Jenkins 잡을 다시 켜고 `backup.py send prechange`로 새 사본을 만든다.

### 7. edo2를 다시 대기계로

- 운영 도메인 가상 호스트와 인증서 사본을 지운다: `/etc/nginx/sites-enabled/eolssu-failover`, `/etc/eolssu-failover-tls/`. edo2에서 인증서를 새로 받았다면 `sudo certbot delete --cert-name k15e102.p.ssafy.io`도 실행한다.
- 대기 앱은 `promote.env` 없이 `docker compose --env-file .env -f compose.yml up -d backend frontend`로 격리 모드로 되돌리고, `promote.env`와 `releases/promote`를 지운다.
- 승격했던 복제본 볼륨은 데이터가 edo1과 덤프 파일에 남은 것을 확인한 뒤 사용자 승인을 받아 비운다. 그다음 [README의 초기 사본 절차](README.md#운영-읽기-전용-복제본)로 복제를 다시 구성한다. 터널·감시 타이머를 켜고 원래 `replica.compose.yml`(읽기 전용)로 기동한다.

## DNS 부탁 글

### 사전 문의

SSAFY 운영 담당에게 보낸다. 받은 답은 [930 기록](../../docs/plans/S15P31E102-930.md)과 이 문서의 전제 표에 반영한다.

> **제목:** [S15P31E102 / 얼쑤] 운영 도메인 A 레코드 장애 전환 절차 문의
>
> 안녕하세요. S15P31E102 팀(서비스명 얼쑤)입니다.
> 저희 서비스 주소 `k15e102.p.ssafy.io`는 현재 A 레코드 `43.203.215.107`(서버 1)을 가리킵니다.
> 서버 1이 장애로 멈추면 저희가 운영하는 대기 서버 `15.165.144.7`(서버 2)로 주소를 옮기려고 합니다. 아래 세 가지를 여쭙습니다.
>
> 1. 장애 때 A 레코드 값을 바꿔 달라고 요청할 담당자·채널과, 평일 주간·야간·주말에 처리되는 대략적인 시간
> 2. 가능하다면 정한 시간에 한 번 연습할 수 있을지 (예: `15.165.144.7`로 바꾸고 10분 뒤 `43.203.215.107`로 되돌림). 연습 동안 서버 2도 같은 서비스와 인증서로 응답하도록 준비하겠습니다.
> 3. 또는 이 레코드 하나(`k15e102.p.ssafy.io`, A)만 바꿀 수 있는 권한을 팀에 위임받을 방법이 있는지
>
> TTL은 지금처럼 300초로 두면 됩니다. 감사합니다.

### 장애 시 요청 양식

> **제목:** [긴급] `k15e102.p.ssafy.io` A 레코드 변경 요청 (S15P31E102)
>
> - 대상: `k15e102.p.ssafy.io` A 레코드
> - 변경: `43.203.215.107` → `15.165.144.7`, TTL 300 유지
> - 사유: 서버 1 장애(HH:MM KST부터). 서버 2에서 같은 인증서로 응답하는 것을 확인함(HH:MM)
> - 원복: 서버 1 복구 후 따로 요청드리겠습니다.
> - 요청자·연락처: (이름, 연락처)
