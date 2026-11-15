# 서버 설치본·Jenkins 잡 정의와 저장소 일치 점검 (S15P31E102-943)

서버의 관리 파일·배포 도구·systemd 유닛과 Jenkins inline Pipeline은 MR 병합만으로 서버에 반영되지 않습니다([운영 서버 문서](../runtime/README.md)). 이 도구는 [manifest.json](manifest.json)에 적힌 서버 경로와 저장소 원본을 **읽기 전용**으로 비교합니다.

```sh
python3 -I -B infra/drift/check.py            # edo1·edo2 전체
python3 -I -B infra/drift/check.py --host edo2
python3 -B -m unittest discover -s infra/drift/tests -v
```

- 서버에서는 `sudo -n sha256sum`과 Jenkins 잡 `config.xml`의 `sudo -n cat`만 실행합니다. 파일을 쓰거나 서비스를 다시 시작하지 않습니다.
- 결과는 `일치`·`불일치`·`서버 없음`과 manifest의 메모입니다. 하나라도 일치하지 않으면 종료 코드 1, 접속 실패는 2입니다.
- 비교 방식: 기본은 바이트 단위입니다. `render`는 템플릿 치환 뒤(`__TLS_DOMAIN__` 등) 비교하고, `compare: text`는 CRLF와 끝 빈 줄을 무시합니다. inline Jenkins 잡은 줄 끝을 무시하고 비교합니다.
- 제외 대상
  - 비밀값·키·인증서·상태 파일: `.env`, credentials, `backend-route/active.conf`, `deployment.json` 등
  - 설치기가 문자열로 만드는 백업 유닛: [backup/install.py](../backup/install.py). 2026-11-15에 13개 모두 일치를 따로 확인했습니다.
  - 블로그 에이전트 유닛: `origin/blog`의 `scripts/jenkins/`에서 관리합니다.
- 서버에 새 관리 파일을 설치하거나 경로를 바꾸면 manifest에도 한 줄을 추가합니다. 남은 차이와 조치는 [S15P31E102-943 기록](../../docs/plans/S15P31E102-943.md)을 따릅니다.
