# 자동 보안 업데이트 뒤 Jenkins 에이전트 재시작 미루기 (S15P31E102-758)

edo1은 매일 06:00~07:00 UTC(15:00~16:00 KST)에 자동 보안 업데이트(`apt-daily-upgrade.timer`, unattended-upgrades)를 설치합니다. 설치가 끝나면 needrestart가 바뀐 라이브러리·Java를 쓰는 서비스를 찾아 바로 재시작합니다. Jenkins 에이전트는 빌드를 직접 실행하므로 재시작되면 진행 중인 빌드·배포가 끊깁니다.

- 2026-11-11 06:33 UTC: OpenJDK 21 업데이트 뒤 에이전트 3개가 재시작돼 웹 수동 배포 #10이 wasm-opt 단계에서 끊겼습니다([S15P31E102-755](../../docs/plans/S15P31E102-755.md)).
- 2026-11-13 06:51 UTC에도 같은 방식으로 에이전트 3개가 재시작됐습니다.

[50-eolssu-jenkins-agents.conf](50-eolssu-jenkins-agents.conf)는 에이전트 4개(`eolssu-jenkins-agent`·`-ai-agent`·`-blog-agent`·`-web-agent`)의 자동 재시작을 미룹니다. 보안 업데이트 설치는 그대로 유지합니다. Jenkins 컨트롤러는 Docker 컨테이너라 호스트 Java 업데이트의 영향을 받지 않습니다. 실패 알림 타이머(`eolssu-jenkins-notify`)는 짧게 실행되고 끝나므로 대상이 아닙니다.

## 설치

edo1에서 기존 파일이 없는지 확인한 뒤 설치합니다. 서비스는 재시작되지 않습니다.

```sh
test ! -e /etc/needrestart/conf.d/50-eolssu-jenkins-agents.conf
sudo install -o root -g root -m 0644 50-eolssu-jenkins-agents.conf /etc/needrestart/conf.d/
```

## 확인

[check-override.pl](check-override.pl)은 실제 설정(`needrestart.conf`와 `conf.d/*.conf`)을 읽어 needrestart 3.6과 같은 방식으로 서비스별 판정을 출력합니다. 서비스를 재시작하지 않습니다.

```sh
sudo perl check-override.pl eolssu-jenkins-agent.service eolssu-jenkins-ai-agent.service \
  eolssu-jenkins-blog-agent.service eolssu-jenkins-web-agent.service ssh.service
# 에이전트 4개는 defer, ssh.service는 restart여야 한다.
sudo needrestart -r l -v 2>&1 | grep -E 'eval|Error'   # conf.d 파일을 읽고 Error가 없어야 한다.
python3 -I -B infra/drift/check.py --host edo1          # 로컬에서 서버 설치본과 저장소 원본 비교
```

자동 업데이트가 실제로 Java를 올린 날에는 `/var/log/unattended-upgrades/unattended-upgrades-dpkg.log`에서 에이전트가 `Restarting services...`가 아니라 `Service restarts being deferred:` 아래에 있어야 합니다.

## 새 Java를 에이전트에 적용하기 (사람이 할 일)

재시작을 미룬 에이전트는 이전 Java로 계속 실행됩니다. 아래 명령으로 재시작이 필요한지 확인하고, **실행 중인 빌드가 없을 때** 재시작합니다. 발표 동결 중에는 하지 않습니다([릴리스 절차](../release/README.md#동결-창)).

```sh
sudo needrestart -b | grep 'NEEDRESTART-SVC: eolssu-jenkins'   # 출력이 있으면 재시작 필요
# Jenkins 화면에서 해당 노드(edo1-host·edo1-ai·edo1-web)와 블로그 잡에 실행 중인 빌드가 없는지 확인한 뒤
sudo systemctl restart eolssu-jenkins-agent eolssu-jenkins-ai-agent eolssu-jenkins-blog-agent eolssu-jenkins-web-agent
systemctl is-active eolssu-jenkins-agent eolssu-jenkins-ai-agent eolssu-jenkins-blog-agent eolssu-jenkins-web-agent
```

재시작 뒤 Jenkins의 노드 화면에서 `edo1-host`·`edo1-ai`·`edo1-web`·블로그 노드가 다시 온라인인지 확인합니다.

## 되돌리기

`sudo rm /etc/needrestart/conf.d/50-eolssu-jenkins-agents.conf`로 지우면 다음 자동 업데이트부터 이전처럼 에이전트를 바로 재시작합니다. 지울 때도 서비스는 재시작되지 않습니다.
