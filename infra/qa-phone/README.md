# 실제 Android 기기 QA 웹

현행 운영 앱 QA와 로그 확인은 [LIVE.md](LIVE.md)를 따릅니다. 격리 검사 서버·자동 검사·부하 실행은 종료했습니다. 아래의 이전 구축 기록은 재현 근거입니다.

S15P31E102-498/936. 기존 Task290/312 중계를 공유해 휴대폰과 태블릿을 별도 프로세스로 실행한다. 기본 원격 제어는 제품 앱 변경 없이 동작하고, Task936의 격리 관측은 별도 QA 빌드 옵션을 사용한다. Node.js24 기본 모듈을 사용하며 npm install은 필요 없다.

- 휴대폰: https://15.165.144.7/qa/ · Galaxy S10
- 태블릿: https://15.165.144.7/qa/tablet/ · Galaxy Tab S9 FE
- [현재 운영: 켜기·끄기·복구·APK 업데이트](OPERATIONS.md)
- [Windows 실기기 실행·관측과 격리 자동 검사 (936)](LIVE.md)
- [기기 격리 스펙](../../docs/specs/qa-tablet-relay.md) · [실제 검증과 제약](../../docs/plans/S15P31E102-498.md)

## 구성

팀원 HTTPS → EDO2 Nginx → 기기별 loopback 역방향 SSH 터널 → Windows loopback Node 중계 → 지정한 실제 기기의 ADB. Windows 포트17680/17681과 EDO2 역방향 포트27890/27891은 외부에 직접 열지 않는다. `nginx.location.conf`는 기존 HTTPS server 블록에 포함한다. 운영자 설정으로 정한 serial·포트·SSH 별칭만 사용하며 웹에 임의 ADB 셸이나 APK 업로드를 제공하지 않는다.

기기마다 입장 세션·제어권·영상·입력 큐·설치 작업·무선 복구 상태가 독립적이다. 태블릿은 `/qa/tablet/` 전용 쿠키 이름과 Path를 사용한다. 같은 출처에서 휴대폰 쿠키가 함께 전송돼도 태블릿 로그인으로 인정하지 않는다. 휴대폰·태블릿 모두 이름만 입력해 입장한다. 공개 주소를 아는 사람은 화면을 보고 제어권을 요청할 수 있다. 표시 이름은 실명 인증이 아니다. [입장 정책](../../docs/specs/qa-name-entry.md)을 참고한다.

헤더에서 대상 기기와 참여자·현재 조작자를 확인한다. 제어권 요청은 현재 조작자의 승인/거절을 거치며, 30초 동안 응답하지 않으면 기존 정책에 따라 양도한다. 소유자는 반납할 수 있고 연결해제는 본인의 웹 세션만 종료한다. 실제 기기나 다른 사람의 접속을 끊지 않는다. 연결/입력 오류·화면 회전은 이전 입력 권한을 무효화한다. 복구 후 제어권을 다시 요청한다.

조작자와 참여자 모두 10분간 활동이 없으면 1분 동안 **계속 연결** 확인을 받는다. 응답하지 않으면 해당 웹 세션, 영상, 제어권이 종료된다. 자동 화면 갱신은 활동으로 세지 않는다.

화면 클릭은 터치, 최대1초 드래그는 스와이프다. Android 탐색 키와128자 이내 한글·영문·기호 입력을 지원한다. 한글 도구는 기기 클립보드 내용을 입력한 텍스트로 대체하고 붙여넣는다. 멀티터치는 지원하지 않는다. 실행 중인 유한 ADB 동작이 끝난 뒤 제어권을 전환하고 이전 대기 입력은 폐기한다. 입력 실패 후에는 입력 프로세스 종료와 새 캡처를 확인한 뒤 잠금을 해제하며 실패 입력을 재전송하지 않는다.

공유 영상은 scrcpy5.0.1의 H.264 출력만 사용한다(상한30FPS/2Mbps). 브라우저가 WebCodecs를 지원하지 않거나 영상이 실패하면 PNG로 복구한다. 실제 수신 FPS 또는 PNG 갱신 간격을 화면에 표시한다. 오디오·마이크·카메라는 중계하지 않으며 실제 기기 것을 사용한다. 제어권 변경은 제품 앱을 재시작하거나 녹음/재생을 조작하지 않는다.

입장한 참여자의 유효 heartbeat 또는 영상 연결이 있으면 `SCREEN_BRIGHT_WAKE_LOCK`을 유지한다. 도구는5초 heartbeat와 기기 자체15초 lease를 사용하고 마지막 접속 종료·stdin EOF·기한 만료 시 해제한다. 전역 밝기/화면 타임아웃은 바꾸지 않는다. 휴대폰 기존24시간 타임아웃도 유지한다. Android12/API31과 Android16/API36의 실제 도구 실행을 확인했다. 다른 OS의 숨김 AIDL 계약은 미검증이다.

## 새 호스트 준비와 로컬 실행

PowerShell7.4+, Node24, 공식 Android Platform-Tools, 승인된 실제 기기의 USB 디버깅 또는 무선 페어링이 필요하다. 여러 기기 중 serial을 반드시 명시한다. 루팅·부트로더 변경은 필요 없다. 설치와 디버깅 허용은 기기 소유자가 진행한다.

```powershell
$QaAdb = 'C:\Android\platform-tools\adb.exe'
& $QaAdb devices -l
$QaSerial = '실제_기기_serial'
& $QaAdb -s $QaSerial shell getprop ro.product.model
./infra/qa-phone/setup-video.ps1
./infra/qa-phone/setup-screen.ps1
./infra/qa-phone/start.ps1 -Serial $QaSerial -Adb $QaAdb
```

로컬 기본 주소는 http://127.0.0.1:17680 이다. 브라우저에서 이름을 입력하고 기기 연결을 누른다. 이미 운영 중인 공개 중계와 같은 포트로 시작하지 않는다. `setup-screen.ps1`은 기존 EDO1 JDK21/Android SDK로 작은 DEX 도구를 빌드하고 소스/바이너리 해시를 검증한다. `setup-video.ps1`은 고정된 공식 scrcpy 서버를 검증한다. 설치 도구의 실행 인자는 각 스크립트 param 블록을 따른다.

공개 운영은 [OPERATIONS.md](OPERATIONS.md)의 두 설정 파일과 감독 명령을 사용한다. 새 기기는 전용 BasePath·Port·ReversePort·RuntimeDirectory를 지정해야 하며 휴대폰 기본값 재사용을 거부한다. `ToolRoot`로 검증한 도구 폴더를 명시할 수 있다. 감독 잠금은 프로세스 종료 시 OS가 해제하는 파일 핸들이며 다른 기기의 stop 파일/터널 기록으로 종료하지 않는다.

## 검사

```powershell
node --test infra/qa-phone/test/*.test.mjs
node --check infra/qa-phone/server.mjs
node --check infra/qa-phone/public/app.js
```

APK 게시기는 Linux의 `fcntl`을 사용한다. Linux 체크아웃에서 `python3 -B -m unittest discover -s infra/qa-phone/test -p test_publish_apk.py`를 실행한다. Windows Python에서는 이 검사를 실행할 수 없다.

Node 테스트는 고정 캡처/입력 기록을 사용하는 격리 대역이다. 기기 두 대에서 실행한 두 HTTP 중계의 쿠키·제어권·입력·설치 분리는 실제 물리 PC 두 대 검증과 구분한다. 기존 실제 태블릿의 공개 HTTPS 영상·터치·한글·회전·승인/거절·APK 설치와 Android31/36 밝기 도구 조건은 [Task498 기록](../../docs/plans/S15P31E102-498.md)에 있다. 새 격리 관측·실제 AI/DB 검사 및 앱 확인 범위는 [Task936](../../docs/plans/S15P31E102-936.md)에 별도로 기록한다.
