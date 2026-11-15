# Android 학습·운지 실제 흐름 검증과 관찰 기준

2026-11-15 [S15P31E102-934](../../plans/S15P31E102-934.md)에서 Galaxy S24+(Android 17/API 37, 1440×3120, density 600)를 사용했다. 명시적인 순차 인계 후만 조작했고 종료 후 사용권을 반환했다. 아래는 코드 추정과 구분한 실제 관찰이다.

## 재현한 결함

### 마지막 수동 박스와 단계 완료는 다른 상태다

`LearnSegmentPractice`는 음높이 계획이 있어도 마이크를 쓸 수 없으면 `해 봤어요`를 제공한다. 기존 단계 연결은 `pitch == null`만 축하 조건으로 삼아 `pitch != null && !canListen`을 빠뜨렸다. 할 일 박스는 모두 완료됐지만 단계 완료 창·서버 저장으로 이어지지 않았다.

인계받은 `qa933-local-fea297e4-qa309`에서 OS 마이크 설정을 거부한 뒤 요청 창도 거부했다. 5단계 두 박스와 6단계 한 박스를 끝내도 완료 창이 없고 목록은 `0 / 7`이었다. 수정본 `qa-local-934-3d9a7725`에서는 같은 권한 상태의 마지막 수동 확인 후 완료 창이 열리고 다음 단계 동작으로 저장됐다. 재실행 후에도 완료 상태가 유지됐다.

이미 실험으로 마친 박스는 전체 진도 초기화 대신 그 박스의 왼쪽 체크 동그라미로 다시 열었다. 제목을 눌러 펼치는 동작과 다르다. 이 동작은 해당 단계의 로컬 박스 진행을 줄여 저장하므로, 이번 테스트에서 만든 마지막 박스에만 적용했다. 기존 사용자 진도를 되돌리는 절차로 일반화하지 않는다.

관련 코드: `LearnStagePractice.kt`의 `completeLearnSegment`, `LearnStepper.kt`의 `StepBox.onUndo`, `LearnFirstTour.kt`의 `undo`.

### 시스템 바는 OS 테마와 앱 테마를 구분해야 한다

AndroidX activity 1.13.0의 `enableEdgeToEdge()` 기본 `SystemBarStyle.auto`는 OS `uiMode`로 아이콘 색을 판단한다. 앱은 `lightColorScheme`·`LightEolssuColors`를 고정 사용한다. OS 야간 모드가 yes인 실제 홈·학습 화면에서 흰 아이콘이 밝은 배경에 묻혔다.

`MainActivity`에서 두 바의 `auto`에 앱 테마 판단 `{ false }`를 제공했다. AndroidX 기본 scrim과 대비 처리 방식은 유지했다. 수정 APK에서 OS 라이트·다크 전환, 앱 재실행, 홈·학습·프로필 이동 뒤 어두운 아이콘과 제스처 표시가 유지됐다. Android 17에서 확인한 결과이며 구형 API의 시각 검증은 아니다.

## 실제 실행 범위

증거 루트는 `C:/higher/qa-evidence/S15P31E102-934/`다. 표의 이름은 그 안의 PNG/XML 또는 JSON이다. 계정이 보이는 원본 화면과 APK는 Git에 포함하지 않는다.

| 범위 | 관찰 결과 | 대표 증거 |
| --- | --- | --- |
| 홈 | 담당 기능 진입·Back 뒤 메뉴 스크롤 위치 유지 | `fixed-home-scroll-restored`, `fixed-home-after-reading` |
| 기존 5·6단계 | 마지막 박스는 완료, 완료 창 없음, 목록 0/7 | `baseline-stage5-last-manual`, `baseline-stage6-last-manual`, `baseline-completion-not-saved` |
| 수정 5·6단계 | 권한 차단 안내→수동 확인→완료 창→다음 단계→목록 2/7 | `fixed-stage5-complete-dialog`, `fixed-stage6-complete-dialog`, `fixed-completion-list` |
| 1·2단계 | 일부 박스 완료 후 이탈·재진입에서 진행 유지, 마지막 완료 창·저장 | `fixed-stage1-partial-reentry`, `fixed-stage2-partial-reentry`, `fixed-stage2-done-dialog` |
| 3·4·7단계 | 권한 거부 안내·설정 버튼·스스로 확인 경로, 이전/다음 이동 | `fixed-stage3-permission-options`, `fixed-stage4-self-check`, `fixed-stage7-blocked` |
| 전체 학습 | 단소 7/7 완료, 프로세스 재실행 후 유지, 소금 0/7와 분리 | `fixed-seven-persisted`, `fixed-sogeum-progress-separate`, `fixed-danso-progress-preserved` |
| 실패 복구 | 데이터 차단 후 앱 시작 연결 오류, 데이터 복원→재시도→로그인된 홈 | `fixed-offline-current`, `fixed-network-recovered` |
| 운지 | 단소·소금·산조대금·정악대금 기본음/요성 시작·정지, 음·취법·악기 전환, 이탈·백그라운드 해제 | `audio-danso-*`, `audio-sogeum-*`, `audio-sanjo-*`, `audio-jeongak-*`, `audio-instrument-change-released`, `audio-fingering-exit` |
| 읽기 | 6쪽·스크롤·이전/다음·목차 선택·목차 Back, 길이 4종/높낮이 3종 재생과 해제 | `fixed-reading-page1`~`page6`, `fixed-reading-contents-back`, `audio-reading-*` |
| 프로필 | 닉네임 임시 입력·아바타 임시 선택 취소, 원래 값 보존, 키보드 위 저장 버튼 표시 | `fixed-profile-nickname-draft-ime`, `fixed-profile-nickname-cancelled`, `fixed-profile-avatar-cancelled` |
| 문서 화면 | 출처·이용약관·개인정보·삭제 안내 열람과 Back | `fixed-profile-credits`, `fixed-profile-terms`, `fixed-profile-privacy`, `fixed-profile-deletion-information` |
| 시스템 바·율명 | 라이트/다크 OS에서 대비 유지. 관찰한 율명과 한글 이름의 연결·글리프·선택 테두리 이상 없음 | `fixed-home-light-system`, `fixed-home-dark-system-restored`, `fixed-*-register`, `fixed-stage4-box5` |

## 오디오 관찰에서 주의할 점

Android 구현은 `MediaPlayer`의 MEDIA/MUSIC 용도를 사용한다. 실제 재생의 양성 표본을 먼저 잡고 해당 UID/PID와 `piid`를 따라야 한다. 과거 `started` 로그를 현재 재생으로 해석하면 안 된다.

이번 검증에서는 앱 UID 10033의 현재 `AudioPlaybackConfiguration`에서 `state:started`를 확인한 뒤 정지·전환·이탈 후 현재 목록 소멸과 같은 `piid`의 release 이벤트를 대조했다. 예를 들어 소금 백그라운드 전환은 플레이어 125863이 시작된 뒤 약 1.2초 후 해제됐고 복귀 후 현재 플레이어는 0개였다. 읽기 높낮이 예시는 각각 125943→125951→125959로 바뀌며 동시에 시작된 앱 플레이어는 하나였다.

- 운지의 자연 종료는 `retainCompletedMedia=true`이므로 완료된 플레이어가 남을 수 있다. 이를 곧바로 누수로 판단하지 않는다.
- OS Home 직후 애니메이션이 끝나기 전에 앱을 다시 열면 `ON_STOP`에 도달하지 않을 수 있다. 최초의 너무 빠른 Home→복귀 표본은 백그라운드 종료 판정에서 제외했다. 후속 검사는 고정 sleep 대신 현재 플레이어가 해제될 때까지 최대 3초 관찰했다.
- 읽기 예시가 짧으므로 캡처를 먼저 기다리면 자연 종료를 놓칠 수 있다. 재생 직후 started 표본을 얻고 동작 후 release를 확인했다.
- 이 관찰은 재생 자원 수명 증거다. 스피커에서 올바른 음정·음질이 들렸다는 청음 증거는 아니다. 주변 소리 녹음은 수행하지 않았다.

## 한계와 재사용 조건

실제 기준 앱은 #372가 아니라 인계된 QA933이다. #372와 QA933의 해당 학습 소스 차이는 없었지만 그것을 #372 실행 결과로 표현하지 않는다. 최종 APK·서명·SHA와 자동 검사 결과는 작업 계획에 기록했다.

새 로그인, 로그아웃·계정 연결/해제·비밀번호 변경·탈퇴, 마이크 입력에 의한 자동 통과, 저장 API의 오프라인 실패, iOS/Web은 실기기 실행 범위에 없다. 약관은 화면 열람과 복귀를 확인했으며 법률적 타당성이나 뒤에 합쳐진 서버 새 판의 배포 상태를 확인한 것이 아니다.

계정 진도를 초기화하지 않았다. 테스트의 완료 저장으로 단소 0/7은 7/7이 됐으며 다른 악기와 구분해 유지했다. 닉네임·아바타는 원래 값, 마이크 권한·네트워크·OS 테마는 원래 설정, 현재 플레이어는 0개인 로그인된 홈에서 기기를 반환했다. `phone-handoff-final.json`이 종료 관찰을 모은 로컬 기록이다.
