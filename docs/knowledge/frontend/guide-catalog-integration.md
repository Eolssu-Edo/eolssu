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
