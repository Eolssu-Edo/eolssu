# 모범 연주 API와 Android 재생 (S15P31E102-261)

CAT-05의 게시 음원 메타데이터를 기존 연습 화면의 모범 연주 조작부에 연결한다. 서버 계약은 S15P31E102-288의 `GET /api/songs/{songId}/model-audio`다. 이 구현은 운영 음원 적재·공개 사용권·실기기 음악적 품질 승인과 별개다.

## S15P31E102-859 모범 음원 파일 캐시

2026-11-13 사용자 요청으로 공개 모범 음원의 파일을 화면 수명과 분리했다. [스펙](../../specs/model-audio-file-cache.md)에 따라 `ApiModelAudioRepository`는 기존 게시 메타데이터·시간표 검증을 마친 뒤 `ModelAudioFileCache`에서 SHA-256이 같은 파일을 얻는다. `ModelAudio.localFile`은 직렬화하지 않으며, `ModelAudioSession`이 기존 `AudioSource.localFile`로 전달한다. 내 악보의 `SheetPlayback`과 장단 포함 여부는 그대로 구분한다.

Android `cacheDir/model-audio-v1`, iOS `NSCachesDirectory/model-audio-v1`에 최대 4개/128 MiB(파일당 최대 32 MiB)를 저장한다. 파일 해시를 다운로드 뒤와 재사용 때 확인한다. 취소된/불완전한 파일은 완료 파일로 게시하지 않고 임시 파일에서 atomic move한다. 파일 삭제·손상 시 다시 받고, 캐시 다운로드 전체 10초 제한이나 저장 오류 때는 기존 원격 URL을 사용한다. 인증 쿠키·토큰 갱신을 설치하지 않은 별도 클라이언트가 최대 3회 HTTPS 리다이렉트를 검증한다. 개발 빌드의 기존 127.0.0.1 허용 조건만 계승한다.

현재 게시 메타데이터 조회는 매 진입 유지한다. 따라서 게시 해제·악보/음원 교체를 파일 캐시가 우회하지 않는다. 메타데이터 조회 실패 시 캐시만으로 오프라인 재생하지 않는다. 최초 파일 준비와 로컬 디코더 초기화는 여전히 필요하며, 반복 방문의 음원 재다운로드를 없애는 변경이다. 화면 이탈 시 네이티브 플레이어·포커스를 해제하는 기존 수명 관리는 유지한다.

검증에서 최초 파일 재사용 테스트의 실패를 관찰한 뒤 통과를 확인했다. 화면을 새로 만드는 재생 통합 테스트도 캐시 연결 전 로컬 파일 미전달로 실패했고 연결 후 통과했다. `ModelAudioFileCacheTest`는 저장소 재생성, 서명 URL 변경, 손상/삭제, 동시 요청, 최근 사용 순서 제거, 취소/시간 초과, 크기 제한, 잘못된 리다이렉트를 확인한다. `ModelAudioCachePlaybackTest`는 실제 Repository·Session과 파일 캐시를 함께 사용해 재진입 다운로드 1회, 메타데이터 재확인, 음원 교체, 파일 받기 실패 시 원격 경로, 게시 해제 거부를 확인한다. HTTP 엔진과 파일 작업을 같은 테스트 스케줄러에 놓아 가상 시간과 실제 실행 스레드 혼용으로 생기는 거짓 시간 초과를 제거했다([Ktor 3.6 MockEngineConfig](https://api.ktor.io/ktor-client-mock/io.ktor.client.engine.mock/-mock-engine-config/index.html)).

`frontend`에서 `:shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug :shared:compileKotlinJs :shared:compileKotlinWasmJs --max-workers=2`가 통과했다. 전체 Android host 검사는 1,629개 중 1,624개 통과·환경 조건부 5개 생략이며 실패는 0개다. Windows에서 iOS DI·AVPlayer는 빌드/실기기 미확인이다. Android 실기기 관찰 결과는 아래에 별도로 기록한다.

### Android 실기기 관찰

### 게시 전 리뷰 보완

`070c21cf` 검토에서 P2: 앱이 숨겨진 동안 OS가 캐시를 제거했을 때 이어 듣기가 삭제된 경로를 그대로 여는 문제를 확인했다. `ModelAudioSession.play`의 일시정지 상태 재사용이 Repository의 파일 검증을 건너뛰는 것이 원인이었다. 파일 삭제 후 같은 Session에서 재생하는 통합 검사는 보완 전에 추가 다운로드 0회로 실패했다. 네이티브 플레이어가 해제된 캐시 음원은 Repository를 다시 거쳐 복구하도록 바꾸고, 원래 미디어 위치 보존·게시 음원 교체 시 악보 위치 대응·일반 일시정지의 추가 조회 없음까지 검사했다. 보완 후 관련 테스트는 통과했다. 이 복귀 경계는 격리 테스트 결과이며 아래 실기기 재진입 관찰과 구분한다. 검토는 변경 작성과 같은 컨텍스트에서 수행했으며 독립 리뷰로 표기하지 않는다.

### Android 실기기 재진입 관찰

Galaxy S24+ SM-S926N / Android 17에서 별도 패키지 `com.ssafy.eolssu.qa859`를 설치했다. 기존 앱 데이터와 분리한 QA 빌드, 로컬 카탈로그·가짜 로그인 응답·800,044바이트 WAV 고정 음원을 사용했다. 실제 앱에서 연습곡 선택 → 모범 연주 재생 → 뒤로 → 같은 곡 재진입 → 재생을 관찰했다. 두 재생 모두 진행 시간 0:01과 ‘일시정지’ 상태를 확인했고, 음원 메타데이터 요청은 1→2회, 재생 리다이렉트와 파일 요청은 각각 1→1회였다. 캐시 파일의 기기 `sha256sum`은 메타데이터의 `6106d603b0cff4fe1a05daacfc0e2d65f85b2a0006a09401f01e2a9998c008c2`와 같았다. 두 번째 뒤로 가기 후 곡 선택 화면 복귀도 확인했다.

ADB 재연결로 reverse가 사라진 시도에서는 연결 오류가 발생했으며, reverse 복원 후 다시 확인한 결과만 위 관찰에 포함했다. 이 검사는 Android 네이티브 파일 재생·반복 방문의 음원 요청 제거를 증명한다. 운영 R2·서명 HTTPS 전체 경로, M4A 음질, 정밀한 시작 시간 단축 비율을 측정한 결과는 아니다. HTTPS 리다이렉트·서명 주소 변경·해시 검증은 별도 격리 HTTP 검사로 확인했다.

로컬 근거는 해당 worktree의 `frontend/build/qa859/journey-results.json`(최초 재생), `play-reentry.xml`, `reentry-counts.json`, `after-back.xml`, `requests.json`이며 전체 빌드 로그는 `frontend/build859.log`다. 종료 후 해당 QA 앱·전용 ADB reverse·UI 임시 파일·로컬 fixture 서버를 정리했다. 공유 기기의 다른 앱과 포트는 변경하지 않았다.

## S15P31E102-380 후속 연결 범위

2026-11-02 사용자는 생성 자료를 먼저 사용하고 이상한 곡을 숨김·교체하는 흐름을 선택했다. FE380은 별도 MR !222로 유지한다. 최신 develop을 통합한 뒤 장단이 없는 곡도 원본 공통 악보의 시간·장단명을 그대로 사용한다. 장단 pattern은 만들지 않고 패턴 미리듣기·장단 박 표시만 비활성화한다. 기존 연결 장단의 이름·BPM 단위 검증과 공개4곡 동작은 유지한다.

CAT-05의 상대 `audioUrl`은 같은 서버의 재생 경로로 해석한다. 선택적 `performanceUrl`과 `sourceCommonSha256`이 함께 있으면 수행 JSON을 조회하고 기존 FE380 파서로 곡·악보 버전·음원 해시·길이·원본 SHA를 대조한 뒤 실제 연주 위치에 사용한다. 제공된 수행 데이터가 잘못되면 재생 실패로 처리한다. 수행 데이터가 원래 없는 기존 음원만 정본 시간표를 사용한다. 검증은 HTTP 대역의 정상·NULL·불일치·실패와 연습 상태 테스트, 전체 Android host 검사 및 Web 컴파일로 한다. 실제 기기·운영 배포는 별도 결과로 기록한다.

2026-11-02 검증: `origin/develop`의 `ba888d04`를 `554e22ea`로 통합했고, 전체 `:shared:testAndroidHostTest`는 571개 중 569개 성공·2개 명시적 skip·실패 0개였다. skip은 별도 서버를 요구하는 `CatalogLocalServerTest`와 운영 읽기 검사를 opt-in하는 `QaLiveCatalogTest`다. `:shared:compileKotlinJs`, `:shared:compileKotlinWasmJs`, 기본 설정의 `:androidApp:assembleDebug`도 통과했다. 새 회귀 검사는 111개 목록 중 장단 NULL 103개, NULL 장단의 CAT04 악보·시간 보존, 정악/산조 운지 구분, 상대 재생 경로, 수행 JSON의 누락·불일치 시 실패를 확인한다. 기존 연결 장단·4곡 회귀 검사도 통과했다.

`GeneratedCatalogBundleTest`는 `EOLSSU_MODEL_AUDIO_BUNDLE`로 지정한 외부 batch-339 원본 107개를 읽고, 실제 원문 SHA를 대조한 다음 앱의 공통 악보 파서와 HTTP 수행 시간표 연결을 모두 통과했다(단소 46·소금 23·정악대금 28·산조대금 10). 정악대금019의 정본 28초와 실제 수행 29000.014499999997ms 차이도 보존했다. 외부 원본·음원은 저장소에 추가하지 않는다. 재현은 환경변수를 지정한 뒤 `:shared:testAndroidHostTest --tests '*GeneratedCatalogBundleTest'`이며 미지정 시 skip이다.

배포 제약: 장단을 필수로 가정한 기존 앱은 NULL 곡이 있는 전체 목록을 읽지 못한다. 107곡을 공개하기 전에 이 변경을 포함한 Android 앱 업데이트가 필요하다. 기존 제공 곡 탭에서 연습 전용으로 열며 AI 평가 목록이나 회원가입 악기 카탈로그를 확대하지 않는다. Web/iOS의 `UnavailableAudioPlayer`는 유지하며 이번 Web 검증은 공통 코드·HTTP 변환과 컴파일 범위다. 신규 플랫폼 재생 구현은 포함하지 않는다. 실제 Android MediaPlayer 재생·화면 관찰은 별도의 격리 서버/에뮬레이터 검증 결과를 따라야 하며, Windows 빌드나 host 검사로 실기기 확인을 대신하지 않는다.

## 계약과 상태

`ApiModelAudioRepository`는 양의 Long 문자열 ID, 정수 scoreVersion, BPM 30~400, QUARTER/DOTTED_QUARTER 단위, 지원 MIME, 소문자 SHA-256을 검사한다. URL은 같은 곡의 정확한 `/api/songs/{id}/model-audio/play`를 서버 origin으로 해석하거나 기존 HTTPS URL을 수용한다. 사용자정보·fragment·공백이 있는 외부 URL은 거절한다. 수행 JSON은 같은 곡의 `/api/songs/{id}/model-audio/performance`만 조회하며 서버가 보관한 원본 `sourceCommonSha256`을 사용한다. CAT04 JSON을 다시 직렬화해서 원문 해시를 만들지 않는다. MediaPlayer의 위치 범위에 맞춰 길이는 양의 Int 밀리초 범위만 지원한다. 메타데이터 SHA의 형식 확인이며 스트리밍 파일 전체의 해시 재계산은 아니다.

404는 게시 음원 없음, 통신/응답 오류는 실패로 구분한다. 악보의 ID·버전·기본 BPM·박 단위가 일치해야 재생한다. 다른 버전 음원이나 샘플 파일로 대체하지 않는다. 로딩·미등록·오류·플랫폼 미지원·포커스 중단을 화면에 표시한다. 오류와 미등록 상태는 같은 조작부에서 다시 조회할 수 있다.

재생 중 위치는 MediaPlayer의 실제 media position에서 `startOffsetMs`를 뺀 뒤 수행 시간표가 있으면 그 구간의 원본 tick으로 대응하고, 없으면 기본 BPM과 ticksPerBeat로 대응한다. 녹음용 `PracticeTimelineClock`으로 소리가 재생되는 것처럼 진행하지 않는다. 앞 무음과 잔향은 실제 파일 길이대로 재생하고 악보 길이를 늘리지 않는다. 템포 변경은 pitch를 유지한 재생 속도 비율로 적용한다. 화면 시간은 현재 템포에서의 악보 위치이며 템포 변경 시 실제 청취 누적 시간과 다를 수 있다.

## 플랫폼과 수명

- 공통 `AudioPlayer`를 플랫폼 DI로 공급한다. Android는 화면마다 실제 `AndroidAudioPlayer`, iOS는 `IosAudioPlayer`(S15P31E102-373, Mac·실기기 미검증), Web은 명시적 `UnavailableAudioPlayer`다.
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
- 통합 후 실제 HTTPS M4A native probe: API37/x86_64/16KB에서 준비·위치 증가·pause/resume·속도·같은 UID의 두 번째 OS focus client 중단·오류·자원 해제가 통과했다. 별도 debug package/로컬 CA만 사용하고 삭제했으며 OS CA나 release 설정은 변경하지 않았다. 결과는 `model-audio-native-probe/audio-probe.json`, 전체 통합 검사는 [최종 기록](app-catalog-api-integration.md)을 따른다.
- 실제 HTTPS 파일의 Android 디코딩·스피커 출력·포커스 경합·통화·헤드폰·화면 회전/분할 크기 변경 관찰은 통합 에뮬레이터 또는 후속 실기기 검사다. 자동 상태 검사만으로 이 검사가 통과했다고 하지 않는다.

2026-11-02 FE380 후속 검증에서 공통 악보107개와 실제 BE HTTP 응답의 결합을 확인했다. Android API37 에뮬레이터의 실제 앱에서 단소 몽금포타령·정악대금 “중고태황” 연습곡·산조대금 늴리리야·소금 개구리소리의 R2 HTTPS M4A 재생과 진행 표시를 확인했다. 단소 일시정지38초→앞으로43초, 정악대금 수행 길이28초→29초 반영도 관찰했다. 테스트 앱 종료 후 네이티브 player는 남지 않았다. 음원은 실제 파일이지만 메타데이터/수행은 실제 BE 응답을 제공하는 로컬 fixture, 로그인은 가짜 로컬 응답이었다. 운영302 경로 전체·실기기 스피커 청음·Web/iOS 재생 통과로 확대하지 않는다. 외부 증거는 `audio-publish-preflight/android-publication-339/verification.json`에 보관한다.

## Android skills 적용

프로젝트에 설치된 공식 `android-permissions-security`, `android-intent-security`, `edge-to-edge`, `testing-setup`, `anr-audit-and-fix`를 이 추가분에 대조했다. UI는 기존 inset 처리 안에 상태 문구를 추가하며 새로운 Activity/권한/Intent 경계를 만들지 않는다. 기존 테스트 스택을 유지한다. 이 기록은 기존 Play 정책 감사의 통과를 의미하지 않는다.

ANR risk: none found in this file (`AndroidAudioPlayer`). 후보 `setDataSource`/미디어 IPC는 공개 메서드 → `dispatch` → 전용 HandlerThread에서 실행하므로 메인 스레드 차단 후보에서 제외한다. `prepare` 대신 `prepareAsync`를 사용하며 Handler delay는 스레드를 블로킹하지 않는다. Attached View를 worker에서 수정하지 않고 StateFlow만 갱신한다. 실제 ANR trace 수집이나 성능 측정 결과는 아니다.

근거: [Android audio focus](https://developer.android.com/media/optimize/audio-focus), [MediaPlayer state/resources](https://developer.android.com/media/platform/mediaplayer/state-resources), [MediaPlayer API](https://developer.android.com/reference/android/media/MediaPlayer). 2026-10-29 확인. 앱의 minSdk 24에 맞춰 포커스/seek의 API 26, 동적 수신기 API 33 경계를 분기한다.
