# iOS Ad Hoc 자동 배포 — S15P31E102-444

`develop`의 iOS 빌드 입력 변경 → 기존 backend 잡 성공 → `eolssu-ios-qa` → 은서 Mac(`ios-eunseo`) → Ad Hoc IPA → https://k15e102.p.ssafy.io/ipas/ . iPhone은 Safari에서 설치한다. Mac과 같은 네트워크나 USB 연결은 필요 없다.

- 유료 팀 `WDA26H33JA`, Bundle ID `com.ssafy.eolssu`, Sign in with Apple 유지.
- 등록 iPhone이 포함된 Ad Hoc 배포 프로파일이 필요하다. 배포 인증서는 아래 API 키 인증으로 클라우드 서명한다. `release-testing`은 현재 Xcode의 Ad Hoc export 명칭이다.
- `build-ipa.sh`와 `verify-ipa.py`는 Mac의 `~/jenkins-agent/`에서 관리한다. `publish-ipa.py`는 edo1 `/opt/eolssu/config/`에서 관리한다. 변경된 저장소 코드가 과거 develop 재빌드를 막지 않도록 파이프라인은 관리 복사본을 사용한다.
- 게시된 IPA는 등록 기기에서만 설치된다. 기존 HTTPS 공개 경로와 최근 5개 보관 정책을 유지한다. TestFlight·앱스토어 심사 제출은 실행하지 않는다.
- `publish-ipa.py`는 CMS 내부 메타데이터를 검사한다. 암호학적 서명 검증은 Xcode export 및 기기의 iOS 검증이 담당한다.

## Mac 에이전트

전용 노드 `mac-ios-eunseo`, executor 1개, exclusive label `ios-eunseo`, remoteFS `/Users/o-eunseo/jenkins-agent`. 기존 `mac-ios` 노드는 복구용으로 보존한다.

사용자 LaunchAgent 두 개가 로그인 시 실행된다.

- `~/Library/LaunchAgents/com.eolssu.jenkins-tunnel.plist`: SSH로 서버 loopback 8080을 Mac loopback 18089에 전달.
- `~/Library/LaunchAgents/com.eolssu.jenkins-agent.plist`: Java 21 + WebSocket inbound agent.
- secret은 `~/jenkins-agent/secret`, 0600. 서버 관리자 인증·개인키·secret은 저장소에 넣지 않는다.
- Mac 사용자 로그인, 인터넷 연결, `~/jenkins-agent/asc/`의 API 키가 필요하다. 잠자기·종료·로그아웃 중에는 빌드할 수 없다.
- 전원 어댑터에 연결해 둔다(S15P31E102-908). 이 Mac은 AC 전원에서 `sleep 0`, 배터리에서 `sleep 1`이다. 배터리로 두면 15분 남짓마다 잠들고 DarkWake로 잠깐 깬다. 그 사이 에이전트가 다시 붙어 빌드를 받았다가, Mac이 다시 잠들면 서버에 `ChannelPinger … Ping failed`가 남고 60초 뒤 Jetty `Idle timeout expired`, 이어서 `ClosedChannelException` 또는 `is offline`·45분 `Timeout has been exceeded`로 실패한다. 덮개를 닫으면 AC 전원에서도 외부 디스플레이 없이는 잠든다. `build-ipa.sh`의 `caffeinate -i`는 유휴 잠자기만 막는다.
- Mac 단계는 에이전트 끊김으로 실패하면 재연결 뒤 최대 2회 실행한다(`retry(conditions: [agent(), nonresumable()])`).
- Mac executor를 15분(`MAC_WAIT_MINUTES`) 안에 받지 못하면 `Mac agent (label ios-eunseo) did not become available within 15 min`으로 FAILURE가 된다. 45분 빌드 제한은 executor를 받은 뒤부터 센다. 재시도에서도 대기는 다시 15분으로 제한된다. 전체 4시간 제한은 안전장치다(S15P31E102-1258).
- `disableConcurrentBuilds(abortPrevious: true)`: 새 빌드가 큐에 들어오면 실행·대기 중인 이전 빌드를 중단한다. QA에는 최신 develop만 필요하므로, Mac이 끊긴 동안 쌓인 옛 커밋을 복구 뒤 차례로 빌드하지 않는다. 특정 SHA를 수동으로 재빌드하는 중에 backend가 새 커밋을 넘기면 그 수동 빌드도 중단된다.
- 끊긴 빌드의 `build-ipa.sh`·`xcodebuild`는 Mac에 남아 같은 workspace를 계속 쓴다. executor가 1개이므로 `build-ipa.sh`는 시작할 때 다른 `build-ipa.sh`와 이 derived data·`build/ipa`를 쓰는 `xcodebuild`를 종료한다. 이 Mac에서 같은 경로를 쓰는 수동 빌드를 따로 돌리지 않는다.

상태 확인: `launchctl print gui/$(id -u)/com.eolssu.jenkins-agent`, `~/jenkins-agent/com.eolssu.jenkins-agent.err.log`. secret 내용은 출력하지 않는다.

끊김 확인: Mac의 `pmset -g batt`(전원), `pmset -g log | grep -E ' (Sleep|Wake|DarkWake) '`(잠자기 시각)와 서버 `docker logs eolssu-jenkins-controller-1`의 `ChannelPinger`·`WebSocketAgents` 시각(UTC)을 대조한다. 남은 빌드 프로세스는 `pgrep -fl 'build-ipa|xcodebuild'`로 본다.

## 서명: App Store Connect API 키 (S15P31E102-757)

`build-ipa.sh`는 archive·export 모두 App Store Connect API 키로 인증한다. Mac의 Xcode 계정 로그인이 풀려도 빌드가 멈추지 않게 하기 위해서다(2026-11-10 `exportArchive No Accounts`로 연속 실패). 배포 인증서는 로컬 키체인에 두지 않고 Apple 클라우드 서명을 쓴다.

- 키: App Store Connect → 사용자 및 액세스 → 통합 → App Store Connect API에서 **팀 키**를 만든다. 클라우드 서명 인증서와 프로파일을 만들 수 있게 역할은 **Admin**으로 한다. `.p8`은 생성 직후 한 번만 내려받을 수 있다.
- Mac 배치: `~/jenkins-agent/asc/`(0700)에 `AuthKey_<KEY_ID>.p8` 하나와 Issuer ID 한 줄을 담은 `issuer-id`를 둔다. 두 파일은 0600이다. 다른 위치는 `IOS_ASC_KEY_DIR`로 지정한다.
- 키·ID는 저장소·Jenkins 설정·로그에 넣지 않는다. 스크립트는 사용 여부만 출력한다.
- 키가 없으면 경고를 남기고 Xcode 계정 세션으로 서명한다. `.p8`이 여러 개거나 `issuer-id`가 없거나 형식이 틀리면 빌드를 멈춘다.
- 교체: 새 키로 폴더의 두 파일을 바꾸고, 셸에서 마지막 아카이브로 `xcodebuild -exportArchive … -authenticationKeyPath …`가 성공하는지 확인한 뒤 이전 키를 App Store Connect에서 폐기한다.
- 저장소 파일을 바꾸면 관리 복사본 `~/jenkins-agent/build-ipa.sh`도 같게 갱신한다.

## 수동 재빌드

기존 Jenkins `eolssu-ios-qa`에서 `REVISION`에 develop에 속한 전체 40자 SHA를 지정한다. 백엔드 전체 배포를 다시 실행할 필요 없다. 결과는 `/ipas/`에 표시된다. 설치 후 앱의 로그인·평가 동작은 실제 iPhone에서 따로 확인한다.

## 검사

```sh
bash -n infra/ios-qa/build-ipa.sh
python3 -B -m unittest discover -s infra/ios-qa -p 'test_*.py' -v
```

운영 Jenkins의 `declarative-linter`로 `ios.Jenkinsfile`을 검사한 뒤 적용한다. 적용은 파일을 `/var/lib/jenkins/ios-qa-candidate.Jenkinsfile`(컨테이너와 같은 경로)에 올리고 관리자 CLI `groovy = < apply-jenkins.groovy`로 한다. 잡 원본 XML·관리 스크립트를 서버의 root 전용 백업에 저장하고, 실행 중인 iOS 빌드가 없을 때 전환한다. backend 잡의 변경 감지·Android 예약·다른 잡은 수정하지 않는다.

복구는 저장한 iOS 잡 XML과 게시 도구를 복원하는 방식이다. 기존 Mac 노드·기존 IPA는 삭제하지 않는다. 이전 잡은 무료 팀 Apple 로그인 서명 오류가 있으므로 설정 복원만으로 성공을 보장하지 않는다.
