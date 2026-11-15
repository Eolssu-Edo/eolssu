# Python 테스트 CI 연결 (S15P31E102-945)

2026-11-15 기준 develop(`6ebd0b8f`)의 Python 테스트는 대부분 CI에서 돌지 않았다. 운영 Jenkins는 backend 잡에서 infra 테스트 파일 5개(배포 이미지·배포 도구·인증서·Nginx 2개, Nginx 3개는 Docker 필요)와 웹 잡에서 1개만 실행한다. 이 문서는 새 잡 `eolssu-python-tests`의 선택 규칙과 로컬 실측, 격리한 테스트의 근거를 남긴다. 운영 적용 절차는 [운영 문서](../../../infra/runtime/README.md#python-테스트-잡-s15p31e102-945)를 따른다.

## 영역별 로컬 실측

macOS(arm64), Python 3.12.13(uv 관리), uv 0.13.6, 실제 폴더를 `TMPDIR`로 사용했다. 표의 수치는 Push 전 develop `924e9b68` 위로 다시 맞춘 코드에서 잰 값이다. 서버 실측이 아니며, 운영 Jenkins의 첫 실행에서 다시 잰다.

| 묶음 | 실행 방식 | 결과 | 시간(초) |
| --- | --- | --- | ---: |
| infra(15개 폴더)와 `backend/tools` | 호스트 `python3` unittest | 457건 실행(이 작업의 15건, `backend/tools` 6건 포함), 실패 0, 건너뜀 24(Docker·OpenSSL 23, boto3 1) | 29 |
| catalog | data 가상환경 unittest | 135건 실행, 실패 0, 건너뜀 24(폐기용 MySQL 미지정) | 5 |
| data(3개 폴더) | data 가상환경 pytest | 520건 통과, 실패 0, 격리 2건 제외 | 41 |
| ai | `ai/uv.lock` 고정 환경 pytest | 524건 통과, 실패 0, 건너뜀 47(jeongganbo-generator·Node·OpenCV 필요), 격리 17건 제외 | 49 |
| 격리 단계 | data·ai 격리 테스트만 | data 2건 실패, ai 16건 실패·1건 통과(빌드 결과에는 영향 없음) | 3 |

시간은 `python-tests.py run <묶음>`을 새 작업 폴더에서 실행한 벽시계 시간이고, 가상환경 준비(uv 캐시 재사용)를 포함한다. 네 묶음을 모두 실행해도 약 2분 30초다. uv 캐시가 빈 상태의 ai 첫 준비는 11초였다.

## 문서 명령과 달라진 점

- **data는 unittest가 아니라 pytest로 실행한다.** `data/README.md`의 `python -m unittest discover -s data/tests`는 `test_scan_register.py`·`test_scan_register_cli.py`처럼 pytest 함수로 쓴 파일을 수집하지 못한다. pytest는 unittest 테스트도 함께 수집하므로 범위가 넓어진다. 이 두 파일은 OpenCV·pdfplumber가 필요해 [고정 의존성](../../../infra/jenkins/python-tests-requirements.txt)에 `ai/omr-scan-requirements.txt`의 같은 버전을 넣었다.
- **catalog는 문서대로 unittest로 실행한다.** 테스트가 같은 폴더의 다른 테스트 모듈을 이름으로 가져와 pytest의 수집 방식과 맞지 않는다.
- **macOS에서는 실제 폴더를 `TMPDIR`로 써야 한다.** `/var`가 심볼릭 링크라 `infra/storage`의 대상 경로 검사 2건과 data 재생 테스트 2건이 실패했다. 실행 도구는 항상 작업 폴더 안의 `tmp`를 `TMPDIR`로 지정한다.

## 선택 규칙

직전 성공 실행이 검사한 SHA부터 HEAD까지 바뀐 경로로 묶음을 고른다. 테스트가 다른 영역의 파일을 읽으므로 읽는 쪽도 함께 실행한다.

| 바뀐 경로 | 실행 묶음 | 이유 |
| --- | --- | --- |
| `ai/` | ai | |
| `data/` | data, ai, catalog | ai 테스트가 `data/src/scores`·`data/reference`·`data/tests/fixtures`를, catalog 테스트가 `data/src/scores`·`data/annotations`를 읽는다 |
| `infra/catalog/` | catalog, data | data 테스트가 `infra/catalog`의 교정 계획·`enable_scoring.py`를 읽는다 |
| 그 밖의 `infra/` | infra | |
| `backend/src/main/resources/db/migration/` | infra, catalog | 배포 도구·catalog 테스트가 마이그레이션 SQL을 읽는다 |
| `backend/tools/` | infra | 이 폴더의 unittest(악보 4박 보정 도구)를 infra 묶음에서 실행한다 |
| `frontend/shared/.../feature/guide/` | data | data 재생 테스트가 `Sigimsae.kt`의 시김새 이름 수를 센다 |
| `docs/knowledge/data/` | catalog | 음원 대응표 근거 문서 이름을 검사한다 |
| 나머지 frontend·backend, blog·docs·루트 문서와 설정 예시 | 없음 | Python 테스트가 읽지 않는다 |
| 이 잡의 파일, `ai/.python-version`, 목록에 없는 경로 | 전체 | 놓치지 않는 쪽을 택한다 |

직전 성공 실행이 없거나 그 SHA가 HEAD의 조상이 아니면 전체를 실행한다. 실패한 실행 뒤에는 마지막 성공 이후의 변경 전체를 다시 검사한다. `FULL_RUN` 매개변수로 언제든 전체를 실행할 수 있고, 매주 월요일 새벽 타이머 실행은 항상 전체를 검사한다. data 고정 의존성은 직접 쓰는 패키지만 고정해 하위 패키지와 uv의 Python 3.12 패치 버전이 바뀔 수 있으므로, 오래 선택되지 않은 묶음의 환경 변화를 이 주간 실행으로 드러낸다.

## 격리한 테스트

아래 테스트는 바뀐 코드가 아니라 CI 환경 때문에 실패한다. 통과 판정에서는 제외하고, 별도 `Quarantined tests` 단계에서 실행해 결과를 남긴다. 이 단계는 실패해도 빌드를 실패로 만들지 않는다. 원인을 고치면 [목록](../../../infra/jenkins/python-tests.py)에서 지운다.

| 테스트 | 원인 | 고칠 방향(담당 영역 확인 필요) |
| --- | --- | --- |
| `ai/tests/unit/test_staff_rhythm_preservation.py` (15건 실패) | jeongganbo-generator 클론(`JGB_GENERATOR_DIR`)과 Node가 필요한데, 같은 조건의 다른 OMR 테스트와 달리 skip 표시가 없다 | 같은 `skipif` 표시를 붙이거나 OMR 검사 이미지에서만 실행 |
| `ai/tests/unit/test_onset_spread_experiment.py::test_experiment_rejects_wrong_recording_hash` | `ai/evaluation/dtw_performance.py`가 해시 검사보다 Windows 전용 메모리 제한을 먼저 실행해 Linux·macOS에서 다른 오류로 끝난다 | 해시 검사를 먼저 하거나 Windows 밖에서는 건너뜀 |
| `data/tests/test_scan_register_cli.py` | 정간보 OMR 모델 실행 환경(torch 등)이 필요하다. [지식 문서](../ai/jeongganbo-double-water-register.md)의 검사 이미지 안에서 실행하는 테스트다 | OMR 이미지 검사로 분리 |

## 운영 실행 방식의 선택

- **backend 잡 뒤에 실행한다.** edo1의 빌드 executor는 1개다. 이 잡도 develop을 polling하면 같은 변경에서 backend 배포보다 먼저 잡힐 수 있으므로, `upstream` 트리거로 backend 잡이 성공·불안정·실패로 끝난 뒤 실행한다(중단·미실행 뒤에는 예약되지 않고 다음 실행이 마지막 성공부터 다시 검사한다). backend 잡에는 경로 필터가 없어 모든 develop 변경 뒤에 한 번씩 실행된다. 다만 이 잡이 도는 동안 새 develop 변경이 들어오면 그 backend 배포는 이 잡이 끝날 때까지(로컬 실측 기준 약 2분 30초, 첫 실행은 내려받기만큼 더) 기다린다.
- **MR 단계 검사가 아니라 develop 병합 후 검사다.** 기존 Jenkins는 GitLab MR 파이프라인 없이 develop을 polling한다. 병합 전 검사는 이 구조를 바꾸는 별도 결정이 필요하다.
- **ai 배포 게이트와 분리한다.** ai 이미지 빌드 전 pytest 게이트는 244(AI·OMR·재생 이미지 배포)가 맡는다. 이 잡은 변경 경로 기반 검사만 한다.
- **실패 알림은 기존 서비스를 그대로 쓴다.** [Jenkins 실패 알림](../../../infra/jenkins/notifications/README.md)이 `eolssu-*` 잡을 모두 읽으므로 설정을 바꾸지 않는다.

## 미확인

- 운영 Jenkins 사용자의 `python3` 버전과 `uv` 설치 여부. 확인 전에는 적용하지 않는다.
- edo1에 ffmpeg가 PATH에 있으면 `data/src/audio`·재생 인코더 테스트가 그 버전으로 실행된다. 로컬은 Homebrew ffmpeg로 통과했고, 서버 버전에서의 결과는 첫 실행에서 확인한다.
- edo1에서의 실제 소요 시간과 첫 실행의 Python·패키지 내려받기 시간.
- Jenkins Pipeline linter 결과. 로컬에 Jenkins가 없어 문법은 다른 관리 Jenkinsfile과 같은 구성만 사용했다.
