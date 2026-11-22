# 자동 보안 업데이트와 Jenkins 에이전트 (S15P31E102-758 · 1229)

edo1은 매일 06:00~07:00 UTC(15:00~16:00 KST)에 자동 보안 업데이트(`apt-daily-upgrade.timer`, unattended-upgrades)를 설치합니다. 설치가 끝나면 needrestart가 바뀐 라이브러리·Java를 쓰는 서비스를 찾아 바로 재시작합니다. Jenkins 에이전트는 빌드를 직접 실행하므로 재시작되면 진행 중인 빌드·배포가 끊깁니다.

- 2026-11-11 06:33 UTC: OpenJDK 21 업데이트 뒤 에이전트 3개가 재시작돼 웹 수동 배포 #10이 wasm-opt 단계에서 끊겼습니다([S15P31E102-755](../../docs/plans/S15P31E102-755.md)).
- 2026-11-13 06:51 UTC에도 같은 방식으로 에이전트 3개가 재시작됐습니다.
- 2026-11-20 06:58 UTC: 758 설정으로 에이전트 재시작은 미뤄졌지만, 이후 아무도 재시작하지 않았습니다. 예전 JVM이 교체된 `jspawnhelper`를 실행하지 못해 checkout의 `git init`부터 실패했고, 11-22 08:46 UTC에 재시작할 때까지 backend·ai·omr·playback 잡이 1분마다 실패했습니다([S15P31E102-1229](../../docs/plans/S15P31E102-1229.md)).

두 설정을 함께 씁니다.

| 파일 | 설치 위치 | 역할 |
| --- | --- | --- |
| [50-eolssu-jenkins-agents.conf](50-eolssu-jenkins-agents.conf) | `/etc/needrestart/conf.d/` | 에이전트 4개(`eolssu-jenkins-agent`·`-ai-agent`·`-blog-agent`·`-web-agent`)의 자동 재시작을 미룹니다. JDK 외 라이브러리(glibc·OpenSSL 등) 업데이트에도 빌드가 끊기지 않습니다. |
| [51eolssu-jenkins-jdk](51eolssu-jenkins-jdk) | `/etc/apt/apt.conf.d/` | `openjdk-` 패키지를 자동 업데이트에서 뺍니다. 디스크의 JDK가 실행 중인 에이전트 JVM과 어긋나지 않게 합니다. |

다른 보안 업데이트의 자동 설치는 그대로 유지합니다. 대신 **JDK 보안 업데이트는 자동으로 설치되지 않으므로 사람이 챙겨야 합니다**([JDK 업데이트하기](#jdk-업데이트하기-사람이-할-일)). Jenkins 컨트롤러는 Docker 컨테이너 안의 자체 JDK를 쓰므로 호스트 Java 업데이트의 영향을 받지 않습니다. 실패 알림 타이머(`eolssu-jenkins-notify`)는 짧게 실행되고 끝나므로 대상이 아닙니다.

## 설치

edo1에서 기존 파일이 없는지 확인한 뒤 설치합니다. 서비스는 재시작되지 않습니다.

```sh
test ! -e /etc/needrestart/conf.d/50-eolssu-jenkins-agents.conf
sudo install -o root -g root -m 0644 50-eolssu-jenkins-agents.conf /etc/needrestart/conf.d/
test ! -e /etc/apt/apt.conf.d/51eolssu-jenkins-jdk
sudo install -o root -g root -m 0644 51eolssu-jenkins-jdk /etc/apt/apt.conf.d/
```

## 확인

[check-override.pl](check-override.pl)은 실제 설정(`needrestart.conf`와 `conf.d/*.conf`)을 읽어 needrestart 3.6과 같은 방식으로 서비스별 판정을 출력합니다. 서비스를 재시작하지 않습니다.

```sh
sudo perl check-override.pl eolssu-jenkins-agent.service eolssu-jenkins-ai-agent.service \
  eolssu-jenkins-blog-agent.service eolssu-jenkins-web-agent.service ssh.service
# 에이전트 4개는 defer, ssh.service는 restart여야 한다.
sudo needrestart -r l -v 2>&1 | grep -E 'eval|Error'   # conf.d 파일을 읽고 Error가 없어야 한다.
apt-config dump | grep -A1 'Package-Blacklist'          # "openjdk-" 항목이 있어야 한다.
python3 -I -B infra/drift/check.py --host edo1          # 로컬에서 서버 설치본과 저장소 원본 비교
```

자동 업데이트 로그(`/var/log/unattended-upgrades/unattended-upgrades-dpkg.log`)에서 에이전트는 `Restarting services...`가 아니라 `Service restarts being deferred:` 아래에 있어야 합니다.

## JDK 업데이트하기 (사람이 할 일)

JDK는 자동으로 올라가지 않습니다. 정기 점검 때 업데이트할 JDK가 있는지 확인하고, **실행 중인 빌드가 없을 때** 올린 뒤 **바로 에이전트를 재시작**합니다. JDK만 올리고 재시작하지 않으면 11-20처럼 모든 빌드가 checkout 단계에서 실패합니다. 발표 동결 중에는 하지 않습니다([릴리스 절차](../release/README.md#동결-창)).

```sh
apt list --upgradable 2>/dev/null | grep openjdk                # 출력이 있으면 업데이트할 JDK가 있다
# Jenkins 화면에서 edo1-host·edo1-ai·edo1-web·edo1-blog 노드에 실행 중인 빌드가 없는지 확인한 뒤
sudo apt-get install --only-upgrade 'openjdk-21-*'
sudo systemctl restart eolssu-jenkins-agent eolssu-jenkins-ai-agent eolssu-jenkins-blog-agent eolssu-jenkins-web-agent
systemctl is-active eolssu-jenkins-agent eolssu-jenkins-ai-agent eolssu-jenkins-blog-agent eolssu-jenkins-web-agent
```

JDK 외 라이브러리 업데이트로 재시작이 미뤄진 경우에도 같은 방식으로 빌드가 없을 때 재시작합니다. `sudo needrestart -b | grep 'NEEDRESTART-SVC: eolssu-jenkins'`에 출력이 있으면 재시작이 필요합니다.

재시작 뒤 Jenkins의 노드 화면에서 `edo1-host`·`edo1-ai`·`edo1-web`·`edo1-blog`가 다시 온라인인지 확인하고, 다음 빌드의 checkout이 통과하는지 봅니다.

## 증상과 복구

빌드 로그에 아래 오류가 보이면 에이전트 JVM과 디스크의 JDK가 어긋난 상태입니다. 위 명령으로 에이전트 4개를 재시작하면 복구됩니다.

```
Cannot run program "git" ... Failed to exec spawn helper ... JDK version mismatch
```

## 되돌리기

- `sudo rm /etc/apt/apt.conf.d/51eolssu-jenkins-jdk`로 지우면 다음 자동 업데이트부터 JDK도 자동으로 올라갑니다. 이때는 업데이트 뒤 에이전트를 직접 재시작해야 합니다.
- `sudo rm /etc/needrestart/conf.d/50-eolssu-jenkins-agents.conf`로 지우면 다음 자동 업데이트부터 이전처럼 에이전트를 바로 재시작합니다. 지울 때도 서비스는 재시작되지 않습니다.
