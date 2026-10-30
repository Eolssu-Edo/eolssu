# Android QA 약관 우회와 실제 API 검증

- 작업 S15P31E102-309, 2026-10-30. 기준 origin/develop `03253c10` + Task 변경. 서버는 검증 시점의 운영 HTTPS `https://k15e102.p.ssafy.io`이다. 배포는 수행하지 않았다.
- 합의: 기존 테스트 계정으로 검사하며 명시적으로 켠 debug QA APK의 첫 실행 약관만 건너뛴다. [스펙](../../specs/android-qa-consent-bypass.md), [제거 위치·조건·진행](../../plans/S15P31E102-309.md).

## 실제 HTTP 결과

Python urllib의 독립 CookieJar 세션으로 호출했다. 비밀번호는 getpass로 입력하고 파일·출력에 남기지 않았다. 각 검사 세션은 로그아웃으로 끝냈다. HTTP 상태뿐 아니라 공통 isSuccess·result 필드와 식별자를 확인했다.

| 경로·기능 | 관찰 |
| --- | --- |
| 비로그인/로그아웃 후 GET /api/users/me | 401 UNAUTHENTICATED |
| POST /api/auth/sign-in, GET /api/users/me | 200, 실제 쿠키 인증 성공 |
| POST /api/auth/refresh, 이후 내 정보, sign-out | 각각 200, 이후 보호 API 401 |
| GET /api/instruments | 200, 1개 |
| GET /api/songs 및 각 곡 /api/songs/{1..4}, /score | 모두 200, 4곡 악보·타임라인 제공 |
| 각 곡 /model-audio | 모두 404 RESOURCE_NOT_FOUND, 재생 가능한 모범 음원 미제공 |
| GET /api/terms | 200이지만 items 0개, 현 앱의 약관 canStart 조건이 false가 되는 원인 |
| GET /api/pitches?instrumentId=1 | 200, 11개 |
| GET /api/instruments/1/fingerings, /api/notation-symbols | 200이지만 각각 0개, 해당 학습 콘텐츠 미제공 |
| GET /api/jangdans | 200, 3개 |
| GET /api/users/me/growth/summary, /calendar (2026-10-01~30) | 200, 요약 필드·일별 30항목 제공 |
| POST /api/practice-sessions + 같은 Idempotency-Key 재전송 | 201, 같은 sessionId 25를 반환 |
| GET /api/practice-sessions/25, 업로드 전 /evaluation | 200 RECORDING, 409 STATE_CONFLICT |
| POST /api/practice-sessions/25/recording | 202, 이후 PENDING → DONE |
| GET /api/practice-sessions/25/evaluation | 200, 총점98·음정100·박자98·길이97 |
| GET /api/practice-sessions/25/evaluation/notes, /weak-sections | 200, 음표61개·취약 구간1개 |
| POST /api/users/me/score-views + 같은 키 재전송 | 201 → 200, 2초 체류 기록 중복 없이 수용 |

## 입력·재현·한계

- 세션25는 이번 QA에서 테스트 계정으로 만든 검증 데이터다. 공개 곡1 악보의 onsetBeat/durationBeat·BPM96, pitch semitone과 E♭4=311.1269837Hz로 30초 고정 사인파를 만들고 ffmpeg AAC/M4A(22050Hz mono 96kbps)로 인코딩해 업로드했다. 실제 사용자의 연주·마이크가 아니며, 점수는 모델 품질 검증 결과가 아니다.
- 합성 오디오·API 검사 스크립트와 비밀값 없는 요약 JSON은 `/private/tmp/qa309-*`에 남겼다. 재현은 별도 테스트 계정에서 로그인 → 공개 곡·악보 조회 → UUID 기반 Idempotency-Key로 세션 생성 → audio 필드 M4A 업로드 → DONE까지 상태 조회 → 평가·오류·취약 구간 조회 → 로그아웃이다. 새 세션의 ID로 경로를 바꾼다.
- 체류 기록 첫 검사에서 검사 스크립트가 countedSeconds를 누락해 400 INVALID_REQUEST였다. 실제 앱 HttpScoreViewApi에는 해당 필드가 있으며 동일 계약으로 수정한 검사에서 201/200을 확인했다. 제품 코드 결함으로 분류하지 않는다.
- 모범 음원404·빈 운지/기호 목록은 미등록 시 허용된 계약 응답이다. 화면 기능의 콘텐츠 준비 완료로 보고하지 않는다. 약관0개는 이번 임시 우회가 필요한 상태다.
- 기존 DI는 기록 저장·목록·메모에 UnavailablePracticeRecordRepository를 사용한다. 이 기능의 실제 API 연동은 미구현이므로 검증 통과로 볼 수 없다.
- 회원가입·메일 발송·비밀번호 변경/재설정·탈퇴·소셜 연결/해제와 장애 유발·분석 재시도는 이번 검증에서 수행하지 않았다. 계정 자격 증명·보안 정책은 바꾸지 않았다.
- 연결된 adb 기기가 없어 실제 APK 설치·약관 우회 화면·휴대폰 마이크·재생은 미검증이다. HTTP 검사는 Android 앱 네트워크/DTO 호환성 검사를 대신하지 않는다.

## 자동 검사·빌드

- `:shared:compileTestKotlinJs`: QA 인자가 없는 원본에서 새 검사 컴파일 실패를 관찰한 뒤 구현 후 성공.
- `:shared:jsBrowserTest`: 공통504개, 실패0. QA 첫 실행 MIC·기본 CONSENT·완료 HOME·우회로 완료 저장 없음 포함.
- Android 호스트/QA APK 및 운영 Repository 검사 결과는 작업 계획에 최종 기록한다.

- Android 호스트511개 실패0·skip1(로컬 전용 검사), 실제 운영을 앱 Repository/악보 어댑터로 소비하는 QaLiveCatalogTest는 통과. 명령은 `ANDROID_HOME=/private/tmp/eolssu-qa309-sdk EOLSSU_QA_LIVE_CATALOG=true ./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:generateReleaseBuildConfig -Peolssu.qaSkipConsent=true --max-workers=2`. EOLSSU_QA_LIVE_CATALOG를 주지 않으면 운영 호출 검사는 명시적으로 skip된다.
- QA APK 빌드·debug 서명·16KB ZIP 정렬 검사 통과. release 생성 상수는 QA 속성을 주어도 false, QA APK 버전은 1.0.0-qa309이다.

- 기본 debug 생성 상수 QA_SKIP_CONSENT=false와 `:androidApp:lintDebug` 성공도 확인했다. 구현 커밋은 `29a2d8c4`다.
