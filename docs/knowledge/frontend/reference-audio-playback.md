# 기본음·시김새 녹음의 앱 연결

S15P31E102-367, 2026-11-02. [기능 계약](https://lab.ssafy.com/s15-final/S15P31E102/-/blob/6b6157d686569b78871ff3384fc735e38b9e855e/docs/specs/reference-audio-catalog.md), [DB/API와 운영 설정](https://lab.ssafy.com/s15-final/S15P31E102/-/blob/6b6157d686569b78871ff3384fc735e38b9e855e/docs/knowledge/backend/reference-audio-catalog.md).

## 운지와 녹음의 제공 범위 차이 (2026-11-02)

`7658ce59`의 운지표와 4악기 원본 녹음 라벨을 대조했다. 운지 카드는 [FingeringTables.kt](../../../frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/core/model/fingering/FingeringTables.kt)의 교육자료 기반 표이고, 연주 옵션은 [ReferenceAudioViewModel.kt](../../../frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/feature/guide/ReferenceAudioViewModel.kt)의 악기·취법·율명 정확 일치로 연결한다. 두 자료의 수록 범위가 같다는 계약은 없으며, 운지 존재 여부로 녹음을 만들거나 녹음 존재 여부로 운지를 확정하지 않는다.

| 악기 | 운지는 있으나 연결할 녹음 라벨이 없는 카드 |
| --- | --- |
| 단소 | 평취 협·남 |
| 소금 | 평취 응 / 역취 청응·중청협·중청남 |
| 정악대금·산조대금 각각 | 저취 탁이·탁응·대·협·유 / 평취 이·응·청대·청협·청유 / 역취 청이·청응 |

반대 사례는 **단소 역취 청태**다. 앱 운지표에 `?????`와 `운지 확인 필요`가 남아 있지만 원본에는 `ngc-danso-2341.tone-005`(역취), `ngc-danso-2342.tone-010`(음계+요성) 녹음이 있다. [단소 라벨 근거](../data/danso.md)의 역취 태와 화면 청태를 명시적으로 대응한다. 녹음의 inferred/채점 제외 상태는 운지 검증과 별개다.

**후속 원문 확인·설명 정정:** 앞선 “자료가 갈려 미확정” 설명은 코드 주석을 실제 검증 사실로 확대했다. `git log --all -S "자료가 갈려" -- docs frontend`와 최초 UI 구현 `0cb27b2c`의 Task150 계획/운지표를 확인하면 와이어프레임 이식 때부터 미확정 값이 있었으며, 국악원 원문끼리 운지가 충돌한다는 대조 기록은 확인되지 않았다. 2026-11-02 [국립국악원 e국악아카데미 단소 안공법](https://academy.gugak.go.kr/dp/pds/HTML5-1-2-2/HTML5-1-2-2-4/index.htm)의 본문과 HTML 표를 직접 대조했다. 높은 태는 제1공·제3공을 막고 제2·4·5공을 여는 `● ○ ● ○ ○`이며 앱의 구멍 순서로 `10100`이다. 본문도 높은 음의 운지는 낮은 음과 같다는 설명에서 태를 예외로 둔다. 따라서 이 사례는 녹음 부족과 달리 앱 운지표의 검증·보완 누락이다. 이번 후속 질의에서는 근거와 설명만 정정했으며 제품의 운지 값은 변경하지 않았다.

읽기 전용 대조는 `data/annotations/audio/{danso,sogeum,jeongak-daegeum,sanjo-daegeum}/**/labels.json`의 `columns`와 `groups[].rows`를 해석해 `(datasetId, register, yulmyeong)`으로 묶고, 운지표의 reading에 현재 `recordingLabel` 별칭을 적용해 비교했다. 단소 56개/10조합, 소금 146개/16조합, 정악대금 107개/18조합, 산조대금 121개/18조합이며 대응 카드 자체가 없는 녹음 조합은 0개였다. FE는 이 경로에서 `pitchId`를 필터로 쓰지 않으므로 DB의 미연결 FK가 위 빈 옵션의 원인은 아니다. 이는 저장소 라벨 대조이며 현재 운영 API의 게시 상태·실제 기기 재생을 새로 검증한 결과가 아니다.

사용자 요청에 따라 운지법 화면의 `등록된 율명·운지 자료` 버튼과 열기 상태/콜백을 제거했다. 세 화면 폭의 기존 카드와 내부 녹음 선택·재생은 유지한다. 녹음이나 운지의 빈 부분을 다른 음으로 대체하는 변경은 하지 않았다.

### 승인 후 단소 높은 태 운지 보완

후속 사용자 승인으로 기존 단소 청태(㶃)의 구멍 상태를 국악원 안공법의 `10100`으로 수정했다. `운지 확인 필요` 표시가 해제되고 제1공(뒷구멍)·제3공을 막는 그림이 같은 공유 운지 데이터에서 제공된다. 단소 안내와 기초 학습의 역취 설명에도 높은 태의 운지는 낮은 태와 다르다는 예외를 넣었다. 기존 음 목록·율명·옥타브·취법과 `danso/역취/태` 녹음 선택은 유지하며, 녹음이 없는 카드에는 운지와 함께 `녹음 자료 없음`을 표시한다. 위의 430행 대조와 미확정 운지 설명은 수정 전 `7658ce59` 기준이다.

원문에서 독립적으로 읽은 `Closed/Open/Closed/Open/Open`을 기대하는 `FingeringTest.dansoHighTaeUsesNationalGugakAcademyFingering`가 기존 코드의 `Unknown` 5개 때문에 실패함을 확인했다. 수정 후 운지 검사 11개가 통과했고, 낮은 태의 전개방·청태의 기존 녹음 식별·미확정 운지 일반 처리도 검사했다. 전체 `:shared:testAndroidHostTest`는 585개 중 583개 통과/기존 opt-in 2개 제외/실패·오류 0, `:androidApp:assembleDebug` 성공. 로그는 로컬 `%TEMP%/fe367-fingering-{red,green,final}.log`, JUnit은 `frontend/shared/build/test-results/testAndroidHostTest/`다. 실제 화면·실기기 청취·iOS 검증은 이번 변경에서 수행하지 않았다.

2026-11-03 최신 develop `14456693` 통합 후에는 host 620개 중 617개 통과·opt-in 3개 제외(CatalogLocalServerTest, GeneratedCatalogBundleTest, QaLiveCatalogTest), Android APK·Wasm 개발 번들 성공을 확인했다. 새 단계 연습 이동과 카드 내부 재생을 함께 보존했고 DB/API·운지표 전체 정합성 검수는 추가로 수행하지 않았다. 첫 Web 번들은 선언된 `@js-joda/core:3.2.0`의 로컬 설치 폴더 누락으로 실패했다. `:kotlinWasmNpmInstall --rerun-tasks --info`를 단독 실행한 뒤 파일 존재와 Web 번들 성공을 확인했으며 의존성·lock·빌드 설정은 변경하지 않았다. 설치가 처음 누락된 원인은 확정하지 않았다. 실제 UI·운영 재생·iOS 검증은 이전 근거와 구분한다.

## 연결 방식

운지법과 기초 학습의 음 듣기는 악기·취법·율명에 맞는 게시 음원 목록을 연다. 실제 녹음에는 같은 음의 강·중·약, 요성, 서로 다른 구간이 있어 첫 항목을 대표음으로 자동 선택하지 않는다. 주법·율명·연주 예시 번호를 보고 사용자가 파일 하나를 선택한다. 전체 음원과 기본음계/시김새 필터로 같은 악기의 다른 연주 예시도 찾을 수 있다.

최초 구현은 외부 URI 재생이었고, 사용자 수정 요청으로 앱 내부 재생으로 교체했다. 명시적 클릭에서 화면 전용 플레이어가 재생한다. 서버가 반환한 경로가 선택한 자산의 `/api/reference-audios/{assetId}/play`인지 검사하고 앱의 기존 API 주소로 해석한다. R2 비밀값과 별도 프론트 환경변수는 필요 없다. 목록에는 조회 중·비어 있음·실패/재시도 상태가 있으며 필터 변경·창 닫기는 이전 요청을 취소한다.

`SoundPlayer`는 기존 로컬 리소스 재생을 유지한다. 새 `rememberReferenceAudioPlayer`는 Android의 기존 AudioPlayer, Web의 HTMLAudioElement, iOS의 HTTP 로드/AVAudioPlayer 구현을 화면에 제공한다. 다른 기능의 플레이어 DI는 변경하지 않았다. 시김새 설명의 합성 MP3는 그림과 고정 시간표로 묶여 있으므로 유지한다. 이 연결은 그 그림의 재생 파일을 임의로 치환하지 않는다.

## 화면 음 이름과 원본 라벨

운지표와 녹음 목록은 표기 방식이 다르다. [단소](../data/danso.md)의 평취/역취 같은 다섯 율명, [정악대금](../data/jeongak-daegeum.md)의 저취 탁임~중·평취 임~청중·역취 청임, [산조대금](../data/sanjo-daegeum.md)의 취법별 기본 율명, [소금](../data/sogeum.md)의 역취 마지막 임 구분을 근거로 악기·취법별 명시적 별칭을 사용한다. 화면 이름과 원본 파일 라벨은 그대로 표시한다.

- 단소 역취 청중은 데이터의 역취 중으로 조회한다. 이를 평취 중과 합치지 않는다.
- 소금 역취 청임은 임, 중청임은 중청임으로 조회한다. 중청남은 자료가 없으므로 남으로 대체하지 않는다.
- 청/탁/중청 접두사를 일괄 삭제하지 않는다. 반음계 등 없는 음에 가까운 다른 녹음을 붙이지 않는다.
- 추정 라벨은 연주 예시 조회에만 사용하며 표준 주파수·운지 정답·채점 기준 확정으로 해석하지 않는다.

## 검증 경계

로컬 브라우저 검증은 실제 앱 빌드, 생성 manifest와 M4A를 제공하는 폐기 가능한 HTTP fixture를 사용했다. 이것은 실제 R2 공개·서명 검증을 대신하지 않는다.

- 신규 repository·상태·명시적 별칭 검사14개 통과. 구현 전 repository1개 및 별칭2개의 기대 실패를 관찰했다.
- `:shared:testAndroidHostTest`:548개 중546개 통과, 기존 opt-in 서버 검사2개 제외. 실패0.
- `:webApp:wasmJsBrowserDevelopmentWebpack` 성공. production 최적화는 완료 전 중단했으며 배포 빌드 통과로 보고하지 않는다.
- 브라우저:360×740·390×844·1280×900에서 선택 창과 운지/기초 학습 경로를 확인했다. 단소 평취 중4개, 전체56개, 시김새26개, 역취 청중→원본 역취 중2개, 학습4단계 태/황 각4개를 확인했다.
- `ngc-danso-2338.tone-001` 선택은 정확한 assetId의 `/play`302를 거쳐 실제 생성 M4A의200/206 응답으로 이어졌다. 파일을 브라우저·외부 재생기로 여는 경로 검증이며 스피커 청취 품질 검증은 아니다.
- fixture503→재시도→빈 목록 안내, 필터 전환, 닫기 버튼과 Esc의 시각적 창 닫힘 확인. 접근성 트리는 창 닫힘 뒤 일시적으로 이전 항목을 남겼으므로 화면으로 대조했으며 전체 키보드 초점 순서 검증으로 확대하지 않는다.
- 브라우저에는 기존 Kotlin/Wasm 메모리 API deprecation 메시지가 관찰됐다. 원격 R2 다운로드·운영 배포·iOS 빌드·모바일 실기기 청취는 미검증이다.

검증 소스는 frontend 작업 커밋`b3cfb852`와 통합 커밋`3c477655`의 동일 frontend 트리다. 로컬 증거는 주 worktree의 Git 제외 `data/processed/reference-audio-v1/frontend-fixture-requests.log`, `frontend-ui-verification.json`과 frontend worktree의 `reference-audio-final.log`·JUnit XML이다. 브라우저 화면은 작업 대화의 CUA 캡처로 확인했다. fixture 서버는 검증 후 종료한다.

## S15P31E102-367: 앱 내부 재생으로 수정

위 첫 브라우저 검증은 외부 재생 단계의 역사적 기록이다. 사용자 요청 후 파일/label ID를 숨기고 주법·율명·연주 예시 번호만 표시한다. 재생/정지 버튼, 로딩/오류 안내, 완료 상태를 제공한다. 새 음원을 고르면 이전 재생을 해제하고 필터 변경·창 닫기·백그라운드 전환에도 정지한다.

최종 Android host548개 중546통과/기존opt-in2skip, Wasm 개발 Webpack 성공. 브라우저에서 실제 M4A의 paused=false/currentTime 증가(0.110442초)/duration5.04288초, 자산 교체 시 audio요소1개, 정지 시0개, 파일404 후 오류 안내와 재시도 복구를 확인했다. 실제 R2와 모바일 스피커 청취 품질은 확인하지 않았다. iOS 소스는 Windows에서 컴파일하지 못했다. 최종 로그는 Git 제외 `data/processed/reference-audio-v1/in-app-build.log`, 브라우저 캡처/관찰은 작업 대화에 남겼다.

Jira 경계:364 DB/API,366 M4A 변환·검증,367 앱 재생,368 운영 R2/DB 적용. 모두 Story28/Epic16. 코드 검증 결과를 분리 추적하며 기존 공유 커밋의 Jira Key는 바꾸지 않는다.

## MR 분리

선행366(변환) →364(DB/API) 병합후367(앱재생)를검토한다. 이MR은프론트만포함하며 frontend제품트리는 기존검증스냅샷fa7f2849와동일하다. 전체host548중546통과/기존2skip, Wasm개발번들및 실제웹재생검증결과는코드동일성확인후재사용한다. iOS·모바일실기기 확인은미검증으로유지한다.

## 운영 음원과 간결한 플레이어 (2026-11-02)

이 절은 위 초기 MR 분리 시점의 검증을 갱신한다. 선행364·368이 완료되어 실제 운영 카탈로그와 R2 음원으로 확인했다. 사용자 요청에 따라 목록에는 주법·율명·연주 예시와 길이만 남기고, 선택한 음원의 재생·일시정지·이어 듣기 버튼과 현재/전체 시간·진행 막대를 목록 아래 고정했다. 진행 막대는 읽기 전용이며 드래그 탐색은 제공하지 않는다. 출처는 작은 `출처·가공 정보` 링크를 누를 때만 열린다. 국립국악원, 공공누리 제1유형(KOGL-1), 구간 절단·채널 평균·PCM16 변환 후 AAC-LC(M4A) 인코딩 설명과 원본 링크를 유지한다.

같은 음원의 Playing은 pause, Paused는 resume으로 처리해 재다운로드 없이 위치를 보존한다. 완료·실패·중단 후에는 원래 API 재생 경로로 다시 요청한다. 다른 음원 선택은 이전 재생을 교체하고, 필터 변경·닫기는 해제한다. 이 결정은 다른 기능의 AudioPlayer 계약이나 서버 API를 바꾸지 않는다. iOS의 resume이 실패하면 타이머와 플레이어를 해제해 Failed가 Completed로 덮이지 않게 했다.

검증 소스는 플레이어58a12cd2, 이어 듣기 보완8fe0c0de와 develop720d0c9e를 통합한5a1a7021이다. PowerShell에서 Android SDK를 지정하고 frontend에서 다음 실제 작업을 실행했다.

```powershell
$env:ANDROID_HOME='C:/Users/SSAFY/AppData/Local/Android/Sdk'
./gradlew.bat :shared:testAndroidHostTest :webApp:wasmJsBrowserDevelopmentWebpack :androidApp:assembleDebug --console=plain
```

- 통합 전566개 중564통과, 통합 후578개 중576통과. 모두 기존 opt-in2skip, 실패·오류0. Wasm 개발 번들·Android debug APK 성공. production 최적화 빌드 결과는 아니다.
- 일시정지 테스트는 기존 stop 동작에서 Paused 대신 Idle이 되는 실패를 관찰한 뒤 통과했다. Playing/Paused 토글, Loading 취소, 다른 자산 교체, Completed/Failed/Interrupted 재요청을 네 테스트에서 검사한다.
- 루프백 검증 서버는 운영 `https://k15e102.p.ssafy.io`의 공개 조회와 재생302만 중계하고, 브라우저가 실제 R2 M4A를 받았다. 인증정보를 전달하거나 운영 데이터를 쓰지 않았다. 단소 평취 중4개, 전체56개를 확인했다.
- 자산c4bdaed9…의 실제 길이5.04288초, Playing의 currentTime3.493788초, 완료 시5.04288초/진행100%를 관찰했다. 별도 재생에서0.191288초에 일시정지해 다음 관찰까지 같은 위치를 유지했고 이어 듣기 후0.326847초로 증가했다. resume 때 새 `/play` 요청은 없었다.
- 닫기 버튼으로 창이 사라지고 audio요소0개가 됐다. 출처 링크를 열고 닫으면 목록이 남고 원본 보기 동작은 재생을 해제한다. 출처 원문 페이지의 로딩 성공까지 검증한 것은 아니다.
- 검증 서버에만 음원404를 주입해 실패 안내를 확인하고, 정상 모드로 돌려 재시도 시 readyState4/paused=false로 실제 음원이 복구됐다. 앞선503 목록 오류도 재시도 후56개로 복구됐다.
- 390×844·360×640 화면에서 목록, 고정 플레이어, 출처 링크와 닫기가 겹치지 않는다. 독립 코드·스크린샷 리뷰에서 추가 차단 결함은 없었다. 접근성 대비 수치를 측정한 검사는 아니다.

증거는 FE worktree의 Git 제외 `frontend/reference-audio-player-integrated-build.log`, JUnit XML, `data/processed/reference-audio-fe367/requests.jsonl`, `player-paused.png`, `player-mobile.png`, `player-small.png`에 있다. 요청 로그에는 서명 URL을 남기지 않는다. iOS 컴파일과 모바일 실기기 청취는 미검증이다. IAB에서 새 탭을 열어도 원래 문서가 visible로 남아 실제 백그라운드 전환을 입증하지 못했다. 출처 팝업 이후 접근성 트리가 지연되어 화면으로 대조했으며 전체 초점 순서·Esc 통과를 주장하지 않는다.

## 카드 안 재생과 탐색으로 대체 (2026-11-02)

사용자가 별도 재생창을 없애고 카드에서 옵션을 고른 뒤 재생하며, 진행 막대를 터치·드래그로 조절하도록 요청했다. 이에 위 모달/읽기 전용 막대 결정은 폐기했다. 운지법의 세 너비 배치와 기초 학습4·6단계는 공통 운지 카드 아래에 같은 컨트롤을 넣는다. 옵션 버튼을 누르면 다음 녹음으로 순환하고 현재 번호/전체 개수를 표시한다. 선택은 자동 재생하지 않는다. 옆 버튼 하나가 재생·일시정지·이어 듣기를 맡고 출처는 같은 카드 안에서 펼친다.

한 Route가 악기 목록과 player 하나를 소유한다. 각 카드는 정확한 dataset/register/yulmyeong으로 기본음·시김새 옵션을 추린다. 카드별 선택은 화면 상태에 보존하며 악기·취법·학습 단계 변경에는 초기화한다. Lazy 카드에 네트워크나 player를 생성하지 않는다. 이전 화면/악기의 늦은 응답은 취소 및 ensureActive로 차단한다.

기존 AudioPlayer 계약은 유지하고 ReferenceAudioPlayer 하위 계약에 seekTo를 추가했다. Web은 HTMLAudioElement.currentTime, Android는 기존 MediaPlayer.seekTo, iOS는 AVAudioPlayer.currentTime을 사용한다. 탐색은 재다운로드하지 않고 Playing/Paused를 보존한다. Completed에서 이동하면 Paused로 준비한다. 로딩·실패·미선택에는 Slider를 비활성화한다. Android는 이 화면의 인스턴스에만 완료 미디어 보존 옵션을 적용하고, iOS는 완료 후 이어 듣기 때 위치 관찰을 재개한다. 다른 재생 기능의 완료 동작은 유지한다.

검증 결과:

- 옵션 순환·카드별 보존의 구현 전 의미 실패2개를 관찰한 뒤 ReferenceAudio focused 검사 통과. seek 상태/범위 정책은 stub에서3개 의미 실패 후4개 통과했다.
- 실제 데이터에1초 미만 음원이 있어 시간을 소수 첫째 자리까지 표시한다. 210/420/999ms와 분 경계를 검사하며 기존 정수 표시에서1개 실패 후 통과했다.
- 최종 전체 host584개 중582통과·기존 opt-in2skip, 실패/오류0. Wasm 개발 번들·Android debug APK 성공. 마지막 비활성 버튼색·원본 링크색 변경 후 Web/APK도 다시 빌드했다. 로그는 `frontend/reference-audio-inline-{red,green,final,visual}.log`와 `reference-audio-subsecond-red.log`.
- 실제 운영 목록/R2의 단소 평취 중 옵션1/4를 선택할 때 audio요소0개, 재생 후 currentTime3.845399초/전체5.04288초를 확인했다. 완료 뒤 막대 클릭은1.394초/Paused, 드래그는3.429초/Paused, 이어 듣기는3.607968초/Playing이었다. 재생 중 탐색도2.866105초/Playing을 유지했다. 옵션2/4로 변경하면 audio요소0개, 시간0초와 해당 음원 길이4.8초로 초기화됐다. 탐색·이어 듣기 중 새 `/play` 요청은 없었다.
- 출처를 같은 카드에서 펼치고 접었다. 390×844 운지 카드와1280×900 학습 양쪽 배치에서 컨트롤이 겹치지 않았다. 학습4단계의 태/황 옵션12개/9개와 명시적 재생 요청, 화면/배치 경로 전환 뒤 audio요소0개를 확인했다. 미연결 반음에는 빈 음원 안내를 표시한다.
- 독립 코드 리뷰의 짧은 음원 표시 지적을 수정했고 재검토에서 추가 차단 결함은 없었다. Windows에서는 iOS 컴파일과 모바일 실기기 탐색/청취를 확인하지 못했다. IAB의 방향키 입력으로 Slider 위치가 바뀌는 것은 관찰하지 못해 전체 키보드 접근성 통과로 보고하지 않는다. 터치에 해당하는 클릭과 드래그는 실제 UI로 확인했다.

레이아웃 연결378b466d, 플랫폼 탐색228db865와 후속 상태·컨트롤 변경이 검증 대상이다. 화면 증거는 Git 제외 `data/processed/reference-audio-fe367/inline-mobile.png`, `inline-learn.png`, `inline-expanded.png`, 최종 `inline-final.png`에 둔다. 이전 모달 스크린샷은 과거 검증 자료이며 최종 UI 증거로 사용하지 않는다.
