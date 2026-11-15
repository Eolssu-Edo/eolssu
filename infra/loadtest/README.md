# 시연 규모 동시 접속 리허설

S15P31E102-940. 발표장 청중이 한꺼번에 가입·로그인·조회·녹음 업로드를 할 때 백엔드가 어디서 막히는지, 격리된 로컬 환경에서 재현한다. 측정 결과와 시연 대책은 [리허설 결과](../../docs/knowledge/infra/demo-load-rehearsal.md)에 있다.

운영 서버(edo1)에는 실행하지 않는다. 운영 측정은 시간·범위를 팀이 승인한 경우에만 smoke 수준으로 한다.

## 구성

| 파일 | 역할 |
| --- | --- |
| `loadtest.py` | 표준 라이브러리만 쓰는 부하 스크립트. 사용자 N명을 barrier로 같은 순간에 출발시킨다(`signup`·`login`·`browse`·`practice`). |
| `sheet_jobs.py` | 격리 서버의 실제 악보 PDF 접수 또는 제공곡 복사 → 음원 생성 → 파일 다운로드를 1~4명으로 실행한다. ID·판본·수행표 해시·다운로드 바이트를 대조한다. |
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

## 악보 변환·음원 생성 실제 경로 (954)

[954 계획](../../docs/plans/S15P31E102-954.md)과 [실행 결과·한계](../../docs/knowledge/infra/async-resource-validation.md)를 함께 본다. `sheet_jobs.py`는 실제 OMR/CAT가 연결된 폐기 backend, 시험 계정, 다운로드 URL을 발급할 수 있는 전용 객체 저장소가 준비된 뒤 실행한다. 위 `fake_ai.py`는 OMR/CAT를 대체하지 않는다. 운영 부하 실행용 도구가 아니며 backend는 loopback 또는 `backend`, 저장소는 loopback 또는 격리 환경의 `storage`/`host.docker.internal`만 허용하고 redirect를 따르지 않는다.

계정 파일은 `[{"email":"qa@example.test","password":"시험 계정 비밀번호"}]` 형식이다. Git 밖에 두고 출력·커밋하지 않는다. 실제 계정 수만큼 `--users`를 주되 1→2→4 순으로 검사한다. 매 회차 새로운 `--tag`와 증거 디렉터리를 사용한다.

```powershell
# 저장소 루트, 외부 증거 경로. 실제 OMR/CAT·서명 URL에 도달 가능한 환경에서 실행한다.
$evidence = 'C:\higher\qa-artifacts\S15P31E102-954'
python -B infra/loadtest/sheet_jobs.py audio --base http://127.0.0.1:18955 --accounts "$evidence\accounts.json" --users 1 --tag audio-r1 --out "$evidence\audio-r1" --cleanup
python -B infra/loadtest/sheet_jobs.py import --base http://127.0.0.1:18955 --accounts "$evidence\accounts.json" --users 1 --tag staff-r1 --out "$evidence\staff-r1" --notation STAFF --instrument-id 1 --file 'C:\Users\kingc\Downloads\악보 추가 편집 테스트 자료\02_AuClairDeLaLune_양손피아노.pdf' --cleanup
```

- `audio`에는 PATH의 FFmpeg/ffprobe가 필요하다. Windows에서 저장소 URL의 Docker hostname을 해석할 수 없다면 동일 격리 네트워크의 FFmpeg 포함 컨테이너에서 도구를 실행하고 저장소/증거 경로를 bind한다. hostname이나 서명 URL을 임의로 바꾸지 않는다.
- `import`는 PDF만 받는다. 정간보는 `--notation JEONGGANBO`, 실제 `--jangdan-id`와 악기 ID를 지정한다. `--hwang-pitch`는 오선보의 황종 기준을 지정할 때만 사용한다(예: `Eb4`). 사진·편집·취소/장애·Android UI는 이 두 CLI의 통과 범위에 포함하지 않는다.
- 자동 worker가 접수 작업을 DONE/FAILED로 끝낼 때까지 기다린다. `--deadline` 기본 720초는 관찰 종료 시점이며 제품 timeout을 바꾸지 않는다. 제한에 도달하거나 응답 파싱이 실패하면 통과하지 않는다. 모호한 접수는 실행 장부·기존 키로 먼저 확인한다.
- `audio`는 판본/수행표 해시, HTTP 200·M4A media type, 전체 크기·SHA-256, FFprobe의 AAC/container/길이와 전체 FFmpeg 디코딩을 검사한다. 길이는 메타데이터와 100ms 이내 차이를 허용한다. 청감·리듬 정확도는 별도다.
- 인식 결과는 `scoreData` 또는 검토용 `draft.score`의 비어 있지 않은 columns/events와 셀/단위 구조 및 판본을 확인한다. 이는 인식 내용의 정답률·검토 완료 판정이 아니다.
- `--cleanup`은 이번 실행에서 응답으로 확인한 악보만 삭제한다. 접수 응답 유실·삭제 실패로 남은 자료는 고유 tag와 DB/스토리지 장부로 확인하고 소유 범위를 정한 뒤 정리한다. 무작위 새 키 재시도로 숨기지 않는다.
- `events.json`, M4A, 수행표는 외부 증거 폴더에 남긴다. 서명 URL·쿠키는 실행 장부에 넣지 않는다. HTTP 통과만으로 디코딩이나 큐의 FIFO/정리를 증명하지 않으며 DB 선점 기록·프로세스/메모리 관찰을 함께 남긴다.

도구 회귀는 `python -B -m unittest discover -s infra/loadtest/tests -p 'test_*.py'`다. FFmpeg/ffprobe 없는 환경의 media 검사는 skip이며, 실제 도구가 있는 환경에서 실행해야 그 경계 검증을 완료한다.
