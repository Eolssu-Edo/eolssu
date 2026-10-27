# Grafana·Metabase

운영 서버 edo1에서 Grafana(실시간 모니터링)와 Metabase(통계 탐색)를 띄우고, 운영 MySQL에 읽기 계정으로 연결합니다. 작업: [S15P31E102-69](https://ssafy.atlassian.net/browse/S15P31E102-69)

| 서비스 | 서버 안 주소 | 이미지 |
| --- | --- | --- |
| Grafana | 127.0.0.1:3000 | grafana/grafana:11.3.0 |
| Metabase | 127.0.0.1:3001 | metabase/metabase:v0.52.4 |

두 서비스는 서버 밖에 열지 않고 SSH 터널로만 접속합니다. 운영 스택(`eolssu-runtime`)의 Compose·Nginx 설정은 바꾸지 않고, 그 스택의 내부 네트워크 `eolssu-runtime_default`에 붙어 MySQL을 `mysql:3306`으로 찾습니다.

## 준비 조건

- 운영 스택 `eolssu-runtime`이 실행 중이어야 합니다. `docker network ls`에 `eolssu-runtime_default`가 보여야 합니다.
- 운영 DB 이름은 `eolssu`입니다.
- 서버 메모리 2GB 이상 여유. Metabase가 혼자 1GB를 씁니다(`JAVA_OPTS -Xmx1g`).

## 1. MySQL 계정 만들기 (처음 한 번)

운영 MySQL에 쓰는 작업이므로 운영 스택 담당과 먼저 맞춥니다.

| 계정·DB | 권한 | 용도 |
| --- | --- | --- |
| `grafana` | `eolssu` 읽기 | Grafana 데이터 소스 |
| `metabase` | `eolssu` 읽기, `metabase` 전체 | Metabase 데이터 탐색과 자체 설정 저장 |
| `metabase` DB | - | Metabase 대시보드·질문 저장소. MySQL 백업에 함께 포함됨 |

비밀번호는 서버에서 `openssl rand -hex 16`으로 두 개 만듭니다. Metabase 비밀번호는 접속 주소에 들어가므로 영문·숫자만 씁니다.

```bash
docker exec -it eolssu-runtime-mysql-1 mysql -uroot -p
```

root 비밀번호는 `sudo grep MYSQL_ROOT_PASSWORD /opt/eolssu/.env`로 확인합니다. `mysql>`에서 실행합니다.

```sql
CREATE USER 'grafana'@'%' IDENTIFIED BY '<grafana 비밀번호>';
GRANT SELECT ON eolssu.* TO 'grafana'@'%';

CREATE DATABASE metabase CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'metabase'@'%' IDENTIFIED BY '<metabase 비밀번호>';
GRANT SELECT ON eolssu.* TO 'metabase'@'%';
GRANT ALL PRIVILEGES ON metabase.* TO 'metabase'@'%';

SHOW GRANTS FOR 'grafana'@'%';
SHOW GRANTS FOR 'metabase'@'%';
```

`'%'`는 모든 주소에서 접속을 허용한다는 뜻이지만, 이 MySQL은 서버 밖에 포트를 열지 않아 같은 Docker 네트워크 안에서만 접속됩니다.

## 2. 파일 올리고 실행

내 PC의 저장소 루트에서 서버로 복사합니다.

```bash
ssh edo1 "mkdir -p ~/eolssu-monitoring"
```

```bash
scp infra/monitoring/compose.yml infra/monitoring/.env.example edo1:~/eolssu-monitoring/
```

서버에서 비밀값을 채우고 실행합니다.

```bash
cd ~/eolssu-monitoring && cp .env.example .env && chmod 600 .env && nano .env
```

```bash
docker compose up -d
```

`docker compose ps`에서 grafana와 metabase가 `Up`이면 됩니다. Metabase는 처음 1~2분 걸리며, `docker compose logs -f metabase`에 `Metabase Initialization COMPLETE`가 나오면 준비된 것입니다.

## 3. 접속과 DB 연결

내 PC에서 터널을 엽니다. 이 창을 닫으면 접속이 끊깁니다.

```bash
ssh -L 3000:127.0.0.1:3000 -L 3001:127.0.0.1:3001 edo1
```

| 도구 | 주소 | 연결 설정 |
| --- | --- | --- |
| Grafana | http://localhost:3000, `admin` / `GRAFANA_ADMIN_PASSWORD` | Connections → Data sources → MySQL. Host `mysql:3306`, Database `eolssu`, User `grafana` |
| Metabase | http://localhost:3001, 처음 접속 때 관리자 생성 | 데이터 추가 → MySQL. Host `mysql`, Port `3306`, Database `eolssu`, User `metabase`, 추가 JDBC 옵션 `allowPublicKeyRetrieval=true` |

Grafana는 **Save & test**에서 `Database Connection OK`가 나오면 연결된 것입니다.

## 4. 대시보드

작업: [S15P31E102-102](https://ssafy.atlassian.net/browse/S15P31E102-102). 시각은 DB에 UTC로 저장되고 두 도구가 브라우저 시각으로 바꿔 보여 줍니다.

### Grafana: 운영 현황과 최근 행

[dashboards/eolssu-overview.json](dashboards/eolssu-overview.json)을 가져옵니다. 10초마다 새로고침합니다.

1. Grafana에서 Dashboards → New → Import를 엽니다.
2. JSON 파일을 올리고, `eolssu MySQL` 칸에서 3단계에서 만든 MySQL 데이터 소스를 고릅니다.
3. Import를 누릅니다. 같은 uid(`eolssu-overview`)로 다시 가져오면 덮어씁니다.

| 패널 | 내용 |
| --- | --- |
| 현황 | 가입자 수(탈퇴 제외), 선택 기간의 연습 세션·채점 결과 수, 최근 연습 시작 시각 |
| 일별 막대 | 일별 가입·연습·채점 수. 하루는 UTC 기준이라 한국 시각 오전 9시에 나뉩니다 |
| 최근 행 | practice_sessions·evaluations·note_results·users의 최근 20행. users는 이메일·닉네임·비밀번호 해시를 뺍니다 |

화면에서 고친 대시보드는 Share → Export → Save to file로 내려받아 이 파일을 바꿔 커밋합니다. 데이터가 없으면 숫자는 0, 표는 `No data`로 보입니다.

### Grafana: 채점 품질

작업: [S15P31E102-196](https://ssafy.atlassian.net/browse/S15P31E102-196). 기준: [스펙](../../docs/specs/scoring-quality-dashboard.md).

[dashboards/eolssu-scoring-quality.json](dashboards/eolssu-scoring-quality.json)을 위와 같은 순서로 가져옵니다. uid는 `eolssu-scoring-quality`입니다. 자동 새로고침은 없고 기본 기간은 최근 30일입니다.

위쪽 선택상자로 악기·곡·모델 버전을 거릅니다. 곡 목록은 고른 악기의 곡만 보입니다. 오차는 +가 높게·늦게·길게 분 쪽입니다.

| 패널 | 내용 |
| --- | --- |
| 오차 분포 | 음높이(10 cent)·박자(20 ms)·길이(50 ms) 구간별 음 수. 범위를 넘는 값은 끝 칸에 넣고, 음을 못 잡은 MISSED는 뺍니다 |
| 음별 판정 | 정답 음별 맞음·음높이·박자·못 잡음 비율 |
| 곡별 판정 비율 | 곡별 음 수와 판정 비율(%) |
| 모델 버전별 평균 점수 | 버전별 채점 수와 총점·음높이·박자·길이 평균 |
| 실패 세션 원인 | FAILED 세션의 `fail_reason`별 수. 채점 전에 끝난 세션이라 모델 버전 거르기는 적용되지 않습니다 |
| 채점 처리 시간 | 녹음 종료(`ended_at`)부터 채점 저장(`evaluated_at`)까지의 중앙값과 5초 구간 분포 |

시김새 음은 음높이 판정에서 빠지지만 DB에 시김새 여부가 없어 음높이 오차 분포에는 섞여 있습니다.

### Metabase: 테이블 보기와 곡별 통계

- **테이블 보기**: 왼쪽 메뉴 데이터 둘러보기 → eolssu → 테이블을 엽니다. 열 때마다 DB를 새로 읽습니다. 새 테이블은 스키마 동기화(기본 1시간) 뒤에 보이며, 관리자 설정 → 데이터베이스 → eolssu → 지금 스키마 동기화로 바로 반영합니다.
- **곡별·악기별 연습**: 새로 만들기 → SQL 쿼리에서 아래를 저장하고, 새 대시보드 `얼쑤 연습 통계`에 넣습니다. 대시보드 자동 새로고침은 최소 1분입니다.

```sql
SELECT i.name AS 악기, s.title AS 곡, COUNT(ps.id) AS 연습_횟수, ROUND(AVG(e.total_score), 1) AS 평균_점수
FROM practice_sessions ps
JOIN songs s ON s.id = ps.song_id
JOIN instruments i ON i.id = s.instrument_id
LEFT JOIN evaluations e ON e.session_id = ps.id
GROUP BY i.name, s.title
ORDER BY 연습_횟수 DESC
```

Metabase 대시보드는 MySQL `metabase` DB에 저장되므로 저장소에 파일이 없습니다. 위 SQL이 기준입니다.

## 문제 해결

| 증상 | 원인 |
| --- | --- |
| `network eolssu-runtime_default not found` | 운영 스택이 꺼져 있거나 이름이 바뀌었습니다. `docker network ls`로 확인합니다. |
| Metabase가 계속 재시작 | `docker compose logs metabase`에 `Access denied`가 있으면 `.env`의 비밀번호가 MySQL 계정과 다릅니다. |
| `RSA public key is not available` | 접속 주소나 JDBC 옵션에 `allowPublicKeyRetrieval=true`가 빠졌습니다. |
| 테이블이 안 보임 | 백엔드가 Flyway 마이그레이션을 아직 적용하지 않았을 수 있습니다. 연결 성공까지만 확인합니다. |

## 끄기와 데이터

- 끄기: `docker compose down`. Grafana 설정은 `grafana-data` 볼륨, Metabase 설정은 MySQL `metabase` DB에 남습니다.
- 일 배치와 집계 DB, 외부 공개 주소·TLS, 알림은 아직 없습니다.
