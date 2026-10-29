# 모범 연주 API와 Android 재생 (S15P31E102-261)

CAT-05의 게시 음원 메타데이터를 기존 연습 화면의 모범 연주 조작부에 연결한다. 서버 계약은 S15P31E102-288의 `GET /api/songs/{songId}/model-audio`다. 이 구현은 운영 음원 적재·공개 사용권·실기기 음악적 품질 승인과 별개다.

## 계약과 상태

`ApiModelAudioRepository`는 양의 Long 문자열 ID, 정수 scoreVersion, BPM 30~400, QUARTER/DOTTED_QUARTER 단위, HTTPS URL, 지원 MIME, 소문자 SHA-256을 검사한다. 사용자정보·fragment·공백이 있는 URL은 거절한다. MediaPlayer의 위치 범위에 맞춰 길이는 양의 Int 밀리초 범위만 지원한다. 메타데이터 SHA의 형식 확인이며 스트리밍 파일 전체의 해시 재계산은 아니다.

404는 게시 음원 없음, 통신/응답 오류는 실패로 구분한다. 악보의 ID·버전·기본 BPM·박 단위가 일치해야 재생한다. 다른 버전 음원이나 샘플 파일로 대체하지 않는다. 로딩·미등록·오류·플랫폼 미지원·포커스 중단을 화면에 표시한다. 오류와 미등록 상태는 같은 조작부에서 다시 조회할 수 있다.

재생 중 위치는 MediaPlayer의 실제 media position에서 `startOffsetMs`를 뺀 뒤 기본 BPM과 ticksPerBeat로 원본 tick에 대응한다. 녹음용 `PracticeTimelineClock`으로 소리가 재생되는 것처럼 진행하지 않는다. 앞 무음과 잔향은 실제 파일 길이대로 재생하고 악보 길이를 늘리지 않는다. 템포 변경은 pitch를 유지한 재생 속도 비율로 적용한다. 화면 시간은 현재 템포에서의 악보 위치이며 템포 변경 시 실제 청취 누적 시간과 다를 수 있다.

## 플랫폼과 수명

- 공통 `AudioPlayer`를 플랫폼 DI로 공급한다. Android는 화면마다 실제 `AndroidAudioPlayer`, iOS/Web은 명시적 `UnavailableAudioPlayer`다. Windows에서 iOS 빌드를 확인하지 않았다.
- MediaPlayer 생성·data source 설정·prepareAsync·seek·재생·release는 전용 HandlerThread 한 곳에서 실행한다. 준비/seek가 30초 내 끝나지 않으면 실패로 복구한다.
- 재생 시작/재개 전에 오디오 포커스를 요청한다. 포커스 상실·일시 상실·duck 요청은 중단으로 처리하고 사용자가 눌러 재개한다. 헤드폰 분리 방송도 같은 중단 경로다. 자동 백그라운드 재생과 서비스는 추가하지 않았다.
- ON_STOP/화면 폐기 때 위치를 보존하고 네이티브 플레이어·포커스를 해제한다. 중복 수명 알림으로 위치를 0으로 덮어쓰지 않는다. 평가 녹음 시작·화면 뒤로 가기·ViewModel 해제도 재생을 종료한다.
- 기존 마이크·INTERNET 권한 외 새 권한, exported 컴포넌트, Intent 전달, SDK 의존성을 추가하지 않는다. 동적 수신기는 시스템 보호 방송 `ACTION_AUDIO_BECOMING_NOISY`만 받고 API 33 이상은 NOT_EXPORTED다.

## 검사와 한계

현재 테스트 스택은 Koin DI, kotlin.test, kotlinx-coroutines-test, Ktor MockEngine이다. 새 테스트 프레임워크를 설치하지 않고 HTTP/플레이어 경계의 테스트 대역으로 실패·중단·지연 응답을 재현한다. 테스트 대역은 commonTest에만 있으며 release DI에 없다.

- CAT-05 메타데이터·ID·URL·404/오류·악보 일치: `ModelAudioRepositoryTest`.
- 재조회, 숨긴 화면의 지연 응답 억제, 실제 위치 재개, 다른 버전/잘린 파일 거절, 앞 무음·잔향·템포 변경: `ModelAudioSessionTest`.
- 연습 화면의 긴 NOTE/tick·박 표시, 일시정지, 화면 이탈·재개, 실제 player 속도 전달: `PracticeLiveProgressTest`. 녹음 시계 테스트는 보존한다.
- 재현: frontend에서 `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug`, 웹 공통 코드 `:shared:compileKotlinJs :shared:compileKotlinWasmJs`.
- 로컬 서버 설정이 없는 `CatalogLocalServerTest`는 skip이며 실제 서버 검사 통과로 기록하지 않는다.
- 실제 HTTPS 파일의 Android 디코딩·스피커 출력·포커스 경합·통화·헤드폰·화면 회전/분할 크기 변경 관찰은 통합 에뮬레이터 또는 후속 실기기 검사다. 자동 상태 검사만으로 이 검사가 통과했다고 하지 않는다.

## Android skills 적용

프로젝트에 설치된 공식 `android-permissions-security`, `android-intent-security`, `edge-to-edge`, `testing-setup`, `anr-audit-and-fix`를 이 추가분에 대조했다. UI는 기존 inset 처리 안에 상태 문구를 추가하며 새로운 Activity/권한/Intent 경계를 만들지 않는다. 기존 테스트 스택을 유지한다. 이 기록은 기존 Play 정책 감사의 통과를 의미하지 않는다.

ANR risk: none found in this file (`AndroidAudioPlayer`). 후보 `setDataSource`/미디어 IPC는 공개 메서드 → `dispatch` → 전용 HandlerThread에서 실행하므로 메인 스레드 차단 후보에서 제외한다. `prepare` 대신 `prepareAsync`를 사용하며 Handler delay는 스레드를 블로킹하지 않는다. Attached View를 worker에서 수정하지 않고 StateFlow만 갱신한다. 실제 ANR trace 수집이나 성능 측정 결과는 아니다.

근거: [Android audio focus](https://developer.android.com/media/optimize/audio-focus), [MediaPlayer state/resources](https://developer.android.com/media/platform/mediaplayer/state-resources), [MediaPlayer API](https://developer.android.com/reference/android/media/MediaPlayer). 2026-10-29 확인. 앱의 minSdk 24에 맞춰 포커스/seek의 API 26, 동적 수신기 API 33 경계를 분기한다.
