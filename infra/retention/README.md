# edo1 배포 산출물 보존 정리

작업: [S15P31E102-921](../../docs/plans/S15P31E102-921.md). 측정 근거, 보호 규칙 선택 이유, 운영 적용 순서는 작업 기록을 따른다.

`retention.py`는 edo1의 `/opt/eolssu/releases/<SHA>`, `eolssu-backend:ci-<SHA>`·`eolssu-frontend:ci-<SHA>` 이미지 태그, 72시간 지난 빌드 캐시를 정리한다. 다른 이미지·컨테이너·볼륨과 SHA 형식이 아닌 디렉터리는 건드리지 않는다.

| 보호 대상 | 근거 |
| --- | --- |
| `.env`의 `BACKEND_JAR`·`WEB_DIST_DIR` | 현재 서비스 중인 릴리스 |
| `deployment.json`·`frontend-deployment.json`의 현재·직전 | 롤백과 config 백업이 읽는다 |
| 컨테이너 bind 원본, 컨테이너가 쓰는 이미지 | 실행 중이거나 보관 중인 컨테이너 |
| 최근 SHA 20개, 7일 이내 | 최근 배포를 SHA로 재배포 |

배포 상태가 `deployed`가 아니면 실패하고 아무것도 지우지 않는다.

```sh
sudo install -o root -g root -m 0755 infra/retention/retention.py /opt/eolssu/bin/eolssu-retention
sudo /opt/eolssu/bin/eolssu-retention            # 계획만 출력
sudo /opt/eolssu/bin/eolssu-retention --apply    # 승인 후 정리
```

자동 실행은 `eolssu-retention.service`·`.timer`(매일 04:50 KST)를 `/etc/systemd/system/`에 설치하고 `systemctl daemon-reload`한다. `enable --now`는 별도로 승인받는다. 결과는 `journalctl -u eolssu-retention`의 JSON 한 줄로 확인한다.

테스트: `python3 -m unittest discover -s infra/retention/tests`
