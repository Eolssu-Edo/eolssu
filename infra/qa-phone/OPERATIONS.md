# QA 화면 공유: 켜기·끄기·연결 복구·APK 업데이트

접속: **https://15.165.144.7/qa/** (Galaxy S10) · **https://15.165.144.7/qa/tablet/** (Galaxy Tab S9 FE) · 작업: S15P31E102-498 · 갱신: 2026-11-05

## 1. 켜기

연결용 Windows PC와 각 기기를 같은 네트워크에 두고 **무선 디버깅**을 켠다. USB 연결도 가능하다. 현재 연결은 휴대폰192.168.30.82:40701, 태블릿192.168.30.89:41631이다(2026-11-05 확인). 주소는 바뀔 수 있으므로 기기 화면과 확인한다. PC는 켜 두고 절전하지 않는다.

두 중계는 **서로 다른 터미널**에서 실행한다. 아래 각 블록 전체를 해당 터미널에 붙여넣는다. 현재 두 감독이 실행 중이면 중복 실행하지 않는다.

휴대폰:

```powershell
$QaRoot = 'C:\Users\SSAFY\AppData\Local\Packages\OpenAI.Codex_2p2nqsd0c76g0\LocalCache\Local\EolssuQA'
& 'C:\Users\SSAFY\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File 'C:\Users\SSAFY\.codex\worktrees\qa498-tablet\S15P31E102\infra\qa-phone\supervise.ps1' -Config (Join-Path $QaRoot 'relay\supervisor.json')
```

태블릿:

```powershell
$QaRoot = 'C:\Users\SSAFY\AppData\Local\Packages\OpenAI.Codex_2p2nqsd0c76g0\LocalCache\Local\EolssuQA'
& 'C:\Users\SSAFY\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File 'C:\Users\SSAFY\.codex\worktrees\qa498-tablet\S15P31E102\infra\qa-phone\supervise.ps1' -Config (Join-Path $QaRoot 'tablet-relay\supervisor.json')
```

`QA 감독 실행 시작`과 `QA 접속 주소`가 출력되면 **터미널을 계속 켜 둔다.**
브라우저에서 주소 접속 → 이름·접속 키 입력 → 제어권 요청 → 기존 조작자가 승인한다.
팀원 PC에는 ADB·SSH·폰 페어링이 필요 없다.

- 실행 중 중계·SSH 터널·저장된 주소의 무선 ADB 재연결을 자동으로 시도한다.
- 무선 포트 자동 검색은 최초 연결 성공 때 같은 폰의 고유 ID를 저장한 뒤 동작한다. 약 5초 간격으로 연결 상태와 ADB mDNS/연결된 기기를 확인하고, 고유 ID가 일치하는 새 주소를 찾으면 해당 중계를 정상 재시작한다. ADB 명령 시간과 감독 재시도 대기가 추가되므로 전체 복구 시간이 5초라는 뜻은 아니다. 웹 재로그인과 제어권 재요청이 필요하다.
- **PC 재부팅 후에는 위 명령을 직접 다시 실행한다. 부팅 자동 실행은 등록하지 않았다.**
- 일반 터미널과 Codex의 AppData 경로 차이는 감독 스크립트가 자동 처리한다.

## 2. 끄기

대상 기기의 실행 터미널에서 **Ctrl+C**를 누르고 명령 입력 상태로 돌아올 때까지 기다린다.
웹의 **연결해제**는 본인 접속만 종료하며 중계 전체를 끄는 버튼이 아니다.

실행 터미널을 찾을 수 없으면 다른 PowerShell에서 아래 명령으로 정상 종료를 요청한다. 아래는 휴대폰용이다. 태블릿은 폴더 `relay`를 `tablet-relay`로, 조회 포트를17681로 바꾼다. 다른 기기의 폴더를 지우거나 종료하지 않는다.

```powershell
$QaRelay = "$env:LOCALAPPDATA\Packages\OpenAI.Codex_2p2nqsd0c76g0\LocalCache\Local\EolssuQA\relay"
[IO.File]::WriteAllText((Join-Path $QaRelay 'supervisor.disabled'), '')
Get-NetTCPConnection -LocalPort 17680 -State Listen -ErrorAction SilentlyContinue
```

감독 실행이 종료되고 마지막 조회의 수신 항목도 없어졌는지 확인한다.
이 방식으로 껐다면 **다음에 켜기 전에** 중지 표시를 제거한다.

```powershell
Remove-Item -LiteralPath (Join-Path $QaRelay 'supervisor.disabled') -ErrorAction SilentlyContinue
```

그다음 1번 명령으로 켠다. 중계 재시작 후에는 팀원이 다시 로그인해야 한다. 폰 앱은 종료하지 않는다.

## 3. 연결이 끊겼을 때

| 증상 | 대응 |
| --- | --- |
| **일시적인 502 / 인터넷 끊김** | 감독 터미널을 유지하고 네트워크를 복구한다. SSH 단절 감지·재연결 시간을 기다린 뒤 새로고침한다. |
| **502가 계속됨 / 터미널을 닫음 / PC 재부팅** | 1번 명령으로 다시 켠다. 이미 실행 중이면 2번으로 종료한 뒤 재실행한다. |
| **웹은 열리지만 폰 연결 대기** | 폰 무선 디버깅·같은 네트워크인지 확인한다. 최초 기기 ID가 저장됐다면 포트 변경도 자동 검색한다. mDNS가 차단됐거나 최초 ID를 아직 저장하지 못했다면 아래 수동 주소 복구를 사용한다. |
| **입력 연결 복구 중** | 정상 연결로 돌아오면 제어권을 다시 요청한다. 실패한 터치는 자동 재전송하지 않는다. 계속되면 폰 연결 확인 후 중계를 재시작한다. |
| **화면은 보이지만 클릭 불가** | 제어권 요청·승인 여부를 확인한다. 폰이 잠겼으면 폰에서 직접 잠금 해제한다. |
| **감독 잠금 오류** | 다른 터미널에 실행 중인지 확인한다. 실행 중인 감독을 먼저 종료한다. lock 파일을 임의로 삭제하지 않는다. |
| **설정 파일/폴더 없음** | 아래 실제 경로를 확인한다. 빈 relay 폴더만 만들어서는 도구·설정·접속 키가 복구되지 않는다. |

### 기기 IP·포트가 바뀐 경우

같은 IP에서 연결 포트만 바뀌었다면 해당 QA 페이지 헤더의 **디버깅 포트 → 포트 적용**을 사용한다. 페어링 팝업이 아닌 무선 디버깅 기본 화면의 포트다. 해당 기기의 중계만 재시작하므로 재로그인해야 한다. IP까지 바뀌었거나 웹 복구가 안 되면 아래 운영자 절차를 사용한다. 태블릿 설정 경로는 `tablet-relay/supervisor.json`이다.

자동 검색이 되지 않을 때 사용하는 복구 절차다. 먼저 2번으로 중계를 끈다. 폰의 **무선 디버깅 기본 화면** 주소를 확인하고 설정을 수정한다.
**페어링 팝업의 포트와 연결 포트는 다르다.**

```powershell
$QaRoot = "$env:LOCALAPPDATA\Packages\OpenAI.Codex_2p2nqsd0c76g0\LocalCache\Local\EolssuQA"
$QaConfigPath = Join-Path $QaRoot 'relay\supervisor.json'
$QaConfig = Get-Content -LiteralPath $QaConfigPath -Raw | ConvertFrom-Json
$QaConfig.serial = Read-Host '폰 기본 화면의 현재 IP:연결포트'
$QaConfig | ConvertTo-Json | Set-Content -LiteralPath $QaConfigPath -Encoding utf8
& (Join-Path $QaRoot 'platform-tools\adb.exe') connect $QaConfig.serial
& (Join-Path $QaRoot 'platform-tools\adb.exe') devices -l
```

해당 폰이 **device**인지 확인하고 1번으로 다시 켠다. 중지 표시를 만들었다면 먼저 제거한다.
페어링 자체가 풀렸다면 폰의 ‘페어링 코드로 기기 페어링’을 열고 아래 명령을 실행한다.

```powershell
& (Join-Path $QaRoot 'platform-tools\adb.exe') pair (Read-Host 'IP:페어링포트')
```

코드는 터미널에 직접 입력한다. 이후 기본 화면 주소로 다시 `connect`한다.

## 4. 최신 원격 develop APK로 업데이트

### QA 웹 버튼으로 업데이트

1. 대상 기기 URL에 본인 이름으로 로그인한다. 휴대폰과 태블릿은 각각 접속한다.
2. 헤더에서 **제어권 요청** 후 현재 조작자가 있으면 승인을 받는다.
3. **앱 버전 → 최신 APK로 업데이트**를 누른다. 현재 보관소의 최신 APK를 설치하며, 이력 목록에서 선택한 이전 버전과는 무관하다.
4. **설치 중…**에는 해당 기기의 터치/중복 설치가 잠긴다. 다른 기기는 계속 사용한다. 실행 중인 앱은 종료될 수 있으므로 녹음·작업을 먼저 마친다.
5. **최신 버전 사용 중**과 설치된 MR을 확인한다. 실패 문구가 나오면 기기 연결/설치 오류를 확인한 뒤 다시 시도한다. 앱 삭제/초기화를 자동 수행하지 않는다.

기기 단절·입력 오류·제어권 없음·설치 조회 실패·목록 동기화 오류에서는 버튼이 비활성이다. 이미 같은 버전이면 재설치하지 않는다. ‘최신’은 **성공하여 보관된 APK 중 최신**이며 아직 빌드되지 않은 origin/develop 최신 SHA와는 다를 수 있다. 해당 SHA의 APK 생성까지 필요한 경우 아래 운영자 절차를 따른다.

### 원격 SHA에 맞춘 빌드·운영자 설치

Codex에 **“최신 원격 develop 기준 APK로 태블릿을 업데이트해”**처럼 대상 기기를 명시해 요청한다. 두 기기는 설치 상태가 독립적이며 한쪽 설치가 다른 기기를 업데이트하지 않는다.
다른 팀원이 조작 중이면 그 사람의 QA 웹에 뜨는 업데이트용 **제어권 요청을 승인**한다.
설치 중에는 실행 중인 앱이 종료될 수 있으므로 녹음·작업을 끝낸 뒤 진행한다.

**앱 버전 목록은 조회용이다. 목록에서 버전을 선택하는 것만으로 설치되지 않는다.**
빌드 보관·목록 갱신은 자동이지만 실제 폰 설치는 명시적으로 실행한다.
운영자가 직접 진행하려면 아래 순서를 따른다. 휴대폰을 연결한 PC와 중계는 계속 켜 둔다.

### ① 최신 커밋과 빌드 확인

저장소가 있는 PC의 PowerShell에서 원격 develop의 **전체 40자리 SHA**를 확인한다.

```powershell
git -C 'C:\Users\SSAFY\.codex\worktrees\qa498-tablet\S15P31E102' ls-remote origin refs/heads/develop
```

대상 기기의 QA 웹에 로그인해 앱 버전 목록의 **MR 번호 - 작성자 - MR 제목**과 기준 커밋을 확인한다. 여러 빌드가 같은 MR에 속할 수 있어 빌드 번호도 함께 표시한다. ‘최신’ 표시는 **보관된 APK 중 최신**이며,
원격 develop의 최신 커밋과 다를 수 있다. 아래 설치 명령은 전체 SHA가 같은 APK만 선택한다.
아직 해당 커밋 APK가 없으면 EDO1 Jenkins의 `eolssu-android-qa`에서 `REVISION`에 전체 SHA를 넣어
매개변수 빌드를 실행하고 **SUCCESS 및 APK 게시**를 확인한다. 이미 같은 SHA의 성공 APK가 있으면 재빌드하지 않는다.
동기화는 EDO1 약 1분·웹 목록 약 5초 간격이며 다운로드 시간이 추가될 수 있다.
동기화 오류나 빌드 실패로 대상이 없으면 원인을 확인하고, 다른 커밋 APK로 대신 설치하지 않는다.

### ② 제어권을 받은 뒤 설치

QA 웹에서 **제어권 요청 → 현재 조작자 승인**을 거쳐 본인 이름이 현재 조작자인지 확인한다.
그 QA 웹 탭의 브라우저 개발자도구 **Console**에서 아래 코드를 실행한다.
입력창에는 ①의 전체 SHA를 넣는다. 로그인된 세션을 사용하므로 접속 키를 코드에 넣을 필요가 없다.
이 코드는 일치하는 APK를 찾으면 **즉시 설치를 시작**한다. 이미 같은 버전이면 재설치하지 않는다.

```javascript
(async () => {
  if (location.origin !== 'https://15.165.144.7' || !['/qa/', '/qa/tablet/'].includes(location.pathname)) {
    throw new Error('공개 QA 웹 탭에서 실행하세요.');
  }
  const sha = prompt('최신 origin/develop의 전체 40자리 SHA')?.trim();
  if (!/^[a-f0-9]{40}$/.test(sha || '')) throw new Error('전체 SHA가 필요합니다.');
  const base = location.pathname; // 현재 열린 기기만 대상으로 한다.
  const api = async (path, data) => {
    const response = await fetch(base + 'api/' + path, {
      method: data === undefined ? 'GET' : 'POST',
      ...(data === undefined ? {} : {
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data)
      })
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.error || `HTTP ${response.status}`);
    return result;
  };
  const catalog = await api('builds');
  const build = catalog.builds.filter(b => b.commit === sha)
    .sort((a, b) => b.builtAt.localeCompare(a.builtAt) || b.build - a.build)[0];
  if (!build) throw new Error('이 커밋의 APK가 없습니다. 빌드·동기화를 확인하세요.');
  const matches = actual => actual?.package === build.package &&
    actual.versionCode === build.versionCode && actual.versionName === build.versionName;
  const state = await api('state');
  if (!state.online || state.inputFault || state.maintenance || state.owner !== state.you) {
    throw new Error('폰 연결과 본인 제어권을 확인하고, 다른 설치가 끝난 뒤 실행하세요.');
  }
  if (matches(catalog.installed)) return console.log('이미 설치된 버전입니다.', build);
  console.log('설치 시작', { MR: build.mergeRequest, commit: build.commit, id: build.id });
  await api('builds/install', { id: build.id, epoch: state.epoch });
  const deadline = Date.now() + 150000;
  while (Date.now() < deadline) {
    await new Promise(resolve => setTimeout(resolve, 1000));
    const result = await api('builds');
    if (result.job.id !== build.id) throw new Error('설치 작업이 변경됐습니다. 상태를 확인하세요.');
    if (result.job.status === 'failed') throw new Error(result.job.error);
    if (result.job.status === 'success') {
      if (!matches(result.installed)) throw new Error('설치 버전 불일치: 폰 상태를 확인하세요.');
      console.log('업데이트 완료', result.installed, build);
      return;
    }
  }
  throw new Error('확인 시간 초과: 재실행 전에 설치 작업 상태를 확인하세요.');
})().catch(console.error);
```

서버가 보관 APK의 크기·SHA256을 검사하고 설치 중 입력을 잠근다. 기존 패키지를 업데이트하며
서명 불일치·설치 실패를 해결하려고 앱 삭제나 데이터 초기화를 자동 실행하지 않는다.
제어권이 중간에 바뀌면 설치 요청이 거부될 수 있으므로 상태를 확인한 뒤 다시 진행한다.

### ③ 완료 확인과 실패 대응

- Console의 **업데이트 완료**, QA 웹의 **설치됨 MR 번호**, APK 버전·기준 커밋을 대조한다.
- 앱이 실행되고 설치 중 입력 잠금이 해제됐는지 확인한다. 이후 제어권을 반납하거나 연결해제한다.
- `HTTP 401`이면 다시 로그인한다. 제어권 오류면 승인받은 뒤 재시도한다.
- 확인 시간 초과·통신 오류라면 설치가 계속 진행 중일 수 있다. 먼저 아래 조회로 `job`과 `installed`를 확인한다. 설치 중에는 중복 실행하거나 중계를 끄지 않는다.

```javascript
fetch(location.pathname + 'api/builds').then(r => r.json()).then(console.log);
```

- 연결 오류는 3번 복구 절차를 따른다. 서명 불일치 등은 오류 내용을 운영자에게 공유하고 앱을 임의로 삭제하지 않는다.
- 2026-11-02 검증 예: MR **!209**, commit `1dec028b389b5d5df33106a6b773710e9643a739`, Android 빌드 **#21**, 설치 완료 **12:19 KST**. 이후 업데이트에서는 매번 원격 SHA를 새로 확인한다. [현재 설치·검증 기록](../../docs/plans/S15P31E102-498.md)

## APK 보관 위치와 설치 정보

EDO1 원본은 두 기기가 공통으로 사용한다: `/opt/eolssu/apks/<build-id>/app.apk`, 목록은 `/opt/eolssu/apks/index.json`이다. Windows의 실제 루트는 `C:/Users/SSAFY/AppData/Local/Packages/OpenAI.Codex_2p2nqsd0c76g0/LocalCache/Local/EolssuQA`이다.

| 대상 | Windows APK 사본 | 별도 상태 |
| --- | --- | --- |
| 휴대폰 | `<루트>/relay/apks/<build-id>/app.apk` | `relay/supervisor.json`, 로그·무선 상태·설치 조회 |
| 태블릿 | `<루트>/tablet-relay/apks/<build-id>/app.apk` | `tablet-relay/supervisor.json`, 로그·무선 상태·설치 조회 |

초기 태블릿 보관소의 기존 불변 APK는 NTFS 하드링크로 연결해 디스크 중복을 줄였고, 목록 파일과 이후 동기화/설치 상태는 분리했다. 보관 파일을 직접 편집하지 않는다. 두 감독의 `ToolRoot`는 검증된 공통 도구 `<루트>/tablet-tools`를 가리킨다. 기존 휴대폰 도구는 복구용으로 보존했다.

‘설치됨’은 해당 기기의 `com.ssafy.eolssu.qa`를 ADB로 읽은 결과다. 일반 앱 `com.ssafy.eolssu`와 별개다. 조회 중/조회 실패와 실제 미설치를 구분한다. 기존 앱 데이터 보존은 `adb install -r`로 같은 패키지를 갱신하며 앱 삭제·데이터 초기화를 자동 실행하지 않는다는 의미다. 서명이 다르면 설치를 실패시키고 기존 앱을 유지한다. 이 동작을 자동 설치 약속으로 표시하지 않는다.

신규 MR의 작성자/제목도 EDO1의 `eolssu-qa-mr-sync.timer`가 약1분마다 GitLab 실제 API에서 갱신한다. Windows 미러는 약1분, 웹 목록은 약5초 간격으로 가져오므로 새 MR 반영에는 보통1~2분과 다운로드 시간이 더해진다. 제목을 나중에 바꾼 경우도 다음 수집 때 갱신된다. API 오류 시 기존 APK/정보를 유지하고 재시도한다. 미확보 정보는 ‘확인 중’으로 표시하며 커밋 작성자를 MR 작성자로 추정하지 않는다.

프로젝트 전용 `eolssu-qa-mr-read` 토큰은 **Reporter / read_api**, 실제 생성본 만료일은 **2027-07-31**이다. 기존 checkout 토큰을 수정하지 않았다. 원본은 EDO1 `/etc/eolssu/qa-gitlab-read-token`에 root:root/0600으로 보관하고, systemd LoadCredential이 실행 시에만 jenkins 사용자 서비스로 전달한다. 값은 명령 인수·환경변수·로그·저장소에 기록하지 않는다. 이 파일을 일반 사용자에게 읽을 수 있도록 바꾸지 않는다.

```bash
systemctl status eolssu-qa-mr-sync.timer --no-pager
systemctl show eolssu-qa-mr-sync.service -p Result -p ExecMainStatus
journalctl -u eolssu-qa-mr-sync.service -n 10 --no-pager
sudo systemctl start eolssu-qa-mr-sync.service
```

조회 실패가 지속되면 만료·회수 여부와 프로젝트 read_api 권한을 확인한다. 갱신은 GitLab에서 해당 프로젝트 읽기 전용 새 토큰을 발급하고, 승인된 숨김 입력으로 위 root 전용 파일을 교체한 뒤 단발 실행을 확인한다. 키를 채팅·셸 인수에 붙여넣지 않는다. 토큰 회수는 GitLab 프로젝트 Settings → Access tokens의 정확한 토큰을 확인해 운영자가 수행한다.

수집 서비스만 중지하려면 `sudo systemctl disable --now eolssu-qa-mr-sync.timer`를 사용한다. 필요 시 실행 중인 단발 서비스도 `sudo systemctl stop eolssu-qa-mr-sync.service`로 중지한다. APK 빌드·미러·기기 중계는 계속 동작하며 기존 정보는 남는다. 코드·unit 원본은 이 폴더의 `sync-mr-details.py`, `eolssu-qa-mr-sync.service/.timer`다. 초기 index 백업은 `/var/backups/qa498-mr-auto-20261105T032200Z/index.json`이다.

실제 검증: 2026-11-05 12:25:52 KST 태블릿,12:26:11 KST 휴대폰에 MR !333 / Android #96 / versionCode1793847399 / `qa-20261105.025639-85a9f6d0`를 두 페이지 버튼으로 설치했다. 태블릿에서 #95를 목록 선택해도 버튼은 #96을 설치했다. 설치 후 브라우저 새로고침에서 영상/설치 정보와 ‘최신 버전 사용 중’을 확인했다.

## 실제 설정·로그 위치

`%LOCALAPPDATA%\Packages\OpenAI.Codex_2p2nqsd0c76g0\LocalCache\Local\EolssuQA\relay`

- 설정: `supervisor.json` · 접속 키: `access-key.txt` (허용한 팀원에게만 별도 전달)
- 무선 검색 상태: `wireless-state.json` (`identity`: 고정한 기기 ID, `endpoint`: 확인한 주소, `status`: 연결/검색/최초 ID 대기 상태). 수동으로 supervisor.json의 serial을 바꾸면 이전 설정용 캐시는 사용하지 않고 새 명시 주소에서 최초 ID를 읽는다. 페어링이 해제되면 자동 재페어링하지 않는다.
- 무선 검색 오류: `wireless.stderr.log`. ADB mDNS 광고와 연결된 같은 IP의 TLS 기기에서만 후보를 찾으며 LAN 전체나 포트 전체를 탐색하지 않는다. 같은 기기 후보가 복수이거나 ID가 다르면 자동 전환을 보류한다.
- 2026-11-03 실제 확인: 최초 기기 ID 등록과, 오래된39191 캐시에서 ADB에 연결된44185를 찾아 정상 재시작하는 경로는 통과했다. 현재 LAN에서는 ADB mDNS 목록이 비어 있고 폰에 제한한 검색 질의도 응답이 없었다. 이후 포트 변경 시 새 주소가 ADB 연결 목록에도 나타나지 않으면 자동 검색되지 않을 수 있으며 위 수동 복구가 필요하다. 무선 디버깅 OFF→ON의 완전 무인 복구를 검증한 결과는 아니다.
- 로그: `supervisor.log`, `relay.stderr.log`, `tunnel.stderr.log`, `adb-connect.stderr.log`
- 복구 확인: **웹 접속 → 로그인 → 휴대폰 연결됨 → 영상 표시 → 제어권 요청 가능**

[세부 README](README.md) · [검증 기록](../../docs/plans/S15P31E102-498.md)
