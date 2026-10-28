# 프론트엔드 협업 규칙

- 작업: `S15P31E102-59`
- 구조와 배치 기준은 [architecture.md](architecture.md), 실행 지침은 [AGENTS.md](../AGENTS.md), Jira·브랜치·커밋·MR 규칙은 [루트 AGENTS.md](../../AGENTS.md)를 따른다.

프론트엔드 개발자는 2명이고 둘 다 Kotlin Multiplatform 경험이 적다. 이 문서는 두 사람이 같은 구조 안에서 충돌 없이 나눠 일하기 위한 규칙이다.

## 1. 담당은 기능 단위로 나눈다

- 담당 단위는 `feature/<기능>`이다. 파일·화면·레이어(화면 담당, API 담당)로 나누지 않는다.
- 기능 담당자는 그 기능의 **화면(`feature/<기능>`), 상태(ViewModel·UiState), 그 기능이 쓰는 데이터 패키지**를 함께 관리한다. 곡 목록 담당이면 `feature/song`과 `data/song`을 같이 맡는다.
- feature와 data는 1:1이 아니다. 기능마다 같은 이름의 data 폴더를 만들지 말고 아래 대응을 따른다.

| feature | 사용하는 data | 비고 |
| --- | --- | --- |
| `auth`, `profile` | `data/user` | 인증·프로필 API |
| `song` | `data/song` | 곡 목록·상세 API |
| `practice`, `result`, `history` | `data/practice` | 연주 제출·평가 결과·연습 기록 API |

- `data/practice`·`data/user`처럼 여러 기능이 함께 쓰는 데이터 패키지는 먼저 만드는 사람이 주 담당이 되고, 다른 기능이 필요한 API를 추가할 때는 주 담당에게 알린 뒤 추가한다.
- 기능 안에서 끝나는 변경(그 기능의 화면·상태·DTO·Repository)은 담당자가 혼자 결정한다. 아래 2절의 공동 파일을 건드리면 조율한다.

현재 기능 목록: `home`, `guide`, `auth`, `song`, `practice`, `result`, `history`, `profile`. 담당 배정은 Jira Task로 관리하고 이 문서에는 적지 않는다.

## 2. 공동 파일은 수정 전에 조율한다

다음은 두 사람 모두에게 영향을 주는 파일이다. 수정하기 전에 상대에게 무엇을 왜 바꾸는지 알리고, 같은 시점에 둘이 동시에 고치지 않는다.

| 공동 파일·영역 | 이유 |
| --- | --- |
| `app/navigation/` | 모든 화면의 이동 경로가 모인다. 경로 이름·인자가 바뀌면 상대 기능이 깨진다 |
| `app/di/` | 객체 생성·연결이 모인다 |
| `core/theme/` | 모든 화면의 색·글꼴이 바뀐다 |
| `core/ui/component/`, `core/ui/score/`, `core/ui/adaptive/` | 여러 기능이 쓴다. 시그니처 변경은 호출처를 모두 고쳐야 한다 |
| `core/model/` | 공유 모델 변경은 `data`·`feature` 양쪽에 영향 |
| `core/network/`, `core/audio/`, `core/storage/` | 공통 인터페이스 변경은 플랫폼 구현도 함께 바꿔야 한다 |
| `shared/build.gradle.kts`, `gradle/libs.versions.toml`, `settings.gradle.kts` | 빌드 전체에 영향. 충돌 시 해결이 어렵다 |
| `App.kt`, 각 플랫폼 진입점 | 앱 시작 흐름 |
| `AGENTS.md`, `docs/architecture.md`, 이 문서 | 규칙 자체 |

조율 방법: 변경 전에 팀 채널에 한 줄(무엇을, 왜, 영향받는 기능)을 올린다. 분리 여부는 루트 `AGENTS.md`의 Task 단위 기준을 따른다.

- 기능 완성에 **필요한** 공동 파일 변경(새 화면의 `navigation` 등록, Repository·플랫폼 구현을 `di`에 연결, 그 기능이 쓸 공통 컴포넌트 추가)은 같은 Task·브랜치에 포함한다. 대신 MR 설명에 어떤 공동 파일을 왜 바꿨는지 적는다.
- 기능과 무관하게 **독립적으로 완료·검토할 수 있는** 공동 파일 변경(테마 전면 교체, 공통 컴포넌트 시그니처 정리, Gradle·라이브러리 변경)은 별도 Task·브랜치로 먼저 올려 빨리 합친다.

## 3. 공통 컴포넌트는 두 번째 사용처가 생길 때 뽑는다

- 처음 만드는 UI는 그 기능의 `feature/<기능>/component/`에 둔다. 나중에 쓰일 것 같다는 이유로 `core/ui/component`에 먼저 넣지 않는다.
- 다른 기능에서 같은 UI가 필요해지면 그때 `core/ui/component`로 옮긴다. 옮기는 사람이 기존 호출처도 함께 고치고, 양쪽 담당자가 리뷰한다.
- 옮길 때 기준: 두 기능 이상에서 쓰이고, 기능 전용 상태(특정 UiState)를 받지 않으며, 외관·동작이 같다. 하나라도 어긋나면 각 기능에 따로 둔다.
- 정간보 렌더러는 연습·결과 화면이 함께 쓰는 것이 확실하므로 처음부터 `core/ui/score`에 만든다.

## 4. 새 라이브러리와 구조 변경 절차

새 라이브러리 도입:

1. 필요한 이유와 대안(직접 구현, 기존 라이브러리)을 한 줄로 정리한다.
2. [klibs.io](https://klibs.io) 등에서 Android·iOS·JS·Wasm 지원 여부와 현재 Kotlin 2.4.20·Compose 1.12.1과의 호환 버전을 확인한다. 한 타깃이라도 지원하지 않으면 `commonMain`에 넣을 수 없다.
3. 두 사람이 합의하면 `gradle/libs.versions.toml`에 등록하고 `shared/build.gradle.kts`의 해당 소스 세트에 추가한다.
4. 도입 결정과 근거를 `docs/decisions/`에 결정 문서로 남긴다(루트 [docs/README.md](../../docs/README.md) 기준). 화면 이동·DI·HTTP 라이브러리처럼 구조에 영향을 주는 선택은 반드시 남긴다.

구조 변경(최상위 패키지·소스 세트·Gradle 모듈 추가, 이름 변경, 분리, `domain` 계층 도입):

1. 왜 현재 구조로 안 되는지 사례를 들어 제안한다.
2. 두 사람이 합의하고, 다른 파트에 영향이 있으면 팀에도 알린다.
3. 변경과 같은 MR에서 `AGENTS.md`와 `docs/architecture.md`를 고친다. 문서를 나중에 따로 고치지 않는다.
4. 구조 변경이 현재 기능과 독립적으로 완료·검토될 수 있으면 별도 Task·브랜치로 다룬다. 기능 완료에 필수인 구조 변경(예: 연습 기능 구현에 필요한 `core/audio` 인터페이스 신설)은 같은 Task에 포함하되, 1~3의 합의와 문서 수정은 똑같이 한다.

## 5. 브랜치·작업 흐름에서 지킬 것

- 브랜치·커밋·MR 형식은 루트 `AGENTS.md`를 따른다. 기능 하나의 작업은 하나의 Task·브랜치에서 하고, 그 기능 완성에 필요한 공동 파일 변경도 같은 브랜치에 둔다. 공동 파일 변경을 분리하는 기준은 2절을 따른다.
- `develop`을 자주 받아 합친다. 상대의 공동 파일 변경을 오래 묵히면 충돌이 커진다.
- iOS는 Windows에서 컴파일할 수 없다. Mac을 쓰는 사람이 `develop`을 주기적으로 받아 iOS 빌드를 확인하고, 깨지면 바로 알린다. `commonMain`만 바꿔도 iOS가 깨질 수 있다.
- 웹 빌드로 생기는 `kotlin-js-store/` lock 파일은 커밋한다.

## 6. PR(MR)에서 확인할 항목

리뷰어는 다음만 빠르게 확인한다. 세부 코드 스타일보다 구조 위반을 먼저 본다.

- [ ] 새 파일이 `app / feature / data / core / platform` 중 역할에 맞는 위치에 있다. 새 최상위 패키지가 생기지 않았다.
- [ ] import 방향이 `app → feature → data → core`를 거스르지 않는다. `core`가 `feature`·`data`를 import하지 않는다.
- [ ] feature가 다른 feature의 내부(ViewModel·component)를 import하지 않는다. 기능 사이 이동은 `app/navigation`에 있다.
- [ ] `commonMain`에 Android·Apple 전용 import가 없다. 플랫폼 구현은 `androidMain`·`iosMain`의 `platform/`에 있다.
- [ ] 화면은 `Route / Screen / ViewModel / UiState` 이름 규칙을 따르고, Screen은 ViewModel 대신 상태·콜백을 받는다.
- [ ] 한 기능만 쓰는 UI가 `core/ui/component`에 들어가지 않았다.
- [ ] 공동 파일(2절)을 바꿨다면 상대가 알고 있고, 변경 이유가 MR 설명에 있다.
- [ ] 라이브러리를 추가했다면 네 타깃 지원 확인과 합의 기록이 있다.
- [ ] 빈 클래스·빈 ViewModel·동작하지 않는 Repository가 추가되지 않았다.
- [ ] Android 테스트(`:shared:testAndroidHostTest`)와 필요한 빌드를 실제로 실행한 결과가 MR에 있다. iOS 미검증이면 그렇게 적혀 있다.
- [ ] 구조를 바꿨다면 `AGENTS.md`·`docs/architecture.md`가 같은 MR에서 갱신됐다.
