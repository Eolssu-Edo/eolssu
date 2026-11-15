# 릴리스 버전 단일화 방안

- 작업: [S15P31E102-916](https://ssafy.atlassian.net/browse/S15P31E102-916), Story S15P31E102-40 / Epic S15P31E102-21
- 저장소 기준: `aa8b2294c67d1093ad938e89928999adcb1a84b7` (2026-11-15 origin/develop)
- 상태: **제안**. 팀 결정 전이며 버전 값·빌드 설정을 바꾸지 않았다. 실행 절차는 [릴리스 절차](../../../infra/release/README.md)를 따른다.

## 현재 표기

| 구성 | 값 | 위치 | 실제 식별 수단 |
| --- | --- | --- | --- |
| backend | `0.0.1-SNAPSHOT` | `backend/build.gradle.kts` | 배포 SHA: `/opt/eolssu/deployment.json`, `releases/<SHA>` |
| web | 없음 | — | `/__eolssu_release.json`의 SHA·manifest 해시 |
| ai·omr·playback | ai `0.1.0`(pyproject), 나머지 없음 | `ai/pyproject.toml` | 이미지 라벨 `org.opencontainers.image.revision` |
| Android | `1.0.0` / code `2`(기본값, `-Peolssu.versionName`·`versionCode`로 덮어씀) | `frontend/androidApp/build.gradle.kts` | QA APK는 `infra/qa-phone` 카탈로그의 commit·sha256 |
| iOS | `1.0.1` / build `5` | `frontend/iosApp/Configuration/Config.xcconfig` | App Store 1.0.1 업데이트(S15P31E102-814) |

서버 구성 요소는 이미 **Git SHA로 정확히 식별**된다. 서로 다른 것은 사용자에게 보이는 앱 버전(Android 1.0.0, iOS 1.0.1)과, 이것들을 한 이름으로 묶을 릴리스 식별자가 없다는 점이다. backend의 SNAPSHOT 표기는 JAR 파일 이름에만 쓰이고 배포 도구는 SHA로 판단한다.

## 선택지

| 안 | 내용 | 장점 | 비용·위험 |
| --- | --- | --- | --- |
| A. Git 태그 기준(권장) | RC SHA에 `vX.Y.Z` 주석 태그. 릴리스 노트에 구성별 SHA·앱 버전을 묶어 기록. 앱 표시 버전만 X.Y.Z로 맞춤 | 코드 변경이 거의 없음. 기존 SHA 기반 배포·검사와 충돌하지 않음 | 서버 구성 요소의 자체 버전 문자열은 그대로 |
| B. 단일 버전 파일 | 루트 `VERSION`을 Gradle·xcconfig·pyproject가 읽도록 변경 | 한 곳만 바꾸면 됨 | 세 빌드 체계를 모두 고쳐야 하고 iOS xcconfig 연동과 스토어 규칙 검증이 필요. 발표 전 변경 위험이 큼 |
| C. 현 상태 유지 | 태그만 만들고 앱 버전 불일치를 노트에 남김 | 비용 없음 | 발표 자료·스토어 버전과 저장소 표기가 엇갈림 |

## 권장(A)의 적용 방법

1. 릴리스 이름을 팀이 정한다. iOS는 이미 1.0.1이 게시돼 있어 다음 제출은 그보다 커야 한다(예: `v1.1.0`). Android도 같은 값으로 맞춘다.
2. iOS: `Config.xcconfig`의 `MARKETING_VERSION`을 릴리스 값으로, `CURRENT_PROJECT_VERSION`을 이전보다 크게 바꾸는 커밋을 RC 전에 병합한다.
3. Android: 기본값을 릴리스 값으로 바꾸거나, 릴리스 빌드에서 `-Peolssu.versionName=<값> -Peolssu.versionCode=<이전보다 큰 정수>`를 넘긴다. QA APK 빌드(`infra/qa-phone/build-apk.sh`)는 이미 이 속성을 쓴다.
4. 서버 구성 요소는 SHA를 유지하고 `release-candidate.py notes --deployed ...`로 RC와의 일치를 기록한다.
5. `release-candidate.py notes --expect-version <값>`이 경고 없이 끝나는지 확인한다.

backend에 `/api/actuator/info`로 빌드 SHA를 노출하는 방안도 있으나, 현재 actuator 공개 범위가 `health, prometheus`로 제한돼 있고 보안 노출 범위를 바꾸는 일이라 이번 범위에서 제외했다.

## 미확인

- Play Console에 게시된 Android versionCode, App Store Connect의 최신 빌드 번호는 저장소에서 확인할 수 없다. 담당자(S15P31E102-814 등)에게 확인해야 한다.
- GitLab main 보호 설정(직접 Push 허용 여부, fast-forward MR 가능 여부)을 확인하지 않았다.
