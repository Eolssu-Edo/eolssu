# edo1 Jenkins 컨트롤러

작업: [S15P31E102-76](../../../docs/plans/S15P31E102-76.md). 운영 전환 여부와 실제 검증 결과는 작업 기록을 확인한다.

## 경로와 보존 범위

| 대상 | edo1 경로 |
| --- | --- |
| Compose·등록 스크립트·서비스 원본 | `/opt/eolssu/jenkins/` |
| 기존 Jenkins 홈 | `/var/lib/jenkins` → 컨트롤러의 같은 경로 |
| 호스트 빌드 에이전트·workspace·연결 secret | `/var/lib/jenkins-agent` |
| 같은 컨트롤러에서 받은 remoting JAR | `/usr/local/lib/eolssu-jenkins/agent.jar` |
| 호스트 에이전트 서비스 | `/etc/systemd/system/eolssu-jenkins-agent.service` |
| 웹 수동 배포 전용 에이전트·workspace·secret | `/var/lib/jenkins-web-agent` |
| 웹 수동 배포 전용 에이전트 서비스 | `/etc/systemd/system/eolssu-jenkins-web-agent.service` |
| 전환 전 백업 | `/opt/backups/eolssu-jenkins76-<시각>/` |

Jenkins는 기존 2.580.1과 Java 21을 사용한다. 공식 이미지 digest를 고정하고 호스트의 기존 `jenkins` UID/GID 111:113으로 실행한다. 다른 서버에서는 이 값을 먼저 확인한다. 홈 전체를 mount하므로 잡·사용자·Credentials 암호화 키·플러그인·빌드 이력을 그대로 사용한다. `COPY_REFERENCE_FILE_LOG`도 새 홈으로 지정해야 기본 이미지의 `/var/jenkins_home` 권한 오류를 피할 수 있다. 신규 홈을 자동 생성하지 않으며 기존 홈 소유권을 재귀 변경하지 않는다.

컨트롤러의 executor는 0개, 같은 edo1의 `edo1-host` 에이전트는 기존과 같은 1개다. 기존 Pipeline의 `agent any`·Git 인증·SCM polling·빌드·sudo 배포 명령은 유지한다. 에이전트는 기존 호스트 `jenkins` 사용자와 Docker 그룹·기존 sudoers를 사용한다. Java·Docker·Gradle 캐시·Chrome 등 호스트 빌드 도구도 계속 사용한다. 작업 디렉터리는 `/var/lib/jenkins-agent/workspace`로 바뀌며 과거 workspace는 삭제하지 않는다.

컨트롤러는 `127.0.0.1:8080`만 공개하고 인바운드 TCP agent 포트는 끈다. 에이전트는 이 주소에 WebSocket으로 연결한다. 컨테이너에는 Docker 소켓·운영 `.env`·배포 스크립트를 mount하지 않는다. 기존 호스트 에이전트의 Docker 접근과 제한된 sudo는 그대로이므로 이번 변경을 호스트 보안 격리 완료로 간주하지 않는다.

공식 이미지의 VOLUME 선언 때문에 사용하지 않는 기본 `/var/jenkins_home`에는 별도 Docker 볼륨이 보일 수 있다. 실제 데이터는 `JENKINS_HOME=/var/lib/jenkins`와 같은 경로의 bind mount에만 저장하며, 기본 경로를 데이터 백업 대상으로 혼동하지 않는다.

## 전환 절차

동일 서버에서 설정을 변경하는 다른 작업이 끝난 뒤 수행한다. 모든 관리 명령은 edo1에서 실행하며 비밀값을 출력하지 않는다. `/opt/eolssu/jenkins`에는 기존 root 전용 `admin-cli.auth`와 `jenkins-cli.jar`가 있으므로 디렉터리를 덮어쓰거나 권한을 넓히지 않는다.

1. `id jenkins`, 기존 버전, `systemctl cat jenkins`, 잡·노드·실행 중인 빌드·queue를 확인한다. 원본 executor가 1개인지 확인한다. Compose 이미지 다운로드·`config --quiet`, `systemd-analyze verify`를 먼저 끝낸다.
2. 기존 Jenkins CLI의 `quiet-down`으로 새 빌드 시작을 막는다. 기존 빌드가 끝나고 queue가 비었는지 확인한다. 설정·잡·Credentials·키·사용자·플러그인의 파일 SHA-256과 잡 정의·트리거·마지막 빌드 번호를 서버 전용 영수증에 기록한다. 앱 컨테이너 ID·환경 파일 해시·배포 영수증도 수집한다.
3. root 전용 0700 백업 디렉터리를 만들고 기존 `jenkins.service`를 중단한다. 중단 상태에서 `/var/lib/jenkins` 전체와 기존 systemd override·sudoers·관리 파일을 소유권·권한과 함께 보관한다. `tar -tf`와 아카이브 해시로 백업을 확인한다. 하나의 홈을 apt 서비스와 컨테이너가 동시에 열지 않도록 한다.
4. 이 폴더의 `compose.yml`, `register-agent.groovy`, 서비스 원본을 `/opt/eolssu/jenkins`에 root 소유로 설치한다. 관리 인증 파일은 보존한다. 기존 컨트롤러와 같은 버전의 `/jnlpJars/agent.jar`를 `/usr/local/lib/eolssu-jenkins/agent.jar`에 root 소유 0644로 설치한다.
5. 중단된 홈의 `config.xml`에서 `numExecutors`만 1에서 0으로 바꾼 뒤 아래 Compose로 기동한다. 재기동 시 quiet-down 유지 여부와 무관하게 호스트 도구가 없는 컨트롤러에서 빌드가 시작되지 않게 한다. 기존 관리자 인증으로 CLI가 연결되면 다시 `quiet-down`을 호출한다. 잡 정의·Credentials·키·사용자·플러그인 해시와 executor 외의 루트 설정을 백업과 비교한다. 자동 업그레이드나 새 플러그인 설치를 하지 않는다.
6. `register-agent.groovy`를 기존 관리자 CLI의 `groovy =`에 입력해 노드를 등록한다. 기존 노드와 충돌하면 중단하며 임의로 덮어쓰지 않는다. `Jenkins.get().getComputer('edo1-host').getJnlpMac()` 결과는 서버의 Python subprocess 등으로 메모리에서만 받아 `/var/lib/jenkins-agent/secret`에 jenkins 소유 0600으로 저장한다. 터미널·로그·명령 인자에 secret 값을 직접 출력하지 않는다.

   첫 inbound 에이전트 연결은 홈에 `secrets/jenkins.slaves.JnlpSlaveAgentProtocol.secret`을 생성할 수 있다. 등록 전에는 전체 설정 목록을 엄격하게 대조하고, 등록 후에는 기존 모든 파일의 해시·존재를 보존하면서 이 파일 하나의 신규 생성만 허용한다. 기존 Credentials·암호화 키의 변경이나 다른 새 설정 파일을 포괄적으로 허용하지 않는다.
7. 에이전트 디렉터리를 jenkins 소유로 만들고 서비스 파일을 설치해 `daemon-reload`, `enable --now eolssu-jenkins-agent`를 실행한다. 연결·executor 1개·실제 Java/Docker/Compose·기존 sudo 권한을 확인한다. 기존 `jenkins.service`는 disable하고 패키지와 백업은 복구를 위해 보존한다.
8. 컨트롤러·에이전트를 재기동하고 인증·노드 재연결·잡·트리거·과거 빌드 조회를 확인한다. `cancel-quiet-down` 후 기존 잡을 실행해 checkout·테스트·이미지·아티팩트·후보 검증·배포 영수증과 외부 HTTP/HTTPS 응답을 확인한다. 같은 SHA가 이미 배포됐으면 `already_deployed` 결과를 기록한다. 자동 연계 잡이 있으면 그 결과도 확인한다.

```sh
sudo docker compose -f /opt/eolssu/jenkins/compose.yml config --quiet
sudo docker compose -f /opt/eolssu/jenkins/compose.yml up -d --wait --wait-timeout 180
sudo docker compose -f /opt/eolssu/jenkins/compose.yml ps
sudo systemctl status eolssu-jenkins-agent --no-pager
sudo docker compose -f /opt/eolssu/jenkins/compose.yml logs --tail 50 controller
sudo journalctl -u eolssu-jenkins-agent --since today --no-pager
```

원시 빌드 로그·systemd 환경·Jenkins XML에는 비밀값이 포함될 수 있다. 공유 영수증에는 잡 이름·버전·해시 일치 여부·테스트 결과·배포 SHA만 남긴다. 서버 재부팅 시 Docker restart policy로 컨트롤러가 시작하고 에이전트 서비스는 연결을 재시도한다. 버전 업그레이드 시 remoting JAR도 같은 컨트롤러에서 갱신한다.

## 웹 수동 배포 전용 슬롯

작업: [S15P31E102-755](../../../docs/plans/S15P31E102-755.md). `eolssu-frontend-web-수동배포용`은 전용 노드 `edo1-web`에서 실행한다. 노드는 executor 1개, EXCLUSIVE 모드, 노드 이름과 같은 라벨만 받으므로 `agent any` 잡은 이 노드를 쓰지 않는다. 백엔드 잡이 `edo1-host`를 쓰는 동안에도 웹 잡이 대기열에서 기다리지 않는다. 실제 운영 배포는 기존 공유 lock과 백엔드 journal 상태 검사로 계속 한 번에 하나만 진행한다.

같은 서버의 `jenkins` 계정·Docker 그룹·sudoers·`~/.gradle` 캐시를 공유하고 작업 공간만 `/var/lib/jenkins-web-agent/workspace`로 분리한다. 전환 후 첫 웹 빌드는 새 작업 공간이라 Gradle 프로젝트 캐시·`wasm-opt` 결과가 없어 평소보다 오래 걸린다. 두 잡이 동시에 돌면 같은 CPU를 나눠 쓰므로 각 빌드 시간이 늘 수 있다.

적용 순서는 다음과 같다. secret 값은 터미널·로그·명령 인자에 출력하지 않는다.

1. 실행 중인 웹 빌드가 없는지 확인한다. [등록 파일](register-web-agent.groovy)을 기존 관리자 CLI의 `groovy =`에 입력한다. 같은 설정의 노드는 재사용하고, 다르면 덮어쓰지 않고 중단한다.
2. `/var/lib/jenkins-web-agent`를 `jenkins:jenkins` 소유 0750으로 만든다. `Jenkins.get().getComputer('edo1-web').getJnlpMac()` 결과를 CLI 출력에서 바로 `secret` 파일로 받아 jenkins 소유 0600으로 둔다.
3. [서비스 파일](eolssu-jenkins-web-agent.service)을 `/etc/systemd/system/`에 설치하고 `daemon-reload`, `enable --now eolssu-jenkins-web-agent`를 실행한다. `edo1-web`과 `edo1-host`가 모두 온라인인지 확인한다.
4. 웹 잡 설정의 Pipeline을 [frontend.Jenkinsfile](../frontend.Jenkinsfile)과 같게 고친다(`agent { label 'edo1-web' }`). Jenkins linter를 통과한 정의만 저장한다.
5. 현재 운영 SHA로 웹 잡을 실행해 `edo1-web`에서 checkout·브라우저 테스트·이미지·배포 결과(`already_deployed` 포함)를 확인한다.

되돌릴 때는 실행·예약 중인 웹 빌드가 없을 때 웹 잡의 Pipeline을 `agent any`로 되돌리고 `eolssu-jenkins-web-agent`를 중지한다. 노드 설정과 작업 공간은 보존한다.

## 웹 테스트 잡

작업: [S15P31E102-1270](https://ssafy.atlassian.net/browse/S15P31E102-1270). `eolssu-web-tests` 잡([web-tests.Jenkinsfile](../web-tests.Jenkinsfile))은 develop 커밋의 웹 브라우저 테스트(`:shared:wasmJsTest`·`:shared:jsTest`)만 돌리고 이미지를 만들거나 배포하지 않는다. 고른 근거는 [지식 문서](../../../docs/knowledge/infra/web-tests-ci.md)에 있다.

- `eolssu-backend`의 `Queue web tests` 단계가 웹 빌드 입력이 바뀐 커밋마다 `REVISION`을 넘겨 기다리지 않고 예약한다. 웹 빌드 입력은 `frontend/shared/src`의 common·web·js·wasmJs 소스와 테스트, Gradle 설정, `kotlin-js-store/`다. 비교 기준은 iOS QA 예약과 같은 직전 성공 커밋(`IOS_QA_BASE`)이다.
- 백엔드 검사·배포 단계보다 먼저 예약하므로 백엔드가 실패해도 웹 테스트는 돈다.
- 웹 수동 배포와 같은 `edo1-web` 노드(executor 1개)를 쓴다. 두 잡이 겹치면 나중 잡이 기다린다.
- 새 커밋이 예약되면 대기·실행 중인 이전 빌드를 중단한다(`abortPrevious`). 새 develop 커밋이 이전 변경을 모두 담기 때문이다.
- 결과는 잡의 테스트 결과(junit)에 남는다. 실패·UNSTABLE은 [Jenkins 실패 알림](../notifications/README.md)이 `eolssu-*` 잡으로 따로 설정 없이 Mattermost에 알린다. GitHub PR에는 표시되지 않는다.

### 적용 (develop 병합 뒤)

순서를 지킨다. 백엔드 잡을 먼저 바꾸면 `build job:`이 없는 잡을 불러 백엔드 잡이 실패한다. CLI 명령은 아래 'ai 배포 전용 슬롯'과 같다.

1. develop에 병합된 `web-tests.Jenkinsfile`을 edo1에 두고 linter·check를 통과한 뒤 apply한다.

   ```sh
   # 로컬 저장소 루트에서
   ssh -o ClearAllForwardings=yes edo1 'sudo -n tee /var/lib/jenkins/eolssu-web-tests.Jenkinsfile >/dev/null && sudo -n chown jenkins:jenkins /var/lib/jenkins/eolssu-web-tests.Jenkinsfile' < infra/jenkins/web-tests.Jenkinsfile
   # edo1에서
   C="sudo -n java -jar /opt/eolssu/jenkins/jenkins-cli.jar -s http://127.0.0.1:8080 -auth @/opt/eolssu/jenkins/admin-cli.auth"
   $C declarative-linter < /var/lib/jenkins/eolssu-web-tests.Jenkinsfile
   $C groovy = check < register-web-tests-job.groovy
   $C groovy = apply < register-web-tests-job.groovy
   ```

   [등록 파일](register-web-tests-job.groovy)은 실행·대기 중인 빌드가 있거나 `edo1-web` 노드·`eolssu-github` Credentials가 없으면 멈춘다. 기존 잡이 있으면 config.xml을 `/var/lib/jenkins/webtests1270-backup-<시각>.config.xml`로 백업하고 바꾼다.
2. 현재 develop 전체 SHA를 `REVISION`에 넣어 `eolssu-web-tests`를 한 번 수동 실행한다. 두 단계의 테스트 결과가 잡에 남는지 확인한다. 첫 실행은 작업 공간이 새로 생기지만, 같은 커밋을 웹 수동 배포 잡이 빌드한 적이 있으면 Gradle 빌드 캐시로 컴파일을 건너뛰어 몇 분 안에 끝난다(2026-11-24 154초, [실측](../../../docs/knowledge/infra/web-tests-ci.md#운영-적용-s15p31e102-1275)).
3. 실행·대기 중인 백엔드 빌드가 없을 때 `eolssu-backend` 잡의 config.xml과 서버 사본 `/opt/eolssu/config/backend.Jenkinsfile`을 root 전용 폴더에 백업한다. develop의 [backend.Jenkinsfile](../backend.Jenkinsfile)을 `declarative-linter`로 검사한 뒤 관리자 CLI `update-job`으로 저장하고, 서버 사본도 같은 내용으로 바꾼다. 저장할 config.xml은 `get-job eolssu-backend` 결과에서 `<script>` 안만 XML 이스케이프한 develop 파일로 바꿔 만든다. 저장 뒤 `get-job`을 다시 받아 만든 파일과 같은지 비교한다.
4. [일치 점검](../../drift/README.md)(`infra/drift/check.py --host edo1`)에서 `jenkins:eolssu-backend`, `jenkins:eolssu-web-tests`, `/opt/eolssu/config/backend.Jenkinsfile`이 '일치'인지 확인한다.
5. 웹 빌드 입력을 바꾼 다음 develop 커밋에서 백엔드 잡 로그에 `Web test diff:`가 찍히고 `eolssu-web-tests`가 같은 SHA로 예약·통과하는지 확인한다.

되돌릴 때는 실행·대기 중인 빌드가 없을 때 백업한 config.xml의 정의로 `eolssu-backend`를 다시 저장하고 서버 사본도 되돌린 뒤 `eolssu-web-tests`를 비활성화한다. 순서는 적용의 반대로, 백엔드 잡을 먼저 되돌린다.

## ai 배포 전용 슬롯

작업: [S15P31E102-244](../../../docs/plans/S15P31E102-244.md). `eolssu-ai` 잡([ai.Jenkinsfile](../ai.Jenkinsfile))은 전용 노드 `edo1-ai`에서 실행한다. 웹 슬롯과 같이 executor 1개, EXCLUSIVE, 노드 이름과 같은 라벨만 받는다. AI 이미지 빌드(수 분)가 백엔드 빌드를 막거나 기다리지 않는다. 운영 교체는 공유 배포 lock으로 한 번에 하나만 진행한다. 작업 공간은 `/var/lib/jenkins-ai-agent/workspace`다.

2026-11-15에 아래 1~4를 운영에 적용했다. secret 값은 터미널·로그·명령 인자에 출력하지 않는다. CLI 명령은 `sudo -n java -jar /opt/eolssu/jenkins/jenkins-cli.jar -s http://127.0.0.1:8080 -auth @/opt/eolssu/jenkins/admin-cli.auth groovy =`이다.

1. [등록 파일](register-ai-agent.groovy)을 CLI에 입력한다. 같은 설정이면 재사용하고, 다르면 덮어쓰지 않고 멈춘다.
2. `/var/lib/jenkins-ai-agent`를 `jenkins:jenkins` 0750으로 만든다. `println …getComputer('edo1-ai').getJnlpMac()`의 출력을 root 셸 안에서 개행을 지워 `secret`(jenkins 0600, 64자)으로 바로 저장한다. `print`는 CLI 출력이 비어 이 용도에 쓰지 않는다.
3. [서비스 파일](eolssu-jenkins-ai-agent.service)을 설치하고 `systemd-analyze verify`, `daemon-reload`, `enable --now eolssu-jenkins-ai-agent` 순서로 실행한 뒤, `edo1-ai`가 online인지 확인한다.
4. develop에 병합된 [ai.Jenkinsfile](../ai.Jenkinsfile)을 `declarative-linter`로 검사하고 `/var/lib/jenkins/eolssu-ai.Jenkinsfile`(jenkins 소유)에 둔다. [잡 등록 파일](register-ai-job.groovy)을 `check`로 확인한 뒤 `apply`로 저장한다. 기존 잡이 있으면 config.xml을 백업하고 바꾼다.

되돌릴 때는 실행·예약 중인 ai 빌드가 없을 때 `eolssu-ai` 잡을 비활성화하고 `eolssu-jenkins-ai-agent`를 중지한다. 노드 설정과 작업 공간은 보존한다. 운영 ai 이미지 되돌리기는 [운영 안내](../../runtime/README.md#ai-분석-서비스)를 따른다.

## omr·playback 이미지 잡

작업: [S15P31E102-951](../../../docs/plans/S15P31E102-951.md). `eolssu-omr`·`eolssu-playback` 잡은 같은 파일([service-image.Jenkinsfile](../service-image.Jenkinsfile))을 쓰고, 잡 이름에서 서비스를 정한다. 두 잡 모두 ai와 같은 노드 `edo1-ai`에서 실행한다. 새 노드를 만들지 않은 이유는 다음과 같다.

- 이미지 빌드·이미지 안 테스트는 CPU·메모리를 많이 쓴다(omr 빌드 최대 수 분, ai 실행 확인 6GiB). 4코어·15GiB 운영 서버에서 한 번에 하나만 돌려 운영 서비스 응답을 지킨다.
- 슬롯·secret·systemd 서비스를 더 만들지 않아 운영 변경이 줄어든다. 백엔드 빌드(`edo1-host`)와는 여전히 서로 기다리지 않는다.
- 대가: `ai/src`가 바뀐 커밋은 ai와 omr을 함께 다시 만들므로 두 잡이 차례로 돈다(나중 잡이 앞 잡의 빌드 시간만큼 늦게 반영된다).

2026-11-15에 아래 1~3을 운영에 적용했다(첫 배포 omr #1 6분 2초, playback #1 2분 11초 성공). CLI 명령은 위 'ai 배포 전용 슬롯'과 같다.

1. develop에 병합된 `service-image.Jenkinsfile`을 `declarative-linter`로 검사하고 `/var/lib/jenkins/eolssu-service-image.Jenkinsfile`(jenkins 소유)에 둔다.
2. [잡 등록 파일](register-service-image-jobs.groovy)을 `check`로 확인한 뒤 `apply`로 저장한다. 기존 잡이 있으면 config.xml을 백업하고 바꾼다.
3. 잡마다 **Build Now**로 한 번 실행한다. polling은 첫 실행 뒤에 켜진다.

되돌릴 때는 실행·예약 중인 빌드가 없을 때 두 잡을 비활성화한다. `edo1-ai` 노드는 ai 잡이 계속 쓰므로 멈추지 않는다. 운영 이미지 되돌리기는 [운영 안내](../../runtime/README.md#omrplayback-자동-배포-s15p31e102-951)를 따른다.

## Android 출시 잡

작업: [S15P31E102-1254](https://ssafy.atlassian.net/browse/S15P31E102-1254). `eolssu-android-release` 잡([android-release.Jenkinsfile](../android-release.Jenkinsfile))은 `edo1-host`에서 develop 커밋을 테스트·lint한 뒤 업로드 키로 서명한 AAB를 만들어 보관하고, 선택한 Google Play 트랙에 올린다. 업로드 도구는 [play-upload.py](../play-upload.py)이며 방식을 고른 근거는 [지식 문서](../../../docs/knowledge/infra/android-play-upload.md)에 있다.

### 실행

Jenkins → `eolssu-android-release` → **Build with Parameters**.

| 매개변수 | 값 |
| --- | --- |
| `REVISION` | develop에 속한 전체 40자 SHA |
| `VERSION_CODE` | Play에 올린 모든 번들보다 큰 정수 |
| `VERSION_NAME` | `1.4.0` 형식 |
| `PLAY_TRACK` | `production`(기본)·`internal`·`none`(AAB만 만들고 올리지 않음) |
| `PLAY_STATUS` | `draft`(기본): 초안으로 올리고 Play Console에서 사람이 검토 전송한다.<br>`completed`: 커밋과 함께 검토 전송한다. 승인되면 100% 출시된다. |
| `RELEASE_NOTES_KO` | ko-KR 출시 노트(500자 이하). 비우면 노트 없이 올린다. |

단계는 다음 순서로 진행한다.

1. 매개변수를 검사하고 REVISION을 체크아웃한다. 업로드를 고르면 그 커밋에 `play-upload.py`가 있어야 한다.
2. `Check Google Play access`: 서비스 계정으로 edit을 열어 트랙별 출시와 최고 versionCode를 로그에 남기고 edit을 지운다. `VERSION_CODE`가 최고값 이하이면 긴 빌드 전에 실패한다.
3. 테스트·lint·서명 AAB 빌드 후 `release-out/`에 AAB와 sha256을 보관한다.
4. `Upload to Google Play`: edit을 열어 번들을 올리고, Play가 읽은 versionCode·sha256을 확인한 뒤 트랙에 출시 하나(`<versionCode> (<versionName>)`)를 설정해 커밋한다. 커밋 전에 실패하면 edit을 지우므로 Play에는 아무것도 남지 않는다. 같은 번호를 다시 올릴 수 없으므로 이 단계는 재시도하지 않는다.

Play Console에 검토로 보내지 않은 다른 변경이 쌓여 있으면 Play가 자동 검토 전송을 거부한다. 이때 도구는 `changesNotSentForReview=true`로 다시 커밋하고, 로그에 `committed without sending for review`를 남긴다. 사람이 Play Console의 출시 개요에서 검토 전송을 해야 한다.

### Credentials

값은 `withCredentials` 안에서만 쓰고 출력하지 않는다.

| ID | 종류 | 내용 |
| --- | --- | --- |
| `eolssu-android-upload-keystore` | Secret file | 업로드 keystore(`eolssu-upload.jks`). alias는 첫 PrivateKeyEntry를 읽고, 키 비밀번호는 저장소 비밀번호와 같다고 가정한다. |
| `eolssu-android-upload-password` | Secret text | keystore 비밀번호 |
| `eolssu-play-service-account` | Secret file | Google Cloud 프로젝트 `eolssu-s15p31e102`의 서비스 계정 `eolssu-play-publisher` JSON 키 |

서비스 계정은 프로젝트 IAM 역할이 없다. Play Console(Team Edo) 사용자 및 권한에서 얼쑤 앱에만 `프로덕션으로 출시, 기기 제외, Play 앱 서명 사용`과 `앱을 테스트 트랙으로 출시` 권한을 받았다. 프로젝트에는 Google Play Android Developer API를 사용 설정했다. 키를 교체할 때는 새 키를 같은 Credentials ID로 덮어쓴 뒤 이전 키를 Google Cloud에서 삭제한다.

### 잡 정의 갱신

잡 정의의 원본은 이 저장소다. develop에 병합된 Jenkinsfile을 edo1에 두고 lint·check를 통과한 뒤 apply한다. CLI 명령은 위 'ai 배포 전용 슬롯'과 같다.

```sh
# 로컬 저장소 루트에서
ssh -o ClearAllForwardings=yes edo1 'sudo -n tee /var/lib/jenkins/eolssu-android-release.Jenkinsfile >/dev/null && sudo -n chown jenkins:jenkins /var/lib/jenkins/eolssu-android-release.Jenkinsfile' < infra/jenkins/android-release.Jenkinsfile
# edo1에서
C="sudo -n java -jar /opt/eolssu/jenkins/jenkins-cli.jar -s http://127.0.0.1:8080 -auth @/opt/eolssu/jenkins/admin-cli.auth"
$C declarative-linter < /var/lib/jenkins/eolssu-android-release.Jenkinsfile
$C groovy = check < register-android-release-job.groovy
$C groovy = apply < register-android-release-job.groovy
```

[등록 파일](register-android-release-job.groovy)은 실행·대기 중인 빌드가 있거나 Credentials가 빠졌으면 멈춘다. 기존 잡의 config.xml을 `/var/lib/jenkins/android1254-backup-<시각>.config.xml`로 백업하고 바꾼다. 선언형 매개변수는 실행 후에야 등록되므로 `ParametersDefinitionProperty`를 Jenkinsfile과 같은 순서로 직접 설정한다. 매개변수를 바꾸면 등록 파일의 목록도 함께 고친다. 운영 파일을 건드리지 않고 미리 검사할 때는 후보를 `/var/lib/jenkins` 아래 다른 이름으로 두고 `groovy = check <후보 경로>`로 실행한다.

2026-11-23 12:28 KST에 이 절차로 운영 잡을 갱신했다(정의 sha256 `fe47a078`, 이전 정의는 `android1254-previous.Jenkinsfile`과 `android1254-backup-2026-11-23T03-28-06…config.xml`에 보관). 병합 커밋 `17c7d6b66` 이전의 develop 커밋에는 `play-upload.py`가 없으므로 `PLAY_TRACK=none`으로만 실행할 수 있다.

되돌릴 때는 실행·대기 중인 빌드가 없을 때 백업한 config.xml의 정의로 잡을 다시 저장한다. 이미 커밋한 Play 출시는 Play Console에서 초안을 삭제하거나 출시를 중단한다.

## iOS 출시 잡

작업: [S15P31E102-1256](https://ssafy.atlassian.net/browse/S15P31E102-1256). `eolssu-ios-release` 잡([ios-release.Jenkinsfile](../ios-release.Jenkinsfile))은 은서 Mac(`mac-ios-eunseo`, label `ios-eunseo`)에서 develop 커밋으로 Release 아카이브와 App Store용 IPA를 만들어 보관하고, 고르면 App Store Connect에 업로드한다. 심사 제출은 하지 않는다. 서명·업로드 방식을 고른 근거는 [지식 문서](../../../docs/knowledge/infra/ios-app-store-release.md)에 있다. Mac 에이전트의 연결·전원 조건은 [iOS QA 문서](../../ios-qa/README.md#mac-에이전트)와 같다.

### 실행

실행 전에 Mac 담당자(은서)에게 알려 Mac을 전원에 연결하고 덮개를 연 채 로그인 상태로 두게 한다. 연결이 끊겨 있으면 실행은 대기열에서 기다린다.

Jenkins → `eolssu-ios-release` → **Build with Parameters**.

| 매개변수 | 값 |
| --- | --- |
| `REVISION` | develop에 속한 전체 40자 SHA |
| `MARKETING_VERSION` | `1.0.3` 형식. 이미 출시한 버전보다 커야 한다. |
| `CURRENT_PROJECT_VERSION` | App Store Connect에 올린 모든 빌드보다 큰 정수 |
| `UPLOAD_TO_APP_STORE_CONNECT` | 꺼짐(기본): IPA만 만들어 보관한다.<br>켜짐: 같은 아카이브를 App Store Connect에 업로드한다. |

두 버전 값은 `Config.xcconfig`의 값보다 우선한다. 출시한 뒤에는 저장소의 `Config.xcconfig`도 같은 값으로 올려 다음 번호를 맞춘다. 2026-11-23 기준 마지막 업로드는 1.0.2 빌드 8(출시됨)이므로 다음은 1.0.3 이상, 빌드 9 이상이다.

단계는 다음 순서로 진행한다.

1. `Build App Store IPA on Eunseo Mac`: 매개변수를 검사하고 REVISION을 체크아웃해 develop 포함 여부를 확인한다. Bundle ID `com.ssafy.eolssu`, 팀 `WDA26H33JA`로 Release 아카이브를 만들고(derived data `~/jenkins-agent/ios-release-derived`), `app-store-connect` 방식으로 IPA를 내보낸다. IPA의 Bundle ID·버전·빌드 번호·팀과 프로파일(기기 목록 없음, `get-task-allow=false`)을 검사한 뒤 `release-out/`의 IPA·sha256·dSYM zip·`revision.txt`를 보관한다. Mac이 잠들어 연결이 끊기면 이 단계만 최대 2회 다시 실행한다.
2. `Upload to App Store Connect`(업로드를 켰을 때만): 같은 Mac 작업 공간의 아카이브를 `destination=upload`로 다시 내보내 업로드한다. 같은 빌드 번호는 다시 올릴 수 없으므로 이 단계는 재시도하지 않는다. 업로드 뒤 App Store Connect에서 빌드 처리가 끝나면 TestFlight 배포와 심사 제출을 사람이 한다.

처음 쓰는 버전은 업로드를 끄고 실행해 IPA 검사 결과를 확인한 뒤, 같은 값으로 업로드를 켜서 다시 실행한다.

### Credentials

값은 `withCredentials` 안에서만 쓰고 출력하지 않는다.

| ID | 종류 | 내용 |
| --- | --- | --- |
| `eolssu-asc-api-key` | Secret file | App Store Connect 팀 API 키 `AuthKey_<KEY_ID>.p8` |
| `eolssu-asc-key-id` | Secret text | Key ID(영문 대문자·숫자 10자) |
| `eolssu-asc-issuer-id` | Secret text | Issuer ID(UUID) |

키는 클라우드 서명 인증서와 프로파일을 만들 수 있도록 **Admin** 역할 팀 키여야 한다. Mac의 `~/jenkins-agent/asc/` 키는 `eolssu-ios-qa` 전용으로 따로 둔다. 키를 교체할 때는 세 Credentials를 같은 ID로 덮어쓴 뒤 이전 키를 App Store Connect에서 폐기한다. 같은 키를 Mac 폴더에도 두었다면 그쪽도 함께 바꾼다.

### 잡 정의 갱신

잡 정의의 원본은 이 저장소다. 절차는 'Android 출시 잡'과 같다.

```sh
# 로컬 저장소 루트에서
ssh -o ClearAllForwardings=yes edo1 'sudo -n tee /var/lib/jenkins/eolssu-ios-release.Jenkinsfile >/dev/null && sudo -n chown jenkins:jenkins /var/lib/jenkins/eolssu-ios-release.Jenkinsfile' < infra/jenkins/ios-release.Jenkinsfile
# edo1에서
C="sudo -n java -jar /opt/eolssu/jenkins/jenkins-cli.jar -s http://127.0.0.1:8080 -auth @/opt/eolssu/jenkins/admin-cli.auth"
$C declarative-linter < /var/lib/jenkins/eolssu-ios-release.Jenkinsfile
$C groovy = check < register-ios-release-job.groovy
$C groovy = apply < register-ios-release-job.groovy
```

[등록 파일](register-ios-release-job.groovy)은 실행·대기 중인 빌드가 있거나 Credentials가 빠졌으면 멈춘다. 기존 잡의 config.xml을 `/var/lib/jenkins/ios1256-backup-<시각>.config.xml`로 백업하고 바꾼다. 매개변수 목록은 Jenkinsfile과 같은 순서로 직접 설정하므로, 매개변수를 바꾸면 등록 파일의 목록도 함께 고친다.

2026-11-23 14:26 KST에 이 절차로 운영 잡을 저장소 정의와 맞췄다(정의 sha256 `ba26ea10`, 직전 정의는 `ios1256-backup-2026-11-23T05-26-31…config.xml`에 보관). 같은 날 #1(develop `70c64b1a3`, 1.0.3 빌드 9, 업로드 끔)이 30분 만에 성공해 `Apple Distribution` 인증서와 App Store 프로파일로 서명된 IPA를 보관했다. 업로드 단계는 아직 실제로 실행하지 않았다.

되돌릴 때는 실행·대기 중인 빌드가 없을 때 백업한 config.xml의 정의로 잡을 다시 저장한다. 이미 업로드한 빌드는 App Store Connect에서 만료 처리한다. 같은 빌드 번호는 다시 쓸 수 없다.

## 복구

전환 검증에 실패하면 새 빌드를 막고 실행 중인 빌드가 끝난 뒤 에이전트와 컨트롤러를 중단한다. 먼저 현재 홈의 실패 시점 복사본을 별도로 보존한다. 기존 홈을 즉시 삭제하거나 백업을 그 위에 무분별하게 덮어쓰지 않는다.

- 전환 직후 아직 신규 빌드·설정 변경이 없다면 홈 전체를 전환 전 백업으로 복원하고 기존 서비스로 시작할 수 있다. 복원 전에 컨테이너와 agent가 모두 멈췄는지 확인한다.
- 신규 빌드가 생겼다면 최신 홈을 유지하고 `edo1-host` 노드를 제거한 뒤 executor를 원래 1개로 되돌리는 방식을 우선 검토한다. 기존 apt와 같은 Jenkins 버전이므로 전체 백업 복원으로 신규 이력을 잃을 필요가 없다.
- 기존 `jenkins.service`를 `enable --now`하고 관리자 인증·잡·트리거·빌드 가능 여부를 다시 확인한 뒤 quiet down을 해제한다. 앱·DB·모니터링 Compose와 볼륨은 내리지 않는다.

## 로그 회전 구성 비교

이 절차는 기존 Docker 컨트롤러의 로그 옵션만 바꾸는 S15P31E102-713에 사용한다. Jenkins 홈은 `/var/lib/jenkins`의 기존 bind mount를 계속 사용한다. 롤백은 직전 Compose로 컨트롤러만 복원하고 같은 홈으로 검증한다. 위의 apt 전환 복구와 작업 범위가 다르다.

1. 교체 전에 root 전용 증거 디렉터리에 설치된 Compose와 후보를 보관한다. 기존 경로를 프로젝트 디렉터리로 지정해 양쪽 설정을 렌더링한다. 비교 결과에는 달라진 Compose 경로만 기록한다. 렌더링한 환경 변수 값은 파일이나 보고서에 남기지 않는다. 적용·롤백 명령과 `--dry-run`의 대상은 모두 `controller` 한 개여야 한다.

   ```bash
   # edo1의 root 셸에서 경로를 실제 값으로 지정한다.
   set -e -o pipefail
   umask 077
   project=/opt/eolssu/jenkins
   verify=/path/to/infra/jenkins/controller/verify-rotation.py
   evidence=$(mktemp -d /var/backups/edo-docker-713/jenkins.XXXXXX)
   cp -p "$project/compose.yml" "$evidence/compose.before.yml"
   # 작성한 후보 파일을 "$evidence/compose.candidate.yml"에 보관한다.
   docker compose --project-directory "$project" -f "$evidence/compose.candidate.yml" config --quiet
   python3 -B "$verify" compose \
     <(docker compose --project-directory "$project" -f "$evidence/compose.before.yml" config --format json) \
     <(docker compose --project-directory "$project" -f "$evidence/compose.candidate.yml" config --format json) \
     > "$evidence/compose.diff.json"
   ```

2. 교체 직전 컨트롤러가 실행 중일 때 기준값을 남긴다. 같은 방식으로 새 컨트롤러가 실행 중일 때 후보의 실제 값을 남긴다. 전체 `docker inspect`는 파이프로만 전달한다. 기록에는 비교 대상인 이미지·마운트·포트·네트워크·종료 유예·보안·로그 설정이 들어간다. 환경은 `JENKINS_HOME` 경로 일치 여부만 기록한다.

   ```bash
   capture_controller() {
     local security
     security=$(docker info --format '{{json .SecurityOptions}}') || return
     docker inspect --type container eolssu-jenkins-controller-1 |
       python3 -B "$verify" capture --security-options "$security" > "$evidence/$1.runtime.json"
   }
   capture_controller before
   ```

   실제 교체와 준비 확인 뒤 후보를 캡처한다.

   ```bash
   capture_controller candidate
   python3 -B "$verify" compare apply "$evidence/before.runtime.json" "$evidence/candidate.runtime.json" \
     > "$evidence/apply.diff.json"
   ```

3. `observed`에 기준·후보 값, `unexpected`에 기대와 다른 필드·값이 기록된다. 종료 코드 `0`은 통과, `1`은 구성 차이, `2`는 입력 오류다. 적용에서 새 ID와 `json-file`의 `10m`·`3`이 필요하다. SELinux가 비활성이고 실제 Jenkins 홈과 같은 익명 로컬 볼륨이 유지됐을 때 `/var/jenkins_home`의 `Mode`가 빈 값에서 `z`로 바뀌는 경우만 허용한다. 이 차이도 `observed`에 남는다. 다른 필드 차이 또는 입력 오류가 있으면 빌드 시작 차단을 유지하고 Jenkins만 직전 설정으로 복원한다. 복구 후 `capture_controller rollback`을 실행하고 같은 기준 파일에 `compare rollback`으로 비교한 결과를 `rollback.diff.json`에 기록한다.

4. 적용과 롤백 모두 빌드·대기열, 기존 잡 설정과 빌드 이력, 연결돼 있던 에이전트 복귀 및 다른 운영 컨테이너의 ID를 별도로 확인한다. `quiet-down`은 재기동 뒤 다시 설정한다. 재기동을 넘겨 빌드 시작을 막으려면 대기열과 실행 중 빌드가 모두 비었을 때 기존 활성 잡만 잠시 비활성화하고, 검증을 마친 뒤 원래 활성 상태를 복원하고 `quiet-down`을 해제한다.

## 근거

- [Jenkins 공식 Docker 이미지와 홈·UID 설정](https://github.com/jenkinsci/docker/blob/master/README.md)
- [Jenkins 컨트롤러 executor와 에이전트](https://www.jenkins.io/doc/book/using/using-agents/)
- [Remoting inbound agent와 secret 파일 인자](https://github.com/jenkinsci/remoting/blob/master/docs/inbound-agent.md)
- [Compose 5.6.1의 익명 볼륨 상속](https://github.com/docker/compose/blob/v5.6.1/pkg/compose/create.go#L1053-L1077)
