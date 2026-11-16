# 최종 발표 릴리스·시연 동결 절차

- Jira: [S15P31E102-916](https://ssafy.atlassian.net/browse/S15P31E102-916) · 결정·검증 기록: [916 계획](../../docs/plans/S15P31E102-916.md) · 버전 표기: [릴리스 버전 단일화 방안](../../docs/knowledge/infra/release-versioning.md)
- 현행화: [S15P31E102-962](https://ssafy.atlassian.net/browse/S15P31E102-962). 11-15에 운영에 들어간 ai·omr·playback 자동 배포(244·951), backend databaseTest CI 실행(946), 940 리허설의 시연 대책, 948 서버 리허설을 반영했다.
- 상태: **초안**. 일정은 2026-11-15 사용자 결정에 따라 **main 반영 2026-11-17(화) 17:00 KST, 발표 2026-11-18(수)**이다. 동결 시작·해제 시각과 그 밖의 세부 시각은 제안이며 팀 확인이 필요하다.
- 이 문서는 절차와 준비물만 다룬다. main 반영, 태그 생성·Push, Jenkins 잡 중지, 운영 배포는 담당자가 팀 승인 뒤 직접 실행한다.

## 현재 상태 (2026-11-15 기준)

| 항목 | 상태 | 근거 |
| --- | --- | --- |
| main | `400aaf13`(10-20 초기 설정)에 머물러 있고 develop보다 약 2,980커밋 뒤처짐. develop의 조상이라 fast-forward 가능 | `git merge-base --is-ancestor origin/main origin/develop` |
| 태그 | 원격·로컬 모두 0개 | `git ls-remote --tags origin` |
| backend 배포 | develop 변경을 1분마다 감지해 **자동 배포**(블루그린) | [backend.Jenkinsfile](../jenkins/backend.Jenkinsfile) `pollSCM('* * * * *')` |
| web 배포 | 수동 잡 `eolssu-frontend-web-수동배포용`에 `REVISION` 입력 | [runtime README](../runtime/README.md) 웹 수동 배포 절 |
| ai·omr·playback 배포 | 잡 `eolssu-ai`·`eolssu-omr`·`eolssu-playback`이 develop을 1분마다 확인해, 해당 이미지 입력이 바뀌면 빌드·검사 후 **자동 배포** | [ai.Jenkinsfile](../jenkins/ai.Jenkinsfile)·[service-image.Jenkinsfile](../jenkins/service-image.Jenkinsfile) `pollSCM('* * * * *')`, [runtime README](../runtime/README.md#자동-배포-s15p31e102-244) |
| backend DB 테스트 | `eolssu-backend`가 폐기용 MySQL로 `databaseTest`를 실행. 동결 해제(11-18)까지 **경고 모드**라 실패해도 배포는 막지 않고 UNSTABLE로 알림 | [backend.Jenkinsfile](../jenkins/backend.Jenkinsfile) `catchError(buildResult: 'UNSTABLE')` |
| 배포 순서 검사 | backend·web 배포 도구는 **develop 이력 안의 SHA만**, **현재보다 오래된 SHA는 거부** | [deploy-backend.py](../jenkins/deploy-backend.py) `check_release_order` |
| 버전 표기 | Android `1.0.0`(code 2), iOS `1.0.1`(build 5), backend `0.0.1-SNAPSHOT`, ai `0.1.0`로 서로 다름 | `release-candidate.py notes` 출력 |
| edo2 대기계 | 대기계·MySQL 복제 구성은 579로 develop 반영. 전환 런북·운영 도메인 TLS 절차는 930에서 작성. SSAFY DNS 변경 경로는 답이 없다. 사본 서버 리허설은 948에서 실행(명령 약 35초, 사람 진행 포함 약 2분 30초) | [런북](../standby/failover-runbook.md) · [S15P31E102-930](https://ssafy.atlassian.net/browse/S15P31E102-930) |

따라서 다음 두 가지가 절차의 전제다.

1. **릴리스 SHA는 develop의 커밋이어야 한다.** main에 별도 병합 커밋을 만들면 그 SHA는 배포 도구가 거부한다. main은 RC SHA로 fast-forward만 한다.
2. **동결 중 develop 병합은 곧 운영 배포다.** backend는 모든 병합에, ai·omr·playback은 각 이미지 입력이 바뀐 병합에 1분 안에 교체된다. 동결은 병합 중지(또는 배포 잡 4개 일시 중지)로 구현해야 한다.

## 일정

확정은 main 반영(11-17 17:00)과 발표일(11-18)뿐이다. 나머지 시각은 그 두 시점에서 거꾸로 잡은 제안이다.

| 시점 (KST) | 할 일 | 담당 |
| --- | --- | --- |
| 11-15(일)~11-16(월) | 기능 범위 확정. 이후 develop에는 시연을 막는 결함 수정·문서만 병합 | 팀 |
| 11-16(월) 저녁 | RC 후보 SHA 선정, [테스트 영수증](#rc-선정과-테스트-영수증) 작성 시작 | 릴리스 담당 |
| 11-17(화) 오전 | 실기기 QA·시연 시나리오 리허설. 실패 시 수정 후 RC 재선정(재선정 마감 13:00 제안) | QA·각 파트 |
| 11-17(화) 15:00 | 영수증 완료, 운영에 RC 반영 확인(backend·web·ai·omr·playback), 녹화 영상 촬영 | 릴리스 담당·발표자 |
| **11-17(화) 17:00** | **main fast-forward·태그(팀 승인 후) → 동결 시작** | 릴리스 담당(승인: 팀) |
| 11-18(수) 발표 전 | [당일 점검](#시연-당일-점검) → 발표 → [장애 판단](#장애-판단과-전환) | 발표자·운영 담당 |
| 11-18(수) 발표 후 | 동결 해제 공지, 멈춘 배포 잡 재개 확인, [발표 후 정리](#발표-후-정리) | 릴리스 담당 |

## RC 선정과 테스트 영수증

RC는 아래를 모두 만족하는 develop 커밋 하나다. 영수증은 [916 계획](../../docs/plans/S15P31E102-916.md)의 표 형식을 복사해 이 Task(또는 실제 릴리스 Task) 계획 문서에 남긴다. 각 줄에 **검사한 SHA, 실행 시각, 결과 위치**를 적는다. 다른 SHA의 결과는 재사용하지 않는다(문서만 바뀐 경우 예외로 하되 diff 범위를 적는다).

```sh
git fetch origin
python3 -B infra/release/release-candidate.py check <RC_SHA>
python3 -B infra/release/release-candidate.py notes <RC_SHA> --tag v<버전> \
  --deployed backend=<SHA> --deployed web=<SHA> --deployed ai=<SHA> \
  --deployed omr=<SHA> --deployed playback=<SHA> > release-notes.md
```

| 검사 | 명령·위치 | 비고 |
| --- | --- | --- |
| RC 형식 | `release-candidate.py check` | develop 이력, main fast-forward 가능 여부 |
| backend 기본 | Jenkins `eolssu-backend`의 해당 SHA 빌드 SUCCESS(`test`·`mediaTest`·`build`) | 자동 배포 성공까지 포함 |
| backend DB | 같은 빌드의 `Backend database tests` 단계 결과(폐기용 MySQL `databaseTest`) | 경고 모드라 빌드가 UNSTABLE이면 이 단계 실패다. 원인을 고치거나 받아들이는 이유를 적는다. Jenkins 결과가 없으면 `cd backend && ./gradlew databaseTest` |
| web | 웹 수동 배포 잡 SUCCESS(Wasm·JS 브라우저 테스트 포함) | `/__eolssu_release.json`의 SHA 일치 |
| Android | `cd frontend && ./gradlew :shared:testAndroidHostTest` + RC로 만든 APK 실기기 설치 | QA APK는 `infra/qa-phone`의 SHA별 빌드 |
| iOS | RC로 빌드한 앱의 실기기 시연 시나리오 | Xcode 빌드 번호를 기록 |
| ai·omr·playback | RC 이전 마지막 `eolssu-ai`·`eolssu-omr`·`eolssu-playback` 빌드 SUCCESS(이미지 안 테스트·운영 조건 실행 확인 포함). 입력이 바뀌지 않아 빌드가 없으면 `cd ai && uv run pytest` | 운영 이미지 revision이 RC와 다르면 차이를 적는다. 입력이 같으면 다를 수 있다 |
| data | 변경된 영역의 `unittest`([data README](../../data/README.md)) | playback 변경 시 필수 |
| infra | `python3 -B -m unittest discover -s infra/jenkins/tests`, `infra/release/tests` 등 | 배포 도구 변경 시 필수 |
| 시연 시나리오 | 팀 시연 대본의 모든 단계를 RC 운영 환경에서 1회 이상 | 실패 단계와 대체 경로 기록 |
| 운영 상태 | `/edo1` 점검, 디스크 여유, 최근 백업 성공, 인증서 만료일이 2026-11-25 이후 | 아래 [당일 점검](#시연-당일-점검) 명령 |

`notes`의 경고(버전 불일치, SNAPSHOT, 배포 revision이 RC와 다름)는 **없애거나, 받아들이는 이유를 영수증에 적는다**. 배포 revision은 사람이 서버에서 읽어 입력한다. 도구는 서버에 접속하지 않는다.

```sh
ssh edo1 'sudo cat /opt/eolssu/deployment.json'            # backend sha
curl -fsS https://k15e102.p.ssafy.io/__eolssu_release.json  # web sha
ssh edo1 "sudo docker inspect --format '{{index .Config.Labels \"org.opencontainers.image.revision\"}}' eolssu-ai:runtime eolssu-omr:runtime eolssu-playback:runtime"
```

## 동결 창

동결 기간(제안: 11-17(화) 17:00 main 반영 직후 ~ 11-18(수) 발표 종료)에는 다음을 지킨다.

- **develop 병합 중지.** GitLab에서 develop 병합 권한을 릴리스 담당으로 좁히거나 팀 공지로 멈춘다. 병합하면 1분 안에 backend가, `ai/`·`data/`의 이미지 입력이 바뀌었으면 ai·omr·playback도 운영에서 교체된다.
- 병합을 막기 어렵다면 배포 잡 **4개를 모두** 일시 중지(Disable)한다: `eolssu-backend`, `eolssu-ai`, `eolssu-omr`, `eolssu-playback`. backend만 멈추면 채점·악보 인식·음원 서비스는 계속 자동 교체된다. 진행 중인 빌드가 있으면 끝난 뒤 멈춘다. 재개 후 첫 빌드가 동결 중 쌓인 변경을 한꺼번에 배포하므로 발표 후 병합 순서를 확인하고 재개한다.
- 웹은 수동 잡이라 자동 배포되지 않는다. 동결 중에는 `eolssu-frontend-web-수동배포용`을 실행하지 않는다.
- ai·omr·playback 수동 이미지 재빌드, Flyway 수동 적용, Nginx·Compose·Jenkins 설정 변경, 서버 패키지 업데이트, 재부팅을 하지 않는다.
- **운영 카탈로그를 직접 바꾸지 않는다.** 곡 게시·숨김, 채점 켜기, 모범 음원·판본 교체는 코드 병합 없이 운영 DB·R2에 바로 반영된다([infra/catalog](../catalog/README.md) 도구, 수동 SQL). 동결 시작 전에 진행 중인 카탈로그 작업 담당자에게 알린다.
- 예외: 시연을 막는 결함만 수정한다. 팀원 2명 이상 승인 → develop 병합 → 새 RC로 영수증 전체를 다시 작성한다. 시간이 부족하면 수정 대신 [녹화 영상](#장애-판단과-전환)으로 대체한다.

## main 반영과 태그

11-17(화) 17:00에 팀 승인을 받은 뒤 사람이 실행한다. `notes` 출력의 마지막 절에 같은 명령이 SHA와 함께 나온다.

1. `release-candidate.py check <RC_SHA>`가 성공하는지 다시 확인한다.
2. main을 RC SHA로 fast-forward한다. GitLab의 main 보호 설정에 맞춰 MR(병합 방식 fast-forward)이나 권한자 Push를 사용한다. **병합 커밋을 만들지 않는다.**
3. 주석 태그 `v<버전>`을 RC SHA에 만들고 Push한다. RC를 여러 번 고르면 `v<버전>-rc.N` 태그를 쓰고 최종에만 `v<버전>`을 붙인다.
4. `release-notes.md`를 GitLab 릴리스 또는 계획 문서에 첨부한다.
5. 다음 릴리스의 `notes`는 이 태그를 비교 기준으로 자동 사용한다.

## 시연 당일 점검

발표 1시간 전과 직전에 실행하고 결과를 기록한다.

```sh
curl -fsS https://k15e102.p.ssafy.io/__eolssu_release.json
curl -fsS -o /dev/null -w '%{http_code}\n' https://k15e102.p.ssafy.io/api/actuator/health/readiness
echo | openssl s_client -connect k15e102.p.ssafy.io:443 -servername k15e102.p.ssafy.io 2>/dev/null | openssl x509 -noout -enddate
```

- 동결 방법으로 잡을 멈췄다면 Jenkins에서 배포 잡 4개가 모두 비활성 상태인지 본다.
- Mattermost `/edo1` 점검이 모두 정상인지 확인한다.
- **AI를 예열한다.** 첫 분석은 채점 코드 컴파일로 약 16초가 더 걸리고, 컴파일 캐시는 메모리 임시 폴더(`/tmp`)에 있어 컨테이너가 다시 뜨면 사라진다. 배포 도구는 배포 때만 예열하므로, 발표 1시간 전과 직전에 한 번씩 실행한다. 녹음·네트워크 없이 합성음으로 채점 경로만 돈다.

  ```sh
  ssh edo1 'sudo docker exec eolssu-runtime-ai-1 python -m ai_service.warmup'
  ```

- 시연 곡이 그대로인지 본다. `curl -fsS https://k15e102.p.ssafy.io/api/songs`(로그인 불필요)의 곡 목록과 시연 곡의 공개 상태가 RC 때와 같은지 확인한다.
- 시연 계정으로 로그인·연주 평가·결과 화면까지 한 번 실행한다. 계정 기준은 [만료 없는 테스트 계정](../../docs/specs/non-expiring-test-account.md)을 따른다.
- 시연 기기: RC 빌드 설치, 충전, 네트워크(테더링 예비), 마이크 권한, 무음 모드 해제를 확인한다.
- 녹화 영상: 발표 PC에 **오프라인 파일**로 두고 재생을 확인한다.

### 청중 체험 운영

[동시 접속 리허설](../../docs/knowledge/infra/demo-load-rehearsal.md#시연-대책-코드-변경-없음-바로-가능)(940) 결과에 따른 진행 규칙이다.

- **가입은 준비 계정이나 소셜 로그인으로 받는다.** 이메일 가입은 인증 메일 발송 동안 전역 잠금을 잡아, 메일이 1초 걸리면 동시에 5명까지만 성공한다. 이메일 가입을 보여야 하면 발표자 1명만 하거나 몇 초 간격으로 차례로 한다. 준비 계정 수·발급·회수 방법은 [940 계획](../../docs/plans/S15P31E102-940.md)의 남은 일을 따른다.
- **녹음 체험은 한 번에 10명 안쪽으로 나눈다.** 분석은 2건씩 처리되고 4~6초씩 걸려, 10명이면 모두 성공하고 마지막 사람은 22~32초를 기다린다. 30명이 한꺼번에 하면 12명이 시간 초과였다.
- **운영 담당은 Grafana 백엔드 대시보드에서 503과 분석 결과 수를 지켜본다.** 앱 로그에는 남지 않는다. 앱의 상태 조회가 먼저 시간 초과로 끝낸 세션은 `TIMEOUT`이 아니라 나중에 `NOT_PENDING`(분석 중이었다면 `DISCARDED`)으로 집계되므로 세 값을 함께 본다.

### 접근 권한 점검

2026-11-15 점검 결과는 [S15P31E102-939](../../docs/plans/S15P31E102-939.md)에 있다. 시연 계정(회원 1)은 운영에서 만료 없는 세션을 받고 소셜 계정이 연결돼 있었으며, QA 원격 제어 웹이 켜져 있었다. 아래는 모두 사람이 앱·PC에서 직접 하는 일이고, 서버 변경은 없다.

| 시점 | 할 일 | 확인 |
| --- | --- | --- |
| 발표 1~2일 전 | 앱에서 시연 계정 비밀번호를 바꾼다. 곧바로 마이페이지에서 팀이 의도하지 않은 소셜 연결을 해제한다. 새 비밀번호는 시연 담당자에게만 알린다 | 이 계정의 기존 세션이 모두 끊긴다. 다시 로그인은 시연 기기에서만 한다 |
| 11-17 저녁 ~ 발표 종료 | Windows QA PC에서 휴대폰·태블릿 중계를 끈다([QA 운영](../qa-phone/OPERATIONS.md)) | `https://15.165.144.7/qa/`와 `/qa/tablet/`이 502를 돌려준다. 꺼 둔 동안 `/applog` 수집도 멈춘다 |
| 발표 전 | 시연 기기는 미리 로그인해 두고 '이메일 기억'을 끈다. 현장에서 새로 로그인하지 않는다 | 틀린 비밀번호를 5번 입력하면 15분 동안 잠긴다 |
| 발표 전 | 발표 PC에서 Eolssu Updates를 종료하고 SSH 터널을 닫는다. phpMyAdmin에서 로그아웃한다 | 개발자 도구·Swagger·`/ipas/`를 화면에 띄우지 않고, 주소는 도메인만 쓴다 |
| 시연 직전 | 시연 계정이 정상 로그인 상태인지(탈퇴 대기가 아닌지), 닉네임·악기·악보 표기가 그대로인지 본다 | 웹으로 시연했다면 끝난 뒤 앱 안의 '로그아웃'을 누른다 |

## 장애 판단과 전환

시연 중 판단 시간은 짧게 정해 둔다. 기본 대체 수단은 **녹화 영상**이다.

| 증상 | 즉시 조치 | 전환 기준 |
| --- | --- | --- |
| 한 화면·요청이 실패 | 한 번 다시 시도 | 두 번째도 실패하면 해당 장면을 녹화 영상으로 |
| 분석 결과가 오지 않음 | 평가 대기 화면 유지(제한 60초) | 30초 넘으면 녹화 영상으로 넘어가고 운영 담당이 원인 확인 |
| 사이트 전체 접속 불가 | 운영 담당이 `/edo1`·readiness 확인 | 즉시 녹화 영상. 발표 중 복구 작업은 하지 않음 |
| 시연 기기 문제 | 예비 기기로 교체 | 예비 기기도 실패하면 녹화 영상 |

발표 중에는 edo2 대기계로 전환하지 않는다. 2026-11-15 사용자 결정에 따라 장애 시 녹화 영상으로 대체하고, 서버 전환은 발표가 끝난 뒤 [장애 전환 런북](../standby/failover-runbook.md)으로 판단한다([S15P31E102-930](../../docs/plans/S15P31E102-930.md)).

## 롤백

- **backend·web**: 배포 도구가 현재보다 오래된 SHA를 거부한다. 문제 커밋을 되돌리는 **새 커밋을 develop에 병합**해 앞으로 배포한다(backend 자동, web은 그 SHA로 수동 잡). 배포 도중 실패는 도구가 이전 슬롯·릴리스를 자동 복구한다. 상세는 [runtime README](../runtime/README.md)의 블루그린·웹 배포 절을 따른다.
- **ai·omr·playback**: 배포 도구가 검사에 실패하면 이전 이미지로 자동 복구한다. 배포가 끝난 뒤 되돌릴 때는 먼저 해당 잡을 비활성화한다(그대로 두면 다음 develop 변경에 다시 배포된다). 그다음 `sudo docker tag eolssu-<서비스>:rollback eolssu-<서비스>:runtime` 뒤 그 서비스만 재생성한다. omr·playback은 대기열이 빈 것을 확인하고 한다. 도구가 중단돼 기록이 `deploying`·`rollback_failed`로 남았으면 `sudo /opt/eolssu/bin/deploy-ai --recover` 또는 `sudo /opt/eolssu/bin/deploy-service-image <서비스> --recover`를 쓴다. 상세는 [runtime README](../runtime/README.md#자동-배포-s15p31e102-244)를 따른다. `:rollback`은 직전 배포 이미지 하나만 가리킨다.
- **DB**: Flyway 변경은 JAR 롤백으로 되돌아가지 않는다. 동결 중에는 스키마 변경을 반영하지 않는다. 데이터 복구가 필요하면 [백업 운영 안내](../backup/README.md)를 따른다.
- **발표 중**에는 롤백하지 않고 녹화 영상으로 전환한다. 롤백은 발표 뒤에 한다.

## 발표 후 정리

동결 해제와 함께 아래를 확인한다. 각 항목은 해당 작업 기록의 절차를 따른다.

- 멈춘 배포 잡 4개를 병합 순서를 확인한 뒤 재개한다.
- backend `databaseTest` 경고 모드를 차단 모드로 바꿀지 정한다([946](https://ssafy.atlassian.net/browse/S15P31E102-946)). Python 테스트 잡 등록도 동결 뒤로 미뤄져 있다([runtime README](../runtime/README.md#python-테스트-잡-s15p31e102-945)).
- 시연 계정의 만료 없는 인증 예외를 끌지 정하고, 939가 발표 뒤로 미룬 계정·노출 항목을 정리한다([939 계획](../../docs/plans/S15P31E102-939.md)).
- 꺼 둔 QA 휴대폰·태블릿 중계를 다시 켤지 정한다.

## Jenkins 검증 잡 초안

[release-candidate.Jenkinsfile](release-candidate.Jenkinsfile)은 `RC_SHA`를 받아 체크아웃 → RC 형식 검사 → 릴리스 노트 생성 → backend `test`·`mediaTest` → 배포 도구·릴리스 도구 테스트를 실행하고 노트를 보관한다. **배포·태그·main 변경 단계는 없다.** Jenkins에 등록하지 않았으며, 등록하려면 팀 승인 뒤 기존 잡 등록 절차(정의 백업·linter·트리거 없음 확인)를 따른다.

## 검증

```sh
python3 -B -m unittest discover -s infra/release/tests -v
```
