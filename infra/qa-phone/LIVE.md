# Windows 실기기 실행·관측

S15P31E102-936. 단일 /qa/ 페이지에 등록된 최대6대의 연결 기기를 자동 배치한다. 기존 /qa/tablet/ 주소도 같은 통합 화면을 제공한다. Mac 대시보드의 밝은 종이색·흰 패널·올리브/라임·상태 색상을 따른다. 영상·한글 입력·제어권 요청/승인/거절·무활동 연결해제·APK 이력/업데이트·페어링·밝기 유지는 기존 중계가 담당한다.

## 동작 범위

- 관측 시작은 `-Peolssu.qaLive=true`로 만든 관측용 `com.ssafy.eolssu.qa`만 실행한다. 이 빌드는 API가 `http://127.0.0.1:18180`인 경우에만 만들 수 있다. 일반 QA APK를 최신 버전으로 설치한 경우 관측용 빌드를 다시 설치해야 한다. 앱 데이터 삭제나 서명 불일치 우회는 하지 않는다.
- 격리 backend·현재 AI·MySQL은 Windows Docker의 전용 네트워크와 소유권 라벨을 사용한다. 운영 DB·사용자 계정·스토리지·메일을 사용하지 않는다. API 포트는 Windows loopback만 연다. 앱에는 선택한 기기의 `adb reverse`로 연결한다.
- 휴대폰·태블릿의 고유 ID를 운영자가 등록한다. 현재 ADB 목록에서 해당 ID를 확인해 USB 또는 복구된 TLS 연결을 선택한다. 목록의 첫 기기를 임의 선택하지 않는다. 다른 기기의 관측 이벤트를 복사하지 않는다.
- 등록 기기의 관측은 기기별 프록시18280~18285·ADB reverse·수집기·시작 세션으로 분리해 함께 실행할 수 있다. 서버 자동 검사·동시 제출은 관측 및 앱 실행과 중앙 Runtime에서 배타 처리한다. 시작에는 기존 QA 제어권·epoch·최신 프레임이 필요하며, 종료/중단은 실행을 시작한 웹 세션만 가능하다. 다른 참여자의 제어권 양도는 앱 녹음·재생 자체를 중단하지 않는다.
- 관측·검사 중에는 자원 정리 타이머가 실행을 끊지 않는다. 30분 동안 실행이 없는 격리 환경은 소유한 컨테이너·녹음·폐기용 계정을 정리한다. 다음 시작은 다시 준비하며 실행 이력은 보존한다.
- 관측/AI 계측 코드 갱신은 QA 종료 후 `stop-live.ps1`/`start-live.ps1`로 런타임과 수집기를 함께 준비한다. AI 컨테이너만 재시작하면 기존 `docker logs -f` 프로세스가 끝나 완료 근거 수집이 끊길 수 있다. 실제 S10 마이크 수행 결과와 관측 오류 수정 근거는 [검증 기록](../../docs/knowledge/infra/qa-s10-real-microphone-verification.md)에 둔다.

## 화면에서 사용

[QA 담당자 사용 설명서](public/guide.html)는 별도 페이지다. QA 화면에는 단계명·조작 버튼·상태·결과를 두고 설명 문장은 사용 설명서에 둔다.

1. 이름으로 입장하면 각 등록 중계의 세션을 연결한다. **최신 APK 적용**은 최신 보관 빌드와 실제 설치 패키지/버전이 다른 연결 기기만 갱신한다. **QA 앱 실행**은 연결된 기기를 대상으로 현재 설치된 APK를 실행한다. 제어권이 없고 다른 조작자가 있으면 승인 대기로 남긴다. 관측용 빌드는 기존 격리 관측을 시작하고, 최신 자동 빌드 APK는 고정된 QA 앱 컴포넌트만 실행하며 존재하지 않는 앱 로그를 생성하지 않는다.
2. 이메일 칸을 클릭한 다음 **이메일 입력**을 누른다. 반드시 비밀번호 칸을 따로 클릭해 포커스를 확인한 뒤 **비밀번호 입력**을 누른다. 계정 값은 서버가 직접 입력하며 비밀번호를 웹 응답·관측 이벤트로 보내지 않는다.
3. 로그인 후 곡 목록 → 곡 상세 → 연습·평가를 조작한다. **최신**을 끄면 확인할 앱 동작을 고정할 수 있다.
4. 기본 녹음 입력은 **기기 마이크**다. **녹음 입력 변경**에서 **QA 테스트 소리**를 선택하고 **입력 적용**을 누르면 합성 PCM 파일을 준비한다. 적용은 다음 녹음 시작 시 반영되며 진행 중인 녹음은 기존 입력을 계속 사용한다. 실제 사용은 `synthetic_input` 이벤트로 확인하며 입력 설정만으로 마이크/합성 입력 검증을 완료하지 않는다.
5. 각 기기의 **로그 수집 종료** 후 서버 자동 검사의 네 버튼 중 하나를 선택한다. `feedback`은 두 합성 연주의 음표별 차이, `retry`는 QA AI의 지정 세션 1회503과 재시도, `duplicate`는 동시 중복 제출, `ownership`은 타인 평가 거절과 본인 평가 성공을 검사한다.
6. **동시 제출**은 먼저6개로 실행한다. 150/300/500/750/1000은 명시적으로 선택해야 한다. 성공/실패/5초 기준, 제출별 저장 확인과 소요 시간, 중단과 이전 실행 결과를 확인한다. 대규모 실행은 이번 Windows 검증 결과에 포함하지 않는다.

## 로그 열람 계약

- 이름 세션으로 읽는 `GET ./api/live/status?logs=<선택값>`은 선택한 근거를 `evidence: {id,total,records}`로 추가한다. 선택하지 않은 기본 상태 응답에서는 로그 본문을 재귀적으로 제외한다. 읽기 권한과 기기별 이벤트 격리는 기존 세션 경계를 따른다.
- 선택값은 현재 앱 동작 ID, `device/all`, `server/scenario`, `server/load`, `history/<실행 UUID>`다. `device/all`은 해당 기기의 앱·중계·수집 이벤트와 그 요청/timing ID에 결속된 서버·AI·DB 근거만 반환한다. 브라우저의 모든 기기 범위는 각 기기의 이 응답을 번호/색상으로 합치며 다른 실행의 결과를 복사하지 않는다. 서버가 가진 동작/실행과 일치하는 것만 조회하며 임의 파일·URL·셸 입력으로 사용하지 않는다. 없는 기록은 빈 목록이다.
- 앱 동작은 해당 요청 UUID/평가 timing ID와 시도를 연결한 로그다. HTTP 성공만으로 DB나 앱 그리기 기록을 생성하지 않는다. 서버 검사는 실행 시작 이후의 해당 요청 UUID·세션에 속하는 백엔드/AI 파일 기록, 실제 HTTP 요청/응답, 실제 DB 조회 결과를 수집한다.
- 출처는 `app`, `backend`, `ai`, `db`, `test`, `proxy`, `observer`로 구분한다. `db`는 DB 조회 기록이며 DB 원문 로그가 아니다. `test`는 검사 도구의 HTTP 기록이며 기기 앱 로그가 아니다. 원문은 허용된 ID·상태·시각·수치 필드로 정규화하고 비밀번호/토큰/쿠키/본문은 반환하지 않는다.
- 실행의 수집 기록은 최근500건을 보관한다. 앱 동작은 수집기 보관 범위에서 해당 동작의 총건수와 최근500건을 반환한다. 화면은 출처 필터 후 표시 수/보관 수를 표시한다. 과거에 로그를 저장하지 않은 실행은 복원하거나 합성하지 않는다.

## 증거 해석

실제 HTTP 요청 시도마다 UUID가 다르며 앱·프록시·서버 이벤트를 같은 UUID로 연결한다. 앱 재시작 때 다시 쓰는 작은 숫자는 `appRun`과 flow/visit/submission을 함께 사용한다. 업로드는 timing ID·session ID·attempt·evaluation ID로 연결한다.

서버 요청 수신은 실제 backend 필터 로그이며 프록시 수신과 구분한다. 업로드 202, AI 시작/완료, DB의 DONE·평가 1개·음표 저장, 앱의 DONE 수신, 첫 `score_drawn`을 별도로 보여 준다. HTTP 200만으로 앱 그리기를 통과시키지 않는다. 증거가 없으면 대기다.

화면 시각은 관측기가 이벤트를 받은 시각이다. 로그 수집 지연을 서버 처리 시간으로 계산하지 않는다. 앱의 요청 후 경과는 같은 앱의 monotonic 시계, 서버·AI 처리 시간은 해당 구성요소가 기록한 duration을 사용한다. 동시 제출의 측정 구간은 업로드 HTTP 송신 시작부터 본인 평가 응답과 DB 저장 확인까지다. 성공·실패·취소를 포함한 전체 제출 대상이 분모이며 5초 이내 수도 같은 분모다. 서버 분석 마감은 60초, 클라이언트 결과 확인 마감은 75초다.

## 운영 PC 실행

현재 고정 릴리스는 QA 도구 폴더의 `live-936/release-edf90cc3`이다. 기존 두 예약 작업은 각자의 `supervisor.json`의 `releaseDirectory`를 읽으므로 이전 체크아웃 UI로 돌아가지 않는다. 이름 입장·페어링·APK 캐시 설정은 각각의 `relay`와 `tablet-relay`에 유지된다. 예약 작업의 실행 계정/권한은 변경하지 않는다.

릴리스 패키지는 `prepare-release.ps1 -OutputDirectory <별도 경로> -IncludeLive`로 준비한다. Node 모듈·PowerShell·`scrcpy-manifest.json`·정적 화면·Python 도구·합성 fixture·빌드된 QA backend JAR을 포함하고 `release-manifest.json`에 SHA-256을 기록한다. 비밀키나 실행 계정은 패키지에 넣지 않는다. Docker Desktop, JDK21, Node24, Python3.11+, 공식 Android Platform-Tools가 필요하다.

```powershell
$QaTools = Join-Path $env:LOCALAPPDATA 'Packages\OpenAI.Codex_2p2nqsd0c76g0\LocalCache\Local\EolssuQA'
$QaRelease = Join-Path $QaTools 'live-936\release-edf90cc3'
& (Join-Path $QaRelease 'start-live.ps1') `
  -Serial 'R54X3035L8J' -Identity 'R54X3035L8J' `
  -Adb (Join-Path $QaTools 'platform-tools\adb.exe') `
  -Python (Join-Path $QaTools 'live-936\venv\Scripts\python.exe') `
  -RuntimeDirectory (Join-Path $QaTools 'live-936\runtime') `
  -RelayRuntime (Join-Path $QaTools 'tablet-relay') `
  -AdditionalRelays @{R39M402VJ0H=(Join-Path $QaTools 'relay')}
```

위 명령은 등록된 기기 ID를 사용하는 현재 PC용 예시다. 먼저 `adb devices -l`로 시작 기기의 연결을 확인한다. 추가 기기는 나중에 연결해도 실행 시 ID를 다시 확인한다. 서비스는 별도 숨김 PowerShell 자식 프로세스로 실행돼 ChatGPT 종료와 무관하게 유지된다. 기기별 기존 중계는 예약 작업이 유지한다. 관측 서비스는 PC 재부팅 뒤 위 명령으로 시작한다. 이미 실행 중이면 중복 시작을 거부한다.

```powershell
& (Join-Path $QaRelease 'stop-live.ps1') -RuntimeDirectory (Join-Path $QaTools 'live-936\runtime')
```

종료는 해당 runId의 라벨이 일치하는 컨테이너와 네트워크만 제거한다. QA UI 이력은 `live-936/runtime/history.json`, 소유한 검증 실행의 임시 원문은 그 아래 `runtime/`에 있다. 브라우저에는 허용한 ID·수치·판정만 보낸다. private 설정·원문 로그·녹음 파일을 공개하거나 Git에 넣지 않는다. 관측 서비스가 재시작해 키가 바뀌어도 중계가 설정을 다시 읽으므로 기존 QA 탭을 새로고침할 필요가 없다.

## 검증

```powershell
node --test infra/qa-phone/test/*.test.mjs
python -m unittest discover -s infra/qa-phone/live -p test_windows_runtime.py -v
```

실제 기기·격리 API·네 검사·소규모 부하 결과와 남은 항목은 [Task936](../../docs/plans/S15P31E102-936.md)에 구분해 기록한다. Mac 이력은 Windows 통과 증거로 사용하지 않는다.

## 통합 기기·앱 실행 계약 · 2026-11-16

- `public/devices.json`에 중계 ID·표시 이름·동일 출처 basePath를1~6개 등록한다. 현재는 `/qa/`와 `/qa/tablet/` 두 중계다. 추가 기기는 기존 `supervise.ps1`의 전용 RuntimeDirectory·Port·ReversePort·BasePath와 원격 Nginx 경로를 먼저 준비하고, 관측 시 `start-live.ps1 -AdditionalRelays`에 고유 ID를 등록한다. 임의의 미등록 ADB 기기를 자동 제어하지 않는다. 화면에서는 연결된 기기만 자동 표시하며 따로 표시 기기를 선택하지 않는다.
- 각 경로의 HttpOnly 쿠키와 소유권/epoch/순번·영상은 독립적이다. 헤더는 선택한 기기를 제어하고, 포트 폼은 카드의 ⋯ 대화상자에서 고정한 기기만 변경한다. 미연결 기기는 하단 페어링 메뉴에서 접근한다. 참여자 표시는 등록 중계의 표시 이름을 합쳐 보여 준다.
- `POST ./api/app/launch`는 JSON `{epoch}`와 현재 이름 세션을 요구한다. 소유권·epoch·온라인/최신 프레임·유지보수를 검사한 후 등록된 `com.ssafy.eolssu.qa/com.ssafy.eolssu.MainActivity`만 실행한다. 패키지/컴포넌트/셸 입력을 요청으로 받지 않는다. 200의 `{started:true}`는 앱 실행 명령 완료이고 앱 로그인·API·녹음·채점·그리기 완료를 뜻하지 않는다. 비로그인401, 비소유자403, 오래된 epoch/화면409, 실행 확인 실패503이다. 관측 서비스가 설정돼 있으면 중앙 `launch` 예약을 사용해 서버검사/부하 시작과의 경합을 막는다.
- 버전이 확인 중/실패이면 최신으로 표시하지 않는다. 일괄 갱신은 클릭 때의 대상 기기를 고정하고 각 적용 직전 최신 목록·설치 상태·소유권을 다시 검사한다. 이미 최신인 기기와 다른 기기의 입력/데이터는 바꾸지 않는다. 설치 실패 시 제거/데이터 삭제로 우회하지 않는다.
- USB가 연결된 등록 기기는 USB 전송을 우선한다. 무선 포트 검색·이력·페어링은 유지하고, USB가 없으면 검증된 TLS endpoint로 돌아간다. 영상 초기화 중 online=false가 되어도 인증된 스트림은 유지해 geometry/전송 복구를 마친다. 상태가 오래되거나 권한이 없으면 입력을 차단한다.
- 중계·영상은 기존 숨김 PowerShell 감독이 유지한다. ChatGPT 종료와 무관하게 실행된다. 컨트롤러만 교체할 때 동일 runId의 세 컨테이너/계정/녹음은 소유권 확인 후 재채택할 수 있다. 재채택 실패는 기존 자원을 보존하며 임의 초기화로 대체하지 않는다.
