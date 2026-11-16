# edo1의 eolssu 운영 구성

## Android App Links 준비 (S15P31E102-211)

정확한 경로 `/.well-known/assetlinks.json`은 Nginx가 백엔드로 프록시합니다. SPA나 리다이렉트가 아닙니다. `AUTH_ANDROID_APP_SHA256_FINGERPRINTS`에는 실제 설치할 Android Studio APK의 공개 SHA-256 인증서 지문만 쉼표로 구분해 설정합니다. 잘못된 지문은 기동 시 거절됩니다. 정상 설정은 com.ssafy.eolssu 대상의 JSON 항목을 제공하며 QA 앱 설정과 미설정 처리 기준은 아래를 따릅니다. 이 값은 비밀키가 아니며, 키스토어·키 비밀번호는 필요하지 않습니다. 지문은 추측하지 않고 APK 제작자에게 받습니다. 환경 주입·Nginx/백엔드 배포·실기기 verified 확인은 별도이며, 준비 코드만으로 도메인 검증 완료로 보지 않습니다. [현재 계약·인계](../../docs/specs/social-auth.md)를 참고합니다.

운영 Compose 원본은 `/opt/eolssu/config/compose.yml`, 실제 환경변수는 `/opt/eolssu/.env`입니다. 이 폴더의 `compose.yml`·`nginx.conf`는 서버에서 확인한 프론트·백엔드·MySQL 구성을 관리합니다. 루트의 개발/운영 Compose 두 서비스 예시와 구분합니다.

## 이름과 데이터 보존

| 대상 | 운영 값 |
| --- | --- |
| Compose 프로젝트 | `eolssu-runtime` |
| 컨테이너 | `eolssu-runtime-frontend-1`, `eolssu-runtime-backend-1`, `eolssu-runtime-mysql-1` |
| 설정·릴리스 경로 | `/opt/eolssu/config`, `/opt/eolssu/releases` |
| HTTP | 기존 공개 바인딩·80 포트 유지 |
| MySQL 물리 볼륨 | `eolsu-runtime_mysql_data`를 external volume으로 재사용 |
| 앱 내부 패키지·JAR·DB 식별자 | 이번 작업에서 변경하지 않음 |

물리 볼륨의 기존 이름은 데이터 식별자이므로 서비스명 변경에 맞춰 새 볼륨을 만들지 않습니다. `MYSQL_DATA_VOLUME`이 없으면 구성 검사를 실패시키며, 명시한 외부 볼륨이 없으면 실행도 실패합니다. 기존 `.env`의 DB 이름·사용자·비밀번호를 보존합니다.

## 확인·실행·복구

실제 환경변수 값이 출력되지 않도록 구성 검사는 `--quiet`로 실행합니다.

```sh
sudo docker compose --env-file /opt/eolssu/.env \
  -f /opt/eolssu/config/compose.yml config --quiet
sudo docker compose --env-file /opt/eolssu/.env \
  -f /opt/eolssu/config/compose.yml ps
curl -i http://127.0.0.1/
curl -i http://127.0.0.1/api/actuator/health/readiness
curl -i http://127.0.0.1/api/terms
```

프론트 `/`는 200, readiness는 200과 `{"status":"UP"}`, 공개 업무 API `/api/terms`는 200 JSON이 정상입니다. Nginx HTML 404나 502와 구분합니다. 등록 안 된 `/api/...` 경로는 인증 때문에 401이라 확인 기준으로 쓰지 않습니다. 배포 변경 후에는 이 응답, Nginx 설정 검사, MySQL health·물리 볼륨을 함께 확인합니다.

API의 실제 준비 상태는 `/api/actuator/health/readiness`로 검사합니다. Nginx는 `/api/` 경로를 보존해 전달합니다. 단, 점검 주소(`/api/actuator`)는 readiness만 백엔드로 넘기고 나머지는 Nginx가 404로 막습니다. Prometheus 수치(`/api/actuator/prometheus`)는 서버 안에서 `frontend:8081`로 읽습니다. `/api/` 응답 중 1KiB 이상의 JSON만 `Accept-Encoding: gzip`을 보낸 클라이언트에게 gzip으로 압축합니다. 녹음·음원 같은 미디어는 압축하지 않습니다. 웹 정적 파일(`location /`)은 Wasm·JS·CSS·글꼴·문자열 XML을 같은 조건으로 압축하고, 내용 해시 이름의 Wasm만 1년 `immutable`, 나머지는 `no-cache`(ETag 재검증)로 내려보냅니다. 서버 반영 절차는 [917 기록](../../docs/plans/S15P31E102-917.md#운영-반영-절차-사용자-확인-후)을 따릅니다. 저장소의 Nginx 설정 3개(`nginx.conf`, `nginx.tls.conf.template`, `infra/nginx/default.conf`)를 바꿔도 서버의 `/opt/eolssu/config/` 파일은 따로 고쳐야 하므로, 이 규칙은 백엔드 배포보다 먼저 서버에 반영합니다. backend는 `ops` 프로필에서 기존 `.env`의 MySQL DB·사용자·비밀번호로 업무 API를 제공하고, 애플리케이션 준비 상태와 DB 연결을 함께 확인합니다. Flyway·SQL 초기화는 비활성화하며 스키마·물리 볼륨을 변경하지 않습니다. 스키마 변경은 마이그레이션 Task에서 따로 적용합니다. Docker healthcheck도 같은 경로의 HTTP 성공과 정확한 `{"status":"UP"}`를 확인합니다. 주기는 30초, 요청 제한 3초, healthcheck 제한 5초, 초기 유예 30초, 실패 허용 3회입니다. 운영 JRE 이미지 안의 curl을 확인해 사용합니다.

### backend 실행 이미지

backend는 `eclipse-temurin:21-jre`에 녹음 검사용 `ffmpeg=7:8.0.1-3ubuntu2`만 더한 `eolssu-backend-runtime:21-jre-ffmpeg8.0.1`로 실행하고, Jenkins가 만든 JAR을 `/app/app.jar`에 읽기 전용으로 붙입니다. Jenkins가 서버 `backend.Dockerfile`로 만드는 `eolssu-backend:ci-<SHA>`는 후보 검증과 JAR 추출에만 쓰이고 운영 컨테이너 이미지가 되지 않으므로, 운영 실행에 필요한 도구는 이 실행 이미지에 넣어야 합니다. 배포는 `--pull never`로 재생성하므로 이 이미지는 edo1에서 직접 빌드해 둡니다.

```sh
D=$(mktemp -d) && cp /opt/eolssu/config/backend-runtime.Dockerfile "$D/Dockerfile"
sudo docker build --tag eolssu-backend-runtime:21-jre-ffmpeg8.0.1 "$D" && rm -rf "$D"
sudo docker exec "$(sudo /opt/eolssu/bin/deploy-backend --active-container)" ffprobe -version
```

원본은 저장소 `infra/runtime/backend-runtime.Dockerfile`이며 서버 `/opt/eolssu/config/backend-runtime.Dockerfile`과 같게 유지합니다. 베이스 JRE 보안 업데이트를 반영하려면 같은 태그로 다시 빌드한 뒤 backend만 재생성하고 readiness·ffprobe를 확인합니다. 베이스의 Ubuntu가 바뀌어 고정 ffmpeg 버전 설치가 실패하면 `backend/Dockerfile`과 함께 버전을 갱신합니다.

### ai 분석 서비스

연주 분석 API([ai README](../../ai/README.md#분석-api))는 `ai` 서비스로 실행합니다. 인증이 없으므로 호스트 포트를 열지 않고, backend가 Compose 내부망의 `http://ai:8000`으로만 부릅니다(`ANALYSIS_AI_BASE_URL`). 주소는 비밀값이 아니라 Compose에 직접 적습니다. AI가 없거나 응답하지 않으면 업로드한 세션은 `SERVER_ERROR` 또는 제한 시간(60초) 뒤 `TIMEOUT`이 되고, 다른 API에는 영향이 없습니다.

이미지는 저장소 `ai/Dockerfile`로 edo1에서 빌드한 `eolssu-ai:runtime`을 씁니다(약 1.65GB, 빌드에 2GB 넘는 디스크 여유 필요). 배포는 `--pull never`입니다. 2026-11-15부터 Jenkins 잡 `eolssu-ai`(아래 '자동 배포')가 `ai/` 변경을 빌드·검사·배포합니다. 아래 수동 빌드는 Jenkins를 쓸 수 없을 때만 씁니다.

#### 자동 배포 (S15P31E102-244)

[ai.Jenkinsfile](../jenkins/ai.Jenkinsfile)은 전용 슬롯 `edo1-ai`([슬롯 구성](../jenkins/controller/README.md#ai-배포-전용-슬롯))에서 develop을 1분마다 확인합니다. 흐름과 각 단계의 근거는 [244 작업 기록](../../docs/plans/S15P31E102-244.md)에 있습니다.

1. [build-ai-image.py](../jenkins/build-ai-image.py) `plan`: 이미지를 만드는 입력(`ai/Dockerfile`·`.dockerignore`·`.python-version`·`pyproject.toml`·`uv.lock`·`src/`)의 Git 해시를 실행 중인 ai 이미지의 `com.eolssu.ai.source` 라벨과 비교합니다. 같으면 빌드·배포를 생략합니다.
2. `build`: `eolssu-ai:ci-<SHA>`를 커밋·입력 해시 라벨과 함께 만듭니다.
3. `test`: 그 이미지의 Python·의존성·ffmpeg로 읽기 전용 저장소의 채점 테스트를 돌립니다. `omr_service` 테스트는 OMR 이미지 몫이라 빼고, Windows 전용 평가 실험 테스트 1건은 노드 ID로만 제외합니다.
4. `smoke`: 운영과 같은 조건(비root, 읽기 전용, `/tmp` tmpfs, 네트워크 없음)으로 띄워 `/health`와 합성음 채점(`python -m ai_service.warmup`)을 확인합니다.
5. [deploy-ai.py](../jenkins/deploy-ai.py) (`sudo -n /opt/eolssu/bin/deploy-ai <SHA>`): backend·frontend와 같은 lock을 쓰고, backend가 전환·drain 중이면 멈춥니다. 중단된 배포를 먼저 복구한 뒤 순서·라벨을 검사하고, `eolssu-ai:runtime` 태그를 후보로 옮겨 `ai`만 `--wait`로 재생성합니다. 이어서 실행 이미지, backend 안에서의 422 응답, `/health`, 예열 채점, backend·frontend·MySQL 동일성을 확인합니다. 하나라도 실패하면 이전 이미지로 되돌립니다. 이전 이미지는 `eolssu-ai:rollback` 태그와 `/opt/eolssu/ai-deployment.json`(root 0600)에 남습니다. ai는 DB·설정을 바꾸지 않으므로 DB 백업은 하지 않습니다.

실패는 잡 이름이 `eolssu-`로 시작하므로 [Jenkins 실패 알림](../jenkins/notifications/README.md)이 그대로 보냅니다. 쌓이는 `eolssu-ai:ci-*` 태그는 [보존 정리](../retention/README.md)가 함께 정리합니다.

운영 설치(2026-11-15 적용, 다시 설치할 때도 같은 순서). 각 단계 전에 실행 중인 배포 잡이 없는지 확인합니다.

1. `deploy-ai.py`를 `deploy-backend` 옆에 설치합니다(`sudo install -o root -g root -m 0755 infra/jenkins/deploy-ai.py /opt/eolssu/bin/deploy-ai`). 같은 폴더의 `deploy-backend` 공통 함수를 읽으므로 두 파일을 함께 관리합니다. omr·playback 배포 도구(`deploy-service-image`)도 이 파일을 읽습니다(아래 'omr·playback 자동 배포'). 보존 정리 도구도 같은 방법으로 `/opt/eolssu/bin/eolssu-retention`에 다시 설치합니다(이전 판은 `eolssu-retention.bak-244`).
2. `visudo -cf`로 검사한 `/etc/sudoers.d/eolssu-ai`(root 0440)에 `jenkins ALL=(root) NOPASSWD: /opt/eolssu/bin/deploy-ai *`를 둡니다.
3. 실행 중인 ai 컨테이너의 이미지와 `eolssu-ai:runtime` 태그가 같은지 확인합니다(`sudo docker inspect --format '{{.Image}}' eolssu-runtime-ai-1`, `sudo docker image inspect --format '{{.Id}}' eolssu-ai:runtime`). 다르면 도구가 시작을 거부합니다.
4. 슬롯과 잡은 [컨트롤러 문서](../jenkins/controller/README.md#ai-배포-전용-슬롯)대로 등록합니다(2026-11-15 등록, 첫 운영 배포 #1 성공). 잡을 처음 만들면 Jenkinsfile의 polling은 첫 실행 뒤에 켜지므로 **Build Now**로 한 번 실행합니다. 확인할 것은 `ai-deployment.jsonl`의 `deployed`, 운영 `eolssu-ai:runtime`의 revision 라벨, backend 안의 422 응답(아래 명령), 분석 1건입니다.
5. 이후 수동 빌드 이미지는 source 라벨이 없으므로 다음 자동 실행이 다시 빌드·배포합니다.

되돌리기: 잡을 비활성화합니다. 도구가 중단돼 journal이 `deploying`·`rollback_failed`이면 `sudo /opt/eolssu/bin/deploy-ai --recover`로 이전 이미지를 복구합니다. 배포가 끝난 뒤 이전 판으로 돌릴 때는 `sudo docker tag eolssu-ai:rollback eolssu-ai:runtime`을 실행한 뒤 아래 `up` 명령으로 `ai`만 재생성합니다.

#### 수동 빌드

```sh
# 로컬 저장소에서: 되돌릴 수 있게 지금 이미지에 빌드 커밋 이름표를 붙이고,
# 반영할 커밋의 ai/만 서버 임시 폴더로 보내 빌드한다. 빌드 커밋은 이미지 라벨에도 남긴다.
REV=$(git rev-parse origin/develop)
ssh edo1 "sudo docker tag eolssu-ai:runtime eolssu-ai:runtime-<이전 빌드 커밋 앞 7자리>"
D=$(ssh edo1 mktemp -d)
git archive "$REV" ai | ssh edo1 "tar -x -C $D"
ssh edo1 "sudo docker build --label org.opencontainers.image.revision=$REV --tag eolssu-ai:runtime $D/ai && rm -rf $D"
# edo1에서: ai만 (재)생성하고 backend에서 연결을 확인한다.
sudo docker compose --env-file /opt/eolssu/.env \
  -f /opt/eolssu/config/compose.yml up -d --no-deps --pull never ai
sudo docker compose --env-file /opt/eolssu/.env \
  -f /opt/eolssu/config/compose.yml ps ai
sudo docker exec "$(sudo /opt/eolssu/bin/deploy-backend --active-container)" \
  curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' -d '{}' http://ai:8000/v1/analyses
```

마지막 명령은 422가 정상입니다(빈 요청을 계약 위반으로 거절). 연결 거부·이름 풀이 실패는 `ai`가 없거나 내부망이 다른 것입니다.

### omr·playback 자동 배포 (S15P31E102-951)

2026-11-15부터 운영 중입니다(첫 배포 omr #1·playback #1 성공). `ai/`·`data/`의 해당 입력이 바뀌면 Jenkins가 반영하므로, 각 절의 수동 절차는 Jenkins를 쓸 수 없을 때만 씁니다. 흐름과 근거는 [951 작업 기록](../../docs/plans/S15P31E102-951.md)에 있습니다.

Jenkins 잡 `eolssu-omr`·`eolssu-playback`이 같은 [service-image.Jenkinsfile](../jenkins/service-image.Jenkinsfile)로 ai 잡과 같은 슬롯 `edo1-ai`에서 develop을 1분마다 확인합니다([잡 등록](../jenkins/controller/README.md#omrplayback-이미지-잡)).

1. [build-service-image.py](../jenkins/build-service-image.py) `<서비스> plan`: 이미지에 들어가는 입력의 Git 해시를 실행 중 컨테이너 이미지의 `com.eolssu.<서비스>.source` 라벨과 비교합니다. 같으면 빌드·배포를 생략합니다. omr 입력은 `ai/omr.Dockerfile`과 그 `.dockerignore`가 허용하는 `ai/`(소스·의존성·스캔 요구 목록)·`data/src/scores`·`data/reference`이고, playback 입력은 `data/playback.Dockerfile`과 그 `.dockerignore`가 허용하는 `data/src/playback`·`data/src/audio/jangdan_audio.py`·`data/src/scores`·`data/reference`입니다. 그래서 `ai/src`가 바뀌면 ai와 omr이 함께 다시 만들어집니다.
2. `build`: 저장소 루트를 맥락으로 `eolssu-<서비스>:ci-<SHA>`를 만듭니다. omr은 운영과 같은 마지막 단계 `scanner`(`--target scanner`)이고 BuildKit(Docker 23+ 기본)이 필요합니다.
3. `test`: omr은 이미지의 Python·Audiveris·정간보 변환기로 `ai/tests/unit` 중 `omr_service`를 쓰는 테스트를 돌립니다(ai 게이트가 나머지를 돌림). 이미지에 이미 있는 패키지 버전은 바꾸지 않고 dev 그룹만 더합니다. playback은 이미지의 Python·FFmpeg로 `data/src/playback/tests`와 `data/tests/test_playback_import.py`를 돌립니다.
4. `smoke`: 운영 Compose와 같은 제한(비root, 읽기 전용, `/tmp` tmpfs, 메모리·CPU, playback은 스왑 없음)과 네트워크 없음으로 띄워 `/health`, 빈 요청 응답(omr 422, playback 400), 실행 사용자의 도구(omr: Audiveris·정간보 변환기·스캔 모델·node, playback: ffmpeg·ffprobe)를 확인합니다. playback 자산은 실행 중 컨테이너가 붙인 폴더를 읽기 전용으로 씁니다.
5. [deploy-service-image.py](../jenkins/deploy-service-image.py) (`sudo -n /opt/eolssu/bin/deploy-service-image <서비스> <SHA>`): 먼저 omr은 `sheet_conversions`, playback은 `sheet_audios`의 QUEUED·RUNNING이 0이 될 때까지 배포 lock 밖에서 기다립니다(최대 20분, 넘으면 아무것도 바꾸지 않고 실패). backend는 연결이 끊긴 순간의 작업을 재시도 없이 `FAILED`로 끝내기 때문입니다. lock을 잡고 교체 직전에 다시 확인해 그 사이 들어온 작업이 있으면 lock을 놓고 다시 기다립니다. 이후는 ai와 같습니다. `eolssu-<서비스>:runtime` 태그를 후보로 옮겨 그 서비스만 `--wait`로 재생성하고(playback은 `--profile playback`), 실행 이미지, backend 안의 빈 요청 응답, `/health`, 도구, backend·frontend·MySQL 동일성을 확인합니다. 하나라도 실패하면 이전 이미지로 되돌립니다. 이전 이미지는 `eolssu-<서비스>:rollback`과 `/opt/eolssu/<서비스>-deployment.json`(root 0600)에 남고, 재생성 동안 연결 실패로 끝난 작업 수는 journal의 `failed_during_switch`에 남습니다.

실패 알림은 잡 이름이 `eolssu-`로 시작해 [Jenkins 실패 알림](../jenkins/notifications/README.md)이 그대로 보냅니다. `eolssu-omr:ci-*`·`eolssu-playback:ci-*`는 [보존 정리](../retention/README.md)가 최근 3개만 남깁니다.

운영 설치(다시 설치할 때도 같은 순서, 각 단계 전에 실행 중인 배포 잡이 없는지 확인). 2026-11-15 설치 때 이전 판은 `deploy-ai.bak-951`·`eolssu-retention.bak-951`로 남겼습니다:

1. 이번 변경의 `deploy-ai.py`와 `deploy-service-image.py`를 함께 설치합니다(`sudo install -o root -g root -m 0755 infra/jenkins/deploy-ai.py /opt/eolssu/bin/deploy-ai`, 같은 방법으로 `/opt/eolssu/bin/deploy-service-image`). `deploy-service-image`는 `deploy-ai`를, `deploy-ai`는 `deploy-backend`를 읽습니다. 보존 정리 도구도 `/opt/eolssu/bin/eolssu-retention`에 다시 설치하고 dry-run을 봅니다.
2. `visudo -cf`로 검사한 `/etc/sudoers.d/eolssu-service-image`(root 0440)에 `jenkins ALL=(root) NOPASSWD: /opt/eolssu/bin/deploy-service-image *`를 둡니다.
3. 실행 중 컨테이너 이미지와 `:runtime` 태그가 같은지 확인합니다(`sudo docker inspect --format '{{.Image}}' eolssu-runtime-omr-1`, `sudo docker image inspect --format '{{.Id}}' eolssu-omr:runtime`, playback도 같음). 다르면 도구가 시작을 거부합니다.
4. 잡을 [컨트롤러 문서](../jenkins/controller/README.md#omrplayback-이미지-잡)대로 등록하고 잡마다 **Build Now**로 한 번 실행합니다(2026-11-15 등록, 첫 배포 성공). 수동 빌드 이미지에는 source 라벨이 없어 첫 실행은 빌드·배포합니다. omr 첫 빌드는 Audiveris·스캔 모델을 받아 수 분 걸립니다. 확인할 것은 `service-deployment.jsonl`의 `deployed`, `:runtime`의 revision 라벨, backend 안의 빈 요청 응답(각 절의 명령), 변환·음원 1건입니다.

되돌리기: 잡을 비활성화합니다. 도구가 중단돼 journal이 `deploying`·`rollback_failed`이면 `sudo /opt/eolssu/bin/deploy-service-image <서비스> --recover`로 이전 이미지를 복구합니다. 배포가 끝난 뒤 이전 판으로 돌릴 때는 대기열이 빈 것을 확인하고 `sudo docker tag eolssu-<서비스>:rollback eolssu-<서비스>:runtime` 뒤 각 절의 `up` 명령(playback은 `--profile playback`)으로 그 서비스만 재생성합니다.

### playback 내 악보 음원 생성기

[내 악보 음원 스펙](../../docs/specs/sheet-audio.md)의 생성기는 `playback` 프로필로 따로 띄웁니다(S15P31E102-481). 인증이 없으므로 호스트 포트를 열지 않고, backend만 내부망 `http://playback:8000`으로 부릅니다. 2026-11-15부터 Jenkins `eolssu-playback`이 빌드·배포합니다([자동 배포](#omrplayback-자동-배포-s15p31e102-951)). 아래 수동 절차는 Jenkins를 쓸 수 없을 때만 씁니다. 운영 상태는 [S15P31E102-710 작업 기록](../../docs/plans/S15P31E102-710.md)에서 확인합니다.

- 이미지: 저장소 루트를 맥락으로 `data/playback.Dockerfile`을 빌드해 `eolssu-playback:runtime`으로 태그합니다.
- 자산: `PLAYBACK_ASSETS_DIRECTORY`에 악기별 CAT v1.3 ZIP과 `assets.json`(악기 code → ZIP 파일 이름), `PLAYBACK_JANGGU_DIRECTORY`에 `deong`·`gi`·`kung`·`deok`·`da`·`deo`.wav를 둡니다. 없으면 서비스가 시작을 거부합니다.
- 자원: `mem_limit: 1g`, `memswap_limit: 1g`, `cpus: 1`로 스왑 없이 고정합니다. 실측은 스펙에 있습니다.
- 연결: `SHEET_AUDIO_BASE_URL=http://playback:8000`을 지정해야 생성 요청을 받습니다. 비어 있으면 503입니다.
- 점검: 로그는 syslog 태그 `eolssu-playback`으로 남습니다. 요청 처리 로그는 오류 코드와 생성기 종료 상태만 담고, 시작 실패는 예외 전체를 남깁니다. 상태는 `/health` healthcheck로 봅니다.

2026-11-14, 병합된 MR !739의 빠르기 지원을 운영 OMR·playback에 반영했습니다. 검증 후보 revision `24273759`의 AI/DATA 소스가 병합된 develop과 같음을 파일 SHA로 확인해 OMR `b75f30cb…`, playback `529d5859…` 이미지를 재사용했습니다. 운영 PDF의 숫자 빠르기·가속과 실제 CAT 생성 길이를 검증했습니다. [904 배포·검증·복구 기록](../../docs/plans/S15P31E102-904.md)을 참고합니다. 당시에는 Jenkins가 이 두 런타임 이미지를 갱신하지 않아 기능 소스 반영과 운영 이미지 반영을 구분했습니다. 2026-11-15부터는 `eolssu-omr`·`eolssu-playback` 잡이 갱신합니다. 기존에 빠르기가 누락된 사용자 악보는 자동 복구되지 않습니다.

Windows PowerShell 5.1에서는 파이프가 바이너리를 글자로 바꿔 tar가 깨지므로 `git archive -o ai.tar "$REV" ai`로 파일을 만든 뒤 `scp`로 보내 서버에서 `tar -x -C $D -f $D/ai.tar`로 풉니다. ssh 설정에 포트 연결(LocalForward)이 있으면 열린 터널과 겹치므로 `-o ClearAllForwardings=yes`를 붙입니다. 되돌릴 때는 `sudo docker tag eolssu-ai:runtime-<이전 커밋> eolssu-ai:runtime` 뒤 같은 명령으로 `ai`만 재생성합니다. 빌드 기록: [S15P31E102-228](../../docs/plans/S15P31E102-228.md)(`88a1b05`), [S15P31E102-361](../../docs/plans/S15P31E102-361.md)(`c701c32`, pyin-4), [S15P31E102-291](../../docs/plans/S15P31E102-291.md#운영-반영-2026-11-15-사용자-승인)(`90869163`, pyin-9, 이전 이미지 `runtime-4e6aa8d`).

- 컨테이너 파일 시스템은 읽기 전용이고 `/tmp`만 512MiB tmpfs로 씁니다(녹음 임시 파일, numba 캐시).
- 메모리 한도는 6GiB입니다. AI는 채점 DTW 예상 메모리가 4GiB(120초 × 120초)를 넘는 요청을 먼저 거절하고, 120초 녹음 채점에 약 3GiB가 듭니다([측정 기록](../../docs/knowledge/ai/analysis-latency.md)). backend는 분석을 기본 2개까지 동시에 보내므로, 긴 녹음 두 개가 겹치면 한도를 넘어 `ai`가 재시작되고 두 세션이 `SERVER_ERROR`가 될 수 있습니다.
- 요청 본문의 녹음 서명 URL은 uvicorn 접근 로그(경로만 남김)와 분석 로그(`analysis session=… attempt=… result=… ms=…`)에 남지 않습니다. 배포 뒤 `sudo sh -c 'grep -ciE "X-Amz|audioUrl" /var/log/eolssu/ordinary/eolssu-ai.$(date -u +%F).log'`가 0인지 확인합니다. 운영 컨테이너는 syslog 로그 드라이버라 `docker logs`가 아무것도 읽지 못해, 그 명령으로는 늘 '없음'처럼 보입니다.

backend가 분석을 시작하지 못하면 업로드마다 경고 로그가 남습니다. 배포 뒤 `sudo sh -c 'grep -i wiring /var/log/eolssu/ordinary/eolssu-backend.$(date -u +%F).log'`가 비어 있어야 합니다.

### omr 악보 인식 서비스

악보 인식 API([ai README](../../ai/README.md#악보-인식-api), [계약](../../docs/specs/score-recognition.md))는 `omr` 서비스로 실행합니다. 채점 `ai`와 메모리·CPU를 나누지 않게 따로 띄우고, 인증이 없으므로 호스트 포트를 열지 않습니다. backend 변환 작업자가 내부망 `http://omr:8000`으로 부르게 되는 것은 [S15P31E102-347](https://ssafy.atlassian.net/browse/S15P31E102-347)에서 합니다. 그 전에는 띄우지 않아도 다른 서비스에 영향이 없습니다. 2026-11-15부터 Jenkins `eolssu-omr`이 빌드·배포합니다([자동 배포](#omrplayback-자동-배포-s15p31e102-951), 이전 수동 이미지 `b75f30cb…`는 [904](../../docs/plans/S15P31E102-904.md)). 아래 수동 절차는 Jenkins를 쓸 수 없을 때만 씁니다.

이미지는 저장소 `ai/omr.Dockerfile`로 edo1에서 빌드한 `eolssu-omr:runtime`입니다. 운영은 마지막 단계 `scanner`(정간보 스캔 확장 포함, 약 4.0GB)를 씁니다. 아래 명령처럼 `--target` 없이 빌드하면 이 단계가 됩니다. 빌드 맥락은 저장소 루트라서 `ai/`와 `data/src/scores`·`data/reference`를 함께 보냅니다. Audiveris deb(약 78MB)와 jeongganbo-generator는 빌드 중에 받습니다.

```sh
# 로컬 저장소에서: 반영할 커밋의 필요한 폴더만 서버 임시 폴더로 보내 빌드한다.
REV=$(git rev-parse origin/develop)  # 전체 SHA. 이름을 그대로 넣으면 이미지 라벨에 이름이 남는다
# 다시 빌드할 때만(처음이면 건너뜀): 되돌릴 수 있게 지금 이미지에 이전 빌드 커밋 이름표를 남긴다.
ssh edo1 "sudo docker tag eolssu-omr:runtime eolssu-omr:runtime-<이전 빌드 커밋 앞 7자리>"
D=$(ssh edo1 mktemp -d)
git archive "$REV" ai data/src/scores data/reference | ssh edo1 "tar -x -C $D"
# omr.Dockerfile은 BuildKit이 필요하다(ADD --checksum, omr.Dockerfile.dockerignore). buildx가 없으면 여기서 멈춘다.
ssh edo1 "sudo DOCKER_BUILDKIT=1 docker build --label org.opencontainers.image.revision=$REV -f $D/ai/omr.Dockerfile --tag eolssu-omr:runtime $D; s=\$?; rm -rf $D; exit \$s"
scp infra/runtime/compose.yml edo1:/tmp/eolssu-compose.yml
# edo1에서: 서버 Compose와 다른 곳이 omr 서비스뿐인지 본 뒤 백업하고 바꾼다. 검사를 통과해야 omr만 생성한다.
sudo diff /opt/eolssu/config/compose.yml /tmp/eolssu-compose.yml
sudo /opt/eolssu/bin/install-runtime-compose /tmp/eolssu-compose.yml &&
  rm /tmp/eolssu-compose.yml &&
  sudo docker compose --env-file /opt/eolssu/.env \
    -f /opt/eolssu/config/compose.yml up -d --no-deps --pull never omr
sudo docker compose --env-file /opt/eolssu/.env \
  -f /opt/eolssu/config/compose.yml ps omr
sudo docker exec "$(sudo /opt/eolssu/bin/deploy-backend --active-container)" \
  curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' -d '{}' http://omr:8000/v1/recognitions
```

마지막 명령은 422가 정상입니다(빈 요청을 계약 위반으로 거절). `diff`에 omr 밖의 차이가 보이면 서버 파일을 먼저 저장소에 반영하고 멈춥니다. Windows PowerShell에서는 `ai` 절과 같이 `git archive -o omr.tar "$REV" ai data/src/scores data/reference`로 파일을 만들어 `scp`로 보낸 뒤 서버에서 풉니다. 되돌릴 때는 `sudo docker tag eolssu-omr:runtime-<이전 커밋> eolssu-omr:runtime` 뒤 같은 `up` 명령으로 `omr`만 재생성합니다. 아직 backend가 부르지 않으므로 문제가 생기면 `sudo docker compose ... stop omr`로 멈추고, Compose까지 되돌릴 때는 설치 도구가 출력한 백업 경로를 같은 도구에 전달합니다.

- 컨테이너 파일 시스템은 읽기 전용이고 `/tmp`만 1GiB tmpfs로 씁니다(원본·쪽 그림·Audiveris 작업 폴더와 홈 `/tmp/home`). 로컬 실측에서 요청 사이 `/tmp`는 14MB였습니다.
- 메모리 한도 2GiB, CPU 2개입니다. Audiveris는 한 번에 하나만 돌고(동시 요청은 남은 시간 안에서 기다림), 로컬 실측 최대 1.31GiB(tmpfs 포함)였습니다. Audiveris가 도는 동안 다른 요청이 큰 PDF 쪽을 그림으로 바꾸는 경우는 재지 않았습니다. 정간보 그림 경로([S15P31E102-458](https://ssafy.atlassian.net/browse/S15P31E102-458), torch)를 붙이면 다시 잽니다.
- 요청 본문의 서명 URL은 uvicorn 접근 로그와 인식 로그(`recognition conversion=… result=… ms=…`)에 남지 않습니다. 배포 뒤 `sudo sh -c 'grep -ciE "X-Amz|https?://" /var/log/eolssu/ordinary/eolssu-omr.$(date -u +%F).log'`가 0인지 확인합니다. rsyslog는 omr 줄 중 healthcheck·요청 경로와, 변환 ID가 `sheet-<숫자>-<숫자>`인 인식 요약만 저장합니다([규칙](../privacy/30-eolssu-logs.conf)). 그래서 아래 시험 요청(`probe-…`)의 요약 줄은 남지 않습니다.

서버(4코어) 처리 시간과 메모리는 처음 배포할 때 사람이 잽니다. omr은 파일을 URL로만 받고 컨테이너가 읽기 전용이라, 시험 그림을 같은 내부망의 임시 웹 서버(`omr-probe`, omr 이미지의 Python 사용)로 내보내고 backend 컨테이너에서 요청합니다. 서버에 컨테이너를 잠깐 더 띄우는 일이므로 실행하는 사람이 판단합니다. 시험 그림은 [조사 문서](../../docs/knowledge/ai/score-omr-feasibility.md) 5절의 4곡 오선보 쪽 그림(표준악보집 300DPI PNG, 오른쪽 반)입니다.

```sh
# edo1에서: 시험 그림(예: onara.png)을 /tmp/omr-probe에 두고 같은 내부망에서 읽기 전용으로 연다.
mkdir -p /tmp/omr-probe && chmod 755 /tmp/omr-probe   # 그림도 다른 사용자가 읽을 수 있어야 한다(컨테이너 사용자 10001)
sudo docker run --rm -d --name omr-probe --network eolssu-runtime_default \
  -v /tmp/omr-probe:/srv:ro eolssu-omr:runtime python -m http.server -d /srv 8080
sudo docker exec "$(sudo /opt/eolssu/bin/deploy-backend --active-container)" curl -s -w '\n%{time_total}s\n' -X POST -H 'Content-Type: application/json' \
  -d '{"conversionId":"probe-1","notation":"STAFF","instrument":"DANSO","files":[{"url":"http://omr-probe:8080/onara.png","contentType":"image/png"}]}' \
  http://omr:8000/v1/recognitions | grep -o '"status":"[A-Z_]*"\|"failReason":"[A-Z_]*"\|[0-9.]*s$'
sudo docker exec eolssu-runtime-omr-1 cat /sys/fs/cgroup/memory.peak   # 바이트. cgroup v2·커널 5.19 이상에만 있다
sudo docker stop omr-probe && rm -rf /tmp/omr-probe
```

`"status":"DONE"`과 처리 시간을 4곡마다 보고, 최대 메모리와 함께 조사 문서에 남깁니다. `memory.peak`가 없으면 요청 중에 `sudo docker stats --no-stream eolssu-runtime-omr-1`을 여러 번 읽습니다. 쪽당 60초에 가까우면 계약의 시간 상한을 다시 봅니다. 2026-11-04 첫 실측(CPU 1.5, 쪽당 30초)은 4곡 1쪽 25.1~29.5초로 1곡이 `TIMEOUT`이었고, `docker update --cpus 3`으로 잠깐 올리면 18.6~22.3초, 최대 메모리 0.76GB였습니다([S15P31E102-468](https://ssafy.atlassian.net/browse/S15P31E102-468)). 시험 서버를 띄운 직후 요청하면 받을 곳이 없어 바로 실패하므로 2초 기다립니다.

### backend 비밀값

인증 비밀값은 `/opt/eolssu/.env`에만 두고 Compose의 backend `environment`에 필요한 변수만 연결합니다. 값은 저장소·채팅·로그·빌드 인자에 넣지 않습니다.

| 변수 | 용도 | 값이 없을 때 |
| --- | --- | --- |
| `AUTH_JWT_SECRET` | 로그인 토큰 서명 키. 32바이트 이상 무작위 값 | backend는 기동하고 로그인 토큰만 모두 거절 |
| `AUTH_CODE_HMAC_SECRET` | 이메일 인증번호 해시 키. 32바이트 이상 무작위 값 | 인증번호 발송·확인 거절. 이메일 가입·비밀번호 재설정도 불가 |
| `AUTH_MAIL_HOST`·`AUTH_MAIL_PORT` | 인증 메일 SMTP 서버. 운영은 `smtp.naver.com`·587(STARTTLS는 백엔드 설정에 고정. 465 SSL로 포트만 바꾸지 않음) | 호스트가 없으면 메일 발송만 거절. 포트는 Compose 기본 587 |
| `AUTH_MAIL_USERNAME`·`AUTH_MAIL_PASSWORD` | 발송용 네이버 메일 주소와 네이버 애플리케이션 비밀번호(로그인 비밀번호 아님). 실제 값은 서버 `.env`에만 둠 | 메일 발송만 거절 |
| `AUTH_MAIL_FROM`·`AUTH_MAIL_FROM_NAME` | 발신 주소·이름(`얼쑤`) | 주소는 Compose가 `AUTH_MAIL_USERNAME`으로 채움. 이름이 없으면 주소만 표시 |
| `AUTH_OAUTH_{KAKAO,GOOGLE,NAVER}_CLIENT_ID`·`_CLIENT_SECRET` | 간편 로그인 제공자별 앱 키. 백엔드가 개발자 콘솔에서 발급해 별도 경로로 전달 | 그 제공자의 로그인만 거절 |
| `RECORDING_STORAGE_ENDPOINT`·`_BUCKET`·`_ACCESS_KEY_ID`·`_SECRET_ACCESS_KEY`·`_REGION` | 녹음 원본 저장소(Cloudflare R2) S3 API 주소·버킷과 버킷 전용 R2 API 토큰의 S3 키. 지역은 `auto`. 준비 방법은 [backend README](../../backend/README.md#녹음-업로드와-원본-저장소pra-02) | 녹음 업로드만 503으로 거절 |
| `SHEET_STORAGE_ENDPOINT`·`_BUCKET`·`_ACCESS_KEY_ID`·`_SECRET_ACCESS_KEY`·`_REGION` | 내 악보 임시 M4A용 별도 비공개 R2 버킷과 그 버킷에 한정한 Object Read & Write 키. 지역 기본값은 `auto`. 버킷 공개 여부와 실제 업로드·서명 읽기·삭제는 [710 작업 기록](../../docs/plans/S15P31E102-710.md) 참조 | 필수 값이 비면 내 악보 음원 생성은 503으로 거절 |

Compose는 값이 없는 변수를 빈 문자열로 넘기므로 백엔드는 빈 값을 미설정과 같게 처리합니다. `AUTH_COOKIE_SECURE`는 연결하지 않습니다. 백엔드 기본값이 `true`이고 운영은 HTTPS라 넣을 필요가 없습니다. 값을 넣거나 바꿀 때는 `.env`를 root 전용으로 백업한 뒤 `sudoedit`로 고치고, 아래 절차대로 backend만 재생성합니다. 서명 키를 바꾸면 기존 로그인 토큰은 모두 무효가 되고, 인증번호 해시 키를 바꾸면 발송 중인 인증번호가 모두 무효가 됩니다. 네이버 발송 계정은 2단계 인증을 켠 뒤 네이버ID 보안설정에서 애플리케이션 비밀번호를 발급하고, PC 네이버 메일 환경설정의 IMAP/SMTP 사용을 `사용함`으로 둡니다([네이버 공식 안내](https://help.naver.com/service/30029/contents/21344?osType=COMMONOS)). 애플리케이션 비밀번호를 폐기·재발급하면 이전 값은 바로 쓸 수 없으므로 서버 `.env`를 고친 뒤 backend를 재생성하고, 회원가입 인증 메일 수신으로 발송을 확인합니다. readiness 성공만으로 메일 발송 성공을 판단하지 않습니다.

```sh
openssl rand -base64 48
```

운영 Compose는 edo1의 `/opt/eolssu/bin/install-runtime-compose`로 교체합니다. 설치 원본은 [install-runtime-compose.py](../scripts/install-runtime-compose.py)이며, edo1에서 root 소유 `0755`로 설치합니다. 서버의 현재 Compose를 바탕으로 후보를 만들고, 변경 범위를 확인한 뒤 후보 경로를 전달합니다.

```sh
sudo /opt/eolssu/bin/install-runtime-compose /path/to/reviewed-compose.yml
```

설치 도구는 운영 배포 잠금을 잡고 후보를 먼저 운영 환경으로 검사합니다. 이후 `root:jenkins 0640`을 적용하고 Jenkins 계정과 `ci.env.example`로 검사한 뒤 교체합니다. 직전 파일은 출력된 root 전용 백업 경로에 있습니다. 복원할 때는 그 경로를 같은 도구에 전달합니다. 운영 비밀값은 `root:root 0600`의 `/opt/eolssu/.env`에 둡니다.

실행 설정을 반영할 때는 변경 대상 서비스만 별도로 재생성합니다. 백엔드 배포 후 readiness HTTP 200·UP, Docker healthy, 프론트·MySQL ID와 물리 볼륨을 확인합니다. Jenkins 계정으로 설치 결과를 다시 검사할 수 있습니다.

```sh
sudo -u jenkins docker compose --env-file /opt/eolssu/config/ci.env.example \
  -f /opt/eolssu/config/compose.yml config --quiet
```

2026-11-06 백엔드 #279는 `compose.yml`이 `root:root` `0600`으로 바뀌어 이 검사에서 실패했습니다. 파일 내용이 같은 상태에서 소유 그룹과 읽기 권한을 복구했습니다. 같은 `develop` SHA `2c69ce33`을 사용한 Replay #280은 Compose 검사와 운영 배포에 성공했습니다. 운영 컨테이너의 JAR 경로와 API readiness `UP`을 확인했고 웹 수동 잡의 자동 실행과 예약은 모두 0건이었습니다. [Jira S15P31E102-528](https://ssafy.atlassian.net/browse/S15P31E102-528) · [백엔드 #279](http://127.0.0.1:18080/job/eolssu-backend/279/) · [백엔드 #280](http://127.0.0.1:18080/job/eolssu-backend/280/)

#285~#288에서도 같은 오류가 재발했습니다. 내 악보 R2 설정을 반영하면서 새 파일이 `root:root 0600`으로 들어갔습니다. 권한 복구 후 #289가 배포에 성공했습니다. 교체 경로를 바꾼 근거와 검증은 [S15P31E102-541 기록](../../docs/plans/S15P31E102-541.md)에 있습니다.

이름 전환 전 설정·릴리스와 SQL 전체 백업은 서버의 `/opt/backups/eolssu-S15P31E102-65-20261022T072645Z/`에 root 전용으로 보관했습니다. `receipt.json`에는 아티팩트 SHA-256·DB 서버 UUID·물리 볼륨·검증 결과가 있습니다. SQL과 실제 `.env`는 저장소나 로그로 복사하지 않습니다. `/opt/eolsu`도 변경 전 복귀 경로로 보존합니다.

전체 이름 전환을 되돌려야 하면 배포 담당자가 현재 작업·접속을 확인하고 새 프로젝트를 중지한 뒤 기존 프로젝트를 실행합니다. 볼륨을 삭제하지 않습니다.

```sh
sudo docker compose --env-file /opt/eolssu/.env \
  -f /opt/eolssu/config/compose.yml down --timeout 30
sudo docker compose --env-file /opt/eolsu/.env \
  -f /opt/eolsu/config/compose.yml up -d --pull never --wait --wait-timeout 120
```

## Let's Encrypt와 HTTPS

`/opt/app/infra`는 서버 소스 체크아웃이며 운영 설정은 `/opt/eolssu/config`에 있습니다. 소스가 오래되었거나 스크립트가 없는 버전에서는 `./scripts/init-letsencrypt.sh`를 실행할 수 없습니다. develop 병합 후 다음과 같이 소스를 갱신하고 실행합니다. 기존 작업 트리가 깨끗한지 먼저 확인합니다.

```sh
cd /opt/app
git status --short
git pull --ff-only origin develop
sudo apt-get install -y certbot
cd infra
./scripts/init-letsencrypt.sh
```

기본 도메인은 `k15e102.p.ssafy.io`이며 `--domain`으로 DNS 이름을 지정할 수 있습니다. 선택한 연락처는 `--email`로 전달합니다. 생략하면 이메일 없는 ACME 계정으로 등록합니다. DNS가 이 서버를 가리키고 외부에서 TCP 80·443에 접속할 수 있어야 합니다. 현재 Compose는 `/opt/eolssu/acme/.well-known`을 프론트의 `.well-known`에 읽기 전용으로 mount합니다. 초기화 도구는 이 mount를 찾아 고정 `/opt/eolssu/acme`를 Certbot webroot로 사용합니다. 이전 구성에는 실제 웹 릴리스 경로를 사용합니다. 이 방식은 [Certbot webroot 안내](https://eff-certbot.readthedocs.io/en/stable/using.html#webroot)를 따릅니다.

스크립트는 필요할 때 sudo로 재실행합니다. 기존 운영 `.env`와 기본 Compose를 보존하고, 인증서 발급·Nginx 설정 검사를 통과한 다음 `compose.tls.yml`로 프론트에 443과 인증서 읽기 전용 mount를 추가합니다. TLS 도메인의 HTTP 요청은 경로·쿼리·메서드를 유지하는 308로 HTTPS에 전환합니다. `/.well-known/acme-challenge/`는 같은 webroot의 파일만 HTTP로 제공하고, 토큰이 없으면 404를 반환합니다. 도메인 전용 80 server가 이 동작을 담당하며, `127.0.0.1`을 Host로 사용하는 기존 Jenkins 검사와 최초 인증서 발급은 기본 HTTP server를 계속 사용합니다. 백엔드·MySQL·DB 볼륨은 재생성하지 않습니다. 교체 중에는 프론트 요청이 잠시 끊길 수 있습니다. 설정·HTTPS 검증에 실패하면 이전 프론트 구성을 복구하며 설정 백업은 `/opt/eolssu/tls-*`에 남습니다. Jenkins 배포와 같은 lock을 사용합니다.

발급한 인증서는 `/etc/letsencrypt`에만 보관합니다. `certbot.timer`가 갱신을 확인하며, 갱신 성공 후 root 소유 `/etc/letsencrypt/renewal-hooks/deploy/eolssu-nginx`가 Nginx 설정 검사·reload를 수행합니다. 재실행은 유효한 인증서를 강제로 재발급하지 않습니다.

```sh
curl -I 'http://k15e102.p.ssafy.io/nested?check=redirect'
curl -I https://k15e102.p.ssafy.io/
curl -i https://k15e102.p.ssafy.io/api/actuator/health/readiness
sudo certbot renew --dry-run
sudo systemctl status certbot.timer --no-pager
python3 -B -m unittest discover -s infra/scripts/tests -v
RUN_NGINX_TESTS=1 python3 -B -m unittest discover -s infra/scripts/tests -v
```

HTTP 검사는 `308`과 원래 경로·쿼리가 포함된 HTTPS `Location`을 확인합니다. 기본 Python 검사는 외부 도구 없이 TLS 초기화의 복구 계약을 확인합니다. `RUN_NGINX_TESTS=1` 검사는 Docker·OpenSSL이 필요하며, 임시 인증서와 loopback 임의 포트의 격리 Nginx로 실제 HTTP 전환·ACME·내부 검사·HTTPS를 확인한 뒤 컨테이너와 fixture를 정리합니다. 같은 조건에서 `test_nginx_actuator.py`는 가짜 백엔드를 붙인 Nginx 설정 3개가 readiness와 일반 API만 넘기고 다른 점검 주소와 우회 경로(이중 슬래시·URL 인코딩·`../`·`;`)는 404로 막는지, `/api/` 본문을 25MiB까지 넘기고(녹음 업로드의 20MiB 판정은 백엔드) 그보다 크면 413으로 막는지 확인합니다. `test_nginx_blog_ipa.py`는 TLS 설정의 iOS QA IPA(`/ipas/`, 서버 `/opt/eolssu/ipas` 읽기 전용 마운트)가 캐시·검색 노출 없이 제공되는지, 블로그 편집 프록시(`/blog-editor/config·session·post·views` → `172.18.0.1:18121`) 밖의 `/blog-editor` 경로가 404인지 확인합니다.

HTTPS 적용 후 프론트를 수동 재생성할 때는 두 Compose 파일을 함께 사용합니다. 백엔드만 바꾸는 Jenkins 배포에는 기존 명령을 그대로 사용합니다.

```sh
sudo docker compose --env-file /opt/eolssu/.env \
  -f /opt/eolssu/config/compose.yml -f /opt/eolssu/config/compose.tls.yml \
  up -d --no-deps --pull never frontend
```

HTTPS를 되돌릴 때는 기본 Compose만으로 프론트를 재생성합니다. 인증서·원래 HTTP 설정·DB 데이터는 유지됩니다.

```sh
sudo docker compose --env-file /opt/eolssu/.env \
  -f /opt/eolssu/config/compose.yml up -d --no-deps --pull never frontend
```

## Jenkins

컨테이너 구성·경로·호스트 에이전트·백업·복구 절차는 [Jenkins 컨트롤러 운영 문서](../jenkins/controller/README.md)를 따릅니다. 실제 전환·검증 결과는 [Task 76 작업 기록](../../docs/plans/S15P31E102-76.md)에 남깁니다.

### 검증 JAR와 Gradle JVM 재사용 (510)

2026-11-05 14:19 KST에 `aa334ff5` 기준으로 운영 백엔드·프론트 잡에 적용했습니다. 기존 inline 잡 정의와 관리 파일 백업은 edo1의 `/var/backups/eolssu-jenkins-510-j4wj8_dy`에 있습니다. [스펙](../../docs/specs/jenkins-build-reuse.md) · [운영 적용과 실측](../../docs/knowledge/infra/jenkins-latency.md#운영-적용과-실측)

관리 backend Pipeline은 `python3 -B infra/jenkins/build-backend-image.py "$GIT_COMMIT" --dockerfile /opt/eolssu/config/backend-artifact.Dockerfile`을 호출합니다. 스크립트는 실행 JAR 하나만 임시 context에 넣어 `eolssu-backend:ci-<SHA>`를 만들고 이미지의 JAR 해시 일치를 검사합니다. 개발용 소스 Dockerfile과 이전 `/opt/eolssu/config/backend.Dockerfile`은 복구용으로 보존합니다. 각 Gradle 명령은 stage를 유지하며 daemon을 재사용하고 60초 유휴 종료를 설정합니다.

변경이 `develop`에 병합된 뒤 다음 순서로 반영합니다.

1. 실행 중인 잡을 취소하지 않고 현재 관리 파일과 실제 inline 잡 정의를 별도 백업합니다. 설치 전·후 SHA-256과 기존 stage·QA 예약·알림·트리거를 확인합니다.
2. `infra/jenkins/backend-artifact.Dockerfile`을 root 소유 0644의 `/opt/eolssu/config/backend-artifact.Dockerfile`에 먼저 설치합니다. 조회 시 실제 파일과 저장소 원본의 SHA-256이 같아야 합니다.
3. 관리 backend/frontend Pipeline의 변경을 운영 inline 정의와 대조해 반영합니다. Android/iOS QA 예약은 2026-11-15부터 저장소 [backend.Jenkinsfile](../jenkins/backend.Jenkinsfile)에도 있습니다(S15P31E102-943). 그래도 운영 정의에만 있는 환경 주입이 없는지 [일치 점검](../drift/README.md)으로 먼저 확인합니다. 병합 전 revision에는 helper·검사 파일이 없어 새 Pipeline이 실패하므로 미리 활성화하지 않습니다.
4. 실제 Jenkins linter 검사 후 정의를 저장하고 다시 읽어 해시와 기존 옵션·stage를 확인합니다. 정상 자동 실행에서 daemon 재사용, JAR 해시, 테스트·배포 상태와 대기/실행 시간을 나누어 확인합니다. 문제 시 백업 Pipeline을 복원하고 새 artifact Dockerfile은 사용하지 않습니다.

빠른 회귀 검사:

```sh
python3 -B -m unittest discover -s infra/jenkins/tests -v
python3 -B -m unittest discover -s infra/backup/tests -p test_deploy_hooks.py -v
```

JDK 21·설치된 Gradle 9.5.1이 있는 환경에서 JVM 재사용을 별도로 재현합니다. 첫 인자는 실제 Gradle 실행 파일의 절대 경로이며 wrapper의 설치된 distribution에서도 찾을 수 있습니다. fixture는 전용 home과 256MiB heap을 사용하며 그 home의 daemon만 종료합니다.

```sh
python3 -B infra/jenkins/tests/verify_gradle_reuse.py /absolute/path/to/gradle-9.5.1/bin/gradle --receipt /tmp/gradle-reuse-receipt.json
```

### 운영 적용 상태

Jenkins LTS `2.580.1` 컨트롤러는 `/opt/eolssu/jenkins/compose.yml`의 `eolssu-jenkins-controller-1` 컨테이너로 실행합니다. 기존 홈 `/var/lib/jenkins`와 UID/GID 111:113을 유지하며 `127.0.0.1:8080`에서만 접속할 수 있습니다. 관리자 인증·CSRF·가입 차단·인바운드 TCP 에이전트 포트 차단은 보존했습니다. 기존 apt의 `jenkins.service`는 중단·비활성화했고 패키지는 복구를 위해 남겼습니다.

빌드·배포는 같은 서버의 `eolssu-jenkins-agent.service`가 기존 `jenkins` 계정·호스트 도구·Docker 그룹·sudoers로 실행합니다. `edo1-host` 노드의 executor는 1개이고 컨트롤러는 0개입니다. 웹 수동 배포 잡은 같은 계정·도구를 쓰는 전용 노드 `edo1-web`(`eolssu-jenkins-web-agent.service`, executor 1개, 전용 라벨만 수용)에서 실행해 백엔드 빌드를 기다리지 않습니다([적용 절차](../jenkins/controller/README.md#웹-수동-배포-전용-슬롯)). 현재 workspace는 `/var/lib/jenkins-agent/workspace`이며 과거 workspace와 빌드 이력은 보존합니다. 에이전트는 loopback HTTP 주소로 WebSocket 연결합니다.

관리자 이름은 `eolssu-admin`입니다. 초기 비밀번호는 서버의 `/var/lib/jenkins/secrets/eolssu-admin-password`에 0600으로 보관하며 채팅·저장소에 기록하지 않습니다. 사용자 본인의 SSH 터미널에서 다음과 같이 조회할 수 있습니다.

```sh
ssh edo1 'sudo cat /var/lib/jenkins/secrets/eolssu-admin-password'
ssh -N -L 18080:127.0.0.1:8080 edo1
```

터널을 유지한 상태에서 브라우저로 `http://127.0.0.1:18080/`에 접속합니다. 로컬 18080이 사용 중이면 다른 로컬 포트를 선택합니다. Jenkins의 인증 정보를 실제로 변경했다면 위 초기 비밀번호 파일과 서버 전용 CLI 인증 파일도 함께 정리해야 합니다.

백엔드 잡은 `eolssu-backend`, GitLab 연결은 Credentials `eolssu-gitlab`을 사용합니다. 기존 서버 Git 연결 정보를 Jenkins의 암호화된 Credentials 저장소에 옮기고 잡에는 비밀값 없는 저장소 URL·`develop`만 지정합니다. 구성 원본은 [infra/jenkins/backend.Jenkinsfile](../jenkins/backend.Jenkinsfile)이며 서버 복사본은 `/opt/eolssu/config/backend.Jenkinsfile`입니다.

현재 MR의 인프라 파일이 아직 develop에 없으므로 잡은 서버의 관리된 Pipeline 정의에서 develop 소스를 명시적으로 checkout합니다. Dockerfile과 Dockerfile 전용 ignore 규칙도 `/opt/eolssu/config`의 관리 파일을 사용합니다. 앱 소스는 수정하지 않습니다. CI 구성 검사는 실제 DB 비밀번호 대신 `ci.env.example`의 고정 데이터만 사용하며 서비스를 기동하지 않습니다.

잡은 `develop`을 1분마다 SCM polling으로 확인합니다. 변경이 있을 때만 실행하며 직접 Push로 develop이 바뀌는 경우도 감지합니다. 별도 GitLab webhook이나 Jenkins 공개 포트는 필요하지 않습니다. 정확한 ref `refs/heads/develop`만 checkout하고, 동시 실행을 막으며 20분 제한을 둡니다. 경로 필터는 없습니다.

실행 순서는 Gradle 빌드·테스트 → Compose/Nginx 검사 → 이미지 빌드·JAR 보관 → 후보 배포입니다. 격리 후보는 운영 DB 없이 liveness를 확인하고 JAR을 추출한 뒤 정리합니다. 이후 운영 DB 마이그레이션을 확인하고 비활성 슬롯을 실제 운영 설정으로 기동합니다.

블루그린이 활성화되면 두 슬롯은 같은 Compose 네트워크·MySQL을 사용합니다. 새 슬롯의 readiness가 UP이면 Nginx 설정을 검사하고 리로드합니다. 실제 HTTPS에서 선택된 슬롯과 준비 상태를 확인한 뒤 활성 슬롯·JAR을 기록합니다. 전환 전에 접수된 요청·악보 변환이 남아 있는 동안 이전 슬롯이 계속 실행됩니다. 이전 슬롯을 내릴 때(`compose stop`, 종료 대기 250초)는 이미 받은 연주 분석을 `eolssu.analysis.shutdown-wait`(기본 65초)까지 끝내고, 새 분석은 받지 않습니다. 종료 로그와 확인 방법은 [S15P31E102-947](../../docs/plans/S15P31E102-947.md#운영-적용-절차)을 따릅니다.

배포 도구 원본은 [deploy-backend.py](../jenkins/deploy-backend.py), 서버 설치 위치는 `/opt/eolssu/bin/deploy-backend`입니다. root 소유 0755로 설치하고 Jenkins에는 아래 도구의 sudo 실행만 허용합니다. 운영 `.env`는 root 전용 0600을 유지합니다. Jenkins의 기존 Docker 접근도 서버 수준 권한이므로 잡 설정·실행은 승인된 관리자만 사용합니다.

```sudoers
jenkins ALL=(root) NOPASSWD: /opt/eolssu/bin/deploy-backend *
```

도구는 인자 하나의 40자리 SHA·이미지 revision·원격 develop 이력 소속을 확인합니다. Python isolated mode와 고정 PATH를 사용하고 임의 파일·셸 명령 인자를 받지 않습니다. 빌드 중 develop이 전진해도 선택한 SHA를 유지합니다. 공유 lock 안에서 중단된 배포를 먼저 복구한 후 현재 활성 JAR 릴리스 SHA와 Git 조상 관계를 비교하고, 이미 배포된 후속 버전을 이전 후보가 덮어쓰는 경우를 차단합니다. 같은 SHA는 기존 불변 파일·건강 검사를 유지합니다. 실패 journal의 후보 SHA는 배포 기준으로 쓰지 않습니다. Git 이력을 확인할 수 없거나 이력이 분기되면 순서를 증명할 수 없어 실패합니다. SHA 도입 전 초기 릴리스에는 비교 기준이 없으므로 첫 관리 배포를 허용합니다.

### 운영 DB 마이그레이션 (S15P31E102-426)

`ops` 프로필의 Flyway는 계속 끕니다. 배포 도구가 후보 검증·JAR 추출 뒤, 컨테이너 교체 직전에 같은 공유 lock 안에서 마이그레이션을 적용합니다. 백업 시점은 배포 시작 시의 `prechange`(DB·설정)입니다. 결정 근거와 리허설 결과는 [운영 Flyway 마이그레이션 자동화](../../docs/knowledge/infra/ops-flyway-auto-migration.md)에 있습니다.

1. `Check database migrations`: 후보 JAR의 `db/migration`과 운영 `flyway_schema_history`를 비교합니다. 대기 중인 마이그레이션이 없으면 Flyway를 실행하지 않고 그대로 교체합니다.
2. `Apply database migrations`: 실행 중 백엔드의 DB 연결값으로 후보 JAR을 `db` 프로필 일회성 컨테이너(`eolssu-migrate-<SHA 12자리>`, 웹·스케줄러·메일 꺼짐)에서 실행합니다. 대상 버전은 후보의 최대 버전입니다. 운영 URL의 2초 `socketTimeout`은 300초로 바꾸고, `lock_wait_timeout`·`innodb_lock_wait_timeout`은 5초로 둡니다. 로그는 root 전용 `/opt/eolssu/releases/<SHA>/migration.log`에 남깁니다.
3. 적용 뒤 이력을 다시 읽어 성공을 확인하고 기존 교체·health 검사를 이어갑니다. 스키마를 바꾼 배포는 사후 백업을 `postmigration`(DB·설정)으로 보냅니다.

다음 경우에는 교체 전에 배포를 멈추고 기존 앱이 그대로 동작합니다.

| 멈춤 원인 | 의미 |
| --- | --- |
| `duplicate Flyway version` / `unexpected file` | 후보 JAR에 같은 버전이나 규칙에 맞지 않는 파일이 있음. 백엔드 `MigrationFileTests`가 test 단계에서 먼저 막는다 |
| `failed Flyway migration in history` | 이전 적용이 실패해 `success=0` 행이 남음 |
| `database has a migration the candidate lacks` | 운영에 적용된 버전이 후보에 없거나 이름이 다름(DB가 후보보다 앞섬) |
| `applied migration was edited` | 적용된 파일의 checksum이 운영 기록과 다름 |
| `older than applied history` | 이미 적용된 최대 버전보다 낮은 미적용 버전이 있음 |
| `drops, renames or deletes data` | 주석·문자열 밖에 테이블·컬럼 DROP, RENAME(인덱스 제외), CHANGE, TRUNCATE, DELETE가 있음 |
| `earlier migration container remains` | 이전 마이그레이션 컨테이너가 남아 있음(실패·시간 초과) |
| `migration failed` / `did not finish in time` | Flyway가 실패했거나 300초 안에 끝나지 않음. 컨테이너를 강제 종료하지 않는다 |

JAR 롤백은 스키마를 되돌리지 않습니다. 그래서 자동 적용 대상은 이전 릴리스도 읽을 수 있는 추가형 변경입니다. 파괴적 변경은 사람이 이전 릴리스 호환성과 복구 방법을 검토한 뒤 해당 파일의 SHA-256을 승인 파일로 남깁니다. 다음 배포가 같은 내용일 때만 적용합니다.

```sh
# 검토한 커밋의 파일로 해시를 만든다. 파일 이름은 후보 JAR의 이름과 같아야 한다.
sha256sum backend/src/main/resources/db/migration/V20__example.sql
ssh edo1 'sudo install -d -m 700 /opt/eolssu/migration-approvals && echo <위 해시> | sudo tee /opt/eolssu/migration-approvals/V20__example.sql >/dev/null'
```

마이그레이션이 실패하면 같은 SHA를 다시 배포하지 않습니다. MySQL DDL은 중간까지 반영될 수 있으므로 먼저 실제 스키마를 확인합니다.

```sh
ssh edo1 'sudo docker ps -a --filter label=com.eolssu.stage=migration'
ssh edo1 'sudo tail -n 80 /opt/eolssu/releases/<SHA>/migration.log'
ssh edo1 'sudo docker exec eolssu-runtime-mysql-1 sh -c '"'"'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -D eolssu -e "SELECT version,script,success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3"'"'"''
```

남은 변경을 `prechange` 백업과 대조해 정리하거나 백업으로 복구한 뒤 실패 행을 정리합니다. `flyway repair`로 이력만 맞추지 않습니다. 원인을 고친 마이그레이션은 새 커밋으로 병합하고, 확인을 마친 컨테이너는 `sudo docker rm <이름>`으로 지운 뒤 다음 배포를 진행합니다. 실행 중인 컨테이너는 끝날 때까지 지우지 않습니다.

서버 반영 순서는 `/opt/eolssu-backup/backup.py`(`postmigration` 지원)를 먼저 설치하고, 그다음 `/opt/eolssu/bin/deploy-backend`를 root 소유 0755로 교체하는 것입니다. 순서를 바꾸면 스키마를 바꾼 배포의 사후 백업이 `unsupported operation`으로 실패합니다. 2026-11-03 이 순서로 edo1에 설치했고 edo2의 `backup.py`도 같은 버전으로 맞췄습니다. 첫 자동 적용은 Jenkins #214의 V19입니다. 이전 파일과 설치 확인 결과는 [운영 Flyway 마이그레이션 자동화](../../docs/knowledge/infra/ops-flyway-auto-migration.md)의 운영 설치에 있습니다.

## 프론트 웹 수동 배포

수동 배포 설정을 운영에 반영한 뒤에는 백엔드의 develop 자동 흐름에서 웹 잡을 예약하지 않습니다. 백엔드 배포 도구, 인증서 초기화, Nginx health 라우팅과 HTTPS 리다이렉트 검사는 백엔드 잡에서 실행합니다. 공통 앱 로직은 Android QA의 `:shared:testAndroidHostTest`에서도 검증합니다.

웹을 배포하려면 성공한 `eolssu-backend` 빌드에서 검증한 develop 전체 커밋 SHA를 확인합니다. `eolssu-frontend-web-수동배포용`의 **Build with Parameters**에서 그 SHA를 `REVISION`에 입력합니다. 전용 잡은 체크아웃 SHA와 develop 이력 포함 여부를 먼저 검증합니다. 완료 기준은 전용 잡의 SUCCESS와 `/opt/eolssu/frontend-deployment.json`, 운영의 `/__eolssu_release.json`에서 확인한 SHA 일치입니다. [웹 수동 배포 잡](http://127.0.0.1:18080/job/eolssu-frontend-web-%EC%88%98%EB%8F%99%EB%B0%B0%ED%8F%AC%EC%9A%A9/)

이전 웹 빌드는 비활성화된 `eolssu-frontend-이력보관용`에서 조회합니다. [웹 빌드 이력](http://127.0.0.1:18080/job/eolssu-frontend-%EC%9D%B4%EB%A0%A5%EB%B3%B4%EA%B4%80%EC%9A%A9/)

프론트 잡은 다음 명령을 Jenkins 사용자로 실행합니다. JDK 21·Docker·Google Chrome이 필요하며 Node·Yarn·Wasm tooling은 Gradle이 관리합니다. Linux 서버에서 웹 타깃만 빌드·테스트합니다. Android·iOS 패키지 배포는 포함하지 않습니다.

```sh
cd frontend
CHROME_BIN=/usr/bin/google-chrome ./gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 \
  :shared:wasmJsTest :shared:jsTest :webApp:wasmJsBrowserDistribution
```

수동 웹 잡에서 프론트 배포 도구와 JS·Wasm 브라우저 테스트를 실행합니다. JUnit 결과와 `webApp/build/dist/wasmJs/productionExecutable/`를 보관하고 [frontend.Dockerfile](../jenkins/frontend.Dockerfile)로 커밋 revision label을 붙인 `eolssu-frontend:ci-<SHA>`를 만듭니다. 관리된 Pipeline 원본은 [frontend.Jenkinsfile](../jenkins/frontend.Jenkinsfile)입니다.

프론트 `Clean workspace`는 기존 Git 작업공간에서 추적 파일을 복원하고, Git이 무시하지 않는 미추적 파일만 정리합니다. `.gradle`, `.kotlin`, `build`, `node_modules`의 캐시·산출물은 유지하며 Gradle의 입력/출력 검증으로 재사용 여부를 결정합니다. 소스는 매번 전달받은 전체 `REVISION`으로 checkout합니다. 처음 실행하거나 Git 작업공간이 없으면 전체 정리 후 checkout합니다. clone 중단으로 `.git`만 남고 유효한 HEAD 커밋이 없을 때도 helper의 종료 코드 2를 받아 재초기화합니다. 그 밖의 정리 오류는 작업공간을 삭제하지 않고 빌드를 실패시킵니다. 수동 실행에도 production 최적화와 20분 제한을 적용합니다.

[prepare-frontend-workspace.sh](../jenkins/prepare-frontend-workspace.sh)를 root 소유 0755의 `/opt/eolssu/config/prepare-frontend-workspace.sh`에 **먼저** 설치한 뒤 관리된 `frontend.Jenkinsfile`을 갱신합니다. 스크립트는 저장소 루트에서만 실행됩니다. 캐시 손상이 의심될 때는 실행 중인 잡이 없는지 확인하고 해당 프론트 작업공간의 캐시만 수동 초기화합니다. Jenkins 사용자 홈 전체의 Gradle 캐시는 삭제하지 않습니다.

[deploy-frontend.py](../jenkins/deploy-frontend.py)를 root 소유 `/opt/eolssu/bin/deploy-frontend`에 설치하고 Jenkins에는 이 도구의 실행만 추가로 허용합니다. SHA 검증·원자적 파일 교체·HTTP 검사·공유 lock은 인접한 root 소유 `deploy-backend`의 검증된 공통 함수를 사용하므로 두 도구를 함께 관리합니다. Jenkins에는 운영 `.env` 읽기 권한을 주지 않습니다.

```sudoers
jenkins ALL=(root) NOPASSWD: /opt/eolssu/bin/deploy-frontend *
```

도구는 SHA·이미지 label·원격 develop 이력 소속과 현재 활성 웹 릴리스 대비 배포 순서를 확인하고, 비공개 internal network의 후보 Nginx에서 **모든 정적 파일의 SHA-256과 Wasm MIME**을 검사합니다. HTML fallback이 JS 누락을 가리는 경우도 실패합니다. symlink·특수 파일·필수 번들 누락·256MiB/5,000개 초과 산출물은 거부합니다. 통과한 이미지에서 추출한 파일만 `/opt/eolssu/releases/<SHA>/web`에 보관하며 다른 내용으로 덮어쓰지 않습니다.

배포 journal에 이전 웹 경로를 기록한 다음 실제 `.env`의 `WEB_DIST_DIR`만 원자적으로 교체하고, HTTP+TLS Compose로 프론트만 재생성합니다. HTTP 정적 자산·HTTPS 인증서·API 프록시·실제 bind mount와 백엔드/MySQL ID·DB 볼륨을 확인합니다. 새 릴리스의 `/__eolssu_release.json`에서 SHA·manifest 해시를 조회할 수 있습니다. 프론트 교체 중에는 짧은 연결 중단이 있을 수 있습니다.

백엔드·프론트 배포는 같은 lock을 사용하고 최대 120초 기다립니다. 실패하면 이전 프론트를 복구하고, 강제 종료로 남은 `deploying`·`rollback_failed` journal은 다음 실행에서 먼저 복구합니다. 동일 SHA·이미지·정적 파일이 이미 배포돼 있으면 상태·실제 응답을 확인하고 재생성하지 않습니다. 원인 제거 후 검증한 SHA로 프론트 잡을 다시 실행합니다.

```sh
ssh edo1 'sudo cat /opt/eolssu/frontend-deployment.json'
curl -fsS https://k15e102.p.ssafy.io/__eolssu_release.json
python3 -B -m unittest discover -s infra/jenkins/tests -v
```

전용 잡을 만들 때는 기존 웹 잡의 XML을 백업하고 실행·예약이 비었는지 확인합니다. 저장된 웹 Pipeline을 관리 파일과 대조하고 Jenkins linter를 통과한 정의를 새 잡에 등록합니다. 기존 잡을 비활성화한 뒤 새 잡을 활성화합니다. 두 잡의 트리거가 비어 있는지, 기존 이력 URL이 열리는지, 새 잡에서만 `REVISION`으로 실행되는지 확인합니다. 전용 잡을 되돌리려면 실행과 예약이 없는 상태에서 전용 잡부터 비활성화하고 백업한 기존 잡 XML을 복원합니다. [자동 예약 제거](../../docs/plans/S15P31E102-520.md) · [전용 잡 분리](../../docs/plans/S15P31E102-527.md)

웹 릴리스를 복구할 때는 실행 중인 배포가 없는지 확인합니다. journal의 `previous_web`를 확인해 `sudoedit /opt/eolssu/.env`에서 `WEB_DIST_DIR`만 복구하고 위의 두 Compose를 사용하는 프론트 재생성 명령을 실행합니다. 이전 릴리스·journal·설정 백업은 보존하고 DB·볼륨을 내리지 않습니다.

이전 인증서 설정을 고정 경로로 옮길 때는 프론트에 ACME mount를 적용한 후 **도메인별 매핑도 함께** 바꿉니다. 서버의 Certbot 2.9는 `reconfigure --webroot-path`로 기존 `webroot_map`을 바꾸지 못하고, `--webroot-map`을 추가하면 도메인 변경으로 인식해 거부합니다. 실제 시험에서 두 동작을 확인했습니다. 이번 설치에서는 root 전용으로 원본을 백업한 뒤 ConfigObj로 갱신 설정의 `renewalparams.webroot_path`와 `renewalparams.webroot_map` 두 항목만 원자적으로 변경하고, 아래 명령으로 **저장된 설정 자체**와 reload hook을 검증했습니다. 실패하면 설정·프론트를 이전 상태로 복구했습니다. 운영 인증서는 강제 재발급하지 않았습니다.

```sh
sudo certbot renew --cert-name k15e102.p.ssafy.io --dry-run \
  --run-deploy-hooks --no-random-sleep-on-renew
```

실제 설치·활성화·자동 실행·복구 검증과 백업 위치는 [Task 73 작업 기록](../../docs/plans/S15P31E102-73.md)을 따릅니다.

### 블루그린 최초 설치와 복구

[스펙](../../docs/specs/backend-blue-green.md) · [검증 기록](../../docs/plans/S15P31E102-604.md)

최초 설치는 유지보수 시간에 진행합니다. 새 마운트를 적용하려면 Nginx와 블루 슬롯을 한 번 재생성해야 합니다. 이후의 백엔드 릴리스에서는 Nginx를 리로드해 전환합니다.

1. 기존 Jenkins 자동 배포를 일시 정지하고 실행 중인 배포와 신규 악보 업로드를 정리합니다. `DeploymentSlot.class`를 포함한 호환 JAR을 기존 배포 도구로 먼저 설치합니다. `deploying`·`rollback_failed` 상태와 진행 중인 악보 인식·음원 작업을 해소합니다. 활성 JAR·Compose·Nginx·배포 도구·Jenkins 정의를 백업합니다. 운영 부하와 함께 JVM 하나를 더 실행할 CPU·메모리·DB 연결 여유를 확인합니다.
2. root로 라우팅 디렉터리와 임시 원본·락 전용 디렉터리를 준비합니다. 임시 원본은 기존 config·녹음 백업 대상 밖에 두고 웹 경로에서 분리합니다.

   ```sh
   sudo install -d -o root -g root -m 0755 /opt/eolssu/config/backend-route
   sudo install -d -o 10001 -g 10001 -m 0700 /opt/eolssu/backend-shared
   printf '%s\n' 'set $backend http://backend:8080;' |
     sudo tee /opt/eolssu/config/backend-route/active.conf >/dev/null
   sudo chmod 0644 /opt/eolssu/config/backend-route/active.conf
   ```

3. `sudoedit /opt/eolssu/.env`에서 `BACKEND_SLOT=backend`를 추가합니다. `BACKEND_JAR`는 호환 JAR을 가리켜야 합니다. 로컬 녹음 읽기 URL이 과거 기본값으로 명시돼 있으면 `http://frontend:8081/api/recordings/read`로 바꿉니다. 운영 슬롯은 OMR 콜백을 자기 슬롯으로 고정합니다.
4. [Compose 설치 도구](../scripts/install-runtime-compose.py)로 새 `compose.yml`을 검증해 설치합니다. 관리 중인 `nginx.conf`와 운영 도메인으로 치환한 `nginx.tls.conf`도 함께 설치합니다. 기존 TLS·ACME 경로를 확인하고 아래처럼 대상만 재생성합니다. MySQL과 볼륨은 계속 사용합니다.

   ```sh
   sudo docker compose --env-file /opt/eolssu/.env -f /opt/eolssu/config/compose.yml \
     up -d --no-deps --force-recreate --pull never --wait backend
   sudo docker compose --env-file /opt/eolssu/.env -f /opt/eolssu/config/compose.yml \
     -f /opt/eolssu/config/compose.tls.yml up -d --no-deps --force-recreate --pull never --wait frontend
   curl -fsS https://k15e102.p.ssafy.io/api/actuator/health/readiness
   curl -fsS https://k15e102.p.ssafy.io/api/terms
   ```

5. root가 관리하는 `deploy-backend`·`deploy-frontend`·Jenkins 정의와 운영 점검 도구를 같은 릴리스로 설치합니다. [Prometheus 대상](../monitoring/prometheus.yml)을 `frontend:8081`로 반영한 뒤 수집을 확인합니다. 공개 HTTPS의 `/api/internal/`·`/api/actuator/prometheus` 차단과 Docker의 8081·8082 비공개 설정을 확인하고 Jenkins를 재개합니다. 최초 설치 실패 시 자동 배포를 정지한 채 백업한 설정·도구·호환 JAR로 단일 슬롯을 복구합니다.

정기 배포는 기존 공유 lock과 `/opt/eolssu/deployment.json`을 사용합니다. `preparing` 또는 `deploying` 도중 끊기면 다음 실행이 이전 슬롯을 먼저 복구합니다. `retire_slot`이 남아 있으면 요청·변환을 처리 중인 슬롯을 유지하고, 다음 배포 또는 `--recover`가 종료 조건을 다시 확인합니다. 이 상태에서 웹 배포는 백엔드 복구·종료가 완료될 때까지 대기합니다. 유지보수나 백업 복원으로 직접 기동할 때도 활성 슬롯 하나만 지정합니다.

```sh
sudo /opt/eolssu/bin/deploy-backend --active-container
sudo /opt/eolssu/bin/deploy-backend --recover
sudo cat /opt/eolssu/deployment.json
```

자동 원복이 실패하면 Jenkins를 정지한 채 두 슬롯·라우팅 파일·journal을 확인합니다. 이전 슬롯이 기동할 수 있도록 보관된 JAR과 설정을 복구한 뒤 `--recover`를 실행합니다. 이미 확정된 배포 이후에는 호환되는 이전 변경을 새 커밋으로 되돌려 정기 배포 경로를 사용합니다. 적용된 Flyway 변경은 앱 전환과 별개로 취급합니다.


서버의 관리된 Pipeline·Dockerfile·배포 도구는 MR 병합만으로 자동 덮어쓰지 않습니다. 이 파일 자체를 바꿀 때는 서버 복사본을 별도 검토·설치하고 linter와 배포 검사 후 잡 정의도 갱신합니다. 앱 소스는 매 실행 develop에서 새로 checkout합니다.

```sh
sudo docker compose -f /opt/eolssu/jenkins/compose.yml ps
sudo docker compose -f /opt/eolssu/jenkins/compose.yml logs --tail 50 controller
sudo systemctl status eolssu-jenkins-agent.service
sudo journalctl -u eolssu-jenkins-agent.service --since today --no-pager
```

원시 Jenkins 설정·로그에는 인증 정보가 포함될 수 있으므로 공유 전 비밀값을 확인합니다. 잡 실행 결과·최종 배포 상태는 [작업 기록](../../docs/plans/S15P31E102-65.md)을 따릅니다.


### 단계별 실패 표시 (S15P31E102-262)

운영 backend는 15개, frontend는 20개 상위 stage로 나눠 컴파일·테스트·최적화·번들·구성 검사·이미지·보관·후속 잡 예약을 구분합니다. frontend의 JS/Wasm JUnit은 각 browser test 단계에서 따로 보관합니다. Gradle 명령 분리에 따라 JVM 시작 비용이 추가되며 기존 20분 제한은 유지합니다. 전체 앱 빌드는 다음 정상 CI 실행의 소요 시간과 결과로 확인합니다.

배포 stage는 [deployment-stages.groovy](../jenkins/deployment-stages.groovy)를 `/opt/eolssu/config/deployment-stages.groovy`에서 읽습니다. 이 파일도 root 소유 0644로 관리합니다. 실제 배포 프로세스가 출력하는 phase 이벤트를 동시에 관찰해 잠금·백업·순서 검사·후보 기동·산출물·서비스 재생성·상태 검사·롤백·정리를 개별 하위 stage로 표시합니다. 별도 배포 명령으로 쪼개지 않아 공유 lock·복구 journal의 일관성을 유지합니다. 실패한 첫 phase는 빌드 설명에 남으며, 실패·강제 종료로 완료 이벤트가 없거나 phase가 전혀 없어도 후속 잡 예약을 막습니다. stdout/stderr는 실패하더라도 `deployment.jsonl`/`frontend-deployment.jsonl`로 보관합니다.

Pipeline Graph View에서 빌드를 열어 `Stages`의 `Deploy ... transaction`과 phases 분기를 펼쳐 세부 실패 stage를 확인합니다. stage 실행 시간은 관찰 시점 기준이고 정확한 프로세스 프로파일링 수치는 아닙니다. 기존 실행 이력에는 과거 stage 구조가 남으며 새 실행부터 적용됩니다.

블로그 잡은 별도 blog 브랜치의 `Jenkinsfile`·`scripts/deploy.sh`를 사용합니다. 작업 S15P31E102-262의 blog 브랜치 변경은 정리·checkout·Hugo 준비/빌드·산출물 검사·Pages 원격 준비·파일 복사·커밋·Push의 9단계로 나눕니다. blog 변경은 blog 대상 MR로 전달하고 기존 SCM 정의와 polling을 보존합니다. 원격 반영/병합 전에는 운영 blog 잡에 새 stage가 적용되지 않습니다.

검증은 `python3 -B -m unittest discover -s infra/jenkins/tests -v`, `python3 -B -m unittest discover -s infra/backup/tests -p test_deploy_hooks.py -v`로 수행합니다. 별도 임시 Jenkins 잡에서 성공·phase 실패/롤백·프로세스 중단·로그 없는 실패를 주입해 실제 stage 결과와 후속 단계 차단을 검증했습니다. 앱 배포나 공개 Pages 게시를 이 검증에 사용하지 않습니다.

### Python 테스트 잡 (S15P31E102-945)

`eolssu-python-tests`는 `eolssu-backend`가 성공·불안정·실패로 끝날 때마다, 그리고 매주 월요일 새벽에 전체 검사로 develop을 다시 받아 직전 성공 실행과의 변경 경로에 맞는 infra·catalog·data·ai 테스트만 실행합니다. 배포·DB·외부 서비스는 건드리지 않습니다. 실패 알림은 `eolssu-*` 잡을 읽는 기존 [Jenkins 실패 알림](../jenkins/notifications/README.md)이 그대로 보냅니다. 선택 규칙·실측·격리 테스트는 [Python 테스트 CI 기록](../../docs/knowledge/infra/python-tests-ci.md)을 따릅니다. **아직 운영 Jenkins에 등록하지 않았습니다.** 발표(11-18) 동결이 끝난 뒤 사용자 확인을 받고 적용합니다.

| 파일 | 역할 |
| --- | --- |
| [python-tests.Jenkinsfile](../jenkins/python-tests.Jenkinsfile) | 잡 정의. 서버 복사본 `/opt/eolssu/config/python-tests.Jenkinsfile`(root 0644) |
| [python-tests.py](../jenkins/python-tests.py) | `plan`(변경 경로→묶음), `run <묶음>`(환경 준비·실행), `run <묶음> --quarantine` |
| [python-tests-requirements.txt](../jenkins/python-tests-requirements.txt) | data·catalog 고정 의존성. ai는 `ai/uv.lock` |
| [register-python-tests.groovy](../jenkins/register-python-tests.groovy) | 잡 생성 전용. 이미 있으면 같은 정의일 때만 통과하고 덮어쓰지 않음 |

적용 전제는 Jenkins 사용자로 `python3`와 `uv`(ai 이미지와 같은 0.13.6)를 실행할 수 있고, 첫 실행에서 uv가 Python 3.12와 패키지를 받을 외부 연결이 있는 것입니다. 받은 Python·패키지는 Jenkins 사용자의 uv 캐시에 남아 다음 실행부터 재사용됩니다.

1. 실행 중·대기 중인 빌드가 없을 때 edo1에서 `sudo -u jenkins python3 --version`, `sudo -u jenkins uv --version`을 확인합니다. uv가 없으면 버전을 고정해 설치한 뒤 진행합니다.
2. 검토한 커밋의 `python-tests.Jenkinsfile`을 `/opt/eolssu/config/`에 root 소유 0644로 설치하고 Jenkins linter를 통과시킵니다.
3. 기존 관리자 CLI의 `groovy =`로 `register-python-tests.groovy`를 실행합니다. 이어서 **Build Now**로 한 번 실행해 매개변수와 트리거를 등록합니다(기준 SHA가 없어 첫 실행은 모든 묶음을 검사합니다). 그리고 단계별 결과와 소요 시간을 [기록](../../docs/plans/S15P31E102-945.md)에 남깁니다.
4. 다음 develop 병합 뒤 `eolssu-backend` 완료 직후 이 잡이 예약되는지, 빌드 설명의 SHA·묶음이 변경 경로와 맞는지 확인합니다.
5. [일치 점검 manifest](../drift/manifest.json)에 `/opt/eolssu/config/python-tests.Jenkinsfile`과 `eolssu-python-tests` 잡 두 줄을 더하고 `python3 -I -B infra/drift/check.py --host edo1`로 일치를 확인합니다. 등록 전에 넣으면 `서버 없음`으로 실패하므로 등록 뒤에 넣습니다.

되돌릴 때는 실행·예약이 없는 상태에서 잡을 비활성화합니다. 다른 잡과 서버 파일은 바꾸지 않으므로 함께 되돌릴 대상이 없습니다. 로컬·리뷰 검증은 `python3 -B -m unittest discover -s infra/jenkins/tests -p test_python_tests.py -v`와 `python3 -B infra/jenkins/python-tests.py run <묶음> --work <새 폴더>`로 합니다.

## 테스트 회원의 인증 만료 예외

`AUTH_NON_EXPIRING_USER_IDS`는 기본 빈 목록이며, 검토한 테스트 회원 ID에만 설정합니다. 이 설정을 전달하는 관리된 Compose와 V7이 적용된 백엔드 릴리스가 필요합니다. 운영 `ops`는 Flyway를 실행하지 않으므로 마이그레이션은 먼저 `db` 프로필로 별도 수행합니다. 실제 계정 생성·배포 순서와 검증은 [S15P31E102-260](../../docs/plans/S15P31E102-260.md)을 따릅니다. 설정을 비우고 백엔드에 다시 주입하면 예외 토큰을 거부합니다. 기존 비밀값을 출력하거나 전체 `.env`를 덮어쓰지 않습니다.

구버전 JAR로 롤백할 때는 허용 목록을 끄는 것만으로 충분하지 않습니다. NULL 만료 세션을 먼저 만료·폐기해 구버전의 non-null 매핑과 호환시킨 뒤 JAR을 바꿉니다. 검증한 SQL과 순서는 [Task 260의 복구 절차](../../docs/plans/S15P31E102-260.md#예외-중단과-바이너리-롤백)를 따릅니다.

## R2 버킷명·보관 규칙 점검

`RECORDING_STORAGE_BUCKET`에는 R2 버킷 이름을 지정합니다. API 토큰의 표시 이름과 구분합니다. 현재 버킷은 `eolssu-recordings`이며 토큰 이름 `eolssu-backend-recordings`를 넣으면 객체 PUT·DELETE가 403 AccessDenied가 됩니다. readiness는 R2 객체 접근을 검사하지 않으므로 변수 존재·기동 성공만으로 저장소 연결을 판정하지 않습니다.

Cloudflare 버킷 Settings에서 사용자 도메인 없음·Public Development URL disabled, `recordings-expire-after-30-days`의 접두사 `recordings/`·객체 생성 후 30일 삭제·Enabled를 확인합니다. 기본 미완료 multipart 7일 중단 규칙은 별도로 보존합니다. 새 검증 객체의 `x-amz-expiration`에서 30일 만료 시각과 규칙명을 확인하며, 실제 자동 삭제 실행과 구분합니다. 저장소 접근 키·서명 URL은 출력하지 않습니다.

2026-10-29의 버킷명 정정·backend 재생성·서명 읽기·만료·삭제 검증과 복구 위치는 [Task 272](../../docs/plans/S15P31E102-272.md)·[R2 검증 기록](../../docs/knowledge/infra/r2-storage-verification.md)을 따릅니다. 일반 재현은 테스트 전용 버킷을 쓰며 운영 객체 검사는 범위·정리 대상을 먼저 승인받습니다.

### 일반·QA 앱의 App Links 지문

기존 `AUTH_ANDROID_APP_SHA256_FINGERPRINTS`는 일반 패키지 `com.ssafy.eolssu` 전용으로 유지합니다. `AUTH_ANDROID_QA_APP_SHA256_FINGERPRINTS`는 `com.ssafy.eolssu.qa` 전용이며 각 실제 설치 APK의 공개 SHA-256 인증서 지문을 쉼표로 설정합니다. 한 패키지 지문을 다른 패키지에 복사하지 않습니다. 설정된 패키지만 JSON 배열에 포함하고 둘 다 비어 있을 때만404입니다. 두 설정 모두 형식 검증·대문자 콜론 표기 정규화·중복 제거를 적용합니다. 설정은 운영 Compose 전달과 백엔드 반영 후 확인합니다.

검증 순서: 설치 APK 패키지·versionCode·공개 서명 확인 → `/.well-known/assetlinks.json`의 리다이렉트 없는200/application/json과 패키지별 지문 일치 → 기기 `adb shell pm verify-app-links --re-verify com.ssafy.eolssu.qa` 후 `adb shell pm get-app-links com.ssafy.eolssu.qa`에서 도메인 verified → 새 카카오 인증 시작부터 앱 복귀·완료 API를 확인합니다. 재검증은 비동기이며 강제 인텐트 실행만으로 verified를 대체하지 않습니다. 키스토어·비밀번호는 공유하지 않습니다.

## V25 삭제 전 호환 백엔드 배포

S15P31E102-499의 1차 호환 릴리스 A는 임시 `bootJar` 블록으로 실행 JAR에서만 V25를 제외하고 V26 이상을 차단했습니다. A 운영 반영·세션 실호출 확인 후 준비하는 후속 B는 이 임시 블록을 제거해 원본 V25를 다시 포함합니다. 아래는 두 릴리스를 순서대로 수행하는 절차입니다. [이유·검증](../../docs/knowledge/infra/mission-migration-compat.md), [작업 상태](../../docs/plans/S15P31E102-499.md)를 확인합니다.

1. 배포 전 운영 Flyway 이력이 V24 이하이고 실패 행이 없는지 확인합니다. V25가 이미 적용된 환경에는 이 호환 JAR을 배포하지 않습니다. 승인 파일을 먼저 추가하지 않습니다.
2. 1차 호환 릴리스 A를 기존 Jenkins 경로로 단독 배포합니다. `Check database migrations` → 필요 시 V24까지 적용 → 백엔드 교체 순서로 동작합니다. 실행 JAR에 V25가 없는지 확인합니다.
3. 실제 운영 컨테이너의 연결 JAR이 A의 SHA이고 healthy인지 확인합니다. 연습 세션 생성·조회와 멱등 재전송이 정상인지 확인하고 증거를 남깁니다. 확인 전에는 후속 변경을 합치지 않습니다.

```sh
ssh edo1 'sudo -n docker inspect --format "{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}{{range .Mounts}}{{println .Source}}{{end}}" "$(sudo -n /opt/eolssu/bin/deploy-backend --active-container)"'
```

4. A 반영 후 `backend/build.gradle.kts`의 S15P31E102-499 임시 `bootJar` 블록 전체를 제거하는 후속 릴리스 B를 준비합니다. 원본 V25를 변경하거나 새 번호로 복제하지 않습니다. B JAR에 원본 V25가 바이트 그대로 포함되는지 확인합니다.
5. 운영 데이터의 `mission_id IS NOT NULL` 개수, 외래키·인덱스·뷰 등 의존성과 최신 복원 가능한 백업을 검토합니다. 공유 대화의 과거 데이터 상태를 현재 사실로 대신하지 않습니다. 검토 후 B JAR에서 추출한 V25의 SHA-256을 기존 승인 경로에 등록하고 B를 배포합니다.
6. V25 성공 이력·컬럼 삭제·세션 생성/조회·health·사후 백업을 확인합니다. V25 이후 정상 재배포할 복구 JAR은 컬럼을 읽지 않는 코드와 원본 V25를 함께 포함해야 합니다. A JAR은 V25 누락 검사로 정상 재배포가 거부됩니다. 구 운영 e335ac 버전은 삭제된 컬럼을 읽으므로 복구 대상으로 쓰지 않습니다.

검증용 로컬 `bootRun`/`db`는 V25를 적용합니다. 운영 호환성 점검에는 실행 JAR을 사용합니다. 1차·2차를 한 번에 배포하면 선배포 목적을 달성하지 못합니다.

### 업로드 원본 임시 처리 (S15P31E102-534)

534 브랜치의 원본 비보관 전환은 [정책](../../docs/specs/user-sheets.md#업로드-원본-비보관-정책-2026-11-06-확정-s15p31e102-534)을 따릅니다. 원본은 backend `/tmp/eolssu-sheet-imports`와 요청 처리 중의 `/tmp/eolssu-multipart`에 있는 비공개 임시 파일이며 최대 30분·종료 즉시 정리합니다. 두 디렉터리를 영구 볼륨·백업에 넣지 않습니다. Nginx가 본문을 받는 동안의 임시 파일은 승인된 예외이며 요청이 끝나면 지워집니다. 한 backend 인스턴스에서 운영합니다.

`SHEET_IMPORT_READ_BASE_URL`은 단일 앱에서 사용합니다. 운영 블루그린에서는 접수 슬롯이 OMR 원본 읽기 주소를 자기 내부 이름으로 고정합니다. OMR만 내부망에서 만료 토큰으로 읽으며 Nginx의 `/api/internal/` 차단을 함께 배포해야 합니다. 모든 외부 프록시도 같은 경로를 차단해야 합니다. R2 설정과 별개로 원본을 불러올 수 있습니다. 생성 M4A는 `SHEET_STORAGE_*`의 버킷에 임시 보관합니다. 다운로드 기한은 생성 완료 후 10분이며, 만료된 파일은 백엔드가 정리합니다. MIDI는 내부 생성에서 사용합니다. 기존 R2 원본 일괄 정리는 별도 승인·실행 범위입니다.
