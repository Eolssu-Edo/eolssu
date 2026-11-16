# 실제 앱 QA 운영

[시연 대본](public/guide.html) · [설치 APK 로그 계약](../../docs/specs/qa-installed-app-logs.md)

- 현재 설치된 APK와 그 APK에 설정된 실제 백엔드를 사용한다. 앱 설치/재시작/로그인/마이크 입력은 QA 담당자가 선택해 수행한다. 페이지 접속이나 로그 조회는 앱을 시작하거나 서버 데이터를 만들지 않는다.
- Windows의 기존 기기 중계가 화면·제어권과 APK 성능 DB 읽기를 함께 담당한다. 별도 QA 백엔드·AI·DB·로그 서버를 실행하지 않는다. 기존 이름 입장·한글 입력·무활동 종료·기기별 제어권·USB/TLS 복구는 유지한다.
- QA 항목은 앱 실행, 로그인, 기초 콘텐츠, 연습 기록, 연습·평가, 악보 추가·편집의6개다. 격리 서버 자동 검사·부하·합성 녹음·테스트 계정 자동 입력은 종료했다. 운영 서버로 고의 실패/부하 명령을 이전하지 않는다.
- 최신 APK 적용 버튼은 연결된 기기 중 최신 버전이 아닌 기기에만 적용하며 기기별 제어권을 확인한다. 새 빌드 보관은 설치를 뜻하지 않으며 APK/앱 데이터/기존 기록은 유지한다.

## 시작과 연결

기존 Windows 연결 명령/예약 작업을 그대로 사용한다. 각 중계의 supervisor.json에 지정한 releaseDirectory의 supervise.ps1이 실행된다. 수동으로 시작할 때는 이미 실행 중인지 먼저 확인한다.

```powershell
$QaTools = Join-Path $env:LOCALAPPDATA 'Packages\OpenAI.Codex_2p2nqsd0c76g0\LocalCache\Local\EolssuQA'
$QaConfig = Join-Path $QaTools 'relay\supervisor.json'
$QaRelease = (Get-Content -Raw -LiteralPath $QaConfig | ConvertFrom-Json).releaseDirectory
& (Join-Path $QaRelease 'supervise.ps1') -Config $QaConfig
```

추가 기기는 tablet-relay 등 각 기기의 supervisor.json을 지정한다. 중복 실행은 배타 잠금으로 거부된다. ChatGPT 종료와 무관하게 기존 숨김 PowerShell 감독이 중계를 유지한다. USB가 있으면 유선을 우선하고 없으면 등록 IP의 TLS 포트를 계속 검색한다. Wi-Fi/IP가 바뀌면 등록 IP를 먼저 갱신한다.

## 로그와 근거

- GET ./api/live/status는 이름 세션을 확인하고 해당 기기의 원본 보존 로그를 보여 준다. 격리 서비스 설정/토큰을 읽거나 서버 status에 접속하지 않는다.
- 현재 설치 버전의 보존 기록만 표시하고, 단조 시각으로 단계 경과 시간을 계산한다. HTTP 성공은 앱의 HTTP 응답 근거다. 서버 수신·AI 완료·DB 저장은 별도 근거 없이 만들지 않는다.
- 전체/HTTP/오류 필터와 선택 기기/전체 연결 기기/선택 동작 범위,150건씩 이전/최신 기록을 지원한다. 비밀번호·토큰·쿠키·URL·본문은 제외한다. 원본 ACK/삭제를 수행하지 않는다.
- 직접 로그 DB를 읽을 수 없는 앱/기기·WAL·16MiB 초과 사본은 연결 대기로 표시한다. APK를 바꾸거나 원본 기록을 삭제해 우회하지 않는다.
- 기존 POST ./api/live/control은 로그인 후410으로 종료를 알린다. 오래된 탭도 서버 검사·부하·테스트 계정/오디오 명령을 실행할 수 없다.

## 검증과 과거 기록

```powershell
node --test infra/qa-phone/test/*.test.mjs
```

과거936의 격리 환경 결과는 당시 조건의 기록이다. live/는 그 결과의 재현 소스로 보존하며 현행 중계에 포함하지 않는다. start-live.ps1과 prepare-release.ps1 -IncludeLive는 실행을 거부한다. 기존 격리 환경의 종료만 stop-live.ps1로 수행할 수 있다. 현재 결과는 [Task994](../../docs/plans/S15P31E102-994.md)에 기록한다.
