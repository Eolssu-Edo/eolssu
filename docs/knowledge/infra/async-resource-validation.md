# 악보 변환·음원 생성의 큐·자원·복구 검증

[S15P31E102-954](https://ssafy.atlassian.net/browse/S15P31E102-954), 담당 박찬형, Story 328. [실행 계획과 체크리스트](../../plans/S15P31E102-954.md). 2026-11-15 사용자가 계획 실행·재현 결함 수정·재검증·독립 리뷰·로컬 커밋을 승인했다. 아래는 실행 증거이며 계획의 모든 행을 통과 처리하는 목록이 아니다.

## 기준과 환경

- 배포 조회 시점: 2026-11-15 20:32 KST. Android QA APK **#394**, `20261115T111645Z-394-ff65c430`, package `com.ssafy.eolssu.qa`, versionCode `1794741356`. 내려받은 파일과 배포 메타데이터의 APK SHA-256은 `546f4f170495031d53068136e8ccaf8f3ee5e9366b32443115b5b0be26e4161d`로 일치한다.
- 기준 소스 `ff65c4308759ff597616efee6770cb34c52b71ae`. 배포 backend/OMR/AI revision은 이 소스다. playback revision은 `44efe083`이고 빌드 입력 해시 `3f7d544e46d91b0cdb3b0b9e7d966039b797f0932e2c49401970b81fb0e63326`가 기준 소스와 같다. 서버는 읽기 전용으로 조회했다. 로컬 수정·검증이 배포 반영을 뜻하지 않는다.
- 폐기 MySQL 8.4.13: 앱 DB `eolssu_qa954`, DB 테스트는 별도 `eolssu_test`. 실제 HTTP 로그인용 격리 계정 4개. 44개 마이그레이션, 실제 `ops` 스케줄러, 로컬 MinIO 버킷, 실제 CAT 자산을 사용했다. 운영 DB·버킷·메일에는 연결하지 않았다.
- 로컬 한도: backend 2 CPU/2 GiB(JVM 768 MiB), playback 1 CPU/1 GiB/스왑 없음, OMR 2 CPU/2 GiB(메모리+스왑 4 GiB), MySQL 2 CPU/1.5 GiB, MinIO 1 CPU/384 MiB. tmpfs는 backend/playback 각 512 MiB, OMR 1 GiB, DB 768 MiB, MinIO 128 MiB다. 임시 볼륨은 컨테이너 정지 시 보존되지 않으므로 재기동 때 버킷·자료 상태를 확인한다.
- playback의 Ubuntu 26 빌드는 호스트 메모리 부족과 같은 시점에 Docker EOF로 중단됐다. 실행은 기존 Ubuntu 24 OMR 이미지에 FFmpeg/mido/jsonschema와 기준 playback 소스를 넣은 **번역 환경**이다. OMR도 기존 이미지의 의존성에 기준 소스를 읽기 전용으로 붙였다. 운영 이미지와 동일한 용량·지연을 검증했다고 해석하지 않는다.
- 원시 증거는 Git 밖 `C:\higher\qa-artifacts\S15P31E102-954`에 있다. 계정·비밀값 파일은 공개하지 않는다. 입력은 지정된 `C:\Users\kingc\Downloads\악보 추가 편집 테스트 자료` 원본을 읽었으며 원본·사본·M4A·APK를 커밋하지 않는다. `inputs.json`은 13개 PDF의 실제 쪽 수·크기·SHA-256을 기록한다.

## 재현한 결함과 수정

### D1. 저장 직후 파일을 읽지 못해도 재생 준비 완료로 표시

- 재현: 내 악보 다운로드에서 저장소가 `Saved`를 반환한 직후 `get(song)`이 null/예외가 되게 한다. 다시 버튼을 누르고 파일 조회가 복구되는 순서까지 실행한다.
- 기대: [702 기기 다운로드 계약](../../specs/sheet-audio.md#연습-화면에서-기기로-다운로드)에 따라 유효한 현재 판본 파일을 얻었을 때 재생 버튼을 제공한다. 실패 중에는 안내·재시도가 가능해야 한다.
- 실제/영향: 파일이 없는데 `Ready`가 되어 재시도 제어가 사라졌다. 핵심 다운로드→재생 흐름을 막는다.
- 수정: `PracticeViewModel`은 저장 후 조회한 실제 파일이 있을 때만 `Ready`로 전환한다. 실패는 기존 실패 상태와 재시도 흐름으로 보낸다. 세션·취소 가드는 유지한다.
- 동일 재현: `red-corrected.log` 18개 중 1개 실패(`Failed` 기대/`Ready` 실제) → `green-retry.log` 18개 통과. 재시도에서 이미 저장된 파일을 재사용하고 추가 생성/저장을 하지 않는 것까지 확인했다. 파일 저장 동시 요청 2개→HTTP/저장 1회, metadata 게시 실패→이전 유효 파일 보존과 다음 저장 복구도 통과했다.
- 영향 회귀: Android host 전체 1,814개 중 **1,809 통과, 5 환경 생략**, 실패/오류 0. 실제 기기 디스크 부족·OS 파일 읽기 실패 주입을 실행한 것은 아니다. 증거 `fe-focused/`. 독립 변경 리뷰는 명세·규칙·품질에서 실질 지적 없음.

### D2. 생성기 결과 오류와 종료한 프로세스의 하위 작업 미정리

- 재현: renderer의 `result.json`을 잘못된 JSON/UTF-8 또는 배열/null/문자열로 만든다. 별도 renderer는 자식을 띄운 뒤 exit 7로 먼저 끝나거나 제한 시간을 넘긴다. 이후 정상 요청을 보낸다.
- 기대: [생성기 내부 계약](../../specs/sheet-audio.md#내부-계약-초안)의 서버 실패 응답, 제한 시간 종료, 임시 디렉터리·실행 자원 반환, 다음 요청 처리.
- 실제/영향: 내부 결과 오류가 잘못된 입력 400 또는 연결 종료로 나타났다. Linux에서 부모가 먼저 끝난 경우 하위 프로세스가 계속 실행됐다. PID 1이 Python이면 종료한 손자 프로세스가 zombie로 남기도 했다.
- 수정: 내부 결과 형식 오류를 `500 SERVER_ERROR`/`no-store`로 응답한다. Linux process group을 성공·실패 종료 경로에서 정리하고 Compose playback에 `init: true`를 두어 고아 종료 프로세스를 회수한다. 입력 계약·슬롯·시간 제한은 바꾸지 않는다.
- Windows에서도 원래 Store Python venv 재현 명령의 부모 선종료 때 자식·임시 폴더가 남았다. 프로세스 생성 시 Win32 Job에 연결하고 Store launcher의 별도 프로세스 이탈을 막아 같은 Python 환경에서 전체 트리의 종료를 기다리도록 수정했다. interpreter 교체로 통과시키지 않았다.
- 동일 재현: `playback/recovery-final-baseline-red.log` 이후 `process-probe-linux-init-fixed.log`에서 timeout/crash 후 하위 PID·zombie·임시 파일이 남지 않고 다음 요청이 200이다. Windows 원래 명령도 timeout 504/crash 500, 하위 작업·임시 폴더 정리, 다음 요청 200이다(`process-probe-windows-native-job.log`). Store venv와 python.org Python 3.12의 대상 회귀는 각각 14개 통과했다. 최종 Linux 전체는 **273개 통과/Windows 전용 2개 생략**이다(`linux-after-windows-final.log`).
- 독립 리뷰에서 제품·Compose 변경의 필수 지적 없음. 작성자가 아닌 검토자가 Store Python에서 자식 3개의 timeout/crash도 실행해 정리를 확인했다. 정확한 명령·플랫폼 API 근거·검사 파일은 `playback/windows-final-receipt.json`, 추가 실행은 `windows-review-three-descendants.log`에 있다. Windows 10 1703 이전은 미검증이다.

### D3. 만료 파일 하나의 삭제 실패가 다른 파일 정리를 중단

- 재현: MySQL에서 서로 다른 회원의 완료 음원 두 개를 10분 만료시킨다(고정 시계). 첫 객체 삭제만 실패하도록 저장소를 설정하고 실제 `cleanup` 경로를 호출한다. 이후 삭제를 복구하고 정상 생성을 실행한다.
- 기대: [임시 M4A 계약](../../specs/sheet-audio.md#임시-m4a-다운로드와-기기-재생-s15p31e102-684)의 만료 링크 차단·삭제. 실패 객체는 재시도 장부에 남기고 독립적인 다른 만료 객체는 정리해야 한다.
- 실제/영향: 첫 실패에서 반복문이 `break`되어 두 번째 만료 파일까지 남았다. 같은 선두 객체가 계속 실패하면 다른 사용자의 임시 파일도 축적될 수 있다. 만료 링크 차단 자체는 통과했다.
- 수정: 삭제 실패에서 배치를 중단하던 `break`만 제거했다. 실패 객체의 RETIRED 장부는 보존하며 나머지 객체 정리를 진행한다.
- 증거: `backend-cleanup/red-retry.log`, `red.xml`: 1개 실제 실행/1개 실패, 객체 1개 기대/2개 실제 → 같은 재현 통과. 기본 검사 775개 통과/자료 미설정 1개 생략, DB 두 클래스 46개 통과/실제 CAT 조건부 2개 생략 후 준비된 CAT로 두 검사를 별도 실행해 2개 통과했다. `clean build`·JAR 생성 성공. 독립 리뷰에서 추가 수정 지적 없음. 앞선 DB 연결·디스크 공간 실패는 제품 결함 재현에 포함하지 않았다.

### D4. PDFium 잠금 대기와 Audiveris 실패 로그 정리

- 재현: 실제 PDFium lock을 다른 요청이 보유한 상태에서 0.05초 작업 예산으로 오선보 읽기를 시작한다. 별도 검사에서는 Audiveris timeout을 주입하고 로그·실행 슬롯·다음 요청을 관찰한다.
- 기대: [OMR 시간 예산](../../specs/score-recognition.md)에 모든 엔진 대기를 포함하고 timeout/실패에서도 임시 원본·로그와 실행 슬롯을 정리한다.
- 실제/영향: 0.2초 뒤에도 PDFium 대기가 끝나지 않고 Audiveris 슬롯을 잡았다. lock을 풀어야 TimeoutError가 났다. Audiveris timeout의 로그는 다음 정상 요청 전까지 남았다. 후속 변환 지연과 실패 자료 잔류를 유발한다.
- 수정: 기존 absolute deadline의 남은 시간으로 PDFium lock을 획득한다. 획득한 lock만 해제하며 PDF document close를 먼저 보장한다. Audiveris 로그 정리를 `finally`로 옮기고 로그 정리 오류에도 실행 lock은 해제한다. API·보안 정책·timeout 값은 유지한다.
- 재검증: `omr-fix/red.log` 2개 실패 → `green.log` 2개 통과. 원래 Linux probe에서도 제한 시간 내 반환/Audiveris lock 해제/timeout 로그 삭제를 확인했다. 관련 6개 파일 회귀 209개 통과/2개 실제 PDF 미지정 생략 후 지정 AuClair PDF로 그 2개를 별도 실행해 모두 통과했다. 실제 PDFium·Node/HTTP 경계를 실행했으며 이 회귀의 Audiveris 출력은 대역이다. 독립 리뷰에서 추가 수정 지적 없음.
- 기존 API 로그 검사 두 개가 baseline에서도 capture 연결 부족으로 실패했다. 기존 로그 assertion을 유지하고 명시적으로 실제 logger의 capture handler를 연결해 검사를 복구했다. 실패를 skip하거나 기대 로그를 제거하지 않았다.

### D5. 변환 중 기본 음원 요청을 입력 오류로 응답

- 실제 HTTP 재현: PDF를 접수하고 SHT-10에서 RUNNING을 확인한 뒤 SHT-12에 `{}`를 보낸다. `400 INVALID_REQUEST`가 반환됐다(`import-races/events.json`).
- 기대: [SHT-12 표](../../specs/sheet-audio-app-flow.md#서버-계약-요약)의 악기 생략 허용·변환 중 `409 STATE_CONFLICT`. 잘못된 입력과 아직 변환 중인 상태를 구분해야 한다.
- 원인/영향: 아직 `scoreJson`이 없는 상태에서 기본 악기 조회가 먼저 실패해 상태 검사에 도달하지 못했다. 유효한 요청인데 클라이언트가 입력 오류로 안내할 수 있다.
- 수정: 기본 악기 조회에 필요한 확정 악보가 없으면 기존 `STATE_CONFLICT`로 분류한다. 잘못된 악기 ID 형식/저장 악기는 400, 존재하지 않는 유효 형식 ID는 404를 유지한다. QUEUED/RUNNING의 악기 생략/지정 요청 모두 409이며 음원 행·객체를 생성하지 않는 회귀를 추가했다.
- 재검증: 새 회귀의 409 기대/400 실제 실패 → 최신 BE 기본 775개 통과/자료 1개 생략, 음원 DB 40개 통과/CAT 조건부 2개 생략, `clean build`·JAR 생성 성공. 원래 실제 HTTP 절차도 `import-races-green`에서 통과했다. 최신 JAR로 backend 재시작 복구와 실제 CAT HTTP 생성·전체 AAC decode도 별도 통과했다(`backend-restart`, `android-local/proxy-verify`). 독립 리뷰의 필수 지적 없음.

## 실행 장부

각 행의 통과는 아래 관찰 경계에 한정한다. 실제 음악의 정답률·실기기 청감·운영 최대 용량을 뜻하지 않는다.

- 실제 CAT HTTP 단일/2명/4명: `audio-1`, `audio-2`, `audio-4`에서 각각 1/2/4건 모두 자동 worker의 DONE→서명 다운로드→SHA-256/크기 확인→시험 악보 삭제를 완료했다. 마지막 다운로드는 각각 약 8.2/10.9/18.7초. 2·4명 회차 DB 샘플의 최대 동시 RUNNING은 1이었다. 표본·공유 호스트 간섭 때문에 지연 목표나 용량을 결론내리지 않는다.
- 초기 자원 관찰 스크립트가 cgroup v2 경로를 사용했으나 로컬 Docker는 v1이었다. 따라서 위 초기 회차의 메모리 peak/OOM 계측은 **무효**다. DB 상태 관찰과 HTTP 결과는 별개이며, 경로·오류 검사를 고쳐 아래 `audio-final-*` 등에서 재측정했다.
- Linux 느린 응답 대역: 15 MiB 두 응답을 겹쳐 3회 수행했다. 연결 종료 후 handler 2→0, 자식·임시 파일 정리를 확인했다. RSS 25.5→59.1→61.7 MiB는 allocator 잔류와 양립하며 원래 메모리로 돌아왔거나 누수가 없다고 단정하지 않는다. 실제 CAT 최대 길이 동시 생성 실험은 아니다.
- 실행 도구 검사는 제품 검증과 별개다. `sheet_jobs.py`는 현재 ID/판본, 비어 있지 않은 악보/검토 초안, 수행표의 원본/음원 해시 연결, 다운로드 전체 바이트를 대조하고 로컬 주소의 redirect를 따르지 않는다.

| 실제 실행 | 결과와 경계 | 증거 폴더 |
| --- | --- | --- |
| 오선보 AuClair 1쪽 | 접수→QUEUED→RUNNING→DONE→초안 조회. 44음/44검토 항목, 검토 필요. 약 17.3초 | `staff-1c` |
| 정간보 동동 1쪽 | 악기 3/시험 선택 장단 1로 접수→DONE→악보 조회. 95음/45검토 항목, 검토 필요. 약 12.2초. 선택 장단을 원곡 정답으로 확정하지 않는다. | `jgb-1` |
| 수정 후 오선보 2명/4명 | 2/2·4/4 DONE, 최대 RUNNING 1. DB 최종 accepted/start/finish를 대조해 FIFO·비중첩 확인. 4건의 실제 순서는 ID 16→17→19→18로, ID 정렬을 접수 순서라고 가정하지 않았다. | `staff-final-2`, `staff-final-4`의 `queue-final.tsv`, `queue-assertions.json` |
| 수정 후보 음원 1/2/4명 | 총 7건 모두 현재 판본·수행표 해시 연결·M4A 크기/SHA-256·HTTP200/media type·전체 AAC decode·삭제 확인. 최대 RUNNING 1. 1명 파일은 611,349 bytes, 44.1kHz/mono, 30.550023초(메타데이터 30,551ms). | `audio-final-1/2/4` |
| 중복 복사·초안/확정 저장·음원 | 같은 복사 키는 동일 sheetId. 초안 동시 요청 200/409, 확정 저장 200/409 후 버전 1회 증가·초안 제거. 음원 동시 요청 202/409, 같은 회원의 다른 생성 409, 완료 파일 재사용 200. | `contracts` |
| 권한·빈 데이터·잘못된 입력 | 미로그인 401, 타 회원의 악보/음원 조회·생성·삭제 404. 빈 악보·잘못된 BPM/타입은 거절하고 확정본 유지. 삭제 후 조회·다시 삭제는404. | `contracts` |
| 업로드 입력 경계 | 0바이트·GIF 서명→415, 잘린 PDF→400, 20MiB 초과→413, 11쪽 PDF→400 TOO_MANY_PAGES. 거절 전후 목록 동일. 빈 파일의 최초 검사 기대값 400은 실제 계약 415로 바로잡았으며 제품 결함으로 집계하지 않았다. | `import-rejections` |
| 실제 저장소 통신 실패·복구 | 기존 음원 완성 후 소유 MinIO를 pause, 다른 BPM 생성은 SERVER_ERROR. unpause 후 기존 파일 해시/바이트 보존, 재시도→DONE·다운로드·전체 decode·삭제. 총 약 58초. 운영 R2 장애를 대신하지 않는다. | `storage-fault` |
| 혼합 2건 | 실제 OMR 1건 + 음원 생성 1건을 동시에 시작, 모두 DONE 및 인식 결과/음원 검증·삭제 | `mixed-2` |
| 혼합 4건 | 위 두 경로 + 실제 길이 사전검사를 거치는 확정 저장/일반 조회 + 기존 긴 음원의 제한 속도 다운로드. 판본 증가·일반 조회·두 음원의 해시/전체 decode·정리 확인. | `mixed-4` |
| 변환 중 중복·삭제·늦은 완료 | 동일 키→같은 ID, 충돌 키/같은 회원의 새 변환→409, 타 회원→404. 변환 중 음원 요청→409. 실행 중 삭제 후 다음 변환은 DONE, 늦은 이전 결과 조회는 404로 악보를 되살리지 않는다. 약 22.4초. | `import-races-green` |
| 음원 실행 중 backend 재시작 | 소유 backend만 강제 종료·시작. 이전 RUNNING은 FAILED/SERVER_ERROR, 음원 미공개. 같은 흐름 재시도→DONE·전체 AAC decode·정리. | `backend-restart` |

올바른 cgroup v1 계측의 샘플 메모리 최고값은 playback 약 195/200/208MiB(음원 1/2/4명), OMR 약 773MiB(오선보 단일), 1,107MiB(정간보 단일), 722/755MiB(수정 후 오선보 2/4명)였다. 샘플링 사이 peak를 놓칠 수 있으므로 `memory.max_usage_in_bytes`의 컨테이너 수명 누적 최고값도 별도로 보존한다. 기존 JVM·에뮬레이터 등 다른 호스트 작업이 있어 운영 여유·처리량 결론으로 사용하지 않는다. CPU 누적/쓰로틀·FD·TCP·PID·tmp 파일·큐 상태는 `samples.json`, 종료 후 관찰은 `after/samples.json`에 남겼다. OMR의 native/model cache와 font cache를 원본 누수로 분류하지 않는다.

환경 오류도 증거에 보존했다. Docker 연결 단절 당시 소유 컨테이너들은 exit255/OOMKilled=false였고 원인은 확정하지 않았다. tmpfs DB/저장소를 재구성한 이후 실행 ID를 구분했다. 재기동 전 조기 로그인 실패(`staff-1`)와 로컬 OMR tmpfs의 `exec` 누락에 따른 native library 실패(`staff-1b`)는 제품 결함이 아니다. 기존 Compose의 `rw,exec,size=1g`에 맞춘 `staff-1c`부터 실제 OMR 결과가 유효하다. 제품 자원 상한·timeout을 통과 목적에 맞게 늘리지 않았다.

## 코드·도구 검증과 리뷰

- playback 최종 Linux 273개 통과/Windows 전용 2개 생략(실제 CAT는 위 HTTP 실행과 분리), Windows 두 Python 환경 각각 14개 통과. Android host 1,809개 통과/5개 조건부 생략. BE D3에서 큐·음원 DB 46개 통과 뒤 실제 CAT 2개 별도 통과, D5에서 음원 DB 40개 통과/CAT 조건부 2개 생략, 기본 775개 통과/자료 1개 생략. AI 관련 209개 통과 뒤 실제 PDF 조건부 2개 별도 통과. 서로 다른 회차를 한 번의 전체 통과 수로 합산하지 않는다.
- 새 실행 도구 전체 17개가 FFmpeg/ffprobe가 있는 Linux에서 통과했다. 잘못된 ID/판본/해시, 빈·잘못된 구조의 인식 결과, 부분 HTTP·틀린 media type·비음원 바이트, 선언 길이 불일치, 응답 JSON 파싱 실패·redirect를 통과시키지 않는 반례 검사를 포함한다. 처음 Windows 검사에서 나타난 기존 SMTP teardown 경고를 최종 Linux 통과와 혼합하지 않는다.
- Compose는 격리된 placeholder 환경으로 `docker compose ... config --quiet`를 검증했다. `--no-interpolate`만 쓰면 기존 필수 경로의 `${...:?}` 때문에 볼륨 파싱이 실패하므로 실제 설정 유효성 검사에 사용하지 않았다.
- FE·BE·AI·playback 제품 수정과 QA 도구/Compose 변경을 작성자와 다른 에이전트가 명세·저장소 규칙·품질 세 축으로 검토했다. 발견한 도구의 허위 통과 경계는 수정 후 실제 HTTP/디코딩 실행으로 재확인했다. 사람의 MR 승인이나 배포 승인을 받은 것으로 표시하지 않는다.
- 수정 Android APK도 기존 debug 옵션으로 빌드했다. API `http://127.0.0.1:18955`, package `com.ssafy.eolssu`, 기존 시험 계정의 후속 검사에만 `qaSkipConsent=true`를 사용한다. 인증은 실제 로그인하며 회원가입·약관 검증을 대신하지 않는다. SHA-256 `44820fc7bb28817ef906d5b27a9af3ef9dbbcd78a4a6067c07e3051c4255bf15`. 배포 QA #394와 다른 패키지/설정이며 UI 실행 결과는 별도 기록한다.

## Android 실제 실행

Android Studio SDK의 API 37 AVD `Eolssu_QA911_S10_API37`를 `-read-only`, RAM 1,536MiB/2 cores, `-no-audio`로 별도 실행했다. 실제 APK 조작은 `adb -s emulator-5594`로 했고 다른 기기·AVD는 조작하지 않았다. 최초 부팅에서 System UI 무응답 창이 한 차례 나타났지만 Wait 후 복구됐다. 앱 ANR로 집계하지 않는다.

| 대상/흐름 | 실제 관찰 | 증거 (`android-local/`) |
| --- | --- | --- |
| 배포 QA #394 | APK 설치·versionName `qa-20261115.111556-ff65c430` 대조. 기존 인증 홈→연습→제공곡 11개/내 악보 10개 목록. 기존 운영 시험 자료는 조회만 했다. | `qa394-home.png`, `qa394-practice-fresh.xml`, `qa394-personal.xml` |
| 수정 APK + 로컬 실제 서버 | 격리 계정 로그인→악보 추가·편집→제공곡 아리랑 사본→편집 제목 저장→`QA954-Android-flow`(sheetId 31, 판본 2)→연습의 음원 다운로드 | `local-copied-editor.xml`, `local-save-named.xml`, `local-saved.xml`, `local-practice-download.xml` |
| 다운로드 연속 누르기 | 준비 중 표시→완료 알림→미리듣기 준비 완료. 해당 sheetId의 DB 음원 한 행이 DONE. 버튼을 두 번 눌렀으며 실제 POST 횟수까지 계측했다고 주장하지 않는다. | `download-doubletap.xml`, `download-complete.xml` |
| 저장·재생·화면 재진입 | 앱 전용 `no_backup/sheet-audio-v1/1/31/` 파일 SHA-256이 서버와 일치(`31df6ba8…54ac141`). 재생 위치 0:13/0:30과 일시정지 버튼 관찰. 목록으로 나갔다가 같은 악보 재진입 시 즉시 준비 완료. | `device-file-sha.txt`, `playing.xml`, `reentry-ready.xml` |
| 서버 연결 제거 후 기기 파일 재생 | ADB reverse 18955를 제거하고 기존 파일 재생→0:06/0:30·일시정지 표시, finally에서 연결 복구. 전체 인터넷 차단이나 이동통신 전환 검사는 아니다. | `offline-playing.xml` |
| 앱 강제 종료·재시작 | 자동 로그인 체크가 꺼진 첫 실행은 로그인 화면으로 돌아갔다. 체크 상태를 확인해 켜고 다시 로그인한 뒤 force-stop/재시작하면 홈 복구, 같은 악보의 저장 음원은 준비 완료였다. | `restart-home.xml`, `login-preferences.xml`, `auto-restart.xml`, `restart-ready.xml` |

Android의 debug 평문 허용 목록을 바꾸지 않기 위해 Git 밖 nginx 프록시가 같은 `127.0.0.1:18955`에서 API와 로컬 MinIO의 서명 경로를 전달했다. Host·query를 유지했고 서명 URL 로그는 비활성화했다. 최신 BE/AI/playback/FE 소스를 결합한 환경이며 `proxy-verify`는 같은 경로의 실제 CAT→서명 다운로드→전체 AAC decode→정리도 통과했다. 앱 파일 해시·진행 시계 관찰은 청감·실기기 리듬 정확도나 인식 결과의 음악적 정답률을 보증하지 않는다.

## 계획 대비 실행 범위

| 단계 | 실행한 경계 | 아직 실제로 실행하지 않은 경계 |
| --- | --- | --- |
| Q0 | 배포 조합 조회, 입력·격리 계정·자원 한도·계측 고정 | 운영 이미지·전용 호스트의 동일 환경 재현 |
| Q1 | PDF 오선/정간 인식, 별도 제공곡 사본의 편집→실제 생성→기기 저장·재생 | 인식 결과를 사람이 교정한 동일 악보의 전체 연속 흐름, 사진/스캔 입력 |
| Q2 | 실제 멱등·회원 제한·권한·초안/확정 저장 충돌·변환 중 삭제/늦은 완료 | 동일 악보의 실제 길이 계산/생성과 편집의 정밀한 중첩. DB 대역 경합 회귀와 구분 |
| Q3 | 실제 OMR/CAT 1·2·4건, FIFO/슬롯·자원 계측, 느린 다운로드 | 동일 실제 엔진 시나리오 3회 반복 안정성. 3회 반복은 합성 느린 응답에 한정 |
| Q4 | MinIO 장애/복구, 음원 실행 중 BE 재시작, 변환 중 삭제. 고정 시계의 만료·대역 timeout/부분 응답 회귀 | 접수·공개 단계별 응답 유실 전체 조합, OMR 실행 중 BE 재시작, 실제 10분/30분 경과, 기기 디스크 부족 |
| Q5 | 정한 `mixed-2/4` 두 조합 | 최대 처리량·운영 포화·장시간 내구성은 별도 범위 |
| Q6 | 5개 결함 수정·같은 재현·영향 회귀·독립 리뷰·로컬 커밋, Android 위 표 | 생성 중 화면 이탈/복귀·완료 직전 로그아웃/계정 전환의 실제 UI, 모바일 통신 장애 후 재다운로드, 실기기 청감 |

계획 전체를 PASS로 표시하지 않는다. 동일 실제 시나리오의 반복·정밀 경합과 단계별 장애 주입은 이번 실행에서 남았으며 환경 생략과 구분한다. 인식 결과의 교정과 원곡 적합성 판단은 사람이 확인할 항목이다. 기존 호스트/DB 회귀 통과가 위 실제 경로의 미실행을 없애지는 않는다.

## 남은 관찰과 실행 제한

- **OMR 하위 프로세스 위험(실제 엔진 영향 미확인):** 공용 `_run`에 손자를 띄우는 합성 명령을 넣으면 timeout 뒤 손자가 `S`로 남았다(`omr-fix/linux-probe.log`). 해당 경로는 `subprocess.run(timeout)`이다. 현재 Audiveris launcher는 ELF/jpackage 실행 파일이며, 사용 중인 Node import 경로에는 명시적 자식 생성 호출이 발견되지 않았다. 실제 엔진의 손자 생성·잔류는 재현하지 못했으므로 운영 결함으로 확정하지 않는다. 향후 launcher가 자식 트리를 만들면 timeout 후 자원이 남을 가능성이 있다. playback 수정으로 이 OMR 경계까지 해결됐다고 주장하지 않는다.
- **미정 성능 기준:** 큐의 실제 1→2→4 동작은 확인했지만 목표 지연·처리량·용량을 새로 확정하지 않았다. 관찰값은 같은 호스트의 다른 JVM/에뮬레이터 영향을 포함한다.
- **환경 부족/비동일 환경:** 운영 Ubuntu 26 이미지 빌드 중 Docker EOF, 일부 초기 Gradle 메모리 할당 실패, 디스크 부족과 Docker 연결 단절이 있었다. 실패한 명령을 통과에 합산하지 않았고 준비된 번역 이미지에서 재실행한 결과만 구분해 기록했다. Windows 10 1703 이전·실기기 청감은 확인하지 않았다.

- 환경 생략 5개: captured CAT-04 자료, 로컬 catalog, 생성 107곡 corpus, 로컬 v21 장단 자료 미설정 및 production QA opt-in 미요청. 호스트 테스트 통과 수에서 제외했다.
- R2 자체 장애·운영 포화·실제 AI 다중 분석·4건 초과·장시간 내구성은 실행하지 않았다. Story 283의 실제 AI 다중 분석 보류 결정을 유지한다.

## 화요일 사람이 확인할 항목

- 실제 배포 APK·서버·OMR·playback revision과 이 수정의 포함 여부. 로컬 커밋은 배포 반영이 아니다.
- 지정 PDF/사진을 추가하고 검토 항목을 실제 원곡과 대조해 교정→저장→다운로드→재생까지 같은 악보로 수행한다. 선택 장단·악기·판본·리듬과 소리를 확인한다.
- 생성 중 다른 화면/백그라운드로 이동했다가 복귀하고 완료 알림의 대상·중복 여부를 확인한다. 완료 직전 로그아웃·다른 계정 진입도 확인한다.
- 비행기 모드·Wi-Fi/모바일 전환으로 다운로드를 중단했다가 재시도한다. 폐기 실기기의 제한 저장 공간에서 실패 안내·이전 파일 보존·공간 확보 후 복구를 확인한다.

## 로컬 변경과 인계

| 커밋 | 변경 |
| --- | --- |
| `9006e638` | playback 결과 오류·Linux 하위 프로세스 정리 |
| `56112039` | Android 저장 후 파일 재조회 실패의 재시도 |
| `69b44759` | BE 만료 파일 정리의 실패 격리 |
| `ac79db0d` | 실제 큐·산출물 검증 도구와 Compose init |
| `e57dea6d` | OMR 잠금 deadline·실패 로그 정리 |
| `72d88d6f` | Windows 생성기 프로세스 트리·임시 파일 정리 |
| `5f757f20` | 변환 중 기본 음원 요청의 상태 오류 분류 |

각 수정은 같은 Task의 목적에 속한다. 문서 커밋은 이 결과와 계획·목록을 함께 보존한다. 작성자와 다른 에이전트의 코드 리뷰를 완료했으며, 사람의 MR 승인을 대신하지 않는다. Jira 시작 때 확인한 Task는 Story 328 관련 링크·활성 스프린트 59321(스프린트 5)·진행 중이며 로컬 완료로 Jira를 완료 전환하지 않는다. Push·MR 생성·배포는 수행하지 않았다.

종료 시 소유 시험 악보 9개를 실제 DELETE API로 정리하고 각각 후속 조회 404를 확인했다. DB 악보·음원·객체 장부·임시 원본은 모두 0건, 로컬 버킷 객체도 0건이었다. `com.eolssu.qa.task=S15P31E102-954` 라벨과 이름을 확인한 컨테이너 7개, 연결이 없는 소유 네트워크 2개, read-only 에뮬레이터만 종료·제거했다. 원본 자료·다른 작업의 서비스·실기기에는 적용하지 않았다. 증거는 `pre-cleanup-state.json`, `cleanup-http-events.json`, `cleanup-db-counts.tsv`, `cleanup-receipt.json`이다. 로그·APK·고정 입력 사본 등 실행 증거는 Git 밖에 남긴다.

이후 사용자의 Push·MR 요청에 따라 develop `3bef4600`을 동기화했다(`ebb613d4`). 통합 호스트 회귀 1,809통과/5조건부 생략·실패/오류 0, debug APK 빌드 성공을 다시 확인했다. 새로 들어온 완료 알림 자동 닫힘과 자료 출처 변경은 이 Task의 신규 수정으로 집계하지 않는다. 통합 APK의 자동 닫힘을 실제 Android에서 재관찰한 것은 아니다. [게시 준비와 검증 경계](../../plans/S15P31E102-954.md#후속-pushmr-요청), 외부 증거 `push/integration-result.json`을 참조한다.
