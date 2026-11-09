# iOS Ad Hoc 자동 배포 — S15P31E102-444

`develop`의 iOS 빌드 입력 변경 → 기존 backend 잡 성공 → `eolssu-ios-qa` → 은서 Mac(`ios-eunseo`) → Ad Hoc IPA → https://k15e102.p.ssafy.io/ipas/ . iPhone은 Safari에서 설치한다. Mac과 같은 네트워크나 USB 연결은 필요 없다.

- 유료 팀 `WDA26H33JA`, Bundle ID `com.ssafy.eolssu`, Sign in with Apple 유지.
- 등록 iPhone이 포함된 Ad Hoc 배포 프로파일과 배포 인증서가 필요하다. `release-testing`은 현재 Xcode의 Ad Hoc export 명칭이다.
- `build-ipa.sh`와 `verify-ipa.py`는 Mac의 `~/jenkins-agent/`에서 관리한다. `publish-ipa.py`는 edo1 `/opt/eolssu/config/`에서 관리한다. 변경된 저장소 코드가 과거 develop 재빌드를 막지 않도록 파이프라인은 관리 복사본을 사용한다.
- 게시된 IPA는 등록 기기에서만 설치된다. 기존 HTTPS 공개 경로와 최근 5개 보관 정책을 유지한다. TestFlight·앱스토어 심사 제출은 실행하지 않는다.
- `publish-ipa.py`는 CMS 내부 메타데이터를 검사한다. 암호학적 서명 검증은 Xcode export 및 기기의 iOS 검증이 담당한다.

## Mac 에이전트

전용 노드 `mac-ios-eunseo`, executor 1개, exclusive label `ios-eunseo`, remoteFS `/Users/o-eunseo/jenkins-agent`. 기존 `mac-ios` 노드는 복구용으로 보존한다.

사용자 LaunchAgent 두 개가 로그인 시 실행된다.

- `~/Library/LaunchAgents/com.eolssu.jenkins-tunnel.plist`: SSH로 서버 loopback 8080을 Mac loopback 18089에 전달.
- `~/Library/LaunchAgents/com.eolssu.jenkins-agent.plist`: Java 21 + WebSocket inbound agent.
- secret은 `~/jenkins-agent/secret`, 0600. 서버 관리자 인증·개인키·secret은 저장소에 넣지 않는다.
- Mac 사용자 로그인, 잠금 해제된 서명 키체인, 인터넷 연결이 필요하다. 잠자기·종료·로그아웃 중에는 빌드할 수 없다.

상태 확인: `launchctl print gui/$(id -u)/com.eolssu.jenkins-agent`, `~/jenkins-agent/com.eolssu.jenkins-agent.err.log`. secret 내용은 출력하지 않는다.

## 수동 재빌드

기존 Jenkins `eolssu-ios-qa`에서 `REVISION`에 develop에 속한 전체 40자 SHA를 지정한다. 백엔드 전체 배포를 다시 실행할 필요 없다. 결과는 `/ipas/`에 표시된다. 설치 후 앱의 로그인·평가 동작은 실제 iPhone에서 따로 확인한다.

## 검사

```sh
bash -n infra/ios-qa/build-ipa.sh
python3 -B -m unittest discover -s infra/ios-qa -p 'test_*.py' -v
```

운영 Jenkins의 `declarative-linter`로 `ios.Jenkinsfile`을 검사한 뒤 적용한다. 잡 원본 XML·관리 스크립트를 서버의 root 전용 백업에 저장하고, 실행 중인 iOS 빌드가 없을 때 전환한다. backend 잡의 변경 감지·Android 예약·다른 잡은 수정하지 않는다.

복구는 저장한 iOS 잡 XML과 게시 도구를 복원하는 방식이다. 기존 Mac 노드·기존 IPA는 삭제하지 않는다. 이전 잡은 무료 팀 Apple 로그인 서명 오류가 있으므로 설정 복원만으로 성공을 보장하지 않는다.
