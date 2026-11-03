# API 명세서 내용 품질 검토 기록 — S15P31E102-379

2026-11-02에 Notion API 명세서의 실제 내용이 있는 68페이지를 현재 코드와 대조했다. 인증 23·카탈로그/성장/AI 20·연습/평가/기록 12·학습/내 악보 13페이지이며, 데이터베이스의 빈 행 19개는 API 계약 페이지에 포함하지 않는다. 검토는 네 영역별 보고서와 두 교차 검토를 종합했다.

이 문서는 **정적 검토 결론과 교정안의 기록**이다. Notion 적용 완료, 운영 배포, API 실호출, 새 테스트 통과 또는 앱 E2E 완료를 뜻하지 않는다. 외부 문서는 사용자가 지정한 명세서의 export를 읽었으며 코드·SQL·기존 테스트 소스와 대조했다. 기존의 날짜가 있는 운영·검증 기록은 출처와 시점을 보존하며 이번 확인 결과로 재표현하지 않는다.

## 검토 기준과 근거

| 구분 | 코드 기준·범위 | 주요 근거 |
| --- | --- | --- |
| D | develop `46bde1a76042496b49c6e2167ffd979d53f9ad35` | 아래 D-A/C/P/G/I는 모두 이 커밋의 실제 구현이다. |
| D-A | 인증·회원·약관·웹훅 | `feature/auth/application/EmailVerificationService.kt:50`, `SocialAuthService.kt:153`, `SignUpService.kt:36`, `domain/SignIn.kt:17`, `PasswordPolicy.kt:5`, `api/AuthCookies.kt:13`, `application/KakaoEventVerifier.kt:22`, `feature/user/api/MyInfoController.kt:63`, `config/SecurityConfig.kt:40`. |
| D-C | 곡·기준 카탈로그 | `feature/song/api/SongController.kt:20`, `SongScoreController.kt:22`, `ModelAudioController.kt:18`, `feature/catalog/api/CatalogReferenceController.kt:21`, `application/CatalogReferenceQueries.kt:9`, `domain/CatalogReferenceRepository.kt:32`; `backend/src/main/resources/mybatis/song/ModelAudioMapper.xml:4`. |
| D-P | 세션·업로드·분석 재시도·평가 | `feature/practice/application/{CreatePracticeSession,UploadRecording,GetPracticeSession}.kt`, `feature/analysis/application/RetryPracticeAnalysis.kt`, `feature/evaluation/api/{PracticeEvaluationController,EvaluationNotesController,SectionComparisonController}.kt`, `feature/evaluation/domain/SectionComparison.kt`; 관련 MyBatis XML·컨트롤러/DB 테스트 소스. |
| D-G | 성장 요약·달력·체류 | `feature/growth/application/GetGrowthStats.kt:22`, `domain/DateRange.kt:19`, `domain/ScoreViewSegment.kt:21`, `application/RecordScoreView.kt:14`, `api/GrowthController.kt:75`; `backend/src/main/resources/mybatis/growth/PracticeStatsMapper.xml`. |
| D-I | 동기 AI 분석 | `ai/src/ai_service/api.py:59,111,148,159`, `pipelines/scoring.py:170,203,245`; BE `feature/analysis/infrastructure/ai/HttpAnalysisClient.kt:22`. |
| R342 | 미병합 -342 `79762ddeefb5ba98421d3fbc0b29ef672940fc53` | `docs/specs/practice-records.md`, `docs/plans/S15P31E102-342.md`, `feature/record/domain/{PracticeRecords,MemoChange}.kt` 및 해당 Controller/application/SQL. 기록API 4개·세션조회 변경·보관 결정을 develop에 반영된 것으로 보지 않는다. |
| R344 | 미병합 -344 `cdd39eb10ff4c9c9206a9654a43a7648fbdad0c8` | `docs/specs/learning-progress-apis.md`, `docs/plans/S15P31E102-344.md:17,73`, `feature/lesson/api/LessonController.kt:34,103,116`. 사용자 결정·구현은 있지만 공유계약의 앱 담당·팀 합의와 병합은 별도다. |
| R346 | 미병합 -346 `d4f6586e084bd038a4b570dd6421f24a51d0b3af` | `docs/specs/growth-score-trend.md`, `feature/growth/api/ScoreTrendController.kt:24,63`, `application/GetScoreTrend.kt:17`. 최근 곡 선택·scope·응답 변경은 이 브랜치 기준이다. |
| R364 | 확보한 origin/-364 `6b6157d686569b78871ff3384fc735e38b9e855e` | `feature/catalog/api/ReferenceAudioController.kt:23`, `domain/ReferenceAudio.kt:3`, `infrastructure/storage/S3ReferenceAudioReadUrls.kt:17`. 최신 Notion V13의 음높이 관계 추가 전 코드여서 최신 스키마를 전부 검증한 근거로 쓸 수 없다. |

위 표에서 `feature/`·`config/`로 시작하는 경로의 접두사는 `backend/src/main/kotlin/com/ssafy/eolssu/backend/`이다. 브랜치별 근거를 섞어 단일 배포 계약으로 작성하지 않는다.

## 핵심 결론

1. 카탈로그 8페이지의 현재 구현 설명은 대체로 정확하지만 표·예시는 이전 초안인 경우가 많다. 정수 버전, 누락된 필수 필드, 항상null인 콘텐츠 URL, 404/409/500 조건을 **기존 표**에 통합해야 표만 읽어도 올바른 요청을 만들 수 있다.
2. 인증은 발송 실패의 익명화, 무기한 인증 예외, 30일 탈퇴 복구 경계, 선택 악기 유지, 비밀번호 없는 소셜 회원을 구체화해야 한다. 병합의 추가 증명 검증은 `providerEmailVerified=false`일 때만 적용한다.
3. 평가 API는 표면적인 타입 외에 집계 단위가 중요하다. 오류는 세션 범위이고, 단일판정·새 오류를 포함한 remainingErrors·BPM단위·예시의 산술관계를 맞춰야 한다. 녹음 URL/가능여부는 객체 실재 확인을 보장하지 않는다.
4. -342/-344/-346의 완료된 브랜치 구현과 develop, 운영을 분리한다. 앱 연결은 각각369·370·372다. -342 보관 정책의 실제R2 변경은375 후속이며, -344 공유계약 합의가 남았다는 기록도 유지한다.
5. 교차 검토는 **미반환 필드를 필수Y로 보존하는 모순**, 전체곡의 각을0/마지막 각으로 단정한 설명, 조합 이모지를1자로 세는 설명을 추가로 지적했다. 옛 필드는 삭제 승인 전 `이전 초안·미지원/미반환`으로 표시하고 각은 첫·끝NOTE 위치, 메모는Unicode 코드 포인트로 설명한다.
6. 기존 초안을 보존하더라도 현재 사실처럼 앞세우지 않는다. ERD 셀은 `이전 초안 기록`·`코드 대조`·`외부 상태 미확인`을 구분한다. 내 악보 10개와 미사용 내부 API 2개는 구현된 계약처럼 보완하지 않는다.

## 68페이지별 판정

`정합/적합`은 검토한 주요 계약의 정적 정합성을 뜻한다. 형식·문장 개행까지 원격 반영되었다는 뜻은 아니다. 페이지 링크에는 중복 경로도 구분할 수 있는 Notion ID가 포함되어 있다.

### 인증·회원·약관·웹훅

| 페이지 | 기준 | 한 줄 판정 |
| --- | --- | --- |
| [POST /auth/email-verifications](https://www.notion.so/406a0252aa458101b35bc38d09e7f5e9) | D-A | 교정: ACCOUNT_MERGE도 미가입·메일 발송 실패를202로 숨긴다. SIGN_UP 실패만 롤백하며 키·저장소 장애는503이다. |
| [POST /auth/email-verifications/{verificationId}/confirm](https://www.notion.so/406a0252aa4581e3a411d2f8feb4b0c9) | D-A | 보완: 확인 토큰은 목적·이메일에 묶인30분·1회 소비값이며 HMAC 키 미설정도503이다. |
| [POST /auth/oauth/merge](https://www.notion.so/406a0252aa45817daa1cd593a92fdeb3) | D-A | 교정: 병합 증명은 providerEmailVerified=false일 때만 검증한다. 기존 악기 유지·30일 복구 경계·소셜 전용 계정을 명시한다. |
| [POST /auth/oauth/sign-up](https://www.notion.so/40ca0252aa4581928629fd440d1cfade) | D-A | 보완: 닉네임·약관·악기 선택 검증 및 기본값을 구체화한다. 운영 반영 기록과 앱 연결 대기 속성은 별도 확인한다. |
| [POST /auth/oauth/{provider}](https://www.notion.so/406a0252aa4581e89433e069f3d7aea5) | D-A | 보완: 인가 코드·redirectUri 한도, signupTicket 결속 쿠키 갱신, 기존 악기 유지와 탈퇴30일 경계. |
| [POST /auth/oauth/{provider}/start](https://www.notion.so/409a0252aa458125b785f4d48db441cb) | D-A | 핵심 계약 적합. 공개 시작 요청과 OAuth 결속 쿠키의 역할을 속성에서도 구분한다. |
| [POST /auth/password-reset](https://www.notion.so/407a0252aa458162b7f7fa9b20003dfc) | D-A | 교정: 저장 비밀번호가 있는 회원만 재설정하며 재설정 자체는 탈퇴를 취소하지 않는다. 토큰·새 비밀번호 조건을 보완한다. |
| [POST /auth/refresh](https://www.notion.so/401a0252aa45803c9f2dea171e7cbe66) | D-A | 교정: 무기한 인증 대상의 accessTokenExpiresAt은 포함되지만 null이다. refresh 쿠키 인증·회전·회원 상태를 명시한다. |
| [POST /auth/sign-in](https://www.notion.so/578a0252aa458339913981d3bf2235e2) | D-A | 보완: 두 쿠키의Path·TTL·무기한 예외, 로그인 실패 잠금, 탈퇴 요청30일 정각의 복구 종료. |
| [POST /auth/sign-out](https://www.notion.so/401a0252aa458073952ef0689ccd45c8) | D-A | 교정: access 쿠키 필수·refresh 선택. 일반 토큰 폐기와 session_hash 기반 무기한 세션 종료를 구분한다. |
| [POST /auth/sign-up](https://www.notion.so/401a0252aa458055bd4cfcc566bdabe5) | D-A | 보완: ID1~18자리·약관400/422·Unicode 비밀번호·닉네임 한도·악기 기본값과 재가입/복구 경계. |
| [GET /terms](https://www.notion.so/406a0252aa45816ca23ee8fcf965226f) | D-A | 교정: code·version은 DB 문자열로 고정 enum·날짜 타입이 아니다. 운영 목록·버전은 날짜가 있는 기존 기록으로만 보존한다. |
| [GET /users/me](https://www.notion.so/401a0252aa4580b28d91f37ffbd8f91c) | D-A | 보완: email이 있는 소셜 전용 회원도 비밀번호가 없을 수 있다. hasPassword가 없어 email로 비밀번호 UI를 판정할 수 없다. |
| [PATCH /users/me](https://www.notion.so/401a0252aa45813396abf9037c97b204) | D-A | 보완: 닉네임은 비공백·UTF-16 최대60자이며 앞뒤 공백을 자동 제거하지 않는다. |
| [DELETE /users/me](https://www.notion.so/401a0252aa4581809de1caf2a43fc683) | D-A | 보완: 비밀번호 또는 결속 쿠키를 동반한 탈퇴 티켓 중 한 수단으로 요청하며 탈퇴30일 경계·쿠키 제거를 명시한다. |
| [PATCH /users/me/password](https://www.notion.so/401a0252aa4581d7908ae833331ae39b) | D-A | 보완: 비밀번호 없는 회원401, 새 비밀번호 규칙, 변경·탈퇴의 현재 비밀번호 실패 합산 잠금. |
| [PATCH /users/me/preferences](https://www.notion.so/401a0252aa458150a111ddb3ed7eccea) | D-A | 보완: 악기·표기 중 하나 이상 필요하고 null은 기존값 유지다. 곡별BPM은 현 API에서 지원하지 않는다. |
| [DELETE /users/me/social-accounts/{provider}](https://www.notion.so/407a0252aa458114abffc82189ee2e66) | D-A | 핵심 계약 적합. 사용자 연결 해제의 LAST_LOGIN_METHOD와 외부 카카오 해제를 구분한다. |
| [POST /users/me/social-accounts/{provider}](https://www.notion.so/407a0252aa458169a463dce5e4c40a87) | D-A | 보완: 인가 코드·redirectUri 한도와 결속을 명시한다. 상충한 Google/Naver 운영 검증 문구는 시점·범위 재확인. |
| [POST /users/me/social-accounts/{provider}/start](https://www.notion.so/409a0252aa458137967edad85de1403e) | D-A | 핵심 계약 적합. 로그인 회원의 연결 시작과 비회원 로그인 시작을 구분한다. |
| [POST /users/me/withdrawal/oauth/{provider}](https://www.notion.so/40ca0252aa4581d2ac2ff961bccbfca1) | D-A | 핵심 계약 적합. 탈퇴 증명 발급과 실제 탈퇴를 분리하며 배포·앱 검증 상태는 추가 확인한다. |
| [POST /users/me/withdrawal/oauth/{provider}/start](https://www.notion.so/40ca0252aa458139b9c5e3167a1a9c0b) | D-A | 핵심 계약 적합. 기존 연결의 소셜 재인증 시작이며 공개 로그인 시작과 구별한다. |
| [POST /webhooks/kakao/events](https://www.notion.so/409a0252aa4581c782a6cfe9d03ae1ab) | D-A | 보완: SET claim 타입·길이·시간 경계·중복 방지를 명시한다. 외부 해제는 마지막 로그인 수단이어도 반영하며 FE 직접 연결 대상이 아니다. |

### 카탈로그

| 페이지 | 기준 | 한 줄 판정 |
| --- | --- | --- |
| [GET /instruments](https://www.notion.so/401a0252aa458193a28ed814b71227de) | D-C | 핵심 DTO 적합. 활성·ID순 목록과 Accept 선택을 표에 넣고 저장된 pitchToleranceCent를 AI 기준으로 오인하지 않게 한다. |
| [GET /instruments/{instrumentId}/fingerings](https://www.notion.so/401a0252aa4581c5ba89c9dd2c39a53c) | D-C | 교정: description nullable·structureImageUrl 항상null. 악기 없음404와 운지 없음200 빈목록을 구별한다. |
| [GET /jangdans](https://www.notion.so/401a0252aa45805b8357fbc7d3dc4a9d) | D-C | 교정: ID순 정렬은 구현되어 있다. beatCount/groupCount는 각 배열 길이이며 저장 데이터 오류500을 보완한다. |
| [GET /jangdans/{jangdanId}](https://www.notion.so/401a0252aa45819da290f2c9209e23d2) | D-C | 교정: soundUrl은null이고 bpm_unit은 존재한다. group 양수·합 검증은 domain에서 실제 수행한다. |
| [GET /notation-symbols](https://www.notion.so/401a0252aa45812b8461f9db7ae5bc78) | D-C | 핵심 DTO 적합. ID순·빈목록·nullable imageUrl과 Accept 선택을 표에 통합한다. |
| [GET /pitches](https://www.notion.so/401a0252aa458118b35dd90a941b893a) | D-C | 교정: description/soundUrl 항상null. 악기 음역은 pitchId 크기가 아닌 semitone 범위이며 semitone·ID순이다. |
| [GET /songs](https://www.notion.so/6f7a0252aa4583f5afa701028e59cf2a) | D-C | 정합: 무필터·무페이지 전체 공개 목록, 직접 defaultBpm·isScorable·원본 JSON·difficulty1~5를 정확히 구분한다. |
| [GET /songs/{songId}](https://www.notion.so/814a0252aa4583db8f63016c2fe16c42) | D-C | 정합: Long 변환 실패400, 별도 양수 검증 없음, 없는·비공개·삭제 곡404와 원본 JSON 반환. |
| [GET /songs/{songId}/model-audio](https://www.notion.so/401a0252aa4581dea149f6f878bf52e5) | D-C | 교정: scoreVersion/startOffsetMs/sha256 누락. BPM은 곡 기준이며 durationMs는 잔향 포함 파일 길이다. V8 저장 구조 반영 필요. |
| [GET /songs/{songId}/score](https://www.notion.so/401a0252aa458163b48aeecac251ed79) | D-C | 교정: 두 버전은INT. eventId/startTick/durationTick/원본 score 및400/409를 구표·예시에도 반영한다. |
| [GET /reference-audios](https://www.notion.so/40ca0252aa45804d827cdc7a11018f79) | R364 | 부분 확인: 기본 목록 계약은 확보한 -364 코드와 맞지만 최신 V13 pitchId/pitchRelations는 확보 코드보다 새로워 독립 검증 미완료. |
| [GET /reference-audios/{assetId}](https://www.notion.so/40ca0252aa4580608571f70e13483576) | R364 | 부분 확인: 기본 자산·metadata 계약 적합. 최신 음높이 관계 DTO의 필수/null·필터는 최신 -364 코드 확인 후 확정한다. |
| [GET /reference-audios/{assetId}/play](https://www.notion.so/40ca0252aa458047890fc38cf00720bb) | R364 | 부분 확인: 302·no-store·900초 서명·503·성공 본문없음은 확인. 운영 객체 존재·재생·V13 적용은 확인하지 않았다. |

### 연습·평가·기록

| 페이지 | 기준 | 한 줄 판정 |
| --- | --- | --- |
| [POST /practice-sessions](https://www.notion.so/401a0252aa4581baa514e9e525c3a624) | D-P | 핵심 계약 적합: 엄격 JSON·ID·BPM·멱등키·범위·상태 일치. 재요청은 저장된 현재 상태로201을 반환한다. |
| [POST /practice-sessions/{sessionId}/recording](https://www.notion.so/401a0252aa4581c1acdfd43c26760883) | D-P | 교정:410은 RECORDING40분초과 또는 FAILED/UPLOAD_TIMEOUT에 한정한다. 그 외 상태 충돌은409다. |
| [GET /practice-sessions/{sessionId}](https://www.notion.so/401a0252aa4581aeafcaefdf7fc21702) | D-P/R342 | 핵심 계약 적합.400·EVA 구현 근거 보완. 삭제기록404·viewed_at 갱신은 -342 변경이며 develop 동작과 분리한다. |
| [POST /practice-sessions/{sessionId}/retry](https://www.notion.so/401a0252aa458182aff0e8ea5e805311) | D-P | 핵심 계약 적합: 재시도 가능 실패·상태·접수. 키는 형식만 검사하고 원본 조건은 DB키 존재이며 객체 실재 검사가 아니다. |
| [GET /practice-sessions/{sessionId}/evaluation](https://www.notion.so/401a0252aa4581a1aea6d14cc571fc21) | D-P | 핵심 계약 적합: 평가·소유권·삭제·상태·null 및 튜닝 두 값의 동시null 조건. |
| [GET /practice-sessions/{sessionId}/evaluation/notes](https://www.notion.so/401a0252aa4581e3b5eed82f1357d7c6) | D-P | 교정: evaluationId 필수, errorTypes0~1개, recordingOffsetMs 항상null·현 분석 detectedPitchId null. 경로 검증·예시 정리. |
| [GET /practice-sessions/{sessionId}/weak-sections](https://www.notion.so/401a0252aa4581478a1ceabc53ed44fc) | D-P | 교정: 오류 칸 수는 세션 구간 기준. dominantError는 단일 판정음표 수 기준이며 PITCH 예시 reason을 맞춘다. |
| [GET /practice-sessions/{sessionId}/comparison](https://www.notion.so/401a0252aa45814980a6f98e0f191dfe) | D-P | 교정: bpmUnit도 비교 조건. 동시각 허용·remainingErrors는 이번 오류 전체. delta와 corrected 예시를 산술적으로 맞춘다. |
| [PUT /practice-sessions/{sessionId}/record](https://www.notion.so/401a0252aa45812fa1a4d6eb5c9ea838) | R342 | 미병합: 메모는Unicode 코드 포인트500개, 각은 첫·끝NOTE 위치이며 버전 불일치 시null. 새 보관 결정·앱369·운영375를 분리한다. |
| [GET /users/me/practice-records](https://www.notion.so/401a0252aa4581d68d28d8f16bdb0117) | R342 | 미병합: lastSavedAt·곡 선택 빈결과·각null·NEW 및 메모 출처를 최신 계약에 맞춘다. 녹음 가능 표시는 실파일 확인이 아니다. |
| [GET /practice-sessions/{sessionId}/recording](https://www.notion.so/401a0252aa4581bab492f721feea16bc) | R342 | 미병합: duration nullable·15분 서명·503. 저장/상태 검사와 객체 실재 확인을 보장하지 않으며 보관 정책 운영 반영은 별도다. |
| [DELETE /practice-sessions/{sessionId}/record](https://www.notion.so/401a0252aa458046a465c0029f58e901) | R342 | 미병합: 저장기록만 삭제하고 파일삭제 후DB표시, 평가 보존.409분기 없음·503존재 및 recordingDeleted의 의미를 구체화한다. |

### 성장·AI

| 페이지 | 기준 | 한 줄 판정 |
| --- | --- | --- |
| [GET /users/me/growth/summary](https://www.notion.so/401a0252aa45811fb739efdafe7872a3) | D-G | 정합:366일·연도·KST·체류시간·DONE수·평균·기간독립 최고점/성장·연속일·미션null. 중복된 요청 예시만 교정. |
| [GET /users/me/growth/calendar](https://www.notion.so/401a0252aa4581bf87f0ddfbab35637f) | D-G | 정합: 시작 시각의KST날짜로 시간·횟수를 독립 집계하고 조회 기간의 빈 날짜도0으로 채운다. |
| [GET /users/me/growth/scores](https://www.notion.so/401a0252aa4581308610e8a2518af84d) | R346 | 미병합: 최근 곡 자동선택·FULL/SECTION·조건별 comparable·songs목록. 미반환 옛 필드는 필수Y로 남기지 않고 이전 초안으로 표시한다. |
| [POST /users/me/score-views](https://www.notion.so/406a0252aa4581e68434d7f5c8582202) | D-G | 정합: 엄격 타입·선행0허용·마이크로초·1초허용·멱등200/201/409·회원401. 깨진 practice-time 문서 링크 교정. |
| [POST /v1/analyses](https://www.notion.so/408a0252aa4581528abee5ac1bdfd1a7) | D-I | 보완: 시김새 음정 제외·첫 요청음 기준 박 위치 변환·원래 onsetError와TIMING판정 차이. 동기 응답·실패·한도는 정합. |
| [POST /internal/evaluation-jobs](https://www.notion.so/401a0252aa45810c8a81d06f86dfe1cd) | D-I/초안 | 미사용 비동기 접수 초안. 현 핸들러가 없고 /v1/analyses가 대체한다. 상태를 임의 하향하지 않고 비고에 전체 표의 지위를 명시한다. |
| [PUT /internal/practice-sessions/{sessionId}/evaluation](https://www.notion.so/401a0252aa458189a3e8c0453e1c197b) | D-I/초안 | 미사용 결과 콜백 초안. SUCCEEDED·DURATION·errorTypes 등 옛 설계를 현 AI 응답으로 오인하지 않도록 표시하고 삭제는 승인 대상으로 둔다. |

### 학습·내 악보

| 페이지 | 기준 | 한 줄 판정 |
| --- | --- | --- |
| [GET /lessons](https://www.notion.so/401a0252aa4581f08033dde176d0928f) | R344 | 미병합: 필수track·thumbnail 제외·트랙진도로 변경. 기존 instrumentId 표·발췌예시와 구분하며 앱370·팀 공유계약 합의가 남았다. |
| [GET /lessons/{lessonId}](https://www.notion.so/401a0252aa45811d8f9ed3c98066424c) | R344 | 미병합: 서버 문구·track·pitchNote/parts/ranges/songs가 실제 응답이다. 옛 songIds/instrumentId와 새 계약의 경계를 명시한다. |
| [PUT /lessons/{lessonId}/completion](https://www.notion.so/401a0252aa4581c58628d192330bab29) | R344 | 미병합: 사용자 완료 표시만 저장·엄격Boolean·재요청200. 자동판정·기기진도 이관 미구현이며409옛 행의 근거가 없다. |
| [GET /users/me/sheets](https://www.notion.so/405a0252aa45808a804dca25f1b3d1f3) | 초안 | 미구현: 공통악보 방향은 일관되나1차 속성/본문2차 충돌은 우선순위 합의 사항이다. |
| [DELETE /users/me/sheets/{sheetId}](https://www.notion.so/405a0252aa45807fb2abcc7859295000) | 초안 | 미구현: 공통쿠키 정책과 라우트 부재를 구분한다. 이미삭제true/없는자원404 및 변환중 삭제·취소 경합은 결정이 필요하다. |
| [POST /users/me/sheets](https://www.notion.so/405a0252aa4580419bd3dfe9045860e9) | 초안 | 미구현: PDF수동입력·pdfSheetId는 새 인식/검수 흐름과 충돌한다. title/columnCount 필수와 기본값 및 버전 타입은 미확정이다. |
| [PUT /users/me/sheets/{sheetId}/score](https://www.notion.so/405a0252aa4580a5a6aec4e201682e6c) | 초안 | 미구현: 버전STRING·공통event/tick 정합화 필요. confirm=true만으로pending0을 보장하지 않고 검증 통과 성공에 한정한다. |
| [GET /users/me/sheets/{sheetId}/staff](https://www.notion.so/405a0252aa45803ea09dca2f7bdff610) | 초안 | 미구현: PDF지원과 정간보만 안내하는409처리가 충돌한다. 공통악보 준비 상태를 기준으로 설명하며 출력 형식은 미정이다. |
| [GET /users/me/sheets/{sheetId}/preview-audio](https://www.notion.so/405a0252aa458050a0ecf4ca0938a132) | 초안 | 미구현: PDF도 변환·검수 후 미리듣기 대상으로 설명한다. 버전·서버/앱 합성 방식과 정확한 오류 조건은 확정하지 않는다. |
| [POST /users/me/sheets/pdf](https://www.notion.so/405a0252aa4580428551fa53eb63deaa) | 초안 | 미구현: 공통악보 인식 방향은 일관된다. title필수와 파일명 기본값의 주체·파일/쪽수 상한을 결정해야 한다. |
| [GET /users/me/sheets/{sheetId}/score](https://www.notion.so/405a0252aa4580308b24fe9c7aa1ea70) | 초안 | 미구현: 버전STRING 충돌·source 생략/null·issues 항목 및 원본 위치/이벤트 참조 구조가 미정이다. |
| [POST /users/me/sheets/audio](https://www.notion.so/405a0252aa45801cb225cfab1ed85a0d) | 초안 | 미구현: 음원 입력의 kind=MIDI는 기존 enum초안이다. 입력/출력 의미와 파일 한도를 공통 흐름에 맞춰 결정한다. |
| [GET /users/me/sheets/{sheetId}/conversion](https://www.notion.so/405a0252aa458046946dc77d6305593d) | 초안 | 미구현: PDF·이미지까지 포함하면서 음원전용409를 설명한 모순을 해소한다. 충돌 조건·상태·타입은 임의 확정하지 않는다. |

## 프로퍼티와 표 작성 기준

Service·HTTP·경로는 본문 계약과 맞춘다. Request Header는 access 쿠키, OAuth 결속 쿠키, 공개 조회, 내부 전용을 구별한다. API 구현·FE 연결·E2E는 서로 다른 단계이므로 서버 코드 존재만으로 FE나실기기 검증을 완료로 올리지 않는다. 작업중 상태도 develop 미반영만을 이유로 하향하지 않는다. 담당자·우선순위·확인되지 않은 JiraKey는 추정해 변경하지 않는다. 웹훅·AI 내부 API의 FE상태는 기존 옵션을 확인하고, 적절한 옵션이 없으면 비고에서 직접연결 대상이 아님을 설명한다.

필수 칸은 필드 존재 여부, 타입의null은 값 허용 여부로 나눈다. 항상 포함되는 nullable 필드를 선택필드로 표시하지 않는다. 미지원/미반환 옛 필드에는 Y/N을 현행 선택사항처럼 남기지 않는다. 추가 정보는 기존 요청·응답·오류·규칙 표에 넣고 문장은 셀 안에서 문장 단위로 개행한다. 표를 최신화하면 응답 예시·제목·과거 이력 안내도 함께 맞추되 불필요 내용의 실제 삭제는 승인을 받는다.

## 삭제 승인 후보

아래는 삭제 실행 목록이 아니라 사용자가 승인할 범위다. 새 계약 결정이 필요한 항목은 폐기부터 하지 않는다.

| 유형 | 구체 범위 | 이유·보존 조건 |
| --- | --- | --- |
| 미사용 설계 전체 | `/internal/evaluation-jobs`, `/internal/practice-sessions/{sessionId}/evaluation`의 요청·응답·예외·규칙 초안 또는 두 페이지의 별도 보관 전환 | 현 핸들러가 없고 동기 `/v1/analyses`가 실제 연동이다. 설계 이력 보존 필요성을 확인한 뒤 삭제 또는 보관한다. |
| 현 브랜치의 미지원 필드 | GRO-03의 query startNoteIdx/endNoteIdx, items의 songId/songTitle/isFullSong, categoryGrowth와3개 하위행; LRN-01 thumbnailUrl, LRN-02의 기존 instrumentId/songIds 및 그림필드 중 새 계약이 대체한 행; LRN-03의 근거없는409행 | 실제 응답과 다르다. 적용할 브랜치·팀 합의와 삭제 승인을 확인하고, 그 전에는 옛 초안임을 표시한다. |
| 최신표와 중복된 옛 설명 | 카탈로그 8페이지(`/instruments`, `/pitches`, `/fingerings`, `/jangdans` 목록·상세, `/notation-symbols`, `/songs/{songId}/score`, `/model-audio`)의 v0.1 응답 예시와 중복 현재계약 산문 | 완전한 최신 표·예시가 마련된 뒤 중복 부분만 제거한다. 콘텐츠·사용권·음악적 검수·미정 기획 후속은 일괄 삭제하지 않는다. |
| 무관한 공통 템플릿 | 날짜 집계가 없는 인증/카탈로그의 `날짜 집계 KST`, 해당API와 무관한 `이전 연주·취약 구간·미션·성장까지1차 포함`, 점수·주파수가 없는 API의 가상 점수 안내 | 계약 이해에 도움이 되지 않는 행만 선별한다. 이메일 소유확인·약관·계정통합 등 해당API에 직접 관련된 결정 출처와 가상예시 안내는 유지한다. |
| 정상응답을 예외로 중복한 행 | `/instruments`, `/jangdans`, `/notation-symbols` Exceptions의200 빈목록 행 | 응답표의 빈목록 설명과 중복이다. 삭제 승인 전에는 정상 빈응답임을 분명히 표시한다. |
| 작업·폐기 이력의 중복 | PRA-02의“위 빨간 글씨” 변경이력, EVA-04의 폐기된 규칙5, PRA-04 규칙3·5의 상세 실험서술 중 현 동작 설명과 중복 부분 | 현재 계약·검증문서 링크는 남기고 상세 이력을 원래 기록으로 모으는 안을 승인받는다. 검증 근거 자체를 지우지 않는다. |
| 새 결정이 먼저 필요한 후보 | SHT-03의 PDF수동입력 startType=PDF/pdfSheetId 및 관련 규칙 | 공통악보 인식·검수 흐름과 충돌하지만 대체 필드·원본 참조 설계가 미정이다. 삭제가 곧 새 계약 확정이 되므로347·351 정합화 전에는 보존한다. |

## 확인하지 않은 사항과 후속 검증

- Notion 원격 적용·읽기 재검증의 실제 결과는 메인 작업에서 별도로 기록한다. 이 정적 검토 기록으로 적용 완료를 주장하지 않는다.
- 최신 -364 V13의 pitchId/pitchRelations·최종 테스트수·운영DB/R2 매핑·실청취는 확보한 코드로 전부 확인하지 못했다. 최신 커밋을 확인한 뒤 nullable 필수성과 전체 예시를 맞춰야 한다.
- -342/-344/-346의 병합·배포·앱 연결·E2E, -342 녹음 보관 R2정책375는 미확인이다. 기존 서버 테스트 기록을 이번 통과 또는 앱 E2E로 확대하지 않는다.
- 소셜 제공자 콘솔·Google/Naver 왕복 검증, 약관 운영DB/R2, 카카오 웹훅 측정·실회원 삭제, 모범음원 게시/권리·실재 파일 등 외부 상태는 이번에 다시 조회하지 않았다. 상충한 기록은 날짜와 검증 범위로 분리한다.
- 외부 ERD 최신 상태를 조회하지 않았다. 저장소 마이그레이션·모델의 존재/부재와 외부 ERD 반영 여부는 별개다.
- GET `/users/me`에는 비밀번호 보유 여부가 없다. UI가 필요로 하면 별도 공유계약 변경으로 합의해야 하며 명세에 hasPassword를 임의 추가하지 않는다.
- REC의 recordingOffsetMs가null일 때 onsetBeat·BPM만으로 정확한 녹음seek를 보장할 수 없다. 구간 시작·BPM단위·녹음 시작 여백을 포함한 앱 계산은 별도 확정 대상이다.
- SHT의 필수값/기본값, 버전타입, source·issues구조, 상태와오류, 파일한도, 합성방식, 출시우선순위는 코드가 없는 결정사항으로 남는다.

## 작성에 사용한 검토 산출물

영역별 `auth-379-review.json`, `catalog-ai-379-review.md`, `practice-379-review.md`, `learning-sheets-379-review.md`와 교차검토 `379-crossreview-auth.md`, `379-crossreview-catalog.md`를 종합했다. 개별 old/new·페이지ID·코드 근거는 각 영역 패치 JSON에 있다. 여기서는 계약에 영향을 주는 결론만 압축했으며 원문 페이지를 통째로 복사하지 않았다.
