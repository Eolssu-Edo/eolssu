# iOS App Store 출시 잡의 서명·업로드 방식 (S15P31E102-1256)

- 상태: 잡 등록 완료, 첫 실제 빌드 전. 결정·구현은 [S15P31E102-1256](https://ssafy.atlassian.net/browse/S15P31E102-1256), 실행 절차는 [Jenkins 컨트롤러 README](../../../infra/jenkins/controller/README.md#ios-출시-잡)에 있다.
- 기록일: 2026-11-23

## 선택

`eolssu-ios-release` 잡은 은서 Mac에서 `xcodebuild archive`와 `-exportArchive`(`method=app-store-connect`)로 IPA를 만든다. 서명은 App Store Connect 팀 API 키로 Apple 클라우드 서명을 쓰고, 키는 Jenkins Credentials에서 `withCredentials`로 받는다. 업로드는 같은 아카이브를 `destination=upload`로 한 번 더 내보내는 방식이다.

## 근거

2026-11-23에 확인한 사실이다.

| 항목 | 확인 결과 | 판단 |
| --- | --- | --- |
| 빌드 에이전트 | Xcode가 있는 노드는 `mac-ios-eunseo`뿐이다. `mac-ios`(복구용)는 온라인이지만 `xcodebuild`가 Command Line Tools만 가리킨다. | `ios-eunseo` label을 쓴다. |
| 기존 서명 방식 | `eolssu-ios-qa`는 Mac `~/jenkins-agent/asc/`의 Admin 팀 키로 클라우드 서명한다(S15P31E102-757). 배포 인증서는 키체인에 없다. | 같은 클라우드 서명을 쓴다. 인증서·프로파일 Credentials는 따로 두지 않는다. |
| 키 위치 | Jenkins에는 Apple 관련 Credentials가 없었다. | 사용자가 Jenkins Credentials 등록을 골랐다. 키 교체·폐기를 Jenkins에서 관리하고, QA 잡의 Mac 폴더와 분리한다. |
| 매개변수 이름 | 요청한 `BUILD_NUMBER`는 Jenkins가 실행 번호로 쓰는 환경 변수와 같다. | xcconfig 키 이름인 `CURRENT_PROJECT_VERSION`을 쓴다. `MARKETING_VERSION`과 짝이 맞는다. |
| 버전 주입 | 앱 타깃이 `GENERATE_INFOPLIST_FILE=YES`라 버전이 빌드 설정에서 온다. | `xcodebuild`에 `MARKETING_VERSION`·`CURRENT_PROJECT_VERSION`을 넘겨 xcconfig 값을 덮는다. |
| 업로드 도구 | `altool`은 키를 정해진 폴더에서만 찾는다. `xcodebuild -exportArchive`는 키 경로를 인자로 받는다. | 빌드와 같은 인증 인자로 `destination=upload`를 쓴다. Mac에 키 파일을 따로 두지 않아도 된다. |

## 잡에서 정한 것

- IPA를 보관하기 전에 Bundle ID, 버전, 빌드 번호, 팀, 프로파일에 기기 목록이 없는지, `get-task-allow`가 false인지 검사한다. Ad Hoc나 개발 프로파일로 잘못 서명된 IPA를 걸러 낸다.
- 업로드는 기본으로 끄고, 별도 단계로 둔다. 빌드 단계는 Mac 연결 끊김(S15P31E102-908) 때문에 최대 2회 재시도하지만, 업로드는 같은 번호를 다시 올릴 수 없어 재시도하지 않는다.
- derived data는 QA 잡과 다른 `~/jenkins-agent/ios-release-derived`를 쓴다. 끊긴 빌드가 남긴 `xcodebuild`는 이 경로나 `release-out/`을 쓰는 것만 종료한다.
- 심사 제출, TestFlight 그룹 배포, 스토어 메타데이터는 다루지 않는다. App Store Connect에서 사람이 한다.

## 검증

- 2026-11-23 Jenkins `declarative-linter`를 통과했다. 등록 스크립트 check·apply로 잡을 만들고 매개변수 4개(`UPLOAD_TO_APP_STORE_CONNECT` 기본 false)와 sandbox=true를 확인했다.
- 셸 단계는 `bash -n`, IPA 검사 Python은 컴파일 검사만 했다.
- 아직 실제 빌드는 실행하지 않았다. 첫 실행에서 Admin 키의 클라우드 서명이 App Store 배포 인증서를 만들 수 있는지 확인해야 한다.
