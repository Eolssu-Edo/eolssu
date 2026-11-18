# Grafana·Prometheus

운영 서버 edo1에서 Grafana와 Prometheus를 띄웁니다. Grafana는 운영 MySQL(읽기 계정)과 Prometheus를 읽고, Prometheus는 백엔드 수치를 모읍니다. 작업: [S15P31E102-69](https://ssafy.atlassian.net/browse/S15P31E102-69). Metabase는 [S15P31E102-202](https://ssafy.atlassian.net/browse/S15P31E102-202)에서 빼고, 곡별 통계와 테이블 보기를 Grafana로 옮겼습니다.

| 서비스 | 서버 안 주소 | 이미지 |
| --- | --- | --- |
| Grafana | 127.0.0.1:3000 | grafana/grafana:11.3.0 |
| Prometheus | 127.0.0.1:3001 | prom/prometheus:v3.5.0 |
| node exporter | 포트 없음(Docker 내부 `node-exporter:9100`) | prom/node-exporter:v1.9.1 |

서비스는 서버 밖에 열지 않고 SSH 터널로만 접속합니다. 운영 스택(`eolssu-runtime`)의 내부 네트워크 `eolssu-runtime_default`에 붙어 MySQL을 `mysql:3306`, 활성 백엔드의 수치는 `frontend:8081`로 읽습니다.

Prometheus는 15초마다 `frontend:8081/api/actuator/prometheus`와 서버 수집기 `node-exporter:9100`을 읽고 15일 보관합니다([prometheus.yml](prometheus.yml)). 백엔드 수치 주소 `/api/actuator/prometheus`는 인터넷 쪽에서 Nginx가 404로 막습니다([infra/runtime](../runtime/README.md)).

서버 수집기는 서버 루트를 읽기 전용으로만 보고 포트를 열지 않습니다(`/:/host:ro,rslave`). `rslave`는 서버 루트가 shared 마운트여야 하며 edo1은 shared입니다. Docker Desktop에서는 이 옵션으로 뜨지 않습니다.

## 준비 조건

- 운영 스택 `eolssu-runtime`이 실행 중이어야 합니다. `docker network ls`에 `eolssu-runtime_default`가 보여야 합니다.
- 운영 DB 이름은 `eolssu`입니다.
- Prometheus 수치를 보려면 백엔드가 S15P31E102-202 이후 버전이어야 합니다. 그 전 버전은 수치 주소가 없어 대상이 `down`으로 보입니다.

## 1. MySQL 계정 만들기 (처음 한 번)

운영 MySQL에 쓰는 작업이므로 운영 스택 담당과 먼저 맞춥니다.

| 계정·DB | 권한 | 용도 |
| --- | --- | --- |
| `grafana` | `eolssu`의 대시보드가 읽는 테이블과 `users`의 개인정보 제외 열 | Grafana 데이터 소스 |

비밀번호는 서버에서 `openssl rand -hex 16`으로 만듭니다.

```bash
docker exec -it eolssu-runtime-mysql-1 mysql -uroot -p
```

root 비밀번호는 `sudo grep MYSQL_ROOT_PASSWORD /opt/eolssu/.env`로 확인합니다. `mysql>`에서 실행합니다.

```sql
CREATE USER 'grafana'@'%' IDENTIFIED BY '<grafana 비밀번호>';

-- users는 개인정보(email·password_hash·nickname)를 뺀 열만
GRANT SELECT (id, instrument_id, notation_mode, status, last_login_at, withdrawal_requested_at, purge_scheduled_at, created_at, deleted_at)
  ON eolssu.users TO 'grafana'@'%';

-- 운영 현황·채점 품질 대시보드가 읽는 테이블
GRANT SELECT ON eolssu.practice_sessions TO 'grafana'@'%';
GRANT SELECT ON eolssu.evaluations       TO 'grafana'@'%';
GRANT SELECT ON eolssu.note_results      TO 'grafana'@'%';
GRANT SELECT ON eolssu.songs             TO 'grafana'@'%';
GRANT SELECT ON eolssu.instruments       TO 'grafana'@'%';
GRANT SELECT ON eolssu.pitches           TO 'grafana'@'%';

SHOW GRANTS FOR 'grafana'@'%';
```

인증 테이블(`email_verifications`·`refresh_tokens`·`user_social_accounts`)과 `users`의 개인정보 열은 읽지 못합니다. 권한은 더해지므로 `users`에 테이블 단위 SELECT를 함께 주면 열 제한이 효과가 없습니다. 대시보드에서 다른 테이블이 필요하면 그 테이블만 더합니다.

예전에 Metabase를 띄웠던 서버는 `metabase` 계정과 DB가 남아 있습니다. 백업 스크립트([infra/backup](../backup/README.md))를 `metabase` 없이 덤프하는 버전으로 설치한 뒤에 지웁니다. 먼저 지우면 매시 백업이 없는 DB를 덤프하다 실패합니다.

```sql
DROP USER 'metabase'@'%';
DROP DATABASE metabase;
```

`'%'`는 모든 주소에서 접속을 허용한다는 뜻이지만, 이 MySQL은 서버 밖에 포트를 열지 않아 같은 Docker 네트워크 안에서만 접속됩니다.

2026-11-15 운영 조회에서 `metabase` 계정과 DB가 모두 없고, `grafana`의 권한이 위 SQL과 같음을 확인했습니다([S15P31E102-939](../../docs/plans/S15P31E102-939.md)).

### 권한을 바꿀 때 (edo2 복제 주의)

edo2 복제본에는 `root@localhost` 말고 다른 계정이 없습니다. 그래서 운영에서 기존 계정에 `GRANT`·`REVOKE`·`DROP USER`를 실행하면 복제본에서는 실패하고, **edo2 복제가 멈춥니다**. 복제가 멈추면 [edo-check](../edo-check/README.md) 알림이 울립니다. 기존 계정의 권한을 바꾸거나 되돌릴 때는 한 줄짜리라도 바이너리 로그에 남기지 않도록 아래처럼 실행합니다.

```sql
SET SESSION sql_log_bin = 0;
-- 표 단위 권한을 먼저 회수한 뒤 열 단위 권한을 준다. 순서를 바꾸면 표 단위 REVOKE가 열 권한까지 지운다.
REVOKE SELECT ON eolssu.practice_sessions FROM 'grafana'@'%';
GRANT SELECT (<대시보드 쿼리가 쓰는 열>) ON eolssu.practice_sessions TO 'grafana'@'%';
SHOW GRANTS FOR 'grafana'@'%';
SET SESSION sql_log_bin = 1;
```

열 목록은 바꾸기 전에 대시보드 쿼리에서 확인합니다. 이때 사용자가 직접 쓴 메모인 `practice_sessions.memo`는 목록에서 뺍니다.

## 2. 파일 올리고 실행

내 PC의 저장소 루트에서 서버로 복사합니다.

```bash
ssh edo1 "mkdir -p ~/eolssu-monitoring"
```

```bash
scp infra/monitoring/compose.yml infra/monitoring/.env.example infra/monitoring/prometheus.yml edo1:~/eolssu-monitoring/
```

서버에서 비밀값을 채우고 실행합니다.

```bash
cd ~/eolssu-monitoring && cp .env.example .env && chmod 600 .env && nano .env
```

```bash
docker compose up -d
```

`docker compose ps`에서 grafana·node-exporter·prometheus가 `Up (healthy)`인지 확인합니다. Grafana는 `/api/health`의 데이터베이스 상태를 확인합니다. node-exporter는 `/metrics`에서 시간 지표를 확인합니다. Prometheus의 실제 수집 상태는 `curl -s 127.0.0.1:3001/api/v1/targets`의 `"health":"up"`으로 확인합니다. 예전 Compose에 있던 metabase 컨테이너는 `docker compose up -d --remove-orphans`로 함께 지웁니다.

### 운영 헬스체크 반영

기존 Compose를 백업하고 새 설정을 `docker compose config --quiet`로 검증한 뒤, 변경된 모니터링 컨테이너 두 개만 다시 만듭니다.

```bash
cd /home/ubuntu/eolssu-monitoring
docker compose up -d --no-deps --no-build --pull never --wait --wait-timeout 90 grafana node-exporter
docker compose ps grafana node-exporter
```

n8n은 별도 Compose 프로젝트 `hey-edo1`에 속합니다. [헬스체크 오버레이](hey-edo1-healthcheck.compose.json)는 n8n 컨테이너의 `127.0.0.1:5678/healthz/readiness`와 응답의 `status=ok`를 검사합니다. 운영 `/home/ubuntu/hey/compose.json`은 비밀값을 담고 있으므로 운영 서버에서 보호된 경로에 백업하고, 오버레이의 `services.n8n.healthcheck`만 적용합니다. 운영 Compose를 새로 생성할 때도 같은 필드를 포함합니다.

운영 n8n 데이터베이스에서 진행 중인 실행이 없는지 아래 읽기 전용 조회로 확인합니다. 결과가 `0`이면 적용한 Compose를 검증하고 n8n을 다시 만듭니다.

```bash
query="SELECT count(*) FROM execution_entity WHERE status IN ('running','new','waiting') AND \"deletedAt\" IS NULL;"
docker exec -e 'PGOPTIONS=-c default_transaction_read_only=on -c statement_timeout=3000' \
  hey-edo1-postgres-1 psql -X -v ON_ERROR_STOP=1 -U postgres -d n8n -Atc "$query"

cd /home/ubuntu/hey
docker compose -p hey-edo1 -f compose.json config --quiet
docker compose -p hey-edo1 -f compose.json up -d --no-deps --no-build --pull never --wait --wait-timeout 90 n8n
docker compose -p hey-edo1 -f compose.json ps n8n
```

실패하면 변경한 헬스체크 필드를 이전 상태로 복원하고 같은 서비스만 다시 만듭니다. 설정 백업 이후 다른 배포가 있었다면 현재 Compose에서 해당 필드만 복원해 이후 변경을 보존합니다.

## 3. 접속과 DB 연결

내 PC에서 터널을 엽니다. 이 창을 닫으면 접속이 끊깁니다.

```bash
ssh -L 3000:127.0.0.1:3000 edo1
```

| 도구 | 주소 | 연결 설정 |
| --- | --- | --- |
| Grafana | http://localhost:3000, `admin` / `GRAFANA_ADMIN_PASSWORD` | Connections → Data sources → MySQL. Host `mysql:3306`, Database `eolssu`, User `grafana` |
| Grafana | 같음 | Connections → Data sources → Prometheus. Prometheus server URL `http://prometheus:9090` |

MySQL은 **Save & test**에서 `Database Connection OK`, Prometheus는 `Successfully queried the Prometheus API`가 나오면 연결된 것입니다. Prometheus 자체 화면이 필요하면 터널에 `-L 13001:127.0.0.1:3001`을 더하고 http://localhost:13001 로 엽니다.

## 4. 대시보드

작업: [S15P31E102-102](https://ssafy.atlassian.net/browse/S15P31E102-102). 시각은 DB에 UTC로 저장되고 대시보드는 한국 시각(Asia/Seoul)으로 보여 줍니다. SQL에서 날짜로 묶거나 글자로 바꾸는 곳은 9시간을 더해 KST로 맞춥니다.

### Grafana: 운영 현황과 최근 행

[dashboards/eolssu-overview.json](dashboards/eolssu-overview.json)을 가져옵니다. 10초마다 새로고침합니다.

1. Grafana에서 Dashboards → New → Import를 엽니다.
2. JSON 파일을 올리고, `eolssu MySQL` 칸에서 3단계에서 만든 MySQL 데이터 소스를 고릅니다.
3. Import를 누릅니다. 같은 uid(`eolssu-overview`)로 다시 가져오면 덮어씁니다.

| 패널 | 내용 |
| --- | --- |
| 현황 | 가입자 수(탈퇴 제외), 선택 기간의 연습 세션·채점 결과 수, 최근 연습 시작 시각 |
| 일별 막대 | 일별 가입·연습·채점 수. 하루는 한국 시각 자정에 나뉩니다 |
| 최근 행 | practice_sessions·evaluations·note_results·users의 최근 20행. users는 이메일·닉네임·비밀번호 해시를 뺍니다 |
| 곡별 연습 | 선택 기간에 시작한 연습의 곡·악기별 횟수와 평균 점수. 채점이 없는 연습도 셉니다. Metabase에서 옮겼습니다 |

화면에서 고친 대시보드는 Share → Export → Save to file로 내려받아 이 파일을 바꿔 커밋합니다. 데이터가 없으면 숫자는 0, 표는 `No data`로 보입니다.

### Grafana: 채점 품질

작업: [S15P31E102-196](https://ssafy.atlassian.net/browse/S15P31E102-196). 기준: [스펙](../../docs/specs/scoring-quality-dashboard.md).

[dashboards/eolssu-scoring-quality.json](dashboards/eolssu-scoring-quality.json)을 위와 같은 순서로 가져옵니다. uid는 `eolssu-scoring-quality`입니다. 자동 새로고침은 없고 기본 기간은 최근 30일입니다.

위쪽 선택상자로 악기·곡·모델 버전을 거릅니다. 곡 목록은 고른 악기의 곡만 보입니다. 오차는 +가 높게·늦게·길게 분 쪽입니다.

| 패널 | 내용 |
| --- | --- |
| 오차 분포 | 음높이(10 cent)·박자(20 ms)·길이(50 ms) 구간별 음 수. 범위를 넘는 값은 끝 칸에 넣고, 음을 못 잡은 MISSED는 뺍니다. 박자는 곡 처음 기준 늦음이라 `pyin-4`부터 판정(곡 처음 기준과 앞뒤 음 기준 중 덜 어긋난 쪽)과 다릅니다 |
| 연주 안 박자 흔들림 | 박자 오차에서 연주별 평균을 뺀 값의 20 ms 구간별 음 수. 녹음 시작 밀림처럼 연주 전체에 똑같이 더해진 늦음이 빠집니다 |
| 음별 판정 | 정답 음별 맞음·음높이·박자·길이·못 잡음 비율. 길이(`LENGTH`)는 `pyin-8`부터 나옵니다 |
| 곡별 판정 비율 | 곡별 음 수와 다섯 판정 비율(%) |
| 모델 버전별 평균 점수 | 버전별 채점 수와 총점·음높이·박자·길이 평균 |
| 연주별 음 판정 | 위쪽 `연주` 선택상자로 고른 채점 하나의 음마다 정답 음·잡힌 음·판정·오차. 연주 목록은 기간·악기·곡·모델 버전으로 거른 최근 200개입니다 |
| 실패 세션 원인 | FAILED 세션의 `fail_reason`별 수. 채점 전에 끝난 세션이라 모델 버전 거르기는 적용되지 않습니다 |
| 채점 처리 시간 | 녹음 종료(`ended_at`)부터 채점 저장(`evaluated_at`)까지의 중앙값과 5초 구간 분포 |

시김새 음은 음높이 판정에서 빠지지만 DB에 시김새 여부가 없어 음높이 오차 분포에는 섞여 있습니다.

### Grafana: 백엔드 성능

[dashboards/eolssu-backend.json](dashboards/eolssu-backend.json)을 같은 순서로 가져오되, `Prometheus` 칸에서 Prometheus 데이터 소스를 고릅니다. uid는 `eolssu-backend`이고 30초마다 새로고침합니다. 연주 분석 실행기 행만 백엔드가 직접 내보내는 수치(`eolssu_analysis_*`, `executor_*{name="analysis"}`)를 쓰고, 나머지는 Spring 기본 수치입니다.

| 패널 | 내용 |
| --- | --- |
| API 응답 시간 | 주소 패턴별 5분 평균·p95(상위 5%)와 최근 최대. 수집 요청(`/api/actuator`)은 뺍니다. p95는 5ms~10s 범위에서 계산해서, 약 4.75ms면 "5ms 미만", 10s면 "10s 이상"으로 읽습니다 |
| 상태 코드별 요청 수·5xx 오류율 | 상태 코드별 초당 요청 수와 서버 오류 비율. 요청이 없으면 0입니다 |
| JVM | 힙 영역별 사용량과 최대, GC 멈춤 시간, 스레드 수 |
| DB 연결 풀 | 사용 중·쉬는 중·대기 연결 수와 연결 대기 시간 p95. MySQL에 붙는 `ops` 프로필에서만 나옵니다. 대기 p95는 1ms~5s 범위라 약 0.95ms면 "1ms 미만"입니다 |
| 연주 분석 실행기 | 업로드 뒤 분석·다시 시도의 실행 중·대기열 수와 연결 여부, 실행 시간 평균·p95(100ms~2분 범위)와 대기 시간 평균, 5분 동안의 결과 코드별 건수와 거절 건수(`queue_full`·`shutdown`). 분석 연결이 갖춰진 `ops` 프로필에서만 나옵니다. 활성 슬롯만 수집하므로 배포로 내려가는 이전 슬롯이 종료 중에 처리·거절한 분석은 보이지 않고, 그 슬롯의 로그로 확인합니다([S15P31E102-947](../../docs/plans/S15P31E102-947.md#운영-적용-절차)) |
| 서버 | edo1 CPU 사용률, 캐시를 뺀 메모리 사용률, 루트(`/`) 디스크 사용률 |

새로 생긴 주소의 요청 수와 응답 시간은 수집이 두 번 이상 된 뒤(30초 안팎)부터 보입니다.

### 테이블 보기

대시보드에 없는 테이블은 왼쪽 메뉴 Explore에서 `eolssu MySQL` 데이터 소스를 고르고 SQL로 봅니다. 예: `SELECT * FROM songs LIMIT 50`. grafana 계정은 읽기 전용이고 위 표의 테이블만 읽습니다. Explore는 Editor 이상 권한에서 열립니다.

## 문제 해결

| 증상 | 원인 |
| --- | --- |
| `network eolssu-runtime_default not found` | 운영 스택이 꺼져 있거나 이름이 바뀌었습니다. `docker network ls`로 확인합니다. |
| Prometheus 대상이 `down` | 백엔드가 수치 주소가 없는 이전 버전이거나 운영 스택이 꺼져 있습니다. 서버에서 `docker exec eolssu-monitoring-prometheus-1 wget -qO- http://frontend:8081/api/actuator/prometheus`로 확인합니다. |
| 테이블이 안 보임 | 백엔드가 Flyway 마이그레이션을 아직 적용하지 않았을 수 있습니다. 연결 성공까지만 확인합니다. |

## 끄기와 데이터

- 끄기: `docker compose down`. Grafana 설정은 `grafana-data` 볼륨에 남습니다.
- 일 배치와 집계 DB, 외부 공개 주소·TLS, Prometheus 경보 규칙은 아직 없습니다. 장애·백업·복제·디스크 상태 변화 알림은 edo2의 [능동 알림](../edo-check/README.md#상태-변화-능동-알림)이 맡습니다.
