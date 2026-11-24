# Jenkins 실패 알림 운영

[요구사항](../../../docs/specs/jenkins-failure-notifications.md) · [진단 근거](../../../docs/knowledge/infra/jenkins-failure-notifications.md) · [509 실행 기록](../../../docs/plans/S15P31E102-509.md)

edo1의 독립 systemd 서비스가 Jenkins 완료 결과를 읽고 기존 Mattermost `e102-cicd` webhook으로 알린다. Jenkins·앱 재시작이나 Jenkinsfile 변경은 필요 없다. Python 3 표준 라이브러리만 사용한다.

| 구성 | 운영 위치 / 동작 |
| --- | --- |
| 프로그램 | `/opt/eolssu/jenkins-notify/notify.py`, root 소유 644 |
| 스케줄 | `eolssu-jenkins-notify.timer`, 각 실행 종료 후 30초, 부팅 시 자동 시작 |
| Jenkins 인증 | 기존 `/opt/eolssu/jenkins/admin-cli.auth`를 `LoadCredential`로 읽음 |
| Mattermost 인증 | `/etc/eolssu-jenkins-notify/webhook-url`, root:root 600, 상위 디렉터리 700 |
| 상태 | `/var/lib/eolssu-jenkins-notify/state.json`, DynamicUser 전용 디렉터리 700·파일 600 |
| 대상 | 최상위 `eolssu-*` 잡(한글 이름 포함, [952](../../../docs/plans/S15P31E102-952.md)), 잡당 최근 100개 빌드; 현재 backend/웹 수동 배포(`eolssu-frontend-web-수동배포용`)/android-qa/ios-qa/blog/ai([244](../../../docs/plans/S15P31E102-244.md))/omr/playback([951](../../../docs/plans/S15P31E102-951.md)) (graphify 잡은 [S15P31E102-508](../../../docs/plans/S15P31E102-508.md)에서 제거), 등록 후 python-tests([945](../../../docs/plans/S15P31E102-945.md))·web-tests([1270](../controller/README.md#웹-테스트-잡))도 별도 설정 없이 포함 |

FAILURE·UNSTABLE과 로그에서 제한시간 초과가 확인된 ABORTED를 알린다. 정상·수동 중단은 제외한다. 최초 시작은 완료된 과거 빌드를 기준점으로 삼고 진행 중 빌드는 계속 추적한다. 운영 중 처음 보이는 잡(새 잡이나 대상 조건에 새로 들어온 잡)은 직전 감지 전에 이미 끝난 빌드를 건너뛰고, 그 뒤에 끝나는 빌드부터 알린다. 동시에 실행한 이전 빌드가 늦게 끝나도 알린다. 알림 처리에 걸리는 시간과 API 응답 시간은 30초 감지 주기에 추가된다. 장기 중단 중 잡당 100개를 넘긴 빌드의 누락 복구는 별도 확인이 필요하다.

메시지는 실행 계기·변경 작성자, 잡/번호·실패 단계·SHA, 확인된 원인, 조치 표 하나와 **오류 로그 최대 3줄·합계 300자**다. 원인을 판정할 근거가 없으면 미확정, 로그를 못 읽으면 조회 실패라고 표시하고 실패 알림은 보낸다. 작성자를 원인 제공자로 단정하지 않는다. 로그의 인증 값·URL·이메일·호출 멘션을 마스킹한다.

전체 로그 링크는 기존 SSH 터널 `127.0.0.1:18080`을 사용한다. 로그 앞의 L번호는 표시된 단계 발췌 안의 상대 행 번호다. 병렬·중첩 단계나 단계 메타데이터가 없는 빌드는 전체 Console Output 확인이 필요하다.

## 점검과 복구

다음 명령은 edo1에서 실행한다. 인증 파일 내용이나 webhook URL을 터미널에 출력하지 않는다.

```sh
sudo systemctl status eolssu-jenkins-notify.timer --no-pager
sudo systemctl show eolssu-jenkins-notify.service -p Result -p ExecMainStatus
sudo journalctl -u eolssu-jenkins-notify.service -n 30 --no-pager
sudo systemctl list-timers eolssu-jenkins-notify.timer --no-pager
```

`sent`는 Mattermost HTTP 200 응답을 받은 상태다. `detail_failed`는 다음 주기에 Jenkins 상세 조회를 재시도한다. `uncertain`은 전송 시간 초과·오류 또는 전송 중 서비스 종료로 수신 여부가 불명확한 상태다. **중복 전송을 막기 위해 uncertain을 자동 재전송하지 않는다.** 채널에서 해당 잡·빌드 번호를 먼저 검색한다. 실제 미수신이 확인된 경우에만 타이머를 중지하고 해당 delivery 기록의 수동 복구 여부를 결정한다. 전체 상태 파일을 삭제하면 기준점과 중복 방지가 초기화되므로 삭제하지 않는다. 전송 결과와 API 조회 오류는 인증 값 없이 journal에 남는다.

롤백은 `sudo systemctl disable --now eolssu-jenkins-notify.timer`로 새 감지를 중지하고, 실행 중이면 `sudo systemctl stop eolssu-jenkins-notify.service`를 수행한다. Jenkins와 배포 잡은 그대로 동작한다. 상태와 자격 증명은 보존한다.

프로그램을 갱신할 때는 타이머와 실행 중 서비스를 중지하고 root 소유 프로그램만 교체한 뒤 `sudo systemctl start eolssu-jenkins-notify.service`의 결과를 확인하고 타이머를 다시 활성화한다. unit 변경은 `systemd-analyze verify`와 `systemctl daemon-reload`도 수행한다. `--preview JOB#번호`는 전송 없이 표시하며 systemd credential 환경에서 실행해야 한다. `--send-replay JOB#번호`는 실제 메시지를 보내므로 운영 검증을 명시적으로 요청한 경우에만 사용한다. 검증 문구가 붙고 같은 번호는 재전송하지 않는다.

## 검증

저장소 루트에서 실행한다. 실제 알림 전송이나 앱·DB 실행은 하지 않는다.

```sh
python3 -B -m unittest discover -s infra/jenkins/notifications -q
python3 -B -m unittest discover -s infra/jenkins/tests -q
```

알림 테스트는 길이·마스킹·원인 미확정·실패 단계 구분·로그 조회 실패·초기 기준점·동시 완료·수동 중단/시간 초과·모호한 전송의 중복 방지를 검증한다. 기존 배포 테스트는 로컬 HTTP 서버를 사용하므로 로컬 소켓을 허용한 환경이 필요하다.
