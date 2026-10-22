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

## 문제 해결

| 증상 | 원인 |
| --- | --- |
| `network eolssu-runtime_default not found` | 운영 스택이 꺼져 있거나 이름이 바뀌었습니다. `docker network ls`로 확인합니다. |
| Metabase가 계속 재시작 | `docker compose logs metabase`에 `Access denied`가 있으면 `.env`의 비밀번호가 MySQL 계정과 다릅니다. |
| `RSA public key is not available` | 접속 주소나 JDBC 옵션에 `allowPublicKeyRetrieval=true`가 빠졌습니다. |
| 테이블이 안 보임 | 백엔드가 Flyway 마이그레이션을 아직 적용하지 않았을 수 있습니다. 연결 성공까지만 확인합니다. |

## 끄기와 데이터

- 끄기: `docker compose down`. Grafana 설정은 `grafana-data` 볼륨, Metabase 설정은 MySQL `metabase` DB에 남습니다.
- 대시보드, 일 배치, 외부 공개 주소·TLS, 알림은 아직 없습니다.
