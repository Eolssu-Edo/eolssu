# 최종 발표 릴리스·시연 동결 절차

- Jira: [S15P31E102-916](https://ssafy.atlassian.net/browse/S15P31E102-916) · 결정·검증 기록: [916 계획](../../docs/plans/S15P31E102-916.md) · 버전 표기: [릴리스 버전 단일화 방안](../../docs/knowledge/infra/release-versioning.md)
- 상태: **초안**. 일정은 2026-11-15 사용자 결정에 따라 **main 반영 2026-11-17(화) 17:00 KST, 발표 2026-11-18(수)**이다. 동결 시작·해제 시각과 그 밖의 세부 시각은 제안이며 팀 확인이 필요하다.
- 이 문서는 절차와 준비물만 다룬다. main 반영, 태그 생성·Push, Jenkins 잡 중지, 운영 배포는 담당자가 팀 승인 뒤 직접 실행한다.

## 현재 상태 (2026-11-15 기준)

| 항목 | 상태 | 근거 |
| --- | --- | --- |
| main | `400aaf13`(10-20 초기 설정)에 머물러 있고 develop보다 약 2,980커밋 뒤처짐. develop의 조상이라 fast-forward 가능 | `git merge-base --is-ancestor origin/main origin/develop` |
| 태그 | 원격·로컬 모두 0개 | `git ls-remote --tags origin` |
| backend 배포 | develop 변경을 1분마다 감지해 **자동 배포**(블루그린) | [backend.Jenkinsfile](../jenkins/backend.Jenkinsfile) `pollSCM('* * * * *')` |
| web 배포 | 수동 잡 `eolssu-frontend-web-수동배포용`에 `REVISION` 입력 | [runtime README](../runtime/README.md) 웹 수동 배포 절 |
| ai·omr·playback | 사람이 edo1에서 이미지를 빌드·재생성. Jenkins가 갱신하지 않음 | [runtime README](../runtime/README.md) ai·omr·playback 절 |
| 배포 순서 검사 | backend·web 배포 도구는 **develop 이력 안의 SHA만**, **현재보다 오래된 SHA는 거부** | [deploy-backend.py](../jenkins/deploy-backend.py) `check_release_order` |
| 버전 표기 | Android `1.0.0`(code 2), iOS `1.0.1`(build 5), backend `0.0.1-SNAPSHOT`, ai `0.1.0`로 서로 다름 | `release-candidate.py notes` 출력 |
| edo2 대기계 | 대기계·MySQL 복제 구성은 579로 develop 반영. 전환 런북·운영 도메인 TLS 절차는 930에서 작성. SSAFY DNS 변경 경로는 문의 전이고 서버 리허설은 미실행 | [런북](../standby/failover-runbook.md) · [S15P31E102-930](https://ssafy.atlassian.net/browse/S15P31E102-930) |

따라서 다음 두 가지가 절차의 전제다.

1. **릴리스 SHA는 develop의 커밋이어야 한다.** main에 별도 병합 커밋을 만들면 그 SHA는 배포 도구가 거부한다. main은 RC SHA로 fast-forward만 한다.
2. **동결 중 develop 병합은 곧 운영 backend 배포다.** 동결은 병합 중지(또는 backend 잡 일시 중지)로 구현해야 한다.

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
| 11-18(수) 발표 후 | 동결 해제 공지, backend 잡 재개 확인 | 릴리스 담당 |

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
| backend DB | `cd backend && ./gradlew databaseTest` (폐기용 MySQL) | Jenkins가 돌리지 않으므로 사람이 실행 |
| web | 웹 수동 배포 잡 SUCCESS(Wasm·JS 브라우저 테스트 포함) | `/__eolssu_release.json`의 SHA 일치 |
| Android | `cd frontend && ./gradlew :shared:testAndroidHostTest` + RC로 만든 APK 실기기 설치 | QA APK는 `infra/qa-phone`의 SHA별 빌드 |
| iOS | RC로 빌드한 앱의 실기기 시연 시나리오 | Xcode 빌드 번호를 기록 |
| ai | `cd ai && uv run pytest` | 운영 이미지 revision이 RC와 다르면 차이를 적는다 |
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

- **develop 병합 중지.** GitLab에서 develop 병합 권한을 릴리스 담당으로 좁히거나 팀 공지로 멈춘다. 병합하면 1분 안에 backend가 운영에 배포된다.
- 병합을 막기 어렵다면 Jenkins `eolssu-backend` 잡을 **일시 중지**(Disable)한다. 재개 후 첫 빌드가 동결 중 쌓인 커밋을 한꺼번에 배포하므로 발표 후 병합 순서를 확인하고 재개한다.
- ai·omr·playback 이미지 재빌드, Flyway 수동 적용, Nginx·Compose·Jenkins 설정 변경, 서버 패키지 업데이트, 재부팅을 하지 않는다.
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

- Mattermost `/edo1` 점검이 모두 정상인지 확인한다.
- 시연 계정으로 로그인·연주 평가·결과 화면까지 한 번 실행한다. 계정 기준은 [만료 없는 테스트 계정](../../docs/specs/non-expiring-test-account.md)을 따른다.
- 시연 기기: RC 빌드 설치, 충전, 네트워크(테더링 예비), 마이크 권한, 무음 모드 해제를 확인한다.
- 녹화 영상: 발표 PC에 **오프라인 파일**로 두고 재생을 확인한다.

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
- **ai·omr·playback**: 재빌드 전에 남긴 `eolssu-<서비스>:runtime-<이전 커밋>` 이미지를 `runtime` 태그로 되돌린 뒤 해당 서비스만 재생성한다.
- **DB**: Flyway 변경은 JAR 롤백으로 되돌아가지 않는다. 동결 중에는 스키마 변경을 반영하지 않는다. 데이터 복구가 필요하면 [백업 운영 안내](../backup/README.md)를 따른다.
- **발표 중**에는 롤백하지 않고 녹화 영상으로 전환한다. 롤백은 발표 뒤에 한다.

## Jenkins 검증 잡 초안

[release-candidate.Jenkinsfile](release-candidate.Jenkinsfile)은 `RC_SHA`를 받아 체크아웃 → RC 형식 검사 → 릴리스 노트 생성 → backend `test`·`mediaTest` → 배포 도구·릴리스 도구 테스트를 실행하고 노트를 보관한다. **배포·태그·main 변경 단계는 없다.** Jenkins에 등록하지 않았으며, 등록하려면 팀 승인 뒤 기존 잡 등록 절차(정의 백업·linter·트리거 없음 확인)를 따른다.

## 검증

```sh
python3 -B -m unittest discover -s infra/release/tests -v
```
