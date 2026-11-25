# Android 출시 잡의 Play 업로드 방식 (S15P31E102-1254)

- 상태: 적용 중. 결정·구현은 [S15P31E102-1254](https://ssafy.atlassian.net/browse/S15P31E102-1254), 실행 절차는 [Jenkins 컨트롤러 README](../../../infra/jenkins/controller/README.md#android-출시-잡)에 있다.
- 기록일: 2026-11-23

## 선택

`eolssu-android-release` 잡은 저장소의 [play-upload.py](../../../infra/jenkins/play-upload.py)로 Play Developer API(androidpublisher v3 edits)를 직접 호출한다. Python 표준 라이브러리와 `openssl` CLI만 쓴다. 서비스 계정 토큰 요청(JWT, RS256)은 `openssl dgst -sha256 -sign`으로 서명한다.

## 근거

2026-11-23에 edo1에서 확인한 사실이다.

| 후보 | 확인 결과 | 판단 |
| --- | --- | --- |
| Jenkins Google Play Android Publisher 플러그인 | 설치되어 있지 않음 | 운영 Jenkins에 새 플러그인을 넣어야 해서 제외 |
| Gradle Play Publisher | 앱은 AGP 9.1.1. 앱 빌드 스크립트에 출시 전용 플러그인과 의존성이 추가됨 | 앱 빌드에 영향을 주고 AGP 9 호환을 따로 확인해야 해서 제외 |
| `google-api-python-client` | edo1 `python3` 3.12.3에 없음, `pip3`도 없음 | 서버에 패키지 설치가 필요해서 제외 |
| 표준 라이브러리 + `openssl` | `python3` 3.12.3, OpenSSL 3.0.13 있음 | 설치 없이 동작하고 HTTP 대역으로 단위 테스트할 수 있어 선택 |

## Play API 동작에서 정한 것

- 번들은 단일 청크 resumable 업로드로 올린다. AAB는 수십 MB라 simple 업로드보다 안전하다.
- 업로드 응답의 versionCode·sha256을 로컬 파일과 대조한 뒤에 트랙을 바꾼다.
- 트랙 갱신에는 새 출시 하나만 보낸다. 요청의 `releases`는 바꿀 출시만 뜻하므로 기존 프로덕션 출시는 남는다.
- 커밋 전 실패에서는 edit을 지운다. 커밋하지 않은 edit은 Play에 아무것도 남기지 않는다.
- 커밋은 기본값(검토 자동 전송)으로 먼저 시도한다. Play Console에 검토로 보내지 않은 다른 변경이 있으면 Play가 `changesNotSentForReview`를 요구하며 거부한다. 이 경우에만 그 값을 `true`로 다시 커밋한다. 처음부터 `true`를 쓰면 자동 전송 상태의 앱에서 Play가 거꾸로 거부하고, 콘솔에 쌓인 다른 사람의 변경이 의도치 않게 검토로 넘어가는 일도 기본값의 거부로 막을 수 있다.
- 출시 이름은 Play Console 기본 형식인 `<versionCode> (<versionName>)`을 쓴다.
- 커밋 뒤 로그에는 트랙·상태별로 사람이 Play Console에서 할 일을 남긴다. internal 초안은 출시를 시작해야 테스터에게 배포되고, production 초안은 검토 전송이 필요하다.
- Play Console의 API 액세스(Google Cloud 프로젝트 연결) 화면은 더 이상 열리지 않는다. 서비스 계정을 사용자 및 권한에 초대하는 것만으로 API를 쓸 수 있다.

## 검증

- 2026-11-23 단위 테스트(로컬 Python 3.11, edo1 Python 3.12.3·OpenSSL 3.0.13)와 Jenkins `declarative-linter`를 통과했다.
- 같은 날 `eolssu-android-release` #2를 실행해 실제 업로드를 확인했다.
    - 입력: develop `17c7d6b66`, `VERSION_CODE` 11, `VERSION_NAME` 1.3.0, `PLAY_TRACK=internal`, `PLAY_STATUS=draft`, ko-KR 출시 노트 한 줄
    - 접근 검사: 서비스 계정으로 edit을 열고 트랙을 읽었다(production completed vc10, alpha completed vc8, internal 없음). 최고 versionCode 10보다 커서 진행했다.
    - 업로드: Play가 읽은 번들이 versionCode 11, sha256 `f39636380edc678ea6d8471667eee7460915b2251e0184dc0568b56c464a2683`으로 로컬 파일과 같았다. internal 트랙에 `11 (1.3.0)`을 draft로 설정하고 커밋했다. 결과는 SUCCESS였다.
    - Play Console 내부 테스트 화면에 "임시 버전: 11 (1.3.0)"이 생겼고, production의 vc10 출시는 바뀌지 않았다.
- 이 실행으로 vc11은 사용된 번호가 되었다. 다음 업로드는 vc12 이상이어야 한다.

## 미확인

- `PLAY_TRACK=production`과 `PLAY_STATUS=completed`(검토 자동 전송) 조합은 실제로 실행하지 않았다.
- `changesNotSentForReview`를 요구받아 다시 커밋하는 경로는 단위 테스트로만 확인했다.
