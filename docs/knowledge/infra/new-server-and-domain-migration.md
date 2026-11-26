# 새 서버·새 도메인 이전 안내 (SSAFY 종료 대비)

- 상태: 정리만 됨, 실행 전. 새 도메인은 아직 정하지 않았다(2026-11-26).
- 작성 근거: 저장소 파일(`infra/`, 앱 코드, `main` 브랜치 `exec/1-porting-manual.md`·`exec/2-external-services.md`). 실제 서버 상태는 확인하지 않았다.
- 관련 작업: [GitHub 전환 (S15P31E102-1290)](../../plans/S15P31E102-1290.md)

## 왜 해야 하나

SSAFY 과정이 끝나면 다음이 함께 없어진다.

- 서버 `edo1`(운영)과 `edo2`(백업 보관·복원 시험·복제본·Mattermost 중계)
- 도메인 `k15e102.p.ssafy.io`: SSAFY가 관리하는 Route 53 레코드라 팀이 옮길 수 없다.
- SSAFY GitLab, Mattermost(`meeting.ssafy.com`), Jira

코드는 GitHub `team-edo/eolssu`로 옮겼다. 저장소 쪽 GitHub 전환(Jenkins 클론 주소, QA 도구, 블로그 배포·편집기)은 1290 PR에 있다.

## 0. 먼저 정할 것

| 정할 것 | 이유 |
| --- | --- |
| 새 도메인 | 인증서, 소셜 로그인, 앱의 서버 주소, App Links, 스토어 URL이 모두 도메인에 묶여 있다. 한 번 정하면 바꾸기 어려우니 오래 쓸 도메인으로 정한다. 하위 주소를 만들 수 있게 대표 도메인을 사는 것을 권한다. |
| 새 서버 | 문서 기준 사양은 4코어·16GB·Ubuntu 24.04. 22·80·443 포트를 연다. |
| 백업 보관 위치 | `edo2`가 없어진다. 새 백업 대상과 백업 암호화 키 보관 방법을 정한다. |
| 알림 경로 | Jenkins 실패 알림은 "그대로" 두기로 했다(2026-11-26). 다만 `infra/jenkins/notifications/notify.py:211`이 webhook 주소를 `meeting.ssafy.com`으로만 허용하므로, 다른 Mattermost를 쓰려면 이 검사도 바꿔야 한다. |
| 실데이터 DB 이전 방법 | `exec/3-eolssu-db-dump.sql`은 개인정보 행을 뺀 덤프라 실제 이전에 쓸 수 없다. 쓰기 중지 → 전체 덤프 → 복원 → 검증 순서를 정한다. |

## 1. 새 서버 구축 (도메인 없이 가능)

도메인이 없어도 서버 IP로 여기까지 진행할 수 있다. 자세한 명령은 각 참고 문서에 있다.

| 순서 | 할 일 | 참고 |
| --- | --- | --- |
| 1 | Ubuntu·Docker 설치, `jenkins` 사용자(UID/GID 111:113) | 포팅 매뉴얼 §6-1의 1, [컨트롤러 README](../../../infra/jenkins/controller/README.md) "경로와 보존 범위" |
| 2 | `/opt/app`에 `https://github.com/team-edo/eolssu.git` 클론, 운영 설정·배포 도구·sudoers 설치 | 포팅 매뉴얼 §6-1의 2, [runtime README](../../../infra/runtime/README.md) |
| 3 | 비밀값: `/opt/eolssu/.env`(0600), 알림 webhook, QA·편집기 토큰, mysql-admin 비밀번호 | 포팅 매뉴얼 §6-1의 3 |
| 4 | DB 볼륨 생성 → MySQL 기동 → 전체 덤프 복원 → 자료 복사 | 포팅 매뉴얼 §6-1의 4·6·8 |
| 5 | 이미지 빌드(backend·ai·omr·playback) | 포팅 매뉴얼 §4-5, §6-1의 5 |
| 6 | Jenkins 홈 복사 → 컨트롤러 기동 → 노드 secret 재발급 → 에이전트 설치 → GitHub 인증 정보 등록 | 컨트롤러 README "전환 절차", runtime README의 GitHub 연결 4단계 |
| 7 | 모니터링(Grafana·Prometheus)·phpMyAdmin | `infra/monitoring/README.md`, `infra/mysql-admin/README.md` |
| 8 | 첫 배포: `eolssu-backend` → 웹 잡(REVISION) → ai·omr·playback. 처음 한 번은 직접 실행해야 polling이 켜진다. | runtime README, 포팅 매뉴얼 §6-1의 10 |

### 옮길 데이터

- **복사**
  - MySQL 전체 덤프(DB `eolssu`, 계정 `eolssu`·`grafana`·`eolssu_web_read`)
  - Jenkins 홈 `/var/lib/jenkins`: 인증 정보 암호화 키와 빌드 기록
  - 저장소에 없는 원본: `/opt/eolssu/playback-assets`, `/opt/eolssu/janggu`
  - 보관함: `/opt/eolssu/apks`, `/opt/eolssu/ipas`
    - QA APK 보관함은 새로 시작하기로 했다. 예전 GitLab MR 번호가 GitHub PR 번호와 겹치기 때문이다(1290 결정).
  - Grafana 데이터, 블로그 조회수·편집 대기 DB `/var/lib/blog-views`, `/opt/eolssu/migration-approvals`
- **새로 만들기**: Docker 이미지, `/opt/eolssu/config`·`bin`, systemd 유닛, 인증서, Prometheus 데이터
- **옮기지 않음**: R2 객체(외부 저장소), hey 봇(n8n, 폐기 예정)

### Jenkins

- 노드: `edo1-host`(라벨 `edo1 linux docker`), `edo1-web`, `edo1-ai`, `edo1-blog`, Mac `mac-ios-eunseo`
  - 이름에 `edo1`이 들어가지만 라벨일 뿐이다. 같은 이름으로 다시 만들면 Jenkinsfile을 고치지 않아도 된다.
- 인증 정보: `eolssu-github`(새로 등록), `github-blog-token`, Android 업로드 키·Play 서비스 계정, App Store Connect API 키
- 잡: `eolssu-backend`, `eolssu-frontend-web-수동배포용`, `eolssu-web-tests`, `eolssu-python-tests`, `eolssu-ai`, `eolssu-omr`, `eolssu-playback`, `eolssu-android-qa`, `eolssu-android-release`, `eolssu-ios-qa`, `eolssu-ios-release`, `eolssu-blog`
- GitHub PAT
  - Jenkins 클론용: Contents 읽기
  - QA 폰 PR 동기화용: Pull requests 읽기
  - 블로그 편집기용: Contents 읽기·쓰기 (`EDITOR_GITHUB_WRITE_TOKEN_FILE`)
- iOS Mac 에이전트: SSH 터널 대상·키를 새 서버로 바꾸고 `infra/ios-qa/setup-agent.py`를 다시 실행한다. 지금은 `ubuntu@k15e102.p.ssafy.io`와 키 `K15E102T.pem`을 쓴다.
- 백업: `infra/backup/install.py:55,62,75`에 지금 서버와 `edo2`의 IP가 박혀 있다. 새 서버 IP와 새 백업 대상으로 바꾼다.

### 문서에 없는 것

- Jenkins를 다른 서버로 옮기는 절차: 문서에는 같은 서버 안에서 옮긴 기록만 있다.
- 블로그 편집기 컨테이너(`eolssu-blog-editor-writable`)의 실행 명령: [editor README](https://github.com/team-edo/eolssu/blob/blog/editor/README.md)에 설정은 있지만 `docker run` 명령은 없다.
- 포팅 매뉴얼(`main`)에 낡은 내용이 있다.
  - GitLab 주소와 `eolssu-gitlab`
  - 노드 목록에 `edo1-blog`가 없다.
  - §6-1의 2에서 `nginx.tls.conf`를 `install-runtime-compose`가 설치한다고 적었지만, 실제로는 `init-letsencrypt.py`가 만든다.

## 2. 도메인이 정해지면

새 도메인을 `NEW`(예: `eolssu.example`)라고 하자. 아래는 모두 `k15e102.p.ssafy.io`를 `NEW`로 바꾸는 일이다.

### 2-1. DNS와 인증서

1. DNS에서 `NEW`의 A 레코드를 새 서버 IP로 지정한다.
2. 서버에서 Let's Encrypt 인증서를 발급한다: `infra/scripts/init-letsencrypt.sh --domain NEW` ([runtime README](../../../infra/runtime/README.md) "Let's Encrypt와 HTTPS").
   - 외부에서 80·443에 접속할 수 있어야 한다.
   - Nginx 설정 `infra/runtime/nginx.tls.conf.template`은 `__TLS_DOMAIN__`을 치환해서 쓰므로 고치지 않는다.
3. 확인: `certbot renew --dry-run`

### 2-2. 저장소 코드 (PR 하나로)

**앱** (다시 빌드하고 스토어에 다시 배포해야 반영된다)

- `frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/core/network/HttpClients.kt:58`: 운영 서버 주소
- `frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/data/user/SocialAuth.kt:47`: 소셜 로그인 복귀 호스트
- `frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/data/user/PublishedLegalDocument.kt:5-7`: 약관·개인정보·계정 삭제 주소
- `frontend/androidApp/build.gradle.kts:14,21,81`: 디버그·릴리스 API 주소
- `frontend/androidApp/src/main/AndroidManifest.xml:51`: App Links 호스트(43행 주석 포함)
- 테스트: `frontend/shared/src/commonTest/**`(OAuth 관련 10여 파일), `frontend/shared/src/webTest/.../WebOAuthTest.kt`, `backend/src/test/.../ProxiedRequestSecurityTests.kt`
  - 일괄 치환 뒤 Android·웹·백엔드 테스트를 돌린다.
- iOS는 Associated Domains를 쓰지 않아 iOS 설정 파일은 고칠 것이 없다(서버 중계 방식).

**인프라**

- `infra/jenkins/deploy-backend.py:195`: `TLS_DOMAIN`(배포 후 HTTPS 확인)
- `infra/jenkins/deploy-frontend.py:113-114`: 웹 배포 후 확인
- `infra/scripts/init-letsencrypt.py:108-109`: 기본 도메인. 테스트 `infra/scripts/tests/test_init_letsencrypt.py:19`도 함께 고친다.
- `infra/drift/manifest.json:195`: 설정 일치 점검의 `__TLS_DOMAIN__`
- `infra/ios-qa/ios.Jenkinsfile:66`: IPA 게시 `--base-url`
- `infra/ios-qa/setup-agent.py:33`: Mac SSH 터널 대상
- `infra/terms/publish.py:13`, `infra/catalog/publish_score_tempo_quiesced.py:25`: 운영 API 주소
- 예시 파일: `infra/runtime/.env.example:32,33,47,50`, `backend/.env.example:81`
- 정리 대상(없어지는 서버용, 바꾸지 말고 지워도 됨): `infra/standby/edo2-domain.nginx.conf`, `infra/edo-check/*`(hey 봇·Mattermost 중계), `infra/docker/rotate-replication-proxy-20261113.py`

**블로그** (`blog` 브랜치)

- `hugo.toml:12,14`: `editorAPI`·`pageViewsAPI`가 `https://k15e102.p.ssafy.io/blog-editor/...`이다.
- 블로그 주소 `team-edo.github.io`는 그대로라 `BLOG_ORIGIN`과 GitHub OAuth 앱 콜백은 바꾸지 않는다.

**문서**

- md 파일: `infra/runtime/README.md` 8곳, `infra/release/README.md` 5곳, `infra/catalog/README.md` 3곳, `infra/ios-qa/README.md`·`infra/storage/README.md` 각 1곳, `backend/README.md`·`frontend/README.md` 각 2곳, `docs/specs/social-auth.md` 10곳, `docs/specs/api-documentation.md`·`docs/decisions/frontend-http-client.md` 각 1곳
- `docs/plans`·`docs/knowledge`의 지난 기록은 고치지 않는다.
- 포팅 매뉴얼·외부 서비스 문서(`main` 브랜치 `exec/`)도 고친다.
- 찾는 명령: `git grep -n "k15e102" -- . ':!docs/plans' ':!docs/knowledge'`

### 2-3. 서버 `.env`

- `AUTH_OAUTH_KAKAO_REDIRECT_URIS`, `AUTH_OAUTH_GOOGLE_REDIRECT_URIS`, `AUTH_OAUTH_NAVER_REDIRECT_URIS`, `AUTH_OAUTH_APPLE_REDIRECT_URIS`, `AUTH_OAUTH_APPLE_DEV_REDIRECT_URIS`
  - 주소 모양은 아래 2-4와 같다.
- `SECURITY_ALLOWED_ORIGINS`(값이 있다면): `https://NEW`

### 2-4. 외부 서비스 콘솔

복귀 주소 모양(`{provider}`는 `kakao`·`google`·`naver`·`apple`):

- Android(App Links): `https://NEW/auth/android/oauth/{provider}/callback`
- iOS(서버 중계): `https://NEW/api/auth/ios/oauth/{provider}/callback`
- 웹: `https://NEW/auth/oauth/{provider}/callback`
- iOS 개발 앱(Apple): `https://NEW/api/auth/ios-dev/oauth/apple/callback`

| 서비스 | 바꿀 것 |
| --- | --- |
| 카카오 개발자 | 플랫폼 도메인, Redirect URI, 연결 해제 웹훅 `https://NEW/api/webhooks/kakao/events` |
| 구글 Cloud Console | OAuth 클라이언트의 승인된 리디렉션 URI(웹 클라이언트면 JavaScript 원본도) |
| 네이버 개발자 | 서비스 URL `https://NEW`, Callback URL |
| Apple Developer | Sign in with Apple 서비스 ID의 도메인과 Return URL(운영·개발 앱 모두) |
| Cloudflare R2 | 버킷 CORS 허용 출처 `https://NEW` (`*` 금지) |
| Google Play Console | 개인정보 처리방침 `https://NEW/api/terms/documents/current/privacy`, 계정 삭제 `https://NEW/api/terms/documents/account-deletion` |
| Android App Links | 새 도메인의 `/.well-known/assetlinks.json` 응답과 `adb shell pm get-app-links com.ssafy.eolssu` 검증 결과 확인 |

### 2-5. 배포와 확인

1. 2-2 PR을 `develop`에 병합한다. backend가 자동 배포되고, 웹은 수동 잡으로 배포한다.
2. 확인
   - `https://NEW/api/actuator/health/readiness`가 200이고 `UP`인지
   - `https://NEW/__eolssu_release.json`의 SHA
3. 소셜 로그인 4종을 Android·iOS·웹에서 각각 시험한다. 카카오 연결 해제 웹훅도 확인한다.
4. Android·iOS 앱을 새 버전으로 빌드해 비공개 테스트·TestFlight에 올린다.
   - 예전 앱은 예전 도메인을 부르므로 SSAFY 도메인이 없어지면 동작하지 않는다.
5. 블로그 `hugo.toml`을 바꾼 뒤 블로그의 로그인·조회수가 동작하는지 확인한다.
6. `infra/drift/check.py --host <새 호스트>`로 설정 일치를 점검한다. 새 호스트 이름을 `infra/drift/check.py:96`의 선택지와 `manifest.json`에 추가해야 한다.

## 3. 마지막 정리

- 정리 타이머(retention·privacy-retention·qa-mr-sync)를 켠다.
- 예전 서버를 폐기하기 전에 마지막 DB 덤프와 Jenkins 홈을 한 번 더 받아 둔다.
- 팀원 로컬 저장소 주소를 바꾼다: `git remote set-url origin https://github.com/team-edo/eolssu.git`
- `AGENTS.md`의 Jira 규칙(브랜치·커밋 Key, PR `Closes`)을 Jira가 닫힌 뒤 어떻게 할지 정한다.
