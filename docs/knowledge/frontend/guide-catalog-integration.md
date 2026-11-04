# 학습 카탈로그 API 앱 연결

- 작업: S15P31E102-261. 서버 계약은 S15P31E102-288의 [카탈로그 조회 명세](../../specs/catalog-read-apis.md)를 따른다.
- 기준 소스: `b88c949`에 분리 구현. 최종 develop 통합·DI·Android 화면 검증은 261의 최종 결과를 함께 확인한다.
- 근거: 사용자의 2026-10-29 CAT-03~10 구현 및 앱 연결 요청. 서버 DTO의 ID·널·박 단위를 보존하며 등록되지 않은 콘텐츠를 생성하지 않는다.

## 연결과 상태

| API | 소비 화면 | 처리 |
| --- | --- | --- |
| CAT-06 `/pitches?instrumentId` | 운지법 → 등록된 율명·운지 자료, 음원 열기 | 활성 악기 목록의 실제 ID로 조회. 율명 ID·옥타브·반음 값·표기를 그대로 유지. null 음원은 미등록 안내. |
| CAT-07 `/instruments/{id}/fingerings` | 같은 자료 창 | 등록된 운지 설명·그림 링크와 구조 그림 링크. 구멍 위치 데이터가 없으므로 검증된 기존 로컬 운지 그림을 덮어쓰지 않음. |
| CAT-10 `/jangdans` | 정간보 쉽게 읽기 → 장단·기호 자료 | 실제 목록에서 장단 선택. 빈 목록·통신 실패·재시도 구분. |
| CAT-08 `/jangdans/{id}` | 선택한 장단 상세 | 원본 구음·묶음과 기준 BPM/박 단위 표시. 404는 미등록, 통신 실패는 재시도. |
| CAT-09 `/notation-symbols` | 같은 자료 창 | 실제 기호·설명·그림 링크. 기존 학습의 의미·순서를 바꾸지 않음. |

운지 화면의 악기 변경은 진행 중 조회를 취소하고 이전 자료를 비운다. 늦은 응답은 취소 상태를 확인하므로 다른 악기의 자료로 덮어쓰지 않는다. 실패는 빈 배열로 바꾸지 않으며 기존 학습 자료는 계속 볼 수 있다.

단소 `DANSO`, 소금 `SOGEUM`은 기존 앱 code 대응을 사용하고 ID는 조회 결과에서 찾는다. 정악/산조 대금의 구분 code는 현재 계약에 없으므로 일반 `DAEGEUM` 행 하나를 두 유형에 임의로 연결하지 않는다. 해당 유형은 미등록 상태로 남긴다. 현재 서버 시드는 단소만 있으며, 다른 악기 등록·유형 매핑은 데이터 담당의 확정 계약에 따른다.

자료 링크는 HTTPS·호스트 존재·사용자 인증정보 없음·fragment 없음 조건을 확인한 뒤 사용자가 누를 때만 외부 앱으로 연다. 외부 앱을 열 수 없으면 오류를 표시한다. 운지 화면은 음원이 없을 때 재생을 흉내 내는 버튼 강조를 하지 않는다. 실제 플랫폼 재생·음질 검증으로 간주하지 않는다. 기초 학습 화면의 별도 음 듣기 구현은 이 변경 범위 밖이다.

## 검증

2026-10-29 Windows/JDK21, 기존 KMP·Koin·Ktor MockEngine·kotlin.test·coroutines-test를 사용했다. 새 SDK·라이브러리·소스 세트는 추가하지 않았다. Android 공식 `testing-setup`의 기존 테스트 스택 유지와 테스트 소스에만 대역을 두는 원칙을 적용했다. `android-intent-security` 관점에서 새 exported 컴포넌트·중첩 Intent·PendingIntent·URI 권한 전달은 없다. 문자열 링크는 위 제한을 통과한 HTTPS만 외부로 연다.

```powershell
./gradlew.bat :shared:testAndroidHostTest --tests '*ReferenceCatalogRepositoryTest' --tests '*GuideCatalogViewModelTest' --tests '*FingeringViewModelTest' :shared:compileKotlinWasmJs --max-workers=2
```

- 통과: 20개 테스트, 실패/오류/skip 0. Repository 신규 10개, ViewModel 신규 5개, 기존 운지 상태 5개.
- 통과: Android 공통/플랫폼 소스 및 Wasm 컴파일, `git diff --check`.
- 검사: 실제 ID와 필터 전달, nullable 보존, 큰 ID, 중복·잘못된 ID·위험 링크·실패 envelope 거절, 빈 목록/404/503 구분, 장단 원본 패턴·박 단위, 악기 변경·취법 보존·재시도·늦은 응답 취소.
- 테스트 보정: 최초 빌드의 공유 상수 참조 누락을 복구했다. MockEngine 응답은 테스트 스케줄러 외부에서 완료될 수 있어, 상태 완료/명시적 gate를 기다리도록 고쳤다. 고정 sleep을 추가하지 않았다.
- 미검증: 이 분리 worktree의 앱 DI·탐색 연결, 에뮬레이터 실제 화면/확대 글꼴/TalkBack/외부 링크 실행, 운영 서버·음원·이미지, iOS 컴파일과 실기기. 통합 브랜치에서 최종 확인한다.

통합할 등록은 `ReferenceCatalogRepository → ApiReferenceCatalogRepository`, `ReadingCatalogViewModel`, `FingeringViewModel(instrument, blow, InstrumentCatalog, ReferenceCatalogRepository)`이며, 기존 읽기 화면 목적지는 `ReadingRoute`를 호출한다. 라이브러리·탐색 경로 이름을 추가할 필요는 없다.

통합 브랜치에서 위 DI·탐색 연결을 완료했다. 실제 Spring HTTP로 음계11개·장단3개와 각 상세·빈 운지/기호를 확인했고, API37 에뮬레이터에서 등록된 율명·운지 자료 창의 11율명/미등록 운지 안내를 관찰했다. 최종 자동 검사 및 적용 소스는 [앱 통합 기록](app-catalog-api-integration.md)을 따른다. 운영 자료·외부 링크 미디어의 실제 품질·전체 화면 접근성 검증은 이 결과에 포함되지 않는다.

## 등록 장단 미리듣기 (S15P31E102-340)

2026-11-04. 정간보 쉽게 읽기에서 장단·기호 자료를 열고, 목록에서 장단을 선택해 한 주기 음원을 재생한다. CAT-08은 미리듣기 파일이 있는 등록 장단에 같은 API 원점의 재생 경로와 음원 출처를 반환한다. 앱은 응답의 상대 경로가 조회한 장단 ID와 정확히 일치할 때만 원점에 붙인다. 음원은 국립국악원 장구 단음으로 만든 MP3이며 API 패키지의 리소스로 제공한다.

재생을 한 번 더 누르면 로딩을 취소하거나 재생을 정지한다. 다른 장단 선택, 팝업 닫기, 화면이 백그라운드로 갈 때 플레이어를 정지한다. 음원이 없거나 재생이 실패하면 각각 안내한다. Android의 로컬 HTTP 재생은 앱이 디버그 빌드이며 기존 네트워크 설정에서 허용된 `127.0.0.1`일 때만 쓴다.

- 로컬 MySQL 8.4와 Spring HTTP에서 등록 장단 10종의 상세, MP3 범위 응답(206)과 파일 해시를 대조했다.
- 백엔드 `clean build databaseTest`는 단위 578건과 DB 222건이 통과했다. 외부 자산 설정이 필요한 DB 검사 1건은 건너뛰었다.
- Android 호스트 742건 통과, 외부 환경이 필요한 3건 생략. 로컬 HTTP 장단 연동 검사, 디버그 APK 빌드, Kotlin JS·Wasm 컴파일이 통과했다.
- Android API 37 에뮬레이터에서 로컬 테스트 계정으로 로그인해 10종 모두 실제 MediaPlayer의 시작·정지를 확인했다. 세령산은 재생 중 백그라운드 전환과 팝업 닫기에서도 미디어가 정지했다. 실기기 청음과 iOS·웹의 실제 재생 확인은 남아 있다.


## 악보 속도에 맞춘 장단 연습 (S15P31E102-340, 2026-11-04)

CAT-04의 optional `jangdanPlayback`은 검수된 악보 판본에만 붙는다. 계약은
`{jangdanId,name,startTick,cycleTicks,pattern:{beats,group}}`이다. 프론트는 기존
`songs.jangdan_id`나 곡 이름에서 연결을 추정하지 않는다. 이 메타데이터가 없으면
악보·모범 연주는 유지하고 장단 연습/미리듣기를 검수 중으로 표시한다. 잘못된 ID,
음수 tick, 빈 패턴, 그룹 길이 불일치, 원본 장단 이름 불일치는 거절한다.

한 구음 칸의 길이는 `cycleTicks / beats.size`, 한 tick의 시간은
`60000 / selectedBpm / ticksPerBeat`다. 악보 한 정간과 구음 한 칸을 같은 단위로
보지 않는다. 중모리의 `cycleTicks=48, BPM=88, ticksPerBeat=4`와
`24, 44, 4`는 동일한 한 주기 시간을 낸다.

- **장단으로 연습**은 녹음하지 않는 연속 재생이다. 악보와 타격 예약은 같은
  monotonic 절대 시계에서 위치를 읽는다. 일시정지, ±5초 이동, 처음부터 재생,
  기존 다섯 속도 변경 시 누적 `delay`로 타이밍을 이어 붙이지 않는다.
- 모범 반주는 기존 `MediaAnchor → ScorePerformance → source tick`을 읽는다.
  실제 음원 진행을 별도 BPM 타이머로 대체하지 않는다. 앞 여백과 끝 잔향 구간은
  타격을 만들지 않고, 연주 기록의 반복으로 source tick이 뒤로 가면 이전 잔향을
  끊고 새 위치에서 시작한다. 굴림 길이는 선택 BPM의 준비된 길이이며 미세한
  루바토마다 클립을 다시 늘이지 않는다. 다음 onset은 source tick을 따른다.
- `JangdanSession` 하나가 모범 반주·패널 미리듣기·장단 연습을 소유한다.
  로딩 성공 전에는 재생 중으로 표시하지 않는다. 취소된 로드의 늦은 완료는
  시작할 수 없다. 설정 OFF, 화면 이탈, 백그라운드, 계정 전환, ViewModel 정리에서
  예약과 잔향을 멈춘다. 평가 카운트다운·녹음·중단 상태는 장단을 잠근다.
- 타격 예약은 5ms 간격으로 현재 절대 tick을 읽고 30ms 이상 지난 타격을 버린다.
  UI 진행 표시 갱신은 기존 약 50ms 간격이다. 과거 타격을 한꺼번에 내지 않는다.
  이는 OS 오디오 출력 지연·Bluetooth 지연을 제거하거나 완벽한 동기를 보장한다는
  뜻은 아니다.

Android에서는 `SoundPool`에 필요한 파일을 먼저 적재하고 재생 rate를 항상 1로 둔다.
단타 6개와 실제 더러러러 녹음을 음높이 보존 시간 변환한 굴림 5개를 묶었다.
굿거리 기준 BPM 99의 기존 다섯 단계는 정수 반올림하여 59/79/99/109/119이며,
두 구음 칸의 길이에 정확히 대응하는 파일만 선택한다. 준비되지 않은 굴림 길이,
미지원 구음·장단·플랫폼은 명시적인 지원 불가 상태다. 임의로 가장 가까운 파일을
틀거나 단타 반복으로 굴림을 흉내 내지 않는다. 현재 웹·iOS는 장단 샘플 엔진이
없으므로 미지원 표시를 하며 모범 음원 정책을 바꾸지 않는다.

소리 출처는 국립국악원 국악 디지털 음원, 공공누리 제1유형이다. 패널에 제공기관,
이용 조건, 가공 사실을 표시하며 파일별 원본·추출 위치·SHA-256은
`frontend/shared/src/commonMain/composeResources/files/jangdan/manifest.json`, 이용 고지는
같은 폴더의 `ASSET_LICENSE.md`에 둔다. `gi`는 기덕 원본의 첫 꾸밈 타격을 분리했다.
최대 동시 스트림은 8, 타격별 출력 gain은 0.65 이하다. 믹스 음량·출력 지연의
실기기 청음은 별도 확인이 필요하다. 사용자 청음은 99/119 BPM 굴림 두 클립에 대해
크게 거슬리지 않는다는 의견을 받았으며, 다섯 속도 전체의 전문 연주 검수는 아니다.

검증은 순수 planner의 길이 선택·동일 시간 단위·늦은 타격 드롭, 세션의 로딩/취소/
출력 실패/반복 경계, Practice의 실제 media cursor·모드 상호배제·pause/seek/tempo/
화면 이탈·평가 녹음 잠금을 대역으로 검사한다. CAT-04 optional 계약은 Ktor
MockEngine으로 기존 FK가 없어도 검수 메타데이터를 보존하는지 확인한다.
별도 `SectionLoopViewModel`의 구간 반복 화면과 한 음씩 따라 불기의 자동 음 판정은
이 연속 장단 재생 변경에 포함하지 않는다. 등록 장단 10종의 카탈로그 한 주기
미리듣기 경로는 그대로 유지한다.

2026-11-04 첫 구현 검증: Android host 전체 793건 중 실패 0, 오류 0, 환경 의존 생략 4건.
Kotlin JS/Wasm 컴파일과 `:androidApp:assembleDebug`(로컬 API 127.0.0.1:18081)가 통과했다.
사용한 명령은 `:shared:testAndroidHostTest :shared:compileKotlinJs :shared:compileKotlinWasmJs :androidApp:assembleDebug --max-workers=2`다.
iOS 빌드/실기기와 Android 출력 지연·청음은 이 자동 검사 결과에 포함되지 않는다.

일시정지 직후 같은 타격 tick에서 재개하면 그 타격을 다시 내던 경계 오류를 추가
행동 검사 2건으로 재현한 뒤 수정했다(RED → GREEN). 재개는 이미 소비한 tick을
cursor에 전달하며, 재개 직후 decoder의 작은 후방 보고에도 재타격하지 않는다.
다음 타격은 누락하지 않고 명시적 seek/restart는 목표의 첫 타격을 허용한다.
곡 끝으로 이동하면 멈추고, 끝에서 재생을 누를 때 처음 타격으로 다시 시작한다.
후속 검증은 Android host 796건(실행 792, 환경 의존 생략 4), JS/Wasm 컴파일,
로컬 API용 debug APK 빌드가 모두 통과했다. `git diff --cached --check`도 통과했다.

런타임 확인 후 목록 보조 문구도 조정했다. CAT-01/02의 기존 장단 FK가 비어 있어도
CAT-04 검수 연결은 있을 수 있으므로, 제공 곡·검색 목록에서 빈 장단명을 ‘장단
미지정’으로 단정하지 않는다. 빈 이름이면 각각 평가 가능 여부/난이도만 표시한다.
목록 API나 조회 횟수는 바꾸지 않고, 상세는 기존 원본 악보 장단명 표시를 유지한다.
장단 패널 설명도 실제 정책에 맞춰 모범 연주·장단 연습에서만 소리를 내고 평가
녹음 중에는 소리가 꺼짐을 명시했다.
이 문구 수정 후 `PracticeJangdanTest`, `JangdanScoreContractTest` 10건과 Android
공통/앱 컴파일·debug APK 빌드가 통과했다. 기존 재생 구현의 전체 검증은 위 796건
결과를 따르며, 문구 변경만으로 전체 플랫폼 검사를 반복하지 않았다.
