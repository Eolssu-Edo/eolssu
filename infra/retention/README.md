# edo1 배포 산출물 보존 정리

작업: [S15P31E102-921](../../docs/plans/S15P31E102-921.md). 측정 근거, 보호 규칙 선택 이유, 운영 적용 순서는 작업 기록을 따른다.

`retention.py`는 edo1의 `/opt/eolssu/releases/<SHA>`, `eolssu-backend:ci-<SHA>`·`eolssu-frontend:ci-<SHA>`·`eolssu-ai:ci-<SHA>` 이미지 태그, 72시간 지난 빌드 캐시를 정리한다. ai 태그를 지워도 `eolssu-ai:runtime`·`eolssu-ai:rollback`이 가리키는 이미지는 이름표만 빠지고 남는다([S15P31E102-244](../../docs/plans/S15P31E102-244.md)). `eolssu-omr:ci-<SHA>`·`eolssu-playback:ci-<SHA>`는 날짜와 관계없이 최근 3개만 남긴다. `ai/src`·`data/` 입력이 바뀔 때마다 다시 만들고 omr은 빌드마다 약 1.5GB가 새로 생기며, 되돌리기는 ci 태그가 아니라 이미지 ID와 `:rollback` 태그를 쓰기 때문이다([S15P31E102-951](../../docs/plans/S15P31E102-951.md)). 다른 이미지·컨테이너·볼륨과 SHA 형식이 아닌 디렉터리는 건드리지 않는다.

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

`--below-free-gib N`을 주면 `/`의 여유가 N GiB 미만일 때만 정리한다. 여유가 충분하면 lock과 Docker를 건드리지 않고 `skipped`만 출력한다.

edo1에는 `eolssu-retention.service`·`.timer`가 설치·활성화돼 있다. 매시 50분(KST)에 `--apply --below-free-gib 10`을 실행하며, 결과는 `journalctl -u eolssu-retention`의 JSON 한 줄로 확인한다. 끌 때는 `sudo systemctl disable --now eolssu-retention.timer`를 실행한다.

테스트: `python3 -m unittest discover -s infra/retention/tests`
