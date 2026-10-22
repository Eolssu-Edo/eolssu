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
| 전환 전 백업 | `/opt/backups/eolssu-jenkins76-<시각>/` |

Jenkins는 기존 2.580.1과 Java 21을 사용한다. 공식 이미지 digest를 고정하고 호스트의 기존 `jenkins` UID/GID 111:113으로 실행한다. 다른 서버에서는 이 값을 먼저 확인한다. 홈 전체를 mount하므로 잡·사용자·Credentials 암호화 키·플러그인·빌드 이력을 그대로 사용한다. `COPY_REFERENCE_FILE_LOG`도 새 홈으로 지정해야 기본 이미지의 `/var/jenkins_home` 권한 오류를 피할 수 있다. 신규 홈을 자동 생성하지 않으며 기존 홈 소유권을 재귀 변경하지 않는다.

컨트롤러의 executor는 0개, 같은 edo1의 `edo1-host` 에이전트는 기존과 같은 1개다. 기존 Pipeline의 `agent any`·Git 인증·SCM polling·빌드·sudo 배포 명령은 유지한다. 에이전트는 기존 호스트 `jenkins` 사용자와 Docker 그룹·기존 sudoers를 사용한다. Java·Docker·Gradle 캐시·Chrome 등 호스트 빌드 도구도 계속 사용한다. 작업 디렉터리는 `/var/lib/jenkins-agent/workspace`로 바뀌며 과거 workspace는 삭제하지 않는다.

컨트롤러는 `127.0.0.1:8080`만 공개하고 인바운드 TCP agent 포트는 끈다. 에이전트는 이 주소에 WebSocket으로 연결한다. 컨테이너에는 Docker 소켓·운영 `.env`·배포 스크립트를 mount하지 않는다. 기존 호스트 에이전트의 Docker 접근과 제한된 sudo는 그대로이므로 이번 변경을 호스트 보안 격리 완료로 간주하지 않는다.

## 전환 절차

동일 서버에서 설정을 변경하는 다른 작업이 끝난 뒤 수행한다. 모든 관리 명령은 edo1에서 실행하며 비밀값을 출력하지 않는다. `/opt/eolssu/jenkins`에는 기존 root 전용 `admin-cli.auth`와 `jenkins-cli.jar`가 있으므로 디렉터리를 덮어쓰거나 권한을 넓히지 않는다.

1. `id jenkins`, 기존 버전, `systemctl cat jenkins`, 잡·노드·실행 중인 빌드·queue를 확인한다. 원본 executor가 1개인지 확인한다. Compose 이미지 다운로드·`config --quiet`, `systemd-analyze verify`를 먼저 끝낸다.
2. 기존 Jenkins CLI의 `quiet-down`으로 새 빌드 시작을 막는다. 기존 빌드가 끝나고 queue가 비었는지 확인한다. 설정·잡·Credentials·키·사용자·플러그인의 파일 SHA-256과 잡 정의·트리거·마지막 빌드 번호를 서버 전용 영수증에 기록한다. 앱 컨테이너 ID·환경 파일 해시·배포 영수증도 수집한다.
3. root 전용 0700 백업 디렉터리를 만들고 기존 `jenkins.service`를 중단한다. 중단 상태에서 `/var/lib/jenkins` 전체와 기존 systemd override·sudoers·관리 파일을 소유권·권한과 함께 보관한다. `tar -tf`와 아카이브 해시로 백업을 확인한다. 하나의 홈을 apt 서비스와 컨테이너가 동시에 열지 않도록 한다.
4. 이 폴더의 `compose.yml`, `register-agent.groovy`, 서비스 원본을 `/opt/eolssu/jenkins`에 root 소유로 설치한다. 관리 인증 파일은 보존한다. 기존 컨트롤러와 같은 버전의 `/jnlpJars/agent.jar`를 `/usr/local/lib/eolssu-jenkins/agent.jar`에 root 소유 0644로 설치한다.
5. 중단된 홈의 `config.xml`에서 `numExecutors`만 1에서 0으로 바꾼 뒤 아래 Compose로 기동한다. 재기동 시 quiet-down 유지 여부와 무관하게 호스트 도구가 없는 컨트롤러에서 빌드가 시작되지 않게 한다. 기존 관리자 인증으로 CLI가 연결되면 다시 `quiet-down`을 호출한다. 잡 정의·Credentials·키·사용자·플러그인 해시와 executor 외의 루트 설정을 백업과 비교한다. 자동 업그레이드나 새 플러그인 설치를 하지 않는다.
6. `register-agent.groovy`를 기존 관리자 CLI의 `groovy =`에 입력해 노드를 등록한다. 기존 노드와 충돌하면 중단하며 임의로 덮어쓰지 않는다. `Jenkins.get().getComputer('edo1-host').getJnlpMac()` 결과는 서버의 Python subprocess 등으로 메모리에서만 받아 `/var/lib/jenkins-agent/secret`에 jenkins 소유 0600으로 저장한다. 터미널·로그·명령 인자에 secret 값을 직접 출력하지 않는다.
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

## 복구

전환 검증에 실패하면 새 빌드를 막고 실행 중인 빌드가 끝난 뒤 에이전트와 컨트롤러를 중단한다. 먼저 현재 홈의 실패 시점 복사본을 별도로 보존한다. 기존 홈을 즉시 삭제하거나 백업을 그 위에 무분별하게 덮어쓰지 않는다.

- 전환 직후 아직 신규 빌드·설정 변경이 없다면 홈 전체를 전환 전 백업으로 복원하고 기존 서비스로 시작할 수 있다. 복원 전에 컨테이너와 agent가 모두 멈췄는지 확인한다.
- 신규 빌드가 생겼다면 최신 홈을 유지하고 `edo1-host` 노드를 제거한 뒤 executor를 원래 1개로 되돌리는 방식을 우선 검토한다. 기존 apt와 같은 Jenkins 버전이므로 전체 백업 복원으로 신규 이력을 잃을 필요가 없다.
- 기존 `jenkins.service`를 `enable --now`하고 관리자 인증·잡·트리거·빌드 가능 여부를 다시 확인한 뒤 quiet down을 해제한다. 앱·DB·모니터링 Compose와 볼륨은 내리지 않는다.

## 근거

- [Jenkins 공식 Docker 이미지와 홈·UID 설정](https://github.com/jenkinsci/docker/blob/master/README.md)
- [Jenkins 컨트롤러 executor와 에이전트](https://www.jenkins.io/doc/book/using/using-agents/)
- [Remoting inbound agent와 secret 파일 인자](https://github.com/jenkinsci/remoting/blob/master/docs/inbound-agent.md)
