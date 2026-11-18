# 원격 Mac에서 iPhone 연결 없이 개발본 배포

S15P31E102-444, 2026-11-09. 목적은 develop의 최신 iOS 개발본을 원격 Mac에서 만들고, 다른 네트워크의 등록 iPhone에서 Safari로 설치하는 것이다.

## 선택과 제약

- 기존 무료 Personal Team 빌드는 Apple 로그인 권한을 포함하지 못해 실패했고, Safari OTA 설치도 거부돼 Mac의 devicectl Wi-Fi 설치에 의존했다.
- 사용자 결정으로 유료 팀 Ad Hoc 서명과 기존 HTTPS `/ipas/` 게시를 사용한다. TestFlight 업로드·심사 제출은 수행하지 않는다.
- Ad Hoc 프로파일의 등록 기기에만 설치된다. IPA는 Release archive에서 `release-testing`으로 export한다. Xcode `-help`에서 이 명칭이 구 `ad-hoc`을 대체함을 확인했다.
- 최초 서명 때 macOS 키체인 접근 승인이 필요했다. 사용자가 직접 암호를 입력했다. 에이전트가 암호를 수집·저장하지 않는다.
- Mac은 로그인·네트워크 연결이 가능해야 한다. 빌드 중 idle sleep만 caffeinate로 막고 전역 전원 설정은 바꾸지 않는다.
- 2026-11-13~14 `eolssu-ios-qa` #196~#205가 연속 실패했다(S15P31E102-908). Mac이 배터리 전원(`sleep 1`)에서 잠자기·DarkWake를 반복하는 동안 잠깐 붙은 에이전트가 빌드를 받았다가 끊겼다. 서버 sshd·WebSocket idle 60초는 Mac이 응답하지 않을 때 드러나는 결과일 뿐 원인이 아니었다. 끊긴 11-13 빌드의 `build-ipa.sh`는 31시간 동안 잠자기 사이사이 진행하며 같은 workspace를 썼다. 전원 연결을 운영 조건으로 두고, Mac 단계의 에이전트 끊김 재시도와 시작 시 남은 프로세스 정리를 넣었다. 전역 전원 설정은 여전히 바꾸지 않는다.
- 2026-11-10 Mac의 Xcode 계정 정보가 사라지자 export가 `No Accounts`로 실패했다. 로컬에 배포 인증서가 없고 Xcode 계정을 통한 클라우드 서명에 의존했기 때문이다. S15P31E102-757에서 App Store Connect API 키 인증으로 바꿔 Xcode 로그인 상태와 무관하게 했다.

## 구성과 검사

- 기존 mac-ios 노드를 보존하고, exclusive `ios-eunseo` 라벨의 `mac-ios-eunseo` 노드를 추가했다. backend·Android 잡은 변경하지 않는다.
- 기존 backend 성공 후 iOS 입력 diff 감지·동일 SHA 예약 흐름을 유지한다. 따라서 backend 실패 시 iOS 잡도 예약되지 않는다.
- 새 iOS 잡은 Mac 아카이브·Ad Hoc export → stash → edo1 게시로 구성한다. 등록 iPhone 연결 여부를 확인하거나 설치 명령을 실행하지 않는다.
- Mac에서 실제 export 프로파일의 팀·Bundle ID·기기 포함·Apple 로그인 권한·만료를 검사한다. 게시기에서도 무료 개발 프로파일·App Store 전용 프로파일·다른 팀을 거부한다.
- Manifest의 bundle-version은 마케팅 버전 대신 실제 CFBundleVersion을 사용한다.
- 기존 공개 HTTPS 경로와 최근 5개 보관 정책을 유지한다. 추가 공개 경로나 인증 권한을 만들지 않는다.
- 단위 테스트는 부적합 프로파일 거부, immutable 재게시, manifest 버전을 확인한다. 실제 기기의 설치·로그인은 별도 검증이다.

운영·복구: [iOS QA README](../../../infra/ios-qa/README.md). 실행 결과: [444 계획](../../plans/S15P31E102-444.md).

## 근거

- [Apple: 등록 기기 배포](https://developer.apple.com/documentation/xcode/distributing-your-app-to-registered-devices)
- [Apple: Ad Hoc 프로파일](https://developer.apple.com/help/account/provisioning-profiles/create-an-ad-hoc-provisioning-profile/)
- [Apple: 배포 및 OTA manifest 옵션](https://developer.apple.com/documentation/xcode/distributing-your-app-for-beta-testing-and-releases)
