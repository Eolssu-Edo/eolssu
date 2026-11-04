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
