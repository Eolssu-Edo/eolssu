# 시연 규모 동시 접속 리허설

S15P31E102-940. 발표장 청중이 한꺼번에 가입·로그인·조회·녹음 업로드를 할 때 백엔드가 어디서 막히는지, 격리된 로컬 환경에서 재현한다. 측정 결과와 시연 대책은 [리허설 결과](../../docs/knowledge/infra/demo-load-rehearsal.md)에 있다.

운영 서버(edo1)에는 실행하지 않는다. 운영 측정은 시간·범위를 팀이 승인한 경우에만 smoke 수준으로 한다.

## 구성

| 파일 | 역할 |
| --- | --- |
| `loadtest.py` | 표준 라이브러리만 쓰는 부하 스크립트. 사용자 N명을 barrier로 같은 순간에 출발시킨다(`signup`·`login`·`browse`·`practice`). |
| `fake_smtp.py` | 메일을 밖으로 보내지 않는 로컬 SMTP. 백엔드가 요구하는 STARTTLS·AUTH를 받고, 메일을 JSON 줄로 남겨 인증번호를 읽게 한다. `--delay`로 실제 발송 시간을 흉내 낸다. Python 3.11 이상. |
| `fake_ai.py` | AI 서비스 `POST /v1/analyses` 대역. 녹음을 내려받고 `--delay`초 기다린 뒤 모든 음표를 맞음으로 답한다. 백엔드의 분석 실행기(동시 2건)와 60초 제한은 그대로 동작한다. |
| `tests/` | 위 도구의 단위 검사. `python3 -B -m unittest discover -s infra/loadtest/tests -p 'test_*.py'` |

## 준비

필요한 것: JDK 21, MySQL 8.4(폐기용), Python 3.11+, `ffmpeg`·`ffprobe`(업로드 검사), `openssl`, `keytool`. 아래에서 `$R`은 실제 경로가 심볼릭 링크가 아닌 작업 폴더다(macOS는 `/private/tmp/...`). 로컬 녹음 저장소가 링크 경로를 거부한다.

1. 백엔드 JAR: `cd backend && ./gradlew bootJar`
2. 폐기용 MySQL 8.4를 띄우고 빈 DB와 DDL 권한이 있는 계정을 만든다. 공유 DB나 개인 기본 MySQL은 쓰지 않는다.
3. 마이그레이션을 운영 배포와 같은 1회성 `db` 프로필로 적용한다.

```sh
DB_URL='jdbc:mysql://127.0.0.1:<port>/<db>?connectTimeout=2000&socketTimeout=2000&allowPublicKeyRetrieval=true&useSSL=false' \
DB_USERNAME=<user> DB_PASSWORD=<pass> \
java -Xmx512m -jar backend/build/libs/eolssu-0.0.1-SNAPSHOT.jar --spring.profiles.active=db \
  --spring.main.web-application-type=none --eolssu.scheduling.enabled=false --eolssu.auth.mail-mode=unavailable
```

4. 마이그레이션은 약관을 넣지 않는다. 필수 약관이 없으면 가입이 503이므로 시험용 약관을 넣는다.

```sql
INSERT INTO terms (code, version, title, content_url, is_required, seq, effective_at) VALUES
('AGE_OVER_14','loadtest','age','https://example.test/age',TRUE,1,'2026-01-01'),
('SERVICE_TERMS','loadtest','service','https://example.test/service',TRUE,2,'2026-01-01'),
('PRIVACY_COLLECTION','loadtest','privacy','https://example.test/privacy',TRUE,3,'2026-01-01');
```

5. 가짜 SMTP용 localhost 인증서와 JVM truststore. 백엔드는 STARTTLS 필수·서버 이름 확인을 코드로 고정한다.

```sh
openssl req -x509 -newkey rsa:2048 -nodes -keyout $R/localhost.key -out $R/localhost.crt -days 3 \
  -subj /CN=localhost -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1'
keytool -importcert -noprompt -alias fake-smtp -file $R/localhost.crt -keystore $R/trust.p12 \
  -storetype PKCS12 -storepass loadtest-trust
```

6. 30초 시험 녹음: `ffmpeg -f lavfi -i 'sine=frequency=440:duration=30' -c:a aac -b:a 96k $R/take30.m4a`

## 실행

```sh
python3.12 infra/loadtest/fake_smtp.py --port 25940 --out $R/mail.jsonl \
  --tls-cert $R/localhost.crt --tls-key $R/localhost.key --delay 1.0
python3 infra/loadtest/fake_ai.py --port 18941 --delay 4.0
```

백엔드는 운영과 같은 `ops` 프로필(커넥션 풀 10, 대기 2초)로 띄운다. 비밀값은 모두 시험용 값이다.

```sh
SPRING_PROFILES_ACTIVE=ops SERVER_PORT=18940 \
DB_URL='jdbc:mysql://127.0.0.1:<port>/<db>?connectTimeout=2000&socketTimeout=2000&allowPublicKeyRetrieval=true&useSSL=false' \
DB_USERNAME=<user> DB_PASSWORD=<pass> \
AUTH_JWT_SECRET=<32바이트 이상 시험값> AUTH_CODE_HMAC_SECRET=<32바이트 이상 Base64 시험값> AUTH_COOKIE_SECURE=false \
AUTH_MAIL_MODE=smtp AUTH_MAIL_HOST=localhost AUTH_MAIL_PORT=25940 AUTH_MAIL_USERNAME=loadtest AUTH_MAIL_PASSWORD=loadtest \
AUTH_MAIL_FROM=noreply@example.test \
RECORDING_STORAGE_TYPE=local RECORDING_STORAGE_LOCAL_DIRECTORY=$R/recordings \
RECORDING_STORAGE_LOCAL_READ_BASE_URL=http://127.0.0.1:18940/api/recordings/read \
RECORDING_STORAGE_LOCAL_SIGNING_SECRET=<32바이트 이상 시험값> \
ANALYSIS_AI_BASE_URL=http://127.0.0.1:18941 \
java -Djava.io.tmpdir=$R/tmp -Djavax.net.ssl.trustStore=$R/trust.p12 \
  -Djavax.net.ssl.trustStorePassword=loadtest-trust -Djavax.net.ssl.trustStoreType=PKCS12 \
  -jar backend/build/libs/eolssu-0.0.1-SNAPSHOT.jar
```

`/api/actuator/health/readiness`가 200이 되면 시나리오를 실행한다. `--tag`는 실행마다 바꾼다. 같은 이메일은 60초 재발송 제한·하루 10통 제한에 걸린다.

```sh
L=infra/loadtest/loadtest.py; B=http://127.0.0.1:18940
python3 $L --base $B --json-out $R/signup.jsonl signup --users 10 --mail-log $R/mail.jsonl --accounts $R/accounts.json --tag r1
python3 $L --base $B signup --users 30 --spread 60 --mail-log $R/mail.jsonl --accounts $R/accounts.json --tag r2
python3 $L --base $B login --users 30 --accounts $R/accounts.json
python3 $L --base $B browse --users 30 --accounts $R/accounts.json --duration 30 --think 1
python3 $L --base $B practice --users 30 --accounts $R/accounts.json --audio $R/take30.m4a --tag p1
```

출력 표의 `ok`는 단계별 기대 상태(발송 202, 확인 200, 가입 201, 업로드 202, 분석 DONE)를 받은 수다. 실행 중 커넥션 풀 대기는 `/api/actuator/prometheus`의 `hikaricp_connections_pending`, 상태별 응답 수는 `http_server_requests_seconds_count`로 확인한다.

## 해석할 때 주의

- 로컬 PC는 운영 edo1(4코어, AI·MySQL·Jenkins와 공유)보다 CPU가 넉넉하다. Argon2 로그인·ffprobe처럼 CPU를 쓰는 단계는 운영에서 더 느리다. 반대로 이메일 인증 잠금과 분석 동시 2건은 코드 구조로 정해져 기계와 무관하게 재현된다.
- `fake_ai.py`는 분석 시간을 고정값으로 흉내 낸다. 실제 AI는 CPU를 쓰므로 동시 분석 수를 늘렸을 때의 효과는 이 도구로 판단하지 않는다.
- 로컬 DB에는 마이그레이션이 넣는 4곡뿐이다. 운영 곡 목록 응답은 곡 수에 비례해 커진다.
